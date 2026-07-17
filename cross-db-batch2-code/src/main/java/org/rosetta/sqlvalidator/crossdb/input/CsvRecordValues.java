package org.rosetta.sqlvalidator.crossdb.input;

import org.apache.commons.csv.CSVRecord;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class CsvRecordValues {

    private CsvRecordValues() {
    }

    static String first(CSVRecord record, String... aliases) {
        Map<String, String> values = record.toMap();
        for (String alias : aliases) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().trim().toLowerCase(Locale.ROOT)
                        .equals(alias.trim().toLowerCase(Locale.ROOT))) {
                    String value = entry.getValue();
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        return "";
    }

    static boolean bool(CSVRecord record, boolean defaultValue, String... aliases) {
        String value = first(record, aliases);
        return value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    static int integer(CSVRecord record, int defaultValue, String... aliases) {
        String value = first(record, aliases);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid integer '" + value + "' for " + Arrays.toString(aliases),
                    exception
            );
        }
    }
}
