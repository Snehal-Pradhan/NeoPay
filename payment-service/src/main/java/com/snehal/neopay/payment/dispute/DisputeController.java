package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.entity.Money;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments/{paymentId}/disputes")
public class DisputeController {

    private final DisputeService disputeService;
    private final MerchantContext merchantContext;

    @PostMapping
    public DisputeResponse open(@PathVariable UUID paymentId,
                                @Valid @RequestBody CreateDisputeRequest request) {
        return DisputeResponse.from(disputeService.open(
                merchantContext.getMerchantId(), paymentId, request));
    }

    @GetMapping
    public List<DisputeResponse> list(@PathVariable UUID paymentId) {
        return disputeService.list(merchantContext.getMerchantId(), paymentId).stream()
                .map(DisputeResponse::from)
                .toList();
    }

    @PostMapping("/{disputeId}/evidence")
    public DisputeResponse submitEvidence(@PathVariable UUID paymentId,
                                          @PathVariable UUID disputeId,
                                          @Valid @RequestBody EvidenceRequest request) {
        return DisputeResponse.from(disputeService.submitEvidence(
                merchantContext.getMerchantId(), disputeId, request));
    }

    @PostMapping("/{disputeId}/represent")
    public DisputeResponse represent(@PathVariable UUID paymentId,
                                     @PathVariable UUID disputeId,
                                     @Valid @RequestBody RepresentRequest request) {
        return DisputeResponse.from(disputeService.represent(
                merchantContext.getMerchantId(), disputeId, request.statement()));
    }

    @PostMapping("/{disputeId}/resolve")
    public DisputeResponse resolve(@PathVariable UUID paymentId,
                                   @PathVariable UUID disputeId,
                                   @Valid @RequestBody ResolveDisputeRequest request) {
        return DisputeResponse.from(disputeService.resolve(merchantContext.getMerchantId(),
                disputeId, request.outcome(), request.reason()));
    }

    public record DisputeResponse(UUID id, UUID paymentId, Money amount, String status,
                                  String reason, String outcomeReason,
                                  LocalDateTime openedAt, LocalDateTime evidenceDueAt,
                                  LocalDateTime evidenceSubmittedAt,
                                  LocalDateTime resolvedAt) {
        static DisputeResponse from(Dispute dispute) {
            return new DisputeResponse(dispute.getId(), dispute.getPaymentId(),
                    dispute.getAmount(), dispute.getStatus().name(), dispute.getReason(),
                    dispute.getOutcomeReason(), dispute.getOpenedAt(),
                    dispute.getEvidenceDueAt(), dispute.getEvidenceSubmittedAt(),
                    dispute.getResolvedAt());
        }
    }
}
