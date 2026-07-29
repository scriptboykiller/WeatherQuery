package com.rosetta.tcup.physicalmodel.service;

import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhysicalModelExtractionService {
    private final PhysicalModelParserRegistry parserRegistry;
    private final int maximumContentLength;

    public PhysicalModelExtractionService(
            PhysicalModelParserRegistry parserRegistry,
            @Value("${physical-model.maximum-content-length:1048576}") int maximumContentLength) {
        this.parserRegistry = parserRegistry;
        this.maximumContentLength = maximumContentLength;
    }

    public ParseResult parse(SourceType sourceType, String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidPhysicalModelInputException("Source content must not be blank");
        }
        if (content.length() > maximumContentLength) {
            throw new InvalidPhysicalModelInputException("Source content exceeds the configured maximum length");
        }
        return parserRegistry.forSourceType(sourceType).parse(content);
    }
}
