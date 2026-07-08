# 06_SQL_Text_Sanity_Check_Design.md

## 1. 结论

建议增加这个能力。

但它不应该叫 H2 Validation，也不应该连接 H2 数据库。

建议命名为：

```text
Phase 1.5：SQL Text Sanity Check
```

它的目标是：

> 在进入 PostgreSQL 真实数据库执行验证之前，先检查 Scanner 抽取出来的 `sqlText / normalizedSqlText` 是否是干净、可解析、没有明显抽取污染的 SQL 文本。

---

## 2. 为什么值得加

现在 Scanner 已经抓到了 365 条 Native SQL。

在进入 PostgreSQL 前，存在一个质量风险：

```text
Scanner 可能把 Java 字符串中的转义字符、拼接残留、注释残留、异常字符一起抓进 SQL。
```

例如：

```text
\n
\r
\t
\s
" +
+ "
.append(
Java 注释
不平衡引号
空 SQL
```

这些问题如果直接进入 PostgreSQL 阶段，会让你很难判断：

```text
到底是 SQL 本身不兼容 PostgreSQL
还是 Scanner 抽取出来的 SQL 文本脏了
```

所以 Phase 1.5 的价值是：

```text
先保证 SQL 文本质量，再做 PostgreSQL 兼容性验证。
```

---

## 3. 为什么不使用 H2 in-memory

如果使用 H2 in-memory：

```java
connection.prepareStatement(sql)
```

很多 SQL 会因为没有真实表结构而失败，例如：

```text
Table not found
Column not found
Function not found
```

这会制造很多噪音。

要让 H2 真正判断一条查询是否可执行，通常需要：

```text
1. 表结构
2. 字段
3. 函数
4. Sequence
5. 部分数据或至少元数据
```

这已经超出 Phase 1.5 的 MVP 范围。

所以本阶段不要连接 H2。

---

## 4. 推荐方案

使用：

```text
Text Rule Check + JSqlParser Parse Check
```

JSqlParser 是一个 Java SQL parser，可以把 SQL statement 解析成 Java class hierarchy。它适合做 SQL 文本结构解析，不需要连接数据库，不需要真实 schema。

但它不是最终数据库执行验证工具。

它能帮你判断：

```text
这条 SQL 文本结构上是否像一条可解析 SQL。
```

它不能保证：

```text
1. H2 一定能执行
2. PostgreSQL 一定能执行
3. 表名存在
4. 字段存在
5. 函数存在
6. 参数类型正确
```

---

## 5. 输入输出

### 输入

```text
output/sql-inventory.csv
```

### 输出

```text
output/sql-sanity-report.csv
output/sql-sanity-summary.txt
```

---

## 6. 检查逻辑

每条 SQL 按下面顺序处理：

```text
1. 取 effectiveSqlText
2. 做基础文本规则检查
3. 替换命名参数为 ?
4. 去掉末尾 ;
5. 用 JSqlParser parse
6. 输出 sanity status
```

---

## 7. effectiveSqlText 规则

```text
effectiveSqlText =
    normalizedSqlText 有值
        ? normalizedSqlText
        : sqlText
```

后续如果增加人工审核字段，也可以改成：

```text
effectiveSqlText =
    resolvedSqlText 有值
        ? resolvedSqlText
        : normalizedSqlText 有值
            ? normalizedSqlText
            : sqlText
```

---

## 8. 文本规则检查

建议检查以下问题：

```text
EMPTY_SQL
LITERAL_BACKSLASH_N
LITERAL_BACKSLASH_R
LITERAL_BACKSLASH_T
LITERAL_BACKSLASH_S
JAVA_CONCAT_REMAINDER
JAVA_APPEND_REMAINDER
UNBALANCED_SINGLE_QUOTE
SUSPICIOUS_DOUBLE_QUOTE
JAVA_LINE_COMMENT_OR_URL
SQL_OR_JAVA_BLOCK_COMMENT
```

注意：

