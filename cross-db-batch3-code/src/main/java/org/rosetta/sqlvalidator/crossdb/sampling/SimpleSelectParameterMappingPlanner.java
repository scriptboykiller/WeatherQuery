package org.rosetta.sqlvalidator.crossdb.sampling;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.BindingParameter;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ColumnMapping;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingPlan;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ValueTransform;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative MVP planner. It intentionally supports one simple source table
 * and AND-connected predicates only.
 */
public final class SimpleSelectParameterMappingPlanner {

    private static final String IDENTIFIER = "(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)";
    private static final String COLUMN = "((?:" + IDENTIFIER + "\\.)?" + IDENTIFIER + ")";

    private static final Pattern DIRECT = Pattern.compile(
            "(?is)^\\s*" + COLUMN + "\\s*(=|<>|!=|>=|<=|>|<)\\s*\\?\\s*$");
    private static final Pattern REVERSED = Pattern.compile(
            "(?is)^\\s*\\?\\s*(=|<>|!=|>=|<=|>|<)\\s*" + COLUMN + "\\s*$");
    private static final Pattern LOWER = Pattern.compile(
            "(?is)^\\s*LOWER\\s*\\(\\s*" + COLUMN + "\\s*\\)\\s*=\\s*\\?\\s*$");
    private static final Pattern IN = Pattern.compile(
            "(?is)^\\s*" + COLUMN + "\\s+IN\\s*\\(\\s*\\?\\s*\\)\\s*$");

    private final JdbcPlaceholderCounter placeholderCounter;

    public SimpleSelectParameterMappingPlanner(JdbcPlaceholderCounter placeholderCounter) {
        this.placeholderCounter = placeholderCounter;
    }

    public SamplingPlan plan(String jdbcSql, List<BindingParameter> bindings) {
        if (jdbcSql == null || jdbcSql.isBlank()) {
            return SamplingPlan.manual("JDBC_SQL_MISSING", "JDBC SQL is missing.");
        }

        String sql = stripLeadingComments(jdbcSql).trim();
        if (startsWithKeyword(sql, "WITH")) {
            return SamplingPlan.unsupported(
                    "CTE_NOT_SUPPORTED",
                    "Batch 3 does not automatically sample CTE queries.");
        }

        int totalPlaceholders = placeholderCounter.count(sql);
        if (totalPlaceholders == 0) {
            return SamplingPlan.ready("", "", List.of());
        }

        int fromIndex = findTopLevelKeyword(sql, "FROM", 0);
        int whereIndex = fromIndex < 0 ? -1 : findTopLevelKeyword(sql, "WHERE", fromIndex + 4);
        if (fromIndex < 0 || whereIndex < 0) {
            return SamplingPlan.manual(
                    "FROM_OR_WHERE_NOT_FOUND",
                    "A simple parameterized SELECT with FROM and WHERE is required.");
        }

        String tableSegment = sql.substring(fromIndex + 4, whereIndex).trim();
        TableRef table = parseSingleTable(tableSegment);
        if (table == null) {
            return SamplingPlan.unsupported(
                    "SINGLE_TABLE_REQUIRED",
                    "Batch 3 supports one source table without JOIN or comma joins.");
        }

        int whereEnd = firstTopLevelKeyword(
                sql,
                whereIndex + 5,
                "GROUP BY", "ORDER BY", "LIMIT", "FETCH", "OFFSET", "FOR");
        if (whereEnd < 0) whereEnd = sql.length();

        String whereClause = sql.substring(whereIndex + 5, whereEnd).trim();
        if (findTopLevelKeyword(whereClause, "OR", 0) >= 0) {
            return SamplingPlan.manual("OR_NOT_SUPPORTED", "OR predicates require manual mapping.");
        }
        if (containsKeyword(whereClause, "LIKE")) {
            return SamplingPlan.manual(
                    "LIKE_PATTERN_REQUIRED",
                    "LIKE requires an explicit wildcard-construction rule.");
        }
        if (Pattern.compile("(?is)\\(\\s*SELECT\\b").matcher(whereClause).find()) {
            return SamplingPlan.manual(
                    "SUBQUERY_NOT_SUPPORTED",
                    "Subquery parameter mapping is outside the MVP.");
        }

        List<String> predicates = splitTopLevelAnd(whereClause);
        Map<Integer, PredicateMapping> byJdbcIndex = new LinkedHashMap<>();
        int jdbcIndex = 0;

        for (String rawPredicate : predicates) {
            String predicate = trimOuterParentheses(rawPredicate.trim());
            int predicateCount = placeholderCounter.count(predicate);
            if (predicateCount == 0) continue;
            if (predicateCount != 1) {
                return SamplingPlan.manual(
                        "COMPLEX_PREDICATE",
                        "A predicate contains more than one JDBC parameter: " + predicate);
            }

            jdbcIndex++;
            PredicateMapping mapping = parsePredicate(predicate, table);
            if (mapping == null) {
                return SamplingPlan.manual(
                        "UNSUPPORTED_PARAMETER_EXPRESSION",
                        "Unsupported parameter predicate: " + predicate);
            }
            byJdbcIndex.put(jdbcIndex, mapping);
        }

        if (byJdbcIndex.size() != totalPlaceholders) {
            return SamplingPlan.manual(
                    "UNMAPPED_JDBC_PARAMETER",
                    "Not every JDBC parameter has a supported source-column mapping.");
        }

        Map<Integer, BindingParameter> bindingByJdbc = new LinkedHashMap<>();
        for (BindingParameter binding : bindings) {
            bindingByJdbc.put(binding.jdbcIndex(), binding);
        }

        Map<String, MutableLogicalMapping> logicalMappings = new LinkedHashMap<>();
        for (int index = 1; index <= totalPlaceholders; index++) {
            BindingParameter binding = bindingByJdbc.getOrDefault(
                    index,
                    new BindingParameter(index, index, "param" + index, "", false));
            PredicateMapping predicate = byJdbcIndex.get(index);

            MutableLogicalMapping logical = logicalMappings.computeIfAbsent(
                    binding.logicalKey(),
                    ignored -> new MutableLogicalMapping(
                            binding.logicalParameterIndex(),
                            binding.parameterName(),
                            binding.javaType(),
                            binding.collection() || predicate.collection(),
                            predicate.columnExpression(),
                            predicate.columnName(),
                            predicate.transform()));

            if (!normalizeIdentifier(logical.columnExpression)
                    .equals(normalizeIdentifier(predicate.columnExpression()))
                    || logical.transform != predicate.transform()) {
                return SamplingPlan.manual(
                        "REPEATED_PARAMETER_DIFFERENT_COLUMNS",
                        "One logical parameter maps to different source columns.");
            }
            logical.collection = logical.collection || binding.collection() || predicate.collection();
            logical.jdbcIndexes.add(index);
        }

        List<ColumnMapping> mappings = logicalMappings.values().stream()
                .sorted(Comparator.comparingInt(value -> value.logicalIndex))
                .map(MutableLogicalMapping::toMapping)
                .toList();

        return SamplingPlan.ready(table.tableExpression(), table.alias(), mappings);
    }

