package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.dto.PaymentSettlementView;
import com.snehal.neopay.common.dto.SettlementBankDetails;
import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.common.money.FeeSchedule;
import com.snehal.neopay.operations.entity.MerchantFeeSchedule;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.outbox.OutboxEventPublisher;
import com.snehal.neopay.operations.repository.MerchantFeeScheduleRepository;
import com.snehal.neopay.operations.repository.SettlementPaymentRepository;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.dto.BankTransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementCarryForwardTest {

    private final UUID merchantId = UUID.randomUUID();
    private SettlementRepository settlementRepository;
    private SettlementPaymentRepository linkRepository;
    private BankTransferProcessor bank;
    private SettlementIntegrationGateway gateway;
    private CarryForwardService carryForward;
    private FeeScheduleResolver resolver;
    private SettlementTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        settlementRepository = mock(SettlementRepository.class);
        linkRepository = mock(SettlementPaymentRepository.class);
        bank = mock(BankTransferProcessor.class);
        gateway = mock(SettlementIntegrationGateway.class);
        carryForward = mock(CarryForwardService.class);
        resolver = mock(FeeScheduleResolver.class);
        when(resolver.forMerchant(merchantId)).thenReturn(FeeSchedule.standardIndia());
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(i -> i.getArgument(0));
        executor = new SettlementTransactionExecutor(settlementRepository, linkRepository, bank,
                mock(OutboxEventPublisher.class), gateway, carryForward, resolver);
        ReflectionTestUtils.setField(executor, "holdDays", 0L);
    }

    @Test
    void priorDebtReducesPayableAndIsCleared() {
        when(carryForward.outstanding(merchantId)).thenReturn(1_000L);
        eligible(view(10_000, null));
        when(gateway.getSettlementBankDetails(merchantId))
                .thenReturn(new SettlementBankDetails("acct", "ifsc", "holder"));
        when(bank.initiate(any(), eq(merchantId), any(), any(), any()))
                .thenReturn(new BankTransferResult("ref"));

        executor.processForMerchant(merchantId, LocalDate.now());

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Settlement saved = captor.getAllValues().getLast();

        assertEquals(1_000, saved.getCarryForwardAmount().getAmountUnits());
        assertEquals(200, saved.getFeeAmount().getAmountUnits());
        assertEquals(36, saved.getGstAmount().getAmountUnits());
        assertEquals(20, saved.getTdsAmount().getAmountUnits());
        assertEquals(450, saved.getReserveAmount().getAmountUnits());
        assertEquals(8_294, saved.getNetAmount().getAmountUnits());
        verify(carryForward).clear(merchantId);
    }

    @Test
    void debtAboveCapturedCarriesForwardWithoutTransfer() {
        when(carryForward.outstanding(merchantId)).thenReturn(20_000L);
        eligible(view(10_000, null));

        executor.processForMerchant(merchantId, LocalDate.now());

        verify(bank, never()).initiate(any(), any(), any(), any(), any());
        verify(carryForward).setOutstanding(merchantId, 10_000, "INR");

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        assertEquals(SettlementStatus.PROCESSED, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getNetAmount().getAmountUnits());
    }

    @Test
    void paymentsInsideHoldPeriodAreExcluded() {
        ReflectionTestUtils.setField(executor, "holdDays", 1L);
        eligible(view(10_000, LocalDateTime.now()));

        executor.processForMerchant(merchantId, LocalDate.now());

        verify(bank, never()).initiate(any(), any(), any(), any(), any());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void merchantScheduleOverridesDefault() {
        MerchantFeeScheduleRepository repository = mock(MerchantFeeScheduleRepository.class);
        when(repository.findById(merchantId)).thenReturn(Optional.of(MerchantFeeSchedule.builder()
                .merchantId(merchantId).feeBps(100).gstBps(1_800).tdsBps(0).reserveBps(0).build()));

        FeeSchedule schedule = new FeeScheduleResolver(repository).forMerchant(merchantId);

        assertEquals(100, schedule.feeBps());
        assertEquals(0, schedule.tdsBps());
    }

    @Test
    void defaultScheduleIsUsedWhenMerchantHasNone() {
        MerchantFeeScheduleRepository repository = mock(MerchantFeeScheduleRepository.class);
        when(repository.findById(merchantId)).thenReturn(Optional.empty());

        FeeSchedule schedule = new FeeScheduleResolver(repository).forMerchant(merchantId);

        assertEquals(FeeSchedule.standardIndia(), schedule);
    }

    private void eligible(PaymentSettlementView view) {
        when(gateway.findUnsettledCaptured(merchantId)).thenReturn(List.of(view));
    }

    private static PaymentSettlementView view(int amount, LocalDateTime capturedAt) {
        return new PaymentSettlementView(UUID.randomUUID(), amount, 0, "INR", capturedAt, amount, "INR");
    }
}
