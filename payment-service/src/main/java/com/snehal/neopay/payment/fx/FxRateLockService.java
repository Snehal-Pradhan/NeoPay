package com.snehal.neopay.payment.fx;

import com.snehal.neopay.common.enums.Currency;
import com.snehal.neopay.common.exception.FxRateExpiredException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FxRateLockService {

    private final FxRateLockRepository repository;
    private final FxRateProvider provider;

    @Value("${fx.lock-ttl-minutes:15}")
    private long lockTtlMinutes;

    @Transactional
    public FxRateLock lock(UUID paymentId, Currency base, Currency quote) {
        return repository.findById(paymentId).orElseGet(() -> repository.save(FxRateLock.builder()
                .paymentId(paymentId)
                .baseCurrency(base.name())
                .quoteCurrency(quote.name())
                .rate(provider.rate(base, quote))
                .spreadBps(0)
                .expiresAt(LocalDateTime.now().plusMinutes(lockTtlMinutes))
                .createdAt(LocalDateTime.now())
                .build()));
    }

    @Transactional(readOnly = true)
    public FxRateLock require(UUID paymentId) {
        FxRateLock lock = repository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("FxRateLock", paymentId));
        if (lock.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new FxRateExpiredException("FX rate lock expired for payment " + paymentId);
        }
        return lock;
    }
}
