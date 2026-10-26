package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.mapper.OrderMapper;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher eventPublisher;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse persist(UUID merchantId, CreateOrderRequest request, UUID customerId,
                                  int defaultOrderExpiryMinutes) {
        OrderRecord order = OrderRecord.builder()
                .receipt(request.receipt())
                .amount(request.amount())
                .notes(request.notes())
                .merchantId(merchantId)
                .customerId(customerId)
                .orderStatus(OrderStatus.CREATED)
                .expiresAt(request.expiresAt() != null ? request.expiresAt() :
                        LocalDateTime.now().plusMinutes(defaultOrderExpiryMinutes))
                .build();

        order = orderRepository.save(order);

        eventPublisher.publish(EventAggregateType.ORDER, order.getId(), "ORDER_CREATED",
                Map.of("orderId", order.getId(),
                        "merchantId", merchantId.toString(),
                        "orderStatus", order.getOrderStatus().name(),
                        "amountUnits", order.getAmount().getAmountUnits(),
                        "amountCurrency", order.getAmount().getCurrency()
                )
        );

        return orderMapper.toResponse(order);
    }
}
