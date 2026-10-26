package com.snehal.neopay.operations.iso20022;

import com.snehal.neopay.common.iso20022.CreditTransfer;
import com.snehal.neopay.common.iso20022.Iso20022Codec;
import com.snehal.neopay.common.iso20022.StatementEntry;
import com.snehal.neopay.operations.recon.StatementRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Iso20022ControllerTest {

    private final Iso20022Controller controller = new Iso20022Controller();

    @Test
    void camt053OutputFeedsReconciliationRows() {
        String xml = Iso20022Codec.writeCamt053(List.of(
                new StatementEntry("BANK-REF-1", 10_000, "INR"),
                new StatementEntry("BANK-REF-2", 2_500, "INR")));

        List<StatementRow> rows = controller.parseCamt053(xml);

        assertEquals(List.of(
                new StatementRow("BANK-REF-1", 10_000, "INR"),
                new StatementRow("BANK-REF-2", 2_500, "INR")), rows);
    }

    @Test
    void pacs008BuildsAndParses() {
        CreditTransfer transfer = new CreditTransfer("E2E-9", 4_200, "INR", "D", "C");

        String xml = controller.buildPacs008(transfer);

        assertTrue(xml.contains("pacs.008.001.08"));
        assertEquals(transfer, controller.parsePacs008(xml));
    }

    @Test
    void pain001BuildsAndParses() {
        var instruction = new com.snehal.neopay.common.iso20022.PayoutInstruction(
                "P-9", 9_900, "INR", "Merchant");

        String xml = controller.buildPain001(List.of(instruction));

        assertEquals(List.of(instruction), controller.parsePain001(xml));
    }
}
