package com.snehal.neopay.payment.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByPaymentIdAndMerchantIdOrderByOpenedAtAsc(UUID paymentId, UUID merchantId);
}
