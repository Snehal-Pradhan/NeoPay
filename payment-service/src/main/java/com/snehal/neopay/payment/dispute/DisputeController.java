package com.snehal.neopay.payment.dispute;

import com.snehal.neopay.common.context.MerchantContext;
import com.snehal.neopay.common.domain.MerchantId;
import com.snehal.neopay.common.domain.PaymentId;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.PaymentStatus;
import com.snehal.neopay.payment.entity.Payment;
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

    public record DisputeResponse(UUID id, UUID paymentId, Money amount, String status,
                                  String reason, LocalDateTime openedAt,
                                  LocalDateTime resolvedAt) {
        static DisputeResponse from(Dispute dispute) {
            return new DisputeResponse(dispute.getId(), dispute.getPaymentId(),
                    dispute.getAmount(), dispute.getStatus().name(), dispute.getReason(),
                    dispute.getOpenedAt(), dispute.getResolvedAt());
        }
    }
}
