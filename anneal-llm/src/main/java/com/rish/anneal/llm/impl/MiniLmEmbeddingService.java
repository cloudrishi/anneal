package com.rish.anneal.llm.impl;

import com.rish.anneal.core.model.Finding;
import com.rish.anneal.llm.service.EmbeddingService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Embedding service backed by AllMiniLmL6V2 — pure Java ONNX model.
 * No network call. No Ollama. No API key.
 * Ships as a dependency (langchain4j-embeddings-all-minilm-l6-v2:0.36.2).
 *
 * Output: 384-dimensional float vector, suitable for pgvector cosine similarity.
 */
@ApplicationScoped
public class MiniLmEmbeddingService implements EmbeddingService {

    private static final Logger log = Logger.getLogger(MiniLmEmbeddingService.class);

    private AllMiniLmL6V2EmbeddingModel model;

    @PostConstruct
    void init() {
        // Model loads the ONNX file from the classpath — takes ~1s on first call.
        model = new AllMiniLmL6V2EmbeddingModel();
        log.info("AllMiniLmL6V2 embedding model loaded");
    }

    @Override
    public float[] embed(Finding finding) {
        String text = embeddingText(finding);
        TextSegment segment = TextSegment.from(text);
        return model.embed(segment).content().vector();
    }

    @Override
    public String embeddingText(Finding finding) {
        // ruleId + severity give the migration context.
        // originalCode gives the specific pattern — keeps similar violations close in vector space.
        // Truncate originalCode to 256 chars — MiniLM has a 256 token limit.
        String code = finding.getOriginalCode() != null
                ? finding.getOriginalCode().strip()
                : "";
        if (code.length() > 256) {
            code = code.substring(0, 256);
        }
        return "%s %s %s".formatted(finding.getRuleId(), finding.getSeverity().name(), code);
    }
}
