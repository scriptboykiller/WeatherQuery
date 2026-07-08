# 01_Dexter_SQL_Validation_Scope.md

## 1. 文档目的

本文档用于固定 Dexter 在本次 PostgreSQL 迁移项目中的工作边界、MVP 目标、工具设计方向和 Copilot/Hermes 执行规则。

本项目不是让 Dexter 负责完整数据库迁移，也不是负责 PostgreSQL 性能调优。Dexter 当前负责的是：

> 从现有 Spring Boot 微服务中全量提取 Native SQL，生成可运行的 JUnit 验证用例，连接已经迁移好的 PostgreSQL 数据库，验证这些 SQL 在 PostgreSQL 中是否存在语法或基础执行错误，并输出问题 SQL、自动修复建议和 Java 修改参考。

---

## 2. 项目背景

当前系统经历过数据库迁移路径：

```text
Oracle
  ↓
H2
  ↓
PostgreSQL
```

历史上因为 Oracle License 成本问题，系统从 Oracle 迁移到 H2。现在由于公司数据中心上云，需要最终统一迁移到云端 PostgreSQL。

当前系统为：

```text
Spring Boot + Spring Data JPA + Native SQL
```

项目中存在大量硬编码 Native SQL，这些 SQL 可能包含：

- Oracle 方言
- H2 方言
- PostgreSQL 不兼容函数
- PostgreSQL 保留字问题
- 日期函数差异
- Sequence 写法差异
- DUAL 表写法
- 字段大小写和引号问题
- 参数占位符问题

本次工作的目标是提前发现这些 SQL 在 PostgreSQL 中的兼容性问题。

---

## 3. 涉及微服务范围

根据会议内容，重点关注以下含大量 Native SQL 的业务微服务：

```text
DRM
Workflow
OS
Entitlement
API Integration
Graph
```

其中改造工作量较大的服务预计为：

```text
DRM
Workflow
API Integration
```

以下服务通常不属于本次 Native SQL 抽取范围：

```text
Gateway
Eureka
Config
其他无业务数据库访问的基础服务
```

如果后续在代码中发现其他服务包含 Native SQL，也可以纳入扫描范围，但不要主动扩大范围到无 SQL 的基础服务。

---

## 4. Dexter 的职责边界

### 4.1 Dexter 负责

Dexter 当前负责以下内容：

```text
1. 扫描 6 个微服务源码
2. 全量提取 Native SQL
3. 识别 SQL 所在类、方法、来源类型
4. 识别 SQL 参数名
5. 尽量推断 SQL 参数类型
6. 为 SQL 生成可执行的 JUnit 验证用例
7. 连接真实 PostgreSQL 测试库执行 SQL
8. 判断 SQL 是否存在 PostgreSQL 语法或基础执行错误
9. 记录失败 SQL、错误信息和所在代码位置
10. 对常见 Oracle/H2 → PostgreSQL 差异进行规则化自动修复
11. 修复后再次连接 PostgreSQL 执行验证
12. 如果修复后成功，输出原 Java 代码应如何修改的参考
13. 如果无法自动修复，标记为 MANUAL_REVIEW
```

### 4.2 Dexter 不负责

以下内容不属于 Dexter 当前职责：

```text
1. PostgreSQL 建库
2. PostgreSQL 建表
3. 表结构迁移
4. 索引迁移
5. View 迁移
6. 生产数据迁移
7. 多线程数据迁移脚本重构
8. 数据总量比对
9. 表行数校验
10. 数据一致性最终验收
11. PostgreSQL DBA 参数调优
12. 慢 SQL 性能专项优化
13. 全量业务 Regression Testing
14. QA 测试执行
15. 页面人工回归测试
```

---

## 5. 依赖关系

Dexter 的工作依赖以下前置条件：

```text
1. PostgreSQL 测试库账号可用
2. PostgreSQL 测试库中已经存在迁移后的表结构
3. PostgreSQL 测试库中已经存在必要的测试数据
4. 同事完成或阶段性完成数据库对象迁移
5. 应用连接 PostgreSQL 的 JDBC 配置可用
```

如果 PostgreSQL 账号或数据库对象尚未准备好，Dexter 可以先做：

```text
1. Native SQL Inventory Scanner
2. SQL 参数解析
3. JUnit 代码生成
4. 报告格式设计
5. 常见 SQL Rewrite Rule 编写
```

但不能完成真实 PostgreSQL 执行验证。

---

## 6. 本工具的定位

工具名称建议：

```text
Native SQL PostgreSQL Compatibility Validator
```

工具定位：

