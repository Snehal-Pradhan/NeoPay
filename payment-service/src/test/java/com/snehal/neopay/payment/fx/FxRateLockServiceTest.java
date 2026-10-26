package com.snehal.neopay.payment.fx;

import com.snehal.neopay.common.enums.Currency;
import com.snehal.neopay.common.exception.FxRateExpiredException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FxRateLockServiceTest {

    private FxRateLockRepository repository;
    private FxRateProvider provider;
    private FxRateLockService service;

    @BeforeEach
    void setUp() {
        repository = mock(FxRateLockRepository.class);
        provider = mock(FxRateProvider.class);
        service = new FxRateLockService(repository, provider);
        ReflectionTestUtils.setField(service, "lockTtlMinutes", 15L);
        when(provider.rate(Currency.USD, Currency.INR)).thenReturn(new BigDecimal("83.00"));
        when(repository.save(any(FxRateLock.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void lockStoresRateAndExpiry() {
        UUID paymentId = UUID.randomUUID();
        when(repository.findById(paymentId)).thenReturn(Optional.empty());

        FxRateLock lock = service.lock(paymentId, Currency.USD, Currency.INR);

        assertEquals(new BigDecimal("83.00"), lock.getRate());
        assertEquals("USD", lock.getBaseCurrency());
        assertEquals("INR", lock.getQuoteCurrency());
        assertEquals(0, lock.getSpreadBps());
    }

    @Test
    void requireReturnsValidLock() {
        UUID paymentId = UUID.randomUUID();
        FxRateLock lock = lock(paymentId, LocalDateTime.now().plusMinutes(5));
        when(repository.findById(paymentId)).thenReturn(Optional.of(lock));

        assertEquals(lock, service.require(paymentId));
    }

    @Test
    void expiredLockIsRejected() {
        UUID paymentId = UUID.randomUUID();
        FxRateLock lock = lock(paymentId, LocalDateTime.now().minusMinutes(1));
        when(repository.findById(paymentId)).thenReturn(Optional.of(lock));

        assertThrows(FxRateExpiredException.class, () -> service.require(paymentId));
    }

    @Test
    void missingLockIsRejected() {
        UUID paymentId = UUID.randomUUID();
        when(repository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.require(paymentId));
    }

    private static FxRateLock lock(UUID paymentId, LocalDateTime expiresAt) {
        return FxRateLock.builder()
                .paymentId(paymentId)
                .baseCurrency("USD")
                .quoteCurrency("INR")
                .rate(new BigDecimal("83.00"))
                .spreadBps(0)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
