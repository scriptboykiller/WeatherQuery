package org.rosetta.sqlvalidator.crossdb.identity;

/**
 * Stable source identity used to reconnect a captured H2 baseline with the
 * SQL found after the Java SQL text has been migrated.
 */
public record BaselineIdentity(
        String serviceName,
        String className,
        String methodName,
        String sourceType,
        String sqlVariableName,
        int occurrenceIndex
) {
    public BaselineIdentity {
        if (occurrenceIndex < 1) {
            throw new IllegalArgumentException("occurrenceIndex must be greater than zero");
        }
    }
}
