# SQL 性能对比工具集成与实现报告（MVP v2）

**版本：** v2.0  
**适用系统：** SQL PostgreSQL Validator + SQL Performance Comparator  
**Java 版本：** JDK 17  
**数据库：** H2 / PostgreSQL  
**范围：** 只读 SELECT  
**核心变更：** 性能工具不再接收“已拼接参数的完整 SQL”，改为接收 **JDBC SQL + Typed Parameters**，并使用 `PreparedStatement` 绑定执行。

---

## 1. 本文档替换关系

本文档完整替换上一版：

```text
PERFORMANCE_TOOL_MVP_INTEGRATION_AND_IMPLEMENTATION_REPORT.md
```

上一版中以下设计全部作废：

```text
由 Validator 将 ? 参数替换成 SQL Literal
生成完整 H2 SQL
生成完整 PostgreSQL SQL
ExecutableSqlRenderer
H2SqlLiteralRenderer
PostgresSqlLiteralRenderer
将完整 SQL 直接传给性能工具
```

新的唯一有效方案是：

```text
H2 JDBC SQL
+
PostgreSQL JDBC SQL
+
同一组 Typed Parameters
+
性能工具使用 PreparedStatement 绑定执行
```

如果项目目录中已经保存了旧版 MD，建议：

1. 删除旧版；
2. 将本文档改名为：

```text
PERFORMANCE_TOOL_MVP_INTEGRATION_AND_IMPLEMENTATION_REPORT.md
```

3. 后续只以 v2 内容为准。

---

## 2. 背景与职责边界

现有 `SQL PostgreSQL Validator` 负责：

- 扫描业务工程中的 Native SQL；
- SQL sanity 检查；
- PostgreSQL EXPLAIN；
- H2 真实参数采样；
- H2/PostgreSQL 正确性执行；
- Row Count、Result Hash、Column Signature 对比；
- Baseline Capture；
- Compare PostgreSQL With Baseline；
- Excel 报告。

同事开发的 `SQL Performance Comparator` 负责：

- 接收一条 H2 JDBC SQL；
- 接收一条 PostgreSQL JDBC SQL；
- 接收同一组带类型参数；
- 分别通过 `PreparedStatement` 执行；
- 进行更详细的性能测量；
- 生成 HTML 报告；
- 生成机器可读的 `result.json`。

两个工具保持低耦合：

```text
Validator
    负责选择 SQL、准备参数、调用 JAR、Excel 链接

Performance Comparator
    负责数据库执行、性能分析、HTML 报告
```

---

## 3. 最终 MVP 范围

不处理全部 300 多条 SQL。

性能报告只为：

```text
最慢的前 N 条 SQL
+
手工指定的 SQL
```

默认配置：

```yaml
top-slowest: 20
default-sample-index: 1
require-result-match: true
```

规则：

1. 每条 SQL 最多生成一个 HTML。
2. 默认使用 `sampleIndex = 1`。
3. 自动选择最慢前 20 条。
4. 再加入配置中指定的 SQL ID。
5. 自动选择和手工选择取并集并去重。
6. 串行调用性能工具 JAR。
7. 不为 Sample 2、Sample 3 重复生成报告。
8. 未选中的 SQL 在 Excel 中显示 `Not Selected`。

---

## 4. 为什么改成 JDBC SQL + Typed Parameters

### 4.1 避免 SQL Literal 拼接风险

把：

```sql
SELECT *
FROM CUSTOMER
WHERE NAME = ?
  AND CREATED_DATE >= ?
```

拼成：

```sql
SELECT *
FROM CUSTOMER
WHERE NAME = 'O''Brien'
  AND CREATED_DATE >= DATE '2026-07-20'
```

需要正确处理：

- 单引号；
- 中文和 Unicode；
- NULL；
- Boolean；
- Decimal；
- Date；
- Time；
- Timestamp；
- UUID；
- byte[]；
- Collection；
- 重复参数；
- H2 与 PostgreSQL Literal 语法差异。

这会在 Validator 中引入额外复杂度和错误风险。

### 4.2 更接近业务系统真实运行方式

业务应用一般使用：

```java
PreparedStatement
```

而不是拼接 Literal SQL。

性能工具也使用 `PreparedStatement`，更接近真实应用的执行路径。

### 4.3 避免 SQL 注入和转义错误

参数不直接拼进 SQL：

```java
statement.setString(1, value);
statement.setLong(2, value);
```

由 JDBC Driver 负责类型和转义。

