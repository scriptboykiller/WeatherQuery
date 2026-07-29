package com.rosetta.tcup.physicalmodel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonSchemaPhysicalModelParserTest {
    private final JsonSchemaPhysicalModelParser parser = new JsonSchemaPhysicalModelParser(new ObjectMapper());

    @Test
    void supportsInlineSchemasAndSimpleArrays() {
        var result = parser.parse("""
                {"type":"object","properties":{"app_name":{"type":"string"},
                "feature_toggle":{"properties":{"local_run":{"type":"boolean"}}},
                "owners":{"type":"array","items":{"type":"string"}}}}""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("app_name", "feature_toggle.local_run", "owners[]");
    }

    @Test
    void resolvesLocalReferencesAndStopsCycles() {
        var result = parser.parse("""
                {"type":"object","properties":{"node":{"$ref":"#/$defs/Node"},"missing":{"$ref":"#/$defs/Missing"}},
                "$defs":{"Node":{"properties":{"name":{"type":"string"},"child":{"$ref":"#/$defs/Node"}}}}}""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path()).containsExactly("node.name");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Recursive"));
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Missing"));
    }

    @Test
    void warnsForUnsupportedDynamicProperties() {
        var result = parser.parse("""
                {"type":"object","properties":{"code":{"type":"string"}},
                 "patternProperties":{"^S_":{"type":"string"}}}""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path()).containsExactly("code");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("patternProperties"));
    }
}
