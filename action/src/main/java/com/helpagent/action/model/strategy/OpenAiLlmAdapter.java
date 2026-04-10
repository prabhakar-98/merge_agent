package com.helpagent.action.model.strategy;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OpenAiLlmAdapter extends BaseLlm {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmAdapter.class);
    private final OpenAiChatModel model;

    public OpenAiLlmAdapter(OpenAiChatModel model) {
        super(model.modelName());
        this.model = model;
    }

    @Override
    public BaseLlmConnection connect(LlmRequest llmRequest) {
        throw new UnsupportedOperationException("Live connection is not supported for this model");
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
        try {
            List<ChatMessage> messages = convertToLangChainMessages(llmRequest);

            log.debug("Sending {} messages to OpenAI model", messages.size());
            Response<AiMessage> response = model.generate(messages);
            AiMessage aiMessage = response.content();

            Content responseContent = convertAiMessageToContent(aiMessage);

            LlmResponse llmResponse = LlmResponse.builder()
                    .content(responseContent)
                    .build();

            return Flowable.just(llmResponse);
        } catch (Exception e) {
            log.error("Error generating content: {}", e.getMessage(), e);
            LlmResponse errorResponse = LlmResponse.builder()
                    .errorMessage(e.getMessage())
                    .build();
            return Flowable.just(errorResponse);
        }
    }

    private List<ChatMessage> convertToLangChainMessages(LlmRequest llmRequest) {
        List<ChatMessage> messages = new ArrayList<>();

        // Add system instructions if present
        llmRequest.config().ifPresent(config ->
                config.systemInstruction().ifPresent(sysContent -> {
                    StringBuilder sysText = new StringBuilder();
                    sysContent.parts().orElse(Collections.emptyList()).forEach(part ->
                            part.text().ifPresent(sysText::append));
                    if (!sysText.isEmpty()) {
                        messages.add(SystemMessage.from(sysText.toString()));
                    }
                })
        );

        // Convert conversation contents
        List<Content> contents = llmRequest.contents();
        if (contents != null) {
            for (Content content : contents) {
                String role = content.role().orElse("user");
                List<Part> parts = content.parts().orElse(Collections.emptyList());

                switch (role) {
                    case "user" -> {
                        StringBuilder text = new StringBuilder();
                        for (Part part : parts) {
                            part.text().ifPresent(text::append);
                            // Handle function responses (tool results sent back to the model)
                            part.functionResponse().ifPresent(fr -> {
                                String name = fr.name().orElse("unknown");
                                Map<String, Object> resp = fr.response().orElse(Collections.emptyMap());
                                messages.add(ToolExecutionResultMessage.from(
                                        null, name, resp.toString()));
                            });
                        }
                        if (!text.isEmpty()) {
                            messages.add(UserMessage.from(text.toString()));
                        }
                    }
                    case "model" -> {
                        StringBuilder text = new StringBuilder();
                        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

                        for (Part part : parts) {
                            part.text().ifPresent(text::append);
                            part.functionCall().ifPresent(fc -> {
                                String name = fc.name().orElse("unknown");
                                Map<String, Object> args = fc.args().orElse(Collections.emptyMap());
                                toolRequests.add(ToolExecutionRequest.builder()
                                        .name(name)
                                        .arguments(toJson(args))
                                        .build());
                            });
                        }

                        if (!toolRequests.isEmpty()) {
                            messages.add(new AiMessage(text.toString(), toolRequests));
                        } else if (!text.isEmpty()) {
                            messages.add(new AiMessage(text.toString()));
                        }
                    }
                    default -> log.warn("Unknown role '{}', treating as user message", role);
                }
            }
        }

        return messages;
    }

    private Content convertAiMessageToContent(AiMessage aiMessage) {
        List<Part> parts = new ArrayList<>();

        // Add text if present
        if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
            parts.add(Part.fromText(aiMessage.text()));
        }

        // Convert tool execution requests to function calls
        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                Map<String, Object> args = parseJson(toolReq.arguments());
                parts.add(Part.fromFunctionCall(toolReq.name(), args));
            }
        }

        return Content.builder()
                .role("model")
                .parts(parts)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON arguments: {}", json, e);
            return Collections.emptyMap();
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize arguments to JSON", e);
            return "{}";
        }
    }
}
