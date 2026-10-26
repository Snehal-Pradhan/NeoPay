package com.snehal.neopay.operations.recon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconRunRepository extends JpaRepository<ReconRun, UUID> {
}
