package org.rosetta.sqlvalidator.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerformanceToolResultTest {

    @Test
    void readsMinimalV2ResultContract() throws Exception {
        String json = """
                {
                  "responseVersion": "v2",
                  "caseId": "SQL-001",
                  "status": "SUCCESS",
                  "htmlReport": "index.html",
                  "errorCode": "",
                  "errorMessage": ""
                }
                """;

        PerformanceToolResult result = new ObjectMapper()
                .readValue(json, PerformanceToolResult.class);

        assertEquals("v2", result.responseVersion());
        assertEquals("SQL-001", result.caseId());
        assertEquals(PerformanceToolStatus.SUCCESS, result.status());
        assertEquals("index.html", result.htmlReport());
    }
}
