package com.snehal.neopay.payment.saga;

import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.Currency;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.IdempotencyConflictException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.fx.FxRateLockService;
import com.snehal.neopay.payment.risk.RiskEnforcer;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAuthorizationRecorder {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransitionService paymentTransitionService;
    private final OutboxEventPublisher eventPublisher;
    private final PaymentMapper paymentMapper;
    private final PaymentMetrics metrics;
    private final RiskEnforcer riskEnforcer;
    private final FxRateLockService fxRateLockService;

    @Transactional
    public Payment recordPayment(UUID merchantId, PaymentInitRequest request, String idempotencyKey) {
        OrderRecord order = orderRepository.findByIdAndMerchantIdForUpdate(request.orderId(), merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        if(order.getOrderStatus() != OrderStatus.CREATED && order.getOrderStatus() != OrderStatus.ATTEMPTED) {
            throw new BusinessRuleViolationException("ORDER_NOT_PAYABLE",
                    "Order cannot accept payment in status: "+order.getOrderStatus());
        }

        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleViolationException("ORDER_EXPIRED",
                    "Order has expired: " + order.getId());
        }

        riskEnforcer.enforce(merchantId, order.getAmount());

        order.markAttempted();

        Payment payment = Payment.builder()
                .order(order)
                .merchantId(merchantId)
                .amount(order.getAmount())
                .status(PaymentStatus.CREATED)
                .method(request.method())
                .idempotencyKey(idempotencyKey != null ? idempotencyKey: UUID.randomUUID().toString())
                .methodDetails(request.methodDetails())
                .build();
        payment = paymentRepository.save(payment);

        if (!"INR".equals(payment.getAmount().getCurrency())) {
            fxRateLockService.lock(payment.getId(),
                    Currency.from(payment.getAmount().getCurrency()), Currency.INR);
        }

        paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_ATTEMPT);
        metrics.paymentInitiated(request.method().name());
        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findExistingAttempt(UUID merchantId, String idempotencyKey,
                                                         PaymentInitRequest request) {
        return paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .map(payment -> {
                    requireSamePayload(payment, request);
                    return paymentMapper.toResponse(payment);
                });
    }

    private static void requireSamePayload(Payment payment, PaymentInitRequest request) {
        boolean sameOrder = payment.getOrder() != null
                && payment.getOrder().getId().equals(request.orderId());
        boolean sameMethod = payment.getMethod() == request.method();
        boolean sameDetails = java.util.Objects.equals(payment.getMethodDetails(),
                request.methodDetails());
        if (!sameOrder || !sameMethod || !sameDetails) {
            throw new IdempotencyConflictException(
                    "Idempotency key reused with a different payment payload");
        }
    }

    @Transactional
    public PaymentResponse compensateAuthorizationFailure(UUID paymentId, String errorCode,
                                                          String errorDescription) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
        payment.recordFailure(errorCode, errorDescription);
        payment = paymentRepository.save(payment);

        metrics.paymentFailed(errorCode);
        publishStatusEvent(payment, "PAYMENT_AUTHORIZATION_COMPENSATED");
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse applyGatewayResult(UUID paymentId, PaymentResult result) {
        log.info("Applying Gateway result for paymentId: {}", paymentId);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        switch (result) {
            case PaymentResult.Pending pending -> payment.recordProcessorReference(pending.registrationRef());
            case PaymentResult.Failure failure -> {
                paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
                payment.recordFailure(failure.errorCode(), failure.errorDescription());
                metrics.paymentFailed(failure.errorCode());
            }
            case PaymentResult.Success success -> {
                paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
                payment.recordBankReference(success.bankReference());
                payment.markAuthorized(LocalDateTime.now());
            }
        }

        payment = paymentRepository.save(payment);
        publishStatusEvent(payment, "PAYMENT_CREATED");
        log.info("Successfully applied Gateway result for paymentId: {}", paymentId);
        return paymentMapper.toResponse(payment);
    }

    private void publishStatusEvent(Payment payment, String eventType) {
        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), eventType,
                Map.of("orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", payment.getMerchantId().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod()
                ));
    }
}





















