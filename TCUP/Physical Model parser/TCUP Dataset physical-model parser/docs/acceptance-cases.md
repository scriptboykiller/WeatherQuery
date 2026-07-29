# TCUP Physical Model Parser POV — Acceptance Cases

## 1. Conventions

- Expected paths are ordered by discovery/input order.
- Each path appears once.
- `.` separates path components.
- `[]` is appended directly to the array or repeatable element.
- XML/XSD retain the real business root.
- JSON/JSON Schema do not receive a virtual root.
- Cases marked **Pending decision** must not be treated as final acceptance criteria until resolved.

## 2. JSON instance cases

### J1 — Nested object and arrays

Input:

```json
{
  "user": {
    "name": "Ava",
    "emails": [
      "a@x.com",
      "b@x.com"
    ]
  },
  "items": [
    {"id": 1, "price": 10},
    {"id": 2, "price": 20}
  ]
}
```

Expected:

```text
user.name
user.emails[]
items[].id
items[].price
```

Expected count: `4`

### J2 — Null remains a leaf; empty object does not

Input:

```json
{
  "employeeId": null,
  "data": {},
  "name": "Dexter"
}
```

Expected:

```text
employeeId
name
```

Expected count: `2`

### J3 — Merge heterogeneous object-array observations

Input:

```json
{
  "items": [
    {"id": 1},
    {"id": 2, "price": 20},
    {"id": 3, "name": "third"}
  ]
}
```

Expected:

```text
items[].id
items[].price
items[].name
```

Expected count: `3`

### J4 — Deduplicate paths

Input:

```json
{
  "items": [
    {"id": 1},
    {"id": 2},
    {"id": 3}
  ]
}
```

Expected:

```text
items[].id
```

Expected count: `1`

### J5 — Invalid JSON

Input:

```text
{"name":
```

Expected:

- request fails with `INVALID_INPUT` or the TCUP equivalent;
- message identifies invalid JSON without exposing a stack trace;
- no attribute result is presented as successful.

### J6 — Empty array

Input:

```json
{"owners": []}
```

Status: **Pending decision**.

Candidate outputs:

```text
owners[]
```

or no attributes because the array's leaf/object shape cannot be determined.

### J7 — Root array

Input:

```json
[
  {"id": 1, "name": "A"},
  {"id": 2, "name": "B"}
]
```

Status: **Pending decision**.

Candidate notation includes:

```text
[].id
[].name
```

or:

```text
$[].id
$[].name
```

## 3. JSON Schema cases

### JS1 — Inline object and simple array

Input:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "AppConfig",
  "type": "object",
  "properties": {
    "app_name": {"type": "string"},
    "environment": {
      "type": "string",
      "enum": ["local", "sit", "uat"]
    },
    "feature_toggle": {
      "type": "object",
      "properties": {
        "local_run": {"type": "boolean"}
      }
    },
    "retry_count": {
      "type": "integer",
      "minimum": 0
    },
    "owners": {
      "type": "array",
      "items": {"type": "string"}
    }
  },
  "required": ["app_name"]
}
```

Expected:

```text
app_name
environment
feature_toggle.local_run
retry_count
owners[]
```

Expected count: `5`

Schema metadata and constraints must not appear in paths.

### JS2 — Same-document `$defs` reference

Input:

```json
{
  "type": "object",
  "properties": {
    "employee": {
      "$ref": "#/$defs/Employee"
    }
  },
  "$defs": {
    "Employee": {
      "type": "object",
      "properties": {
        "id": {"type": "integer"},
        "name": {"type": "string"}
      }
    }
  }
}
```

Expected:

```text
employee.id
employee.name
```

Expected count: `2`

### JS3 — Legacy `definitions` reference

Input:

```json
{
  "type": "object",
  "properties": {
    "address": {
      "$ref": "#/definitions/Address"
    }
  },
  "definitions": {
    "Address": {
      "type": "object",
      "properties": {
        "city": {"type": "string"},
        "country": {"type": "string"}
      }
    }
  }
}
```

Expected:

```text
address.city
address.country
```

Expected count: `2`

### JS4 — Array of referenced objects

Input:

```json
{
  "type": "object",
  "properties": {
    "employees": {
      "type": "array",
      "items": {
        "$ref": "#/$defs/Employee"
      }
    }
  },
  "$defs": {
    "Employee": {
      "type": "object",
      "properties": {
        "id": {"type": "integer"},
        "name": {"type": "string"}
      }
    }
  }
}
```

Expected:

```text
employees[].id
employees[].name
```

Expected count: `2`

### JS5 — Unresolved reference returns partial success

Input:

```json
{
  "type": "object",
  "properties": {
    "code": {"type": "integer"},
    "employee": {"$ref": "#/$defs/Missing"}
  }
}
```

Expected:

```text
code
```

Expected count: `1`

Expected warning includes:

```text
#/$defs/Missing
```

### JS6 — Recursive reference terminates

Input:

```json
{
  "type": "object",
  "properties": {
    "node": {"$ref": "#/$defs/Node"}
  },
  "$defs": {
    "Node": {
      "type": "object",
      "properties": {
        "name": {"type": "string"},
        "child": {"$ref": "#/$defs/Node"}
      }
    }
  }
}
```

Expected minimum result:

```text
node.name
```

Expected behavior:

- parsing terminates;
- the recursive branch is stopped;
- a cycle warning is returned;
- no unbounded `child.child...` path is produced.

## 4. XML instance cases

### X1 — Preserve root and nested elements

Input:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<appConfig>
    <appName>dqma-cicd</appName>
    <environment>local</environment>
    <featureToggle>
        <localRun>true</localRun>
    </featureToggle>
    <retryCount>3</retryCount>
    <owners>
        <owner>dataops-team</owner>
    </owners>
</appConfig>
```

