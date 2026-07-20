package org.rosetta.sqlvalidator.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrossDatabaseSheetWriterTest {

    @Test
    void writesThirdSheetAndFileHyperlink() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Summary");
            workbook.createSheet("Detail");

            new CrossDatabaseSheetWriter().replaceSheet(
                    workbook,
                    "Cross DB Validation",
                    List.of(new CrossDatabaseExcelRow(
                            "key", "OLD", "NEW", 1, "params",
                            "SUCCESS", "SUCCESS", "MATCH",
                            10L, 8L,
                            "Open Performance Report",
                            "performance/reports/SQL-001/index.html")));

            var sheet = workbook.getSheet("Cross DB Validation");
            assertNotNull(sheet);
            assertEquals(3, workbook.getNumberOfSheets());
            assertNotNull(sheet.getRow(1).getCell(10).getHyperlink());
        }
    }
}
