# 02_SQL_Inventory_Scanner_Design.md

## 1. 文档目的

本文档用于设计 Phase 1：

> **SQL Inventory Scanner**

Phase 1 的唯一目标是：

> 扫描 6 个 Spring Boot 微服务源码，尽可能完整地提取项目中的 Native SQL，并生成 `sql-inventory.csv`。

本阶段不连接 PostgreSQL，不执行 SQL，不做自动修复，不生成最终 JUnit。

---

## 2. Phase 1 范围

### 2.1 Phase 1 要做什么

Phase 1 只做以下事情：

```text
1. 输入一个或多个微服务源码根目录
2. 扫描 Java 文件
3. 识别 Native SQL 来源
4. 提取 SQL 文本
5. 识别 SQL 所在文件、类、方法、行号
6. 初步识别参数名
7. 判断是否为动态 SQL
8. 标记是否需要人工 Review
9. 输出 sql-inventory.csv
```

---

### 2.2 Phase 1 不做什么

Phase 1 不做以下事情：

```text
1. 不连接 PostgreSQL
2. 不验证 SQL 是否能运行
3. 不做 SQL 自动修复
4. 不生成 JUnit
5. 不修改业务代码
6. 不做性能调优
7. 不做建表、建库、索引迁移
8. 不判断业务返回结果是否正确
```

---

## 3. 输入与输出

### 3.1 输入

输入为一个或多个微服务源码路径，例如：

```text
D:\workspace\project\drm-service
D:\workspace\project\workflow-service
D:\workspace\project\os-service
D:\workspace\project\entitlement-service
D:\workspace\project\api-integration-service
D:\workspace\project\graph-service
```

工具可以支持命令行参数：

```bash
java -jar sql-postgres-validator.jar ^
  --source D:\workspace\project\drm-service ^
  --source D:\workspace\project\workflow-service ^
  --output D:\workspace\sql-validation-output
```

如果先做 MVP，也可以先在配置文件中写死路径：

```properties
scanner.sourceRoots=D:/workspace/project/drm-service,D:/workspace/project/workflow-service
scanner.outputDir=D:/workspace/sql-validation-output
```

---

### 3.2 输出

Phase 1 输出文件：

```text
sql-inventory.csv
```

可选输出：

```text
sql-inventory.md
scan-summary.txt
```

---

## 4. sql-inventory.csv 字段设计

建议字段如下：

```text
id
serviceName
moduleName
filePath
className
methodName
sourceType
lineNumber
sqlVariableName
sqlText
normalizedSqlText
parameterMode
parameterNames
parameterCount
isDynamicSql
requiresManualReview
manualReviewReason
confidence
notes
```

---

## 5. 字段说明

### 5.1 id

唯一 ID。

建议格式：

```text
{serviceName}-{sequence}
```

示例：

```text
DRM-0001
DRM-0002
WORKFLOW-0001
APIINTEGRATION-0001
```

---

### 5.2 serviceName

从源码根目录或 Maven module 名称推断。

示例：

```text
DRM
Workflow
OS
Entitlement
API Integration
Graph
```

---

### 5.3 moduleName

如果一个微服务下有多个 module，则记录 module 名称。

如果没有多 module，则可以等于 serviceName。

---

### 5.4 filePath

Java 文件相对路径。

示例：

```text
src/main/java/com/company/drm/repository/FieldNameRepository.java
```

---

### 5.5 className

SQL 所在类名。

示例：

```text
FieldNameRepository
InterfaceQueryRepository
SearchDataDaoImpl
```

---

### 5.6 methodName

SQL 所在方法名。

对于 Repository 接口方法：

```text
findByModelId
getNextSequenceId
```

对于类字段常量中定义的 SQL，如果无法归属到具体方法，可以写：

```text
<CLASS_FIELD>
```

---

### 5.7 sourceType

枚举：

```text
SPRING_DATA_QUERY
ENTITY_MANAGER
JDBC_TEMPLATE
NAMED_PARAMETER_JDBC_TEMPLATE
SQL_CONSTANT
UNKNOWN
```

---

### 5.8 lineNumber

SQL 所在起始行号。

如果 JavaParser 无法获取准确行号，可以用文本扫描 fallback 估算。

---

### 5.9 sqlVariableName

