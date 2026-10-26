package com.snehal.neopay.operations.repository;

import com.snehal.neopay.operations.entity.MerchantFeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MerchantFeeScheduleRepository extends JpaRepository<MerchantFeeSchedule, UUID> {
}
