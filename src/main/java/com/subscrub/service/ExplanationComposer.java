package com.subscrub.service;

import com.subscrub.dto.IntentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Takes query result rows and generates a plain-language explanation using Gemini 2.5 Flash.
 * Used by both the template SQL path and the Gemini-generated SQL (DYNAMIC_SQL) path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationComposer {

    private final ChatClient.Builder chatClientBuilder;
    private static final int TIMEOUT_SECONDS = 15;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Generates a 2-3 sentence plain language explanation of the query results.
     * Includes numbers and the time window for clarity and trust.
     */
    public String compose(
            String originalQuery,
            IntentResult intent,
            List<Map<String, Object>> rows,
            String dataWindow) throws Exception {

        ChatClient client = chatClientBuilder.build();

        String rowSummary = rows.isEmpty()
            ? "No results found."
            : rows.subList(0, Math.min(rows.size(), 10)).toString();

        String prompt = """
            You are a helpful financial assistant explaining subscription data to a user.

            The user asked: "%s"
            Detected intent: %s
            Data window: %s
            Query results (first 10 rows): %s

            Write a clear, friendly explanation in 2-3 sentences.
            - Mention specific numbers (amounts, counts, percentages)
            - Mention the time window
            - Use plain language — no jargon
            - Do not mention SQL, databases, or technical terms
            - If no results, explain what was searched and suggest the user upload data
            """.formatted(originalQuery, intent.intent(), dataWindow, rowSummary);

        return executor.submit(() -> client.prompt()
            .user(prompt)
            .call()
            .content())
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
