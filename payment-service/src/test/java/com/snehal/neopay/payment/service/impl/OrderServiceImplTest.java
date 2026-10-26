package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.OrderStatus;
import com.snehal.neopay.payment.dto.request.CreateOrderRequest;
import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import com.snehal.neopay.payment.mapper.OrderMapper;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.OrderId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final com.snehal.neopay.payment.repository.PaymentRepository paymentRepository =
            mock(com.snehal.neopay.payment.repository.PaymentRepository.class);
    private final com.snehal.neopay.payment.mapper.PaymentMapper paymentMapper =
            mock(com.snehal.neopay.payment.mapper.PaymentMapper.class);
    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final com.snehal.neopay.payment.client.CustomerServiceClient customerClient =
            mock(com.snehal.neopay.payment.client.CustomerServiceClient.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final OrderPersistenceService persistence = mock(OrderPersistenceService.class);

    private final OrderServiceImpl service = new OrderServiceImpl(orderRepository, paymentRepository,
            paymentMapper, orderMapper, customerClient, publisher, persistence);

    @Test
    void duplicateReceiptIsRejected() {
        UUID merchantId = UUID.randomUUID();
        when(orderRepository.existsByMerchantIdAndReceipt(merchantId, "r-1")).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.DuplicateResourceException.class,
                () -> service.create(MerchantId.of(merchantId), request("r-1")));
    }

    @Test
    void createDelegatesToPersistence() {
        UUID merchantId = UUID.randomUUID();
        OrderResponse response = mock(OrderResponse.class);
        when(persistence.persist(eq(merchantId), any(), eq(null), any(Integer.class)))
                .thenReturn(response);

        assertEquals(response, service.create(MerchantId.of(merchantId), request("r-2")));
    }

    @Test
    void createWithCustomerResolvesCustomerFirst() {
        UUID merchantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderResponse response = mock(OrderResponse.class);
        when(customerClient.findOrCreate(any())).thenReturn(customerId);
        when(persistence.persist(eq(merchantId), any(), eq(customerId), any(Integer.class)))
                .thenReturn(response);

        CreateOrderRequest request = new CreateOrderRequest(Money.inr(100), "r-3", Map.of(), null,
                new CreateOrderRequest.CustomerDetails("N", "e@x.com", "9999999999"));

        assertEquals(response, service.create(MerchantId.of(merchantId), request));
    }

    @Test
    void getByIdMapsEntity() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = order("r-4", OrderStatus.CREATED);
        OrderResponse response = mock(OrderResponse.class);
        when(orderRepository.findByIdAndMerchantId(order.getId(), merchantId))
                .thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        assertEquals(response, service.getById(MerchantId.of(merchantId), OrderId.of(order.getId())));
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndMerchantId(id, UUID.randomUUID())).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> service.getById(MerchantId.of(UUID.randomUUID()), OrderId.of(id)));
    }

    @Test
    void cancelMarksCancelledAndPublishes() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = order("r-5", OrderStatus.CREATED);
        when(orderRepository.findByIdAndMerchantId(order.getId(), merchantId))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        OrderResponse mapped = mock(OrderResponse.class);
        when(orderMapper.toResponse(order)).thenReturn(mapped);

        assertEquals(mapped, service.cancel(MerchantId.of(merchantId), OrderId.of(order.getId())));

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        verify(publisher).publish(any(), eq(order.getId()), eq("ORDER_CANCELLED"), any());
    }

    @Test
    void cancelPaidOrderIsRejected() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = order("r-6", OrderStatus.PAID);
        when(orderRepository.findByIdAndMerchantId(order.getId(), merchantId))
                .thenReturn(Optional.of(order));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.BusinessRuleViolationException.class,
                () -> service.cancel(MerchantId.of(merchantId), OrderId.of(order.getId())));
    }

    private static CreateOrderRequest request(String receipt) {
        return new CreateOrderRequest(Money.inr(100), receipt, Map.of(), null, null);
    }

    private static OrderRecord order(String receipt, OrderStatus status) {
        return OrderRecord.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(100))
                .receipt(receipt)
                .orderStatus(status)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    @Test
    void listPaymentsMapsList() {
        UUID merchantId = UUID.randomUUID();
        OrderRecord order = order("r-7", OrderStatus.CREATED);
        when(orderRepository.findByIdAndMerchantId(order.getId(), merchantId))
                .thenReturn(Optional.of(order));
        com.snehal.neopay.payment.entity.Payment payment =
                mock(com.snehal.neopay.payment.entity.Payment.class);
        when(paymentRepository.findByOrder_Id(order)).thenReturn(java.util.List.of(payment));
        java.util.List<com.snehal.neopay.payment.dto.response.PaymentResponse> mapped =
                java.util.List.of(mock(com.snehal.neopay.payment.dto.response.PaymentResponse.class));
        when(paymentMapper.toResponseList(java.util.List.of(payment))).thenReturn(mapped);

        assertEquals(mapped,
                service.listPayments(MerchantId.of(merchantId), OrderId.of(order.getId())));
    }

    @Test
    void persistPublishesOrderCreated() {
        OrderRepository repo = mock(OrderRepository.class);
        OutboxEventPublisher pub = mock(OutboxEventPublisher.class);
        OrderMapper mapper = mock(OrderMapper.class);
        OrderPersistenceService persistenceService = new OrderPersistenceService(repo, pub, mapper);
        when(repo.save(any(OrderRecord.class))).thenAnswer(invocation -> {
            OrderRecord original = invocation.getArgument(0);
            return OrderRecord.builder()
                    .id(UUID.randomUUID())
                    .merchantId(original.getMerchantId())
                    .amount(original.getAmount())
                    .receipt(original.getReceipt())
                    .notes(original.getNotes())
                    .customerId(original.getCustomerId())
                    .orderStatus(original.getOrderStatus())
                    .attempts(original.getAttempts())
                    .expiresAt(original.getExpiresAt())
                    .build();
        });
        when(mapper.toResponse(any())).thenReturn(mock(OrderResponse.class));

        persistenceService.persist(UUID.randomUUID(), request("r-8"), null, 30);

        ArgumentCaptor<OrderRecord> captor = ArgumentCaptor.forClass(OrderRecord.class);
        verify(repo).save(captor.capture());
        assertNotNull(captor.getValue().getExpiresAt());
        verify(pub).publish(any(), any(), eq("ORDER_CREATED"), any());
    }
}
