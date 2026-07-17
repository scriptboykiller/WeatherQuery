# Binding-plan reader changes required for Batch 3

Batch 2 only needed an aggregated binding count. Batch 3 needs one binding row
per JDBC position.

Extend the existing binding-plan reader/model so it can return:

```java
Map<String, List<ParameterSamplingModels.BindingParameter>> bindingsBySqlId
```

For each binding-plan CSV row map these concepts from the **actual current
headers written by this project**:

```text
sqlId
jdbcIndex
logicalParameterIndex or equivalent
parameterName
javaType
collection flag
```

Reference mapping logic:

```java
int jdbcIndex = integer(record,
        "jdbcIndex", "bindingIndex", "parameterIndex");

int logicalIndex = integerOrDefault(record, jdbcIndex,
        "logicalParameterIndex", "logicalIndex",
        "jpaParameterIndex", "sourceParameterIndex");

String parameterName = first(record,
        "parameterName", "logicalParameterName",
        "sourceParameterName");

String javaType = first(record,
        "javaType", "parameterJavaType", "resolvedJavaType");

boolean collection = bool(record, false,
        "collection", "isCollection", "collectionParameter");
```

Do not blindly keep all aliases. Copilot must inspect the real binding-plan CSV
writer and use the exact headers wherever possible.

Repeated named/JPA indexed parameters must share the same logical index/name,
while retaining every JDBC index.
