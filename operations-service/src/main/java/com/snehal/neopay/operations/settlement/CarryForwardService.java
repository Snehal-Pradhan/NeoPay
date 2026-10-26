package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.operations.entity.CarryForwardBalance;
import com.snehal.neopay.operations.repository.CarryForwardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outstanding negative balance per merchant. A shortfall in one settlement cycle is
 * deducted from the next one.
 */
@Service
@RequiredArgsConstructor
public class CarryForwardService {

    private final CarryForwardRepository repository;

    @Transactional(readOnly = true)
    public long outstanding(UUID merchantId) {
        return repository.findById(merchantId)
                .map(CarryForwardBalance::getOutstandingUnits)
                .orElse(0L);
    }

    @Transactional
    public void setOutstanding(UUID merchantId, long units, String currency) {
        CarryForwardBalance balance = CarryForwardBalance.builder()
                .merchantId(merchantId)
                .outstandingUnits(units)
                .currency(currency)
                .updatedAt(LocalDateTime.now())
                .build();
        repository.save(balance);
    }

    @Transactional
    public void clear(UUID merchantId) {
        repository.deleteById(merchantId);
    }
}
