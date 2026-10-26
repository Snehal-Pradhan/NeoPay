package com.snehal.neopay.merchant.repository;

import com.snehal.neopay.merchant.entity.MerchantWebhookConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookConfigRepository extends JpaRepository<MerchantWebhookConfig, UUID> {
    List<MerchantWebhookConfig> findByMerchant_Id(UUID merchantId);

    Page<MerchantWebhookConfig> findByMerchant_Id(UUID merchantId, Pageable pageable);

    Optional<MerchantWebhookConfig> findByIdAndMerchant_Id(UUID configId, UUID merchantId);

    List<MerchantWebhookConfig> findByMerchant_IdAndEnabledTrue(UUID merchantId);
}
