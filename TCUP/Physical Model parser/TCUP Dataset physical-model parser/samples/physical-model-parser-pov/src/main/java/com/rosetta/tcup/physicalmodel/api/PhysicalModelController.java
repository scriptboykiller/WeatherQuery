package com.rosetta.tcup.physicalmodel.api;

import com.rosetta.tcup.physicalmodel.service.PhysicalModelExtractionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/physical-models", produces = MediaType.APPLICATION_JSON_VALUE)
public class PhysicalModelController {
    private final PhysicalModelExtractionService extractionService;

    public PhysicalModelController(PhysicalModelExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping(path = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ParsePhysicalModelResponse parse(@Valid @RequestBody ParsePhysicalModelRequest request) {
        return ParsePhysicalModelResponse.from(extractionService.parse(request.sourceType(), request.content()));
    }
}
