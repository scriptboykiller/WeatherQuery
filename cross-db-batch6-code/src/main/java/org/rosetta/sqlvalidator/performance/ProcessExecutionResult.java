package org.rosetta.sqlvalidator.performance;

public record ProcessExecutionResult(
        boolean timedOut,
        int exitCode,
        String output
) {
}
