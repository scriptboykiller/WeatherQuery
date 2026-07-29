package com.rosetta.tcup.physicalmodel.api;

import com.rosetta.tcup.physicalmodel.domain.PhysicalAttribute;
import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import java.util.List;

public record ParsePhysicalModelResponse(
        SourceType sourceType,
        int count,
        List<PhysicalAttribute> attributes,
        List<String> warnings) {

    static ParsePhysicalModelResponse from(ParseResult result) {
        return new ParsePhysicalModelResponse(
                result.sourceType(), result.count(), result.attributes(), result.warnings());
    }
}
