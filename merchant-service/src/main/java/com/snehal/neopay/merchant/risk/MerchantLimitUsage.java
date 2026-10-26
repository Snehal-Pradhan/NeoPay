package com.snehal.neopay.merchant.risk;

import com.snehal.neopay.common.enums.MerchantTier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "merchant_limit_usage",
        uniqueConstraints = @UniqueConstraint(name = "uk_limit_usage_merchant_date",
                columnNames = {"merchant_id", "usage_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MerchantLimitUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "amount_units", nullable = false)
    private long amountUnits;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    public void add(long amount) {
        this.amountUnits = Math.addExact(this.amountUnits, amount);
        this.requestCount++;
    }
}
