package com.snehal.neopay.operations.repository;

import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.entity.SettlementPayment;
import com.snehal.neopay.operations.entity.SettlementPaymentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementPaymentRepository extends JpaRepository<SettlementPayment, SettlementPaymentId> {
    List<SettlementPayment> findBySettlement(Settlement settlement);
}