    private PredicateMapping parsePredicate(String predicate, TableRef table) {
        Matcher matcher = LOWER.matcher(predicate);
        if (matcher.matches()) {
            return createColumnMapping(matcher.group(1), table, ValueTransform.LOWERCASE, false);
        }
        matcher = IN.matcher(predicate);
        if (matcher.matches()) {
            return createColumnMapping(matcher.group(1), table, ValueTransform.IDENTITY, true);
        }
        matcher = DIRECT.matcher(predicate);
        if (matcher.matches()) {
            return createColumnMapping(matcher.group(1), table, ValueTransform.IDENTITY, false);
        }
        matcher = REVERSED.matcher(predicate);
        if (matcher.matches()) {
            return createColumnMapping(matcher.group(2), table, ValueTransform.IDENTITY, false);
        }
        return null;
    }

    private PredicateMapping createColumnMapping(
            String expression,
            TableRef table,
            ValueTransform transform,
            boolean collection
    ) {
        String[] parts = splitQualifiedIdentifier(expression);
        if (parts.length == 2) {
            String qualifier = normalizeIdentifier(parts[0]);
            if (!qualifier.equals(normalizeIdentifier(table.alias()))
                    && !qualifier.equals(normalizeIdentifier(table.unqualifiedTableName()))) {
                return null;
            }
        }
        return new PredicateMapping(
                expression.trim(),
                parts[parts.length - 1],
                transform,
                collection);
    }

    private TableRef parseSingleTable(String segment) {
        String upper = segment.toUpperCase(Locale.ROOT);
        if (upper.contains(" JOIN ") || containsTopLevelComma(segment)
                || segment.contains("(") || segment.contains(")")) {
            return null;
        }

        List<String> tokens = tokenizeOutsideQuotes(segment);
        if (tokens.isEmpty() || tokens.size() > 3) return null;

        String tableExpression = tokens.get(0);
        String alias = "";
        if (tokens.size() == 2) {
            alias = tokens.get(1);
        } else if (tokens.size() == 3) {
            if (!"AS".equalsIgnoreCase(tokens.get(1))) return null;
            alias = tokens.get(2);
        }

        String[] parts = splitQualifiedIdentifier(tableExpression);
        return new TableRef(tableExpression, alias, parts[parts.length - 1]);
    }

    private List<String> splitTopLevelAnd(String clause) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        int start = 0;

