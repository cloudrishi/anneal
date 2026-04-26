package com.rish.anneal.llm.model;

public sealed interface LlmModel permits LlmModel.Ollama, LlmModel.Anthropic {

    String modelName();   // the raw string that goes to the API response

    record Ollama(String modelName) implements LlmModel {
    }

    record Anthropic(String modelName) implements LlmModel {
    }
}