如果 SQL 来自变量，记录变量名。

示例：

```text
interfaceQuery
sql
querySql
FIND_USER_SQL
```

如果 SQL 直接写在 `@Query` 中，可以为空。

---

### 5.10 sqlText

原始 SQL。

需要尽量保留原始格式，但去掉 Java 字符串拼接符号。

示例：

```sql
SELECT FIELD_NAME_SEQ.NEXTVAL FROM DUAL
```

---

### 5.11 normalizedSqlText

标准化后的 SQL，用于后续处理。

建议处理：

```text
1. 去掉多余空格
2. 多行合并
3. 保留参数占位符
4. 不改变 SQL 语义
```

示例：

```sql
SELECT FIELD_NAME_SEQ.NEXTVAL FROM DUAL
```

---

### 5.12 parameterMode

枚举：

```text
NONE
NAMED
POSITIONAL
MIXED
UNKNOWN
```

---

### 5.13 parameterNames

命名参数列表。

示例：

```text
modelId,interfaceName,status
```

从 SQL 中提取：

```sql
WHERE MODEL_ID = :modelId AND STATUS = :status
```

得到：

```text
modelId,status
```

---

### 5.14 parameterCount

位置参数数量。

示例：

```sql
WHERE USER_ID = ? AND STATUS = ?
```

得到：

```text
2
```

---

### 5.15 isDynamicSql

是否是动态 SQL。

以下情况标记为 true：

```text
1. SQL 使用 StringBuilder 拼接
2. SQL 使用 if/else 条件拼接
3. SQL 使用 += 拼接
4. SQL 使用 String.format
5. SQL 由方法返回
6. SQL 从外部变量传入且无法还原
```

---

### 5.16 requiresManualReview

是否需要人工复核。

以下情况标记为 true：

```text
1. 动态 SQL 无法完整还原
2. SQL 参数无法明确识别
3. SQL 来源无法明确判断
4. SQL 文本为空或不完整
5. SQL 拼接中包含复杂 Java 表达式
```

---

### 5.17 manualReviewReason

人工复核原因。

建议枚举：

```text
DYNAMIC_SQL
UNRESOLVED_SQL_VARIABLE
COMPLEX_STRING_CONCAT
METHOD_RETURN_SQL
UNSUPPORTED_SOURCE
PARAMETER_PARSE_FAILED
LOW_CONFIDENCE_EXTRACTION
```

---

### 5.18 confidence

抽取置信度：

```text
HIGH
MEDIUM
LOW
```

建议规则：

```text
HIGH:
@Query(nativeQuery=true) 且 SQL 为直接字符串或 text block。

MEDIUM:
SQL 来自同一方法中的 String 变量，能够简单还原。

LOW:
SQL 为动态拼接、跨方法传递、复杂 StringBuilder、外部来源。
```

---

## 6. Native SQL 来源识别策略

Phase 1 至少覆盖以下 4 类。

---

## 7. SPRING_DATA_QUERY 识别

### 7.1 目标模式

识别：

```java
@Query(
    value = "SELECT * FROM USER_TABLE WHERE USER_ID = :userId",
    nativeQuery = true
)
List<User> findUser(@Param("userId") Long userId);
```

也要识别：

```java
@Query(value = "SELECT * FROM USER_TABLE", nativeQuery = true)
```

也要识别 Java text block：

```java
@Query(
    value = """
        SELECT *
        FROM USER_TABLE
        WHERE USER_ID = :userId
    """,
    nativeQuery = true
)
```

也要识别字符串拼接：

```java
@Query(
    value = "SELECT * " +
            "FROM USER_TABLE " +
            "WHERE USER_ID = :userId",
    nativeQuery = true
)
```

---

### 7.2 提取内容

需要提取：

```text
className
methodName
lineNumber
sqlText
parameterNames
sourceType = SPRING_DATA_QUERY
```

---

### 7.3 nativeQuery 判断

只提取：

```java
nativeQuery = true
```

不提取普通 JPQL：

```java
@Query("select u from User u")
```

如果没有 `nativeQuery = true`，默认跳过。

---

### 7.4 @Query value 提取策略

优先使用 JavaParser AST。

处理顺序：