### 4.4 同一组参数可可靠地用于两个数据库

Validator 发送一组参数：

```text
jdbcIndex = 1 → 100
jdbcIndex = 2 → ACTIVE
```

性能工具将完全相同的值分别绑定到 H2 和 PostgreSQL。

不会出现两个数据库独立取样的问题。

### 4.5 减少 Validator 的改动

Validator 不需要新增：

```text
ExecutableSqlRenderer
H2SqlLiteralRenderer
PostgresSqlLiteralRenderer
```

只需要把 Batch 3 已保存的参数转换成性能工具请求 JSON。

---

## 5. 总体流程

```text
cross-db-validation
    ↓
生成正确性结果 CSV
    ↓
performance-report
    ↓
选择最慢前 20 + 手工指定 SQL
    ↓
选择每条 SQL 的 Sample 1
    ↓
生成 Request JSON
    ↓
逐条调用性能工具 JAR
    ↓
性能工具使用 PreparedStatement
分别执行 H2 / PostgreSQL
    ↓
生成 index.html + result.json
    ↓
Validator 生成 sql-performance-report.csv
    ↓
excel-report
    ↓
第三个 Sheet 添加静态 HTML 链接
```

Excel 点击链接时只打开已生成的 HTML，不启动 JAR。

---

## 6. 哪些 SQL 可进入性能测试

默认必须满足：

```text
statementType = SELECT
H2ExecutionStatus = SUCCESS
PostgresExecutionStatus = SUCCESS
SampleComparisonStatus = MATCH
H2 JDBC SQL 存在
PostgreSQL JDBC SQL 存在
参数可解码
JDBC placeholder 数量与参数数量一致
```

默认跳过：

```text
PENDING_SQL_MIGRATION
CURRENT_POSTGRES_NOT_READY
H2_EXECUTION_FAILED
POSTGRES_EXECUTION_FAILED
MISMATCH
MANUAL_MAPPING_REQUIRED
SKIPPED_UNSAFE
SKIPPED_UNSUPPORTED
```

默认配置：

```yaml
require-result-match: true
```

结果不一致时不做性能对比，因为两条 SQL 的业务含义可能已经不同。

---

## 7. SQL 选择规则

### 7.1 自动选择

每条 SQL 只使用一个代表 Sample。

按以下值降序排序：

```text
max(h2ObservedTimeMs, postgresObservedTimeMs)
```

取前：

```text
top-slowest
```

默认：

```text
20
```

### 7.2 手工指定

```yaml
include-sql-ids:
  - SQL-001
  - SQL-038
  - SQL-126
```

### 7.3 最终集合

```text
Top N SQL
UNION
includeSqlIds
```

同一 SQL 只运行一次。

### 7.4 Sample 规则

默认：

```text
sampleIndex = 1
```

如果没有 Sample 1，使用该 SQL 最小的可用 Sample Index。

---

## 8. 性能工具交付形式

同事需要交付：

```text
sql-performance-comparator.jar
```

推荐为独立可执行 Fat JAR。

Validator 通过独立进程调用：

```text
java -jar sql-performance-comparator.jar
```

不直接引用其 Java Class。

优点：

- 不发生 Spring Boot 依赖冲突；
- 不发生 Jackson、SLF4J、JDBC Driver 冲突；
- 两边可以独立升级；
- 不需要互相提供源码；
- 性能工具失败不会破坏 Validator Spring Context。

---

## 9. 命令行接口

性能工具必须支持：

```bash
java -jar sql-performance-comparator.jar \
  --request-file=/absolute/path/performance-request.json \
  --output-dir=/absolute/path/report-directory
```

可选：

```bash
--config=/absolute/path/performance-tool.yml
```

Windows 示例：

```bat
java -jar tools\sql-performance-comparator.jar ^
  --request-file=output\performance\requests\SQL-001.json ^
  --output-dir=output\performance\reports\SQL-001 ^
  --config=config\performance-tool.yml
```

---

## 10. Request JSON v2

