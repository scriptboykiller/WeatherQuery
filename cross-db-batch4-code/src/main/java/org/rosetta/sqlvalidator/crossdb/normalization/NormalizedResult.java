package org.rosetta.sqlvalidator.crossdb.normalization;

import java.util.List;

public record NormalizedResult(
        int rowCount,
        String columnSignature,
        List<String> normalizedRows,
        boolean resultTooLarge
) {
    public NormalizedResult {
        normalizedRows = normalizedRows == null ? List.of() : List.copyOf(normalizedRows);
    }
}
