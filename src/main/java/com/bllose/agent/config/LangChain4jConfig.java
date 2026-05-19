package com.bllose.agent.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bllose.agent.debug.OpenAiDebugListener;
import com.bllose.agent.service.PaperAssistant;
import com.bllose.agent.service.StreamingAssistant;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.model-name:deepseek-v4-flash}")
    private String modelName;

    @Value("${langchain4j.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.openai.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.openai.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${langchain4j.openai.thinking.enabled:false}")
    private boolean thinkingEnabled;

    @Value("${langchain4j.openai.thinking.effort:high}")
    private String thinkingEffort;

    @Value("${langchain4j.memory.max-messages:20}")
    private int maxMessages;

    @Value("${langchain4j.openai.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.openai.log-responses:false}")
    private boolean logResponses;

    private final McpProperties mcpProperties;

    public LangChain4jConfig(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .returnThinking(true)
                .sendThinking(true);

        if (thinkingEnabled) {
            builder.customParameters(Map.of(
                "thinking", Map.of("type", "enabled"),
                "reasoning_effort", thinkingEffort
            ));
        }

        builder.logRequests(logRequests)
               .logResponses(logResponses);

        if (logRequests || logResponses) {
            builder.listeners(new OpenAiDebugListener());
        }

        return builder.build();
    }

    @Bean
    public OpenAiChatModel chatModel() {
        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests)
                .logResponses(logResponses);

        if (logRequests || logResponses) {
            builder.listeners(new OpenAiDebugListener());
        }

        return builder.build();
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(maxMessages);
    }

    @Bean
    StreamingAssistant streamingAssistant(
            OpenAiStreamingChatModel model,
            ChatMemoryProvider chatMemoryProvider,
            ToolProvider allMcpToolProvider) {
        return AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(allMcpToolProvider)
                .build();
    }

    // ── MCP Clients ────────────────────────────────────────

    @Bean
    McpClient paperMetadataMcpClient() {
        var transport = new StdioMcpTransport.Builder()
                .command(mcpProperties.getPaperMetadata().getCommand())
                .build();
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .key("paper-metadata")
                .build();
    }

    @Bean
    McpClient paperDownloadMcpClient() {
        var transport = new StdioMcpTransport.Builder()
                .command(mcpProperties.getPaperDownload().getCommand())
                .build();
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .key("paper-download")
                .build();
    }

    @Bean
    McpClient minimaxMcpClient() {
        var transportBuilder = new StdioMcpTransport.Builder()
                .command(mcpProperties.getMinimax().getCommand());
        if (mcpProperties.getMinimax().getEnv() != null) {
            transportBuilder.environment(mcpProperties.getMinimax().getEnv());
        }
        return new DefaultMcpClient.Builder()
                .transport(transportBuilder.build())
                .key("minimax")
                .build();
    }

    @Bean
    ToolProvider allMcpToolProvider(
            McpClient paperMetadataMcpClient,
            McpClient paperDownloadMcpClient,
            McpClient minimaxMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(paperMetadataMcpClient, paperDownloadMcpClient, minimaxMcpClient)
                .build();
    }

    @Bean
    PaperAssistant paperAssistant(
            OpenAiStreamingChatModel model,
            OpenAiChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            ToolProvider allMcpToolProvider) {
        return AiServices.builder(PaperAssistant.class)
                .streamingChatModel(model)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(allMcpToolProvider)
                .build();
    }
}
