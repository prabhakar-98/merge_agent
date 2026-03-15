package com.helpagent.action.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a GitHub Pull Request webhook event payload.
 * Captures the essential fields from the webhook JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestEvent {

    private String action;

    @JsonProperty("number")
    private int number;

    @JsonProperty("pull_request")
    private PullRequest pullRequest;

    private Repository repository;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        private long id;
        private String title;
        private String body;
        private String state;
        private boolean mergeable;

        @JsonProperty("mergeable_state")
        private String mergeableState;

        @JsonProperty("merge_commit_sha")
        private String mergeCommitSha;

        private BranchRef head;
        private BranchRef base;

        @JsonProperty("html_url")
        private String htmlUrl;

        private User user;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public boolean isMergeable() { return mergeable; }
        public void setMergeable(boolean mergeable) { this.mergeable = mergeable; }
        public String getMergeableState() { return mergeableState; }
        public void setMergeableState(String mergeableState) { this.mergeableState = mergeableState; }
        public String getMergeCommitSha() { return mergeCommitSha; }
        public void setMergeCommitSha(String mergeCommitSha) { this.mergeCommitSha = mergeCommitSha; }
        public BranchRef getHead() { return head; }
        public void setHead(BranchRef head) { this.head = head; }
        public BranchRef getBase() { return base; }
        public void setBase(BranchRef base) { this.base = base; }
        public String getHtmlUrl() { return htmlUrl; }
        public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BranchRef {
        private String ref;
        private String sha;
        private String label;

        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private long id;

        @JsonProperty("full_name")
        private String fullName;

        private String name;

        @JsonProperty("html_url")
        private String htmlUrl;

        private Owner owner;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getHtmlUrl() { return htmlUrl; }
        public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
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
        private long id;
        private String login;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getLogin() { return login; }
        public void setLogin(String login) { this.login = login; }
    }
}
