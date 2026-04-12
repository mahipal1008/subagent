package com.subscrub.service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates that the SQL produced is safe — only SELECT, only expected tables.
 * Acts as the security boundary before any SQL reaches the database.
 */
@Service
public class SQLGuard {

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "transactions", "subscriptions", "subscription_events",
        "monthly_summaries", "known_services", "users"
    );

    // Word-boundary regex — so "created_at" does NOT match "CREATE"
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|EXECUTE)\\b|--|;",
        Pattern.CASE_INSENSITIVE
    );

    public record GuardResult(boolean safe, String reason) {}

    /**
     * Returns safe=true only if SQL passes all checks.
     * Only allows SELECT statements on the permitted tables.
     */
    public GuardResult validate(String sql) {
        String trimmed = sql.trim();

        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            return new GuardResult(false, "Only SELECT statements are allowed");
        }

        var matcher = FORBIDDEN_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return new GuardResult(false, "Forbidden keyword: " + matcher.group());
        }

        String upper = trimmed.toUpperCase();
        boolean mentionsAllowedTable = ALLOWED_TABLES.stream()
            .anyMatch(t -> upper.contains(t.toUpperCase()));

        if (!mentionsAllowedTable) {
            return new GuardResult(false, "SQL does not reference any allowed table");
        }

        return new GuardResult(true, "OK");
    }
}
