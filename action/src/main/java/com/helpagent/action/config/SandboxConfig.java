package com.helpagent.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for the Docker-based sandbox environment.
 * Maps to the {@code sandbox.*} namespace in application.yml.
 *
 * <p>The sandbox is used to compile and validate resolved code before
 * committing it, catching syntax errors or compilation failures early.
 */
@Configuration
@ConfigurationProperties(prefix = "sandbox")
public class SandboxConfig {

    /** Whether the sandbox is enabled. */
    private boolean enabled = true;

    /** Maximum time (seconds) a sandbox container is allowed to run. */
    private int timeoutSeconds = 30;

    /** Local working directory for staging files before Docker execution. */
    private String workingDir = "./sandbox-workspace";

    /** Languages supported by the sandbox. */
    private List<String> supportedLanguages = List.of("java", "python", "javascript", "typescript");

    /** Docker-specific configuration. */
    private DockerConfig docker = new DockerConfig();

    // ── Getters & Setters ──

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public String getWorkingDir() { return workingDir; }
    public void setWorkingDir(String workingDir) { this.workingDir = workingDir; }

    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public void setSupportedLanguages(List<String> supportedLanguages) { this.supportedLanguages = supportedLanguages; }

    public DockerConfig getDocker() { return docker; }
    public void setDocker(DockerConfig docker) { this.docker = docker; }

    /**
     * Docker image and resource configuration for the sandbox.
     */
    public static class DockerConfig {

        private String javaImage = "openjdk:21-slim";
        private String pythonImage = "python:3.12-slim";
        private String nodeImage = "node:20-slim";
        private String memoryLimit = "256m";
        private double cpuLimit = 1.0;

        public String getJavaImage() { return javaImage; }
        public void setJavaImage(String javaImage) { this.javaImage = javaImage; }

        public String getPythonImage() { return pythonImage; }
        public void setPythonImage(String pythonImage) { this.pythonImage = pythonImage; }

        public String getNodeImage() { return nodeImage; }
        public void setNodeImage(String nodeImage) { this.nodeImage = nodeImage; }

        public String getMemoryLimit() { return memoryLimit; }
        public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }

        public double getCpuLimit() { return cpuLimit; }
        public void setCpuLimit(double cpuLimit) { this.cpuLimit = cpuLimit; }
    }
}
