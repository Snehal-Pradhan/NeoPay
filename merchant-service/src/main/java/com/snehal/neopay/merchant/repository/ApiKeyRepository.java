package com.snehal.neopay.merchant.repository;

import com.snehal.neopay.merchant.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByMerchant_Id(UUID merchantId);

    Page<ApiKey> findByMerchant_Id(UUID merchantId, Pageable pageable);

    Optional<ApiKey> findByKeyId(String keyId);
}
