# TCUP Physical Model Parser POV — Phase 1 Requirements

## 1. Purpose

This POV belongs to the Rosetta project's TCUP module. It validates that TCUP can extract Dataset physical-model attributes from four structured input formats:

- JSON instance
- XML instance
- JSON Schema
- XML Schema (XSD)

The output is a unique, ordered collection of flattened leaf attribute paths. Each path represents one business-facing Physical Model attribute.

This is semantic model extraction, not generic document flattening. Schema syntax such as `properties`, `items`, `complexType`, and `sequence` must never appear in an output path.

## 2. Phase 1 user journey

The POV provides one simple web page:

1. The user selects one of the four source types.
2. The user pastes the source content.
3. The user clicks **Run**.
4. The page displays:
   - the number of Physical Model attributes;
   - one row per attribute path;
   - any parsing errors or non-fatal warnings.

No upload, persistence, authentication, history, editing, or export is required.

## 3. Output model

The minimum Phase 1 domain model is:

```text
PhysicalAttribute
└── path
```

Example paths:

```text
user.name
user.emails[]
items[].id
appConfig.owners.owner[]
```

The parse result contains:

```text
sourceType
attribute count
unique ordered attributes
warnings
```

Actual values, declared or inferred data types, required/nullable flags, constraints, and other metadata are outside Phase 1.

## 4. Confirmed common rules

1. Output leaf attributes only.
2. Join parent and child names with `.`.
3. Append `[]` directly to an array or repeatable node name.
4. Do not add a separator before `[]`.
5. Do not output structural/container nodes by themselves.
6. Do not output an empty object when it provides no determinable leaf attribute.
7. A JSON `null` value is still a determinable leaf attribute; its type is simply unknown.
8. Preserve source names and source hierarchy exactly; do not normalize snake_case, camelCase, wrapper nodes, or equivalent cross-format structures.
9. Return each physical attribute path once, even when multiple instance records exhibit it.
10. Use the complete attribute path—not the final field name—as the unique identity.
11. Preserve discovery/input order in the POV unless a later product decision changes this rule.
12. Schema metadata and constraints are not output, but structural information that affects paths must be interpreted.

## 5. JSON instance rules

- Do not add a virtual root.
- Recurse through object properties.
- A scalar or `null` is a leaf.
- An object is a container unless it has no determinable children.
- An array of simple values is represented by the array path itself:

```json
{
  "user": {
    "emails": ["a@x.com", "b@x.com"]
  }
}
```

```text
user.emails[]
```

- An array of objects appends `[]` to the array property before child paths:

```json
{
  "items": [
    {"id": 1, "price": 10},
    {"id": 2, "price": 20}
  ]
}
```

```text
items[].id
items[].price
```

- Repeated paths discovered from multiple array entries are merged.
- Empty-array behavior and root-array notation remain pending decisions.

## 6. XML instance rules

- Preserve the real XML root element.
- Recurse through child elements.
- An element with no child element is a leaf, including `<name/>` and `<name></name>`.
- Repeated same-name sibling elements are represented by `[]` on the repeated element:

```xml
<phones>
    <phone>1</phone>
    <phone>2</phone>
</phones>
```

```text
phones.phone[]
```

- An XML instance can only infer repeatability when repetition is visible in that instance. Therefore, a single `<owner>` may produce `owner`, while an XSD with `maxOccurs="unbounded"` produces `owner[]`.
- XML attribute notation and namespace handling remain pending decisions.
- XML parsing must treat pasted content as untrusted and disable external entities, external DTD access, and external schema access.

## 7. JSON Schema rules

- Extract the business model described by the schema; do not flatten the schema document itself.
- Walk `properties` as business property names.
- Interpret `type: object` and nested `properties` as containers.
- Interpret `type: array` and `items` as array structure.
- A simple `items` schema produces a leaf array path:

```json
{
  "type": "object",
  "properties": {
    "owners": {
      "type": "array",
      "items": {"type": "string"}
    }
  }
}
```

```text
owners[]
```

- An object `items` schema produces paths such as `employees[].id`.
- Ignore `$schema`, `$id`, `title`, `required`, `additionalProperties`, enumerations, ranges, lengths, defaults, and other constraints in the output.
- Support inline definitions.
- Support same-document local `$ref` values beginning with `#`, including common `$defs` and `definitions` locations.
- Resolve local references logically while walking; no expanded schema file needs to be generated.
- Detect reference cycles and stop the recursive branch with a warning.
- Report unresolved references as warnings while returning attributes extracted from other valid branches.
- External files and remote URL references are out of scope.
- `oneOf`, `anyOf`, and `allOf` behavior remains outside the confirmed Phase 1 scope.

## 8. XSD rules

- Extract business elements described by the XSD; do not output XSD vocabulary such as `schema`, `complexType`, `sequence`, `restriction`, or `enumeration`.
- Preserve each top-level business element as a root path component.
- Support inline `complexType` and `simpleType`.
- Support same-file named `complexType` and named `simpleType`.
- Support same-file global element `ref`.
- A simple built-in or named simple type is a leaf element.
- A complex type is a container and is traversed for leaf elements.
- Treat an element as repeatable when `maxOccurs` is greater than `1` or equals `unbounded`; append `[]` to that element name.
- `minOccurs` does not change the Phase 1 output because required/optional status is not returned.
- Occurrence rules on the reference usage site affect the generated path.
- Resolve same-file named types and element references logically.
- Detect reference cycles and stop the recursive branch with a warning.
- Report unresolved named types or element references as warnings while returning valid attributes from other branches.
- `xs:include`, `xs:import`, external schema files, remote schemas, and cross-file namespace resolution are out of scope.
- `xs:choice` behavior remains outside the confirmed Phase 1 scope.
- XSD parsing must not load external resources.

## 9. Representative outputs

### JSON

```json
{
  "user": {
    "name": "Ava",
    "emails": ["a@x.com", "b@x.com"]
  },
  "items": [
    {"id": 1, "price": 10},
    {"id": 2, "price": 20}
  ]
}
```

```text
user.name
user.emails[]
items[].id
items[].price
```

### JSON Schema

```text
app_name
environment
feature_toggle.local_run
retry_count
owners[]
```

### XML

```text
appConfig.appName
appConfig.environment
appConfig.featureToggle.localRun
appConfig.retryCount
appConfig.owners.owner
```

### XSD

When `owner` has `maxOccurs="unbounded"`:

```text
appConfig.appName
appConfig.environment
appConfig.featureToggle.localRun
appConfig.retryCount
appConfig.owners.owner[]
```

The difference between the XML and XSD owner paths is valid when the XML instance contains only one owner and therefore does not demonstrate repeatability.

## 10. Explicitly excluded from Phase 1

- Cross-format naming or hierarchy normalization
- Values, types, required flags, constraints, and other attribute metadata
- Schema validation of an instance
- External or remote JSON Schema references
- XSD `include` and `import`
- Cross-file namespace resolution
- Database persistence
- File upload and export
- Authentication and authorization
- History, pagination, and attribute editing
- Frontend frameworks
- Automatic source-type detection

## 11. Definition of done

The POV is complete when:

1. One page accepts each of the four source types and displays the extracted count and paths.
2. The confirmed examples in `acceptance-cases.md` pass automated tests.
3. Inline schema definitions work.
4. Same-document JSON Schema `$ref` works.
5. Same-file XSD named types and element refs work.
6. Duplicate paths are removed without losing discovery order.
7. Invalid input returns a clear error.
8. Unresolved and recursive internal references do not hang or discard unrelated valid results.
9. XML/XSD parsing cannot fetch external resources.

