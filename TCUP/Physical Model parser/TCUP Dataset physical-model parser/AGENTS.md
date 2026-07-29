# TCUP Physical Model Parser

## Project context

This module belongs to the Rosetta project and supports Dataset
physical-model attribute extraction.

## Phase 1 inputs

- JSON
- XML
- JSON Schema
- XML Schema / XSD

## Core output

Return unique leaf attribute paths.

Examples:

- user.name
- user.emails[]
- items[].id
- appConfig.owners.owner[]

## Confirmed rules

- Output leaf attributes only.
- Use "." as the path separator.
- Use "[]" for arrays and repeatable elements.
- Preserve the XML/XSD root element.
- Do not introduce a virtual JSON root.
- Do not normalize JSON and XML naming or structures.
- Do not output values, data types, or constraints in Phase 1.
- Support inline Schema definitions.
- Support same-document references:
  - JSON Schema local `$ref`
  - XSD named type and element ref
- Do not support external references in Phase 1.

## Development principles

- Java implementation.
- Reuse the Rosetta/TCUP Spring Boot and dependency versions.
- Follow MVP scope.
- Keep parsers extensible through a common interface.
- Do not add a frontend framework.
- Do not add database persistence.