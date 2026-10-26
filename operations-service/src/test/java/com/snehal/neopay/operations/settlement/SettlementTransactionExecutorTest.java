package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.money.FeeSchedule;
import com.snehal.neopay.common.enums.EventAggregateType;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.entity.SettlementPayment;
import com.snehal.neopay.operations.entity.SettlementPaymentId;
import com.snehal.neopay.operations.outbox.OutboxEventPublisher;
import com.snehal.neopay.operations.repository.SettlementPaymentRepository;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.dto.BankTransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementTransactionExecutorTest {

    private SettlementRepository settlementRepository;
    private SettlementPaymentRepository settlementPaymentRepository;
    private BankTransferProcessor bankTransferProcessor;
    private OutboxEventPublisher outboxEventPublisher;
    private SettlementIntegrationGateway gateway;
    private SettlementTransactionExecutor executor;

    private CarryForwardService carryForwardService;
    private FeeScheduleResolver feeScheduleResolver;

    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settlementRepository = mock(SettlementRepository.class);
        settlementPaymentRepository = mock(SettlementPaymentRepository.class);
        bankTransferProcessor = mock(BankTransferProcessor.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        gateway = mock(SettlementIntegrationGateway.class);
        carryForwardService = mock(CarryForwardService.class);
        feeScheduleResolver = mock(FeeScheduleResolver.class);
        when(feeScheduleResolver.forMerchant(merchantId)).thenReturn(FeeSchedule.standardIndia());
        executor = new SettlementTransactionExecutor(settlementRepository,
                settlementPaymentRepository, bankTransferProcessor, outboxEventPublisher,
                gateway, carryForwardService, feeScheduleResolver);
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void noPaymentsMeansNoSettlement() {
        when(gateway.findUnsettledCaptured(merchantId)).thenReturn(List.of());

        executor.processForMerchant(merchantId, LocalDate.now());

        verify(settlementRepository, never()).save(any());
    }

    @Test
    void settlementComputesFeeGstAndNet() {
        when(gateway.findUnsettledCaptured(merchantId))
                .thenReturn(List.of(new PaymentSettlementView(UUID.randomUUID(), 10_000, 0, "INR", null, 10_000, "INR")));
        when(gateway.getSettlementBankDetails(merchantId))
                .thenReturn(new SettlementBankDetails("acct", "ifsc", "holder"));
        when(bankTransferProcessor.initiate(any(), eq(merchantId), any(), eq("acct"), eq("ifsc")))
                .thenReturn(new BankTransferResult("ref-1"));

        executor.processForMerchant(merchantId, LocalDate.now());

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Settlement saved = captor.getAllValues().getLast();

        assertEquals(10_000, saved.getGrossAmount().getAmountUnits());
        assertEquals(200, saved.getFeeAmount().getAmountUnits());
        assertEquals(36, saved.getGstAmount().getAmountUnits());
        assertEquals(20, saved.getTdsAmount().getAmountUnits());
        assertEquals(500, saved.getReserveAmount().getAmountUnits());
        assertEquals(9_244, saved.getNetAmount().getAmountUnits());
        assertEquals(SettlementStatus.TRANSFER_PENDING, saved.getStatus());
        assertEquals("ref-1", saved.getBankReference());
        verify(gateway).postSettlementLedger(any());
    }

    @Test
    void bankFailureMarksSettlementFailed() {
        when(gateway.findUnsettledCaptured(merchantId))
                .thenReturn(List.of(new PaymentSettlementView(UUID.randomUUID(), 5_000, 0, "INR", null, 5_000, "INR")));
        when(gateway.getSettlementBankDetails(merchantId))
                .thenReturn(new SettlementBankDetails("acct", "ifsc", "holder"));
        when(bankTransferProcessor.initiate(any(), any(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("bank down"));

        executor.processForMerchant(merchantId, LocalDate.now());

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertEquals(SettlementStatus.FAILED, captor.getAllValues().getLast().getStatus());
    }

    @Test
    void resolveSuccessMarksSettledAndPublishes() {
        UUID settlementId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Settlement settlement = Settlement.builder()
                .id(settlementId)
                .merchantId(merchantId)
                .grossAmount(com.snehal.neopay.common.entity.Money.inr(1_000))
                .feeAmount(com.snehal.neopay.common.entity.Money.inr(20))
                .gstAmount(com.snehal.neopay.common.entity.Money.inr(4))
                .netAmount(com.snehal.neopay.common.entity.Money.inr(976))
                .status(SettlementStatus.TRANSFER_PENDING)
                .build();
        SettlementPayment link = SettlementPayment.builder()
                .id(new SettlementPaymentId(settlementId, paymentId))
                .settlement(settlement)
                .build();

        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));
        when(settlementPaymentRepository.findBySettlement(settlement)).thenReturn(List.of(link));

        executor.resolveTransfer(settlementId, null, null);

        assertEquals(SettlementStatus.PROCESSED, settlement.getStatus());
        verify(gateway).markSettled(List.of(paymentId));
        verify(outboxEventPublisher).publish(eq(EventAggregateType.SETTLEMENT), eq(settlementId),
                eq("SETTLEMENT_PROCESSED"), any(Map.class));
    }

    @Test
    void resolveFailureRecordsReason() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder()
                .id(settlementId)
                .merchantId(merchantId)
                .grossAmount(com.snehal.neopay.common.entity.Money.inr(1_000))
                .feeAmount(com.snehal.neopay.common.entity.Money.inr(20))
                .gstAmount(com.snehal.neopay.common.entity.Money.inr(4))
                .netAmount(com.snehal.neopay.common.entity.Money.inr(976))
                .status(SettlementStatus.TRANSFER_PENDING)
                .build();

        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

        executor.resolveTransfer(settlementId, "BANK_ERROR", "declined");

        assertEquals(SettlementStatus.FAILED, settlement.getStatus());
        assertEquals("BANK_ERROR : declined", settlement.getFailureReason());
        verify(gateway, never()).markSettled(any());
    }

    @Test
    void alreadyResolvedSettlementIsIgnored() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder()
                .id(settlementId)
                .status(SettlementStatus.PROCESSED)
                .build();

        when(settlementRepository.findById(settlementId)).thenReturn(Optional.of(settlement));

        executor.resolveTransfer(settlementId, null, null);

        verify(settlementRepository, never()).save(any());
        verify(gateway, never()).markSettled(any());
    }
}
