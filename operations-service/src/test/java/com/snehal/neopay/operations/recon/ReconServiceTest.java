package com.snehal.neopay.operations.recon;

import com.snehal.neopay.common.entity.Money;
import com.snehal.neopay.common.enums.SettlementStatus;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconServiceTest {

    private static final String PSP = """
            reference,amountUnits,currency
            A,1000,INR
            B,1900,INR
            C,3000,INR
            D,4000,INR
            D,4000,INR
            E,5000,INR
            """;

    private static final String BANK = """
            reference,amountUnits,currency
            B,2000,INR
            C,3000,INR
            D,4000,INR
            E,5000,INR
            """;

    private SettlementRepository settlementRepository;
    private ReconRunRepository runRepository;
    private ReconBreakRepository breakRepository;
    private ReconService service;

    @BeforeEach
    void setUp() {
        settlementRepository = mock(SettlementRepository.class);
        runRepository = mock(ReconRunRepository.class);
        breakRepository = mock(ReconBreakRepository.class);
        service = new ReconService(settlementRepository, runRepository, breakRepository,
                new CsvStatementParser(), new SimpleMeterRegistry());

        when(runRepository.save(any(ReconRun.class))).thenAnswer(invocation -> {
            ReconRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
            return run;
        });
        when(settlementRepository.findAll()).thenReturn(List.of(
                settlement("A", 1000),
                settlement("B", 2000),
                settlement("E", 5000)));
    }

    @Test
    void fourSeededDefectsProduceFourClassifiedBreaks() {
        ReconRun run = service.run(PSP, BANK);

        assertEquals(4, run.getBreakCount());
        assertEquals(3, run.getInternalCount());
        assertEquals(6, run.getPspCount());
        assertEquals(4, run.getBankCount());

        ArgumentCaptor<List<ReconBreak>> captor = ArgumentCaptor.forClass(List.class);
        verify(breakRepository).saveAll(captor.capture());
        Set<ReconBreakType> types = captor.getValue().stream()
                .map(ReconBreak::getType).collect(Collectors.toSet());

        assertEquals(Set.of(ReconBreakType.MISSING_BANK, ReconBreakType.AMOUNT_MISMATCH_PSP,
                ReconBreakType.MISSING_INTERNAL, ReconBreakType.DUPLICATE), types);
    }

    @Test
    void cleanStatementProducesNoBreaks() {
        String psp = "reference,amountUnits,currency\nA,1000,INR\nB,2000,INR\nE,5000,INR\n";
        String bank = "reference,amountUnits,currency\nA,1000,INR\nB,2000,INR\nE,5000,INR\n";

        ReconRun run = service.run(psp, bank);

        assertEquals(0, run.getBreakCount());
    }

    @Test
    void duplicateOnBankSideIsDetected() {
        when(settlementRepository.findAll()).thenReturn(List.of(settlement("A", 1000)));
        String psp = "reference,amountUnits,currency\nA,1000,INR\n";
        String bank = "reference,amountUnits,currency\nA,1000,INR\nA,1000,INR\n";

        ReconRun run = service.run(psp, bank);

        assertEquals(1, run.getBreakCount());
    }

    @Test
    void unknownRunBreaksThrow() {
        when(runRepository.existsById(any())).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.snehal.neopay.common.exception.ResourceNotFoundException.class,
                () -> service.breaks(UUID.randomUUID()));
    }

    @Test
    void resolvingBreakMarksItResolved() {
        ReconBreak reconBreak = ReconBreak.builder()
                .id(UUID.randomUUID())
                .type(ReconBreakType.MISSING_BANK)
                .reference("A")
                .status(ReconBreakStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
        when(breakRepository.findById(reconBreak.getId())).thenReturn(java.util.Optional.of(reconBreak));
        when(breakRepository.save(reconBreak)).thenReturn(reconBreak);

        ReconBreak resolved = service.resolveBreak(reconBreak.getId(), "bank confirmed");

        assertEquals(ReconBreakStatus.RESOLVED, resolved.getStatus());
        assertEquals("bank confirmed", resolved.getNote());
    }

    private static Settlement settlement(String reference, int netUnits) {
        return Settlement.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .netAmount(Money.inr(netUnits))
                .bankReference(reference)
                .status(SettlementStatus.PROCESSED)
                .build();
    }
}
