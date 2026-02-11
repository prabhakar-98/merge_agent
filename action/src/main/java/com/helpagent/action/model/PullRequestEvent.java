package com.helpagent.action.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a GitHub Pull Request webhook event payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestEvent {

    private String action;

    @JsonProperty("number")
    private int number;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Repository repository;
    private Sender sender;

    // ── Getters & Setters ──

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public PullRequest getPullRequest() { return pullRequest; }
    public void setPullRequest(PullRequest pullRequest) { this.pullRequest = pullRequest; }

    public Repository getRepository() { return repository; }
    public void setRepository(Repository repository) { this.repository = repository; }

    public Sender getSender() { return sender; }
    public void setSender(Sender sender) { this.sender = sender; }

    // ── Nested Classes ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        private long id;
        private int number;
        private String title;
        private String body;
        private String state;

        @JsonProperty("html_url")
        private String htmlUrl;

        @JsonProperty("diff_url")
        private String diffUrl;

        @JsonProperty("commits_url")
        private String commitsUrl;

        private User user;
        private Head head;
        private Base base;

        @JsonProperty("changed_files")
        private int changedFiles;

        private int additions;
        private int deletions;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getHtmlUrl() { return htmlUrl; }
        public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
        public String getDiffUrl() { return diffUrl; }
        public void setDiffUrl(String diffUrl) { this.diffUrl = diffUrl; }
        public String getCommitsUrl() { return commitsUrl; }
        public void setCommitsUrl(String commitsUrl) { this.commitsUrl = commitsUrl; }
        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }
        public Head getHead() { return head; }
        public void setHead(Head head) { this.head = head; }
        public Base getBase() { return base; }
        public void setBase(Base base) { this.base = base; }
        public int getChangedFiles() { return changedFiles; }
        public void setChangedFiles(int changedFiles) { this.changedFiles = changedFiles; }
        public int getAdditions() { return additions; }
        public void setAdditions(int additions) { this.additions = additions; }
        public int getDeletions() { return deletions; }
        public void setDeletions(int deletions) { this.deletions = deletions; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Head {
        private String ref;
        private String sha;

        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Base {
        private String ref;
        private String sha;

        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private long id;

        @JsonProperty("full_name")
        private String fullName;

        private String name;
        private Owner owner;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Owner getOwner() { return owner; }
        public void setOwner(Owner owner) { this.owner = owner; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        private String login;

        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String login;

        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sender {
        private String login;

        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
    }
}
