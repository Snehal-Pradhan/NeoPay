package com.snehal.neopay.merchant.repository;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    boolean existsByEmail(String email);

    List<Merchant> findByStatus(MerchantStatus merchantStatus);
}
