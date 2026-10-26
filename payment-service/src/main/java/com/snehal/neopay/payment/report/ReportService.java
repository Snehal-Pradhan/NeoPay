package com.snehal.neopay.payment.report;

import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerLine;
import com.snehal.neopay.payment.ledger.LedgerLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ledger-derived reports. Output is deterministic: rows are sorted by account name,
 * there are no timestamps, and the same date range always produces identical bytes.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final LedgerLineRepository ledgerLineRepository;

    @Transactional(readOnly = true)
    public String settlementReport(LocalDate date) {
        Period period = Period.of(date);
        Map<LedgerAccount, long[]> totals = new EnumMap<>(LedgerAccount.class);
        for (LedgerLine line : ledgerLineRepository.findForPeriod(period.from(), period.to())) {
            if (!"SETTLEMENT".equals(line.getReferenceType())) {
                continue;
            }
            accumulate(totals, line);
        }
        return render(totals);
    }

    @Transactional(readOnly = true)
    public String taxReport(LocalDate date) {
        Period period = Period.of(date);
        Map<LedgerAccount, long[]> totals = new EnumMap<>(LedgerAccount.class);
        for (LedgerLine line : ledgerLineRepository.findForPeriod(period.from(), period.to())) {
            if (line.getAccount() != LedgerAccount.GST_PAYABLE
                    && line.getAccount() != LedgerAccount.TDS_PAYABLE) {
                continue;
            }
            accumulate(totals, line);
        }
        return render(totals);
    }

    private static void accumulate(Map<LedgerAccount, long[]> totals, LedgerLine line) {
        long[] entry = totals.computeIfAbsent(line.getAccount(), ignored -> new long[2]);
        entry[0] = Math.addExact(entry[0], line.getDebitUnits());
        entry[1] = Math.addExact(entry[1], line.getCreditUnits());
    }

    private static String render(Map<LedgerAccount, long[]> totals) {
        StringBuilder csv = new StringBuilder("account,debitUnits,creditUnits\n");
        totals.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> csv.append(entry.getKey().name()).append(',')
                        .append(entry.getValue()[0]).append(',')
                        .append(entry.getValue()[1]).append('\n'));
        return csv.toString();
    }

    private record Period(LocalDateTime from, LocalDateTime to) {
        static Period of(LocalDate date) {
            return new Period(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        }
    }
}
