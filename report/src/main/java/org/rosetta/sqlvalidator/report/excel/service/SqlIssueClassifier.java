package org.rosetta.sqlvalidator.report.excel.service;

import org.rosetta.sqlvalidator.report.excel.model.BindingRecord;
import org.rosetta.sqlvalidator.report.excel.model.ClassificationSource;
import org.rosetta.sqlvalidator.report.excel.model.ExecutionRecord;
import org.rosetta.sqlvalidator.report.excel.model.InventoryRecord;
import org.rosetta.sqlvalidator.report.excel.model.IssueClassification;
import org.rosetta.sqlvalidator.report.excel.model.IssueGroup;
import org.rosetta.sqlvalidator.report.excel.model.IssueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SqlIssueClassifier {

    private static final Pattern UPDATE_ALIAS_IN_SET = Pattern.compile(
            "(?is)\\bupdate\\s+[\\w.$\\\"]+\\s+(?:as\\s+)?([a-z_][\\w$]*)\\s+set\\s+\\1\\.[a-z_][\\w$]*\\s*=");

    private static final Pattern JPA_OR_JDBC_PARAMETER = Pattern.compile("\\?(?:\\d+)?|:[a-zA-Z_][a-zA-Z0-9_]*");

    public IssueClassification classify(final InventoryRecord inventory,
                                        final ExecutionRecord execution,
                                        final List<BindingRecord> bindings) {
        final String status = lower(execution == null ? "" : execution.executionStatus());
        final String message = lower(execution == null ? "" : execution.message());
        final String sql = lower(joinSql(inventory, execution));
        final List<BindingRecord> safeBindings = bindings == null ? List.of() : bindings;
        final Set<String> signals = detectSignals(sql);

        if (status.equals("passed")) {
            return result(IssueGroup.PASSED, IssueType.PASSED, "None", ClassificationSource.STATUS,
                    signals, "No action required.");
        }

        if (isSkipped(status)) {
            return result(IssueGroup.MANUAL_REVIEW, IssueType.SKIPPED_OR_NOT_EXECUTED, "Review",
                    ClassificationSource.STATUS, signals,
                    "Review why the SQL was skipped or not executed before estimating migration work.");
        }

        if (message.contains("operator does not exist") && containsTypeMismatch(message)) {
            final boolean stringBinding = safeBindings.stream()
                    .anyMatch(binding -> lower(binding.javaType()).contains("string"));
            return result(IssueGroup.PARAMETER_TYPE_OR_BINDING, IssueType.PARAMETER_TYPE_MISMATCH, "High",
                    stringBinding ? ClassificationSource.ERROR_MESSAGE_AND_BINDING_PLAN : ClassificationSource.ERROR_MESSAGE,
                    signals,
                    "Align the Java parameter type with the PostgreSQL column/operator type or add an explicit cast only when business-safe.");
        }

        if (message.contains("parameter") && (message.contains("bind") || message.contains("index"))) {
            return result(IssueGroup.PARAMETER_TYPE_OR_BINDING, IssueType.PARAMETER_BINDING_ERROR, "High",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Review positional/named parameter mapping and the generated binding plan.");
        }

        if (message.contains("column") && message.contains("of relation")
                && message.contains("does not exist") && UPDATE_ALIAS_IN_SET.matcher(sql).find()) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.UPDATE_TARGET_ALIAS, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "For PostgreSQL UPDATE statements, remove the target-table alias from the left side of SET assignments.");
        }

        if (message.contains("missing from-clause entry for table") && message.contains("seq")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.SEQUENCE_SYNTAX, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace Oracle/H2 sequence syntax such as SEQ.NEXTVAL with PostgreSQL nextval('sequence_name').");
        }

        if (message.contains("relation \"dual\" does not exist") || sql.contains(" from dual")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.DUAL_TABLE, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Remove FROM DUAL and use a direct PostgreSQL SELECT expression.");
        }

        if (message.contains("function nvl(") || sql.contains("nvl(")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.NVL_FUNCTION, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace NVL(expr1, expr2) with COALESCE(expr1, expr2).");
        }

        if (message.contains("column \"sysdate\" does not exist") || sql.contains("sysdate")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.SYSDATE_FUNCTION, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace SYSDATE with CURRENT_TIMESTAMP or CURRENT_DATE according to the required precision.");
        }

        if (message.contains("column \"rownum\" does not exist") || sql.contains("rownum")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.ROWNUM_PSEUDOCOLUMN, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace ROWNUM filtering with LIMIT/FETCH FIRST or ROW_NUMBER() OVER (...), depending on semantics.");
        }

        if (message.contains("function to_char(") || sql.contains("to_char(")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.TO_CHAR_FUNCTION, "Medium",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Review TO_CHAR argument types and format masks; add PostgreSQL-compatible casts or formats.");
        }

        if (message.contains("function to_timestamp(") || sql.contains("to_timestamp(")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.TO_TIMESTAMP_FUNCTION, "Medium",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace Oracle/H2 TO_TIMESTAMP usage with a PostgreSQL-compatible timestamp expression and format mask.");
        }

        if (message.contains("function dateadd(") || sql.contains("dateadd(")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.DATEADD_FUNCTION, "High",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Replace DATEADD with PostgreSQL interval arithmetic, for example CURRENT_TIMESTAMP - INTERVAL '10 minutes'.");
        }

        if (sql.contains("current_timestamp()")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.DATETIME_FUNCTION_SYNTAX, "Medium",
                    ClassificationSource.SQL_TEXT, signals,
                    "Use PostgreSQL CURRENT_TIMESTAMP without empty parentheses, or now(), after confirming expected behavior.");
        }

        if (isQuotedIdentifierMismatch(message, sql)) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.QUOTED_IDENTIFIER_CASE_MISMATCH, "Medium",
                    ClassificationSource.ERROR_MESSAGE_AND_SQL_TEXT, signals,
                    "Review quoted identifiers such as \"VALUE\" or \"ENTITY\". PostgreSQL preserves quoted case exactly; use the actual column name/case.");
        }

        if (message.contains("column") && message.contains("does not exist")) {
            return result(IssueGroup.SOURCE_SQL_DEFECT, IssueType.COLUMN_NOT_FOUND, "High",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Confirm the source SQL column name against the migrated PostgreSQL schema; correct the SQL or schema mapping.");
        }

        if (message.contains("relation") && message.contains("does not exist")) {
            return result(IssueGroup.DATABASE_ENVIRONMENT, IssueType.RELATION_NOT_FOUND, "Environment",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Confirm table/view migration, schema, search_path and identifier case before estimating SQL rewrite effort.");
        }

        if (message.contains("missing from-clause entry")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.IDENTIFIER_CASE_OR_QUOTING, "Medium",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Review identifier qualification, aliases, quoting and Oracle/H2-specific object references.");
        }

        if (message.contains("function") && message.contains("does not exist")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.UNSUPPORTED_FUNCTION, "Medium",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Replace or rewrite the unsupported function with a PostgreSQL equivalent.");
        }

        if (sql.contains("merge into")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.MERGE_SYNTAX, "Medium",
                    ClassificationSource.SQL_TEXT, signals,
                    "Review MERGE syntax and target PostgreSQL version; rewrite to PostgreSQL-supported MERGE or UPSERT when required.");
        }

        if (message.contains("syntax error") || message.contains("sqlstate: 42601") || message.contains("42601")) {
            return result(IssueGroup.SQL_DIALECT_COMPATIBILITY, IssueType.GENERIC_SQL_SYNTAX, "Medium",
                    ClassificationSource.ERROR_MESSAGE, signals,
                    "Review the PostgreSQL parser error position and convert the Oracle/H2-specific syntax.");
        }

        if (inventory != null && (inventory.requiresManualReview() || inventory.dynamicSql())) {
            return result(IssueGroup.MANUAL_REVIEW, IssueType.UNKNOWN, "Review",
                    ClassificationSource.SQL_TEXT, signals,
                    "Resolve the dynamic/manual SQL text before assigning migration effort.");
        }

        return result(IssueGroup.MANUAL_REVIEW, IssueType.UNKNOWN, "Review",
                ClassificationSource.FALLBACK, signals,
                "Manual review required. Use the original SQL, executed SQL, SQLState and PostgreSQL message together.");
    }

    public String parameterSummary(final List<BindingRecord> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return "No parameters";
        }
        return bindings.stream()
                .map(binding -> {
                    final String name = blank(binding.javaParameterName()) ? binding.originalParameter() : binding.javaParameterName();
                    final String type = blank(binding.javaType()) ? "?" : binding.javaType();
                    final String kind = blank(binding.parameterKind()) ? "" : "/" + binding.parameterKind();
                    final String value = blank(binding.mockValue()) ? "" : "=" + binding.mockValue();
                    return "#" + binding.bindingIndex() + " " + name + ":" + type + kind + value;
                })
                .collect(Collectors.joining("; "));
    }

    private IssueClassification result(final IssueGroup group,
                                       final IssueType type,
                                       final String priority,
                                       final ClassificationSource source,
                                       final Set<String> signals,
                                       final String recommendation) {
        return new IssueClassification(group, type, priority, source,
                signals.isEmpty() ? "" : String.join("; ", signals), recommendation);
    }

    private Set<String> detectSignals(final String sql) {
        final Set<String> signals = new LinkedHashSet<>();
        if (sql.contains(".nextval") || sql.contains(" nextval from dual")) signals.add("SEQUENCE/NEXTVAL");
        if (sql.contains(" from dual")) signals.add("DUAL");
        if (sql.contains("nvl(")) signals.add("NVL");
        if (sql.contains("sysdate")) signals.add("SYSDATE");
        if (sql.contains("rownum")) signals.add("ROWNUM");
        if (sql.contains("to_char(")) signals.add("TO_CHAR");
        if (sql.contains("to_timestamp(")) signals.add("TO_TIMESTAMP");
        if (sql.contains("dateadd(")) signals.add("DATEADD");
        if (sql.contains("current_timestamp()")) signals.add("CURRENT_TIMESTAMP()");
        if (sql.contains("\"value\"") || sql.contains("\"entity\"")) signals.add("QUOTED RESERVED IDENTIFIER");
        if (UPDATE_ALIAS_IN_SET.matcher(sql).find()) signals.add("UPDATE SET alias.column");
        if (sql.contains("merge into")) signals.add("MERGE");
        if (JPA_OR_JDBC_PARAMETER.matcher(sql).find()) signals.add("PARAMETERIZED SQL");
        return signals;
    }

    private boolean isQuotedIdentifierMismatch(final String message, final String sql) {
        if (!(message.contains("column") && message.contains("does not exist"))) {
            return false;
        }
        return sql.contains("\"value\"") || sql.contains("\"entity\"")
                || message.contains("column \"value\"") || message.contains("column \"entity\"");
    }

    private boolean containsTypeMismatch(final String message) {
        return message.contains("character varying")
                && (message.contains("numeric") || message.contains("integer") || message.contains("bigint")
                || message.contains("date") || message.contains("timestamp"));
    }

    private boolean isSkipped(final String status) {
        return status.startsWith("skipped") || status.equals("not_executed") || status.equals("not executed");
    }

    private String joinSql(final InventoryRecord inventory, final ExecutionRecord execution) {
        final List<String> parts = new ArrayList<>();
        if (inventory != null) {
            parts.add(inventory.sqlText());
            parts.add(inventory.normalizedSqlText());
        }
        if (execution != null) {
            parts.add(execution.jdbcSql());
            parts.add(execution.adaptedJdbcSql());
        }
        return parts.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" "));
    }

    private String lower(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean blank(final String value) {
        return value == null || value.isBlank();
    }
}
