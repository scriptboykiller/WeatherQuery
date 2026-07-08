# 03_SQL_Inventory_Scanner_Code.md

## 1. 这份文档怎么用

这份文档是 **Phase 1：SQL Inventory Scanner** 的代码搬运手册。

你需要在 IntelliJ IDEA 里新建一个独立 Maven module：

```text
sql-postgres-validator
```

然后按照本文档中的文件路径，把每个代码块复制到对应文件中。

复制完成后，再让 GitHub Copilot 做少量修复和适配。

---

## 2. 非常重要：复制代码后怎么跟 Copilot 说

代码复制完成后，不要再对 Copilot 说：

```text
Implement Phase 1 only.
```

因为这会让 Copilot 误以为要重新生成一套实现。

复制完成后，应该这样说：

```text
I have already copied the Phase 1 SQL Inventory Scanner code into this module.

Please do not rewrite the whole implementation.

Use docs/01_Dexter_SQL_Validation_Scope.md and docs/02_SQL_Inventory_Scanner_Design.md only as reference.

Your task is only to:
1. Fix compilation errors.
2. Adjust package names if needed.
3. Fix Maven dependency issues.
4. Make the scanner runnable from main().
5. Do not add PostgreSQL execution.
6. Do not generate JUnit.
7. Do not implement SQL rewrite rules.
8. Do not modify business service modules.
```

中文理解：

```text
代码已经放进来了。
Copilot 只负责修编译、调包名、调路径、让 main 能跑。
不要让它重写整个工具。
不要让它扩展到数据库执行、JUnit、SQL 自动修复、性能优化。
```

---

## 3. 推荐目录结构

最终目录：

```text
sql-postgres-validator
├── docs
│   ├── 01_Dexter_SQL_Validation_Scope.md
│   ├── 02_SQL_Inventory_Scanner_Design.md
│   └── 03_SQL_Inventory_Scanner_Code.md
│
├── pom.xml
│
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── company
│       │           └── sqlvalidator
│       │               ├── SqlInventoryScannerApplication.java
│       │               ├── model
│       │               │   ├── ExtractionConfidence.java
│       │               │   ├── NativeSqlRecord.java
│       │               │   ├── ParameterMode.java
│       │               │   └── SqlSourceType.java
│       │               ├── parser
│       │               │   ├── JavaStringExpressionResolver.java
│       │               │   ├── ResolvedString.java
│       │               │   ├── ServiceNameResolver.java
│       │               │   └── SqlParameterNameExtractor.java
│       │               ├── report
│       │               │   ├── ScanSummaryWriter.java
│       │               │   └── SqlInventoryCsvWriter.java
│       │               └── scanner
│       │                   ├── EntityManagerNativeQueryScanner.java
│       │                   ├── JdbcTemplateScanner.java
│       │                   ├── NativeSqlScanner.java
│       │                   ├── NamedParameterJdbcTemplateScanner.java
│       │                   ├── ScannerContext.java
│       │                   └── SpringDataQueryScanner.java
│       │
│       └── resources
│           └── application.properties
│
└── output
```

---

## 4. pom.xml

路径：

```text
sql-postgres-validator/pom.xml
```

