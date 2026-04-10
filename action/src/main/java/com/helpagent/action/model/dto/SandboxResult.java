package com.helpagent.action.model.dto;

/**
 * Result of a sandbox code validation run.
 * Contains compilation/execution output, errors, and timing information.
 */
public class SandboxResult {

    /** Whether the code compiled/validated successfully (exit code 0). */
    private boolean success;

    /** Standard output from the compilation/execution. */
    private String stdout;

    /** Standard error output — contains compiler errors, warnings, etc. */
    private String stderr;

    /** Process exit code (0 = success). */
    private int exitCode;

    /** The programming language that was validated (e.g., "java", "python"). */
    private String language;

    /** How long the sandbox execution took in milliseconds. */
    private long durationMs;

    /** The filename that was validated. */
    private String filename;

    public SandboxResult() {}

    public SandboxResult(boolean success, String stdout, String stderr,
                         int exitCode, String language, long durationMs, String filename) {
        this.success = success;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.language = language;
        this.durationMs = durationMs;
        this.filename = filename;
    }

    /** Creates a successful result. */
    public static SandboxResult ok(String stdout, String language, long durationMs, String filename) {
        return new SandboxResult(true, stdout, "", 0, language, durationMs, filename);
    }

    /** Creates a failure result. */
    public static SandboxResult fail(String stdout, String stderr, int exitCode,
                                      String language, long durationMs, String filename) {
        return new SandboxResult(false, stdout, stderr, exitCode, language, durationMs, filename);
    }

    /** Creates a skipped result (unsupported language or sandbox disabled). */
    public static SandboxResult skipped(String reason, String language, String filename) {
        return new SandboxResult(true, reason, "", 0, language, 0, filename);
    }

    // ── Getters & Setters ──

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    @Override
    public String toString() {
        return String.format("SandboxResult{success=%s, language='%s', file='%s', exitCode=%d, duration=%dms}",
                success, language, filename, exitCode, durationMs);
    }
}
