package com.subscrub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reranker — picks the best intent candidate from the bi-encoder's top-K results.
 *
 * Uses the cosine similarity score that SimpleVectorStore sets via Document.getScore().
 * Fully local, zero API calls. Score is in [0, 1] where 1 = identical vectors.
 */
@Slf4j
@Service
public class CrossEncoderService {

    public record RankResult(String intent, double confidence) {}

    /**
     * Returns the intent of the highest-scoring candidate document together
     * with its cosine similarity score as confidence.
     */
    public RankResult rank(String query, List<Document> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new RankResult("UNKNOWN", 0.0);
        }

        String bestIntent    = "UNKNOWN";
        double bestConfidence = 0.0;

        for (Document doc : candidates) {
            double score  = extractScore(doc);
            String intent = (String) doc.getMetadata().getOrDefault("intent", "UNKNOWN");
            log.debug("Bi-encoder score: intent={} score={}", intent, score);

            if (score > bestConfidence) {
                bestConfidence = score;
                bestIntent     = intent;
            }
        }

        log.debug("Best candidate: intent={} confidence={}", bestIntent, bestConfidence);
        return new RankResult(bestIntent, bestConfidence);
    }

    /**
     * SimpleVectorStore sets cosine similarity via Document.getScore() — not in metadata.
     * Returns 0.0 if score is missing (forces Gemini fallback for safety).
     */
    private static double extractScore(Document doc) {
        Double score = doc.getScore();
        if (score != null) {
            return Math.min(Math.max(score, 0.0), 1.0);
        }
        return 0.0;
    }
}
