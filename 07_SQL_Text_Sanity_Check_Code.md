# 07_SQL_Text_Sanity_Check_Code.md

## 1. 这份代码做什么

这份代码给现有 `sql-postgres-validator` 增加 Phase 1.5：

```text
SQL Text Sanity Check
```

它会读取：

```text
output/sql-inventory.csv
```

输出：

```text
output/sql-sanity-report.csv
output/sql-sanity-summary.txt
```

---

## 2. pom.xml 增加依赖

在 `sql-postgres-validator/pom.xml` 里增加：

```xml
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>5.3</version>
</dependency>
```

如果公司 Maven 仓库没有 `5.3`，让 Copilot 改成公司仓库可用版本。

---

## 3. 推荐新增目录

```text
src/main/java/com/company/sqlvalidator/sanity
```

新增文件：

```text
SqlSanityStatus.java
SqlSanityResult.java
SqlTextRuleChecker.java
SqlParserSanityChecker.java
SqlInventoryCsvReader.java
SqlSanityReportWriter.java
SqlSanitySummaryWriter.java
SqlTextSanityCheckApplication.java
```

---

## 4. SqlSanityStatus.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlSanityStatus.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

public enum SqlSanityStatus {
    CLEAN_PARSE_OK,
    TEXT_SUSPECT_PARSE_OK,
    PARSE_ERROR_NO_TEXT_ISSUE,
    TEXT_SUSPECT_PARSE_ERROR,
    SKIPPED_EMPTY_SQL,
    SKIPPED_DYNAMIC_SQL
}
```

---

## 5. SqlSanityResult.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlSanityResult.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import com.company.sqlvalidator.model.NativeSqlRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class SqlSanityResult {

    private NativeSqlRecord sourceRecord;
    private String effectiveSqlText;
    private String parserSql;
    private SqlSanityStatus status;
    private List<String> sanityIssues = new ArrayList<>();
    private String parserError;
    private String recommendation;

    public NativeSqlRecord getSourceRecord() {
        return sourceRecord;
    }

    public SqlSanityResult setSourceRecord(NativeSqlRecord sourceRecord) {
        this.sourceRecord = sourceRecord;
        return this;
    }

    public String getEffectiveSqlText() {
        return effectiveSqlText;
    }

    public SqlSanityResult setEffectiveSqlText(String effectiveSqlText) {
        this.effectiveSqlText = effectiveSqlText;
        return this;
    }

    public String getParserSql() {
        return parserSql;
    }

    public SqlSanityResult setParserSql(String parserSql) {
        this.parserSql = parserSql;
        return this;
    }

    public SqlSanityStatus getStatus() {
        return status;
    }

    public SqlSanityResult setStatus(SqlSanityStatus status) {
        this.status = status;
        return this;
    }

    public List<String> getSanityIssues() {
        return sanityIssues;
    }

    public SqlSanityResult setSanityIssues(List<String> sanityIssues) {
        this.sanityIssues = sanityIssues == null ? new ArrayList<>() : new ArrayList<>(sanityIssues);
        return this;
    }

    public String getParserError() {
        return parserError;
    }

    public SqlSanityResult setParserError(String parserError) {
        this.parserError = parserError;
        return this;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public SqlSanityResult setRecommendation(String recommendation) {
        this.recommendation = recommendation;
        return this;
    }

    public String getSanityIssuesAsString() {
        if (sanityIssues == null || sanityIssues.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("|");
        for (String issue : sanityIssues) {
            joiner.add(issue);
        }
        return joiner.toString();
    }
}
```

---

## 6. SqlTextRuleChecker.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlTextRuleChecker.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import java.util.ArrayList;
import java.util.List;

public class SqlTextRuleChecker {

    public List<String> check(String sql) {
        List<String> issues = new ArrayList<>();

        if (sql == null || sql.trim().isEmpty()) {
            issues.add("EMPTY_SQL");
            return issues;
        }

        if (sql.contains("\\n")) {
            issues.add("LITERAL_BACKSLASH_N");
        }

        if (sql.contains("\\r")) {
            issues.add("LITERAL_BACKSLASH_R");
        }

        if (sql.contains("\\t")) {
            issues.add("LITERAL_BACKSLASH_T");
        }

        if (sql.contains("\\s")) {
            issues.add("LITERAL_BACKSLASH_S");
        }

        if (sql.contains("\" +") || sql.contains("+ \"") || sql.contains("' +") || sql.contains("+ '")) {
            issues.add("JAVA_CONCAT_REMAINDER");
        }

        if (sql.contains(".append(") || sql.contains("StringBuilder")) {
            issues.add("JAVA_APPEND_REMAINDER");
        }

        if (sql.contains("//")) {
            issues.add("JAVA_LINE_COMMENT_OR_URL");
        }

        if (sql.contains("/*") || sql.contains("*/")) {
            issues.add("SQL_OR_JAVA_BLOCK_COMMENT");
        }

        if (hasUnbalancedSingleQuotes(sql)) {
            issues.add("UNBALANCED_SINGLE_QUOTE");
        }

        if (hasSuspiciousDoubleQuotes(sql)) {
            issues.add("SUSPICIOUS_DOUBLE_QUOTE");
        }

        return issues;
    }

