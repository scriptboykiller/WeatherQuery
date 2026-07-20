package org.rosetta.sqlvalidator.performance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "validator.performance-report")
public class PerformanceReportProperties {

    private boolean enabled = false;
    private Path toolJar = Path.of("./tools/sql-performance-comparator.jar");
    private Path toolConfig = Path.of("./config/performance-tool.yml");
    private Path crossDbBaselineInput = Path.of("./output/sql-select-baseline.csv");
    private Path crossDbComparisonInput = Path.of("./output/sql-select-comparison.csv");
    private Path requestDirectory = Path.of("./output/performance/requests");
    private Path reportDirectory = Path.of("./output/performance/reports");
    private Path resultOutput = Path.of("./output/sql-performance-report.csv");
    private int topSlowest = 20;
    private int defaultSampleIndex = 1;
    private boolean requireResultMatch = true;
    private long timeoutSeconds = 180;
    private String javaCommand = "";
    private List<String> includeSqlIds = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Path getToolJar() { return toolJar; }
    public void setToolJar(Path toolJar) { this.toolJar = toolJar; }
    public Path getToolConfig() { return toolConfig; }
    public void setToolConfig(Path toolConfig) { this.toolConfig = toolConfig; }
    public Path getCrossDbBaselineInput() { return crossDbBaselineInput; }
    public void setCrossDbBaselineInput(Path value) { this.crossDbBaselineInput = value; }
    public Path getCrossDbComparisonInput() { return crossDbComparisonInput; }
    public void setCrossDbComparisonInput(Path value) { this.crossDbComparisonInput = value; }
    public Path getRequestDirectory() { return requestDirectory; }
    public void setRequestDirectory(Path requestDirectory) { this.requestDirectory = requestDirectory; }
    public Path getReportDirectory() { return reportDirectory; }
    public void setReportDirectory(Path reportDirectory) { this.reportDirectory = reportDirectory; }
    public Path getResultOutput() { return resultOutput; }
    public void setResultOutput(Path resultOutput) { this.resultOutput = resultOutput; }
    public int getTopSlowest() { return topSlowest; }
    public void setTopSlowest(int topSlowest) { this.topSlowest = topSlowest; }
    public int getDefaultSampleIndex() { return defaultSampleIndex; }
    public void setDefaultSampleIndex(int defaultSampleIndex) { this.defaultSampleIndex = defaultSampleIndex; }
    public boolean isRequireResultMatch() { return requireResultMatch; }
    public void setRequireResultMatch(boolean requireResultMatch) { this.requireResultMatch = requireResultMatch; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getJavaCommand() { return javaCommand; }
    public void setJavaCommand(String javaCommand) { this.javaCommand = javaCommand; }
    public List<String> getIncludeSqlIds() { return includeSqlIds; }
    public void setIncludeSqlIds(List<String> includeSqlIds) {
        this.includeSqlIds = includeSqlIds == null ? new ArrayList<>() : new ArrayList<>(includeSqlIds);
    }
}
