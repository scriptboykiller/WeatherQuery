# 09 Spring Boot CLI Refactor Code

## 1. Scope

This document contains the code skeleton for refactoring the existing `sql-postgres-validator` MVP into a standard JDK 17 Spring Boot CLI project.

This code focuses on:

- Spring Boot CLI entry point.
- Phase-based runner dispatch.
- `application.yml` configuration.
- SLF4J logging.
- Basic project exceptions.
- Wrapping existing Phase 1 and Phase 1.5 logic.

This document does not implement Phase 2 PostgreSQL validation.

---

## 2. Recommended Project Structure

```text
sql-postgres-validator
├── pom.xml
├── README.md
├── docs
│   ├── 08_SpringBoot_CLI_Refactor_Design.md
│   └── 09_SpringBoot_CLI_Refactor_Code.md
└── src
    └── main
        ├── java
        │   └── org
        │       └── rosetta
        │           └── sqlvalidator
        │               ├── SqlPostgresValidatorApplication.java
        │               ├── common
        │               │   ├── constant
        │               │   │   └── ValidatorConstants.java
        │               │   └── exception
        │               │       ├── SqlValidatorException.java
        │               │       └── PhaseExecutionException.java
        │               ├── config
        │               │   └── ValidatorProperties.java
        │               ├── runner
        │               │   ├── RunnerDispatcher.java
        │               │   ├── ValidatorPhase.java
        │               │   └── ValidatorRunner.java
        │               ├── inventory
        │               │   ├── runner
        │               │   │   └── InventoryScanRunner.java
        │               │   └── service
        │               │       ├── InventoryScanService.java
        │               │       └── DefaultInventoryScanService.java
        │               └── sanity
        │                   ├── runner
        │                   │   └── SqlSanityCheckRunner.java
        │                   └── service
        │                       ├── SqlSanityCheckService.java
        │                       └── DefaultSqlSanityCheckService.java
        └── resources
            ├── application.yml
            └── logback-spring.xml
```

---

## 3. `pom.xml`

Use the Spring Boot version allowed by your company Maven repository.

