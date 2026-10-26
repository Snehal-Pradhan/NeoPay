package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.dto.SettlementLedgerRequest;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.common.money.FeeSchedule;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.entity.SettlementPayment;
import com.snehal.neopay.operations.entity.SettlementPaymentId;
import com.snehal.neopay.operations.outbox.OutboxEventPublisher;
import com.snehal.neopay.operations.repository.SettlementPaymentRepository;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.dto.BankTransferResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementTransactionExecutor {

    private final SettlementRepository settlementRepository;
    private final SettlementPaymentRepository settlementPaymentRepository;
    private final BankTransferProcessor bankTransferProcessor;
    private final OutboxEventPublisher outboxEventPublisher;
    private final SettlementIntegrationGateway settlementIntegrationGateway;
    private final CarryForwardService carryForwardService;
    private final FeeScheduleResolver feeScheduleResolver;

    @Value("${settlement.hold-days:0}")
    private long holdDays;

    @Transactional
    public void processForMerchant(UUID merchantId, LocalDate settlementDate) {
        List<PaymentSettlementView> eligible = eligiblePayments(merchantId);
        if (eligible.isEmpty()) {
            return;
        }

        long captured = eligible.stream().mapToLong(PaymentSettlementView::settlementAmountUnits).sum();
        long refunded = eligible.stream().mapToLong(PaymentSettlementView::refundedAmountUnits).sum();
        String currency = eligible.getFirst().settlementCurrency();
        long debt = carryForwardService.outstanding(merchantId);
        long base = captured - refunded - debt;

        FeeSchedule schedule = feeScheduleResolver.forMerchant(merchantId);

        if (base <= 0) {
            carryForwardService.setOutstanding(merchantId, Math.negateExact(base), currency);
            settlementRepository.save(emptySettlement(merchantId, captured, refunded, debt, currency));
            log.info("No payable amount for merchantId={} debt={}", merchantId, Math.negateExact(base));
            return;
        }

        Money grossMoney = Money.of(captured, currency);
        Money baseMoney = Money.of(base, currency);
        Money fee = schedule.fee(grossMoney);
        Money gst = schedule.gst(fee);
        Money tds = schedule.tds(fee);
        Money reserve = schedule.reserve(baseMoney);
        long netUnits = base - fee.getAmountUnits() - gst.getAmountUnits()
                - tds.getAmountUnits() - reserve.getAmountUnits();

        if (netUnits <= 0) {
            carryForwardService.setOutstanding(merchantId, debt + Math.negateExact(netUnits), currency);
            settlementRepository.save(emptySettlement(merchantId, captured, refunded, debt, currency));
            log.info("Settlement shortfall for merchantId={} carried forward", merchantId);
            return;
        }

        Money net = Money.of(netUnits, currency);
        Settlement settlement = Settlement.builder()
                .merchantId(merchantId)
                .grossAmount(grossMoney)
                .refundAmount(Money.of(refunded, currency))
                .feeAmount(fee)
                .gstAmount(gst)
                .tdsAmount(tds)
                .reserveAmount(reserve)
                .carryForwardAmount(Money.of(debt, currency))
                .netAmount(net)
                .status(SettlementStatus.INITIATED)
                .build();
        settlementRepository.save(settlement);
        saveLinks(settlement, eligible);

        try {
            SettlementBankDetails bank = settlementIntegrationGateway.getSettlementBankDetails(merchantId);
            BankTransferResult transfer = bankTransferProcessor.initiate(settlement.getId(), merchantId,
                    net, bank.accountNumber(), bank.ifsc());

            settlementIntegrationGateway.postSettlementLedger(new SettlementLedgerRequest(
                    settlement.getId(), base, fee.getAmountUnits(), gst.getAmountUnits(),
                    tds.getAmountUnits(), reserve.getAmountUnits(), netUnits, currency));

            settlement.markTransferPending(transfer.registrationRef());
            settlementRepository.save(settlement);
            if (debt > 0) {
                carryForwardService.clear(merchantId);
            }
        } catch (Exception e) {
            log.error("Settlement failed for settlementId: {} date: {}",
                    settlement.getId(), settlementDate, e);
            settlement.markFailed("BANK_TRANSFER_FAILED");
            settlementRepository.save(settlement);
        }
    }

    @Transactional
    public void resolveTransfer(UUID settlementId, String errorCode, String errorDescription) {
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow(
                () -> new ResourceNotFoundException("Settlement", settlementId));

        if (settlement.getStatus() != SettlementStatus.TRANSFER_PENDING) {
            log.info("Settlement resolved, skipping for id: {}", settlement.getId());
            return;
        }

        if (errorCode == null) {
            settlement.markProcessed(LocalDateTime.now());
            settlementRepository.save(settlement);
            markPaymentsSettled(settlement);
            publish(settlement, "SETTLEMENT_PROCESSED");
        } else {
            settlement.markFailed(errorCode + " : " + errorDescription);
            settlementRepository.save(settlement);
            publish(settlement, "SETTLEMENT_FAILED");
        }
    }

    private List<PaymentSettlementView> eligiblePayments(UUID merchantId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(holdDays);
        return settlementIntegrationGateway.findUnsettledCaptured(merchantId).stream()
                .filter(p -> p.capturedAt() == null || p.capturedAt().isBefore(cutoff))
                .toList();
    }

    private void saveLinks(Settlement settlement, List<PaymentSettlementView> payments) {
        List<SettlementPayment> links = new ArrayList<>();
        for (PaymentSettlementView payment : payments) {
            links.add(SettlementPayment.builder()
                    .id(new SettlementPaymentId(settlement.getId(), payment.paymentId()))
                    .settlement(settlement)
                    .build());
        }
        settlementPaymentRepository.saveAll(links);
    }

    private void markPaymentsSettled(Settlement settlement) {
        List<UUID> paymentIds = settlementPaymentRepository.findBySettlement(settlement).stream()
                .map(SettlementPayment::getId)
                .map(SettlementPaymentId::getPaymentId)
                .toList();
        settlementIntegrationGateway.markSettled(paymentIds);
    }

    private void publish(Settlement settlement, String eventType) {
        outboxEventPublisher.publish(EventAggregateType.SETTLEMENT, settlement.getId(), eventType,
                Map.of("settlementId", settlement.getId().toString(),
                        "merchantId", settlement.getMerchantId().toString(),
                        "status", settlement.getStatus().name(),
                        "settlementAmount", settlement.getNetAmount().getAmountUnits(),
                        "settlementCurrency", settlement.getNetAmount().getCurrency()));
    }

    private static Settlement emptySettlement(UUID merchantId, long captured, long refunded,
                                              long debt, String currency) {
        Money zero = Money.of(0, currency);
        return Settlement.builder()
                .merchantId(merchantId)
                .grossAmount(Money.of(captured, currency))
                .refundAmount(Money.of(refunded, currency))
                .feeAmount(zero)
                .gstAmount(zero)
                .tdsAmount(zero)
                .reserveAmount(zero)
                .carryForwardAmount(Money.of(debt, currency))
                .netAmount(zero)
                .status(SettlementStatus.PROCESSED)
                .processedAt(LocalDateTime.now())
                .build();
    }
}