```text
真实换行不是问题。
两个字符 \ 和 n 才是 LITERAL_BACKSLASH_N。
```

---

## 9. JSqlParser 解析前预处理

JSqlParser 不一定理解所有业务 SQL 的命名参数。

所以 parse 前建议：

```text
:paramName -> ?
```

正则：

```text
(?<!:):([A-Za-z][A-Za-z0-9_]*)
```

这样可以避免误伤 PostgreSQL 的 `::date`。

还要：

```text
去掉 SQL 末尾分号
```

---

## 10. 状态设计

建议状态：

```text
CLEAN_PARSE_OK
TEXT_SUSPECT_PARSE_OK
PARSE_ERROR_NO_TEXT_ISSUE
TEXT_SUSPECT_PARSE_ERROR
SKIPPED_EMPTY_SQL
SKIPPED_DYNAMIC_SQL
```

含义：

```text
CLEAN_PARSE_OK:
文本规则没有发现问题，JSqlParser 也能解析。

TEXT_SUSPECT_PARSE_OK:
文本规则发现可疑点，但 JSqlParser 仍能解析。
需要人工看一下是否需要修 normalizer。

PARSE_ERROR_NO_TEXT_ISSUE:
JSqlParser 解析失败，但文本规则没发现明显脏字符。
可能是 parser 不支持某些 H2/Oracle/vendor syntax，不一定是 Scanner 问题。

TEXT_SUSPECT_PARSE_ERROR:
文本规则发现问题，同时 JSqlParser 解析失败。
优先怀疑 Scanner/normalizer 抽取污染。

SKIPPED_EMPTY_SQL:
SQL 为空。

SKIPPED_DYNAMIC_SQL:
动态 SQL 无法静态解析。
```

---

## 11. 如何判断是否能进入下一阶段

建议规则：

```text
CLEAN_PARSE_OK
    -> 可以进入 PostgreSQL Validation

TEXT_SUSPECT_PARSE_OK
    -> 可以进入，但建议先人工看 sanityIssues

PARSE_ERROR_NO_TEXT_ISSUE
    -> 不要直接阻塞，可以进入后续真实 PostgreSQL 验证或人工确认

TEXT_SUSPECT_PARSE_ERROR
    -> 优先修 Scanner / normalizer

SKIPPED_EMPTY_SQL
    -> 不能进入

SKIPPED_DYNAMIC_SQL
    -> 需要人工确认 resolvedSqlText 后再进入
```

---

## 12. Report 字段

`sql-sanity-report.csv` 建议字段：

```text
id
serviceName
className
methodName
sourceType
confidence
requiresManualReview
isDynamicSql
effectiveSqlText
parserSql
sanityStatus
sanityIssues
parserError
recommendation
```

---

## 13. Summary 示例

```text
SQL Text Sanity Check Summary
=============================

Total records: 365

CLEAN_PARSE_OK: 330
TEXT_SUSPECT_PARSE_OK: 5
PARSE_ERROR_NO_TEXT_ISSUE: 21
TEXT_SUSPECT_PARSE_ERROR: 3
SKIPPED_EMPTY_SQL: 0
SKIPPED_DYNAMIC_SQL: 6
```

---

## 14. 给老板的 Demo 话术

```text
Before executing against PostgreSQL, we added a local SQL text sanity check.

It does not connect to any database and does not upload company data.

It checks whether extracted SQL text is clean and structurally parseable.

This helps us separate scanner extraction issues from real PostgreSQL compatibility issues.
```

---

## 15. MVP 边界

本阶段只做：

```text
1. 读取 sql-inventory.csv
2. 检查文本可疑字符
3. JSqlParser parse
4. 输出 sql-sanity-report.csv
5. 输出 sql-sanity-summary.txt
```

不做：

```text
1. 不连接 H2
2. 不连接 PostgreSQL
3. 不建表
4. 不校验字段
5. 不校验函数
6. 不修 SQL
7. 不修改业务代码
8. 不生成 JUnit
```
