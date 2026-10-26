package com.snehal.neopay.payment.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReserveReleaseRepository extends JpaRepository<ReserveRelease, UUID> {
}
