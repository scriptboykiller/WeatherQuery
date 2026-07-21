# Troubleshooting

## Java not found

Confirm JDK 17:

```bat
java -version
```

## Validator JAR missing

The release root must contain:

```text
sql-postgres-validator-<version>.jar
```

## H2 connection failed

Check host, port, database path, credentials, VPN/network access and URL
parameters. An empty H2 schema is valid.

## PostgreSQL connection failed

Check host, database, credentials, SSL/URL parameters and schema/search_path.

## Baseline already exists

Capture mode protects it. Back up the approved file and use a deliberate new
path or remove only a known temporary Baseline.

## Compare cannot find baselineKey

Regenerate current Inventory/Binding Plan/Execution Report and review stable
source metadata. Do not automatically fall back to SQL text or line number.

## Excel has no performance link

This is normal when the optional performance phase was not run.

## Comparator JAR missing

Expected path:

```text
tools\sql-performance-comparator.jar
```

Core validation and Excel remain available.

## SUCCESS result but HTML missing

Treat it as `REPORT_MISSING`. The file declared by `htmlReport` must exist.