```json
{
  "requestVersion": "v2",
  "caseId": "SQL-001",
  "sqlId": "SQL-001",
  "baselineKey": "service|repository|method|source|query|1",
  "sampleIndex": 1,

  "h2JdbcSql": "SELECT * FROM CUSTOMER WHERE ID = ? AND STATUS = ?",
  "postgresJdbcSql": "SELECT * FROM customer WHERE id = ? AND status = ?",

  "parameters": [
    {
      "jdbcIndex": 1,
      "logicalParameterIndex": 1,
      "name": "id",
      "valueType": "LONG",
      "jdbcType": "BIGINT",
      "value": 100
    },
    {
      "jdbcIndex": 2,
      "logicalParameterIndex": 2,
      "name": "status",
      "valueType": "STRING",
      "jdbcType": "VARCHAR",
      "value": "ACTIVE"
    }
  ],

  "resultMatchedBeforePerformanceTest": true,
  "outputFileName": "index.html"
}
```

---

## 11. Request 字段定义

### 11.1 顶层字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| requestVersion | 是 | 固定 `v2` |
| caseId | 是 | 性能 Case 唯一标识 |
| sqlId | 是 | Validator SQL ID |
| baselineKey | 是 | 稳定 SQL 标识 |
| sampleIndex | 是 | 使用的真实参数 Sample |
| h2JdbcSql | 是 | H2 JDBC SQL，保留 `?` |
| postgresJdbcSql | 是 | PostgreSQL JDBC SQL，保留 `?` |
| parameters | 是 | 按 jdbcIndex 排序的参数数组 |
| resultMatchedBeforePerformanceTest | 是 | 正确性是否已验证一致 |
| outputFileName | 否 | 默认 `index.html` |

### 11.2 Parameter 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| jdbcIndex | 是 | JDBC 参数位置，从 1 开始 |
| logicalParameterIndex | 否 | Validator 中的逻辑参数位置 |
| name | 否 | 参数名称，仅用于报告展示 |
| valueType | 是 | 通用参数类型 |
| jdbcType | NULL 时必填 | `java.sql.Types` 对应名称 |
| value | 是 | 参数值；NULL 类型时为 null |
| encoding | BYTES 时必填 | 固定 `BASE64` |

---

## 12. 支持的 valueType

```text
STRING
INTEGER
LONG
BIG_INTEGER
DECIMAL
DOUBLE
BOOLEAN
DATE
TIME
TIMESTAMP
OFFSET_DATE_TIME
INSTANT
UUID
BYTES
NULL
```

### 12.1 示例

#### String

```json
{
  "jdbcIndex": 1,
  "valueType": "STRING",
  "jdbcType": "VARCHAR",
  "value": "O'Brien"
}
```

#### Decimal

```json
{
  "jdbcIndex": 2,
  "valueType": "DECIMAL",
  "jdbcType": "DECIMAL",
  "value": "100.250"
}
```

Decimal 推荐使用 JSON String，避免精度丢失。

#### Date

```json
{
  "jdbcIndex": 3,
  "valueType": "DATE",
  "jdbcType": "DATE",
  "value": "2026-07-20"
}
```

#### Timestamp

```json
{
  "jdbcIndex": 4,
  "valueType": "TIMESTAMP",
  "jdbcType": "TIMESTAMP",
  "value": "2026-07-20T10:30:15.123"
}
```

#### UUID

```json
{
  "jdbcIndex": 5,
  "valueType": "UUID",
  "jdbcType": "OTHER",
  "value": "b7bb46a7-245e-4ca0-9d72-9356a96a02dd"
}
```

#### Bytes

```json
{
  "jdbcIndex": 6,
  "valueType": "BYTES",
  "jdbcType": "BINARY",
  "encoding": "BASE64",
  "value": "AQIDBA=="
}
```

#### NULL

```json
{
  "jdbcIndex": 7,
  "valueType": "NULL",
  "jdbcType": "VARCHAR",
  "value": null
}
```

---

## 13. Collection 参数

Validator 当前配置：

```yaml
collection-sample-size: 1
```

原 SQL：

```sql
WHERE ID IN (:ids)
```

Binding Plan 之后：

```sql
WHERE ID IN (?)
```

请求中发送普通 JDBC 参数：

```json
{
  "jdbcIndex": 1,
  "name": "ids",
  "valueType": "LONG",
  "jdbcType": "BIGINT",
  "value": 100
}
```

性能工具不需要理解 Collection。

如果未来 SQL 已展开为：

```sql
WHERE ID IN (?, ?, ?)
```

请求中发送三个参数位置即可。

---

## 14. 重复逻辑参数

如果一个逻辑参数在 SQL 中出现多次，Validator 按 JDBC 位置展开。

例如：

```sql
WHERE START_DATE >= ?
  AND CREATED_DATE = ?
```

两个位置使用同一逻辑值：

