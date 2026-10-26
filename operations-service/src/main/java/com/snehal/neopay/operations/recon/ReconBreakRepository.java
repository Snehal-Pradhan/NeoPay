package com.snehal.neopay.operations.recon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconBreakRepository extends JpaRepository<ReconBreak, UUID> {

    List<ReconBreak> findByRunIdOrderByCreatedAtAsc(UUID runId);
}
