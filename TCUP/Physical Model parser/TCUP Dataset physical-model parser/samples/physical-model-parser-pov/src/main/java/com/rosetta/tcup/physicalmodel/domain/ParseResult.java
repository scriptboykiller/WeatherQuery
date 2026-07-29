package com.rosetta.tcup.physicalmodel.domain;

import java.util.List;

public record ParseResult(SourceType sourceType, List<PhysicalAttribute> attributes, List<String> warnings) {
    public int count() {
        return attributes.size();
    }
}