```json
"parameters": [
  {
    "jdbcIndex": 1,
    "logicalParameterIndex": 1,
    "name": "date",
    "valueType": "DATE",
    "jdbcType": "DATE",
    "value": "2026-07-20"
  },
  {
    "jdbcIndex": 2,
    "logicalParameterIndex": 1,
    "name": "date",
    "valueType": "DATE",
    "jdbcType": "DATE",
    "value": "2026-07-20"
  }
]
```

性能工具只需要按 `jdbcIndex` 绑定，不需要分析逻辑参数关系。

---

## 15. 性能工具参数绑定实现

同事的工具应实现一个集中式 Binder，例如：

```java
public final class TypedParameterBinder {

    public void bind(
            PreparedStatement statement,
            List<PerformanceParameter> parameters
    ) throws SQLException {

        for (PerformanceParameter parameter : parameters) {
            int index = parameter.jdbcIndex();

            switch (parameter.valueType()) {
                case STRING ->
                        statement.setString(index, parameter.stringValue());

                case INTEGER ->
                        statement.setInt(index, parameter.integerValue());

                case LONG ->
                        statement.setLong(index, parameter.longValue());

                case BIG_INTEGER ->
                        statement.setBigDecimal(
                                index,
                                new BigDecimal(parameter.stringValue())
                        );

                case DECIMAL ->
                        statement.setBigDecimal(
                                index,
                                new BigDecimal(parameter.stringValue())
                        );

                case DOUBLE ->
                        statement.setDouble(index, parameter.doubleValue());

                case BOOLEAN ->
                        statement.setBoolean(index, parameter.booleanValue());

                case DATE ->
                        statement.setObject(
                                index,
                                LocalDate.parse(parameter.stringValue())
                        );

                case TIME ->
                        statement.setObject(
                                index,
                                LocalTime.parse(parameter.stringValue())
                        );

                case TIMESTAMP ->
                        statement.setObject(
                                index,
                                LocalDateTime.parse(parameter.stringValue())
                        );

                case OFFSET_DATE_TIME ->
                        statement.setObject(
                                index,
                                OffsetDateTime.parse(parameter.stringValue())
                        );

                case INSTANT ->
                        statement.setTimestamp(
                                index,
                                Timestamp.from(
                                        Instant.parse(parameter.stringValue())
                                )
                        );

                case UUID ->
                        statement.setObject(
                                index,
                                UUID.fromString(parameter.stringValue())
                        );

                case BYTES ->
                        statement.setBytes(
                                index,
                                Base64.getDecoder()
                                        .decode(parameter.stringValue())
                        );

                case NULL ->
                        statement.setNull(
                                index,
                                resolveJdbcType(parameter.jdbcType())
                        );
            }
        }
    }
}
```

具体类名可调整，但必须集中处理，不要散落在 H2 和 PostgreSQL执行代码中。

---

## 16. 性能工具执行前校验

每次调用必须检查：

1. `requestVersion = v2`；
2. `caseId` 非空；
3. 两条 SQL 都非空；
4. 两条 SQL 都是只读 SELECT；
5. 不包含多条语句；
6. 不包含 UPDATE、DELETE、INSERT、MERGE、DDL；
7. 参数按 `jdbcIndex` 唯一；
8. jdbcIndex 从 1 开始；
9. H2 SQL 的 `?` 数量与参数数量一致；
10. PostgreSQL SQL 的 `?` 数量与参数数量一致；
11. NULL 参数提供 `jdbcType`；
12. BYTES 参数使用 BASE64；
13. `resultMatchedBeforePerformanceTest = true`。

验证失败：

```text
status = INVALID_REQUEST
exitCode = 1
```

---

## 17. 两个数据库如何执行

必须对两个数据库使用同一组参数。

伪代码：

```java
PerformanceExecutionResult h2Result =
        execute(
                h2Connection,
                request.h2JdbcSql(),
                request.parameters()
        );

PerformanceExecutionResult postgresResult =
        execute(
                postgresConnection,
                request.postgresJdbcSql(),
                request.parameters()
        );
```

执行方法：

```java
try (PreparedStatement statement =
             connection.prepareStatement(jdbcSql)) {

    statement.setQueryTimeout(timeoutSeconds);
    typedParameterBinder.bind(statement, parameters);

    long start = System.nanoTime();

    try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
            consumeRow(resultSet);
        }
    }

    long elapsedNanos = System.nanoTime() - start;
}
```

计时必须包含 ResultSet 完整消费。

---

## 18. 性能工具数据库配置

