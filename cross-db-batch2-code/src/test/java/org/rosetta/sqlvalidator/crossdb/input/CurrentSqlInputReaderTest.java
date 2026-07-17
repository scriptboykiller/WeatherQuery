package org.rosetta.sqlvalidator.crossdb.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurrentSqlInputReaderTest {

    @TempDir
    Path tempDir;

    private final CurrentSqlInputReader reader = new CurrentSqlInputReader();

    @Test
    void readsAllThreeFilesByHeader() throws Exception {
        Path inventory = tempDir.resolve("inventory.csv");
        Files.writeString(inventory, """
                id,serviceName,filePath,className,methodName,sourceType,lineNumber,sqlVariableName,sqlText,normalizedSqlText,parameterMode,parameterNames,parameterCount,isDynamicSql,requiresManualReview,manualReviewReason,confidence,notes
                sql-1,service,Repository.java,Repository,find,SPRING_DATA_QUERY,10,query,SELECT 1,SELECT 1,NONE,,0,false,false,,HIGH,
                """);

        Path binding = tempDir.resolve("binding.csv");
        Files.writeString(binding, """
                sqlId,jdbcSql,bindingStatus,bindingCount
                sql-1,SELECT 1,READY,0
                """);

        Path execution = tempDir.resolve("execution.csv");
        Files.writeString(execution, """
                sqlId,executionStatus,jdbcSql
                sql-1,PASSED,SELECT 1
                """);

        List<CurrentSqlInventoryRow> inventoryRows =
                reader.readInventory(inventory);
        Map<String, BindingPlanSnapshot> bindingPlans =
                reader.readBindingPlans(binding);
        Map<String, ExecutionReportSnapshot> executionReports =
                reader.readExecutionReports(execution);

        assertEquals(1, inventoryRows.size());
        assertEquals("service", inventoryRows.get(0).serviceName());
        assertTrue(bindingPlans.get("sql-1").usable());
        assertTrue(executionReports.get("sql-1").postgresPassed());
    }
}
