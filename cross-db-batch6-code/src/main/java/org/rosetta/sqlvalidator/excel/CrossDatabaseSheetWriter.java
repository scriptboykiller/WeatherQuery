package org.rosetta.sqlvalidator.excel;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;

import java.util.List;

public final class CrossDatabaseSheetWriter {

    public static final String DEFAULT_SHEET_NAME = "Cross DB Validation";

    private static final String[] HEADERS = {
            "Baseline Key", "Baseline SQL ID", "Current SQL ID",
            "Sample Index", "Parameter Values", "H2 Status",
            "PostgreSQL Status", "Result Comparison",
            "H2 Observed Time (ms)", "PostgreSQL Observed Time (ms)",
            "Performance Report"
    };

    public void replaceSheet(
            Workbook workbook,
            String sheetName,
            List<CrossDatabaseExcelRow> rows
    ) {
        String name = sheetName == null || sheetName.isBlank()
                ? DEFAULT_SHEET_NAME : sheetName;

        int existing = workbook.getSheetIndex(name);
        if (existing >= 0) {
            workbook.removeSheetAt(existing);
        }

        Sheet sheet = workbook.createSheet(name);
        Styles styles = new Styles(workbook);
        writeHeader(sheet, styles.header());

        int rowIndex = 1;
        for (CrossDatabaseExcelRow value : rows) {
            Row row = sheet.createRow(rowIndex++);
            writeText(row, 0, value.baselineKey(), styles.body());
            writeText(row, 1, value.baselineSqlId(), styles.body());
            writeText(row, 2, value.currentSqlId(), styles.body());
            row.createCell(3).setCellValue(value.sampleIndex());
            writeText(row, 4, value.parameterValues(), styles.wrap());
            writeText(row, 5, value.h2Status(), styles.body());
            writeText(row, 6, value.postgresStatus(), styles.body());
            writeText(row, 7, value.resultComparison(), styles.body());
            writeNumber(row, 8, value.h2ObservedTimeMs());
            writeNumber(row, 9, value.postgresObservedTimeMs());
            writePerformance(row, 10, value, workbook, styles);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, rowIndex - 1), 0, HEADERS.length - 1));

        int[] widths = {42, 18, 18, 12, 45, 20, 22, 22, 20, 28, 30};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void writeHeader(Sheet sheet, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeText(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int index, Long value) {
        Cell cell = row.createCell(index);
        if (value != null) cell.setCellValue(value);
    }

    private void writePerformance(
            Row row,
            int index,
            CrossDatabaseExcelRow value,
            Workbook workbook,
            Styles styles
    ) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value.performanceDisplay());
        if (value.performanceLink() != null && !value.performanceLink().isBlank()) {
            CreationHelper helper = workbook.getCreationHelper();
            Hyperlink hyperlink = helper.createHyperlink(HyperlinkType.FILE);
            hyperlink.setAddress(value.performanceLink().replace('\\', '/'));
            cell.setHyperlink(hyperlink);
            cell.setCellStyle(styles.link());
        } else {
            cell.setCellStyle(styles.body());
        }
    }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle wrap;
        private final CellStyle link;

        private Styles(Workbook workbook) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            body = workbook.createCellStyle();
            body.setVerticalAlignment(VerticalAlignment.TOP);

            wrap = workbook.createCellStyle();
            wrap.cloneStyleFrom(body);
            wrap.setWrapText(true);

            Font linkFont = workbook.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            link = workbook.createCellStyle();
            link.cloneStyleFrom(body);
            link.setFont(linkFont);
        }

        private CellStyle header() { return header; }
        private CellStyle body() { return body; }
        private CellStyle wrap() { return wrap; }
        private CellStyle link() { return link; }
    }
}