```text
1. 找 MethodDeclaration
2. 找 @Query annotation
3. 判断 nativeQuery = true
4. 读取 value 属性
5. 如果 value 是 StringLiteralExpr，直接取值
6. 如果 value 是 TextBlockLiteralExpr，直接取值
7. 如果 value 是 BinaryExpr 且 operator 为 PLUS，递归合并字符串
8. 如果 value 是 NameExpr，尝试解析类中常量
9. 解析失败则标记 LOW confidence
```

---

### 7.5 Repository 方法参数

Phase 1 只做参数名初步识别。

从 SQL 中识别：

```text
:parameterName
```

从方法参数中识别：

```java
@Param("parameterName")
```

但参数类型详细匹配留到 Phase 2。

---

## 8. ENTITY_MANAGER 识别

### 8.1 目标模式

识别：

```java
Query query = entityManager.createNativeQuery(sql);
```

```java
Query query = this.entityManager.createNativeQuery(interfaceQuery);
```

```java
entityManager.createNativeQuery("SELECT * FROM USER_TABLE")
```

---

### 8.2 提取内容

需要提取：

```text
className
methodName
lineNumber
sqlVariableName
sqlText
parameterNames
sourceType = ENTITY_MANAGER
```

---

### 8.3 SQL 解析策略

如果是直接字符串：

```java
entityManager.createNativeQuery("SELECT * FROM USER_TABLE")
```

直接提取。

如果是变量：

```java
String sql = "SELECT * FROM USER_TABLE";
Query query = entityManager.createNativeQuery(sql);
```

在同一方法内查找变量定义。

如果是类常量：

```java
private static final String SQL = "SELECT * FROM USER_TABLE";
entityManager.createNativeQuery(SQL);
```

在当前类字段中查找常量。

如果是方法返回：

```java
entityManager.createNativeQuery(buildSql());
```

Phase 1 暂不展开方法调用，记录：

```text
requiresManualReview = true
manualReviewReason = METHOD_RETURN_SQL
```

---

### 8.4 参数识别

识别同一方法内：

```java
query.setParameter("interfaceName", interfaceName);
query.setParameter("modelId", modelId);
```

Phase 1 记录参数名：

```text
interfaceName,modelId
```

参数类型留到 Phase 2。

---

## 9. JDBC_TEMPLATE 识别

### 9.1 目标模式

识别：

```java
jdbcTemplate.query(sql, rowMapper);
jdbcTemplate.query(sql, args, rowMapper);
jdbcTemplate.queryForObject(sql, Long.class);
jdbcTemplate.update(sql, param1, param2);
jdbcTemplate.execute(sql);
```

---

### 9.2 方法名称范围

MVP 支持：

```text
query
queryForObject
queryForList
update
execute
batchUpdate
```

---

### 9.3 提取策略

第一个参数通常为 SQL。

示例：

```java
jdbcTemplate.query(sql, ...)
```

处理：

```text
1. 如果第一个参数是字符串，直接提取
2. 如果第一个参数是变量，在同一方法内解析变量
3. 如果来自类常量，解析类常量
4. 如果来自方法调用，标记 MANUAL_REVIEW
```

---

### 9.4 参数模式

JdbcTemplate 多数为位置参数：

```text
parameterMode = POSITIONAL
```

如果 SQL 内包含 `?`，统计数量：

```text
parameterCount = count("?")
```

注意不要统计字符串字面量中的问号，MVP 可先简单统计，后续优化。

---

## 10. NAMED_PARAMETER_JDBC_TEMPLATE 识别

### 10.1 目标模式

识别：

```java
namedParameterJdbcTemplate.query(sql, params, rowMapper);
namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
namedParameterJdbcTemplate.update(sql, params);
```

---

### 10.2 方法名称范围

MVP 支持：

```text
query
queryForObject
queryForList
update
batchUpdate
```

---

### 10.3 参数模式

```text
parameterMode = NAMED
```

从 SQL 中提取 `:paramName`。

---

### 10.4 参数来源

Phase 1 只记录参数名。

Phase 2 再处理：

```java
MapSqlParameterSource params = new MapSqlParameterSource();
params.addValue("modelId", modelId);
params.addValue("status", status);
```

---

## 11. SQL 常量识别

### 11.1 目标模式

识别：

