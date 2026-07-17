package org.rosetta.sqlvalidator.crossdb.input;

/** Aggregated binding-plan information for one sqlId. */
public record BindingPlanSnapshot(
        String sqlId,
        String jdbcSql,
        String status,
        int bindingCount
) {
    public boolean usable() {
        if (jdbcSql == null || jdbcSql.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return true;
        }

        String normalized = status.trim().toUpperCase();
        return !(normalized.contains("FAIL")
                || normalized.contains("ERROR")
                || normalized.contains("UNRESOLVED")
                || normalized.contains("INCOMPLETE")
                || normalized.contains("MANUAL"));
    }
}