    private boolean hasUnbalancedSingleQuotes(String sql) {
        boolean inQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inQuote = !inQuote;
            }
        }

        return inQuote;
    }

    private boolean hasSuspiciousDoubleQuotes(String sql) {
        long count = sql.chars().filter(ch -> ch == '"').count();

        // Double quotes can be valid for identifiers in SQL.
        // Only odd count is suspicious.
        return count % 2 != 0;
    }
}
```

---

## 7. SqlParserSanityChecker.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlParserSanityChecker.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import com.company.sqlvalidator.model.NativeSqlRecord;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import java.util.List;

public class SqlParserSanityChecker {

    private final SqlTextRuleChecker textRuleChecker = new SqlTextRuleChecker();

    public SqlSanityResult check(NativeSqlRecord record) {
        SqlSanityResult result = new SqlSanityResult()
                .setSourceRecord(record);

        String effectiveSql = resolveEffectiveSql(record);
        result.setEffectiveSqlText(effectiveSql);

        if (effectiveSql == null || effectiveSql.trim().isEmpty()) {
            return result
                    .setStatus(SqlSanityStatus.SKIPPED_EMPTY_SQL)
                    .setRecommendation("SQL text is empty. Check scanner extraction or manual review result.");
        }

        if (record.isDynamicSql() && (record.getSqlText() == null || record.getSqlText().trim().isEmpty())) {
            return result
                    .setStatus(SqlSanityStatus.SKIPPED_DYNAMIC_SQL)
                    .setRecommendation("Dynamic SQL without resolved SQL text. Manual resolvedSqlText is required before validation.");
        }

        List<String> issues = textRuleChecker.check(effectiveSql);
        result.setSanityIssues(issues);

        String parserSql = prepareForParser(effectiveSql);
        result.setParserSql(parserSql);

        boolean parseOk = false;
        String parserError = "";

        try {
            CCJSqlParserUtil.parse(parserSql);
            parseOk = true;
        } catch (Exception ex) {
            parserError = ex.getMessage();
        }

        result.setParserError(parserError);

        boolean hasTextIssue = !issues.isEmpty();

        if (parseOk && !hasTextIssue) {
            return result
                    .setStatus(SqlSanityStatus.CLEAN_PARSE_OK)
                    .setRecommendation("SQL text looks clean and parser accepted it.");
        }

        if (parseOk) {
            return result
                    .setStatus(SqlSanityStatus.TEXT_SUSPECT_PARSE_OK)
                    .setRecommendation("Parser accepted SQL, but text contains suspicious extraction artifacts. Review sanityIssues.");
        }

        if (hasTextIssue) {
            return result
                    .setStatus(SqlSanityStatus.TEXT_SUSPECT_PARSE_ERROR)
                    .setRecommendation("Parser failed and text contains suspicious artifacts. Fix scanner/normalizer first.");
        }

        return result
                .setStatus(SqlSanityStatus.PARSE_ERROR_NO_TEXT_ISSUE)
                .setRecommendation("Parser failed but no obvious text artifact was detected. Could be vendor-specific syntax. Do not treat as final SQL failure.");
    }

    private String resolveEffectiveSql(NativeSqlRecord record) {
        if (record == null) {
            return "";
        }

        if (record.getNormalizedSqlText() != null && !record.getNormalizedSqlText().trim().isEmpty()) {
            return record.getNormalizedSqlText();
        }

        if (record.getSqlText() != null && !record.getSqlText().trim().isEmpty()) {
            return record.getSqlText();
        }

        return "";
    }

    private String prepareForParser(String sql) {
        if (sql == null) {
            return "";
        }

        String parserSql = sql.trim();

        // Remove trailing semicolon.
        parserSql = parserSql.replaceAll(";\\s*$", "");

        // Convert named parameters to JDBC placeholders.
        // Avoid PostgreSQL cast syntax ::date by using negative lookbehind.
        parserSql = parserSql.replaceAll("(?<!:):([A-Za-z][A-Za-z0-9_]*)", "?");

        return parserSql;
    }
}
```

