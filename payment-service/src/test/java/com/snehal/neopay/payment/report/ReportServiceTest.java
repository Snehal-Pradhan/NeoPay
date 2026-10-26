package com.snehal.neopay.payment.report;

import com.snehal.neopay.payment.ledger.LedgerAccount;
import com.snehal.neopay.payment.ledger.LedgerLine;
import com.snehal.neopay.payment.ledger.LedgerLineRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private final LedgerLineRepository repository = mock(LedgerLineRepository.class);
    private final ReportService service = new ReportService(repository);

    @Test
    void settlementReportAggregatesAndSortsByAccount() {
        when(repository.findForPeriod(any(), any())).thenReturn(List.of(
                line(LedgerAccount.MERCHANT_PAYABLE, 1_000, 0, "SETTLEMENT"),
                line(LedgerAccount.PLATFORM_FEE, 0, 20, "SETTLEMENT"),
                line(LedgerAccount.BANK_SETTLEMENT, 0, 976, "SETTLEMENT"),
                line(LedgerAccount.CUSTOMER_FUNDS, 1_000, 0, "PAYMENT")));

        String csv = service.settlementReport(LocalDate.of(2026, 10, 26));

        assertTrue(csv.startsWith("account,debitUnits,creditUnits\n"));
        assertTrue(csv.contains("BANK_SETTLEMENT,0,976"));
        assertTrue(csv.contains("PLATFORM_FEE,0,20"));
        assertTrue(csv.contains("MERCHANT_PAYABLE,1000,0"));
        assertFalse(csv.contains("CUSTOMER_FUNDS"), "payment postings must not be in the settlement report");
        assertTrue(csv.indexOf("BANK_SETTLEMENT") < csv.indexOf("PLATFORM_FEE"),
                "rows must be sorted by account name");
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() {
        when(repository.findForPeriod(any(), any())).thenReturn(List.of(
                line(LedgerAccount.GST_PAYABLE, 0, 36, "SETTLEMENT"),
                line(LedgerAccount.TDS_PAYABLE, 0, 20, "SETTLEMENT")));

        LocalDate date = LocalDate.of(2026, 10, 26);
        assertEquals(service.taxReport(date), service.taxReport(date));
    }

    @Test
    void taxReportOnlyIncludesTaxAccounts() {
        when(repository.findForPeriod(any(), any())).thenReturn(List.of(
                line(LedgerAccount.GST_PAYABLE, 0, 36, "SETTLEMENT"),
                line(LedgerAccount.TDS_PAYABLE, 0, 20, "SETTLEMENT"),
                line(LedgerAccount.PLATFORM_FEE, 0, 200, "SETTLEMENT")));

        String csv = service.taxReport(LocalDate.of(2026, 10, 26));

        assertTrue(csv.contains("GST_PAYABLE,0,36"));
        assertTrue(csv.contains("TDS_PAYABLE,0,20"));
        assertFalse(csv.contains("PLATFORM_FEE"));
    }

    private static LedgerLine line(LedgerAccount account, long debit, long credit, String referenceType) {
        return LedgerLine.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .account(account)
                .debitUnits(debit)
                .creditUnits(credit)
                .currency("INR")
                .referenceType(referenceType)
                .referenceId(UUID.randomUUID())
                .postedAt(LocalDateTime.now())
                .build();
    }
}
