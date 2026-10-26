package com.snehal.neopay.operations.payout;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import com.snehal.neopay.operations.settlement.CarryForwardService;
import com.snehal.neopay.operations.settlement.SettlementIntegrationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Missing-entity, replay and controller branches of the payout path. */
class PayoutCoverageTest {

    private PayoutRepository payoutRepository;
    private SettlementRepository settlementRepository;
    private SettlementIntegrationGateway gateway;
    private CarryForwardService carryForwardService;
    private PayoutService service;

    @BeforeEach
    void setUp() {
        payoutRepository = mock(PayoutRepository.class);
        settlementRepository = mock(SettlementRepository.class);
        gateway = mock(SettlementIntegrationGateway.class);
        carryForwardService = mock(CarryForwardService.class);
        service = new PayoutService(payoutRepository, settlementRepository, gateway,
                carryForwardService);
    }

    @Test
    void payoutCreationForMissingSettlementThrows() {
        UUID settlementId = UUID.randomUUID();
        when(payoutRepository.findBySettlementId(settlementId)).thenReturn(Optional.empty());
        when(settlementRepository.findById(settlementId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createForSettlement(settlementId));
    }

    @Test
    void returnedPayoutIsReusedWithoutAnotherLedgerPost() {
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .settlementId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(100))
                .status(PayoutStatus.RETURNED)
                .createdAt(LocalDateTime.now())
                .build();
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));

        assertSame(payout, service.markReturned(payout.getId(), "late"));
    }

    @Test
    void missingPayoutReturnThrows() {
        UUID payoutId = UUID.randomUUID();
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.markReturned(payoutId, "late"));
    }

    @Test
    void returningAPayoutReturnsTheSavedEntity() {
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .settlementId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(250))
                .status(PayoutStatus.SENT)
                .createdAt(LocalDateTime.now())
                .build();
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutRepository.save(payout)).thenReturn(payout);
        when(carryForwardService.outstanding(payout.getMerchantId())).thenReturn(10L);

        Payout result = service.markReturned(payout.getId(), "account closed");

        assertSame(payout, result);
        assertEquals(PayoutStatus.RETURNED, result.getStatus());
    }

    @Test
    void controllerDelegatesPayoutFileAndReturn() {
        UUID settlementId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(Money.inr(100))
                .createdAt(LocalDateTime.now())
                .build();
        PayoutService mocked = mock(PayoutService.class);
        when(mocked.payoutFile(settlementId)).thenReturn("csv");
        when(mocked.markReturned(payout.getId(), "reason")).thenReturn(payout);
        PayoutController controller = new PayoutController(mocked);

        assertEquals("csv", controller.payoutFile(settlementId));
        assertSame(payout, controller.markReturned(payout.getId(),
                new PayoutController.ReturnRequest("reason")));
    }

    @Test
    void payoutFileUsesTheCreatedPayout() {
        Settlement settlement = Settlement.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .netAmount(Money.inr(750))
                .build();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID())
                .settlementId(settlement.getId())
                .merchantId(settlement.getMerchantId())
                .amount(settlement.getNetAmount())
                .createdAt(LocalDateTime.now())
                .build();
        when(payoutRepository.findBySettlementId(settlement.getId()))
                .thenReturn(Optional.of(payout));

        String csv = service.payoutFile(settlement.getId());

        assertEquals(true, csv.contains("750"));
        assertEquals(true, csv.contains("INR"));
    }
}
