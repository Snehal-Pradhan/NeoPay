package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final MerchantRepository merchantRepository;

    @Transactional
    public MerchantStatus verify(UUID merchantId) {
        Merchant merchant = require(merchantId);
        merchant.verifyKyc();
        merchantRepository.save(merchant);
        return merchant.getStatus();
    }

    @Transactional
    public MerchantStatus reject(UUID merchantId) {
        Merchant merchant = require(merchantId);
        merchant.rejectKyc();
        merchantRepository.save(merchant);
        return merchant.getStatus();
    }

    @Transactional
    public MerchantTier upgradeTier(UUID merchantId, MerchantTier tier) {
        Merchant merchant = require(merchantId);
        merchant.upgradeTier(tier);
        merchantRepository.save(merchant);
        return merchant.getTier();
    }

    @Transactional(readOnly = true)
    public MerchantStatus status(UUID merchantId) {
        return require(merchantId).getStatus();
    }

    private Merchant require(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", merchantId));
    }
}
