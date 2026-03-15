package com.helpagent.action.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.FunctionTool;
import com.helpagent.action.model.strategy.ModelStrategy;
import com.helpagent.action.model.strategy.ModelStrategyFactory;
import com.helpagent.action.tools.MergeConflictTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Merge Help Agent.
 *
 * This agent detects merge conflicts in GitHub pull requests,
 * analyzes them using an LLM, and suggests resolutions by:
 * 1. Fetching base and feature branch info
 * 2. Detecting merge conflicts
 * 3. Analyzing conflicting file diffs
 * 4. Generating resolution suggestions with reasoning
 * 5. Creating a resolved branch with fixes committed
 * 6. Commenting the analysis and resolution on the PR
 */
@Configuration
public class MergeHelpAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(MergeHelpAgentConfig.class);

    private final ModelStrategyFactory modelStrategyFactory;
    private final MergeConflictTools mergeConflictTools;

    public MergeHelpAgentConfig(ModelStrategyFactory modelStrategyFactory,
                                 MergeConflictTools mergeConflictTools) {
        this.modelStrategyFactory = modelStrategyFactory;
        this.mergeConflictTools = mergeConflictTools;
    }

    @Bean
    public LlmAgent mergeHelpAgent() {
        ModelStrategy strategy = modelStrategyFactory.createStrategy();
        BaseLlm model = strategy.createModel();

        log.info("Initializing Merge Help Agent with {} model: {}",
                strategy.getProviderName(), strategy.getModelId());

        LlmAgent agent = LlmAgent.builder()
                .name("merge-help-agent")
                .description("AI merge conflict detection and resolution agent for GitHub pull requests")
                .model(model)
                .instruction(MERGE_AGENT_INSTRUCTION)
                .tools(
                        FunctionTool.create(mergeConflictTools, "fetchBranchInfo"),
                        FunctionTool.create(mergeConflictTools, "detectMergeConflicts"),
                        FunctionTool.create(mergeConflictTools, "fetchFileContent"),
                        FunctionTool.create(mergeConflictTools, "getBranchDiff"),
                        FunctionTool.create(mergeConflictTools, "createResolutionBranch"),
                        FunctionTool.create(mergeConflictTools, "commitResolvedFile"),
                        FunctionTool.create(mergeConflictTools, "commentOnPullRequest")
                )
                .build();

        log.info("Merge Help Agent initialized: {}", agent.name());
        return agent;
    }

    /**
     * The master instruction prompt that guides the agent's merge conflict resolution behavior.
     */
    private static final String MERGE_AGENT_INSTRUCTION = """
            You are an expert Merge Conflict Resolution Agent. Your job is to help developers
            resolve merge conflicts in GitHub pull requests.

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
            - Stop here.

            ### Step 4: Generate Resolution
            For each conflicting file:
            - Determine the best way to merge both sets of changes.
            - Prefer preserving ALL meaningful changes from both branches.
            - If changes are incompatible, prefer the feature branch changes but keep base branch fixes.
            - Write the complete resolved file content.
            - Explain your reasoning for each resolution decision.

            ### Step 5: Create Resolution Branch & Commit
            - Use `createResolutionBranch` to create a new branch named `merge-resolution/pr-{number}` from the base branch.
            - For each resolved file, use `commitResolvedFile` to commit the resolved content.

            ### Step 6: Comment on PR
            Use `commentOnPullRequest` to post a detailed comment with this format:

            ```markdown
            ## 🔀 Merge Conflict Analysis

            ### Conflicts Detected
            - List each conflicting file and what the conflict is about

            ### Resolution Strategy
            For each file:
            - **File**: `filename`
            - **Base branch change**: What was changed in base
            - **Feature branch change**: What was changed in feature
            - **Resolution**: How it was resolved and why

            ### Resolution Branch
            A resolution branch `merge-resolution/pr-{number}` has been created with the resolved files.
            You can review the changes and merge from there.

            ### Reasoning
            Detailed explanation of the resolution approach and any trade-offs made.
            ```

            ## Important Rules
            - ALWAYS fetch branch info first before checking conflicts.
            - ALWAYS read the full file content of conflicting files — don't guess.
            - NEVER modify files that don't have conflicts.
            - Provide CLEAR reasoning for every resolution decision.
            - If you cannot confidently resolve a conflict, say so and explain why manual review is needed.
            - Keep your PR comments professional, detailed, and helpful.
            """;
}
