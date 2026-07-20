package org.rosetta.sqlvalidator.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PerformanceProcessExecutor {

    public ProcessExecutionResult execute(
            List<String> command,
            long timeoutSeconds
    ) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(
                        process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "Unable to read process output: " + exception.getMessage();
            }
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            return new ProcessExecutionResult(
                    true,
                    -1,
                    outputFuture.getNow("Process timed out."));
        }

        return new ProcessExecutionResult(
                false,
                process.exitValue(),
                outputFuture.join());
    }
}
