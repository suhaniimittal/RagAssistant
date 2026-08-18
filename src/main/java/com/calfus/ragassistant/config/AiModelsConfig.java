package com.calfus.ragassistant.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two OpenAI-backed models the RAG pipeline needs, both via LangChain4j's
 * OpenAI integration: one turns text into vectors (used for both document
 * chunks at ingestion time and the user's question at query time), the other
 * generates the actual answer from the retrieved chunks.
 */
@Configuration
public class AiModelsConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.embedding-model}") String modelName) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Bean
    public ChatLanguageModel chatModel(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.chat-model}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
    }
}
