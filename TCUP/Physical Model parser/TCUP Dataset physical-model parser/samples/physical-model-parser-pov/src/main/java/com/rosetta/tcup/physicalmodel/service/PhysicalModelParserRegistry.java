package com.rosetta.tcup.physicalmodel.service;

import com.rosetta.tcup.physicalmodel.domain.SourceType;
import com.rosetta.tcup.physicalmodel.parser.PhysicalModelParser;
import com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PhysicalModelParserRegistry {
    private final Map<SourceType, PhysicalModelParser> parsers = new EnumMap<>(SourceType.class);

    public PhysicalModelParserRegistry(List<PhysicalModelParser> parserImplementations) {
        for (PhysicalModelParser parser : parserImplementations) {
            parsers.put(parser.supportedType(), parser);
        }
    }

    public PhysicalModelParser forSourceType(SourceType sourceType) {
        PhysicalModelParser parser = parsers.get(sourceType);
        if (parser == null) {
            throw new InvalidPhysicalModelInputException("Unsupported source type: " + sourceType);
        }
        return parser;
    }
}
