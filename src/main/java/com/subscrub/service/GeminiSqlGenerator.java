package com.subscrub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Generates SELECT SQL for arbitrary user queries using Gemini 2.5 Flash.
 *
 * Invoked when the bi-encoder + cross-encoder pipeline confidence < 0.70.
 * Gemini receives the full DB schema, strict safety rules, and 3 few-shot
 * examples so it produces H2-compatible SQL that fits within the SQLGuard
 * whitelist (SELECT only, known tables only, :userId parameter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSqlGenerator {

    private final ChatClient.Builder chatClientBuilder;

    private static final String SCHEMA_PROMPT = """
        You are a SQL generator for a subscription finance app backed by an H2 database.

        DATABASE SCHEMA
        ───────────────
        TABLE subscriptions
          id              BIGINT  PRIMARY KEY
          user_id         VARCHAR — always filter WHERE user_id = :userId
          name            VARCHAR — clean service name (e.g. 'Netflix', 'Spotify')
          merchant        VARCHAR — raw bank merchant string
          category        VARCHAR — ENTERTAINMENT, MUSIC, FITNESS, CLOUD, SHOPPING, PROFESSIONAL
          current_amount  DECIMAL(12,2) — latest charge amount
          frequency       VARCHAR — MONTHLY, YEARLY, WEEKLY, QUARTERLY
          status          VARCHAR — ACTIVE, CANCELLED, PAUSED
          first_seen      DATE
          last_seen       DATE
          next_due_date   DATE
          avg_amount      DECIMAL(12,2)

        TABLE transactions
          id              BIGINT  PRIMARY KEY
          user_id         VARCHAR — always filter WHERE user_id = :userId
          date            DATE
          amount          DECIMAL(12,2)
          merchant        VARCHAR — raw bank merchant string
          description     VARCHAR
          channel         VARCHAR — UPI, CARD, NEFT, AUTO_DEBIT, DIRECT_DEBIT
          currency        VARCHAR DEFAULT 'INR'
          raw_entry       TEXT

        TABLE subscription_events
          id                      BIGINT  PRIMARY KEY
          subscription_id         BIGINT  FK → subscriptions.id
          user_id                 VARCHAR — always filter WHERE user_id = :userId
          date                    DATE
          amount                  DECIMAL(12,2)
          source_transaction_id   BIGINT  FK → transactions.id
          anomaly_flag            BOOLEAN — true when price changed from previous charge
          amount_delta            DECIMAL(12,2)
          amount_delta_pct        DECIMAL(6,2)

        TABLE monthly_summaries
          id              BIGINT  PRIMARY KEY
          user_id         VARCHAR — always filter WHERE user_id = :userId
          subscription_id BIGINT  FK → subscriptions.id
          summary_month   DATE — first day of month (e.g. '2025-07-01')
          total_amount    DECIMAL(10,2)
          charge_count    INT
          category        VARCHAR

        TABLE known_services
          id                  INT PRIMARY KEY
          service_name        VARCHAR
          merchant_pattern    VARCHAR
          category            VARCHAR
          typical_amount_min  DECIMAL(10,2)
          typical_amount_max  DECIMAL(10,2)
          frequency           VARCHAR
          website             VARCHAR
          is_active           BOOLEAN

        TABLE users
          id            VARCHAR PRIMARY KEY
          name          VARCHAR
          email         VARCHAR
          created_at    TIMESTAMP
          last_login    TIMESTAMP
          upload_count  INT

        IMPORTANT NOTES
        ───────────────
        - summary_month stores the 1st of each month (e.g. '2026-04-01').
          To get current month's 1st: DATE_TRUNC('MONTH', CURRENT_DATE)
          To get previous month's 1st: DATEADD(MONTH, -1, DATE_TRUNC('MONTH', CURRENT_DATE))

        STRICT RULES (violating any rule = task failure)
        ─────────────────────────────────────────────────
        1. Return ONLY the raw SQL — no explanation, no markdown, no code fences.
        2. Always filter by the named parameter :userId  (write it exactly as :userId).
        3. Use ONLY the tables listed above. No other tables.
        4. Only SELECT statements — no INSERT, UPDATE, DELETE, DROP, ALTER, CREATE.
        5. No semicolons.
        6. H2-compatible functions ONLY:
           - DATE_TRUNC('MONTH', date)     — truncate to first of month
           - DATEADD(unit, N, date)         — e.g. DATEADD(MONTH, -1, date)
           - DATEDIFF(unit, start, end)     — e.g. DATEDIFF(DAY, CURRENT_DATE, d)
           - MONTH(date), YEAR(date), CURRENT_DATE
           NEVER use Oracle/Postgres TRUNC() — always DATE_TRUNC().
        7. Embed all filter values (amounts, dates, day counts) as SQL literals — DO NOT
           introduce any named parameters other than :userId.

        FEW-SHOT EXAMPLES
        ──────────────────
        Q: "Show all my active subscriptions"
        A: SELECT name, current_amount, frequency, status, next_due_date
           FROM subscriptions
           WHERE user_id = :userId AND status = 'ACTIVE'
           ORDER BY current_amount DESC

        Q: "Which subscriptions went up in price?"
        A: SELECT s.name, se.amount, se.amount_delta, se.amount_delta_pct, se.date
           FROM subscription_events se
           JOIN subscriptions s ON s.id = se.subscription_id
           WHERE se.user_id = :userId AND se.anomaly_flag = true AND se.amount_delta > 0
           ORDER BY se.date DESC

        Q: "Total subscription spend this year"
        A: SELECT ms.summary_month, SUM(ms.total_amount) AS total
           FROM monthly_summaries ms
           WHERE ms.user_id = :userId AND YEAR(ms.summary_month) = YEAR(CURRENT_DATE)
           GROUP BY ms.summary_month
           ORDER BY ms.summary_month

        Q: "Compare this month's spend with previous month"
        A: SELECT ms.summary_month, SUM(ms.total_amount) AS total_spend
           FROM monthly_summaries ms
           WHERE ms.user_id = :userId
             AND ms.summary_month IN (
               DATE_TRUNC('MONTH', CURRENT_DATE),
               DATEADD(MONTH, -1, DATE_TRUNC('MONTH', CURRENT_DATE))
             )
           GROUP BY ms.summary_month
           ORDER BY ms.summary_month

        Q: "What payments are due in the next 10 days?"
        A: SELECT s.name, s.current_amount, s.frequency, s.next_due_date,
                  DATEDIFF(DAY, CURRENT_DATE, s.next_due_date) AS days_remaining
           FROM subscriptions s
           WHERE s.user_id = :userId AND s.status = 'ACTIVE'
             AND s.next_due_date BETWEEN CURRENT_DATE AND DATEADD(DAY, 10, CURRENT_DATE)
           ORDER BY s.next_due_date

        NOW ANSWER
        ──────────
        Q: "%s"
        A:""";

    /**
     * Asks Gemini to generate a safe SELECT query for the given natural-language question.
     *
     * @param query  the user's natural-language question
     * @param userId used in log messages only (NOT embedded in the prompt to avoid injection)
     * @return raw SQL string with :userId as the only named parameter
     */
    public String generate(String query, String userId) {
        String prompt = SCHEMA_PROMPT.formatted(query);
        log.debug("GeminiSqlGenerator: generating SQL for query='{}' userId={}", query, userId);

        String sql = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .call()
            .content();

        // Strip any accidental markdown fences Gemini might add
        sql = sql.replaceAll("(?i)```sql", "").replaceAll("```", "").trim();
        // Remove trailing semicolon if Gemini added one
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }

        log.debug("GeminiSqlGenerator: produced SQL={}", sql);
        return sql;
    }
}
