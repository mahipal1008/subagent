package com.subscrub.service;

import com.subscrub.dto.AskResponse;
import com.subscrub.dto.IntentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * Master NLQ pipeline — wires all 8 steps together.
 * Single entry point for every chat query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NLQOrchestrator {

    private final IntentRouter        intentRouter;
    private final SemanticLayer       semanticLayer;
    private final SQLBuilder          sqlBuilder;
    private final SQLGuard            sqlGuard;
    private final QueryExecutor       queryExecutor;
    private final ExplanationComposer explanationComposer;
    private final SimulationEngine    simulationEngine;
    private final GeminiSqlGenerator  geminiSqlGenerator;

    /**
     * Full pipeline: Steps 1 → 8.
     *
     * Step 1: Validate input
     * Step 2: Classify intent (IntentRouter: 2A+2B+2C)
     * Step 3: Confidence gate — branch template SQL vs Gemini fallback
     * Step 4: Fetch SQL template from SemanticLayer
     * Step 5: Build parameterized SQL (SQLBuilder)
     * Step 6: Validate SQL (SQLGuard)
     * Step 7: Execute SQL (QueryExecutor)
     * Step 8: Compose explanation (ExplanationComposer)
     */
    public AskResponse ask(String query, String userId) {
        log.info("ask() userId={} query={}", userId, query);

        // ── Step 1: Basic validation ─────────────────────────────────────────
        if (query == null || query.isBlank()) {
            return errorResponse("Query cannot be empty", userId);
        }

        // ── Step 2: Intent classification (2A + 2B + 2C) ────────────────────
        IntentResult intent = intentRouter.classify(query, userId);
        log.info("Intent: {} conf={}", intent.intent(), intent.confidence());

        // ── Step 3: Confidence gate ──────────────────────────────────────────
        // Low confidence → Gemini generates SQL from schema + few-shot (same guard/execute/explain)
        if (intentRouter.isLowConfidence(intent) || "UNKNOWN".equals(intent.intent())) {
            return handleGeminiSql(query, intent.confidence(), userId);
        }

        // ── Special case: CANCEL_SIMULATION handled by SimulationEngine ──────
        if ("CANCEL_SIMULATION".equals(intent.intent())) {
            return handleSimulation(intent, userId);
        }

        // ── Special case: GREETING — static response, no SQL, no Gemini ──────
        if ("GREETING".equals(intent.intent())) {
            return handleGreeting(intent);
        }

        // ── Step 4: Fetch SQL template from SemanticLayer ────────────────────
        if (!semanticLayer.hasMetric(intent.intent())) {
            return errorResponse("No metric defined for intent: " + intent.intent(), userId);
        }

        // ── Step 5: Build parameterized SQL ──────────────────────────────────
        Map<String, Object> built = sqlBuilder.build(intent, userId);
        String sql = (String) built.get("sql");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) built.get("params");
        String dataWindow = sqlBuilder.buildDataWindow(intent);

        // ── Step 6: SQL Guard validation ──────────────────────────────────────
        SQLGuard.GuardResult guard = sqlGuard.validate(sql);
        if (!guard.safe()) {
            log.error("SQL guard rejected: {}", guard.reason());
            return errorResponse("Internal query validation failed", userId);
        }

        // ── Step 7: Execute SQL ───────────────────────────────────────────────
        List<Map<String, Object>> rows;
        try {
            rows = queryExecutor.execute(sql, params);
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return errorResponse("Failed to query your data: " + e.getMessage(), userId);
        }

        // ── Step 8: Compose explanation ───────────────────────────────────────
        String explanation;
        try {
            explanation = explanationComposer.compose(query, intent, rows, dataWindow);
        } catch (Exception e) {
            log.warn("Explanation generation failed, returning data without NL summary", e);
            explanation = "Here are your results (%d rows returned).".formatted(rows.size());
        }

        return new AskResponse(
            explanation,
            intent.intent(),
            intent.confidence(),
            sql,
            rows,
            semanticLayer.getMetricVersion(),
            dataWindow
        );
    }

    private AskResponse handleSimulation(IntentResult intent, String userId) {
        List<String> targets = intent.cancelTargets() != null
            ? intent.cancelTargets()
            : List.of();

        var simResult = simulationEngine.simulate(userId, targets);

        String explanation = "If you cancel %s, you would save ₹%.0f per month (₹%.0f per year). Your remaining subscription cost would be ₹%.0f per month."
            .formatted(
                String.join(" and ", targets),
                simResult.monthlySaving(),
                simResult.yearlySaving(),
                simResult.projectedMonthlyTotal()
            );

        return new AskResponse(
            explanation, "CANCEL_SIMULATION", intent.confidence(),
            null, List.of(), semanticLayer.getMetricVersion(), "Current snapshot"
        );
    }

    private AskResponse handleGreeting(IntentResult intent) {
        String answer = "👋 Hi! I'm SubScrub — your subscription assistant. Here's what I can help with:\n\n"
            + "• **Show subscriptions** — \"show all my subscriptions\"\n"
            + "• **Monthly trend** — \"how did my spending change over time?\"\n"
            + "• **Category breakdown** — \"which category costs the most?\"\n"
            + "• **Price changes** — \"which payments went up this year?\"\n"
            + "• **Upcoming debits** — \"what's due in the next 10 days?\"\n"
            + "• **Anomalies** — \"show unusual charges\"\n"
            + "• **Cancel simulation** — \"what if I cancel Netflix?\"\n"
            + "• **My profile** — \"what is my user name?\"\n\n"
            + "Just type a question and I'll query your data!";
        return new AskResponse(
            answer, "GREETING", intent.confidence(),
            null, List.of(), semanticLayer.getMetricVersion(), null
        );
    }

    /**
     * Low-confidence path: Gemini generates SQL from the DB schema + few-shot examples.
     * Passes through the same Safety Guard → Execute → Explain pipeline as the template path.
     */
    private AskResponse handleGeminiSql(String query, double confidence, String userId) {
        String sql;
        try {
            sql = geminiSqlGenerator.generate(query, userId);
        } catch (Exception e) {
            log.error("GeminiSqlGenerator failed", e);
            return errorResponse("Could not generate a query for: " + query, userId);
        }

        SQLGuard.GuardResult guard = sqlGuard.validate(sql);
        if (!guard.safe()) {
            log.error("Gemini SQL guard rejected: {}", guard.reason());
            return errorResponse("Generated query did not pass safety check — try rephrasing", userId);
        }

        List<Map<String, Object>> rows;
        try {
            rows = queryExecutor.execute(sql, Map.of("userId", userId));
        } catch (Exception e) {
            log.error("Gemini SQL execution failed", e);
            return errorResponse("Failed to run generated query: " + e.getMessage(), userId);
        }

        IntentResult dynamicIntent = new IntentResult("DYNAMIC_SQL", confidence, null, null, null, null);
        String explanation;
        try {
            explanation = explanationComposer.compose(query, dynamicIntent, rows, "Dynamic");
        } catch (Exception e) {
            log.warn("Explanation generation failed for dynamic SQL, returning data without NL summary", e);
            explanation = "Here are your results (%d rows returned).".formatted(rows.size());
        }

        return new AskResponse(
            explanation, "DYNAMIC_SQL", confidence,
            sql, rows, semanticLayer.getMetricVersion(), "Dynamic"
        );
    }

    private AskResponse errorResponse(String message, String userId) {
        return new AskResponse(message, "ERROR", 0.0, null,
            List.of(), semanticLayer.getMetricVersion(), null);
    }
}
