package com.helpagent.action.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Runs the Code Review ADK Agent against a specific pull request.
 *
 * This service creates a session, sends the review prompt to the agent,
 * and collects the agent's response (which includes tool calls to GitHub
 * and the final structured review output).
 */
@Service
public class CodeReviewAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewAgentRunner.class);

    private final BaseAgent codeReviewAgent;

    public CodeReviewAgentRunner(BaseAgent codeReviewAgent) {
        this.codeReviewAgent = codeReviewAgent;
    }

    /**
     * Runs a code review for the given repository and pull request.
     *
     * @param repoFullName Full repository name (e.g., "owner/repo")
     * @param prNumber     Pull request number
     * @param headSha      The head commit SHA of the PR
     * @return The agent's final text response (expected to be JSON)
     */
    public String runReview(String repoFullName, int prNumber, String headSha) {
        log.info("Starting AI review for {}/pull/{} @ {}", repoFullName, prNumber, headSha);

        InMemoryRunner runner = new InMemoryRunner(codeReviewAgent);
        RunConfig runConfig = RunConfig.builder().build();

        String userId = "review-bot-" + UUID.randomUUID().toString().substring(0, 8);
        String sessionId = "review-" + repoFullName.replace("/", "-") + "-pr" + prNumber;

        // Create a session
        Session session = runner.sessionService()
                .createSession(runner.appName(), userId, null, sessionId)
                .blockingGet();

        log.info("Created session: {} for user: {}", session.id(), userId);

        // Build the review prompt
        String prompt = buildReviewPrompt(repoFullName, prNumber, headSha);
        Content userMessage = Content.fromParts(Part.fromText(prompt));

        // Run the agent and collect the final response
        StringBuilder agentResponse = new StringBuilder();
        Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage, runConfig);

        events.blockingForEach(event -> {
            if (event.finalResponse()) {
                String content = event.stringifyContent();
                if (!content.isBlank()) {
                    agentResponse.append(content);
                }
                log.debug("Final agent response event received");
            } else {
                // Log intermediate events (tool calls, etc.) for debugging
                log.debug("Agent event: author={}, hasFunctionCalls={}",
                        event.author(),
                        event.content() != null && event.content().isPresent() &&
                                event.content().get().parts().isPresent() &&
                                event.content().get().parts().get().stream().anyMatch(p -> p.functionCall().isPresent()));
            }
        });

        String response = agentResponse.toString();
        log.info("AI review completed for {}/pull/{}. Response length: {} chars",
                repoFullName, prNumber, response.length());

        return response;
    }

    /**
     * Builds the prompt that instructs the agent to review a specific PR.
     */
    private String buildReviewPrompt(String repoFullName, int prNumber, String headSha) {
        return """
                Please perform a thorough code review of the following pull request:

                - Repository: %s
                - Pull Request Number: %d
                - Head Commit SHA: %s

                Steps:
                1. First, fetch the PR details to understand the context.
                2. Then, fetch the changed files with their diffs.
                3. If you need more context for any file, fetch its full content using the head SHA.
                4. Analyze all changes against the review criteria.
                5. Return your review as a JSON object with verdict, summary, and inline comments.

                Focus on the most impactful issues. Be constructive and specific.
                Return ONLY the JSON response, no other text.
                """.formatted(repoFullName, prNumber, headSha);
    }
}