> 一个 Java-first 的迁移辅助工具，用于从 Spring Boot 微服务中提取 Native SQL，并在真实 PostgreSQL 数据库中验证这些 SQL 是否可以正常执行。

设计原则：

```text
1. Java 实现优先
2. 不依赖运行时 AI
3. 结果必须由真实 PostgreSQL 执行验证
4. AI/Copilot 只用于开发阶段辅助，不作为正式工具运行依赖
5. 优先保证 SQL 抽取完整性
6. 优先输出清晰报告
7. 不追求一次性自动解决所有复杂 SQL
8. 无法自动处理的 SQL 标记 MANUAL_REVIEW
```

---

## 7. 工具总体流程

```text
扫描源码
  ↓
识别 Native SQL
  ↓
生成 SQL Inventory
  ↓
识别 SQL 参数
  ↓
生成参数 Mock 值
  ↓
生成 JUnit
  ↓
连接 PostgreSQL
  ↓
执行 SQL
  ↓
记录 PASS / FAIL
  ↓
失败 SQL 进入 Rewrite Rule
  ↓
生成 Candidate SQL
  ↓
再次连接 PostgreSQL 执行
  ↓
输出最终报告
```

---

## 8. Native SQL 来源范围

工具至少需要覆盖以下 Native SQL 来源。

### 8.1 Spring Data JPA Repository @Query

示例：

```java
@Query(
    value = "SELECT * FROM USER_TABLE WHERE USER_ID = :userId",
    nativeQuery = true
)
List<User> findUsers(@Param("userId") Long userId);
```

需要提取：

```text
className
methodName
sqlText
parameterName: userId
parameterType: Long
sourceType: SPRING_DATA_QUERY
```

---

### 8.2 EntityManager.createNativeQuery

示例：

```java
Query query = entityManager.createNativeQuery(interfaceQuery);
query.setParameter("interfaceName", interfaceName);
```

需要提取：

```text
className
methodName
sqlVariableName
sqlText or dynamicSqlExpression
parameterName: interfaceName
parameterType: inferred from variable or method parameter
sourceType: ENTITY_MANAGER
```

如果 SQL 是复杂动态拼接，无法 100% 还原，则记录为：

```text
requiresManualReview = true
reason = DYNAMIC_SQL
```

---

### 8.3 JdbcTemplate

示例：

```java
jdbcTemplate.query(sql, rowMapper, param1, param2);
```

需要提取：

```text
className
methodName
sqlVariableName
sqlText
parameterMode: POSITIONAL
parameterCount
sourceType: JDBC_TEMPLATE
```

---

### 8.4 NamedParameterJdbcTemplate

示例：

```java
namedParameterJdbcTemplate.query(sql, params, rowMapper);
```

需要提取：

```text
className
methodName
sqlText
parameterMode: NAMED
parameterNames
sourceType: NAMED_PARAMETER_JDBC_TEMPLATE
```

---

### 8.5 外部 SQL 文件

如果项目中存在 `.sql` 文件，也可以纳入后续版本。

MVP 阶段如果没有明确发现 `.sql` 文件，可以暂不处理。

---

## 9. SQL Inventory 输出字段

建议每条 SQL 记录包含以下字段：

```text
id
serviceName
moduleName
filePath
className
methodName
sourceType
sqlText
normalizedSqlText
parameterMode
parameterNames
parameterTypes
sampleValues
isDynamicSql
requiresManualReview
lineNumber
notes
```

示例：

```text
id: DRM-0001
serviceName: DRM
filePath: src/main/java/.../FieldNameRepository.java
className: FieldNameRepository
methodName: getNextSequenceId
sourceType: SPRING_DATA_QUERY
sqlText: SELECT FIELD_NAME_SEQ.NEXTVAL FROM DUAL
parameterMode: NONE
requiresManualReview: false
```

---

## 10. SQL 参数处理策略

历史问题已经证明：

> 只抽 SQL 不够，必须同时处理参数。

### 10.1 命名参数

示例：

```sql
WHERE MODEL_ID = :modelId
```

优先从以下位置推断类型：

```text
1. Repository 方法参数 @Param("modelId") Long modelId
2. 方法参数名 modelId
3. query.setParameter("modelId", modelId)
4. MapSqlParameterSource.addValue("modelId", value)
5. SqlParameterSource
6. 变量名和字段名推断
```

---

### 10.2 位置参数

示例：

```sql
WHERE USER_ID = ?
```

或：

```sql
WHERE USER_ID = ?1
```

记录：

```text
parameterMode = POSITIONAL
parameterCount = N
```

如果无法推断类型，则先使用默认策略。

---

