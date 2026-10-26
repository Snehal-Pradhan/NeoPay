package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.OrderId;
import com.snehal.neopay.common.dto.FindOrCreateCustomerRequest;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.DuplicateResourceException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.client.CustomerServiceClient;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.dto.response.PaymentResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.entity.Payment;
import com.snehal.neopay.payment.mapper.OrderMapper;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import com.snehal.neopay.payment.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final CustomerServiceClient customerServiceClient;
    private final OutboxEventPublisher eventPublisher;
    private final OrderPersistenceService orderPersistenceService;

    @Value("${payment.order.default-order-expiry-minutes:30}")
    private int defaultOrderExpiryMinutes;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CircuitBreaker(name = "merchant-service")
    @Retry(name = "merchant-service")
    public OrderResponse create(MerchantId merchantId, CreateOrderRequest request) {
        if (request.receipt() != null && orderRepository.existsByMerchantIdAndReceipt(merchantId.value(), request.receipt())) {
            throw new DuplicateResourceException("ORDER_RECEIPT_DUPLICATE", "Order with receipt already exists: " + request.receipt());
        }

        UUID customerId = null;
        if (request.customer() != null) {
            customerId = customerServiceClient.findOrCreate(
                    new FindOrCreateCustomerRequest(merchantId.value(),
                            request.customer().email(),
                            request.customer().name(),
                            request.customer().phone())
            );
        }

        return orderPersistenceService.persist(merchantId.value(), request, customerId, defaultOrderExpiryMinutes);
    }

    @Override
    public OrderResponse getById(MerchantId merchantId, OrderId orderId) {
        OrderRecord order = orderRepository.findByIdAndMerchantId(orderId.value(), merchantId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.value()));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(MerchantId merchantId, OrderId orderId) {
        OrderRecord order = orderRepository.findByIdAndMerchantId(orderId.value(), merchantId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.value()));

        if (order.getOrderStatus() == OrderStatus.CANCELLED || order.getOrderStatus() == OrderStatus.PAID) {
            throw new BusinessRuleViolationException("ORDER_CANNOT_CANCEL",
                    "Cannot cancel order with status: " + order.getOrderStatus().name());
        }

        order.markCancelled();
        order = orderRepository.save(order);

        eventPublisher.publish(EventAggregateType.ORDER, order.getId(), "ORDER_CANCELLED",
                Map.of("orderId", order.getId(),
                        "merchantId", merchantId.value().toString(),
                        "orderStatus", order.getOrderStatus().name(),
                        "amountUnits", order.getAmount().getAmountUnits(),
                        "amountCurrency", order.getAmount().getCurrency()
                )
        );

        return orderMapper.toResponse(order);
    }

    @Override
    public List<PaymentResponse> listPayments(MerchantId merchantId, OrderId orderId) {
        OrderRecord order = orderRepository.findByIdAndMerchantId(orderId.value(), merchantId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.value()));

        List<Payment> paymentList = paymentRepository.findByOrder_Id(order);

        return paymentMapper.toResponseList(paymentList);
    }
}