推荐外部配置：

```yaml
performance-tool:
  h2:
    jdbc-url:
    username:
    password:
    schema:

  postgres:
    jdbc-url:
    username:
    password:
    schema:

  execution:
    warmup-count: 1
    measured-run-count: 3
    timeout-seconds: 120
```

数据库连接信息不放入 Request JSON。

---

## 19. 输出目录

每个 SQL 一个目录：

```text
output/performance/reports/SQL-001/
├── index.html
└── result.json
```

HTML 必须离线可打开，不依赖公网 CDN。

---

## 20. result.json

```json
{
  "responseVersion": "v2",
  "caseId": "SQL-001",
  "status": "SUCCESS",
  "h2ExecutionSuccess": true,
  "postgresExecutionSuccess": true,
  "h2AverageTimeMs": 120.5,
  "postgresAverageTimeMs": 85.2,
  "h2MinTimeMs": 110,
  "h2MaxTimeMs": 135,
  "postgresMinTimeMs": 80,
  "postgresMaxTimeMs": 92,
  "htmlReport": "index.html",
  "errorCode": "",
  "errorMessage": ""
}
```

---

## 21. 状态与 Exit Code

状态：

```text
SUCCESS
INVALID_REQUEST
H2_EXECUTION_FAILED
POSTGRES_EXECUTION_FAILED
BOTH_EXECUTION_FAILED
REPORT_GENERATION_FAILED
TIMEOUT
INTERNAL_ERROR
```

Exit Code：

| Exit Code | 含义 |
|---:|---|
| 0 | 成功 |
| 1 | 请求不合法 |
| 2 | H2 执行失败 |
| 3 | PostgreSQL 执行失败 |
| 4 | 两边执行失败 |
| 5 | HTML/result.json 生成失败 |
| 6 | 超时 |
| 10 | 内部错误 |

Validator 判断成功：

```text
exitCode = 0
AND result.json.status = SUCCESS
AND index.html 存在
```

---

## 22. Validator 侧实现

新增独立 Phase：

```text
performance-report
```

主要步骤：

```java
readCrossDatabaseResult();
selectRepresentativeSamplePerSql();
selectTopSlowestAndManualIncludes();
decodeSavedParameterTuple();
convertToPerformanceParameters();
writeRequestJson();
invokePerformanceJar();
readResultJson();
writePerformanceReportCsv();
```

不修改：

```text
inventory
sanity
validation-explain
cross-db-validation
```

Excel Report 只增加读取性能结果和写静态链接的能力。

---

## 23. Validator 性能配置

```yaml
validator:
  performance-report:
    enabled: true

    tool-jar: ./tools/sql-performance-comparator.jar
    tool-config: ./config/performance-tool.yml

    cross-db-baseline-input: ./output/sql-select-baseline.csv
    cross-db-comparison-input: ./output/sql-select-comparison.csv

    request-directory: ./output/performance/requests
    report-directory: ./output/performance/reports
    result-output: ./output/sql-performance-report.csv

    top-slowest: 20
    default-sample-index: 1
    require-result-match: true
    timeout-seconds: 180

    include-sql-ids: []
```

---

## 24. 批量调用方式

MVP 串行运行：

```java
for (PerformanceCase performanceCase : selectedCases) {
    writeRequestJson(performanceCase);
    invokePerformanceJar(performanceCase);
    readResultJson(performanceCase);
    writeResultRow(performanceCase);
}
```

不并发。

每条 SQL 一次 JAR 调用、一个 HTML。

这样：

```text
最慢 20 条
→ 大约 20 次 JAR 调用
→ 大约 20 个 HTML
```

不会生成 300 多个 HTML。

---

## 25. Performance Report CSV

```text
caseId
baselineKey
sqlId
sampleIndex
selectionReason
performanceStatus
h2AverageTimeMs
postgresAverageTimeMs
htmlReportPath
errorCode
errorMessage
```

selectionReason：

```text
TOP_SLOWEST
MANUAL_INCLUDE
TOP_SLOWEST_AND_MANUAL_INCLUDE
```

performanceStatus：

```text
SUCCESS
NOT_SELECTED
NOT_ELIGIBLE
PARAMETER_DECODE_FAILED
TOOL_FAILED
TIMEOUT
REPORT_MISSING
```

---

## 26. Excel 第三个 Sheet

简化列：

