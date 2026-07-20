package org.rosetta.sqlvalidator.crossdb.normalization;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ResultSetNormalizer {

    private static final String CELL_SEPARATOR = "\u001F";
    private final CanonicalValueFormatter valueFormatter;

    public ResultSetNormalizer(CanonicalValueFormatter valueFormatter) {
        this.valueFormatter = valueFormatter;
    }

    public NormalizedResult read(ResultSet resultSet, int maxRows, boolean orderSensitive)
            throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        String columnSignature = buildColumnSignature(metadata, columnCount);

        List<String> rows = new ArrayList<>();
        int rowCount = 0;
        boolean tooLarge = false;

        while (resultSet.next()) {
            rowCount++;
            if (rowCount > maxRows) {
                tooLarge = true;
                break;
            }

            List<String> cells = new ArrayList<>(columnCount);
            for (int column = 1; column <= columnCount; column++) {
                cells.add(valueFormatter.format(
                        resultSet.getObject(column), metadata.getColumnType(column)));
            }
            rows.add(String.join(CELL_SEPARATOR, cells));
        }

        if (!orderSensitive) {
            Collections.sort(rows);
        }

        return new NormalizedResult(rowCount, columnSignature, rows, tooLarge);
    }

    private String buildColumnSignature(ResultSetMetaData metadata, int columnCount)
            throws SQLException {
        List<String> columns = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
            String label = metadata.getColumnLabel(column);
            if (label == null || label.isBlank()) {
                label = metadata.getColumnName(column);
            }
            columns.add(label.trim().toUpperCase(Locale.ROOT)
                    + ":" + valueFormatter.typeFamily(metadata.getColumnType(column)));
        }
        return String.join("|", columns);
    }
}