```java
private static final String FIND_USER_SQL =
    "SELECT * FROM USER_TABLE WHERE USER_ID = :userId";
```

如果这个常量被 Native SQL 来源引用，则记录到对应 SQL。

如果常量未被使用，Phase 1 可以暂不输出，避免误报。

---

### 11.2 常量解析范围

MVP 先支持：

```text
1. 同一个类中的 static final String
2. 同一个方法中的 String 变量
3. 简单字符串拼接
```

暂不支持：

```text
1. 跨类常量
2. 配置文件中的 SQL
3. SQL Builder 框架
4. 复杂 if/else 拼接
```

---

## 12. 字符串还原规则

需要支持以下 Java 写法。

### 12.1 普通字符串

```java
"SELECT * FROM USER_TABLE"
```

输出：

```sql
SELECT * FROM USER_TABLE
```

---

### 12.2 字符串拼接

```java
"SELECT * " +
"FROM USER_TABLE " +
"WHERE ID = :id"
```

输出：

```sql
SELECT * FROM USER_TABLE WHERE ID = :id
```

---

### 12.3 Text Block

```java
"""
SELECT *
FROM USER_TABLE
WHERE ID = :id
"""
```

输出：

```sql
SELECT *
FROM USER_TABLE
WHERE ID = :id
```

---

### 12.4 常量引用

```java
private static final String SQL = "SELECT * FROM USER_TABLE";

entityManager.createNativeQuery(SQL);
```

输出：

```sql
SELECT * FROM USER_TABLE
```

---

### 12.5 StringBuilder

```java
StringBuilder sql = new StringBuilder();
sql.append("SELECT * FROM USER_TABLE");
sql.append(" WHERE ID = :id");
```

MVP 可以先标记为：

```text
isDynamicSql = true
requiresManualReview = true
manualReviewReason = DYNAMIC_SQL
```

如果后续时间允许，再支持简单 StringBuilder append 还原。

---

### 12.6 if/else 动态拼接

```java
String sql = "SELECT * FROM USER_TABLE WHERE 1=1";
if (status != null) {
    sql += " AND STATUS = :status";
}
```

MVP 标记：

```text
isDynamicSql = true
requiresManualReview = true
manualReviewReason = DYNAMIC_SQL
```

---

## 13. SQL 参数提取规则

### 13.1 命名参数

从 SQL 中提取：

```sql
:modelId
:interfaceName
:status
```

正则建议：

```text
(?<!:):([A-Za-z][A-Za-z0-9_]*)
```

避免把 PostgreSQL 类型转换 `::` 误识别成参数。

例如：

```sql
created_at::date
```

不能识别成参数 `date`。

---

### 13.2 位置参数

从 SQL 中提取：

```sql
?
?1
?2
```

MVP 可统计 `?` 数量。

注意排除 SQL 字符串中的问号是后续优化项。

---

### 13.3 参数模式判断

```text
无 :param 且无 ?       -> NONE
有 :param             -> NAMED
有 ? 或 ?1            -> POSITIONAL
同时有 :param 和 ?    -> MIXED
无法判断             -> UNKNOWN
```

---

## 14. 推荐技术选型

### 14.1 首选

```text
Java 17 或 Java 11
Maven
JavaParser
OpenCSV 或 Apache Commons CSV
JUnit 5
```

---

### 14.2 JavaParser 作用

JavaParser 用于：

```text
1. 遍历 Java AST
2. 获取类名
3. 获取方法名
4. 读取注解
5. 解析方法调用
6. 获取字符串字面量
7. 获取行号
```

---

### 14.3 Regex Fallback

如果公司环境无法引入 JavaParser，或者依赖下载受限，可以使用 Regex Fallback。

但推荐优先使用 JavaParser，因为：

```text
1. @Query 多行注解用正则容易漏
2. 字符串拼接用正则容易错
3. 方法和类归属用正则难维护
4. 行号和变量解析用 AST 更可靠
```

---

## 15. 推荐 Maven 依赖

如果公司允许下载 Maven 依赖，建议：

