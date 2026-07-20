# Dependency Notes

Reuse dependencies already present in the project where possible.

Expected libraries:

```xml
<!-- JSON -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>

<!-- CSV; likely already used by prior batches -->
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-csv</artifactId>
</dependency>

<!-- Excel; already used by the Excel phase -->
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
</dependency>
```

Do not add duplicate versions when Spring Boot dependency management already
controls them.
