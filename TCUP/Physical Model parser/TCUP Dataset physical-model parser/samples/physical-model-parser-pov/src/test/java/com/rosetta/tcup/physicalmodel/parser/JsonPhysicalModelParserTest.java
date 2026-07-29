package com.rosetta.tcup.physicalmodel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonPhysicalModelParserTest {
    private final JsonPhysicalModelParser parser = new JsonPhysicalModelParser(new ObjectMapper());

    @Test
    void extractsNestedArrayLeavesInDiscoveryOrder() {
        var result = parser.parse("""
                {"user":{"name":"Ava","emails":["a@x.com"]},
                 "items":[{"id":1,"price":10},{"id":2,"name":"third"}]}""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("user.name", "user.emails[]", "items[].id", "items[].price", "items[].name");
    }

    @Test
    void keepsNullButNotAnEmptyObjectAndEmitsEmptyArrays() {
        var result = parser.parse("{" +
                "\"employeeId\":null,\"data\":{},\"owners\":[],\"name\":\"Dexter\"}");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("employeeId", "owners[]", "name");
    }

    @Test
    void supportsRootArraysWithoutAddingAVirtualRoot() {
        var result = parser.parse("[{\"id\":1,\"name\":\"A\"},{\"id\":2}]");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("[].id", "[].name");
    }
}
