package com.snehal.neopay.operations.recon;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a CSV statement with the columns {@code reference,amountUnits,currency}.
 * A header row is skipped when the second column is not numeric.
 */
@Component
public class CsvStatementParser {

    public List<StatementRow> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<StatementRow> rows = new ArrayList<>();
        String[] lines = csv.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            StatementRow row = parseLine(line, i + 1);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private StatementRow parseLine(String line, int lineNumber) {
        String[] parts = split(line, lineNumber);
        if (lineNumber == 1 && isHeader(parts[0].trim())) {
            return null;
        }
        String reference = requireValue(parts[0], lineNumber);
        String currency = requireValue(parts[2], lineNumber).toUpperCase();
        long amount = parseAmount(parts[1], lineNumber);
        return new StatementRow(reference, amount, currency);
    }

    private static String[] split(String line, int lineNumber) {
        String[] parts = line.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Statement line " + lineNumber + " must have 3 columns");
        }
        return parts;
    }

    private static String requireValue(String raw, int lineNumber) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Statement line " + lineNumber + " has an empty field");
        }
        return value;
    }

    private static long parseAmount(String raw, int lineNumber) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException invalidAmount) {
            throw new IllegalArgumentException(
                    "Statement line " + lineNumber + " has a non-numeric amount");
        }
    }

    private static boolean isHeader(String firstColumn) {
        return "reference".equalsIgnoreCase(firstColumn) || "ref".equalsIgnoreCase(firstColumn);
    }
}
