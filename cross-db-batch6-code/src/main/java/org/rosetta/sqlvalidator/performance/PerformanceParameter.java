package org.rosetta.sqlvalidator.performance;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerformanceParameter(
        int jdbcIndex,
        Integer logicalParameterIndex,
        String name,
        PerformanceValueType valueType,
        String jdbcType,
        Object value,
        String encoding
) {
}
