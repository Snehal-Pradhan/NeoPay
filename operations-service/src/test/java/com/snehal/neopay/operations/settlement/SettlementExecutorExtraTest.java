package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.outbox.OutboxEventPublisher;
import com.snehal.neopay.operations.repository.SettlementPaymentRepository;
import com.snehal.neopay.operations.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementExecutorExtraTest {

    private SettlementRepository repository;
    private SettlementTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        repository = mock(SettlementRepository.class);
        executor = new SettlementTransactionExecutor(repository,
                mock(SettlementPaymentRepository.class), mock(BankTransferProcessor.class),
                mock(OutboxEventPublisher.class), mock(SettlementIntegrationGateway.class),
                mock(CarryForwardService.class), mock(SettlementFeeCalculator.class));
    }

    @Test
    void resolvingMissingSettlementThrows() {
        UUID settlementId = UUID.randomUUID();
        when(repository.findById(settlementId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> executor.resolveTransfer(settlementId, null, null));
    }

    @Test
    void resolvedSettlementIsNotProcessedTwice() {
        UUID settlementId = UUID.randomUUID();
        Settlement settlement = Settlement.builder()
                .id(settlementId)
                .merchantId(UUID.randomUUID())
                .netAmount(Money.inr(10))
                .status(SettlementStatus.PROCESSED)
                .build();
        when(repository.findById(settlementId)).thenReturn(Optional.of(settlement));

        executor.resolveTransfer(settlementId, null, null);

        verify(repository, never()).save(settlement);
    }
}