```text
Baseline Key
SQL ID
Current SQL ID
Sample Index
Parameter Values
H2 Status
PostgreSQL Status
Result Comparison
H2 Observed Time (ms)
PostgreSQL Observed Time (ms)
Performance Report
```

Performance Report 显示：

| 条件 | 内容 |
|---|---|
| HTML 已生成 | `Open Performance Report` |
| 同一 SQL 的其他 Sample | `Covered by Sample 1` |
| 未选中 | `Not Selected` |
| 结果不一致 | `Result Mismatch` |
| PG 未完成 | `Pending Migration` |
| 参数解析失败 | `Parameter Decode Failed` |
| 性能工具失败 | `Report Generation Failed` |

成功链接：

```text
performance/reports/SQL-001/index.html
```

---

## 27. 同事需要实现的最小模块

推荐模块：

```text
performance/
├── PerformanceToolApplication.java
├── PerformanceRequest.java
├── PerformanceParameter.java
├── ParameterValueType.java
├── TypedParameterBinder.java
├── SelectRequestValidator.java
├── DatabaseExecutionService.java
├── PerformanceMeasurementService.java
├── HtmlReportWriter.java
├── PerformanceResult.java
├── ResultJsonWriter.java
└── ExitCodeMapper.java
```

不要求使用这些精确名称，但职责应清晰。

---

## 28. 同事实现顺序

### Step 1：Request 和校验

- 定义 Request v2；
- JSON 读取；
- 参数校验；
- SELECT-only 校验；
- placeholder 数量校验。

### Step 2：TypedParameterBinder

- 支持全部约定类型；
- NULL 使用 jdbcType；
- BYTES 使用 Base64；
- 单元测试。

### Step 3：单数据库执行

- PreparedStatement；
- 超时；
- 完整消费 ResultSet；
- 执行时间；
- 错误捕获。

### Step 4：双数据库性能测量

- H2；
- PostgreSQL；
- warmup；
- measured runs；
- Min/Max/Average。

### Step 5：输出

- index.html；
- result.json；
- Exit Code。

### Step 6：集成测试

- H2 + PostgreSQL；
- 字符串单引号；
- 中文；
- Date/Timestamp；
- UUID；
- NULL；
- Timeout；
- Windows 路径空格。

---

## 29. 优势总结

相较于完整 SQL 拼接方案，新方案的优势：

1. **代码更少**：Validator 不需要 SQL Literal Renderer。
2. **风险更低**：避免引号、日期、NULL、UUID 等拼接错误。
3. **更安全**：避免 SQL 注入式字符串拼接。
4. **更真实**：PreparedStatement 更接近业务应用。
5. **类型明确**：参数类型由协议清晰定义。
6. **跨库一致**：同一参数列表用于 H2 和 PostgreSQL。
7. **职责清晰**：Validator 准备测试 Case，性能工具负责执行。
8. **维护简单**：双方通过 JSON v2 协议独立演进。
9. **影响更小**：不修改原有 Cross DB Phase 的参数模型和执行逻辑。
10. **更适合交接**：只需要两个 JAR 和配置，不交换源码。

---

## 30. 性能工具验收标准

同事交付的 JAR 必须满足：

1. JDK 17 可运行。
2. 支持 Request JSON v2。
3. 支持 JDBC SQL + Typed Parameters。
4. 使用 PreparedStatement。
5. H2/PG 使用相同参数。
6. 只允许 SELECT。
7. placeholder 数量不匹配时拒绝。
8. 支持 STRING、数字、BOOLEAN、日期时间、UUID、BYTES、NULL。
9. 完整消费 ResultSet。
10. 支持查询超时。
11. 生成离线 index.html。
12. 生成 result.json v2。
13. 返回约定 Exit Code。
14. 路径包含空格时可运行。
15. SQL 包含中文、换行、单引号时可从 JSON 正确读取。
16. 一个 Case 失败不会影响下一次独立调用。

---

## 31. 最终结论

最终 MVP 使用：

```text
最慢前 20
+
手工指定 SQL
+
每条 SQL 一个 HTML
+
默认 Sample 1
+
H2 JDBC SQL
+
PostgreSQL JDBC SQL
+
同一组 Typed Parameters
+
PreparedStatement
+
独立 JAR 串行调用
+
Excel 静态 HTML 链接
```

不使用：

```text
本地拼接完整 SQL
SQL Literal Renderer
300 多条 SQL 全量生成 HTML
每个 Sample 都生成 HTML
Excel 点击时启动 JAR
localhost 服务
Excel 宏
并发性能测试
```
