package com.snehal.neopay.operations.recon;

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

@Entity
@Table(name = "recon_break",
        indexes = @Index(name = "idx_recon_break_run", columnList = "run_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReconBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconBreakType type;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(name = "internal_amount")
    private Long internalAmount;

    @Column(name = "psp_amount")
    private Long pspAmount;

    @Column(name = "bank_amount")
    private Long bankAmount;

    @Column(length = 5)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconBreakStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void resolve(String resolutionNote) {
        this.status = ReconBreakStatus.RESOLVED;
        this.note = resolutionNote;
    }
}
