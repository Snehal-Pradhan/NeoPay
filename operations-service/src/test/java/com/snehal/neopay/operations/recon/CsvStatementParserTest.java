package com.snehal.neopay.operations.recon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    @Test
    void parsesRowsAndSkipsHeaderAndBlankLines() {
        String csv = """
                reference,amountUnits,currency

                A,1000,inr
                B,2500,INR
                """;

        List<StatementRow> rows = parser.parse(csv);

        assertEquals(2, rows.size());
        assertEquals(new StatementRow("A", 1000, "INR"), rows.get(0));
        assertEquals(new StatementRow("B", 2500, "INR"), rows.get(1));
    }

    @Test
    void emptyInputYieldsNoRows() {
        assertEquals(List.of(), parser.parse(null));
        assertEquals(List.of(), parser.parse("   "));
    }

    @Test
    void wrongColumnCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("A,1000"));
    }

    @Test
    void nonNumericAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("A,ten,INR"));
    }

    @Test
    void emptyReferenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(",1000,INR"));
    }
}
