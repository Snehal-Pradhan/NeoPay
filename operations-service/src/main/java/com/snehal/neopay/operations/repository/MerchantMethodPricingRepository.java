package com.snehal.neopay.operations.repository;

import com.snehal.neopay.operations.entity.MerchantMethodPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MerchantMethodPricingRepository extends JpaRepository<MerchantMethodPricing, UUID> {

    @Query("""
            select p from MerchantMethodPricing p
            where p.merchantId = :merchantId
              and p.effectiveFrom <= :onDate
              and (p.effectiveTo is null or p.effectiveTo >= :onDate)
            order by p.effectiveFrom desc
            """)
    List<MerchantMethodPricing> findApplicable(@Param("merchantId") UUID merchantId,
                                               @Param("onDate") LocalDate onDate);
}