The version below is a safe template for JDK 17 enterprise tooling. Adjust it if your company repository only supports a different Spring Boot version.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>org.rosetta</groupId>
    <artifactId>sql-postgres-validator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>sql-postgres-validator</name>
    <description>SQL inventory and PostgreSQL migration validation CLI tool</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <javaparser.version>3.26.4</javaparser.version>
        <commons-csv.version>1.12.0</commons-csv.version>
        <jsqlparser.version>5.3</jsqlparser.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>${commons-csv.version}</version>
        </dependency>

        <dependency>
            <groupId>com.github.jsqlparser</groupId>
            <artifactId>jsqlparser</artifactId>
            <version>${jsqlparser.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: sql-postgres-validator
  main:
    web-application-type: none

validator:
  phase: inventory
  output-dir: ./output

  inventory:
    source-roots:
      - ../sample-service-a/src/main/java
      - ../sample-service-b/src/main/java
    inventory-output: ./output/sql-inventory.csv
    summary-output: ./output/scan-summary.txt

  sanity:
    input: ./output/sql-inventory.csv
    report-output: ./output/sql-sanity-report.csv
    summary-output: ./output/sql-sanity-summary.txt

logging:
  level:
    root: info
    org.rosetta.sqlvalidator: info
```

For real company paths, prefer `application-local.yml` and do not commit it.

Example local run:

```bash
java -jar target/sql-postgres-validator.jar \
  --spring.config.additional-location=./application-local.yml \
  --validator.phase=inventory
```

---

## 5. `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <property name="LOG_FILE" value="output/sql-postgres-validator.log"/>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>output/sql-postgres-validator.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>7</maxHistory>
            <totalSizeCap>100MB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

---

## 6. Main Application

### `SqlPostgresValidatorApplication.java`

```java
package org.rosetta.sqlvalidator;

import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the SQL PostgreSQL validator CLI application.
 */
@SpringBootApplication
@EnableConfigurationProperties(ValidatorProperties.class)
public class SqlPostgresValidatorApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SqlPostgresValidatorApplication.class, args);
    }
}
```

---

## 7. Common Constants

### `common/constant/ValidatorConstants.java`

```java
package org.rosetta.sqlvalidator.common.constant;

/**
 * Common constants used by the SQL validator tool.
 */
public final class ValidatorConstants {

    public static final String DEFAULT_OUTPUT_DIR = "./output";

    public static final String SQL_INVENTORY_FILE_NAME = "sql-inventory.csv";

    public static final String SQL_INVENTORY_SUMMARY_FILE_NAME = "scan-summary.txt";

    public static final String SQL_SANITY_REPORT_FILE_NAME = "sql-sanity-report.csv";

    public static final String SQL_SANITY_SUMMARY_FILE_NAME = "sql-sanity-summary.txt";

    private ValidatorConstants() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }
}
```

---

## 8. Exceptions

### `common/exception/SqlValidatorException.java`

```java
package org.rosetta.sqlvalidator.common.exception;

/**
 * Base runtime exception for the SQL validator tool.
 */
public class SqlValidatorException extends RuntimeException {

    public SqlValidatorException(final String message) {
        super(message);
    }

    public SqlValidatorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
```

### `common/exception/PhaseExecutionException.java`

```java
package org.rosetta.sqlvalidator.common.exception;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;

/**
 * Exception thrown when a validator phase fails.
 */
public class PhaseExecutionException extends SqlValidatorException {

    public PhaseExecutionException(final ValidatorPhase phase, final String message) {
        super("Phase " + phase + " failed: " + message);
    }

    public PhaseExecutionException(final ValidatorPhase phase, final String message, final Throwable cause) {
        super("Phase " + phase + " failed: " + message, cause);
    }
}
```

---

## 9. Configuration Properties

### `config/ValidatorProperties.java`

```java
package org.rosetta.sqlvalidator.config;

import java.util.ArrayList;
import java.util.List;
import org.rosetta.sqlvalidator.common.constant.ValidatorConstants;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SQL validator CLI application.
 */
@ConfigurationProperties(prefix = "validator")
public class ValidatorProperties {

    private ValidatorPhase phase = ValidatorPhase.INVENTORY;

    private String outputDir = ValidatorConstants.DEFAULT_OUTPUT_DIR;

    private final InventoryProperties inventory = new InventoryProperties();

    private final SanityProperties sanity = new SanityProperties();

    public ValidatorPhase getPhase() {
        return phase;
    }

    public void setPhase(final ValidatorPhase phase) {
        this.phase = phase;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(final String outputDir) {
        this.outputDir = outputDir;
    }

    public InventoryProperties getInventory() {
        return inventory;
    }

    public SanityProperties getSanity() {
        return sanity;
    }

    /**
     * Phase 1 inventory scanner properties.
     */
    public static class InventoryProperties {

        private List<String> sourceRoots = new ArrayList<>();

        private String inventoryOutput = "./output/" + ValidatorConstants.SQL_INVENTORY_FILE_NAME;

        private String summaryOutput = "./output/" + ValidatorConstants.SQL_INVENTORY_SUMMARY_FILE_NAME;

        public List<String> getSourceRoots() {
            return sourceRoots;
        }

        public void setSourceRoots(final List<String> sourceRoots) {
            this.sourceRoots = sourceRoots;
        }

        public String getInventoryOutput() {
            return inventoryOutput;
        }

        public void setInventoryOutput(final String inventoryOutput) {
            this.inventoryOutput = inventoryOutput;
        }

        public String getSummaryOutput() {
            return summaryOutput;
        }

        public void setSummaryOutput(final String summaryOutput) {
            this.summaryOutput = summaryOutput;
        }
    }

    /**
     * Phase 1.5 SQL text sanity checker properties.
     */
    public static class SanityProperties {

        private String input = "./output/" + ValidatorConstants.SQL_INVENTORY_FILE_NAME;

        private String reportOutput = "./output/" + ValidatorConstants.SQL_SANITY_REPORT_FILE_NAME;

        private String summaryOutput = "./output/" + ValidatorConstants.SQL_SANITY_SUMMARY_FILE_NAME;

        public String getInput() {
            return input;
        }

        public void setInput(final String input) {
            this.input = input;
        }

        public String getReportOutput() {
            return reportOutput;
        }

        public void setReportOutput(final String reportOutput) {
            this.reportOutput = reportOutput;
        }

        public String getSummaryOutput() {
            return summaryOutput;
        }

        public void setSummaryOutput(final String summaryOutput) {
            this.summaryOutput = summaryOutput;
        }
    }
}
```

---

## 10. Phase Enum

### `runner/ValidatorPhase.java`

```java
package org.rosetta.sqlvalidator.runner;

import java.util.Locale;

/**
 * Supported execution phases of the SQL validator tool.
 */
public enum ValidatorPhase {

    INVENTORY("inventory"),

    SANITY("sanity"),

    VALIDATION_EXPLAIN("validation-explain"),

    VALIDATION_SELECT_SMOKE("validation-select-smoke"),

    VALIDATION_DML_SAFETY("validation-dml-safety"),

    REAL_EXECUTION("real-execution");

    private final String configValue;

    ValidatorPhase(final String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }

    /**
     * Converts a configuration value to a validator phase.
     *
     * @param value phase value from configuration
     * @return matching validator phase
     */
    public static ValidatorPhase fromConfigValue(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return INVENTORY;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final ValidatorPhase phase : values()) {
            if (phase.configValue.equals(normalizedValue) || phase.name().equalsIgnoreCase(normalizedValue)) {
                return phase;
            }
        }

        throw new IllegalArgumentException("Unsupported validator phase: " + value);
    }
}
```

Important note:

Spring Boot relaxed binding can usually bind enum values from names like `INVENTORY`, but it may not automatically bind custom values like `validation-explain` to `VALIDATION_EXPLAIN` in all cases.

If your local binding fails, change `validator.phase` to enum name style:

```yaml
validator:
  phase: INVENTORY
```

Or add a custom converter later.

For MVP stability, enum-name style is the safest:

```bash
java -jar target/sql-postgres-validator.jar --validator.phase=INVENTORY
java -jar target/sql-postgres-validator.jar --validator.phase=SANITY
```

---

## 11. Runner Interface

### `runner/ValidatorRunner.java`

```java
package org.rosetta.sqlvalidator.runner;

/**
 * Common interface for each validator phase runner.
 */
public interface ValidatorRunner {

    /**
     * Returns the supported phase of this runner.
     *
     * @return supported validator phase
     */
    ValidatorPhase getSupportedPhase();

    /**
     * Runs the validator phase.
     */
    void run();
}
```

---

## 12. Runner Dispatcher

### `runner/RunnerDispatcher.java`

```java
package org.rosetta.sqlvalidator.runner;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.rosetta.sqlvalidator.common.exception.PhaseExecutionException;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Dispatches execution to the selected validator phase runner.
 */
@Component
public class RunnerDispatcher implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunnerDispatcher.class);

    private final ValidatorProperties validatorProperties;

    private final Map<ValidatorPhase, ValidatorRunner> runnerMap = new EnumMap<>(ValidatorPhase.class);

    public RunnerDispatcher(final ValidatorProperties validatorProperties, final List<ValidatorRunner> runners) {
        this.validatorProperties = validatorProperties;
        for (final ValidatorRunner runner : runners) {
            runnerMap.put(runner.getSupportedPhase(), runner);
        }
    }

    @Override
    public void run(final ApplicationArguments args) {
        final ValidatorPhase selectedPhase = validatorProperties.getPhase();
        LOGGER.info("Selected validator phase: {}", selectedPhase);

        final ValidatorRunner runner = runnerMap.get(selectedPhase);
        if (runner == null) {
            throw new PhaseExecutionException(selectedPhase, "No runner registered for selected phase");
        }

        final long startTimeMillis = System.currentTimeMillis();
        try {
            runner.run();
            final long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("Validator phase completed successfully. phase={}, elapsedMillis={}", selectedPhase, elapsedMillis);
        } catch (final RuntimeException exception) {
            LOGGER.error("Validator phase failed. phase={}", selectedPhase, exception);
            throw exception;
        }
    }
}
```

---

## 13. Phase 1 Inventory Service Interface

### `inventory/service/InventoryScanService.java`

```java
package org.rosetta.sqlvalidator.inventory.service;

/**
 * Service for running Phase 1 SQL inventory scan.
 */
public interface InventoryScanService {

    /**
     * Runs the Phase 1 SQL inventory scan.
     */
    void scan();
}
```

---

## 14. Phase 1 Inventory Service Implementation

### `inventory/service/DefaultInventoryScanService.java`

```java
package org.rosetta.sqlvalidator.inventory.service;

import java.nio.file.Path;
import java.util.List;
import org.rosetta.sqlvalidator.common.exception.PhaseExecutionException;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default Phase 1 SQL inventory scan service.
 *
 * <p>This class should delegate to the existing working scanner implementation.
 * Keep the existing extraction logic unchanged as much as possible.</p>
 */
@Service
public class DefaultInventoryScanService implements InventoryScanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultInventoryScanService.class);

    private final ValidatorProperties validatorProperties;

    public DefaultInventoryScanService(final ValidatorProperties validatorProperties) {
        this.validatorProperties = validatorProperties;
    }

    @Override
    public void scan() {
        final List<String> sourceRoots = validatorProperties.getInventory().getSourceRoots();
        final Path inventoryOutput = Path.of(validatorProperties.getInventory().getInventoryOutput());
        final Path summaryOutput = Path.of(validatorProperties.getInventory().getSummaryOutput());

        LOGGER.info("Starting Phase 1 SQL inventory scan. sourceRootCount={}, inventoryOutput={}, summaryOutput={}",
                sourceRoots.size(), inventoryOutput, summaryOutput);

        try {
            runExistingInventoryScanner(sourceRoots, inventoryOutput, summaryOutput);
        } catch (final RuntimeException exception) {
            throw new PhaseExecutionException(ValidatorPhase.INVENTORY, "Inventory scan failed", exception);
        }

        LOGGER.info("Phase 1 SQL inventory scan completed. inventoryOutput={}, summaryOutput={}",
                inventoryOutput, summaryOutput);
    }

    /**
     * Delegates to the existing working Phase 1 scanner implementation.
     *
     * <p>Move the old main-method logic here, or call the existing scanner classes directly.
     * Do not rewrite extraction rules during this refactor.</p>
     *
     * @param sourceRoots source root directories
     * @param inventoryOutput inventory CSV output path
     * @param summaryOutput summary TXT output path
     */
    private void runExistingInventoryScanner(final List<String> sourceRoots,
                                             final Path inventoryOutput,
                                             final Path summaryOutput) {
        /*
         * Integration instruction:
         *
         * Replace this block with your existing working Phase 1 logic.
         * Typical old flow may look like:
         *
         * 1. Create ScannerContext from sourceRoots.
         * 2. Call NativeSqlScanner.scan(context).
         * 3. Write records using SqlInventoryCsvWriter.
         * 4. Write summary using ScanSummaryWriter.
         *
         * Keep the CSV columns unchanged.
         * Keep the scanner behavior unchanged.
         */
        throw new UnsupportedOperationException("Please wire existing Phase 1 scanner logic here");
    }
}
```

Important:

After Copilot wires the existing Phase 1 logic, the `UnsupportedOperationException` must be removed.

---

## 15. Phase 1 Inventory Runner

### `inventory/runner/InventoryScanRunner.java`

```java
package org.rosetta.sqlvalidator.inventory.runner;

import org.rosetta.sqlvalidator.inventory.service.InventoryScanService;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runner for Phase 1 SQL inventory scan.
 */
@Component
public class InventoryScanRunner implements ValidatorRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryScanRunner.class);

    private final InventoryScanService inventoryScanService;

    public InventoryScanRunner(final InventoryScanService inventoryScanService) {
        this.inventoryScanService = inventoryScanService;
    }

    @Override
    public ValidatorPhase getSupportedPhase() {
        return ValidatorPhase.INVENTORY;
    }

    @Override
    public void run() {
        LOGGER.info("Running Phase 1: SQL Inventory Scanner");
        inventoryScanService.scan();
    }
}
```

---

## 16. Phase 1.5 Sanity Service Interface

### `sanity/service/SqlSanityCheckService.java`

```java
package org.rosetta.sqlvalidator.sanity.service;

/**
 * Service for running Phase 1.5 SQL text sanity check.
 */
public interface SqlSanityCheckService {

    /**
     * Runs the Phase 1.5 SQL text sanity check.
     */
    void check();
}
```

---

## 17. Phase 1.5 Sanity Service Implementation

### `sanity/service/DefaultSqlSanityCheckService.java`

```java
package org.rosetta.sqlvalidator.sanity.service;

import java.nio.file.Path;
import org.rosetta.sqlvalidator.common.exception.PhaseExecutionException;
import org.rosetta.sqlvalidator.config.ValidatorProperties;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default Phase 1.5 SQL text sanity check service.
 *
 * <p>This class should delegate to the existing working sanity checker implementation.</p>
 */
@Service
public class DefaultSqlSanityCheckService implements SqlSanityCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSqlSanityCheckService.class);

    private final ValidatorProperties validatorProperties;

    public DefaultSqlSanityCheckService(final ValidatorProperties validatorProperties) {
        this.validatorProperties = validatorProperties;
    }

    @Override
    public void check() {
        final Path input = Path.of(validatorProperties.getSanity().getInput());
        final Path reportOutput = Path.of(validatorProperties.getSanity().getReportOutput());
        final Path summaryOutput = Path.of(validatorProperties.getSanity().getSummaryOutput());

        LOGGER.info("Starting Phase 1.5 SQL sanity check. input={}, reportOutput={}, summaryOutput={}",
                input, reportOutput, summaryOutput);

        try {
            runExistingSanityChecker(input, reportOutput, summaryOutput);
        } catch (final RuntimeException exception) {
            throw new PhaseExecutionException(ValidatorPhase.SANITY, "SQL sanity check failed", exception);
        }

        LOGGER.info("Phase 1.5 SQL sanity check completed. reportOutput={}, summaryOutput={}",
                reportOutput, summaryOutput);
    }

    /**
     * Delegates to the existing working Phase 1.5 sanity checker implementation.
     *
     * @param input inventory CSV input path
     * @param reportOutput sanity CSV report output path
     * @param summaryOutput sanity TXT summary output path
     */
    private void runExistingSanityChecker(final Path input,
                                          final Path reportOutput,
                                          final Path summaryOutput) {
        /*
         * Integration instruction:
         *
         * Replace this block with your existing working Phase 1.5 logic.
         * Typical old flow may look like:
         *
         * 1. Read sql-inventory.csv using SqlInventoryCsvReader.
         * 2. Run SqlTextRuleChecker and SqlParserSanityChecker.
         * 3. Write report using SqlSanityReportWriter.
         * 4. Write summary using SqlSanitySummaryWriter.
         *
         * Keep the existing report columns unchanged.
         * Keep the JSqlParser preprocessing behavior unchanged.
         */
        throw new UnsupportedOperationException("Please wire existing Phase 1.5 sanity checker logic here");
    }
}
```

Important:

After Copilot wires the existing Phase 1.5 logic, the `UnsupportedOperationException` must be removed.

---

## 18. Phase 1.5 Sanity Runner

### `sanity/runner/SqlSanityCheckRunner.java`

```java
package org.rosetta.sqlvalidator.sanity.runner;

import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;
import org.rosetta.sqlvalidator.sanity.service.SqlSanityCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runner for Phase 1.5 SQL text sanity check.
 */
@Component
public class SqlSanityCheckRunner implements ValidatorRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlSanityCheckRunner.class);

    private final SqlSanityCheckService sqlSanityCheckService;

    public SqlSanityCheckRunner(final SqlSanityCheckService sqlSanityCheckService) {
        this.sqlSanityCheckService = sqlSanityCheckService;
    }

    @Override
    public ValidatorPhase getSupportedPhase() {
        return ValidatorPhase.SANITY;
    }

    @Override
    public void run() {
        LOGGER.info("Running Phase 1.5: SQL Text Sanity Check");
        sqlSanityCheckService.check();
    }
}
```

---

## 19. Optional: Future Phase Stub Pattern

Do not add this unless you want a clear message for future phase commands.

### `validation/runner/UnsupportedPhaseRunner.java`

```java
package org.rosetta.sqlvalidator.validation.runner;

import org.rosetta.sqlvalidator.common.exception.PhaseExecutionException;
import org.rosetta.sqlvalidator.runner.ValidatorPhase;
import org.rosetta.sqlvalidator.runner.ValidatorRunner;

/**
 * Base class for future Phase 2 runners that are not implemented yet.
 */
public abstract class UnsupportedPhaseRunner implements ValidatorRunner {

    @Override
    public void run() {
        throw new PhaseExecutionException(getSupportedPhase(), "This phase is not implemented in the Spring Boot CLI refactor step");
    }
}
```

MVP recommendation:

Do not register Phase 2 runners yet. It is cleaner for the dispatcher to report:

```text
No runner registered for selected phase
```

until Phase 2 is actually implemented.

---

## 20. `.gitignore`

```gitignore
# Build output
target/

# IntelliJ
.idea/
*.iml

# Runtime output
output/
*.csv
*.log

# Local real company paths / credentials
application-local.yml
.env
```

---

## 21. Build and Run

Build:

```bash
mvn clean package
```

Run Phase 1:

```bash
java -jar target/sql-postgres-validator-0.1.0-SNAPSHOT.jar --validator.phase=INVENTORY
```

Run Phase 1.5:

```bash
java -jar target/sql-postgres-validator-0.1.0-SNAPSHOT.jar --validator.phase=SANITY
```

Alternative with Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--validator.phase=INVENTORY"
```

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--validator.phase=SANITY"
```

---

## 22. Integration Prompt for Copilot

Use this after copying the Spring Boot CLI skeleton:

```text
This is a refactor task only.

The existing Phase 1 SQL inventory scanner and Phase 1.5 SQL sanity checker are already working.

Please refactor the project into a standard JDK 17 Spring Boot CLI application using docs/08 and docs/09.

Required:
1. Wire the existing Phase 1 scanner logic into DefaultInventoryScanService.
2. Wire the existing Phase 1.5 sanity checker logic into DefaultSqlSanityCheckService.
3. Remove the placeholder UnsupportedOperationException after wiring real logic.
4. Keep existing CSV columns unchanged.
5. Keep existing scanner behavior unchanged.
6. Keep existing sanity checker behavior unchanged.
7. Replace System.out.println with SLF4J logging.
8. Move file paths to application.yml.
9. Support running phases separately by validator.phase.
10. Follow JDK 17 and Alibaba Java coding style principles.

Do not:
1. Implement Phase 2 PostgreSQL validation.
2. Add PostgreSQL connection logic.
3. Add parameter binding plan logic.
4. Add EXPLAIN or SELECT smoke execution.
5. Modify business service modules.
6. Rewrite the whole scanner.
7. Change the output CSV format.
```

---

## 23. Verification Checklist

After Copilot refactor, verify:

```text
[ ] mvn clean package succeeds.
[ ] --validator.phase=INVENTORY runs only Phase 1.
[ ] --validator.phase=SANITY runs only Phase 1.5.
[ ] sql-inventory.csv format is unchanged.
[ ] sql-sanity-report.csv format is unchanged.
[ ] scan-summary.txt is generated.
[ ] sql-sanity-summary.txt is generated.
[ ] No PostgreSQL connection code exists.
[ ] No business module was modified.
[ ] Logs use SLF4J.
[ ] output/ and *.csv are ignored by Git.
```

---

## 24. Common Issues

### Issue 1: `validator.phase=inventory` cannot bind to enum

Use enum name:

```bash
--validator.phase=INVENTORY
```

or:

```yaml
validator:
  phase: INVENTORY
```

### Issue 2: Log file cannot be created

Make sure `output/` exists or let the runner create it before file logging.

For MVP, console logging is enough. File logging can be adjusted if the company machine has path restrictions.

### Issue 3: Existing scanner classes are not Spring beans

Do not force all existing classes to become Spring beans immediately.

It is acceptable for `DefaultInventoryScanService` to instantiate existing scanner/helper classes directly during this refactor.

### Issue 4: Too many package moves break imports

Do not move all old scanner/parser classes at once.

First add wrapper runners and services. Move internal classes later only if necessary.

---

## 25. Next Document After Verification

After this refactor is verified, generate the next pair of documents:

```text
10_Phase2_Binding_And_Explain_Validation_Design.md
11_Phase2_Binding_And_Explain_Validation_Code.md
```

Those future documents should cover:

- SQL placeholder parser.
- Spring `NamedParameterUtils` usage or equivalent strategy.
- JPA `?1/?2` parser.
- Java method parameter resolver.
- Binding plan output.
- PostgreSQL EXPLAIN executor.
- SQLState error classifier.

Do not mix that logic into this refactor step.
