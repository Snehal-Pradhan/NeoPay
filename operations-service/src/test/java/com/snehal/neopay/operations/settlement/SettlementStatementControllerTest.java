package com.snehal.neopay.operations.settlement;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementStatementControllerTest {

    @Test
    void statementContainsEveryComponent() {
        SettlementRepository repository = mock(SettlementRepository.class);
        SettlementStatementController controller = new SettlementStatementController(repository);

        Settlement settlement = Settlement.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .grossAmount(Money.inr(10_000))
                .refundAmount(Money.inr(0))
                .feeAmount(Money.inr(200))
                .gstAmount(Money.inr(36))
                .tdsAmount(Money.inr(20))
                .reserveAmount(Money.inr(500))
                .carryForwardAmount(Money.inr(0))
                .netAmount(Money.inr(9_244))
                .status(SettlementStatus.PROCESSED)
                .build();
        when(repository.findById(settlement.getId())).thenReturn(Optional.of(settlement));

        String csv = controller.statement(settlement.getId());

        assertTrue(csv.contains("gross,10000,INR"));
        assertTrue(csv.contains("fee,200,INR"));
        assertTrue(csv.contains("gst,36,INR"));
        assertTrue(csv.contains("tds,20,INR"));
        assertTrue(csv.contains("reserve,500,INR"));
        assertTrue(csv.contains("net,9244,INR"));
    }

    @Test
    void unknownSettlementThrows() {
        SettlementRepository repository = mock(SettlementRepository.class);
        SettlementStatementController controller = new SettlementStatementController(repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> controller.statement(id));
    }
}
