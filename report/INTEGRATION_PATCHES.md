# Excel Report Integration Patches

This is an additive MVP. Do not rewrite the existing scanner or validation flow.

## 1. `pom.xml`

Add to `<properties>`:

```xml
<poi.version>5.5.1</poi.version>
```

Add to `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>${poi.version}</version>
</dependency>
```

Do not add `poi` separately.

## 2. `ValidatorPhase.java`

Add one enum value:

```java
EXCEL_REPORT("excel-report")
```

Remember to add the comma before it if it is not the first/last value.

## 3. `ValidatorProperties.java`

Add a field near `inventory`, `sanity`, and `validation`:

```java
private final ReportProperties report = new ReportProperties();

public ReportProperties getReport() {
    return report;
}
```

Add this nested class inside `ValidatorProperties`:

```java
public static class ReportProperties {
    private String inventoryInput = "./output/sql-inventory.csv";
    private String executionInput = "./output/sql-execution-report.csv";
    private String bindingInput = "./output/sql-binding-plan.csv";
    private String excelOutput = "./output/sql-postgres-migration-report.xlsx";

    public String getInventoryInput() {
        return inventoryInput;
    }

    public void setInventoryInput(final String inventoryInput) {
        this.inventoryInput = inventoryInput;
    }

    public String getExecutionInput() {
        return executionInput;
    }

    public void setExecutionInput(final String executionInput) {
        this.executionInput = executionInput;
    }

    public String getBindingInput() {
        return bindingInput;
    }

    public void setBindingInput(final String bindingInput) {
        this.bindingInput = bindingInput;
    }

    public String getExcelOutput() {
        return excelOutput;
    }

    public void setExcelOutput(final String excelOutput) {
        this.excelOutput = excelOutput;
    }
}
```

## 4. `application.yml`

Add under `validator:`:

```yaml
  report:
    inventory-input: ./output/sql-inventory.csv
    execution-input: ./output/sql-execution-report.csv
    binding-input: ./output/sql-binding-plan.csv
    excel-output: ./output/sql-postgres-migration-report.xlsx
```

To generate the workbook, temporarily set:

```yaml
validator:
  phase: excel-report
```

## 5. Copy source files

Copy the generated package to:

```text
src/main/java/org/rosetta/sqlvalidator/report/excel/
```

## 6. Run

Expected output:

```text
./output/sql-postgres-migration-report.xlsx
```

Workbook sheets:

- `Summary`: issue count sorted by group and count descending.
- `Detail`: all SQL rows already grouped and sorted, with filters and a small six-color group palette.

## 7. MVP classification behavior

Classification combines:

1. PostgreSQL error message.
2. Original/executed SQL text scanning.
3. Optional binding-plan Java type information.

Primary rules include:

- Sequence / `NEXTVAL`
- `DUAL`
- `NVL`
- `SYSDATE`
- `ROWNUM`
- `TO_CHAR`
- `TO_TIMESTAMP`
- `DATEADD`
- quoted `"VALUE"` / `"ENTITY"`
- `UPDATE ... SET alias.column`
- parameter type mismatch
- source column not found
- relation/schema/environment issue

A long SQL may have multiple signals. The report chooses one primary issue type for grouping and keeps all detected signals in the detail sheet.
