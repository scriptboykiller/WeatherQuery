# Build and Packaging Reference

Reuse the existing Spring Boot Maven configuration.

Typical build:

```bat
mvnw.cmd clean test package
```

or:

```bat
mvn clean test package
```

Confirm the built JAR is executable:

```bat
java -jar target\sql-postgres-validator-<version>.jar
```

Package the release:

```bat
build-reference\package-release.bat ^
  target\sql-postgres-validator-1.0.0.jar ^
  1.0.0 ^
  release-template ^
  build\release ^
  output\sql-select-baseline.csv
```

Omit the final Baseline argument when no approved Baseline should be delivered.

The packaging script intentionally does not include a nonexistent performance
comparator JAR.
