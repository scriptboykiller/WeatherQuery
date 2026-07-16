package org.rosetta.sqlvalidator.report.excel.runner;

import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.report.excel.service.SqlMigrationExcelReportService;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class ExcelReportRunner implements ValidatorRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelReportRunner.class);

    private final ValidatorProperties validatorProperties;
    private final SqlMigrationExcelReportService reportService;

    public ExcelReportRunner(final ValidatorProperties validatorProperties,
                             final SqlMigrationExcelReportService reportService) {
        this.validatorProperties = validatorProperties;
        this.reportService = reportService;
    }

    @Override
    public ValidatorPhase getSupportedPhase() {
        return ValidatorPhase.EXCEL_REPORT;
    }

    @Override
    public void run() {
        final ValidatorProperties.ReportProperties report = validatorProperties.getReport();
        try {
            final int rowCount = reportService.generate(
                    Path.of(report.getInventoryInput()),
                    Path.of(report.getExecutionInput()),
                    Path.of(report.getBindingInput()),
                    Path.of(report.getExcelOutput()));
            LOGGER.info("Excel migration report generated. rows={}, output={}", rowCount, report.getExcelOutput());
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to generate Excel migration report", exception);
        }
    }
}
