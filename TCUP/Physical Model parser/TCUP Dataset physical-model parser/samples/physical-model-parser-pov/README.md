# TCUP Physical Model Parser POV

Standalone Java 17 / Spring Boot sample implementing the confirmed Phase 1 path-extraction rules.

## Run

Maven is required. When dependencies are not already cached, use the configured local proxy:

```bash
export https_proxy=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7890
mvn spring-boot:run
```

Open `http://localhost:8080/` and paste JSON, JSON Schema, XML, or XSD.

## Scope decisions implemented

- XML attributes are emitted with `@`, such as `employee.@id`.
- A root JSON array uses `[]`, such as `[].id`.
- An empty JSON array is emitted as a leaf, such as `owners[]`.
- XML repeatability is inferred only from visible same-name siblings.
- Paths are unique and retain discovery order.
- XML/XSD paths use local names.
- JSON Schema composition/dynamic properties and XSD `choice` return warnings rather than inferred paths.
- Recursive schema/XSD references stop the recursive branch and return a warning.

The XSD parser intentionally uses secure JDK DOM and a small same-file index rather than a new library. It supports the documented Phase 1 subset: inline types, named simple/complex types, global element references, sequences/all groups, and `maxOccurs`.
