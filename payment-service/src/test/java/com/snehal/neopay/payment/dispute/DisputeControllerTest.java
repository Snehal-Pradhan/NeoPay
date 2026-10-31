package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.entity.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeControllerTest {

    private final DisputeService service = mock(DisputeService.class);
    private final MerchantContext context = new MerchantContext();
    private final DisputeController controller = new DisputeController(service, context);

    @Test
    void openMapsTheCreatedDispute() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        context.setMerchantId(merchantId);
        Dispute dispute = Dispute.builder()
                .id(disputeId)
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(Money.inr(250))
                .status(DisputeStatus.OPENED)
                .reason("fraud")
                .openedAt(LocalDateTime.now())
                .build();
        CreateDisputeRequest request = new CreateDisputeRequest(Money.inr(250), "fraud");
        when(service.open(merchantId, paymentId, request)).thenReturn(dispute);

        DisputeController.DisputeResponse response = controller.open(paymentId, request);

        assertEquals(disputeId, response.id());
        assertEquals("OPENED", response.status());
        assertEquals("fraud", response.reason());
    }

    @Test
    void listMapsEveryDispute() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        context.setMerchantId(merchantId);
        Dispute dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(Money.inr(100))
                .status(DisputeStatus.WON)
                .openedAt(LocalDateTime.now())
                .resolvedAt(LocalDateTime.now())
                .build();
        when(service.list(merchantId, paymentId)).thenReturn(List.of(dispute));

        List<DisputeController.DisputeResponse> responses = controller.list(paymentId);

        assertEquals(1, responses.size());
        assertEquals("WON", responses.getFirst().status());
    }

    @Test
    void evidenceSubmissionMapsDeadlineAndStatus() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        context.setMerchantId(merchantId);
        LocalDateTime deadline = LocalDateTime.now().plusDays(5);
        Dispute dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(Money.inr(250))
                .status(DisputeStatus.UNDER_REVIEW)
                .reason("fraud")
                .openedAt(LocalDateTime.now())
                .evidenceDueAt(deadline)
                .build();
        EvidenceRequest request = new EvidenceRequest("ev-9", "delivery proof");
        when(service.submitEvidence(merchantId, dispute.getId(), request)).thenReturn(dispute);

        DisputeController.DisputeResponse response =
                controller.submitEvidence(paymentId, dispute.getId(), request);

        assertEquals("UNDER_REVIEW", response.status());
        assertEquals(deadline, response.evidenceDueAt());
    }

    @Test
    void resolveDelegatesOutcomeAndReason() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        context.setMerchantId(merchantId);
        Dispute dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(Money.inr(100))
                .status(DisputeStatus.LOST)
                .openedAt(LocalDateTime.now())
                .resolvedAt(LocalDateTime.now())
                .outcomeReason("EVIDENCE_DEADLINE_MISSED")
                .build();
        when(service.resolve(merchantId, dispute.getId(), DisputeStatus.LOST,
                "EVIDENCE_DEADLINE_MISSED")).thenReturn(dispute);

        DisputeController.DisputeResponse response = controller.resolve(paymentId,
                dispute.getId(), new ResolveDisputeRequest(DisputeStatus.LOST,
                        "EVIDENCE_DEADLINE_MISSED"));

        assertEquals("LOST", response.status());
        assertEquals("EVIDENCE_DEADLINE_MISSED", response.outcomeReason());
    }
}
