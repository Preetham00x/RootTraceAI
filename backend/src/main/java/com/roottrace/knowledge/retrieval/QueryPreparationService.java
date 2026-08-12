package com.roottrace.knowledge.retrieval;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Prepares and normalizes retrieval queries from incident metadata.
 *
 * Design goals:
 * 1. Preserve exact technical identifiers: "HikariPool-1", "NullPointerException", "SQLState 08001"
 * 2. Normalize irrelevant whitespace/punctuation
 * 3. Combine incident title + description + service + error message intelligently
 * 4. Avoid Gemini calls — deterministic preprocessing only
 */
@Service
public class QueryPreparationService {

    // Technical patterns that must NOT be stemmed or modified
    private static final Pattern EXCEPTION_PATTERN =
            Pattern.compile("[A-Z][a-zA-Z]*(?:Exception|Error|Fault|Warning|Timeout)[^\\s]*");
    private static final Pattern SQL_STATE_PATTERN =
            Pattern.compile("(?i)sqlstate\\s*[:\\s]?[0-9A-Z]+");
    private static final Pattern POOL_PATTERN =
            Pattern.compile("[A-Z][a-zA-Z]*(?:Pool|Connection|Thread|Queue)-\\d+[^\\s]*");
    private static final Pattern HTTP_CODE_PATTERN =
            Pattern.compile("(?:HTTP|status)\\s*[:\\s]?[1-5][0-9]{2}");

    /**
     * Builds a normalized retrieval query from incident fields.
     * Preserves technical identifiers while removing noise.
     *
     * @param title       incident title
     * @param description incident description (may be long)
     * @param service     service name
     * @param environment environment
     * @param errorMessage optional error message or stack trace fragment
     * @return normalized query string safe for FTS and embedding
     */
    public String buildQuery(String title, String description, String service,
                             String environment, String errorMessage) {
        StringBuilder query = new StringBuilder();

        // Title carries the highest signal — always include fully
        if (isNotBlank(title)) {
            query.append(title.trim());
        }

        // Service name gives critical context for filtering
        if (isNotBlank(service)) {
            appendWithSeparator(query, service.trim());
        }

        // Extract the most relevant technical tokens from description
        if (isNotBlank(description)) {
            String extracted = extractTechnicalTokens(description);
            if (!extracted.isBlank()) {
                appendWithSeparator(query, extracted);
            }
        }

        // Error message is extremely high signal — include first 300 chars
        if (isNotBlank(errorMessage)) {
            String truncated = errorMessage.trim();
            if (truncated.length() > 300) {
                truncated = truncated.substring(0, 300);
            }
            appendWithSeparator(query, truncated);
        }

        String result = query.toString().trim();
        // Final pass: collapse excessive whitespace
        result = result.replaceAll("\\s{2,}", " ");
        return result;
    }

    /**
     * Extracts technical tokens and the most relevant sentences from a description.
     * Keeps the first 2 sentences plus any sentences containing technical identifiers.
     */
    private String extractTechnicalTokens(String text) {
        if (text == null || text.isBlank()) return "";

        // Split into sentences (rough split)
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder extracted = new StringBuilder();

        // Always keep first 2 sentences for context
        for (int i = 0; i < Math.min(2, sentences.length); i++) {
            if (!sentences[i].isBlank()) {
                if (!extracted.isEmpty()) extracted.append(" ");
                extracted.append(sentences[i].trim());
            }
        }

        // Also extract sentences containing technical patterns
        for (int i = 2; i < sentences.length && extracted.length() < 500; i++) {
            String s = sentences[i].trim();
            if (containsTechnicalPattern(s)) {
                if (!extracted.isEmpty()) extracted.append(" ");
                extracted.append(s);
            }
        }

        return extracted.toString();
    }

    private boolean containsTechnicalPattern(String text) {
        return EXCEPTION_PATTERN.matcher(text).find()
                || SQL_STATE_PATTERN.matcher(text).find()
                || POOL_PATTERN.matcher(text).find()
                || HTTP_CODE_PATTERN.matcher(text).find();
    }

    private void appendWithSeparator(StringBuilder sb, String text) {
        if (!sb.isEmpty()) sb.append(". ");
        sb.append(text);
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
