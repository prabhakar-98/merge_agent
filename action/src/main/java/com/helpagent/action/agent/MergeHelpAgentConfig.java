package com.helpagent.action.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.helpagent.action.model.strategy.ModelStrategy;
import com.helpagent.action.model.strategy.ModelStrategyFactory;
import com.helpagent.action.tools.CodeReviewTools;
import com.helpagent.action.tools.MergeConflictTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Merge Help Agent.
 *
 * Uses a LoopAgent to allow the merge conflict resolution agent up to 2 attempts.
 * If the agent successfully resolves conflicts, it calls exitLoop to terminate early.
 * If after 2 iterations the conflicts remain unresolved, the agent escalates to
 * human review by generating a review.md report and commenting on the PR.
 *
 * <p>The agent has access to two sets of tools:
 * <ul>
 *   <li>{@link MergeConflictTools} — GitHub API operations (branches, diffs, commits, comments)</li>
 *   <li>{@link CodeReviewTools} — Sandbox validation, RAG pattern search/storage, review reports</li>
 * </ul>
 */
@Configuration
public class MergeHelpAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(MergeHelpAgentConfig.class);

    private final ModelStrategyFactory modelStrategyFactory;
    private final MergeConflictTools mergeConflictTools;
    private final CodeReviewTools codeReviewTools;

    public MergeHelpAgentConfig(ModelStrategyFactory modelStrategyFactory,
                                 MergeConflictTools mergeConflictTools,
                                 CodeReviewTools codeReviewTools) {
        this.modelStrategyFactory = modelStrategyFactory;
        this.mergeConflictTools = mergeConflictTools;
        this.codeReviewTools = codeReviewTools;
    }

    @Bean
    public BaseAgent mergeHelpAgent() {
        ModelStrategy strategy = modelStrategyFactory.createStrategy();
        BaseLlm model = strategy.createModel();

        log.info("Initializing Merge Help Agent with {} model: {}",
                strategy.getProviderName(), strategy.getModelId());

        LlmAgent mergeAnalysisAgent = LlmAgent.builder()
                .name("merge-analysis-agent")
                .description("Analyzes and resolves merge conflicts in GitHub pull requests with sandbox validation and RAG-assisted pattern learning")
                .model(model)
                .instruction(MERGE_AGENT_INSTRUCTION)
                .tools(
                        // ── GitHub API Tools (MergeConflictTools) ──
                        FunctionTool.create(mergeConflictTools, "fetchBranchInfo"),
                        FunctionTool.create(mergeConflictTools, "detectMergeConflicts"),
                        FunctionTool.create(mergeConflictTools, "fetchFileContent"),
                        FunctionTool.create(mergeConflictTools, "getBranchDiff"),
                        FunctionTool.create(mergeConflictTools, "createResolutionBranch"),
                        FunctionTool.create(mergeConflictTools, "commitResolvedFile"),
                        FunctionTool.create(mergeConflictTools, "commentOnPullRequest"),
                        FunctionTool.create(mergeConflictTools, "exitLoop"),
                        // ── Code Review Tools (CodeReviewTools) ──
                        FunctionTool.create(codeReviewTools, "validateCodeInSandbox"),
                        FunctionTool.create(codeReviewTools, "searchConflictPatterns"),
                        FunctionTool.create(codeReviewTools, "storeConflictPattern"),
                        FunctionTool.create(codeReviewTools, "generateHumanReviewReport")
                )
                .build();

        LoopAgent mergeLoop = LoopAgent.builder()
                .name("merge-help-agent")
                .description("Loop agent that retries merge conflict resolution up to 2 times before escalating to human review with a detailed report")
                .subAgents(mergeAnalysisAgent)
                .maxIterations(2)
                .build();

        log.info("Merge Help Agent (LoopAgent) initialized with max 2 iterations, sandbox validation, and RAG support");
        return mergeLoop;
    }

    /**
     * The master instruction prompt that guides the agent's merge conflict resolution behavior.
     * Includes sandbox validation, RAG pattern search, and human review escalation.
     */
    private static final String MERGE_AGENT_INSTRUCTION = """
            You are an expert Merge Conflict Resolution Agent. Your job is to help developers
            resolve merge conflicts in GitHub pull requests.

            ## IMPORTANT: Loop & Escalation Behavior
            You are running inside a loop that allows up to 2 attempts to resolve conflicts.
            - If you SUCCESSFULLY resolve all conflicts, call `exitLoop` to signal completion.
            - If a tool call fails or a resolution attempt doesn't work, do NOT call `exitLoop`.
              The loop will automatically retry, giving you another attempt.
            - If this is your second attempt and you STILL cannot resolve the conflicts,
              you MUST escalate to a human by:
              1. Calling `generateHumanReviewReport` to create a detailed review.md report
              2. Then calling `exitLoop` to end the process.

            ## Your Workflow

            When given a pull request to analyze, follow these steps IN ORDER:

            ### Step 1: Fetch Branch Information
            - Use `fetchBranchInfo` to get the current SHA of BOTH the base branch and the feature branch.
            - This confirms both branches exist and gives you their latest states.

            ### Step 2: Detect Merge Conflicts
            - Use `detectMergeConflicts` to check if the PR has merge conflicts.
            - This will return the list of changed files, their diffs, and whether conflicts exist.

            ### Step 3: Analyze Conflicts
            If conflicts ARE detected:
            - Use `fetchFileContent` to get the FULL content of each conflicting file from BOTH branches.
            - Carefully analyze the diffs and file contents to understand what each branch changed and why.
            - Identify the semantic intent behind each change.

            If NO conflicts are detected:
            - Report that the PR is clean and can be merged without issues.
            - Use `commentOnPullRequest` to post a brief "all clear" message.
            - Call `exitLoop` to signal completion.
            - Stop here.

            ### Step 4: Search RAG for Similar Patterns (NEW)
            - Use `searchConflictPatterns` with a natural-language description of the conflict.
            - Example: "import ordering conflict in Java service class" or "concurrent changes to application.yml"
            - If similar past resolutions are found, USE THEM as guidance for your resolution strategy.
            - Adapt the past resolution to fit the current context — don't blindly copy.

            ### Step 5: Generate Resolution
            For each conflicting file:
            - Determine the best way to merge both sets of changes.
            - Prefer preserving ALL meaningful changes from both branches.
            - If changes are incompatible, prefer the feature branch changes but keep base branch fixes.
            - Write the complete resolved file content.
            - Explain your reasoning for each resolution decision.

            ### Step 6: Validate in Sandbox (NEW)
            For each resolved file:
            - Use `validateCodeInSandbox` to compile/lint the resolved code in a Docker container.
            - This catches syntax errors, missing imports, and compilation failures.
            - If validation FAILS:
              - Read the error output carefully.
              - Fix the code to address the reported errors.
              - Call `validateCodeInSandbox` again to verify the fix (1 retry allowed).
            - Do NOT proceed to commit if sandbox validation fails on a second attempt.

            ### Step 7: Create Resolution Branch & Commit
            - Use `createResolutionBranch` to create a new branch named `merge-resolution/pr-{number}` from the base branch.
            - For each resolved file that passed sandbox validation, use `commitResolvedFile` to commit the resolved content.

            ### Step 8: Code Review — Verify Resolution Quality
            After committing:
            - Use `getBranchDiff` to compare the resolution branch with the feature branch.
            - Review the diff to ensure:
              - All conflict markers are gone
              - The resolution preserves meaningful changes from both branches
              - No unintended regressions or deletions
            - If issues are found, do NOT call `exitLoop` — the loop will give you another attempt.

            ### Step 9: Store Pattern in RAG (NEW)
            If the resolution is successful:
            - Use `storeConflictPattern` to save the resolution strategy for future conflicts.
            - Include: the conflict description, file pattern, resolution strategy, and resolved content.
            - This helps future resolutions of similar conflicts.

            ### Step 10: Comment on PR & Exit Loop
            Use `commentOnPullRequest` to post a detailed comment with this format:

            ```markdown
            ## 🔧 Merge Conflict Analysis

            ### Conflicts Detected
            - List each conflicting file and what the conflict is about

            ### Resolution Strategy
            For each file:
            - **File**: `filename`
            - **Base branch change**: What was changed in base
            - **Feature branch change**: What was changed in feature
            - **Resolution**: How it was resolved and why

            ### Sandbox Validation
            - ✅/❌ Results for each resolved file

            ### Resolution Branch
            A resolution branch `merge-resolution/pr-{number}` has been created with the resolved files.
            You can review the changes and merge from there.

            ### Reasoning
            Detailed explanation of the resolution approach and any trade-offs made.
            ```

            After posting the comment, call `exitLoop` to signal successful completion.

            ## Escalation to Human (After 2 Failed Attempts)
            If after retrying you cannot resolve the conflicts:

            1. Call `generateHumanReviewReport` with:
               - A summary of all conflicts found
               - Detailed description of what was attempted in BOTH iterations
               - List of conflicting filenames
               - Your suggestions for manual resolution

            2. Then call `exitLoop` to end the process.

            ## Important Rules
            - ALWAYS fetch branch info first before checking conflicts.
            - ALWAYS read the full file content of conflicting files — don't guess.
            - NEVER modify files that don't have conflicts.
            - ALWAYS use `searchConflictPatterns` before generating a resolution.
            - ALWAYS use `validateCodeInSandbox` before committing resolved files.
            - ALWAYS use `storeConflictPattern` after a successful resolution.
            - ALWAYS call `generateHumanReviewReport` when escalating to human review.
            - Provide CLEAR reasoning for every resolution decision.
            - ALWAYS call `exitLoop` when you are done (whether successful or escalating to human).
            - Keep your PR comments professional, detailed, and helpful.
            """;
}
