package com.snehal.neopay.payment.fx;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FxRateLockRepository extends JpaRepository<FxRateLock, UUID> {
}
