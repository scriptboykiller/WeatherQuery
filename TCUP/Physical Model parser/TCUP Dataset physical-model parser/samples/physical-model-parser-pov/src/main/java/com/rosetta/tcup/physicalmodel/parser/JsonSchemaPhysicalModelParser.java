package com.rosetta.tcup.physicalmodel.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaPhysicalModelParser implements PhysicalModelParser {
    private final ObjectMapper objectMapper;

    public JsonSchemaPhysicalModelParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.JSON_SCHEMA;
    }

    @Override
    public ParseResult parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            if (!root.isObject()) {
                throw new InvalidPhysicalModelInputException("A JSON Schema document must be a JSON object");
            }
            ParseCollector collector = new ParseCollector();
            walk(root, root, "", new LinkedHashSet<>(), collector);
            return new ParseResult(supportedType(), collector.attributes(), collector.warnings());
        } catch (JsonProcessingException exception) {
            throw new InvalidPhysicalModelInputException("Invalid JSON Schema: " + exception.getOriginalMessage(), exception);
        }
    }

    private void walk(JsonNode schema, JsonNode root, String path, Set<String> activeReferences, ParseCollector collector) {
        if (!schema.isObject()) {
            collector.addPath(path);
            return;
        }
        warnUnsupportedKeywords(schema, collector);
        JsonNode reference = schema.get("$ref");
        if (reference != null && reference.isTextual()) {
            String ref = reference.textValue();
            if (!ref.startsWith("#")) {
                collector.warn("External JSON Schema reference is unsupported: " + ref);
                return;
            }
            if (!activeReferences.add(ref)) {
                collector.warn("Recursive JSON Schema reference stopped: " + ref);
                return;
            }
            JsonNode target = root.at(JsonPointer.compile(ref.substring(1)));
            if (target.isMissingNode()) {
                collector.warn("Unresolved same-document reference: " + ref);
            } else {
                walk(target, root, path, activeReferences, collector);
            }
            activeReferences.remove(ref);
            return;
        }

        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                walk(field.getValue(), root, join(path, field.getKey()), activeReferences, collector);
            }
            return;
        }

        boolean isArray = "array".equals(schema.path("type").asText()) || schema.has("items");
        if (isArray) {
            String arrayPath = path + "[]";
            JsonNode items = schema.get("items");
            if (items == null || items.isMissingNode()) {
                collector.warn("Array schema has no items definition: " + arrayPath);
            } else {
                walk(items, root, arrayPath, activeReferences, collector);
            }
            return;
        }

        if (!path.isEmpty()) {
            collector.addPath(path);
        }
    }

    private void warnUnsupportedKeywords(JsonNode schema, ParseCollector collector) {
        for (String keyword : new String[]{"oneOf", "anyOf", "allOf", "patternProperties"}) {
            if (schema.has(keyword)) {
                collector.warn("JSON Schema keyword is unsupported in Phase 1: " + keyword);
            }
        }
        JsonNode additionalProperties = schema.get("additionalProperties");
        if (additionalProperties != null && !additionalProperties.isBoolean()) {
            collector.warn("JSON Schema dynamic properties are unsupported in Phase 1: additionalProperties");
        }
    }

    private String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }
}
