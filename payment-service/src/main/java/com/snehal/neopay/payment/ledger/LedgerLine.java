package com.snehal.neopay.payment.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One side of a double-entry posting. Rows are append-only: there is no setter and no
 * update path. A balanced transaction has two or more lines whose debits equal credits.
 */
@Entity
@Table(name = "ledger_line", indexes = {
        @Index(name = "idx_ledger_transaction", columnList = "transaction_id"),
        @Index(name = "idx_ledger_reference", columnList = "reference_type, reference_id"),
        @Index(name = "idx_ledger_account", columnList = "account")
    },
    uniqueConstraints = @jakarta.persistence.UniqueConstraint(
            name = "ux_ledger_line_txn_account", columnNames = {"transaction_id", "account"}))

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LedgerLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerAccount account;

    @Column(name = "debit_units", nullable = false)
    private long debitUnits;

    @Column(name = "credit_units", nullable = false)
    private long creditUnits;

    @Column(nullable = false, length = 5)
    private String currency;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;
}
