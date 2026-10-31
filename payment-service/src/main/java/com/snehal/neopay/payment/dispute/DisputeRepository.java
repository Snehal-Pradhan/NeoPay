package com.snehal.neopay.payment.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Dispute d where d.id = :id")
    java.util.Optional<Dispute> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Dispute> findByPaymentIdAndMerchantIdOrderByOpenedAtAsc(UUID paymentId, UUID merchantId);

    List<Dispute> findByStatusAndEvidenceDueAtBefore(DisputeStatus status,
                                                     java.time.LocalDateTime cutoff);
}
