package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerService;
import com.snehal.neopay.payment.outbox.OutboxEventPublisher;
import com.snehal.neopay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Evidence window, representment and deadline-sweep behaviour. */
class DisputeEvidenceLifecycleTest {

    private static final UUID MERCHANT = UUID.randomUUID();

    private DisputeRepository disputeRepository;
    private DisputeEvidenceRepository evidenceRepository;
    private LedgerService ledgerService;
    private OutboxEventPublisher eventPublisher;
    private DisputeService service;

    @BeforeEach
    void setUp() {
        disputeRepository = mock(DisputeRepository.class);
        evidenceRepository = mock(DisputeEvidenceRepository.class);
        ledgerService = mock(LedgerService.class);
        eventPublisher = mock(OutboxEventPublisher.class);
        service = new DisputeService(disputeRepository, mock(PaymentRepository.class),
                evidenceRepository, ledgerService, eventPublisher);
        when(disputeRepository.save(any(Dispute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestingEvidenceSetsStatusAndDeadline() {
        Dispute dispute = dispute(DisputeStatus.OPENED);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));
        LocalDateTime deadline = LocalDateTime.now().plusDays(7);

        Dispute updated = service.requestEvidence(MERCHANT, dispute.getId(), deadline);

        assertEquals(DisputeStatus.EVIDENCE_REQUIRED, updated.getStatus());
        assertEquals(deadline, updated.getEvidenceDueAt());
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_EVIDENCE_REQUESTED"), any());
    }

    @Test
    void representedDisputeCannotAskForEvidenceAgain() {
        Dispute dispute = dispute(DisputeStatus.REPRESENTED);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.requestEvidence(MERCHANT, dispute.getId(),
                        LocalDateTime.now().plusDays(1)));

        assertEquals("DISPUTE_ALREADY_REPRESENTED", error.getErrorCode());
    }

    @Test
    void evidenceBeforeDeadlineMovesDisputeUnderReview() {
        Dispute dispute = dispute(DisputeStatus.EVIDENCE_REQUIRED);
        dispute.requestEvidence(LocalDateTime.now().plusDays(3));
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));
        when(evidenceRepository.findByDisputeIdAndExternalEvidenceId(dispute.getId(), "ev-1"))
                .thenReturn(Optional.empty());

        Dispute updated = service.submitEvidence(MERCHANT, dispute.getId(),
                new EvidenceRequest("ev-1", "Delivery proof and signed receipt"));

