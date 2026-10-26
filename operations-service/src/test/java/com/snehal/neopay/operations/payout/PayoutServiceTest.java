package com.snehal.neopay.operations.payout;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.CarryForwardService;
import com.snehal.neopay.operations.settlement.SettlementIntegrationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutServiceTest {

    private PayoutRepository payoutRepository;
    private SettlementRepository settlementRepository;
    private SettlementIntegrationGateway gateway;
    private CarryForwardService carryForward;
    private PayoutService service;

    @BeforeEach
    void setUp() {
        payoutRepository = mock(PayoutRepository.class);
        settlementRepository = mock(SettlementRepository.class);
        gateway = mock(SettlementIntegrationGateway.class);
        carryForward = mock(CarryForwardService.class);
        service = new PayoutService(payoutRepository, settlementRepository, gateway, carryForward);

        when(payoutRepository.save(any(Payout.class))).thenAnswer(invocation -> {
            Payout payout = invocation.getArgument(0);
            if (payout.getId() == null) {
                ReflectionTestUtils.setField(payout, "id", UUID.randomUUID());
            }
            return payout;
        });
    }

    @Test
    void createsPayoutFromSettlementAndMarksSent() {
        Settlement settlement = settlement();
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));

        Payout payout = service.createForSettlement(settlement.getId());

        assertEquals(PayoutStatus.SENT, payout.getStatus());
        assertEquals("NACH-" + settlement.getId(), payout.getFileReference());
        assertEquals(settlement.getNetAmount(), payout.getAmount());
    }

    @Test
    void existingPayoutIsReused() {
        UUID settlementId = UUID.randomUUID();
        Payout existing = Payout.builder()
                .id(UUID.randomUUID())
                .settlementId(settlementId)
                .amount(Money.inr(100))
                .status(PayoutStatus.SENT)
                .build();
        when(payoutRepository.findBySettlementId(settlementId)).thenReturn(Optional.of(existing));

        assertEquals(existing, service.createForSettlement(settlementId));
        verify(settlementRepository, never()).findById(any());
    }

    @Test
    void payoutFileContainsOneRow() {
        Settlement settlement = settlement();
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));

        String csv = service.payoutFile(settlement.getId());

        assertTrue(csv.startsWith("settlementId,merchantId,amountUnits,currency"));
        assertTrue(csv.contains(settlement.getId().toString()));
        assertTrue(csv.contains(",976,INR"));
    }

    @Test
    void returnReversesLedgerAndReopensCarryForward() {
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .settlementId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(500))
                .status(PayoutStatus.SENT)
                .build();
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(carryForward.outstanding(payout.getMerchantId())).thenReturn(100L);

        Payout returned = service.markReturned(payout.getId(), "account closed");

        assertEquals(PayoutStatus.RETURNED, returned.getStatus());
        assertEquals("account closed", returned.getReturnReason());
        verify(gateway).postPayoutReturnLedger(any());
        verify(carryForward).setOutstanding(payout.getMerchantId(), 600L, "INR");
    }

    @Test
    void alreadyReturnedIsNoOp() {
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(500))
                .status(PayoutStatus.RETURNED)
                .build();
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));

        service.markReturned(payout.getId(), "again");

        verify(gateway, never()).postPayoutReturnLedger(any());
        verify(carryForward, never()).setOutstanding(any(), any(Long.class), any());
    }

    private static Settlement settlement() {
        return Settlement.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .grossAmount(Money.inr(1_000))
                .refundAmount(Money.inr(0))
                .feeAmount(Money.inr(20))
                .gstAmount(Money.inr(4))
                .tdsAmount(Money.inr(0))
                .reserveAmount(Money.inr(0))
                .carryForwardAmount(Money.inr(0))
                .netAmount(Money.inr(976))
                .status(SettlementStatus.TRANSFER_PENDING)
                .build();
    }
}
