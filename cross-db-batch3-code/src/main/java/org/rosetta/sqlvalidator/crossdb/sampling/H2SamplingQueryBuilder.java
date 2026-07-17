package org.rosetta.sqlvalidator.crossdb.sampling;

import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ColumnMapping;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.PlanStatus;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingPlan;

import java.util.ArrayList;
import java.util.List;

public final class H2SamplingQueryBuilder {

    public SamplingQuery build(SamplingPlan plan, boolean preferNonNullValues) {
        if (plan.status() != PlanStatus.READY) {
            throw new IllegalArgumentException("Sampling plan is not READY: " + plan.status());
        }

        List<String> aliases = new ArrayList<>();
        List<String> projections = new ArrayList<>();
        List<String> nonNullPredicates = new ArrayList<>();

        for (int index = 0; index < plan.mappings().size(); index++) {
            ColumnMapping mapping = plan.mappings().get(index);
            String alias = "P" + (index + 1);
            aliases.add(alias);
            projections.add(mapping.sourceColumnExpression() + " AS " + alias);
            if (preferNonNullValues) {
                nonNullPredicates.add(mapping.sourceColumnExpression() + " IS NOT NULL");
            }
        }

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ")
                .append(String.join(", ", projections))
                .append(" FROM ")
                .append(plan.tableExpression());

        if (!plan.tableAlias().isBlank()) {
            sql.append(" ").append(plan.tableAlias());
        }
        if (!nonNullPredicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", nonNullPredicates));
        }
        sql.append(" LIMIT ?");

        return new SamplingQuery(sql.toString(), aliases);
    }

    public record SamplingQuery(String sql, List<String> resultAliases) {
        public SamplingQuery {
            resultAliases = List.copyOf(resultAliases);
        }
    }
}
