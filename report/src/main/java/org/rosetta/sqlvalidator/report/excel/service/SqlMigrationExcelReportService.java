package org.rosetta.sqlvalidator.report.excel.service;

import org.rosetta.sqlvalidator.report.excel.model.BindingRecord;
import org.rosetta.sqlvalidator.report.excel.model.ExecutionRecord;
import org.rosetta.sqlvalidator.report.excel.model.InventoryRecord;
import org.rosetta.sqlvalidator.report.excel.model.IssueClassification;
import org.rosetta.sqlvalidator.report.excel.model.ReportDetailRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SqlMigrationExcelReportService {

    private final ReportInputReader reportInputReader;
    private final SqlIssueClassifier sqlIssueClassifier;
    private final SqlMigrationExcelWriter sqlMigrationExcelWriter;

    public SqlMigrationExcelReportService(final ReportInputReader reportInputReader,
                                          final SqlIssueClassifier sqlIssueClassifier,
                                          final SqlMigrationExcelWriter sqlMigrationExcelWriter) {
        this.reportInputReader = reportInputReader;
        this.sqlIssueClassifier = sqlIssueClassifier;
        this.sqlMigrationExcelWriter = sqlMigrationExcelWriter;
    }

    public int generate(final Path inventoryInput,
                        final Path executionInput,
                        final Path bindingInput,
                        final Path excelOutput) throws IOException {
        final Map<String, InventoryRecord> inventoryById = reportInputReader.readInventory(inventoryInput);
        final List<ExecutionRecord> executions = reportInputReader.readExecution(executionInput);
        final Map<String, List<BindingRecord>> bindingsById = reportInputReader.readBindingsOptional(bindingInput);

        final List<ReportDetailRow> rows = new ArrayList<>();
        for (final ExecutionRecord execution : executions) {
            final InventoryRecord inventory = inventoryById.get(execution.sqlId());
            final List<BindingRecord> bindings = bindingsById.getOrDefault(execution.sqlId(), List.of());
            final IssueClassification classification = sqlIssueClassifier.classify(inventory, execution, bindings);
            rows.add(new ReportDetailRow(
                    inventory,
                    execution,
                    sqlIssueClassifier.parameterSummary(bindings),
                    classification,
                    0));
        }

        sqlMigrationExcelWriter.write(rows, excelOutput);
        return rows.size();
    }
}
