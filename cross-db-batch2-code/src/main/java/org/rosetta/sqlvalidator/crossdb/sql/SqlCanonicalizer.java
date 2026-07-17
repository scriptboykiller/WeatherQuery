package org.rosetta.sqlvalidator.crossdb.sql;

/** Conservative canonicalization used only for stale-file detection. */
public final class SqlCanonicalizer {

    public String canonicalize(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.trim()
                .replaceAll("\\s+", " ")
                .replaceAll(";+$", "")
                .trim();
    }
}
