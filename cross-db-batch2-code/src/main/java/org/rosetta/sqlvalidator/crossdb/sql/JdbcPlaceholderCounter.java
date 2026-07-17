package org.rosetta.sqlvalidator.crossdb.sql;

/** Counts JDBC '?' placeholders while ignoring quoted text and comments. */
public final class JdbcPlaceholderCounter {

    public int count(String sql) {
        if (sql == null || sql.isBlank()) {
            return 0;
        }

        int count = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }

            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                }
                continue;
            }

            if (!singleQuote && !doubleQuote) {
                if (current == '-' && next == '-') {
                    lineComment = true;
                    index++;
                    continue;
                }
                if (current == '/' && next == '*') {
                    blockComment = true;
                    index++;
                    continue;
                }
            }

            if (!doubleQuote && current == '\'') {
                if (singleQuote && next == '\'') {
                    index++;
                    continue;
                }
                singleQuote = !singleQuote;
                continue;
            }

            if (!singleQuote && current == '"') {
                if (doubleQuote && next == '"') {
                    index++;
                    continue;
                }
                doubleQuote = !doubleQuote;
                continue;
            }

            if (!singleQuote && !doubleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }
}