---

## 8. SqlInventoryCsvReader.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlInventoryCsvReader.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.ParameterMode;
import com.company.sqlvalidator.model.SqlSourceType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SqlInventoryCsvReader {

    public List<NativeSqlRecord> read(Path csvFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader);

            return toList(records);
        }
    }

    private List<NativeSqlRecord> toList(Iterable<CSVRecord> csvRecords) {
        java.util.ArrayList<NativeSqlRecord> result = new java.util.ArrayList<>();

        for (CSVRecord csv : csvRecords) {
            NativeSqlRecord record = new NativeSqlRecord()
                    .setId(get(csv, "id"))
                    .setServiceName(get(csv, "serviceName"))
                    .setModuleName(get(csv, "moduleName"))
                    .setFilePath(get(csv, "filePath"))
                    .setClassName(get(csv, "className"))
                    .setMethodName(get(csv, "methodName"))
                    .setSourceType(parseEnum(SqlSourceType.class, get(csv, "sourceType"), SqlSourceType.UNKNOWN))
                    .setLineNumber(parseInt(get(csv, "lineNumber")))
                    .setSqlVariableName(get(csv, "sqlVariableName"))
                    .setSqlText(get(csv, "sqlText"))
                    .setNormalizedSqlText(get(csv, "normalizedSqlText"))
                    .setParameterMode(parseEnum(ParameterMode.class, get(csv, "parameterMode"), ParameterMode.UNKNOWN))
                    .setParameterNames(parseList(get(csv, "parameterNames")))
                    .setParameterCount(parseInt(get(csv, "parameterCount")))
                    .setDynamicSql(parseBoolean(get(csv, "isDynamicSql")))
                    .setRequiresManualReview(parseBoolean(get(csv, "requiresManualReview")))
                    .setManualReviewReason(get(csv, "manualReviewReason"))
                    .setConfidence(parseEnum(ExtractionConfidence.class, get(csv, "confidence"), ExtractionConfidence.LOW))
                    .setNotes(get(csv, "notes"));

            result.add(record);
        }

        return result;
    }

    private String get(CSVRecord record, String name) {
        if (!record.isMapped(name)) {
            return "";
        }
        String value = record.get(name);
        return value == null ? "" : value;
    }

    private int parseInt(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean parseBoolean(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T defaultValue) {
        try {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Enum.valueOf(enumClass, value.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
```

---

## 9. SqlSanityReportWriter.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlSanityReportWriter.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import com.company.sqlvalidator.model.NativeSqlRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SqlSanityReportWriter {

    private static final String[] HEADERS = {
            "id",
            "serviceName",
            "className",
            "methodName",
            "sourceType",
            "confidence",
            "requiresManualReview",
            "isDynamicSql",
            "effectiveSqlText",
            "parserSql",
            "sanityStatus",
            "sanityIssues",
            "parserError",
            "recommendation"
    };

    public void write(Path outputFile, List<SqlSanityResult> results) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(HEADERS)
                     .build())) {

            for (SqlSanityResult result : results) {
                NativeSqlRecord record = result.getSourceRecord();

                printer.printRecord(
                        safe(record.getId()),
                        safe(record.getServiceName()),
                        safe(record.getClassName()),
                        safe(record.getMethodName()),
                        record.getSourceType() == null ? "" : record.getSourceType().name(),
                        record.getConfidence() == null ? "" : record.getConfidence().name(),
                        record.isRequiresManualReview(),
                        record.isDynamicSql(),
                        safe(result.getEffectiveSqlText()),
                        safe(result.getParserSql()),
                        result.getStatus() == null ? "" : result.getStatus().name(),
                        safe(result.getSanityIssuesAsString()),
                        safe(result.getParserError()),
                        safe(result.getRecommendation())
                );
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
```

---

## 10. SqlSanitySummaryWriter.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlSanitySummaryWriter.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SqlSanitySummaryWriter {

    public void write(Path outputFile, List<SqlSanityResult> results) throws IOException {
        Files.createDirectories(outputFile.getParent());

        Map<SqlSanityStatus, Integer> counts = new EnumMap<>(SqlSanityStatus.class);

        for (SqlSanityResult result : results) {
            SqlSanityStatus status = result.getStatus();
            counts.put(status, counts.getOrDefault(status, 0) + 1);
        }

        StringBuilder sb = new StringBuilder();

        sb.append("SQL Text Sanity Check Summary").append(System.lineSeparator());
        sb.append("================================").append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append("Total records: ").append(results.size()).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        for (SqlSanityStatus status : SqlSanityStatus.values()) {
            sb.append(status.name()).append(": ")
                    .append(counts.getOrDefault(status, 0))
                    .append(System.lineSeparator());
        }

        sb.append(System.lineSeparator());
        sb.append("Recommended actions:").append(System.lineSeparator());
        sb.append("- Fix TEXT_SUSPECT_PARSE_ERROR first.").append(System.lineSeparator());
        sb.append("- Review TEXT_SUSPECT_PARSE_OK if it contains literal backslash or Java remnants.").append(System.lineSeparator());
        sb.append("- Do not treat PARSE_ERROR_NO_TEXT_ISSUE as final SQL failure. It may be vendor-specific syntax.").append(System.lineSeparator());
        sb.append("- SKIPPED_DYNAMIC_SQL needs manual resolved SQL before automatic validation.").append(System.lineSeparator());

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
    }
}
```

---

## 11. SqlTextSanityCheckApplication.java

路径：

```text
src/main/java/com/company/sqlvalidator/sanity/SqlTextSanityCheckApplication.java
```

代码：

```java
package com.company.sqlvalidator.sanity;

import com.company.sqlvalidator.model.NativeSqlRecord;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SqlTextSanityCheckApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting SQL Text Sanity Check...");

        Properties properties = loadProperties();

        String inputValue = getOption(args, "--input", properties.getProperty("sanity.input", "./output/sql-inventory.csv"));
        String outputDirValue = getOption(args, "--outputDir", properties.getProperty("sanity.outputDir", "./output"));

        Path inputFile = Paths.get(inputValue);
        Path outputDir = Paths.get(outputDirValue);

        System.out.println("Input CSV:");
        System.out.println("  - " + inputFile.toAbsolutePath());
        System.out.println("Output dir:");
        System.out.println("  - " + outputDir.toAbsolutePath());

        List<NativeSqlRecord> records = new SqlInventoryCsvReader().read(inputFile);

        SqlParserSanityChecker checker = new SqlParserSanityChecker();
        List<SqlSanityResult> results = new ArrayList<>();

        for (NativeSqlRecord record : records) {
            results.add(checker.check(record));
        }

        Path reportFile = outputDir.resolve("sql-sanity-report.csv");
        Path summaryFile = outputDir.resolve("sql-sanity-summary.txt");

        new SqlSanityReportWriter().write(reportFile, results);
        new SqlSanitySummaryWriter().write(summaryFile, results);

        System.out.println();
        System.out.println("SQL sanity check records: " + results.size());
        System.out.println("Output files:");
        System.out.println("  - " + reportFile.toAbsolutePath());
        System.out.println("  - " + summaryFile.toAbsolutePath());
        System.out.println("Done.");
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream inputStream = SqlTextSanityCheckApplication.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (Exception ignored) {
            // Use default values.
        }

        return properties;
    }

    private static String getOption(String[] args, String optionName, String defaultValue) {
        if (args == null || args.length == 0) {
            return defaultValue;
        }

        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }

        return defaultValue;
    }
}
```

---

## 12. application.properties 可选配置

在原来的 `src/main/resources/application.properties` 里追加：

```properties
sanity.input=./output/sql-inventory.csv
sanity.outputDir=./output
```

---

## 13. 如何运行

先运行 Phase 1：

```text
SqlInventoryScannerApplication
```

生成：

```text
output/sql-inventory.csv
```

然后运行 Phase 1.5：

```text
SqlTextSanityCheckApplication
```

生成：

```text
output/sql-sanity-report.csv
output/sql-sanity-summary.txt
```

---

## 14. 结果怎么看

优先关注：

```text
TEXT_SUSPECT_PARSE_ERROR
```

这类最可能说明 Scanner / normalizer 抽取污染。

其次看：

```text
TEXT_SUSPECT_PARSE_OK
```

这类 parser 能过，但文本里有可疑字符。

不要直接把下面状态当成 SQL 错误：

```text
PARSE_ERROR_NO_TEXT_ISSUE
```

它可能只是 JSqlParser 不支持某些 H2/Oracle 特殊语法。

---

## 15. 给 Copilot 的修复提示

```text
I have copied Phase 1.5 SQL Text Sanity Check code into this module.

Please do not rewrite the whole implementation.

Only fix:
1. Compilation errors
2. Maven dependency version issues
3. Apache Commons CSV API compatibility
4. JSqlParser version compatibility
5. Import/package issues

Do not:
1. Connect to H2
2. Connect to PostgreSQL
3. Modify business service code
4. Generate JUnit
5. Implement SQL rewrite rules
6. Change report columns unless necessary for compilation
```