Expected:

```text
appConfig.appName
appConfig.environment
appConfig.featureToggle.localRun
appConfig.retryCount
appConfig.owners.owner
```

Expected count: `5`

### X2 — Repeated same-name sibling

Input:

```xml
<employees>
    <employee>
        <id>1001</id>
        <name>Dexter</name>
    </employee>
    <employee>
        <id>1002</id>
        <name>Jerry</name>
    </employee>
</employees>
```

Expected:

```text
employees.employee[].id
employees.employee[].name
```

Expected count: `2`

### X3 — Empty leaf elements

Input:

```xml
<employee>
    <name/>
    <department></department>
</employee>
```

Expected:

```text
employee.name
employee.department
```

Expected count: `2`

### X4 — XML attribute

Input:

```xml
<employee id="1001">
    <id>internal-id</id>
</employee>
```

Status: **Pending decision**.

Recommended candidate:

```text
employee.@id
employee.id
```

### X5 — External entity is blocked

Input contains a DOCTYPE and an external entity.

Expected:

- no file or network resource is read;
- parsing fails safely;
- no external content is returned;
- the server remains responsive.

## 5. XSD cases

### XS1 — Inline definitions and repeatable element

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="appConfig">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="appName" type="xs:string"/>
                <xs:element name="environment">
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            <xs:enumeration value="local"/>
                            <xs:enumeration value="sit"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:element>
                <xs:element name="featureToggle">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="localRun" type="xs:boolean"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                <xs:element name="retryCount" type="xs:integer"/>
                <xs:element name="owners">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="owner"
                                        type="xs:string"
                                        maxOccurs="unbounded"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
```

Expected:

```text
appConfig.appName
appConfig.environment
appConfig.featureToggle.localRun
appConfig.retryCount
appConfig.owners.owner[]
```

Expected count: `5`

### XS2 — Same-file named complex type

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:complexType name="EmployeeType">
        <xs:sequence>
            <xs:element name="id" type="xs:integer"/>
            <xs:element name="name" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:element name="employee" type="EmployeeType"/>
</xs:schema>
```

Expected:

```text
employee.id
employee.name
```

Expected count: `2`

### XS3 — Same-file named simple type

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:simpleType name="EnvironmentType">
        <xs:restriction base="xs:string">
            <xs:enumeration value="local"/>
            <xs:enumeration value="sit"/>
        </xs:restriction>
    </xs:simpleType>
    <xs:element name="environment" type="EnvironmentType"/>
</xs:schema>
```

Expected:

```text
environment
```

Expected count: `1`

### XS4 — Same-file global element ref and usage-site occurrence

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="owner" type="xs:string"/>
    <xs:element name="appConfig">
        <xs:complexType>
            <xs:sequence>
                <xs:element ref="owner" maxOccurs="unbounded"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
```

Expected:

```text
appConfig.owner[]
```

Expected count: `1`

### XS5 — Recursive named type terminates

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:complexType name="NodeType">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
            <xs:element name="child" type="NodeType" minOccurs="0"/>
        </xs:sequence>
    </xs:complexType>
    <xs:element name="node" type="NodeType"/>
</xs:schema>
```

Expected minimum result:

```text
node.name
```

Expected behavior:

- parsing terminates;
- the recursive branch is stopped;
- a cycle warning is returned.

### XS6 — Unresolved named type returns warning

Input:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="code" type="xs:integer"/>
    <xs:element name="employee" type="MissingType"/>
</xs:schema>
```

Expected valid result:

```text
code
```

Expected warning identifies:

```text
MissingType
```

### XS7 — External include/import is not loaded

Input contains:

```xml
<xs:include schemaLocation="common-types.xsd"/>
```

or:

```xml
<xs:import namespace="urn:common"
           schemaLocation="https://example.com/common.xsd"/>
```

Expected:

- no external file or URL is loaded;
- a clear unsupported/external-reference warning or error is returned;
- the server remains responsive.

## 6. Cross-cutting API cases

### A1 — Blank content

Expected: validation error; parser is not invoked.

### A2 — Unknown source type

Expected: validation error or unsupported-source error.

### A3 — Result count

For every successful response:

```text
count == attributes.size()
```

### A4 — Stable discovery order

Given:

```json
{"b": 1, "a": 2}
```

Expected:

```text
b
a
```

This follows the current recommended baseline and remains listed in pending decisions for explicit product confirmation.

### A5 — Input-size limit

Content above the configured maximum is rejected safely before expensive parsing.

