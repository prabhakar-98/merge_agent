package com.helpagent.action.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single review comment to be posted on a pull request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewComment {

    private String path;
    private int line;
    private String side;
    private String body;

    @JsonProperty("start_line")
    private Integer startLine;

    @JsonProperty("start_side")
    private String startSide;

    public ReviewComment() {}

    public ReviewComment(String path, int line, String body) {
        this.path = path;
        this.line = line;
        this.body = body;
        this.side = "RIGHT";
    }

    public ReviewComment(String path, int startLine, int endLine, String body) {
        this.path = path;
        this.line = endLine;
        this.startLine = startLine;
        this.body = body;
        this.side = "RIGHT";
        this.startSide = "RIGHT";
    }

    // ── Getters & Setters ──

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }

    public String getStartSide() { return startSide; }
    public void setStartSide(String startSide) { this.startSide = startSide; }

    @Override
    public String toString() {
        return "ReviewComment{path='%s', line=%d, body='%s'}".formatted(path, line,
                body.length() > 80 ? body.substring(0, 80) + "..." : body);
    }
}
