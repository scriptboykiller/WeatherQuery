# TCUP Physical Model Parser POV — Open-Source Evaluation

## 1. Decision summary

Use mature libraries to parse source syntax and a small TCUP-owned walker to apply Physical Model path rules.

There is no need for a large, universal flattening framework. Generic flatteners are value-oriented and usually create indexed paths such as `items[0].id`, while TCUP needs model-oriented paths such as `items[].id`.

| Area | Phase 1 choice | Status |
|---|---|---|
| Java web/API | Existing TCUP Spring Boot stack | Reuse |
| JSON instance | Jackson tree model | Use |
| JSON Schema | Jackson tree model + JSON Pointer | Use |
| XML instance | JDK JAXP DOM with secure configuration | Use |
| XSD | Apache XmlSchema object model | Preferred; verify against TCUP |
| Frontend | Static HTML/CSS/JavaScript | Use |
| Tests | Existing JUnit 5/Spring test stack | Reuse |
| JSON Schema validator | NetworkNT or equivalent | Do not add in Phase 1 |
| Generic JSON flattener | Various | Do not use |
| Jackson XML mapping | Jackson XML module | Do not add in Phase 1 |
| React/Vue/Angular | Frontend frameworks | Do not add |
| Database | Any | Do not add |

Versions must come from the existing Rosetta/TCUP dependency management wherever possible. Do not hard-code independent versions before inspecting the real repository.

## 2. Jackson for JSON instances

### Why it fits

Jackson's `JsonNode` tree cleanly distinguishes:

- object nodes;
- array nodes;
- scalar value nodes;
- null nodes.

That is enough for a small recursive walker implementing TCUP rules:

```text
object       -> recurse
array        -> append []
scalar/null  -> emit leaf path
empty object -> emit nothing
```

Jackson preserves object field iteration order as parsed, which supports discovery-order output.

### Why a generic flattener does not fit

Typical flatteners aim to preserve individual values:

```text
items[0].id
items[1].id
```

TCUP is extracting a model:

```text
items[].id
```

TCUP must also merge fields observed across multiple object-array records and return one unique path. That business rule belongs in TCUP's own thin walker.

## 3. Jackson for JSON Schema

JSON Schema is JSON syntax, so Jackson remains the simplest representation.

The walker interprets schema semantics:

- `properties` identifies business fields;
- `items` identifies array element structure;
- `$ref` redirects traversal;
- metadata and constraints do not become output paths.

Jackson's JSON Pointer support can locate same-document fragment targets after converting the local `$ref` fragment to the appropriate pointer lookup.

### Why not add a full validator

A JSON Schema validator answers:

> Does this instance comply with this schema?

The POV asks:

> Which leaf Physical Model attribute paths does this schema describe?

A validator may later be appropriate if TCUP adds instance validation, but in Phase 1 it adds dependency and API complexity without producing the required output.

### Phase 1 supported subset

- inline `properties` and `items`;
- local `$ref` beginning with `#`;
- `$defs`;
- legacy `definitions`;
- cycle detection;
- unresolved-reference warnings.

External references and schema-composition semantics are intentionally excluded.

## 4. JAXP DOM for XML instances

### Why it fits

The POV accepts bounded pasted content and needs XML-native concepts:

- a real root element;
- child elements;
- repeated same-name sibling elements;
- empty leaf elements;
- future XML attributes and namespaces.

DOM makes these relationships easy to inspect and keeps XML semantics visible. It avoids converting XML into an artificial JSON shape that could obscure wrapper and repetition rules.

### Security requirements

JAXP must be configured for untrusted input:

- secure processing enabled;
- DOCTYPE disallowed where supported;
- external general entities disabled;
- external parameter entities disabled;
- external DTD access disabled;
- external schema access disabled;
- XInclude disabled;
- entity expansion disabled;
- application input and depth limits applied.

These are mandatory POV safeguards, not future enhancements.

### When to reconsider

If TCUP later parses very large uploaded XML documents, StAX may be a better streaming choice. The common parser interface allows that replacement without changing the API or UI.

## 5. Apache XmlSchema for XSD

### Why it is the preferred helper

XSD traversal becomes more than raw XML traversal as soon as it includes:

- inline and named complex types;
- named simple types;
- global element refs;
- QName-based type references;
- `maxOccurs`;
- particles such as sequences.

Apache XmlSchema provides a Java object model for these concepts and can reduce hand-written XSD vocabulary handling.

TCUP still owns the final business logic:

```text
XSD object model
      ↓
leaf Physical Attribute paths
```

### Required spike before commitment

Within the actual Rosetta/TCUP repository:

1. Confirm the compatible artifact and version.
2. Check license and internal dependency policy.
3. Verify the API for top-level elements, named types, refs, particles, and occurrence limits.
4. Confirm that external resolution can be disabled.
5. Implement one inline, one named-type, and one element-ref acceptance case.

### Fallback

If dependency policy or object-model behavior makes Apache XmlSchema unsuitable, use secure DOM and build only three same-file indexes:

```text
global element QName -> element definition
named complex type QName -> complex type definition
named simple type QName -> simple type definition
```

Then traverse only the confirmed Phase 1 XSD subset. Avoid building a general XSD engine.

## 6. Frontend choice

Use one static HTML page with small CSS and JavaScript files.

Reasons:

- the UI has one input, two buttons, and one results table;
- no client-side routing or complex state;
- Spring Boot can serve static resources directly;
- it integrates with the existing Java project;
- it minimizes build and dependency overhead.

React, Vue, Angular, and server-side templates do not add value for this POV.

## 7. Dependencies deliberately not selected

### Generic JSON/XML flatten libraries

They generally flatten instance values, not business models, and do not understand JSON Schema or XSD semantics.

### Jackson XML object mapping

Mapping arbitrary XML into Java/JSON-like objects can blur wrappers, repeated siblings, attributes, and namespaces. Direct XML traversal is clearer for the confirmed rules.

### JSON Schema validator

Validation is not part of Phase 1. Add only if a future requirement needs compliance checking.

### XMLBeans/JAXB code generation

The input XSD is dynamic pasted content. Generating Java classes is unnecessary and unsuitable for an interactive parser.

### Full frontend framework

The page is intentionally one-screen and stateless.

### Database

The POV does not persist input or results.

## 8. Build-versus-buy conclusion

Buy/reuse the difficult syntax handling:

```text
Jackson       -> JSON tree and local pointer navigation
JAXP          -> secure XML document model
Apache model  -> XSD concepts, if compatible
Spring Boot   -> API and static page hosting
```

Build only the TCUP-specific value:

```text
leaf selection
path construction
array/repetition notation
deduplication and ordering
reference-cycle behavior
warning behavior
```

This balance follows MVP principles and leaves a clean route into the larger TCUP Dataset workflow.

