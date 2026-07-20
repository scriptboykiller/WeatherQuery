package org.rosetta.sqlvalidator.performance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PerformanceReportService {

    private final CrossDatabasePerformanceInputReader inputReader;
    private final PerformanceEligibilityEvaluator eligibilityEvaluator;
    private final PerformanceCaseSelector selector;
    private final PerformanceRequestFactory requestFactory;
    private final PerformanceToolInvoker toolInvoker;
    private final PerformanceReportCsvWriter csvWriter;

    public PerformanceReportService(
            CrossDatabasePerformanceInputReader inputReader,
            PerformanceEligibilityEvaluator eligibilityEvaluator,
            PerformanceCaseSelector selector,
            PerformanceRequestFactory requestFactory,
            PerformanceToolInvoker toolInvoker,
            PerformanceReportCsvWriter csvWriter
    ) {
        this.inputReader = inputReader;
        this.eligibilityEvaluator = eligibilityEvaluator;
        this.selector = selector;
        this.requestFactory = requestFactory;
        this.toolInvoker = toolInvoker;
        this.csvWriter = csvWriter;
    }

    public List<PerformanceReportRecord> run(PerformanceReportProperties properties)
            throws Exception {
        if (!properties.isEnabled()) {
            return List.of();
        }

        toolInvoker.validateConfiguration(properties);

        List<PerformanceSourceRow> rows = inputReader.readPreferred(
                properties.getCrossDbComparisonInput(),
                properties.getCrossDbBaselineInput());

        List<SelectedPerformanceCase> selected = selector.select(
                rows,
                properties.getTopSlowest(),
                properties.getDefaultSampleIndex(),
                properties.isRequireResultMatch(),
                properties.getIncludeSqlIds());

        Map<String, SelectedPerformanceCase> selectedByGroup = new HashMap<>();
        for (SelectedPerformanceCase item : selected) {
            selectedByGroup.put(item.representative().sqlGroupKey(), item);
        }

        Map<String, PerformanceInvocationOutcome> outcomes = new HashMap<>();
        Map<String, String> caseIds = new HashMap<>();

        // Serial execution by design.
        for (SelectedPerformanceCase item : selected) {
            PerformanceSourceRow row = item.representative();
            try {
                PerformanceToolRequest request = requestFactory.create(row);
                caseIds.put(row.sqlGroupKey(), request.caseId());
                Path requestFile = properties.getRequestDirectory()
                        .resolve(request.caseId() + ".json");
                Path reportDir = properties.getReportDirectory()
                        .resolve(request.caseId());
                outcomes.put(row.sqlGroupKey(), toolInvoker.invoke(
                        request, requestFile, reportDir, properties));
            } catch (RuntimeException exception) {
                outcomes.put(row.sqlGroupKey(), new PerformanceInvocationOutcome(
                        PerformanceStatus.PARAMETER_DECODE_FAILED,
                        "", "REQUEST_BUILD_FAILED", exception.getMessage()));
            }
        }

        List<PerformanceReportRecord> records = new ArrayList<>();
        for (PerformanceSourceRow row : rows) {
            PerformanceEligibility eligibility = eligibilityEvaluator.evaluate(
                    row, properties.isRequireResultMatch());
            SelectedPerformanceCase selectedCase = selectedByGroup.get(row.sqlGroupKey());

            if (!eligibility.eligible()) {
                records.add(record(row, "", null, SelectionReason.NONE,
                        PerformanceStatus.NOT_ELIGIBLE, "", "NOT_ELIGIBLE",
                        eligibility.reason()));
                continue;
            }

            if (selectedCase == null) {
                records.add(record(row, "", null, SelectionReason.NONE,
                        PerformanceStatus.NOT_SELECTED, "", "", ""));
                continue;
            }

            int selectedSample = selectedCase.representative().sampleIndex();
            String caseId = caseIds.getOrDefault(row.sqlGroupKey(), "");
            if (row.sampleIndex() != selectedSample) {
                records.add(record(row, caseId, selectedSample,
                        selectedCase.reason(),
                        PerformanceStatus.COVERED_BY_SELECTED_SAMPLE,
                        "", "", ""));
                continue;
            }

            PerformanceInvocationOutcome outcome = outcomes.get(row.sqlGroupKey());
            records.add(record(row, caseId, selectedSample,
                    selectedCase.reason(), outcome.status(),
                    relativize(properties.getResultOutput(), outcome.htmlReportPath()),
                    outcome.errorCode(), outcome.errorMessage()));
        }

        csvWriter.write(properties.getResultOutput(), records);
        return List.copyOf(records);
    }

    private PerformanceReportRecord record(
            PerformanceSourceRow row,
            String caseId,
            Integer selectedSampleIndex,
            SelectionReason reason,
            PerformanceStatus status,
            String htmlPath,
            String errorCode,
            String errorMessage
    ) {
        return new PerformanceReportRecord(
                caseId, row.baselineKey(), row.baselineSqlId(),
                row.currentSqlId(), row.sampleIndex(), selectedSampleIndex,
                reason, status, htmlPath, errorCode, errorMessage);
    }

    private String relativize(Path csvOutput, String htmlPath) {
        if (htmlPath == null || htmlPath.isBlank()) return "";
        try {
            Path base = csvOutput.toAbsolutePath().getParent();
            return base.relativize(Path.of(htmlPath).toAbsolutePath())
                    .toString().replace('\\', '/');
        } catch (RuntimeException exception) {
            return htmlPath.replace('\\', '/');
        }
    }
}
