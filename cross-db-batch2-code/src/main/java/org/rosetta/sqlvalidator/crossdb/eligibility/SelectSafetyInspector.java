package org.rosetta.sqlvalidator.crossdb.eligibility;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative static SELECT inspection. It never executes SQL. */
public final class SelectSafetyInspector {

    private static final Pattern DYNAMIC_IDENTIFIER = Pattern.compile(
            "(?is)\\b(from|join|order\\s+by|group\\s+by)\\s*\\?"
    );

    private static final Pattern UNSAFE = Pattern.compile(
            "(?is)"
                    + "\\bfor\\s+update\\b"
                    + "|\\bnextval\\s*\\("
                    + "|\\bsetval\\s*\\("
                    + "|\\bselect\\s+into\\b"
                    + "|\\bpg_advisory_(xact_)?lock\\s*\\("
                    + "|\\bpg_try_advisory_(xact_)?lock\\s*\\("
    );

    private static final Pattern NON_DETERMINISTIC = Pattern.compile(
            "(?is)"
                    + "\\bcurrent_timestamp\\b"
                    + "|\\bcurrent_time\\b"
                    + "|\\bcurrent_date\\b"
                    + "|\\brandom\\s*\\("
                    + "|\\brand\\s*\\("
                    + "|\\buuid\\s*\\("
                    + "|\\brandom_uuid\\s*\\("
    );

    public SelectInspectionResult inspect(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        boolean select = startsWithSelectOrWith(normalized);

        if (!select) {
            return result(false, false, false, false, false,
                    "NOT_SELECT", "The statement is not a SELECT statement.");
        }
        if (hasMultipleStatements(normalized)) {
            return result(true, true, false, false, false,
                    "MULTIPLE_STATEMENTS",
                    "The SQL contains more than one executable statement.");
        }
        if (DYNAMIC_IDENTIFIER.matcher(normalized).find()) {
            return result(true, false, true, false, false,
                    "DYNAMIC_IDENTIFIER",
                    "A JDBC parameter is used where an identifier is expected.");
        }
        if (UNSAFE.matcher(normalized).find()) {
            return result(true, false, false, true, false,
                    "UNSAFE_SELECT",
                    "The SELECT contains a lock, state-changing function or SELECT INTO.");
        }
        if (NON_DETERMINISTIC.matcher(normalized).find()) {
            return result(true, false, false, false, true,
                    "NON_DETERMINISTIC_SELECT",
                    "The SELECT contains a value that is not stable across executions.");
        }
        return result(true, false, false, false, false,
                "SAFE_SELECT", "The statement passed Batch-2 static SELECT checks.");
    }

    private SelectInspectionResult result(
            boolean select,
            boolean multipleStatements,
            boolean dynamicIdentifier,
            boolean unsafe,
            boolean nonDeterministic,
            String reasonCode,
            String reason
    ) {
        return new SelectInspectionResult(
                select, multipleStatements, dynamicIdentifier, unsafe,
                nonDeterministic, reasonCode, reason
        );
    }

    private boolean startsWithSelectOrWith(String sql) {
        String upper = stripLeadingComments(sql).toUpperCase(Locale.ROOT);
        return upper.startsWith("SELECT") || upper.startsWith("WITH");
    }

    private String stripLeadingComments(String sql) {
        String result = sql;
        boolean changed;
        do {
            changed = false;
            String trimmed = result.stripLeading();
            if (trimmed.startsWith("--")) {
                int newline = trimmed.indexOf('\n');
                result = newline < 0 ? "" : trimmed.substring(newline + 1);
                changed = true;
            } else if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/", 2);
                result = end < 0 ? "" : trimmed.substring(end + 2);
                changed = true;
            }
        } while (changed);
        return result.stripLeading();
    }

    /** Allows one trailing semicolon but rejects multiple statements. */
    private boolean hasMultipleStatements(String sql) {
        boolean singleQuote = false;
        boolean doubleQuote = false;
        int separatorCount = 0;
        int lastSeparatorIndex = -1;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

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
            if (!singleQuote && !doubleQuote && current == ';') {
                separatorCount++;
                lastSeparatorIndex = index;
            }
        }

        if (separatorCount == 0) {
            return false;
        }
        String afterLast = sql.substring(lastSeparatorIndex + 1).trim();
        return separatorCount > 1 || !afterLast.isEmpty();
    }
}
