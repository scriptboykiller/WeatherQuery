package org.rosetta.sqlvalidator.performance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class PerformanceToolInvoker {

    private final ObjectMapper objectMapper;
    private final PerformanceProcessExecutor processExecutor;

    public PerformanceToolInvoker(
            ObjectMapper objectMapper,
            PerformanceProcessExecutor processExecutor
    ) {
        this.objectMapper = objectMapper;
        this.processExecutor = processExecutor;
    }

    public void validateConfiguration(PerformanceReportProperties properties) {
        validateTool(properties);
    }

    public PerformanceInvocationOutcome invoke(
            PerformanceToolRequest request,
            Path requestFile,
            Path outputDirectory,
            PerformanceReportProperties properties
    ) {
        try {
            validateTool(properties);
            prepareCleanDirectory(outputDirectory);
            Files.createDirectories(requestFile.toAbsolutePath().getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(requestFile.toFile(), request);

            List<String> command = command(
                    properties,
                    requestFile,
                    outputDirectory);

            ProcessExecutionResult process = processExecutor.execute(
                    command,
                    properties.getTimeoutSeconds());

            Files.writeString(
                    outputDirectory.resolve("validator-invocation.log"),
                    process.output() == null ? "" : process.output(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            if (process.timedOut()) {
                return new PerformanceInvocationOutcome(
                        PerformanceStatus.TIMEOUT, "", "TIMEOUT",
                        "Performance tool exceeded "
                                + properties.getTimeoutSeconds() + " seconds.");
            }

            Path resultFile = outputDirectory.resolve("result.json");
            PerformanceToolResult toolResult = readResult(resultFile);

            if (toolResult == null) {
                return new PerformanceInvocationOutcome(
                        PerformanceStatus.RESULT_JSON_INVALID, "",
                        "RESULT_JSON_MISSING",
                        "result.json is missing or invalid. Exit code: "
                                + process.exitCode());
            }

            if (!"v2".equals(toolResult.responseVersion())
                    || !request.caseId().equals(toolResult.caseId())) {
                return new PerformanceInvocationOutcome(
                        PerformanceStatus.RESULT_JSON_INVALID, "",
                        "RESULT_JSON_CONTRACT_MISMATCH",
                        "responseVersion or caseId does not match the request.");
            }

            if (process.exitCode() != 0
                    || toolResult.status() != PerformanceToolStatus.SUCCESS) {
                PerformanceStatus status = toolResult.status() == PerformanceToolStatus.TIMEOUT
                        ? PerformanceStatus.TIMEOUT
                        : PerformanceStatus.TOOL_FAILED;
                return new PerformanceInvocationOutcome(
                        status, "",
                        safe(toolResult.errorCode(), toolResult.status().name()),
                        safe(toolResult.errorMessage(),
                                "Performance tool exit code: " + process.exitCode()));
            }

            Path html = safeReportPath(outputDirectory, toolResult.htmlReport());
            if (!Files.isRegularFile(html)) {
                return new PerformanceInvocationOutcome(
                        PerformanceStatus.REPORT_MISSING, "",
                        "HTML_REPORT_MISSING",
                        "The tool returned SUCCESS but the HTML report does not exist.");
            }

            return new PerformanceInvocationOutcome(
                    PerformanceStatus.SUCCESS,
                    html.toString(), "", "");

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new PerformanceInvocationOutcome(
                    PerformanceStatus.TOOL_FAILED, "",
                    "INTERRUPTED", exception.getMessage());
        } catch (Exception exception) {
            return new PerformanceInvocationOutcome(
                    PerformanceStatus.TOOL_FAILED, "",
                    "VALIDATOR_INVOCATION_ERROR", exception.getMessage());
        }
    }

    private void validateTool(PerformanceReportProperties properties) {
        if (!Files.isRegularFile(properties.getToolJar())) {
            throw new IllegalArgumentException(
                    "Performance tool JAR not found: " + properties.getToolJar());
        }
    }

    private List<String> command(
            PerformanceReportProperties properties,
            Path requestFile,
            Path outputDirectory
    ) {
        List<String> command = new ArrayList<>();
        command.add(resolveJava(properties));
        command.add("-jar");
        command.add(properties.getToolJar().toAbsolutePath().toString());
        command.add("--request-file=" + requestFile.toAbsolutePath());
        command.add("--output-dir=" + outputDirectory.toAbsolutePath());

        if (properties.getToolConfig() != null
                && Files.isRegularFile(properties.getToolConfig())) {
            command.add("--config=" + properties.getToolConfig().toAbsolutePath());
        }
        return List.copyOf(command);
    }

    private String resolveJava(PerformanceReportProperties properties) {
        if (properties.getJavaCommand() != null
                && !properties.getJavaCommand().isBlank()) {
            return properties.getJavaCommand().trim();
        }
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                windows ? "java.exe" : "java"
        ).toString();
    }

    private PerformanceToolResult readResult(Path resultFile) {
        if (!Files.isRegularFile(resultFile)) {
            return null;
        }
        try {
            return objectMapper.readValue(resultFile.toFile(), PerformanceToolResult.class);
        } catch (IOException exception) {
            return null;
        }
    }

    private Path safeReportPath(Path outputDirectory, String relativePath)
            throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("htmlReport is empty.");
        }
        Path root = outputDirectory.toAbsolutePath().normalize();
        Path report = root.resolve(relativePath).normalize();
        if (!report.startsWith(root)) {
            throw new IOException("htmlReport escapes the output directory.");
        }
        return report;
    }

    private void prepareCleanDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .filter(path -> !path.equals(directory))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new RuntimeException(exception);
                            }
                        });
            }
        }
        Files.createDirectories(directory);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
