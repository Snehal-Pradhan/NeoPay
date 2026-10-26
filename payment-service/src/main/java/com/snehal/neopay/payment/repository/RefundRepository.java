package com.snehal.neopay.payment.repository;

import com.snehal.neopay.common.enums.RefundStatus;
import com.snehal.neopay.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByPayment_IdAndIdempotencyKey(UUID paymentId, String idempotencyKey);

    List<Refund> findByPayment_IdAndMerchantIdOrderByCreatedAtAsc(UUID paymentId, UUID merchantId);

    @Query("""
            select coalesce(sum(r.amount.amountUnits), 0) from Refund r
            where r.payment.id = :paymentId and r.status = :status
            """)
    long sumAmountByPaymentAndStatus(@Param("paymentId") UUID paymentId,
                                     @Param("status") RefundStatus status);
}
