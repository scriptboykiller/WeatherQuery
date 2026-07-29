package com.rosetta.tcup.physicalmodel.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosetta.tcup.physicalmodel.parser.JsonPhysicalModelParser;
import com.rosetta.tcup.physicalmodel.parser.JsonSchemaPhysicalModelParser;
import com.rosetta.tcup.physicalmodel.parser.PhysicalModelParser;
import com.rosetta.tcup.physicalmodel.parser.XmlPhysicalModelParser;
import com.rosetta.tcup.physicalmodel.parser.XsdPhysicalModelParser;
import com.rosetta.tcup.physicalmodel.service.PhysicalModelExtractionService;
import com.rosetta.tcup.physicalmodel.service.PhysicalModelParserRegistry;
import com.rosetta.tcup.physicalmodel.support.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhysicalModelControllerTest {
    private final MockMvc mockMvc = createMockMvc();

    @Test
    void returnsTheApiResultShape() throws Exception {
        mockMvc.perform(post("/api/physical-models/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"JSON\",\"content\":\"{\\\"b\\\":1,\\\"a\\\":2}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.attributes[0].path").value("b"))
                .andExpect(jsonPath("$.attributes[1].path").value("a"));
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/physical-models/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceType\":\"JSON\",\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockMvc createMockMvc() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<PhysicalModelParser> parsers = List.of(
                new JsonPhysicalModelParser(objectMapper),
                new JsonSchemaPhysicalModelParser(objectMapper),
                new XmlPhysicalModelParser(),
                new XsdPhysicalModelParser());
        PhysicalModelExtractionService service = new PhysicalModelExtractionService(
                new PhysicalModelParserRegistry(parsers), 1_048_576);
        return MockMvcBuilders.standaloneSetup(new PhysicalModelController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
