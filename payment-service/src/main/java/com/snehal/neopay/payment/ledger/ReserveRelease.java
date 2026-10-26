package com.snehal.neopay.payment.ledger;

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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reserve_release",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reserve_release_line", columnNames = "source_line_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReserveRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_line_id", nullable = false)
    private UUID sourceLineId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;
}
