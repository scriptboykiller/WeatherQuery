# Batch 7：最终稳定化、回归测试与发行包

## 目标

Batch 7 不再增加业务功能，只完成：

1. Batch 1–6 全流程回归与失败隔离；
2. 外部配置、Windows 脚本和英文用户手册；
3. 不含源码、只需 JDK 17 的正式发行 ZIP。

同事的性能工具 JAR 尚未交付，因此不得等待，也不得制作假 JAR。
`performance-report` 保持可选并默认关闭。核心校验、Cross DB Comparison
和 Excel 必须能够在没有性能 JAR 的情况下运行。

## 必须验证的主流程

### Capture Baseline

```text
inventory
→ sanity
→ validation-explain
→ cross-db-validation / CAPTURE_BASELINE
→ excel-report
```

验收重点：

- 远程 H2 JDBC 正常；
- H2 schema 可为空；
- JDBC URL 自带参数保持不变；
- PostgreSQL PASSED 的 SELECT 执行双库；
- 其他合格 SELECT 只保存 H2 Baseline；
- Baseline 有真实数据行且不能静默覆盖。

### Compare PostgreSQL With Baseline

```text
inventory
→ sanity
→ validation-explain
→ cross-db-validation / COMPARE_POSTGRES_WITH_BASELINE
→ excel-report
```

验收重点：

- 不连接 H2；
- 只读不可变 Baseline；
- 仅使用 `baselineKey` 匹配；
- 使用已保存的相同参数；
- 只执行当前 PostgreSQL；
- 生成 Comparison CSV 和 Excel 第三个 Sheet。

### Optional Performance

性能 JAR 不存在时：

```text
不运行 performance-report
→ excel-report 仍成功
→ Performance Report 显示 Not Generated
```

性能 JAR 后续存在时才运行：

```text
performance-report
→ excel-report
```

最小结果协议固定为：

```json
{
  "responseVersion": "v2",
  "caseId": "SQL-001",
  "status": "SUCCESS",
  "htmlReport": "index.html",
  "errorCode": "",
  "errorMessage": ""
}
```

## 失败隔离

Phase 级错误应停止当前 Phase：

- 必需配置或输入不存在；
- Baseline 无有效数据；
- 输出不可写；
- 数据库连接完全失败；
- 用户显式运行性能 Phase 但工具 JAR 不存在。

SQL/Case 级错误应记录并继续：

- 单条 SQL 失败或超时；
- 参数解码失败；
- baselineKey 找不到或重复；
- 结果过大；
- 单个性能 Case、result.json 或 HTML 失败。

## 正式发行包

```text
sql-postgres-validator-release/
├── sql-postgres-validator-<version>.jar
├── config/
├── bin/
├── tools/
├── baseline/
├── output/
├── logs/
├── docs/
├── VERSION.txt
└── SHA256SUMS.txt
```

不得包含源码、Git 元数据、IDE 配置、测试源码、业务工程源码、内部 Batch
文档、真实日志或临时构建文件。

## 完成标准

必须报告：

- 编译与测试结果；
- Capture、Compare、Excel 流程结果；
- 无性能 JAR 时的行为；
- Release 目录和 ZIP；
- SHA-256；
- 未解决问题；
- 明确写出 Performance Comparator：OPTIONAL / NOT INCLUDED。
