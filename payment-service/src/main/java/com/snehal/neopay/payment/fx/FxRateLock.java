package com.snehal.neopay.payment.fx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fx_rate_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FxRateLock {

    @Id
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(nullable = false, length = 5)
    private String baseCurrency;

    @Column(nullable = false, length = 5)
    private String quoteCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "spread_bps", nullable = false)
    private int spreadBps;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
