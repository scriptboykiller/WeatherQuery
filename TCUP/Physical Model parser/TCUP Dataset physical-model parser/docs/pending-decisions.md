# TCUP Physical Model Parser POV — Pending Decisions

This file contains unresolved product decisions only. Codex and implementers must not silently convert a recommendation into a confirmed requirement. Decisions should be recorded here and then reflected in `phase-1-requirements.md` and `acceptance-cases.md`.

## Decision summary

| ID | Decision | Recommended default | Phase 1 impact |
|---|---|---|---|
| D1 | XML attribute notation | `employee.@id` | Medium |
| D2 | JSON root-array notation | `[].id` | Medium |
| D3 | Empty JSON array | Emit nothing until shape is known | Medium |
| D4 | XML single occurrence versus array | Infer only visible repetition | Medium |
| D5 | Output order | Preserve discovery/input order | Low |
| D6 | XML namespace in path | Use local names in Phase 1 | Medium |
| D7 | `oneOf`/`anyOf`/`allOf` | Explicitly unsupported in Phase 1 | Scope |
| D8 | `xs:choice` | Explicitly unsupported in Phase 1 | Scope |
| D9 | Dynamic JSON Schema properties | Explicitly unsupported in Phase 1 | Scope |
| D10 | Recursive reference representation | Stop branch and warn | Medium |

Same-document JSON Schema `$ref` and same-file XSD named type/ref support are confirmed Phase 1 requirements and are not pending.

## D1 — XML attributes

Example:

```xml
<employee id="1001">
    <id>internal-id</id>
</employee>
```

Option A:

```text
employee.@id
employee.id
```

Option B:

```text
employee.id
employee.id
```

Option B creates a collision between the XML attribute and child element.

Recommended decision:

> Include XML attributes as leaf Physical Model attributes and prefix their path component with `@`.

If XML attributes are excluded entirely, that must be stated explicitly.

## D2 — JSON top-level array

Example:

```json
[
  {"id": 1, "name": "A"},
  {"id": 2, "name": "B"}
]
```

Candidate A:

```text
[].id
[].name
```

Candidate B:

```text
$[].id
$[].name
```

Candidate C: reject root arrays.

Recommended decision:

> Support root arrays with `[].id` because JSON otherwise has no added virtual root.

The choice must be aligned with downstream TCUP path parsing.

## D3 — Empty JSON array

Example:

```json
{"owners": []}
```

Option A:

```text
owners[]
```

This records the known property and array nature but cannot say whether the items are simple or objects.

Option B: no output.

This follows the rule that an instance must provide a determinable leaf shape.

Recommended decision:

> Emit no Physical Attribute for an empty instance array. A JSON Schema with simple `items` still emits `owners[]`.

If downstream consumers benefit from knowing the array property even without its element shape, choose Option A.

## D4 — XML single element and repeatability

Instance:

```xml
<owners>
    <owner>team-a</owner>
</owners>
```

XSD:

```xml
<xs:element name="owner"
            type="xs:string"
            maxOccurs="unbounded"/>
```

The XML instance visibly supports only:

```text
owners.owner
```

The XSD supports:

```text
owners.owner[]
```

Recommended decision:

> Parse each input independently. XML instance repetition is inferred only from repeated same-name siblings; XSD repetition comes from `maxOccurs`. Do not combine instance and schema unless a future feature explicitly accepts both together.

## D5 — Attribute order

Example:

```json
{"b": 1, "a": 2}
```

Candidate A:

```text
b
a
```

Candidate B:

```text
a
b
```

Recommended decision:

> Preserve discovery/input order while deduplicating paths.

This is easier to inspect and debug, and it matches document traversal.

## D6 — XML namespaces

Example:

```xml
<cfg:appConfig xmlns:cfg="urn:company:config">
    <cfg:appName>tcup</cfg:appName>
</cfg:appConfig>
```

Candidate A — local names:

```text
appConfig.appName
```

Candidate B — prefixes:

```text
cfg:appConfig.cfg:appName
```

Candidate C — namespace URIs:

```text
{urn:company:config}appConfig.{urn:company:config}appName
```

Prefixes are aliases and may change while the namespace URI remains the same. URI-qualified names are correct but verbose and may not fit the existing TCUP path format.

Recommended Phase 1 decision:

> Use local names only and document the possibility of collisions between namespaces. Do not implement cross-file namespace resolution.

## D7 — JSON Schema composition

Examples:

```json
{"oneOf": [ ... ]}
```

```json
{"anyOf": [ ... ]}
```

```json
{"allOf": [ ... ]}
```

These keywords require merge/union/alternative semantics and can create conditional path sets.

Recommended decision:

> Explicitly reject or warn that `oneOf`, `anyOf`, and `allOf` are unsupported in Phase 1.

Do not silently ignore them because that can produce incomplete results that look successful.

## D8 — XSD `choice`

Example:

```xml
<xs:choice>
    <xs:element name="email" type="xs:string"/>
    <xs:element name="phone" type="xs:string"/>
</xs:choice>
```

Potential output could include both possible paths, but the result would no longer say that only one may occur.

Recommended decision:

> Treat `xs:choice` as unsupported in Phase 1 and return a warning or clear error.

## D9 — Dynamic JSON Schema properties

Examples:

```json
{
  "type": "object",
  "additionalProperties": {"type": "string"}
}
```

```json
{
  "type": "object",
  "patternProperties": {
    "^S_": {"type": "string"}
  }
}
```

No fixed attribute name can be extracted.

Recommended decision:

> Do not emit a synthetic path. Warn that dynamic properties are unsupported in Phase 1.

## D10 — Recursive reference output

Example:

```text
Node
├── name
└── child -> Node
```

An unlimited flattened model is impossible:

```text
node.child.child.child...
```

Recommended decision:

> Emit non-recursive leaves reached before the cycle, stop the recursive branch, and return a warning identifying the reference chain.

Do not use an arbitrary expansion depth unless the product later defines one.

## Confirmed exclusions that do not need another decision for the POV

- No cross-format normalization.
- No values, types, required flags, or constraints in output.
- No external or remote JSON Schema references.
- No `xs:include` or `xs:import`.
- No cross-file namespace resolution.
- No persistence, upload, history, export, authentication, or frontend framework.

