package com.rosetta.tcup.physicalmodel.parser;

import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;

public interface PhysicalModelParser {
    SourceType supportedType();

    ParseResult parse(String content);
}
