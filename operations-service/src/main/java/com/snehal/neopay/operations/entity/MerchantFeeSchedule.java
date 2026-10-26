package com.snehal.neopay.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "merchant_fee_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MerchantFeeSchedule {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "fee_bps", nullable = false)
    private int feeBps;

    @Column(name = "gst_bps", nullable = false)
    private int gstBps;

    @Column(name = "tds_bps", nullable = false)
    private int tdsBps;

    @Column(name = "reserve_bps", nullable = false)
    private int reserveBps;
}
