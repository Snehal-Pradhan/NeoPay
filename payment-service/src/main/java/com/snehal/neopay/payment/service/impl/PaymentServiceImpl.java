package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.enums.PaymentEvent;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.dto.request.PaymentInitRequest;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.gateway.PaymentGatewayRouter;
import com.snehal.neopay.payment.gateway.dto.PaymentRequest;
import com.snehal.neopay.payment.gateway.dto.PaymentResult;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.metrics.PaymentMetrics;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.saga.PaymentAuthorizationRecorder;
import com.snehal.neopay.payment.service.PaymentService;
import com.snehal.neopay.payment.statemachine.PaymentTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRouter paymentGatewayRouter;
    private final PaymentMapper paymentMapper;
    private final PaymentTransitionService paymentTransitionService;
    private final OutboxEventPublisher eventPublisher;
    private final PaymentAuthorizationRecorder paymentAuthorizationRecorder;
    private final PaymentMetrics metrics;
    private final LedgerService ledgerService;

    @Override
    public PaymentResponse initiate(MerchantId merchantId, PaymentInitRequest request, String idempotencyKey) {

        if (idempotencyKey != null) {
            var existing = paymentAuthorizationRecorder.findExistingAttempt(merchantId.value(), idempotencyKey, request);
            if (existing.isPresent()) {
                log.info("Idempotency replay for paymentId: {}", existing.get().id());
                metrics.idempotencyReplay();
                return existing.get();
            }
        }

        Payment payment = paymentAuthorizationRecorder.recordPayment(merchantId.value(), request, idempotencyKey);

        PaymentRequest paymentRequest = new PaymentRequest(payment.getId(),
                request.orderId(), merchantId.value(),
                payment.getAmount(), request.method(),
                request.methodDetails());

        PaymentResult result;
        try {
            result = paymentGatewayRouter.initiate(paymentRequest);
        } catch (Exception e) {
            return paymentAuthorizationRecorder.compensateAuthorizationFailure(payment.getId(),
                    "PAYMENT_GATEWAY_ROUTER_UNREACHABLE", e.getMessage());
        }

        return paymentAuthorizationRecorder.applyGatewayResult(payment.getId(), result);
    }


    @Override
    @Transactional
    public PaymentResponse capture(MerchantId merchantId, PaymentId paymentId) {

        Payment payment = paymentRepository.findByIdAndMerchantIdForUpdate(paymentId.value(), merchantId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.value()));

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            log.info("Capture replay for already captured paymentId: {}", paymentId.value());
            metrics.idempotencyReplay();
            return paymentMapper.toResponse(payment);
        }

        paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);

        PaymentResult paymentResult = paymentGatewayRouter.capture(payment.getMethod(), paymentId.value());

        if(paymentResult instanceof PaymentResult.Success) {
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);
            payment.markCaptured(LocalDateTime.now());
            ledgerService.postCapture(paymentId.value(), payment.getAmount());
            if (payment.getOrder() != null) {
                payment.getOrder().markPaid();
            }
            metrics.paymentCaptured(payment.getMethod().name());
            log.info("Payment captured, paymentID: {}", paymentId.value());
        } else if(paymentResult instanceof  PaymentResult.Failure failure) {
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_FAIL);
            payment.recordFailure(failure.errorCode(), failure.errorDescription());
            metrics.paymentFailed(failure.errorCode());
            log.warn("Payment capture failed, paymentID: {}", paymentId.value());
        }

        payment = paymentRepository.save(payment);

        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), "PAYMENT_STATUS_CHANGED",
                Map.of("orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", merchantId.value().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod()
                )
        );

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public void resolveAuthorization(PaymentId paymentId, boolean approve,
                                     String bankRef, String errorCode, String errorDescription) {


        Payment payment = paymentRepository.findByIdForUpdate(paymentId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId.value()));

        if (payment.getStatus() != PaymentStatus.AUTHORIZING) {
            log.warn("Payment is not in Authorizing state, paymentID: {}, status: {}", paymentId.value(), payment.getStatus());
            return;
        }

        OrderRecord orderRecord = payment.getOrder();

        if (approve) {
            paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_SUCCESS);
            payment.recordBankReference(bankRef);
            payment.markAuthorized(LocalDateTime.now());

            // Auto-capture
            paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_REQUEST);
            PaymentResult captureResult = paymentGatewayRouter.capture(payment.getMethod(), paymentId.value());

            if(captureResult instanceof PaymentResult.Success) {
                paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_SUCCESS);
                payment.markCaptured(LocalDateTime.now());
                ledgerService.postCapture(paymentId.value(), payment.getAmount());
                orderRecord.markPaid();
            } else if (captureResult instanceof  PaymentResult.Failure failure){
                paymentTransitionService.apply(payment, PaymentEvent.CAPTURE_FAIL);
                payment.recordFailure(failure.errorCode(), failure.errorDescription());
            }
        } else {
            paymentTransitionService.apply(payment, PaymentEvent.AUTHORIZE_FAIL);
            payment.recordFailure(errorCode, errorDescription);
        }

        paymentRepository.save(payment);
        orderRepository.save(orderRecord);

        eventPublisher.publish(EventAggregateType.PAYMENT, payment.getId(), "PAYMENT_STATUS_CHANGED",
                Map.of("orderId", payment.getOrder().getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "merchantId", payment.getMerchantId().toString(),
                        "paymentStatus", payment.getStatus().name(),
                        "amountUnits", payment.getAmount().getAmountUnits(),
                        "amountCurrency", payment.getAmount().getCurrency(),
                        "paymentMethod", payment.getMethod()
                )
        );
    }
}











