package com.cognologix.fpa.bankrecon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Formats bank-reconciliation LLM logs. Summary lines go to the caller's logger
 * (the main log). Request and response bodies go to {@code llm.trace}.
 */
final class LlmTrace {

    static final Logger TRACE = LoggerFactory.getLogger("llm.trace");
    static final int RESPONSE_LIMIT = 20_000;
    static final int RESPONSE_EDGE = 5_000;
    static final int ERROR_BODY_LIMIT = 2_000;
    static final int RAW_REPLY_SAMPLE = 500;

    private LlmTrace() {}

    static final class CatalogMemory {
        private String loggedHash;

        String loggedHash() {
            return loggedHash;
        }
    }

    static void info(Logger logger, String apiKey, String message) {
        logger.info("{}", redact(message, apiKey));
    }

    static void warn(Logger logger, String apiKey, String message) {
        logger.warn("{}", redact(message, apiKey));
    }

    static void error(Logger logger, String apiKey, String callId, String message, Throwable error) {
        String text = "callId=" + callId + " " + message;
        if (error != null) {
            text = text + "\n" + stackTrace(error);
        }
        logger.error("{}", redact(prefixLines(callId, text), apiKey));
    }

    static void trace(boolean enabled, String apiKey, String message) {
        if (!enabled || !TRACE.isInfoEnabled()) {
            return;
        }
        TRACE.info("{}", redact(message, apiKey));
    }

    static boolean tracing(boolean propertyEnabled) {
        return propertyEnabled && TRACE.isInfoEnabled();
    }

    static String beforeLine(
            String callId,
            String kind,
            String provider,
            String baseUrl,
            String model,
            int transactions,
            int catalogLedgers,
            String catalogHash,
            int estimatedPromptTokens,
            String maxTokens,
            String thinkingDisabled,
            String apiKey) {
        return redact(
                "callId=" + callId
                        + " kind=" + kind
                        + " provider=" + provider
                        + " baseUrl=" + safeBaseUrl(baseUrl, apiKey)
                        + " model=" + model
                        + " transactions=" + transactions
                        + " catalogLedgers=" + catalogLedgers
                        + " catalogHash=" + catalogHash
                        + " estimatedPromptTokens=" + estimatedPromptTokens
                        + " maxTokens=" + maxTokens
                        + " thinkingDisabled=" + thinkingDisabled,
                apiKey);
    }

    static String afterLine(
            String callId,
            long elapsedMs,
            Integer httpStatus,
            String finishReason,
            Integer promptTokens,
            Integer completionTokens) {
        StringBuilder line = new StringBuilder();
        line.append("callId=").append(callId);
        line.append(" elapsedMs=").append(elapsedMs);
        line.append(" httpStatus=").append(httpStatus == null || httpStatus <= 0 ? "unknown" : httpStatus);
        line.append(" finishReason=").append(finishReason == null || finishReason.isBlank() ? "unknown" : finishReason);
        line.append(" promptTokens=").append(promptTokens == null ? "unknown" : promptTokens);
        line.append(" completionTokens=").append(completionTokens == null ? "unknown" : completionTokens);
        if (promptTokens != null && elapsedMs > 0) {
            double perSecond = promptTokens * 1000.0 / elapsedMs;
            line.append(" promptTokensPerSecond=").append(String.format(Locale.ROOT, "%.1f", perSecond));
        }
        return line.toString();
    }

    static String requestText(
            String prompt,
            String provider,
            String baseUrl,
            String model,
            int maxTokens,
            boolean thinkingDisabled,
            CatalogMemory memory,
            String apiKey) {
        String system = systemBlock(prompt);
        String user = userBlock(prompt, memory);
        String options = "provider=" + provider
                + " baseUrl=" + safeBaseUrl(baseUrl, apiKey)
                + " model=" + model
                + " temperature=0"
                + " maxTokens=" + maxTokens
                + " thinkingDisabled=" + thinkingDisabled;
        return "system:\n" + system + "\nuser:\n" + user + "\noptions:\n" + options;
    }

    static String embeddingRequestText(String provider, String baseUrl, String model, String input, String apiKey) {
        return "system:\n(none)\nuser:\n" + (input == null ? "" : input)
                + "\noptions:\nprovider=" + provider
                + " baseUrl=" + safeBaseUrl(baseUrl, apiKey)
                + " model=" + model
                + " kind=embedding";
    }

