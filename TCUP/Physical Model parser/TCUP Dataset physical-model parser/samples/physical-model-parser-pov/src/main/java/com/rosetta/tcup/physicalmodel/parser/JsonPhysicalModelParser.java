package com.rosetta.tcup.physicalmodel.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonPhysicalModelParser implements PhysicalModelParser {
    private final ObjectMapper objectMapper;

    public JsonPhysicalModelParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.JSON;
    }

    @Override
    public ParseResult parse(String content) {
        try {
            ParseCollector collector = new ParseCollector();
            walk(objectMapper.readTree(content), "", collector);
            return new ParseResult(supportedType(), collector.attributes(), collector.warnings());
        } catch (JsonProcessingException exception) {
            throw new InvalidPhysicalModelInputException("Invalid JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    private void walk(JsonNode node, String path, ParseCollector collector) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                walk(field.getValue(), join(path, field.getKey()), collector);
            }
            return;
        }
        if (node.isArray()) {
            String arrayPath = path + "[]";
            if (node.isEmpty()) {
                collector.addPath(arrayPath);
                return;
            }
            for (JsonNode item : node) {
                walk(item, arrayPath, collector);
            }
            return;
        }
        collector.addPath(path);
    }

    private String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }
}