### 10.3 默认 Mock 值策略

如果能推断 Java 类型：

```text
String        -> "TEST"
Long          -> 1L
Integer       -> 1
BigDecimal    -> BigDecimal.ONE
Boolean       -> true
LocalDate     -> LocalDate.now()
LocalDateTime -> LocalDateTime.now()
Date          -> new Date()
Collection    -> List.of(1)
Enum          -> first enum value or "TEST"
```

如果不能推断：

```text
UNKNOWN -> "TEST"
```

但报告中必须标记：

```text
parameterTypeConfidence = LOW
```

---

### 10.4 参数生成的注意事项

本阶段参数值只用于验证 SQL 语法和基础执行，不保证业务结果正确。

允许出现：

```text
0 rows returned
```

只要 SQL 可以被 PostgreSQL 正常解析并执行，即视为基础验证通过。

但如果因为参数类型不匹配导致执行失败，需要记录为：

```text
FAIL_PARAMETER_TYPE
```

而不是直接判断为 SQL 方言错误。

---

## 11. SQL 执行成功标准

### 11.1 成功

以下情况视为成功：

```text
1. SELECT 执行成功，无语法错误
2. 返回 0 行也算成功
3. 返回多行也算成功
4. COUNT 查询成功
5. EXISTS 查询成功
6. Sequence 查询成功
```

### 11.2 失败

以下情况视为失败：

```text
1. PostgreSQL 语法错误
2. PostgreSQL 函数不存在
3. 表不存在
4. 列不存在
5. 类型转换错误
6. 参数绑定失败
7. 保留字冲突
8. Schema 访问错误
9. SQL 动态拼接无法还原
```

其中：

```text
表不存在 / 列不存在
```

需要进一步判断是：

```text
1. 数据库迁移未完成
2. Schema 未指定
3. SQL 本身引用了旧对象
4. 大小写或引号问题
```

不要直接当作 SQL 语法错误。

---

## 12. 状态定义

每条 SQL 最终状态建议限定为以下几类：

```text
PASS
FAIL_THEN_FIXED
FAIL_AFTER_FIX
FAIL_PARAMETER_TYPE
MANUAL_REVIEW
SKIPPED_DYNAMIC_SQL
SKIPPED_UNSUPPORTED_SOURCE
```

含义：

```text
PASS:
原 SQL 直接在 PostgreSQL 执行成功。

FAIL_THEN_FIXED:
原 SQL 在 PostgreSQL 执行失败，经规则修复后再次执行成功。

FAIL_AFTER_FIX:
原 SQL 执行失败，规则尝试修复后仍失败。

FAIL_PARAMETER_TYPE:
主要失败原因是参数类型无法正确生成或绑定。

MANUAL_REVIEW:
工具无法安全判断，需要人工处理。

SKIPPED_DYNAMIC_SQL:
SQL 动态拼接复杂，MVP 阶段无法可靠还原。

SKIPPED_UNSUPPORTED_SOURCE:
发现 SQL 但暂不支持该来源类型。
```

---

## 13. 自动修复规则范围

MVP 阶段只实现高确定性的规则，不做复杂语义重写。

### 13.1 Sequence + DUAL

Oracle/H2 写法：

```sql
SELECT FIELD_NAME_SEQ.NEXTVAL FROM DUAL
```

PostgreSQL 写法：

```sql
SELECT nextval('field_name_seq')
```

---

### 13.2 NVL

Oracle/H2 写法：

```sql
NVL(a, b)
```

PostgreSQL 写法：

```sql
COALESCE(a, b)
```

---

### 13.3 SYSDATE

Oracle/H2 写法：

```sql
SYSDATE
```

PostgreSQL 写法：

```sql
CURRENT_TIMESTAMP
```

---

### 13.4 SYSTIMESTAMP

Oracle 写法：

```sql
SYSTIMESTAMP
```

PostgreSQL 写法：

```sql
CURRENT_TIMESTAMP
```

---

### 13.5 ROWNUM

Oracle 写法：

```sql
WHERE ROWNUM <= 10
```

PostgreSQL 写法：

```sql
LIMIT 10
```

注意：ROWNUM 的复杂场景不要自动修复，标记 MANUAL_REVIEW。

---

### 13.6 TO_DATE / TO_TIMESTAMP

简单场景可以尝试转换，复杂格式标记 MANUAL_REVIEW。

---

### 13.7 Reserved Keywords

如果发现字段名为 PostgreSQL 保留字，例如：

```text
value
user
order
group
```

可尝试加双引号：

```sql
"value"
```

但自动加引号风险较高，建议先记录为候选修复，执行验证成功后再输出建议。

