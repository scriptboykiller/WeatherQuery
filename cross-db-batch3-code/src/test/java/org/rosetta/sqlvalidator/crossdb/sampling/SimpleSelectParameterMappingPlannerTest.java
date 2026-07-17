package org.rosetta.sqlvalidator.crossdb.sampling;

import org.junit.jupiter.api.Test;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.BindingParameter;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.PlanStatus;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.SamplingPlan;
import org.rosetta.sqlvalidator.crossdb.sampling.ParameterSamplingModels.ValueTransform;
import org.rosetta.sqlvalidator.crossdb.sql.JdbcPlaceholderCounter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleSelectParameterMappingPlannerTest {

    private final SimpleSelectParameterMappingPlanner planner =
            new SimpleSelectParameterMappingPlanner(new JdbcPlaceholderCounter());

    @Test
    void mapsTwoParametersFromTheSameTable() {
        SamplingPlan plan = planner.plan(
                "SELECT * FROM CUSTOMER c WHERE c.STATUS = ? AND c.REGION_ID >= ?",
                List.of(
                        binding(1, 1, "status", "java.lang.String", false),
                        binding(2, 2, "regionId", "java.lang.Long", false)
                ));

        assertEquals(PlanStatus.READY, plan.status());
        assertEquals("CUSTOMER", plan.tableExpression());
        assertEquals("c", plan.tableAlias());
        assertEquals(2, plan.mappings().size());
    }

    @Test
    void supportsLowerAndSingleValueCollection() {
        SamplingPlan lower = planner.plan(
                "SELECT * FROM CUSTOMER WHERE LOWER(NAME) = ?",
                List.of(binding(1, 1, "name", "java.lang.String", false)));
        assertEquals(ValueTransform.LOWERCASE, lower.mappings().get(0).transform());

        SamplingPlan in = planner.plan(
                "SELECT * FROM CUSTOMER WHERE ID IN (?)",
                List.of(binding(1, 1, "ids", "java.util.List<java.lang.Long>", true)));
        assertTrue(in.mappings().get(0).collection());
    }

    @Test
    void reusesOneLogicalValueForRepeatedJdbcPositions() {
        SamplingPlan plan = planner.plan(
                "SELECT * FROM CUSTOMER WHERE ID >= ? AND ID <= ?",
                List.of(
                        binding(1, 1, "id", "java.lang.Long", false),
                        binding(2, 1, "id", "java.lang.Long", false)
                ));

        assertEquals(PlanStatus.READY, plan.status());
        assertEquals(1, plan.mappings().size());
        assertEquals(List.of(1, 2), plan.mappings().get(0).jdbcIndexes());
    }

    @Test
    void rejectsJoinOrLikeAndSubquery() {
        assertEquals(
                PlanStatus.SKIPPED_UNSUPPORTED,
                planner.plan(
                        "SELECT * FROM CUSTOMER c JOIN REGION r ON r.ID=c.REGION_ID WHERE c.ID=?",
                        List.of(binding(1, 1, "id", "java.lang.Long", false)))
                        .status());

        assertEquals(
                PlanStatus.MANUAL_MAPPING_REQUIRED,
                planner.plan(
                        "SELECT * FROM CUSTOMER WHERE ID=? OR REGION_ID=?",
                        List.of(
                                binding(1, 1, "id", "java.lang.Long", false),
                                binding(2, 2, "regionId", "java.lang.Long", false)))
                        .status());

        assertEquals(
                "LIKE_PATTERN_REQUIRED",
                planner.plan(
                        "SELECT * FROM CUSTOMER WHERE NAME LIKE ?",
                        List.of(binding(1, 1, "name", "java.lang.String", false)))
                        .reasonCode());

        assertEquals(
                "SUBQUERY_NOT_SUPPORTED",
                planner.plan(
                        "SELECT * FROM CUSTOMER WHERE ID=(SELECT ID FROM OTHER WHERE CODE=?)",
                        List.of(binding(1, 1, "code", "java.lang.String", false)))
                        .reasonCode());
    }

    private BindingParameter binding(
            int jdbc,
            int logical,
            String name,
            String type,
            boolean collection
    ) {
        return new BindingParameter(jdbc, logical, name, type, collection);
    }
}
