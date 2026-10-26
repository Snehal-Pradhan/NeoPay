package com.snehal.neopay.common.iso20022;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Iso20022CodecTest {

    @Test
    void pacs008RoundTrip() {
        CreditTransfer original = new CreditTransfer("E2E-1", 12_500, "INR", "Debtor Ltd", "Creditor Ltd");

        String xml = Iso20022Codec.writePacs008(original);

        assertEquals(original, Iso20022Codec.parsePacs008(xml));
    }

    @Test
    void camt053RoundTrip() {
        List<StatementEntry> entries = List.of(
                new StatementEntry("REF-A", 10_000, "INR"),
                new StatementEntry("REF-B", 2_500, "INR"));

        String xml = Iso20022Codec.writeCamt053(entries);

        assertEquals(entries, Iso20022Codec.parseCamt053(xml));
    }

    @Test
    void pain001RoundTrip() {
        List<PayoutInstruction> instructions = List.of(
                new PayoutInstruction("P-1", 50_000, "INR", "Merchant One"),
                new PayoutInstruction("P-2", 75_000, "INR", "Merchant Two"));

        String xml = Iso20022Codec.writePain001(instructions);

        assertEquals(instructions, Iso20022Codec.parsePain001(xml));
    }

    @Test
    void malformedDocumentIsRejected() {
        assertThrows(Iso20022Exception.class,
                () -> Iso20022Codec.parsePacs008("<Document><bad/></Document>"));
    }

    @Test
    void doctypeIsRejected() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf><CdtTrfTxInf>
                    <PmtId><EndToEndId>&xxe;</EndToEndId></PmtId>
                    <IntrBkSttlmAmt Ccy="INR">1.00</IntrBkSttlmAmt>
                    <Dbtr><Nm>D</Nm></Dbtr><Cdtr><Nm>C</Nm></Cdtr>
                  </CdtTrfTxInf></FIToFICstmrCdtTrf>
                </Document>
                """;

        assertThrows(Iso20022Exception.class, () -> Iso20022Codec.parsePacs008(xxe));
    }

    @Test
    void unknownCurrencyIsRejected() {
        String xml = Iso20022Codec.writePacs008(
                new CreditTransfer("E2E-2", 100, "INR", "D", "C"))
                .replace("Ccy=\"INR\"", "Ccy=\"XYZ\"");

        assertThrows(Iso20022Exception.class, () -> Iso20022Codec.parsePacs008(xml));
    }

    @Test
    void amountWithTooManyDecimalsIsRejected() {
        String xml = Iso20022Codec.writePacs008(
                new CreditTransfer("E2E-3", 100, "INR", "D", "C"))
                .replace(">1.00<", ">1.001<");

        assertThrows(Iso20022Exception.class, () -> Iso20022Codec.parsePacs008(xml));
    }
}
