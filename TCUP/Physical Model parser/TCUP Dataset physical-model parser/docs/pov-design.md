# TCUP Physical Model Parser POV — Technical Design

## 1. Design goals

The POV should be small enough to validate quickly and structured enough to evolve into the TCUP Dataset attribute pipeline without a rewrite.

Primary goals:

- Java implementation compatible with the existing Rosetta/TCUP build and Spring Boot conventions.
- One REST endpoint and one static page.
- Four format-specific parsers behind one interface.
- No database or frontend framework.
- Safe handling of pasted, untrusted XML and XSD.
- Automated tests tied directly to the acceptance cases.

Before implementation, inspect the actual Rosetta/TCUP repository and reuse its Java version, Spring Boot version, dependency management, package naming, exception style, testing conventions, and static-resource approach.

## 2. Proposed architecture

```text
Single static page
        |
        | POST /api/physical-models/parse
        v
PhysicalModelController
        |
        v
PhysicalModelExtractionService
        |
        v
PhysicalModelParserRegistry
        |
        +-- JsonPhysicalModelParser
        +-- JsonSchemaPhysicalModelParser
        +-- XmlPhysicalModelParser
        `-- XsdPhysicalModelParser
```

The controller validates transport-level input and delegates. It must not contain parsing rules.

## 3. Minimal domain model

```java
public enum SourceType {
    JSON,
    JSON_SCHEMA,
    XML,
    XSD
}
```

```java
public record PhysicalAttribute(String path) {
}
```

```java
public record ParseResult(
        SourceType sourceType,
        List<PhysicalAttribute> attributes,
        List<String> warnings
) {
    public int count() {
        return attributes.size();
    }
}
```

```java
public interface PhysicalModelParser {
    SourceType supportedType();
    ParseResult parse(String content);
}
```

Do not add speculative Phase 2 fields to `PhysicalAttribute`. A later version can evolve the domain model when type, required, array, or constraint metadata is genuinely required.

## 4. Parser registry and service

Spring can discover parser implementations and build a registry keyed by `SourceType`.

The extraction service:

1. Locates the parser.
2. Calls it with the source text.
3. Returns the parser result.
4. Fails clearly when the source type is unsupported or content is invalid.

Attribute paths should be accumulated in an insertion-ordered set so duplicate observations are removed while discovery order is preserved.

## 5. JSON instance parser

Use Jackson's tree model.

Traversal rules:

- Object node: traverse fields in document order.
- Scalar or null node: emit the current path.
- Array node:
  - append `[]` to the array property;
  - scalar elements make the array path a leaf;
  - object elements are traversed under the array path;
  - merge fields found across all object elements;
  - nested arrays recurse using the same notation;
  - empty-array behavior follows the decision documented in `pending-decisions.md`.
- Empty object: emit nothing.

Generic JSON flatten libraries are not suitable because they usually produce value-oriented indexed paths such as `items[0].id`.

## 6. JSON Schema parser

Use Jackson's tree model and JSON Pointer navigation.

The parser holds:

- the document root, used for local reference resolution;
- the current business path;
- a recursion-chain set for cycle detection;
- an ordered set of output paths;
- warnings.

Core traversal:

1. Resolve `$ref` when present.
2. Determine whether the effective schema describes an object, array, or leaf.
3. For an object, traverse property names under `properties`.
4. For an array, append `[]` and traverse `items`; emit the array path when items are simple.
5. For a simple schema, emit the current business path.

Supported references:

```text
#/$defs/Employee
#/definitions/Employee
other same-document JSON Pointer fragments beginning with #
```

Reference resolution must:

- correctly decode JSON Pointer escape sequences;
- reject non-local references in Phase 1;
- warn on unresolved targets;
- detect a repeated reference in the active recursion chain;
- stop only the affected branch.

The parser must not return schema-language paths such as `properties.employee.$ref`.

## 7. XML instance parser

Use secure JAXP DOM for the small pasted POV input.

Security configuration must:

- enable secure processing;
- disallow DOCTYPE declarations where supported;
- disable external general and parameter entities;
- set external DTD and schema access to empty;
- avoid XInclude;
- avoid entity expansion;
- impose an application-level maximum input size;
- avoid unbounded application recursion.

Traversal:

1. Start with the document element and include its local name in the path.
2. Group direct child elements by effective name.
3. A repeated same-name sibling receives `[]`.
4. Recurse into element children.
5. An element without child elements is a leaf, even when empty.
6. XML attribute and namespace behavior follows pending decisions.

DOM is appropriate for the POV because content is pasted and bounded. A future large-file requirement may justify StAX, without changing the common parser interface.

## 8. XSD parser

Preferred approach: use Apache XmlSchema if it integrates cleanly with the existing TCUP dependency set and supports the required object-model traversal. Confirm the exact API against the version selected by the project.

Required model operations:

- enumerate top-level business elements;
- distinguish simple and complex element types;
- inspect inline content;
- resolve same-file named simple and complex types;
- resolve same-file global element refs;
- inspect occurrence limits;
- walk sequence children;
- retain QName/local-name information for the namespace decision.

The walker must keep the business element path separate from the XSD object-model structure.

Reference/type traversal requires an active recursion-chain set. On a cycle or unresolved target, issue a warning and stop only that branch.

If Apache XmlSchema cannot be introduced due to the parent project's dependency policy, the fallback is secure DOM plus small, explicit same-file indexes for global elements, named complex types, and named simple types. That fallback should be chosen only after inspecting the real project.

## 9. REST API

### Request

```http
POST /api/physical-models/parse
Content-Type: application/json
```

```json
{
  "sourceType": "JSON",
  "content": "{ \"user\": { \"name\": \"Ava\" } }"
}
```

### Success

```json
{
  "sourceType": "JSON",
  "count": 1,
  "attributes": [
    {"path": "user.name"}
  ],
  "warnings": []
}
```

### Partial success with warning

```json
{
  "sourceType": "JSON_SCHEMA",
  "count": 1,
  "attributes": [
    {"path": "code"}
  ],
  "warnings": [
    "Unresolved same-document reference: #/$defs/Missing"
  ]
}
```

### Invalid input

Use the TCUP project's established error envelope. The error must at least communicate:

```json
{
  "code": "INVALID_INPUT",
  "message": "Invalid JSON near line 3, column 8"
}
```

Do not return stack traces or parser internals to the page.

## 10. Single-page UI

Use static HTML, CSS, and JavaScript served by the existing Spring Boot application.

The page contains only:

- title and one-sentence purpose;
- source type selector;
- large content textarea;
- **Run** button;
- **Clear** button;
- loading/disabled state;
- error and warning area;
- result heading showing the count;
- simple table with row number and attribute path.

Recommended layout:

```text
+--------------------------------------------------+
| TCUP Physical Model Parser                       |
| Extract leaf attribute paths from pasted input.  |
+--------------------------------------------------+
| Source type [JSON v]                             |
|                                                  |
| [ Paste JSON / XML / Schema here              ] |
| [                                               ] |
|                                                  |
| [Run] [Clear]                                    |
+--------------------------------------------------+
| Physical Models: 4                               |
| 1  user.name                                     |
| 2  user.emails[]                                 |
| 3  items[].id                                    |
| 4  items[].price                                 |
+--------------------------------------------------+
```

The UI must not implement parsing logic. It sends content and displays the API result.

## 11. Validation and operational boundaries

- Reject blank content.
- Reject unknown source types.
- Set a configurable maximum content length.
- Use bounded recursion or depth checks to prevent stack exhaustion.
- Never fetch external resources.
- Treat unresolved internal references as warnings when partial output remains useful.
- Treat malformed JSON, XML, JSON Schema, or XSD as invalid input.
- Log detailed server-side causes according to TCUP standards, but return safe messages.

## 12. Test strategy

### Unit tests

One focused test class per parser:

- scalar and nested leaves;
- arrays and repeated elements;
- deduplication and order;
- empty and null cases;
- inline schema definitions;
- same-document references;
- named XSD types and element refs;
- repeatability;
- cycles and unresolved refs;
- malformed input;
- XXE/external-resource denial.

### Service tests

- parser selection;
- unsupported type;
- result count;
- warning preservation.

### API tests

- successful request and response shape;
- invalid request;
- partial success with warnings.

### UI smoke test

- select a type;
- paste an example;
- run;
- verify the count and rows;
- clear the page.

Every confirmed case is listed in `acceptance-cases.md`.

## 13. Suggested implementation sequence

1. Inspect Rosetta/TCUP conventions and dependency management.
2. Add domain types, parser interface, registry, and service.
3. Implement and test JSON.
4. Implement and test JSON Schema with same-document `$ref`.
5. Implement and test secure XML.
6. Integrate the selected XSD object model and test inline/named/ref cases.
7. Add API and error handling.
8. Add the static single-page UI.
9. Run the full acceptance suite and security checks.

## 14. Extension points without Phase 1 overbuilding

The common parser interface permits later formats such as YAML, Avro, or Protobuf. The result model can later gain metadata. Neither possibility justifies implementing unused abstractions now.

The main extensibility boundary is:

```text
source-specific syntax/model traversal
                ↓
ordered PhysicalAttribute results + warnings
```