        for (int index = 0; index < clause.length(); index++) {
            char current = clause.charAt(index);
            char next = index + 1 < clause.length() ? clause.charAt(index + 1) : '\0';

            if (!doubleQuote && current == '\'') {
                if (singleQuote && next == '\'') {
                    index++;
                    continue;
                }
                singleQuote = !singleQuote;
                continue;
            }
            if (!singleQuote && current == '"') {
                if (doubleQuote && next == '"') {
                    index++;
                    continue;
                }
                doubleQuote = !doubleQuote;
                continue;
            }
            if (singleQuote || doubleQuote) continue;

            if (current == '(') depth++;
            else if (current == ')') depth--;

            if (depth == 0 && keywordAt(clause, index, "AND")) {
                result.add(clause.substring(start, index));
                index += 2;
                start = index + 1;
            }
        }
        result.add(clause.substring(start));
        return result;
    }

    private int findTopLevelKeyword(String text, String keyword, int start) {
        int depth = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;

        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';

            if (!doubleQuote && current == '\'') {
                if (singleQuote && next == '\'') {
                    index++;
                    continue;
                }
                singleQuote = !singleQuote;
                continue;
            }
            if (!singleQuote && current == '"') {
                if (doubleQuote && next == '"') {
                    index++;
                    continue;
                }
                doubleQuote = !doubleQuote;
                continue;
            }
            if (singleQuote || doubleQuote) continue;

            if (current == '(') depth++;
            else if (current == ')') depth--;
            else if (depth == 0 && keywordAt(text, index, keyword)) return index;
        }
        return -1;
    }

    private int firstTopLevelKeyword(String text, int start, String... keywords) {
        int result = -1;
        for (String keyword : keywords) {
            int found = findTopLevelKeyword(text, keyword, start);
            if (found >= 0 && (result < 0 || found < result)) result = found;
        }
        return result;
    }

    private boolean containsKeyword(String text, String keyword) {
        return Pattern.compile(
                "(?is)(?<![A-Za-z0-9_$])" + Pattern.quote(keyword) + "(?![A-Za-z0-9_$])")
                .matcher(text)
                .find();
    }

    private boolean keywordAt(String text, int index, String keyword) {
        if (index < 0 || index + keyword.length() > text.length()) return false;
        if (!text.regionMatches(true, index, keyword, 0, keyword.length())) return false;
        char before = index == 0 ? ' ' : text.charAt(index - 1);
        char after = index + keyword.length() >= text.length()
                ? ' '
                : text.charAt(index + keyword.length());
        return !isIdentifierCharacter(before) && !isIdentifierCharacter(after);
    }

    private boolean startsWithKeyword(String text, String keyword) {
        return keywordAt(text, 0, keyword);
    }

    private boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private boolean containsTopLevelComma(String text) {
        int depth = 0;
        boolean quote = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '"') quote = !quote;
            else if (!quote && current == '(') depth++;
            else if (!quote && current == ')') depth--;
            else if (!quote && depth == 0 && current == ',') return true;
        }
        return false;
    }

    private List<String> tokenizeOutsideQuotes(String text) {
        List<String> tokens = new ArrayList<>();
        boolean quote = false;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"') {
                quote = !quote;
                current.append(value);
            } else if (!quote && Character.isWhitespace(value)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(value);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private String[] splitQualifiedIdentifier(String expression) {
        List<String> parts = new ArrayList<>();
        boolean quote = false;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (value == '"') {
                quote = !quote;
                current.append(value);
            } else if (!quote && value == '.') {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        parts.add(current.toString().trim());
        return parts.toArray(String[]::new);
    }

    private String trimOuterParentheses(String value) {
        String result = value;
        while (result.startsWith("(") && result.endsWith(")")
                && wrapsWholeExpression(result)) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private boolean wrapsWholeExpression(String value) {
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '(') depth++;
            else if (current == ')') {
                depth--;
                if (depth == 0 && index < value.length() - 1) return false;
            }
        }
        return depth == 0;
    }

    private String stripLeadingComments(String sql) {
        String result = sql;
        boolean changed;
        do {
            changed = false;
            String trimmed = result.stripLeading();
            if (trimmed.startsWith("--")) {
                int newline = trimmed.indexOf('\n');
                result = newline < 0 ? "" : trimmed.substring(newline + 1);
                changed = true;
            } else if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/", 2);
                result = end < 0 ? "" : trimmed.substring(end + 2);
                changed = true;
            }
        } while (changed);
        return result;
    }

    private String normalizeIdentifier(String value) {
        if (value == null) return "";
        return value.trim().replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private record TableRef(String tableExpression, String alias, String unqualifiedTableName) {
    }

    private record PredicateMapping(
            String columnExpression,
            String columnName,
            ValueTransform transform,
            boolean collection
    ) {
    }

    private static final class MutableLogicalMapping {
        private final int logicalIndex;
        private final String parameterName;
        private final String javaType;
        private boolean collection;
        private final String columnExpression;
        private final String columnName;
        private final ValueTransform transform;
        private final List<Integer> jdbcIndexes = new ArrayList<>();

        private MutableLogicalMapping(
                int logicalIndex,
                String parameterName,
                String javaType,
                boolean collection,
                String columnExpression,
                String columnName,
                ValueTransform transform
        ) {
            this.logicalIndex = logicalIndex;
            this.parameterName = parameterName;
            this.javaType = javaType;
            this.collection = collection;
            this.columnExpression = columnExpression;
            this.columnName = columnName;
            this.transform = transform;
        }

        private ColumnMapping toMapping() {
            return new ColumnMapping(
                    logicalIndex,
                    jdbcIndexes,
                    parameterName,
                    javaType,
                    collection,
                    columnExpression,
                    columnName,
                    transform);
        }
    }
}
