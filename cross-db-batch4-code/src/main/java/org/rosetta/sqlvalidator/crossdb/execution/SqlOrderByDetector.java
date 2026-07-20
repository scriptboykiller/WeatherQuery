package org.rosetta.sqlvalidator.crossdb.execution;

import java.util.Locale;

public final class SqlOrderByDetector {

    public boolean hasTopLevelOrderBy(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        int depth = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;

        for (int index = 0; index < upper.length(); index++) {
            char current = upper.charAt(index);
            char next = index + 1 < upper.length() ? upper.charAt(index + 1) : '\0';

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
            if (singleQuote || doubleQuote) {
                continue;
            }

            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0 && upper.startsWith("ORDER", index)
                    && boundary(upper, index - 1) && boundary(upper, index + 5)) {
                int byIndex = index + 5;
                while (byIndex < upper.length()
                        && Character.isWhitespace(upper.charAt(byIndex))) {
                    byIndex++;
                }
                if (upper.startsWith("BY", byIndex) && boundary(upper, byIndex + 2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean boundary(String value, int index) {
        if (index < 0 || index >= value.length()) {
            return true;
        }
        char c = value.charAt(index);
        return !Character.isLetterOrDigit(c) && c != '_' && c != '$';
    }
}
