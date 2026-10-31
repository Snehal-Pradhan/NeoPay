package com.snehal.neopay.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Method-specific pricing override with effectivity dates.
 *
 * <p>{@code merchant_fee_schedule} stays the default rule per merchant; a row here changes
 * the rate for one payment method over a date range. Settlement selects the newest row whose
 * window contains the settlement date.
 */
@Entity
@Table(name = "merchant_method_pricing",
        uniqueConstraints = @UniqueConstraint(name = "uq_method_pricing_window",
                columnNames = {"merchant_id", "payment_method", "effective_from"}),
        indexes = @Index(name = "idx_method_pricing_lookup",
                columnList = "merchant_id, effective_from"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MerchantMethodPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "fee_bps", nullable = false)
    private int feeBps;

    @Column(name = "fixed_fee_units", nullable = false)
    private long fixedFeeUnits;

    @Column(name = "gst_bps", nullable = false)
    private int gstBps;

    @Column(name = "tds_bps", nullable = false)
    private int tdsBps;

    @Column(name = "reserve_bps", nullable = false)
    private int reserveBps;

    @Column(name = "max_fee_units", nullable = false)
    private long maxFeeUnits;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