代码：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.company</groupId>
    <artifactId>sql-postgres-validator</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <javaparser.version>3.26.2</javaparser.version>
        <commons.csv.version>1.11.0</commons.csv.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>${commons.csv.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>${maven.compiler.source}</source>
                    <target>${maven.compiler.target}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

说明：

```text
如果公司 Maven 仓库没有 3.26.2 或 1.11.0，改成公司仓库里已有版本即可。
如果项目使用 Java 17，可以把 source/target 改成 17。
```

---

## 5. application.properties

路径：

```text
src/main/resources/application.properties
```

代码：

```properties
# Multiple source roots separated by comma.
# If sql-postgres-validator is placed beside business modules, use ../module-name.
scanner.sourceRoots=../drm-service,../workflow-service,../os-service,../entitlement-service,../api-integration-service,../graph-service

# Output directory.
scanner.outputDir=./output
```

---

# 6. Model Classes

## 6.1 ExtractionConfidence.java

路径：

```text
src/main/java/com/company/sqlvalidator/model/ExtractionConfidence.java
```

代码：

```java
package com.company.sqlvalidator.model;

public enum ExtractionConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

---

## 6.2 ParameterMode.java

路径：

```text
src/main/java/com/company/sqlvalidator/model/ParameterMode.java
```

代码：

```java
package com.company.sqlvalidator.model;

public enum ParameterMode {
    NONE,
    NAMED,
    POSITIONAL,
    MIXED,
    UNKNOWN
}
```

---

## 6.3 SqlSourceType.java

路径：

```text
src/main/java/com/company/sqlvalidator/model/SqlSourceType.java
```

代码：

```java
package com.company.sqlvalidator.model;

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

## 6.4 NativeSqlRecord.java

路径：

```text
src/main/java/com/company/sqlvalidator/model/NativeSqlRecord.java
```

代码：

```java
package com.company.sqlvalidator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

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
    private List<String> parameterNames = new ArrayList<>();
    private int parameterCount;
    private boolean dynamicSql;
    private boolean requiresManualReview;
    private String manualReviewReason;
    private ExtractionConfidence confidence;
    private String notes;

    public String getId() {
        return id;
    }

    public NativeSqlRecord setId(String id) {
        this.id = id;
        return this;
    }

    public String getServiceName() {
        return serviceName;
    }

    public NativeSqlRecord setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getModuleName() {
        return moduleName;
    }

    public NativeSqlRecord setModuleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    public String getFilePath() {
        return filePath;
    }

    public NativeSqlRecord setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public String getClassName() {
        return className;
    }

    public NativeSqlRecord setClassName(String className) {
        this.className = className;
        return this;
    }

    public String getMethodName() {
        return methodName;
    }

    public NativeSqlRecord setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    public SqlSourceType getSourceType() {
        return sourceType;
    }

    public NativeSqlRecord setSourceType(SqlSourceType sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public NativeSqlRecord setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
        return this;
    }

    public String getSqlVariableName() {
        return sqlVariableName;
    }

    public NativeSqlRecord setSqlVariableName(String sqlVariableName) {
        this.sqlVariableName = sqlVariableName;
        return this;
    }

    public String getSqlText() {
        return sqlText;
    }

    public NativeSqlRecord setSqlText(String sqlText) {
        this.sqlText = sqlText;
        return this;
    }

    public String getNormalizedSqlText() {
        return normalizedSqlText;
    }

    public NativeSqlRecord setNormalizedSqlText(String normalizedSqlText) {
        this.normalizedSqlText = normalizedSqlText;
        return this;
    }

    public ParameterMode getParameterMode() {
        return parameterMode;
    }

    public NativeSqlRecord setParameterMode(ParameterMode parameterMode) {
        this.parameterMode = parameterMode;
        return this;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public NativeSqlRecord setParameterNames(List<String> parameterNames) {
        this.parameterNames = parameterNames == null ? new ArrayList<>() : new ArrayList<>(parameterNames);
        return this;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public NativeSqlRecord setParameterCount(int parameterCount) {
        this.parameterCount = parameterCount;
        return this;
    }

    public boolean isDynamicSql() {
        return dynamicSql;
    }

    public NativeSqlRecord setDynamicSql(boolean dynamicSql) {
        this.dynamicSql = dynamicSql;
        return this;
    }

    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }

    public NativeSqlRecord setRequiresManualReview(boolean requiresManualReview) {
        this.requiresManualReview = requiresManualReview;
        return this;
    }

    public String getManualReviewReason() {
        return manualReviewReason;
    }

    public NativeSqlRecord setManualReviewReason(String manualReviewReason) {
        this.manualReviewReason = manualReviewReason;
        return this;
    }

    public ExtractionConfidence getConfidence() {
        return confidence;
    }

    public NativeSqlRecord setConfidence(ExtractionConfidence confidence) {
        this.confidence = confidence;
        return this;
    }

    public String getNotes() {
        return notes;
    }

    public NativeSqlRecord setNotes(String notes) {
        this.notes = notes;
        return this;
    }

    public String getParameterNamesAsString() {
        if (parameterNames == null || parameterNames.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String name : parameterNames) {
            joiner.add(name);
        }
        return joiner.toString();
    }
}
```

---

# 7. Parser Classes

## 7.1 ResolvedString.java

路径：

```text
src/main/java/com/company/sqlvalidator/parser/ResolvedString.java
```

代码：

```java
package com.company.sqlvalidator.parser;

import com.company.sqlvalidator.model.ExtractionConfidence;

public class ResolvedString {

    private final String value;
    private final boolean resolved;
    private final boolean dynamic;
    private final String variableName;
    private final String reason;
    private final ExtractionConfidence confidence;

    private ResolvedString(
            String value,
            boolean resolved,
            boolean dynamic,
            String variableName,
            String reason,
            ExtractionConfidence confidence
    ) {
        this.value = value;
        this.resolved = resolved;
        this.dynamic = dynamic;
        this.variableName = variableName;
        this.reason = reason;
        this.confidence = confidence;
    }

    public static ResolvedString resolved(String value, ExtractionConfidence confidence) {
        return new ResolvedString(value, true, false, null, null, confidence);
    }

    public static ResolvedString unresolved(String variableName, String reason, boolean dynamic) {
        return new ResolvedString(null, false, dynamic, variableName, reason, ExtractionConfidence.LOW);
    }

    public String getValue() {
        return value;
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public String getVariableName() {
        return variableName;
    }

    public String getReason() {
        return reason;
    }

    public ExtractionConfidence getConfidence() {
        return confidence;
    }
}
```

---

## 7.2 SqlParameterNameExtractor.java

路径：

```text
src/main/java/com/company/sqlvalidator/parser/SqlParameterNameExtractor.java
```

代码：

```java
package com.company.sqlvalidator.parser;

import com.company.sqlvalidator.model.ParameterMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlParameterNameExtractor {

    private static final Pattern NAMED_PARAM_PATTERN =
            Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");

    public List<String> extractNamedParameters(String sql) {
        Set<String> names = new LinkedHashSet<>();
        if (sql == null || sql.isBlank()) {
            return new ArrayList<>();
        }

        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }

    public int countPositionalParameters(String sql) {
        if (sql == null || sql.isBlank()) {
            return 0;
        }

        int count = 0;
        boolean inSingleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (!inSingleQuote && c == '?') {
                count++;
            }
        }

        return count;
    }

    public ParameterMode detectParameterMode(String sql) {
        List<String> named = extractNamedParameters(sql);
        int positionalCount = countPositionalParameters(sql);

        boolean hasNamed = !named.isEmpty();
        boolean hasPositional = positionalCount > 0;

        if (hasNamed && hasPositional) {
            return ParameterMode.MIXED;
        }
        if (hasNamed) {
            return ParameterMode.NAMED;
        }
        if (hasPositional) {
            return ParameterMode.POSITIONAL;
        }
        return ParameterMode.NONE;
    }

    public String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
```

---

## 7.3 ServiceNameResolver.java

路径：

```text
src/main/java/com/company/sqlvalidator/parser/ServiceNameResolver.java
```

代码：

```java
package com.company.sqlvalidator.parser;

import java.nio.file.Path;

public class ServiceNameResolver {

    public String resolveServiceName(Path sourceRoot) {
        if (sourceRoot == null || sourceRoot.getFileName() == null) {
            return "UNKNOWN";
        }

        String name = sourceRoot.getFileName().toString();
        return toDisplayName(name);
    }

    public String resolveModuleName(Path sourceRoot) {
        if (sourceRoot == null || sourceRoot.getFileName() == null) {
            return "UNKNOWN";
        }
        return sourceRoot.getFileName().toString();
    }

    private String toDisplayName(String rawName) {
        String upper = rawName.toUpperCase();

        if (upper.contains("DRM")) {
            return "DRM";
        }
        if (upper.contains("WORKFLOW")) {
            return "WORKFLOW";
        }
        if (upper.equals("OS") || upper.contains("-OS") || upper.contains("_OS")) {
            return "OS";
        }
        if (upper.contains("ENTITLEMENT")) {
            return "ENTITLEMENT";
        }
        if (upper.contains("API") && upper.contains("INTEGRATION")) {
            return "API_INTEGRATION";
        }
        if (upper.contains("GRAPH")) {
            return "GRAPH";
        }

        return upper.replace("-", "_");
    }
}
```

---

## 7.4 JavaStringExpressionResolver.java

路径：

```text
src/main/java/com/company/sqlvalidator/parser/JavaStringExpressionResolver.java
```

代码：

```java
package com.company.sqlvalidator.parser;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.Optional;

public class JavaStringExpressionResolver {

    public ResolvedString resolve(Expression expression) {
        if (expression == null) {
            return ResolvedString.unresolved(null, "NULL_EXPRESSION", false);
        }

        if (expression.isStringLiteralExpr()) {
            StringLiteralExpr stringLiteralExpr = expression.asStringLiteralExpr();
            return ResolvedString.resolved(stringLiteralExpr.getValue(), ExtractionConfidence.HIGH);
        }

        if (expression.isTextBlockLiteralExpr()) {
            TextBlockLiteralExpr textBlockLiteralExpr = expression.asTextBlockLiteralExpr();
            return ResolvedString.resolved(textBlockLiteralExpr.getValue(), ExtractionConfidence.HIGH);
        }

        if (expression.isBinaryExpr()) {
            return resolveBinaryExpression(expression.asBinaryExpr());
        }

        if (expression.isNameExpr()) {
            return resolveNameExpression(expression.asNameExpr());
        }

        if (expression.isMethodCallExpr()) {
            return ResolvedString.unresolved(expression.toString(), "METHOD_RETURN_SQL", true);
        }

        if (expression.isObjectCreationExpr()) {
            return ResolvedString.unresolved(expression.toString(), "OBJECT_CREATION_SQL", true);
        }

        return ResolvedString.unresolved(expression.toString(), "UNSUPPORTED_EXPRESSION_" + expression.getClass().getSimpleName(), true);
    }

    private ResolvedString resolveBinaryExpression(BinaryExpr binaryExpr) {
        if (binaryExpr.getOperator() != BinaryExpr.Operator.PLUS) {
            return ResolvedString.unresolved(binaryExpr.toString(), "UNSUPPORTED_BINARY_OPERATOR", true);
        }

        ResolvedString left = resolve(binaryExpr.getLeft());
        ResolvedString right = resolve(binaryExpr.getRight());

        if (left.isResolved() && right.isResolved()) {
            return ResolvedString.resolved(left.getValue() + right.getValue(), ExtractionConfidence.HIGH);
        }

        return ResolvedString.unresolved(binaryExpr.toString(), "COMPLEX_STRING_CONCAT", true);
    }

    private ResolvedString resolveNameExpression(NameExpr nameExpr) {
        String variableName = nameExpr.getNameAsString();

        Optional<VariableDeclarator> localVariable = findLocalStringVariable(nameExpr, variableName);
        if (localVariable.isPresent()) {
            Optional<Expression> initializer = localVariable.get().getInitializer();
            if (initializer.isPresent()) {
                ResolvedString resolved = resolve(initializer.get());
                if (resolved.isResolved()) {
                    return ResolvedString.resolved(resolved.getValue(), ExtractionConfidence.MEDIUM);
                }
            }
        }

        Optional<VariableDeclarator> fieldConstant = findFieldStringVariable(nameExpr, variableName);
        if (fieldConstant.isPresent()) {
            Optional<Expression> initializer = fieldConstant.get().getInitializer();
            if (initializer.isPresent()) {
                ResolvedString resolved = resolve(initializer.get());
                if (resolved.isResolved()) {
                    return ResolvedString.resolved(resolved.getValue(), ExtractionConfidence.MEDIUM);
                }
            }
        }

        return ResolvedString.unresolved(variableName, "UNRESOLVED_SQL_VARIABLE", true);
    }

    private Optional<VariableDeclarator> findLocalStringVariable(Node node, String variableName) {
        Optional<BlockStmt> block = node.findAncestor(BlockStmt.class);
        if (block.isEmpty()) {
            return Optional.empty();
        }

        return block.get()
                .findAll(VariableDeclarator.class)
                .stream()
                .filter(v -> variableName.equals(v.getNameAsString()))
                .filter(v -> "String".equals(v.getTypeAsString()))
                .findFirst();
    }

    private Optional<VariableDeclarator> findFieldStringVariable(Node node, String variableName) {
        return node.findCompilationUnit()
                .stream()
                .flatMap(cu -> cu.findAll(FieldDeclaration.class).stream())
                .flatMap(field -> field.getVariables().stream())
                .filter(v -> variableName.equals(v.getNameAsString()))
                .filter(v -> "String".equals(v.getTypeAsString()))
                .findFirst();
    }
}
```

---

# 8. Scanner Context

## 8.1 ScannerContext.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/ScannerContext.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.parser.JavaStringExpressionResolver;
import com.company.sqlvalidator.parser.ServiceNameResolver;
import com.company.sqlvalidator.parser.SqlParameterNameExtractor;

import java.nio.file.Path;

public class ScannerContext {

    private final Path sourceRoot;
    private final Path javaFile;
    private final String serviceName;
    private final String moduleName;
    private final Path relativePath;

    private final JavaStringExpressionResolver stringResolver;
    private final SqlParameterNameExtractor parameterExtractor;

    public ScannerContext(
            Path sourceRoot,
            Path javaFile,
            ServiceNameResolver serviceNameResolver,
            JavaStringExpressionResolver stringResolver,
            SqlParameterNameExtractor parameterExtractor
    ) {
        this.sourceRoot = sourceRoot;
        this.javaFile = javaFile;
        this.serviceName = serviceNameResolver.resolveServiceName(sourceRoot);
        this.moduleName = serviceNameResolver.resolveModuleName(sourceRoot);
        this.relativePath = sourceRoot.relativize(javaFile);
        this.stringResolver = stringResolver;
        this.parameterExtractor = parameterExtractor;
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public Path getJavaFile() {
        return javaFile;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public JavaStringExpressionResolver getStringResolver() {
        return stringResolver;
    }

    public SqlParameterNameExtractor getParameterExtractor() {
        return parameterExtractor;
    }
}
```

---

# 9. Scanner Classes

## 9.1 SpringDataQueryScanner.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/SpringDataQueryScanner.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.SqlSourceType;
import com.company.sqlvalidator.parser.ResolvedString;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpringDataQueryScanner {

    public List<NativeSqlRecord> scan(Node root, ScannerContext context) {
        List<NativeSqlRecord> records = new ArrayList<>();

        for (MethodDeclaration method : root.findAll(MethodDeclaration.class)) {
            Optional<AnnotationExpr> queryAnnotation = findQueryAnnotation(method);
            if (queryAnnotation.isEmpty()) {
                continue;
            }

            if (!isNativeQuery(queryAnnotation.get())) {
                continue;
            }

            Optional<Expression> valueExpression = findQueryValueExpression(queryAnnotation.get());
            if (valueExpression.isEmpty()) {
                records.add(createUnresolvedRecord(method, context, "QUERY_VALUE_NOT_FOUND"));
                continue;
            }

            ResolvedString resolved = context.getStringResolver().resolve(valueExpression.get());

            NativeSqlRecord record = new NativeSqlRecord()
                    .setServiceName(context.getServiceName())
                    .setModuleName(context.getModuleName())
                    .setFilePath(context.getRelativePath().toString())
                    .setClassName(resolveClassName(method))
                    .setMethodName(method.getNameAsString())
                    .setSourceType(SqlSourceType.SPRING_DATA_QUERY)
                    .setLineNumber(resolveLineNumber(method))
                    .setSqlVariableName(resolved.getVariableName())
                    .setDynamicSql(resolved.isDynamic())
                    .setRequiresManualReview(!resolved.isResolved())
                    .setManualReviewReason(resolved.isResolved() ? "" : resolved.getReason())
                    .setConfidence(resolved.isResolved() ? resolved.getConfidence() : ExtractionConfidence.LOW)
                    .setNotes("");

            if (resolved.isResolved()) {
                fillSqlFields(record, resolved.getValue(), context);
            }

            records.add(record);
        }

        return records;
    }

    private Optional<AnnotationExpr> findQueryAnnotation(MethodDeclaration method) {
        return method.getAnnotations()
                .stream()
                .filter(a -> "Query".equals(a.getNameAsString()) || a.getNameAsString().endsWith(".Query"))
                .findFirst();
    }

    private boolean isNativeQuery(AnnotationExpr annotation) {
        if (!annotation.isNormalAnnotationExpr()) {
            return false;
        }

        NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
        return normal.getPairs()
                .stream()
                .anyMatch(pair ->
                        "nativeQuery".equals(pair.getNameAsString())
                                && "true".equalsIgnoreCase(pair.getValue().toString())
                );
    }

    private Optional<Expression> findQueryValueExpression(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
            return Optional.of(single.getMemberValue());
        }

        if (!annotation.isNormalAnnotationExpr()) {
            return Optional.empty();
        }

        NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();

        for (MemberValuePair pair : normal.getPairs()) {
            if ("value".equals(pair.getNameAsString())) {
                return Optional.of(pair.getValue());
            }
        }

        // Sometimes @Query("...") is not represented as value in NormalAnnotationExpr.
        return Optional.empty();
    }

    private NativeSqlRecord createUnresolvedRecord(MethodDeclaration method, ScannerContext context, String reason) {
        return new NativeSqlRecord()
                .setServiceName(context.getServiceName())
                .setModuleName(context.getModuleName())
                .setFilePath(context.getRelativePath().toString())
                .setClassName(resolveClassName(method))
                .setMethodName(method.getNameAsString())
                .setSourceType(SqlSourceType.SPRING_DATA_QUERY)
                .setLineNumber(resolveLineNumber(method))
                .setRequiresManualReview(true)
                .setManualReviewReason(reason)
                .setConfidence(ExtractionConfidence.LOW);
    }

    private void fillSqlFields(NativeSqlRecord record, String sql, ScannerContext context) {
        record.setSqlText(sql);
        record.setNormalizedSqlText(context.getParameterExtractor().normalizeSql(sql));
        record.setParameterNames(context.getParameterExtractor().extractNamedParameters(sql));
        record.setParameterCount(context.getParameterExtractor().countPositionalParameters(sql));
        record.setParameterMode(context.getParameterExtractor().detectParameterMode(sql));
    }

    private String resolveClassName(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("UNKNOWN_CLASS");
    }

    private int resolveLineNumber(Node node) {
        return node.getBegin().map(p -> p.line).orElse(-1);
    }
}
```

---

## 9.2 EntityManagerNativeQueryScanner.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/EntityManagerNativeQueryScanner.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.SqlSourceType;
import com.company.sqlvalidator.parser.ResolvedString;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.ArrayList;
import java.util.List;

public class EntityManagerNativeQueryScanner {

    public List<NativeSqlRecord> scan(Node root, ScannerContext context) {
        List<NativeSqlRecord> records = new ArrayList<>();

        for (MethodCallExpr call : root.findAll(MethodCallExpr.class)) {
            if (!"createNativeQuery".equals(call.getNameAsString())) {
                continue;
            }

            if (call.getArguments().isEmpty()) {
                continue;
            }

            Expression sqlExpression = call.getArgument(0);
            ResolvedString resolved = context.getStringResolver().resolve(sqlExpression);

            NativeSqlRecord record = new NativeSqlRecord()
                    .setServiceName(context.getServiceName())
                    .setModuleName(context.getModuleName())
                    .setFilePath(context.getRelativePath().toString())
                    .setClassName(resolveClassName(call))
                    .setMethodName(resolveMethodName(call))
                    .setSourceType(SqlSourceType.ENTITY_MANAGER)
                    .setLineNumber(resolveLineNumber(call))
                    .setSqlVariableName(resolved.getVariableName())
                    .setDynamicSql(resolved.isDynamic())
                    .setRequiresManualReview(!resolved.isResolved())
                    .setManualReviewReason(resolved.isResolved() ? "" : resolved.getReason())
                    .setConfidence(resolved.isResolved() ? resolved.getConfidence() : ExtractionConfidence.LOW);

            if (resolved.isResolved()) {
                fillSqlFields(record, resolved.getValue(), context);
            }

            records.add(record);
        }

        return records;
    }

    private void fillSqlFields(NativeSqlRecord record, String sql, ScannerContext context) {
        record.setSqlText(sql);
        record.setNormalizedSqlText(context.getParameterExtractor().normalizeSql(sql));
        record.setParameterNames(context.getParameterExtractor().extractNamedParameters(sql));
        record.setParameterCount(context.getParameterExtractor().countPositionalParameters(sql));
        record.setParameterMode(context.getParameterExtractor().detectParameterMode(sql));
    }

    private String resolveClassName(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("UNKNOWN_CLASS");
    }

    private String resolveMethodName(Node node) {
        return node.findAncestor(MethodDeclaration.class)
                .map(MethodDeclaration::getNameAsString)
                .orElse("<CLASS_FIELD>");
    }

    private int resolveLineNumber(Node node) {
        return node.getBegin().map(p -> p.line).orElse(-1);
    }
}
```

---

## 9.3 JdbcTemplateScanner.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/JdbcTemplateScanner.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.SqlSourceType;
import com.company.sqlvalidator.parser.ResolvedString;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JdbcTemplateScanner {

    private static final Set<String> JDBC_METHODS = Set.of(
            "query",
            "queryForObject",
            "queryForList",
            "update",
            "execute",
            "batchUpdate"
    );

    public List<NativeSqlRecord> scan(Node root, ScannerContext context) {
        List<NativeSqlRecord> records = new ArrayList<>();

        for (MethodCallExpr call : root.findAll(MethodCallExpr.class)) {
            if (!JDBC_METHODS.contains(call.getNameAsString())) {
                continue;
            }

            if (!looksLikeJdbcTemplateCall(call)) {
                continue;
            }

            if (call.getArguments().isEmpty()) {
                continue;
            }

            Expression sqlExpression = call.getArgument(0);
            ResolvedString resolved = context.getStringResolver().resolve(sqlExpression);

            NativeSqlRecord record = new NativeSqlRecord()
                    .setServiceName(context.getServiceName())
                    .setModuleName(context.getModuleName())
                    .setFilePath(context.getRelativePath().toString())
                    .setClassName(resolveClassName(call))
                    .setMethodName(resolveMethodName(call))
                    .setSourceType(SqlSourceType.JDBC_TEMPLATE)
                    .setLineNumber(resolveLineNumber(call))
                    .setSqlVariableName(resolved.getVariableName())
                    .setDynamicSql(resolved.isDynamic())
                    .setRequiresManualReview(!resolved.isResolved())
                    .setManualReviewReason(resolved.isResolved() ? "" : resolved.getReason())
                    .setConfidence(resolved.isResolved() ? resolved.getConfidence() : ExtractionConfidence.LOW);

            if (resolved.isResolved()) {
                fillSqlFields(record, resolved.getValue(), context);
            }

            records.add(record);
        }

        return records;
    }

    private boolean looksLikeJdbcTemplateCall(MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return false;
        }

        String scope = call.getScope().get().toString();
        String lower = scope.toLowerCase();

        if (lower.contains("namedparameter")) {
            return false;
        }

        return lower.equals("jdbctemplate")
                || lower.endsWith("jdbctemplate")
                || lower.contains(".jdbctemplate");
    }

    private void fillSqlFields(NativeSqlRecord record, String sql, ScannerContext context) {
        record.setSqlText(sql);
        record.setNormalizedSqlText(context.getParameterExtractor().normalizeSql(sql));
        record.setParameterNames(context.getParameterExtractor().extractNamedParameters(sql));
        record.setParameterCount(context.getParameterExtractor().countPositionalParameters(sql));
        record.setParameterMode(context.getParameterExtractor().detectParameterMode(sql));
    }

    private String resolveClassName(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("UNKNOWN_CLASS");
    }

    private String resolveMethodName(Node node) {
        return node.findAncestor(MethodDeclaration.class)
                .map(MethodDeclaration::getNameAsString)
                .orElse("<CLASS_FIELD>");
    }

    private int resolveLineNumber(Node node) {
        return node.getBegin().map(p -> p.line).orElse(-1);
    }
}
```

---

## 9.4 NamedParameterJdbcTemplateScanner.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/NamedParameterJdbcTemplateScanner.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.SqlSourceType;
import com.company.sqlvalidator.parser.ResolvedString;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NamedParameterJdbcTemplateScanner {

    private static final Set<String> NAMED_JDBC_METHODS = Set.of(
            "query",
            "queryForObject",
            "queryForList",
            "update",
            "batchUpdate"
    );

    public List<NativeSqlRecord> scan(Node root, ScannerContext context) {
        List<NativeSqlRecord> records = new ArrayList<>();

        for (MethodCallExpr call : root.findAll(MethodCallExpr.class)) {
            if (!NAMED_JDBC_METHODS.contains(call.getNameAsString())) {
                continue;
            }

            if (!looksLikeNamedParameterJdbcTemplateCall(call)) {
                continue;
            }

            if (call.getArguments().isEmpty()) {
                continue;
            }

            Expression sqlExpression = call.getArgument(0);
            ResolvedString resolved = context.getStringResolver().resolve(sqlExpression);

            NativeSqlRecord record = new NativeSqlRecord()
                    .setServiceName(context.getServiceName())
                    .setModuleName(context.getModuleName())
                    .setFilePath(context.getRelativePath().toString())
                    .setClassName(resolveClassName(call))
                    .setMethodName(resolveMethodName(call))
                    .setSourceType(SqlSourceType.NAMED_PARAMETER_JDBC_TEMPLATE)
                    .setLineNumber(resolveLineNumber(call))
                    .setSqlVariableName(resolved.getVariableName())
                    .setDynamicSql(resolved.isDynamic())
                    .setRequiresManualReview(!resolved.isResolved())
                    .setManualReviewReason(resolved.isResolved() ? "" : resolved.getReason())
                    .setConfidence(resolved.isResolved() ? resolved.getConfidence() : ExtractionConfidence.LOW);

            if (resolved.isResolved()) {
                fillSqlFields(record, resolved.getValue(), context);
            }

            records.add(record);
        }

        return records;
    }

    private boolean looksLikeNamedParameterJdbcTemplateCall(MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return false;
        }

        String scope = call.getScope().get().toString().toLowerCase();

        return scope.contains("namedparameterjdbctemplate")
                || scope.contains("namedjdbctemplate")
                || scope.contains("namedparameter");
    }

    private void fillSqlFields(NativeSqlRecord record, String sql, ScannerContext context) {
        record.setSqlText(sql);
        record.setNormalizedSqlText(context.getParameterExtractor().normalizeSql(sql));
        record.setParameterNames(context.getParameterExtractor().extractNamedParameters(sql));
        record.setParameterCount(context.getParameterExtractor().countPositionalParameters(sql));
        record.setParameterMode(context.getParameterExtractor().detectParameterMode(sql));
    }

    private String resolveClassName(Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("UNKNOWN_CLASS");
    }

    private String resolveMethodName(Node node) {
        return node.findAncestor(MethodDeclaration.class)
                .map(MethodDeclaration::getNameAsString)
                .orElse("<CLASS_FIELD>");
    }

    private int resolveLineNumber(Node node) {
        return node.getBegin().map(p -> p.line).orElse(-1);
    }
}
```

---

## 9.5 NativeSqlScanner.java

路径：

```text
src/main/java/com/company/sqlvalidator/scanner/NativeSqlScanner.java
```

代码：

```java
package com.company.sqlvalidator.scanner;

import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.parser.JavaStringExpressionResolver;
import com.company.sqlvalidator.parser.ServiceNameResolver;
import com.company.sqlvalidator.parser.SqlParameterNameExtractor;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativeSqlScanner {

    private final ServiceNameResolver serviceNameResolver = new ServiceNameResolver();
    private final JavaStringExpressionResolver stringExpressionResolver = new JavaStringExpressionResolver();
    private final SqlParameterNameExtractor parameterNameExtractor = new SqlParameterNameExtractor();

    private final SpringDataQueryScanner springDataQueryScanner = new SpringDataQueryScanner();
    private final EntityManagerNativeQueryScanner entityManagerScanner = new EntityManagerNativeQueryScanner();
    private final JdbcTemplateScanner jdbcTemplateScanner = new JdbcTemplateScanner();
    private final NamedParameterJdbcTemplateScanner namedParameterJdbcTemplateScanner = new NamedParameterJdbcTemplateScanner();

    public List<NativeSqlRecord> scan(List<Path> sourceRoots) throws IOException {
        List<NativeSqlRecord> allRecords = new ArrayList<>();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot)) {
                System.out.println("[WARN] Source root does not exist: " + sourceRoot.toAbsolutePath());
                continue;
            }

            List<Path> javaFiles = findJavaFiles(sourceRoot);
            System.out.println("Scanning source root: " + sourceRoot.toAbsolutePath());
            System.out.println("Java files found: " + javaFiles.size());

            for (Path javaFile : javaFiles) {
                allRecords.addAll(scanJavaFile(sourceRoot, javaFile));
            }
        }

        List<NativeSqlRecord> uniqueRecords = deduplicate(allRecords);
        assignIds(uniqueRecords);
        return uniqueRecords;
    }

    private List<Path> findJavaFiles(Path sourceRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\build\\"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .sorted()
                    .toList();
        }
    }

    private List<NativeSqlRecord> scanJavaFile(Path sourceRoot, Path javaFile) {
        List<NativeSqlRecord> records = new ArrayList<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            ScannerContext context = new ScannerContext(
                    sourceRoot,
                    javaFile,
                    serviceNameResolver,
                    stringExpressionResolver,
                    parameterNameExtractor
            );

            records.addAll(springDataQueryScanner.scan(cu, context));
            records.addAll(entityManagerScanner.scan(cu, context));
            records.addAll(namedParameterJdbcTemplateScanner.scan(cu, context));
            records.addAll(jdbcTemplateScanner.scan(cu, context));
        } catch (Exception ex) {
            System.out.println("[WARN] Failed to parse Java file: " + javaFile.toAbsolutePath());
            System.out.println("       Reason: " + ex.getMessage());
        }

        return records;
    }

    private List<NativeSqlRecord> deduplicate(List<NativeSqlRecord> records) {
        Map<String, NativeSqlRecord> map = new LinkedHashMap<>();

        for (NativeSqlRecord record : records) {
            String key = safe(record.getFilePath()) + "|"
                    + record.getLineNumber() + "|"
                    + safe(record.getClassName()) + "|"
                    + safe(record.getMethodName()) + "|"
                    + safe(record.getNormalizedSqlText());

            map.putIfAbsent(key, record);
        }

        return new ArrayList<>(map.values())
                .stream()
                .sorted(Comparator
                        .comparing(NativeSqlRecord::getServiceName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(NativeSqlRecord::getFilePath, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(NativeSqlRecord::getLineNumber))
                .toList();
    }

    private void assignIds(List<NativeSqlRecord> records) {
        Map<String, Integer> counters = new LinkedHashMap<>();

        for (NativeSqlRecord record : records) {
            String serviceName = safe(record.getServiceName());
            int next = counters.getOrDefault(serviceName, 0) + 1;
            counters.put(serviceName, next);

            record.setId(serviceName + "-" + String.format("%04d", next));
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
```

注意：

```text
如果你的公司 Java 版本低于 Java 16，`.toList()` 可能报错。
那就让 Copilot 改成 `collect(Collectors.toList())`。
如果使用 Java 11，建议让 Copilot 统一处理这个问题。
```

---

# 10. Report Classes

## 10.1 SqlInventoryCsvWriter.java

路径：

```text
src/main/java/com/company/sqlvalidator/report/SqlInventoryCsvWriter.java
```

代码：

```java
package com.company.sqlvalidator.report;

import com.company.sqlvalidator.model.NativeSqlRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SqlInventoryCsvWriter {

    private static final String[] HEADERS = {
            "id",
            "serviceName",
            "moduleName",
            "filePath",
            "className",
            "methodName",
            "sourceType",
            "lineNumber",
            "sqlVariableName",
            "sqlText",
            "normalizedSqlText",
            "parameterMode",
            "parameterNames",
            "parameterCount",
            "isDynamicSql",
            "requiresManualReview",
            "manualReviewReason",
            "confidence",
            "notes"
    };

    public void write(Path outputFile, List<NativeSqlRecord> records) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(HEADERS)
                     .build())) {

            for (NativeSqlRecord record : records) {
                printer.printRecord(
                        safe(record.getId()),
                        safe(record.getServiceName()),
                        safe(record.getModuleName()),
                        safe(record.getFilePath()),
                        safe(record.getClassName()),
                        safe(record.getMethodName()),
                        record.getSourceType() == null ? "" : record.getSourceType().name(),
                        record.getLineNumber(),
                        safe(record.getSqlVariableName()),
                        safe(record.getSqlText()),
                        safe(record.getNormalizedSqlText()),
                        record.getParameterMode() == null ? "" : record.getParameterMode().name(),
                        safe(record.getParameterNamesAsString()),
                        record.getParameterCount(),
                        record.isDynamicSql(),
                        record.isRequiresManualReview(),
                        safe(record.getManualReviewReason()),
                        record.getConfidence() == null ? "" : record.getConfidence().name(),
                        safe(record.getNotes())
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

## 10.2 ScanSummaryWriter.java

路径：

```text
src/main/java/com/company/sqlvalidator/report/ScanSummaryWriter.java
```

代码：

```java
package com.company.sqlvalidator.report;

import com.company.sqlvalidator.model.ExtractionConfidence;
import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.model.SqlSourceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ScanSummaryWriter {

    public void write(Path outputFile, List<NativeSqlRecord> records) throws IOException {
        Files.createDirectories(outputFile.getParent());

        StringBuilder sb = new StringBuilder();

        sb.append("Native SQL Inventory Scan Summary").append(System.lineSeparator());
        sb.append("=================================").append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append("Total Native SQL records: ").append(records.size()).append(System.lineSeparator());
        sb.append("Manual review required: ").append(countManualReview(records)).append(System.lineSeparator());
        sb.append("Dynamic SQL: ").append(countDynamic(records)).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append("By source type:").append(System.lineSeparator());
        for (Map.Entry<SqlSourceType, Long> entry : countBySourceType(records).entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
        }

        sb.append(System.lineSeparator());
        sb.append("By confidence:").append(System.lineSeparator());
        for (Map.Entry<ExtractionConfidence, Long> entry : countByConfidence(records).entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
        }

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
    }

    private long countManualReview(List<NativeSqlRecord> records) {
        return records.stream().filter(NativeSqlRecord::isRequiresManualReview).count();
    }

    private long countDynamic(List<NativeSqlRecord> records) {
        return records.stream().filter(NativeSqlRecord::isDynamicSql).count();
    }

    private Map<SqlSourceType, Long> countBySourceType(List<NativeSqlRecord> records) {
        Map<SqlSourceType, Long> result = new EnumMap<>(SqlSourceType.class);
        for (NativeSqlRecord record : records) {
            SqlSourceType type = record.getSourceType() == null ? SqlSourceType.UNKNOWN : record.getSourceType();
            result.put(type, result.getOrDefault(type, 0L) + 1);
        }
        return result;
    }

    private Map<ExtractionConfidence, Long> countByConfidence(List<NativeSqlRecord> records) {
        Map<ExtractionConfidence, Long> result = new EnumMap<>(ExtractionConfidence.class);
        for (NativeSqlRecord record : records) {
            ExtractionConfidence confidence = record.getConfidence() == null ? ExtractionConfidence.LOW : record.getConfidence();
            result.put(confidence, result.getOrDefault(confidence, 0L) + 1);
        }
        return result;
    }
}
```

---

# 11. Main Application

## 11.1 SqlInventoryScannerApplication.java

路径：

```text
src/main/java/com/company/sqlvalidator/SqlInventoryScannerApplication.java
```

代码：

```java
package com.company.sqlvalidator;

import com.company.sqlvalidator.model.NativeSqlRecord;
import com.company.sqlvalidator.report.ScanSummaryWriter;
import com.company.sqlvalidator.report.SqlInventoryCsvWriter;
import com.company.sqlvalidator.scanner.NativeSqlScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class SqlInventoryScannerApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Native SQL Inventory Scanner...");

        Properties properties = loadProperties();

        String sourceRootsValue = getOption(args, "--sourceRoots", properties.getProperty("scanner.sourceRoots"));
        String outputDirValue = getOption(args, "--outputDir", properties.getProperty("scanner.outputDir", "./output"));

        if (sourceRootsValue == null || sourceRootsValue.isBlank()) {
            throw new IllegalArgumentException("scanner.sourceRoots is empty. Please configure source roots.");
        }

        List<Path> sourceRoots = Arrays.stream(sourceRootsValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Paths::get)
                .collect(Collectors.toList());

        Path outputDir = Paths.get(outputDirValue);

        System.out.println("Source roots:");
        for (Path sourceRoot : sourceRoots) {
            System.out.println("  - " + sourceRoot.toAbsolutePath());
        }
        System.out.println("Output dir:");
        System.out.println("  - " + outputDir.toAbsolutePath());

        NativeSqlScanner scanner = new NativeSqlScanner();
        List<NativeSqlRecord> records = scanner.scan(sourceRoots);

        Path inventoryFile = outputDir.resolve("sql-inventory.csv");
        Path summaryFile = outputDir.resolve("scan-summary.txt");

        new SqlInventoryCsvWriter().write(inventoryFile, records);
        new ScanSummaryWriter().write(summaryFile, records);

        System.out.println();
        System.out.println("Native SQL found: " + records.size());
        System.out.println("Output files:");
        System.out.println("  - " + inventoryFile.toAbsolutePath());
        System.out.println("  - " + summaryFile.toAbsolutePath());
        System.out.println("Done.");
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();

        try (InputStream inputStream = SqlInventoryScannerApplication.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
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

# 12. 如何运行

## 12.1 方式一：直接运行 main

在 IntelliJ 中打开：

```text
SqlInventoryScannerApplication.java
```

右键：

```text
Run 'SqlInventoryScannerApplication.main()'
```

前提是：

```text
application.properties 中的 scanner.sourceRoots 已经配置正确。
```

---

## 12.2 方式二：命令行运行

在 `sql-postgres-validator` 目录下：

```bash
mvn clean package
```

然后：

```bash
java -cp target/sql-postgres-validator-1.0.0-SNAPSHOT.jar com.company.sqlvalidator.SqlInventoryScannerApplication
```

如果依赖没有被打进 jar，这个命令可能无法直接运行。MVP 阶段优先在 IntelliJ 里直接运行 main 即可。

---

## 12.3 方式三：通过 main 参数覆盖路径

IntelliJ Run Configuration 里设置 Program arguments：

```text
--sourceRoots ../drm-service,../workflow-service,../os-service,../entitlement-service,../api-integration-service,../graph-service --outputDir ./output
```

---

# 13. 第一次运行后你应该看到什么

控制台：

```text
Starting Native SQL Inventory Scanner...
Source roots:
  - D:\workspace\Migration-Workspace\drm-service
  - D:\workspace\Migration-Workspace\workflow-service
Output dir:
  - D:\workspace\Migration-Workspace\sql-postgres-validator\output

Scanning source root: ...
Java files found: 120

Native SQL found: 137
Output files:
  - ...\output\sql-inventory.csv
  - ...\output\scan-summary.txt
Done.
```

输出文件：

```text
sql-postgres-validator/output/sql-inventory.csv
sql-postgres-validator/output/scan-summary.txt
```

---

# 14. 如果出现编译问题，优先这样修

## 14.1 `.toList()` 报错

如果公司 Java 版本是 11，且编译器不支持：

```java
.toList()
```

改成：

```java
.collect(Collectors.toList())
```

提示 Copilot：

```text
This project uses Java 11. Please replace Stream.toList() with collect(Collectors.toList()) where needed.
Do not change scanner logic.
```

---

## 14.2 `Set.of(...)` 报错

如果 Java 版本低于 9，`Set.of()` 会报错。

但 Spring Boot 项目大概率至少 Java 8/11。如果真是 Java 8，可以改成：

```java
new HashSet<>(Arrays.asList("query", "update"))
```

提示 Copilot：

```text
If Java version does not support Set.of(), replace it with compatible Java collection initialization.
Do not change scanner behavior.
```

---

## 14.3 JavaParser 版本问题

如果：

```text
TextBlockLiteralExpr not found
```

说明 JavaParser 版本过低。

解决：

```text
1. 升级 javaparser-core 版本
2. 或暂时移除 text block 支持
```

优先让 Copilot 修成公司 Maven 仓库可用版本。

---

## 14.4 Commons CSV API 问题

如果：

```text
CSVFormat.DEFAULT.builder()
```

报错，说明 commons-csv 版本较低。

可以改成旧写法：

```java
CSVFormat.DEFAULT.withHeader(HEADERS)
```

提示 Copilot：

```text
Please adjust Apache Commons CSV usage to match the available version in our Maven repository.
Do not change output columns.
```

---

# 15. 当前版本的已知限制

这个 Phase 1 Scanner 是 MVP，不假装 100% 完美。

已知限制：

```text
1. 不支持跨类 SQL 常量解析
2. 不支持复杂 StringBuilder 还原
3. 不支持 if/else 动态 SQL 完整展开
4. 不支持从 XML 或外部 .sql 文件读取 SQL
5. 不推断参数 Java 类型
6. 不识别 MyBatis XML
7. 不连接数据库
8. 不生成 JUnit
9. 不做 SQL 自动修复
10. 不修改业务源码
```

遇到这些场景，工具应该标记：

```text
requiresManualReview = true
```

---

# 16. Phase 1 验收标准

Phase 1 只要满足以下标准就算完成：

```text
1. 可以扫描 6 个微服务 module
2. 可以生成 sql-inventory.csv
3. 可以生成 scan-summary.txt
4. 能识别 @Query(nativeQuery = true)
5. 能识别 EntityManager.createNativeQuery
6. 能识别 JdbcTemplate
7. 能识别 NamedParameterJdbcTemplate
8. 能提取大部分 :namedParam 参数名
9. 能统计 ? 位置参数数量
10. 能标记动态 SQL 和 unresolved SQL variable
11. 不连接 PostgreSQL
12. 不修改业务代码
```

---

# 17. 给 Copilot 的最终修复提示

复制代码并第一次编译后，如果有报错，把下面这段给 Copilot：

```text
The Phase 1 SQL Inventory Scanner code has already been copied into this module.

Please do not rewrite the whole scanner.

Use docs/01_Dexter_SQL_Validation_Scope.md and docs/02_SQL_Inventory_Scanner_Design.md only as reference.

Please only:
1. Fix compilation errors.
2. Adjust imports and package names.
3. Adjust Java version compatibility.
4. Adjust Maven dependency versions available in our repository.
5. Make SqlInventoryScannerApplication runnable.

Do not:
1. Add PostgreSQL execution.
2. Generate JUnit tests.
3. Implement SQL rewrite rules.
4. Implement database migration.
5. Modify any business service module.
6. Refactor the whole design unless needed for compilation.
```

---

# 18. 下一步

Phase 1 跑通后，先不要马上做 Phase 2。

先检查：

```text
output/sql-inventory.csv
```

重点看：

```text
1. 总数量是否接近预期，比如 100~150 条
2. @Query 是否大部分抓到了
3. EntityManager 是否抓到了
4. JdbcTemplate 是否抓到了
5. dynamicSql / manualReview 是否合理
6. 有没有明显漏掉的 Repository
7. 有没有误抓 JPQL
```

确认 SQL Inventory 基本可靠后，再进入：

```text
04_Parameter_Parser_And_Mock_Value_Design.md
```
