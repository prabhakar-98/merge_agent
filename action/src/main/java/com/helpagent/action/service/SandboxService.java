package com.helpagent.action.service;

import com.helpagent.action.config.SandboxConfig;
import com.helpagent.action.model.dto.SandboxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Docker-based sandbox service for validating code before committing.
 *
 * <p>For each validation request, a disposable Docker container is spun up
 * with the appropriate language runtime (Java, Python, Node.js). The resolved
 * file content is written into the container, compiled/linted, and the results
 * captured. The container is destroyed after each run.
 *
 * <p>This catches syntax errors, missing imports, and basic compilation
 * failures before the agent commits potentially broken code.
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private final SandboxConfig config;

    /** Maps file extensions to language identifiers. */
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.of(
            ".java", "java",
            ".py", "python",
            ".js", "javascript",
            ".ts", "typescript",
            ".jsx", "javascript",
            ".tsx", "typescript",
            ".mjs", "javascript"
    );

    /** Languages we can validate in Docker. */
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "java", "python", "javascript", "typescript"
    );

    public SandboxService(SandboxConfig config) {
        this.config = config;
    }

    /**
     * Validates code content by running it through a Docker container
     * with the appropriate language runtime.
     *
     * @param filename  the filename (used to detect language)
     * @param content   the code content to validate
     * @param language  optional language override (null to auto-detect from extension)
     * @return SandboxResult with compilation/validation output
     */
    public SandboxResult validate(String filename, String content, String language) {
        if (!config.isEnabled()) {
            log.info("Sandbox disabled, skipping validation for: {}", filename);
            return SandboxResult.skipped("Sandbox is disabled", language, filename);
        }

        // Detect language from file extension if not provided
        String lang = (language != null && !language.isBlank())
                ? language.toLowerCase()
                : detectLanguage(filename);

        if (lang == null || !SUPPORTED_LANGUAGES.contains(lang)) {
            log.info("Unsupported language for sandbox validation: {} (file: {})", lang, filename);
            return SandboxResult.skipped(
                    "Language '" + lang + "' not supported for sandbox validation", lang, filename);
        }

        log.info("Sandbox: validating {} ({}) — content size: {} bytes", filename, lang, content.length());
        long startTime = System.currentTimeMillis();

        try {
            return switch (lang) {
                case "java" -> validateJava(filename, content, startTime);
                case "python" -> validatePython(filename, content, startTime);
                case "javascript" -> validateJavaScript(filename, content, startTime);
                case "typescript" -> validateTypeScript(filename, content, startTime);
                default -> SandboxResult.skipped("No sandbox handler for: " + lang, lang, filename);
            };
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Sandbox execution error for {}: {}", filename, e.getMessage(), e);
            return SandboxResult.fail("", "Sandbox error: " + e.getMessage(), 1, lang, duration, filename);
        }
    }

    /**
     * Validates Java code by compiling it with javac inside a Docker container.
     */
    private SandboxResult validateJava(String filename, String content, long startTime) {
        String imageName = config.getDocker().getJavaImage();
        String containerFile = "/workspace/" + extractFileName(filename);

        // Java compilation command
        String[] command = {"sh", "-c",
                "cd /workspace && javac " + extractFileName(filename) + " 2>&1; echo EXIT_CODE=$?"};

        return runInContainer(imageName, containerFile, content, command, "java", startTime, filename);
    }

    /**
     * Validates Python code by running py_compile inside a Docker container.
     */
    private SandboxResult validatePython(String filename, String content, long startTime) {
        String imageName = config.getDocker().getPythonImage();
        String containerFile = "/workspace/" + extractFileName(filename);

        String[] command = {"sh", "-c",
                "python -m py_compile /workspace/" + extractFileName(filename) + " 2>&1; echo EXIT_CODE=$?"};

        return runInContainer(imageName, containerFile, content, command, "python", startTime, filename);
    }

    /**
     * Validates JavaScript by running Node.js syntax check.
     */
    private SandboxResult validateJavaScript(String filename, String content, long startTime) {
        String imageName = config.getDocker().getNodeImage();
        String containerFile = "/workspace/" + extractFileName(filename);

        String[] command = {"sh", "-c",
                "node --check /workspace/" + extractFileName(filename) + " 2>&1; echo EXIT_CODE=$?"};

        return runInContainer(imageName, containerFile, content, command, "javascript", startTime, filename);
    }

    /**
     * Validates TypeScript using npx tsc (installs TypeScript in the container).
     */
    private SandboxResult validateTypeScript(String filename, String content, long startTime) {
        String imageName = config.getDocker().getNodeImage();
        String containerFile = "/workspace/" + extractFileName(filename);

        // Install TypeScript and run tsc --noEmit
        String[] command = {"sh", "-c",
                "npm install -g typescript 2>/dev/null && " +
                "cd /workspace && tsc --noEmit --allowJs --esModuleInterop " +
                extractFileName(filename) + " 2>&1; echo EXIT_CODE=$?"};

        return runInContainer(imageName, containerFile, content, command, "typescript", startTime, filename);
    }

    /**
     * Runs a command inside a Docker container with the given file content.
     */
    @SuppressWarnings("resource")
    private SandboxResult runInContainer(String imageName, String containerFile, String content,
                                          String[] command, String language, long startTime, String filename) {
        log.info("Sandbox: starting Docker container {} for {}", imageName, filename);

        try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(imageName))
                .withCommand("tail", "-f", "/dev/null") // Keep container alive
                .withStartupTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.getHostConfig()
                            .withMemory(parseMemoryLimit(config.getDocker().getMemoryLimit()))
                            .withCpuCount((long) config.getDocker().getCpuLimit());
                })) {

            container.start();

            // Create workspace directory and copy file content
            container.execInContainer("mkdir", "-p", "/workspace");
            container.copyFileToContainer(Transferable.of(content.getBytes()), containerFile);

            // Execute validation command
            org.testcontainers.containers.Container.ExecResult execResult =
                    container.execInContainer(command);

            long duration = System.currentTimeMillis() - startTime;

            String stdout = execResult.getStdout() != null ? execResult.getStdout() : "";
            String stderr = execResult.getStderr() != null ? execResult.getStderr() : "";

            // Parse exit code from output (we embed it in the command)
            int exitCode = parseExitCode(stdout);

            // Clean the EXIT_CODE marker from stdout
            stdout = stdout.replaceAll("EXIT_CODE=\\d+\\s*$", "").trim();

            if (exitCode == 0 && stderr.isBlank()) {
                log.info("Sandbox: ✅ {} passed validation ({}ms)", filename, duration);
                return SandboxResult.ok(stdout, language, duration, filename);
            } else {
                log.warn("Sandbox: ❌ {} failed validation (exit={}): {}", filename, exitCode, stderr);
                return SandboxResult.fail(stdout, stderr, exitCode, language, duration, filename);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Sandbox: Docker execution failed for {}: {}", filename, e.getMessage());
            return SandboxResult.fail("", "Docker error: " + e.getMessage(), 1, language, duration, filename);
        }
    }

    /**
     * Detects the programming language from a file extension.
     */
    private String detectLanguage(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = filename.substring(dot).toLowerCase();
        return EXTENSION_TO_LANGUAGE.get(ext);
    }

    /**
     * Extracts just the filename from a full path.
     */
    private String extractFileName(String path) {
        if (path == null) return "file";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Parses an exit code from the "EXIT_CODE=N" marker embedded in command output.
     */
    private int parseExitCode(String output) {
        if (output == null) return 1;
        int idx = output.lastIndexOf("EXIT_CODE=");
        if (idx < 0) return 0;
        try {
            String code = output.substring(idx + "EXIT_CODE=".length()).trim();
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Parses a memory limit string (e.g., "256m") to bytes.
     */
    private long parseMemoryLimit(String limit) {
        if (limit == null || limit.isBlank()) return 256 * 1024 * 1024L; // 256MB default
        String num = limit.replaceAll("[^0-9]", "");
        String unit = limit.replaceAll("[0-9]", "").toLowerCase();
        long value = Long.parseLong(num);
        return switch (unit) {
            case "g" -> value * 1024 * 1024 * 1024;
            case "m" -> value * 1024 * 1024;
            case "k" -> value * 1024;
            default -> value;
        };
    }
}
