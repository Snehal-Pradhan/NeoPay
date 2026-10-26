package com.snehal.neopay.operations.payout;

import com.snehal.neopay.common.dto.PayoutReturnLedgerRequest;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.CarryForwardService;
import com.snehal.neopay.operations.settlement.SettlementIntegrationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementIntegrationGateway gateway;
    private final CarryForwardService carryForwardService;

    @Transactional
    public Payout createForSettlement(UUID settlementId) {
        var existing = payoutRepository.findBySettlementId(settlementId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId));

        Payout payout = payoutRepository.save(Payout.builder()
                .settlementId(settlementId)
                .merchantId(settlement.getMerchantId())
                .amount(settlement.getNetAmount())
                .status(PayoutStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .build());

        payout.markSent("NACH-" + settlementId);
        return payoutRepository.save(payout);
    }

    @Transactional
    public String payoutFile(UUID settlementId) {
        Payout payout = createForSettlement(settlementId);
        StringBuilder csv = new StringBuilder("settlementId,merchantId,amountUnits,currency\n");
        csv.append(payout.getSettlementId()).append(',')
                .append(payout.getMerchantId()).append(',')
                .append(payout.getAmount().getAmountUnits()).append(',')
                .append(payout.getAmount().getCurrency()).append('\n');
        return csv.toString();
    }

    @Transactional
    public Payout markReturned(UUID payoutId, String reason) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout", payoutId));
        if (payout.getStatus() == PayoutStatus.RETURNED) {
            return payout;
        }

        payout.markReturned(reason, LocalDateTime.now());
        payout = payoutRepository.save(payout);

        gateway.postPayoutReturnLedger(new PayoutReturnLedgerRequest(
                payout.getId(), payout.getAmount().getAmountUnits(),
                payout.getAmount().getCurrency()));

        long outstanding = carryForwardService.outstanding(payout.getMerchantId());
        carryForwardService.setOutstanding(payout.getMerchantId(),
                Math.addExact(outstanding, payout.getAmount().getAmountUnits()),
                payout.getAmount().getCurrency());

        log.warn("Payout returned payoutId={} merchantId={} reason={}",
                payout.getId(), payout.getMerchantId(), reason);
        return payout;
    }
}
