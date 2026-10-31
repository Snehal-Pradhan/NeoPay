package com.snehal.neopay.payment.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    Optional<DisputeEvidence> findByDisputeIdAndExternalEvidenceId(
            UUID disputeId, String externalEvidenceId);
}