        assertEquals(DisputeStatus.UNDER_REVIEW, updated.getStatus());
        assertEquals("ev-1", updated.getEvidenceReference());
        verify(evidenceRepository).save(any(DisputeEvidence.class));
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_EVIDENCE_SUBMITTED"), any());
    }

    @Test
    void repeatedEvidenceUploadIsIdempotent() {
        Dispute dispute = dispute(DisputeStatus.EVIDENCE_REQUIRED);
        dispute.requestEvidence(LocalDateTime.now().plusDays(3));
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));
        when(evidenceRepository.findByDisputeIdAndExternalEvidenceId(dispute.getId(), "ev-1"))
                .thenReturn(Optional.of(mock(DisputeEvidence.class)));

        assertSame(dispute, service.submitEvidence(MERCHANT, dispute.getId(),
                new EvidenceRequest("ev-1", "same upload")));
        verify(evidenceRepository, never()).save(any());
        assertEquals(DisputeStatus.EVIDENCE_REQUIRED, dispute.getStatus());
    }

    @Test
    void evidenceAfterDeadlineIsRejected() {
        Dispute dispute = dispute(DisputeStatus.EVIDENCE_REQUIRED);
        dispute.requestEvidence(LocalDateTime.now().minusMinutes(1));
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.submitEvidence(MERCHANT, dispute.getId(),
                        new EvidenceRequest("ev-late", "too late")));

        assertEquals("EVIDENCE_WINDOW_CLOSED", error.getErrorCode());
        verify(evidenceRepository, never()).save(any());
    }

    @Test
    void representmentRequiresEvidence() {
        Dispute dispute = dispute(DisputeStatus.OPENED);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.represent(MERCHANT, dispute.getId(), "we shipped it"));

        assertEquals("EVIDENCE_NOT_SUBMITTED", error.getErrorCode());
    }

    @Test
    void representmentAfterEvidenceMovesToRepresented() {
        Dispute dispute = dispute(DisputeStatus.UNDER_REVIEW);
        dispute.submitEvidence("ev-1", LocalDateTime.now());
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        Dispute updated = service.represent(MERCHANT, dispute.getId(), "we shipped it");

        assertEquals(DisputeStatus.REPRESENTED, updated.getStatus());
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_REPRESENTED"), any());
    }

    @Test
    void deadlineSweepClosesAsLostAndPostsOnePair() {
        Dispute dispute = dispute(DisputeStatus.EVIDENCE_REQUIRED);
        dispute.requestEvidence(LocalDateTime.now().minusHours(1));
        when(disputeRepository.findByStatusAndEvidenceDueAtBefore(
                eq(DisputeStatus.EVIDENCE_REQUIRED), any())).thenReturn(List.of(dispute));

        int closed = service.closeExpiredEvidenceWindows(LocalDateTime.now());

        assertEquals(1, closed);
        assertEquals(DisputeStatus.LOST, dispute.getStatus());
        assertEquals(DisputeService.EVIDENCE_DEADLINE_MISSED, dispute.getOutcomeReason());
        verify(ledgerService).postPair(any(), eq(LedgerAccount.DISPUTE_HOLD),
                eq(LedgerAccount.PLATFORM_RECEIVABLE), eq(dispute.getAmount()), any());
        verify(disputeRepository).save(dispute);
    }

    @Test
    void secondSweepFindsNothingToClose() {
        when(disputeRepository.findByStatusAndEvidenceDueAtBefore(
                eq(DisputeStatus.EVIDENCE_REQUIRED), any())).thenReturn(List.of());

        assertEquals(0, service.closeExpiredEvidenceWindows(LocalDateTime.now()));
        verify(ledgerService, never()).postPair(any(), any(), any(), any(), any());
    }

    @Test
    void foreignMerchantCannotSubmitEvidence() {
        Dispute dispute = dispute(DisputeStatus.EVIDENCE_REQUIRED);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        assertThrows(ResourceNotFoundException.class, () -> service.submitEvidence(
                UUID.randomUUID(), dispute.getId(), new EvidenceRequest("ev-1", "x")));
    }

    @Test
    void terminalDisputeRejectsFurtherEvidence() {
        Dispute dispute = dispute(DisputeStatus.WON);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.submitEvidence(MERCHANT, dispute.getId(),
                        new EvidenceRequest("ev-1", "x")));

        assertEquals("DISPUTE_ALREADY_RESOLVED", error.getErrorCode());
    }

    @Test
    void resolvingWithReasonStoresItAndPublishes() {
        Dispute dispute = dispute(DisputeStatus.REPRESENTED);
        when(disputeRepository.findByIdForUpdate(dispute.getId()))
                .thenReturn(Optional.of(dispute));

        Dispute resolved = service.resolve(MERCHANT, dispute.getId(),
                DisputeStatus.WON, "issuer accepted the evidence");

        assertEquals(DisputeStatus.WON, resolved.getStatus());
        assertEquals("issuer accepted the evidence", resolved.getOutcomeReason());
        verify(ledgerService).postPair(any(), eq(LedgerAccount.DISPUTE_HOLD),
                eq(dispute.getSourceAccount()), eq(dispute.getAmount()), any());
        verify(eventPublisher).publish(eq(EventAggregateType.PAYMENT), eq(dispute.getId()),
                eq("DISPUTE_RESOLVED"), any());
    }

    private static Dispute dispute(DisputeStatus status) {
        return Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .merchantId(MERCHANT)
                .amount(Money.inr(500))
                .status(status)
                .sourceAccount(LedgerAccount.MERCHANT_PAYABLE)
                .openedAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