    static String responseText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.length() <= RESPONSE_LIMIT) {
            return raw;
        }
        return raw.substring(0, RESPONSE_EDGE)
                + "\n...[truncated totalLength=" + raw.length() + "]...\n"
                + raw.substring(raw.length() - RESPONSE_EDGE);
    }

    static String errorBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        if (body.length() <= ERROR_BODY_LIMIT) {
            return body;
        }
        return body.substring(0, ERROR_BODY_LIMIT) + "...[truncated totalLength=" + body.length() + "]";
    }

    static String rawReplySample(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String sample = raw.length() <= RAW_REPLY_SAMPLE ? raw : raw.substring(0, RAW_REPLY_SAMPLE);
        return sample.replace('\r', ' ').replace('\n', ' ');
    }

    static String shortSha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static int catalogLedgerCount(String catalog) {
        if (catalog == null || catalog.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : catalog.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals("PAYMENT") || trimmed.equals("RECEIPT") || trimmed.startsWith("##")) {
                continue;
            }
            count++;
        }
        return count;
    }

    static int transactionCount(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : prompt.split("\n", -1)) {
            if (line.startsWith("- id=")) {
                count++;
            }
        }
        return count;
    }

    static String safeBaseUrl(String url, String apiKey) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String cleaned = url.trim();
        try {
            URI uri = URI.create(cleaned);
            if (uri.getHost() != null) {
                URI safe = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
                cleaned = safe.toString();
            } else {
                cleaned = stripUrlSecrets(cleaned);
            }
        } catch (Exception e) {
            cleaned = stripUrlSecrets(cleaned);
        }
        return redact(cleaned, apiKey);
    }

    static String redact(String text, String apiKey) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = text;
        out = out.replaceAll("(?i)authorization\\s*[:=]\\s*\\S+(?:\\s+\\S+)?", "[redacted]");
        out = out.replaceAll("(?i)bearer\\s+\\S+", "[redacted]");
        out = out.replaceAll("(?i)(\"?api[_-]?key\"?\\s*[:=]\\s*)(\"?[^\\s\",}]+\"?)", "$1[redacted]");
        if (apiKey != null && apiKey.length() >= 8) {
            out = out.replace(apiKey, "[redacted]");
        }
        return out;
    }

    static String prefixLines(String callId, String body) {
        String prefix = "callId=" + callId + " ";
        String source = body == null ? "" : body;
        String[] lines = source.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (lines[i].startsWith(prefix)) {
                sb.append(lines[i]);
            } else {
                sb.append(prefix).append(lines[i]);
            }
        }
        return sb.toString();
    }

    private static String systemBlock(String prompt) {
        if (prompt == null) {
            return "";
        }
        int hints = prompt.indexOf("TRANSACTION HINTS\n");
        if (hints < 0) {
            return "";
        }
        return prompt.substring(0, hints).stripTrailing();
    }

    private static String userBlock(String prompt, CatalogMemory memory) {
        if (prompt == null) {
            return "";
        }
        int hints = prompt.indexOf("TRANSACTION HINTS\n");
        String user = hints < 0 ? prompt : prompt.substring(hints);
        String catalog = MappingPrompt.catalogSection(prompt);
        if (catalog.isEmpty()) {
            return user;
        }
        String hash = shortSha256(catalog);
        int ledgers = catalogLedgerCount(catalog);
        CatalogMemory state = memory == null ? new CatalogMemory() : memory;
        if (hash.equals(state.loggedHash)) {
            return replaceCatalog(user, "catalog: " + hash + " (" + ledgers + " ledgers, unchanged)");
        }
        state.loggedHash = hash;
        return user;
    }

    private static String replaceCatalog(String user, String replacement) {
        int start = user.indexOf("LEDGER CATALOG\n");
        int end = user.indexOf("\nTRANSACTIONS\n");
        if (start < 0 || end < 0 || end < start) {
            return user;
        }
        int from = start + "LEDGER CATALOG\n".length();
        return user.substring(0, from) + replacement + user.substring(end);
    }

    private static String stripUrlSecrets(String url) {
        String cleaned = url;
        int query = cleaned.indexOf('?');
        if (query >= 0) {
            cleaned = cleaned.substring(0, query);
        }
        int scheme = cleaned.indexOf("://");
        int at = cleaned.indexOf('@');
        if (scheme >= 0 && at > scheme) {
            cleaned = cleaned.substring(0, scheme + 3) + cleaned.substring(at + 1);
        }
        return cleaned;
    }

    private static String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
