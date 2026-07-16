package org.rosetta.sqlvalidator.report.excel.model;

public record IssueClassification(
        IssueGroup issueGroup,
        IssueType issueType,
        String priority,
        ClassificationSource source,
        String detectedSignals,
        String recommendation) {
}
