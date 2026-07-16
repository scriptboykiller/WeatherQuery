package org.rosetta.sqlvalidator.report.excel.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rosetta.sqlvalidator.report.excel.model.IssueGroup;
import org.rosetta.sqlvalidator.report.excel.model.IssueType;
import org.rosetta.sqlvalidator.report.excel.model.ReportDetailRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SqlMigrationExcelWriter {

    private static final String[] DETAIL_HEADERS = {
            "Rank", "SQL ID", "Service", "Class", "Method", "Line",
            "Execution Status", "Issue Group", "Issue Type", "Priority",
            "Detected Signals", "Classification Source", "SQLState",
            "Parameter Summary", "Original SQL", "Executed SQL",
            "PostgreSQL Error Message", "Recommended Action", "Source File"
    };

    public void write(final List<ReportDetailRow> inputRows, final Path outputFile) throws IOException {
        final Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final List<ReportDetailRow> rows = sortRows(inputRows);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            final Styles styles = new Styles(workbook);
            writeSummary(workbook, rows, styles);
            writeDetail(workbook, rows, styles);
            workbook.setActiveSheet(0);

            try (OutputStream output = Files.newOutputStream(outputFile)) {
                workbook.write(output);
            }
        }
    }

    private List<ReportDetailRow> sortRows(final List<ReportDetailRow> inputRows) {
        final Map<IssueType, Long> counts = inputRows.stream()
                .collect(Collectors.groupingBy(row -> row.classification().issueType(), LinkedHashMap::new, Collectors.counting()));

        return inputRows.stream()
                .map(row -> new ReportDetailRow(row.inventory(), row.execution(), row.parameterSummary(),
                        row.classification(), counts.getOrDefault(row.classification().issueType(), 0L).intValue()))
                .sorted(Comparator
                        .comparingInt((ReportDetailRow row) -> row.classification().issueGroup().getSortOrder())
                        .thenComparing(Comparator.comparingInt(ReportDetailRow::issueTypeCount).reversed())
                        .thenComparing(row -> row.classification().issueType().getDisplayName())
                        .thenComparing(row -> row.inventory() == null ? "" : safe(row.inventory().serviceName()))
                        .thenComparing(ReportDetailRow::sqlId))
                .toList();
    }

    private void writeSummary(final XSSFWorkbook workbook,
                              final List<ReportDetailRow> rows,
                              final Styles styles) {
        final Sheet sheet = workbook.createSheet("Summary");
        sheet.createFreezePane(0, 6);
        sheet.setDisplayGridlines(false);

        final Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        final Cell title = titleRow.createCell(0);
        title.setCellValue("PostgreSQL Native SQL Validation Report");
        title.setCellStyle(styles.title);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

        final long total = rows.size();
        final long passed = rows.stream().filter(row -> row.classification().issueGroup() == IssueGroup.PASSED).count();
        final long failed = total - passed;
        final double passRate = total == 0 ? 0D : (double) passed / total;

        writeKpi(sheet, 2, 0, "Total SQL", total, styles);
        writeKpi(sheet, 2, 2, "Passed", passed, styles);
        writeKpi(sheet, 2, 4, "Needs Attention", failed, styles);
        writeKpi(sheet, 3, 0, "Pass Rate", passRate, styles);
        writeKpi(sheet, 3, 2, "Generated At", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), styles);
        writeKpi(sheet, 3, 4, "Sort Rule", "Group → Count ↓ → Issue Type", styles);
        sheet.getRow(3).getCell(1).setCellStyle(styles.percentKpiValue);

        final int headerRowIndex = 5;
        final Row header = sheet.createRow(headerRowIndex);
        final String[] headers = {"Rank", "Issue Group", "Issue Type", "Count", "% of Total", "Priority", "Recommended Action"};
        for (int i = 0; i < headers.length; i++) {
            final Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        final Map<IssueType, List<ReportDetailRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> row.classification().issueType()));
        final List<Map.Entry<IssueType, List<ReportDetailRow>>> summaryRows = new ArrayList<>(grouped.entrySet());
        summaryRows.sort(Comparator
                .<Map.Entry<IssueType, List<ReportDetailRow>>>comparingInt(entry -> entry.getValue().get(0).classification().issueGroup().getSortOrder())
                .thenComparing(Comparator.comparingInt((Map.Entry<IssueType, List<ReportDetailRow>> entry) -> entry.getValue().size()).reversed())
                .thenComparing(entry -> entry.getKey().getDisplayName()));

        int rowIndex = headerRowIndex + 1;
        int rank = 1;
        for (final Map.Entry<IssueType, List<ReportDetailRow>> entry : summaryRows) {
            final ReportDetailRow sample = entry.getValue().get(0);
            final Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(32);
            set(row, 0, rank++, styles.bodyCenter);
            set(row, 1, sample.classification().issueGroup().getDisplayName(), styles.groupStyle(sample.classification().issueGroup()));
            set(row, 2, entry.getKey().getDisplayName(), styles.body);
            set(row, 3, entry.getValue().size(), styles.bodyCenter);
            final Cell percentage = row.createCell(4);
            percentage.setCellValue(total == 0 ? 0D : (double) entry.getValue().size() / total);
            percentage.setCellStyle(styles.percent);
            set(row, 5, sample.classification().priority(), styles.bodyCenter);
            set(row, 6, sample.classification().recommendation(), styles.wrap);
        }

        if (!summaryRows.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(headerRowIndex, rowIndex - 1, 0, headers.length - 1));
        }
        setWidths(sheet, new int[]{8, 28, 34, 10, 12, 12, 70});
    }

    private void writeKpi(final Sheet sheet,
                          final int rowIndex,
                          final int startColumn,
                          final String label,
                          final Object value,
                          final Styles styles) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
            row.setHeightInPoints(24);
        }
        final Cell labelCell = row.createCell(startColumn);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.kpiLabel);
        final Cell valueCell = row.createCell(startColumn + 1);
        if (value instanceof Number number) {
            valueCell.setCellValue(number.doubleValue());
        } else {
            valueCell.setCellValue(String.valueOf(value));
        }
        valueCell.setCellStyle(styles.kpiValue);
    }

    private void writeDetail(final XSSFWorkbook workbook,
                             final List<ReportDetailRow> rows,
                             final Styles styles) {
        final Sheet sheet = workbook.createSheet("Detail");
        sheet.createFreezePane(0, 1);
        sheet.setDisplayGridlines(false);

        final Row header = sheet.createRow(0);
        header.setHeightInPoints(28);
        for (int i = 0; i < DETAIL_HEADERS.length; i++) {
            final Cell cell = header.createCell(i);
            cell.setCellValue(DETAIL_HEADERS[i]);
            cell.setCellStyle(styles.header);
        }

        int rowIndex = 1;
        int rank = 1;
        for (final ReportDetailRow detail : rows) {
            final Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(46);
            int column = 0;
            set(row, column++, rank++, styles.bodyCenter);
            set(row, column++, detail.sqlId(), styles.body);
            set(row, column++, detail.inventory() == null ? "" : detail.inventory().serviceName(), styles.body);
            set(row, column++, detail.inventory() == null ? "" : detail.inventory().className(), styles.body);
            set(row, column++, detail.inventory() == null ? "" : detail.inventory().methodName(), styles.body);
            set(row, column++, detail.inventory() == null ? 0 : detail.inventory().lineNumber(), styles.bodyCenter);
            set(row, column++, detail.execution() == null ? "" : detail.execution().executionStatus(),
                    styles.statusStyle(detail.classification().issueGroup()));
            set(row, column++, detail.classification().issueGroup().getDisplayName(),
                    styles.groupStyle(detail.classification().issueGroup()));
            set(row, column++, detail.classification().issueType().getDisplayName(),
                    styles.groupStyle(detail.classification().issueGroup()));
            set(row, column++, detail.classification().priority(), styles.bodyCenter);
            set(row, column++, detail.classification().detectedSignals(), styles.wrap);
            set(row, column++, detail.classification().source().name(), styles.body);
            set(row, column++, detail.execution() == null ? "" : detail.execution().sqlState(), styles.bodyCenter);
            set(row, column++, detail.parameterSummary(), styles.wrap);
            set(row, column++, detail.originalSql(), styles.sql);
            set(row, column++, detail.executedSql(), styles.sql);
            set(row, column++, detail.execution() == null ? "" : detail.execution().message(), styles.error);
            set(row, column++, detail.classification().recommendation(), styles.wrap);
            set(row, column, detail.inventory() == null ? "" : detail.inventory().filePath(), styles.wrap);
        }

        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, DETAIL_HEADERS.length - 1));
        }
        setWidths(sheet, new int[]{8, 20, 18, 24, 24, 8, 22, 28, 34, 12, 28, 24, 12, 38, 70, 70, 70, 70, 55});
    }

    private void set(final Row row, final int column, final Object value, final CellStyle style) {
        final Cell cell = row.createCell(column);
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private void setWidths(final Sheet sheet, final int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
        }
    }

    private String safe(final String value) {
        return value == null ? "" : value;
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle bodyCenter;
        private final CellStyle wrap;
        private final CellStyle sql;
        private final CellStyle error;
        private final CellStyle percent;
        private final CellStyle kpiLabel;
        private final CellStyle kpiValue;
        private final CellStyle percentKpiValue;
        private final Map<IssueGroup, CellStyle> groupStyles = new EnumMap<>(IssueGroup.class);
        private final Map<IssueGroup, CellStyle> statusStyles = new EnumMap<>(IssueGroup.class);

        private Styles(final XSSFWorkbook workbook) {
            title = workbook.createCellStyle();
            title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.LEFT);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            final XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            title.setFont(titleFont);

            header = base(workbook, true, true);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            final Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);

            body = base(workbook, false, false);
            bodyCenter = base(workbook, false, false);
            bodyCenter.setAlignment(HorizontalAlignment.CENTER);
            wrap = base(workbook, false, true);
            sql = base(workbook, false, true);
            sql.setVerticalAlignment(VerticalAlignment.TOP);
            error = base(workbook, false, true);
            final Font errorFont = workbook.createFont();
            errorFont.setColor(IndexedColors.DARK_RED.getIndex());
            error.setFont(errorFont);

            percent = base(workbook, false, false);
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
            percent.setAlignment(HorizontalAlignment.CENTER);

            kpiLabel = base(workbook, true, false);
            kpiLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            kpiLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            kpiValue = base(workbook, true, false);
            percentKpiValue = base(workbook, true, false);
            percentKpiValue.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

            for (final IssueGroup group : IssueGroup.values()) {
                groupStyles.put(group, createGroupStyle(workbook, group));
                statusStyles.put(group, createStatusStyle(workbook, group));
            }
        }

        private CellStyle groupStyle(final IssueGroup group) {
            return groupStyles.get(group);
        }

        private CellStyle statusStyle(final IssueGroup group) {
            return statusStyles.get(group);
        }

        private static CellStyle base(final XSSFWorkbook workbook, final boolean bold, final boolean wrapText) {
            final CellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(wrapText);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            if (bold) {
                final Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
            }
            return style;
        }

        private static CellStyle createGroupStyle(final XSSFWorkbook workbook, final IssueGroup group) {
            final CellStyle style = base(workbook, true, true);
            style.setFillForegroundColor(groupColor(group));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle createStatusStyle(final XSSFWorkbook workbook, final IssueGroup group) {
            final CellStyle style = base(workbook, true, false);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setFillForegroundColor(groupColor(group));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static short groupColor(final IssueGroup group) {
            return switch (group) {
                case PASSED -> IndexedColors.LIGHT_GREEN.getIndex();
                case SQL_DIALECT_COMPATIBILITY -> IndexedColors.ROSE.getIndex();
                case SOURCE_SQL_DEFECT -> IndexedColors.LIGHT_ORANGE.getIndex();
                case PARAMETER_TYPE_OR_BINDING -> IndexedColors.LIGHT_YELLOW.getIndex();
                case DATABASE_ENVIRONMENT -> IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex();
                case MANUAL_REVIEW -> IndexedColors.GREY_25_PERCENT.getIndex();
            };
        }
    }
}