```xml
<dependencies>
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>3.26.2</version>
    </dependency>

    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-csv</artifactId>
        <version>1.11.0</version>
    </dependency>

    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

如果企业内网 Maven 仓库版本不同，以公司仓库可用版本为准。

---

## 16. 推荐类设计

Phase 1 建议类结构：

```text
sql-postgres-validator/
    src/main/java/com/company/sqlvalidator/
        SqlInventoryScannerApplication.java

        model/
            NativeSqlRecord.java
            SqlSourceType.java
            ParameterMode.java
            ExtractionConfidence.java

        scanner/
            NativeSqlScanner.java
            JavaSourceFileScanner.java
            SpringDataQueryScanner.java
            EntityManagerNativeQueryScanner.java
            JdbcTemplateScanner.java
            NamedParameterJdbcTemplateScanner.java

        parser/
            JavaStringExpressionResolver.java
            SqlParameterNameExtractor.java
            ServiceNameResolver.java

        report/
            SqlInventoryCsvWriter.java
            ScanSummaryWriter.java
```

---

## 17. 核心 Model 设计

### 17.1 NativeSqlRecord

建议字段：

```java
public class NativeSqlRecord {
    private String id;
    private String serviceName;
    private String moduleName;
    private String filePath;
    private String className;
    private String methodName;
    private SqlSourceType sourceType;
    private int lineNumber;
    private String sqlVariableName;
    private String sqlText;
    private String normalizedSqlText;
    private ParameterMode parameterMode;
    private List<String> parameterNames;
    private int parameterCount;
    private boolean dynamicSql;
    private boolean requiresManualReview;
    private String manualReviewReason;
    private ExtractionConfidence confidence;
    private String notes;
}
```

---

### 17.2 SqlSourceType

```java
public enum SqlSourceType {
    SPRING_DATA_QUERY,
    ENTITY_MANAGER,
    JDBC_TEMPLATE,
    NAMED_PARAMETER_JDBC_TEMPLATE,
    SQL_CONSTANT,
    UNKNOWN
}
```

---

### 17.3 ParameterMode

```java
public enum ParameterMode {
    NONE,
    NAMED,
    POSITIONAL,
    MIXED,
    UNKNOWN
}
```

---

### 17.4 ExtractionConfidence

```java
public enum ExtractionConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

---

## 18. 扫描流程设计

### 18.1 总流程

```text
SqlInventoryScannerApplication
    ↓
读取 sourceRoots
    ↓
递归查找 .java 文件
    ↓
对每个 Java 文件执行 JavaSourceFileScanner
    ↓
JavaParser 解析 CompilationUnit
    ↓
分别调用：
    - SpringDataQueryScanner
    - EntityManagerNativeQueryScanner
    - JdbcTemplateScanner
    - NamedParameterJdbcTemplateScanner
    ↓
合并 NativeSqlRecord
    ↓
生成唯一 ID
    ↓
写出 sql-inventory.csv
    ↓
写出 scan-summary.txt
```

---

### 18.2 伪代码

```java
public class NativeSqlScanner {

    public List<NativeSqlRecord> scan(List<Path> sourceRoots) {
        List<Path> javaFiles = findJavaFiles(sourceRoots);
        List<NativeSqlRecord> records = new ArrayList<>();

        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parse(javaFile);

            records.addAll(springDataQueryScanner.scan(cu, javaFile));
            records.addAll(entityManagerScanner.scan(cu, javaFile));
            records.addAll(jdbcTemplateScanner.scan(cu, javaFile));
            records.addAll(namedParameterScanner.scan(cu, javaFile));
        }

        assignIds(records);
        return records;
    }
}
```

---

## 19. SpringDataQueryScanner 设计

### 19.1 扫描对象

扫描所有 MethodDeclaration。

### 19.2 判断逻辑

```text
1. 方法上是否有 @Query
2. @Query 是否包含 nativeQuery = true
3. value 是否存在
4. value 是否能还原为 SQL 字符串
5. 提取参数
6. 创建 NativeSqlRecord
```

---

### 19.3 伪代码

```java
for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
    Optional<AnnotationExpr> queryAnnotation = findQueryAnnotation(method);

    if (queryAnnotation.isEmpty()) {
        continue;
    }

    if (!isNativeQuery(queryAnnotation.get())) {
        continue;
    }

    String sql = extractQueryValue(queryAnnotation.get());

    NativeSqlRecord record = new NativeSqlRecord();
    record.setSourceType(SqlSourceType.SPRING_DATA_QUERY);
    record.setClassName(resolveClassName(method));
    record.setMethodName(method.getNameAsString());
    record.setSqlText(sql);
    record.setParameterNames(sqlParameterNameExtractor.extract(sql));
}
```

