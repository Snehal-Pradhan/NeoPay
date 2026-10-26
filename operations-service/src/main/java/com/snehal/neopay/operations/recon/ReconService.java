package com.snehal.neopay.operations.recon;

import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.operations.entity.Settlement;
import com.snehal.neopay.operations.repository.SettlementRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Three-way reconciliation: internal settlement records, PSP statement and bank
 * statement, matched on reference.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconService {

    private final SettlementRepository settlementRepository;
    private final ReconRunRepository runRepository;
    private final ReconBreakRepository breakRepository;
    private final CsvStatementParser parser;
    private final MeterRegistry registry;

    @Transactional
    public ReconRun run(String pspCsv, String bankCsv) {
        List<StatementRow> psp = parser.parse(pspCsv);
        List<StatementRow> bank = parser.parse(bankCsv);
        Map<String, Long> internal = internalRows();
        Map<String, List<StatementRow>> pspByRef = group(psp);
        Map<String, List<StatementRow>> bankByRef = group(bank);

        Set<String> references = new LinkedHashSet<>();
        references.addAll(internal.keySet());
        references.addAll(pspByRef.keySet());
        references.addAll(bankByRef.keySet());

        List<BreakCandidate> candidates = new ArrayList<>();
        for (String reference : references) {
            ReconBreakType type = classify(reference, internal, pspByRef, bankByRef);
            if (type != null) {
                candidates.add(candidate(type, reference, internal, pspByRef, bankByRef));
            }
        }

        ReconRun run = runRepository.save(ReconRun.builder()
                .internalCount(internal.size())
                .pspCount(psp.size())
                .bankCount(bank.size())
                .breakCount(candidates.size())
                .createdAt(LocalDateTime.now())
                .status(ReconRunStatus.COMPLETED)
                .build());

        List<ReconBreak> breaks = candidates.stream().map(c -> toEntity(run.getId(), c)).toList();
        breakRepository.saveAll(breaks);
        candidates.forEach(c -> counter(c.type()).increment());

        log.info("Reconciliation {} completed: internal={} psp={} bank={} breaks={}",
                run.getId(), internal.size(), psp.size(), bank.size(), candidates.size());
        return run;
    }

    @Transactional(readOnly = true)
    public List<ReconBreak> breaks(UUID runId) {
        if (!runRepository.existsById(runId)) {
            throw new ResourceNotFoundException("ReconRun", runId);
        }
        return breakRepository.findByRunIdOrderByCreatedAtAsc(runId);
    }

    @Transactional
    public ReconBreak resolveBreak(UUID breakId, String note) {
        ReconBreak reconBreak = breakRepository.findById(breakId)
                .orElseThrow(() -> new ResourceNotFoundException("ReconBreak", breakId));
        reconBreak.resolve(note);
        return breakRepository.save(reconBreak);
    }

    private Map<String, Long> internalRows() {
        Map<String, Long> rows = new HashMap<>();
        for (Settlement settlement : settlementRepository.findAll()) {
            if (settlement.getBankReference() != null && settlement.getNetAmount() != null) {
                rows.put(settlement.getBankReference(), (long) settlement.getNetAmount().getAmountUnits());
            }
        }
        return rows;
    }

    private static Map<String, List<StatementRow>> group(List<StatementRow> rows) {
        Map<String, List<StatementRow>> grouped = new HashMap<>();
        for (StatementRow row : rows) {
            grouped.computeIfAbsent(row.reference(), ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private static ReconBreakType classify(String reference, Map<String, Long> internal,
                                           Map<String, List<StatementRow>> psp,
                                           Map<String, List<StatementRow>> bank) {
        List<StatementRow> pspRows = psp.getOrDefault(reference, List.of());
        List<StatementRow> bankRows = bank.getOrDefault(reference, List.of());

        if (pspRows.size() > 1 || bankRows.size() > 1) {
            return ReconBreakType.DUPLICATE;
        }
        if (!internal.containsKey(reference)) {
            return ReconBreakType.MISSING_INTERNAL;
        }
        if (pspRows.isEmpty()) {
            return ReconBreakType.MISSING_PSP;
        }
        if (bankRows.isEmpty()) {
            return ReconBreakType.MISSING_BANK;
        }
        if (currencyMismatch(pspRows.getFirst(), bankRows.getFirst())) {
            return ReconBreakType.CURRENCY_MISMATCH;
        }
        long internalAmount = internal.get(reference);
        if (pspRows.getFirst().amountUnits() != internalAmount) {
            return ReconBreakType.AMOUNT_MISMATCH_PSP;
        }
        if (bankRows.getFirst().amountUnits() != internalAmount) {
            return ReconBreakType.AMOUNT_MISMATCH_BANK;
        }
        return null;
    }

    private static boolean currencyMismatch(StatementRow psp, StatementRow bank) {
        return !psp.currency().equals(bank.currency());
    }

    private static BreakCandidate candidate(ReconBreakType type, String reference,
                                            Map<String, Long> internal,
                                            Map<String, List<StatementRow>> psp,
                                            Map<String, List<StatementRow>> bank) {
        List<StatementRow> pspRows = psp.getOrDefault(reference, List.of());
        List<StatementRow> bankRows = bank.getOrDefault(reference, List.of());
        Long pspAmount = pspRows.isEmpty() ? null : pspRows.getFirst().amountUnits();
        Long bankAmount = bankRows.isEmpty() ? null : bankRows.getFirst().amountUnits();
        String currency = pspRows.isEmpty()
                ? (bankRows.isEmpty() ? null : bankRows.getFirst().currency())
                : pspRows.getFirst().currency();
        return new BreakCandidate(type, reference,
                new Amounts(internal.get(reference), pspAmount, bankAmount, currency));
    }

    private static ReconBreak toEntity(UUID runId, BreakCandidate candidate) {
        Amounts amounts = candidate.amounts();
        return ReconBreak.builder()
                .runId(runId)
                .type(candidate.type())
                .reference(candidate.reference())
                .internalAmount(amounts.internal())
                .pspAmount(amounts.psp())
                .bankAmount(amounts.bank())
                .currency(amounts.currency())
                .status(ReconBreakStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Counter counter(ReconBreakType type) {
        return Counter.builder("recon_breaks_total").tag("type", type.name()).register(registry);
    }

    private record Amounts(Long internal, Long psp, Long bank, String currency) {
    }

    private record BreakCandidate(ReconBreakType type, String reference, Amounts amounts) {
    }
}
