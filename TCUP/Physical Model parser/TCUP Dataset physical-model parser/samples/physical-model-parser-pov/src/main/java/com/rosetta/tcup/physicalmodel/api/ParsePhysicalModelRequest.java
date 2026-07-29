package com.rosetta.tcup.physicalmodel.api;

import com.rosetta.tcup.physicalmodel.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ParsePhysicalModelRequest(
        @NotNull SourceType sourceType,
        @NotBlank String content) {
}