---

## 20. EntityManagerNativeQueryScanner 设计

### 20.1 扫描对象

扫描所有 MethodCallExpr。

匹配方法名：

```text
createNativeQuery
```

### 20.2 判断调用者

调用者可能是：

```java
entityManager.createNativeQuery(sql)
this.entityManager.createNativeQuery(sql)
em.createNativeQuery(sql)
```

MVP 可以先只通过方法名匹配 `createNativeQuery`。

后续再根据变量类型确认是否为 EntityManager。

---

### 20.3 第一个参数为 SQL

```java
createNativeQuery(sql)
createNativeQuery("SELECT ...")
```

取第一个参数。

---

### 20.4 SQL 变量解析

如果第一个参数是变量：

```java
createNativeQuery(interfaceQuery)
```

在当前方法和当前类中查找：

```text
String interfaceQuery = ...
private static final String interfaceQuery = ...
```

找不到则标记：

```text
requiresManualReview = true
manualReviewReason = UNRESOLVED_SQL_VARIABLE
```

---

## 21. JdbcTemplateScanner 设计

### 21.1 扫描对象

扫描所有 MethodCallExpr。

匹配方法名：

```text
query
queryForObject
queryForList
update
execute
batchUpdate
```

### 21.2 判断调用者

调用者名称包含：

```text
jdbcTemplate
```

但排除：

```text
namedParameterJdbcTemplate
```

MVP 简化判断：

```text
scope name equals jdbcTemplate
or scope name endsWith JdbcTemplate
```

---

### 21.3 第一个参数为 SQL

JdbcTemplate 通常第一个参数为 SQL。

提取方式同 EntityManager。

---

## 22. NamedParameterJdbcTemplateScanner 设计

### 22.1 扫描对象

扫描所有 MethodCallExpr。

匹配方法名：

```text
query
queryForObject
queryForList
update
batchUpdate
```

### 22.2 判断调用者

调用者名称包含：

```text
namedParameterJdbcTemplate
```

或变量名包含：

```text
namedJdbcTemplate
```

---

### 22.3 参数模式

默认：

```text
parameterMode = NAMED
```

从 SQL 中提取 `:paramName`。

---

## 23. JavaStringExpressionResolver 设计

### 23.1 作用

把 Java 表达式尽量还原成 SQL 字符串。

输入：

```java
Expression expr
```

输出：

```java
ResolvedString {
    String value;
    boolean resolved;
    boolean dynamic;
    String reason;
}
```

---

### 23.2 支持范围

MVP 支持：

```text
StringLiteralExpr
TextBlockLiteralExpr
BinaryExpr with PLUS
NameExpr referencing local String variable
NameExpr referencing static final String field in same class
```

---

### 23.3 不支持范围

MVP 暂不支持：

```text
StringBuilder
String.format
复杂 if/else 拼接
跨类常量
方法返回 SQL
读取外部 SQL 文件
```

这些统一标记：

```text
requiresManualReview = true
```

---

## 24. SqlParameterNameExtractor 设计

### 24.1 命名参数正则

建议：

```java
Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)")
```

示例：

```sql
WHERE MODEL_ID = :modelId
```

提取：

```text
modelId
```

---

### 24.2 避免误判 PostgreSQL 类型转换

PostgreSQL 类型转换：

```sql
created_at::date
```

不能提取出 `date`。

所以使用：

```text
(?<!:):
```

避免匹配双冒号后面的内容。

---

### 24.3 位置参数统计

MVP 简化：

```java
int count = countQuestionMarks(sql);
```

后续可以优化，排除字符串中的问号。

---

## 25. 去重策略

同一条 SQL 可能被多个扫描器识别。

建议去重 key：

```text
filePath + lineNumber + normalizedSqlText
```

如果 lineNumber 不可靠，则使用：

```text
filePath + className + methodName + normalizedSqlText
```

---

## 26. 扫描摘要

输出 `scan-summary.txt`：

