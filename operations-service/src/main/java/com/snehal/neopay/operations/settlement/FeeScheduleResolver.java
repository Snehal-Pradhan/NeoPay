package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.money.FeeSchedule;
import com.snehal.neopay.operations.entity.MerchantFeeSchedule;
import com.snehal.neopay.operations.repository.MerchantFeeScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeeScheduleResolver {

    private final MerchantFeeScheduleRepository repository;

    public FeeSchedule forMerchant(UUID merchantId) {
        return repository.findById(merchantId)
                .map(FeeScheduleResolver::toSchedule)
                .orElseGet(FeeSchedule::standardIndia);
    }

    private static FeeSchedule toSchedule(MerchantFeeSchedule stored) {
        return new FeeSchedule(stored.getFeeBps(), stored.getGstBps(),
                stored.getTdsBps(), stored.getReserveBps(), RoundingMode.HALF_UP);
    }
}
