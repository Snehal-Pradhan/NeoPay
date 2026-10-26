package com.snehal.neopay.payment.service.impl;

import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.OrderId;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.client.CustomerServiceClient;
import com.snehal.neopay.payment.mapper.OrderMapper;
import com.snehal.neopay.payment.mapper.PaymentMapper;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.OrderRepository;
import com.snehal.neopay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceImplMissingTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderServiceImpl service = new OrderServiceImpl(orderRepository,
            mock(PaymentRepository.class), mock(PaymentMapper.class), mock(OrderMapper.class),
            mock(CustomerServiceClient.class), mock(OutboxEventPublisher.class),
            mock(OrderPersistenceService.class));

    @Test
    void cancellingMissingOrderIsReported() {
        UUID orderId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(orderRepository.findByIdAndMerchantId(orderId, merchantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.cancel(MerchantId.of(merchantId), OrderId.of(orderId)));
    }

    @Test
    void listingPaymentsForMissingOrderIsReported() {
        UUID orderId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(orderRepository.findByIdAndMerchantId(orderId, merchantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.listPayments(MerchantId.of(merchantId), OrderId.of(orderId)));
    }
}