```text
Total Java files scanned: 520
Total Native SQL records: 137

By source type:
SPRING_DATA_QUERY: 91
ENTITY_MANAGER: 22
JDBC_TEMPLATE: 16
NAMED_PARAMETER_JDBC_TEMPLATE: 8

By confidence:
HIGH: 98
MEDIUM: 26
LOW: 13

Manual review required: 17
Dynamic SQL: 9
Unresolved SQL variable: 5
Unsupported source: 3
```

这个摘要可以直接发给负责人汇报进展。

---

## 27. 验收标准

Phase 1 完成后，应满足：

```text
1. 可以扫描多个微服务源码路径
2. 可以输出 sql-inventory.csv
3. 可以识别 @Query(nativeQuery=true)
4. 可以识别 Java text block SQL
5. 可以识别字符串拼接 SQL
6. 可以识别 EntityManager.createNativeQuery
7. 可以识别 JdbcTemplate
8. 可以识别 NamedParameterJdbcTemplate
9. 可以提取 SQL 参数名
10. 可以标记动态 SQL 和人工 Review 项
11. 不修改任何业务源码
12. 不连接数据库
```

---

## 28. 测试样例

需要准备测试文件覆盖以下场景：

```text
1. @Query 普通字符串
2. @Query 多行拼接字符串
3. @Query text block
4. @Query 使用常量
5. EntityManager 直接 SQL
6. EntityManager 变量 SQL
7. EntityManager 动态 SQL
8. JdbcTemplate query
9. JdbcTemplate update
10. NamedParameterJdbcTemplate query
11. PostgreSQL :: 类型转换避免误识别
12. 无 nativeQuery 的 JPQL 跳过
```

---

## 29. 推荐命令行输出

示例：

```text
Starting Native SQL Inventory Scanner...

Source roots:
[1] D:\workspace\drm-service
[2] D:\workspace\workflow-service

Scanning Java files...
Scanned 520 Java files.

Native SQL found:
SPRING_DATA_QUERY: 91
ENTITY_MANAGER: 22
JDBC_TEMPLATE: 16
NAMED_PARAMETER_JDBC_TEMPLATE: 8

Total: 137

Manual review required: 17

Output:
D:\workspace\sql-validation-output\sql-inventory.csv
D:\workspace\sql-validation-output\scan-summary.txt

Done.
```

---

## 30. 给 Copilot 的执行提示

把下面这段复制给 IntelliJ Copilot：

```text
Please implement Phase 1 only: SQL Inventory Scanner.

Use Java. Prefer JavaParser AST if dependency is available.

Do not connect to PostgreSQL.
Do not generate JUnit yet.
Do not modify business code.
Do not implement SQL rewrite rules yet.
Do not implement database migration, table creation, index migration, or performance tuning.

Implement a Java scanner that recursively scans multiple Spring Boot service source roots and extracts Native SQL from:
1. @Query(nativeQuery = true)
2. EntityManager.createNativeQuery(...)
3. JdbcTemplate methods: query, queryForObject, queryForList, update, execute, batchUpdate
4. NamedParameterJdbcTemplate methods: query, queryForObject, queryForList, update, batchUpdate

Output sql-inventory.csv with these columns:
id, serviceName, moduleName, filePath, className, methodName, sourceType, lineNumber, sqlVariableName, sqlText, normalizedSqlText, parameterMode, parameterNames, parameterCount, isDynamicSql, requiresManualReview, manualReviewReason, confidence, notes.

Support:
- Java String literal
- Java text block
- Simple string concatenation with +
- Local String variable in same method
- static final String constant in same class

If SQL is built by StringBuilder, if/else, String.format, method return value, or unresolved variable, mark requiresManualReview=true and set a clear manualReviewReason.

Keep code simple, readable, and MVP-oriented.
```

---

## 31. 重要结论

Phase 1 的关键不是“执行 SQL”，而是：

```text
把 SQL 找全。
```

如果 SQL Inventory 不完整，后续 PostgreSQL 验证、JUnit 生成、自动修复都会漏问题。

因此第一阶段优先级：

```text
完整性 > 智能修复
清晰报告 > 复杂自动化
可人工复核 > 假装 100% 自动
```

完成 Phase 1 后，再进入 Phase 2：

```text
Parameter Parser and Mock Value Generator
```