---

## 14. 自动修复原则

自动修复必须遵守：

```text
1. 不直接修改业务源码
2. 只生成 suggestedSql
3. suggestedSql 必须再次连接 PostgreSQL 执行
4. 只有复跑成功，才能输出 Java 修改参考
5. 复跑失败则标记 FAIL_AFTER_FIX 或 MANUAL_REVIEW
6. 所有修改都必须在报告中保留 originalSql 和 suggestedSql
```

禁止：

```text
1. 未验证就声称修复成功
2. 直接覆盖原 Java 文件
3. 让 AI 直接修改生产代码
4. 对复杂 SQL 做不安全重写
```

---

## 15. JUnit 生成目标

生成的 JUnit 用例只用于 SQL PostgreSQL 兼容性验证。

JUnit 不负责业务断言。

### 15.1 JUnit 成功条件

```text
SQL execute without PostgreSQL error
```

不要求：

```text
返回指定业务结果
返回指定行数
校验页面结果
校验业务流程
```

---

### 15.2 JUnit 推荐结构

```text
src/test/java/.../sqlvalidation/
    NativeSqlValidationTest.java
    GeneratedSqlInventory.java
    SqlValidationRunner.java
    SqlMockParameterFactory.java
```

---

### 15.3 JUnit 执行方式

推荐使用真实 PostgreSQL 测试库：

```text
spring.datasource.url=jdbc:postgresql://host:port/db
spring.datasource.username=xxx
spring.datasource.password=xxx
```

不建议使用 H2 或 Mock 数据库验证 PostgreSQL 兼容性。

---

## 16. 报告输出

至少输出两种报告：

```text
1. sql-inventory.csv
2. sql-validation-report.csv
```

如果时间允许，再输出：

```text
3. sql-validation-report.md
4. sql-validation-report.xlsx
```

### 16.1 sql-inventory.csv

字段：

```text
id
serviceName
filePath
className
methodName
sourceType
lineNumber
sqlText
parameterNames
parameterTypes
isDynamicSql
requiresManualReview
```

### 16.2 sql-validation-report.csv

字段：

```text
id
serviceName
className
methodName
sourceType
originalSql
status
postgresErrorCode
postgresErrorMessage
suggestedSql
suggestedJavaChange
notes
```

---

## 17. 示例报告

```text
id: DRM-0001
serviceName: DRM
className: FieldNameRepository
methodName: getNextSequenceId
sourceType: SPRING_DATA_QUERY

originalSql:
SELECT FIELD_NAME_SEQ.NEXTVAL FROM DUAL

status:
FAIL_THEN_FIXED

postgresError:
relation "dual" does not exist

suggestedSql:
SELECT nextval('field_name_seq')

suggestedJavaChange:
Replace @Query value with:
SELECT nextval('field_name_seq')

notes:
Verified by executing suggested SQL against PostgreSQL.
```

---

## 18. MVP 实施阶段

### Phase 1：SQL Inventory Scanner

目标：

```text
扫描源码，抽取所有 Native SQL，生成 sql-inventory.csv
```

覆盖：

```text
@Query(nativeQuery = true)
EntityManager.createNativeQuery
JdbcTemplate
NamedParameterJdbcTemplate
```

暂不做：

```text
真实数据库执行
自动修复
复杂动态 SQL 还原
```

---

### Phase 2：Parameter Parser

目标：

```text
识别 SQL 参数名、参数类型、生成 mock 参数
```

覆盖：

```text
@Param
setParameter
MapSqlParameterSource
方法参数类型
简单变量类型
```

---

### Phase 3：PostgreSQL Execution Validator

目标：

```text
连接真实 PostgreSQL，逐条执行 SQL，输出 PASS / FAIL
```

---

### Phase 4：Rewrite Rule Engine

目标：

```text
对高确定性 Oracle/H2 → PostgreSQL 差异进行自动修复，并复跑验证
```

---

### Phase 5：JUnit Generator

目标：

```text
把 SQL Inventory + 参数 + 执行逻辑生成 JUnit 测试入口
```

---

### Phase 6：Report and Java Change Suggestion

目标：

```text
输出最终报告和 Java 修改参考
```

---

## 19. Copilot / Hermes 执行规则

使用 Copilot 或 Hermes 时，必须遵守以下规则。

### 19.1 不要让 Copilot 扩大范围

禁止让 Copilot 主动实现：

```text
完整数据库迁移
建表脚本
索引优化
慢 SQL 分析平台
生产割接脚本
QA 回归测试工具
```

### 19.2 每次只做一个阶段

推荐顺序：

