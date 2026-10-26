package com.snehal.neopay.operations.repository;

import com.snehal.neopay.operations.entity.CarryForwardBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CarryForwardRepository extends JpaRepository<CarryForwardBalance, UUID> {
}
