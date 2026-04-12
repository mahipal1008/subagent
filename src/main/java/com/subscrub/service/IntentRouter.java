package com.subscrub.service;

import com.subscrub.dto.IntentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-step NLQ intent classification pipeline — NO LLM on the hot path.
 *
 *   2A — BGE-M3 bi-encoder      : ANN search in SimpleVectorStore → top-K candidates
 *   2B — ms-marco cross-encoder : (query, candidate) pair scoring → intent + confidence
 *   2C — regex slot extractor   : extract year / days-ahead / service names (no LLM)
 *
 * Gemini is NOT used here at all. When confidence < 0.70, the caller
 * (NLQOrchestrator) routes to Gemini-guided SQL generation instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouter {

    private final SimpleVectorStore   vectorStore;
    private final CrossEncoderService crossEncoderService;

    static final double CONFIDENCE_THRESHOLD = 0.70;
    private static final int TOP_K = 5;

    // ── Known service names — longest first to avoid prefix shadowing ────────
    private static final List<String> KNOWN_SERVICES = List.of(
        "YouTube Premium", "Disney+ Hotstar", "Amazon Prime", "LinkedIn Premium",
        "Zomato Gold", "FitLife Gym", "Disney+", "Hotstar", "Netflix", "Spotify",
        "YouTube", "FitLife", "LinkedIn", "Zomato", "iCloud", "Audible"
    );

    private static final Pattern YEAR_PATTERN      = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern DAYS_PATTERN       =
        Pattern.compile("\\b(\\d{1,3})\\s*(?:days?|din|दिन)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Classifies the user query into an intent with confidence and extracted slot values.
     * Returns intent="UNKNOWN" when confidence < threshold — the orchestrator will then
     * route to the Gemini SQL generation fallback.
     */
    public IntentResult classify(String query, String userId) {

        // ── Step 2A: BGE-M3 bi-encoder ──────────────────────────────────────
        List<Document> topDocs;
        try {
            topDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(TOP_K).build()
            );
        } catch (Exception e) {
            log.warn("2A: embedding search failed — falling back to Gemini SQL. Cause: {}", e.getMessage());
            return unknown(0.0);
        }

        if (topDocs.isEmpty()) {
            log.warn("2A: no candidates found for userId={}", userId);
            return unknown(0.0);
        }

        log.debug("2A: {} candidates for query='{}'", topDocs.size(), query);

        // ── Step 2B: ms-marco cross-encoder ─────────────────────────────────
        CrossEncoderService.RankResult rank = crossEncoderService.rank(query, topDocs);
        log.debug("2B: intent={} confidence={}", rank.intent(), rank.confidence());

        if (rank.confidence() < CONFIDENCE_THRESHOLD || "UNKNOWN".equals(rank.intent())) {
            log.info("2B: confidence {} below threshold {} — will use Gemini SQL fallback",
                rank.confidence(), CONFIDENCE_THRESHOLD);
            return unknown(rank.confidence());
        }

        // ── Step 2C: regex slot extraction (no LLM) ─────────────────────────
        Integer year        = extractYear(query);
        Integer daysAhead   = extractDaysAhead(query);
        List<String> cancels = extractServiceNames(query);
        String serviceName  = cancels.isEmpty() ? null : cancels.get(0);

        log.debug("2C slots: year={} daysAhead={} services={}", year, daysAhead, cancels);

        return new IntentResult(
            rank.intent(),
            rank.confidence(),
            serviceName,
            daysAhead,
            year,
            cancels.isEmpty() ? null : cancels
        );
    }

    /** Returns true when the confidence is below the acceptance threshold. */
    public boolean isLowConfidence(IntentResult result) {
        return result.confidence() < CONFIDENCE_THRESHOLD;
    }

    // ── Slot extractors ──────────────────────────────────────────────────────

    private static Integer extractYear(String query) {
        Matcher m = YEAR_PATTERN.matcher(query);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static Integer extractDaysAhead(String query) {
        Matcher m = DAYS_PATTERN.matcher(query);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Returns all known service names found in the query (preserves mention order). */
    private static List<String> extractServiceNames(String query) {
        String lower = query.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String svc : KNOWN_SERVICES) {
            if (lower.contains(svc.toLowerCase()) && !found.contains(svc)) {
                found.add(svc);
            }
        }
        return found;
    }

    private static IntentResult unknown(double confidence) {
        return new IntentResult("UNKNOWN", confidence, null, null, null, null);
    }
}