```text
1. 先做 SQL Inventory Scanner
2. 再做 Parameter Parser
3. 再做 PostgreSQL Execution Validator
4. 再做 Rewrite Rule Engine
5. 再做 JUnit Generator
6. 最后做 Report Writer
```

### 19.3 所有工具代码优先 Java

优先使用：

```text
Java
Maven or Gradle
JUnit 5
PostgreSQL JDBC Driver
Spring JDBC if project already available
JavaParser if allowed
Regex fallback if JavaParser not available
```

### 19.4 AI 只作为开发辅助

AI 可以用于：

```text
1. 生成 Java 代码骨架
2. 分析 PostgreSQL 报错
3. 补充 Rewrite Rule
4. 生成测试样例
5. 辅助改写复杂 SQL
```

AI 不应该用于：

```text
1. 工具运行时每条 SQL 调用 AI
2. 未经数据库验证就输出最终结论
3. 直接修改生产业务代码
4. 替代 PostgreSQL 执行验证
```

---

## 20. 成功验收标准

MVP 成功标准：

```text
1. 能扫描 6 个微服务源码
2. 能生成完整 sql-inventory.csv
3. 能识别大部分 @Query(nativeQuery=true)
4. 能识别 EntityManager.createNativeQuery
5. 能识别 JdbcTemplate / NamedParameterJdbcTemplate
6. 能识别常见命名参数
7. 能连接真实 PostgreSQL 执行 SQL
8. 能输出 PASS / FAIL 报告
9. 能记录 PostgreSQL 原始错误
10. 能对至少 3 类常见 Oracle/H2 差异自动修复并复跑
```

不作为 MVP 成功标准：

```text
1. 100% 自动修复所有 SQL
2. 100% 推断所有参数类型
3. 覆盖所有复杂动态 SQL
4. 验证业务返回结果正确性
5. 完成 SQL 性能调优
6. 完成 QA 回归测试
```

---

## 21. 推荐项目目录

可以在任意一个服务外部创建独立工具目录：

```text
sql-postgres-validator/
    README.md
    pom.xml
    src/main/java/com/company/sqlvalidator/
        SqlValidatorApplication.java
        scanner/
            NativeSqlScanner.java
            SpringDataQueryScanner.java
            EntityManagerScanner.java
            JdbcTemplateScanner.java
        model/
            NativeSqlRecord.java
            SqlParameterInfo.java
            SqlValidationResult.java
            SqlSourceType.java
            SqlValidationStatus.java
        parser/
            SqlParameterParser.java
            JavaParameterTypeResolver.java
        executor/
            PostgresSqlExecutor.java
            SqlExecutionRequest.java
        rewrite/
            SqlRewriteRule.java
            OracleH2ToPostgresRuleEngine.java
            SequenceNextValRule.java
            NvlRule.java
            SysdateRule.java
            RownumRule.java
        report/
            SqlInventoryCsvWriter.java
            SqlValidationReportWriter.java
        junit/
            JUnitTestGenerator.java
```

---

## 22. 下一步行动

下一步不要直接开始写全部功能。

建议先完成：

```text
Phase 1：SQL Inventory Scanner
```

第一阶段目标非常明确：

> 输入 6 个微服务源码路径，输出 sql-inventory.csv。

第一阶段不需要 PostgreSQL 账号，不依赖数据库环境，可以立即开始。

完成 Phase 1 后，再继续 Phase 2 参数解析。

---

## 23. 给 Copilot 的限制提示

在 IntelliJ 中使用 Copilot 时，可以先给它以下提示：

```text
You are working on a Java-based Native SQL PostgreSQL Compatibility Validator.

Do not implement database migration, table creation, index migration, performance tuning, or regression testing.

Focus only on scanning Spring Boot Java source code to extract Native SQL from:
1. @Query(nativeQuery = true)
2. EntityManager.createNativeQuery(...)
3. JdbcTemplate
4. NamedParameterJdbcTemplate

Generate clean Java code that outputs sql-inventory.csv with:
serviceName, filePath, className, methodName, sourceType, lineNumber, sqlText, parameterNames, isDynamicSql, requiresManualReview.

Do not modify business source code.
Do not call AI at runtime.
Do not connect to PostgreSQL in Phase 1.
Keep the design simple and MVP-oriented.
```

---

## 24. 重要结论

本工具的正确方向是：

```text
Java-first
PostgreSQL-verified
AI-assisted during development only
MVP-oriented
No production code auto-modification
```

Dexter 当前最应该优先完成的是：

```text
SQL Inventory Scanner
```

而不是一次性做完整迁移工具。
