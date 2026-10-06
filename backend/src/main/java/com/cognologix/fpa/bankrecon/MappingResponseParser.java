package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.dto.BankReconDtos.LedgerMappingResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a JSON array of mapping objects, including a response cut off mid-array.
 */
final class MappingResponseParser {

    record Parsed(List<LedgerMappingResult> mappings, boolean incomplete, int returned) {}

    private static final Pattern QUOTED_ID = Pattern.compile(
            "\"(?:transactionId|id)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUMERIC_ID = Pattern.compile(
            "\"(?:transactionId|id)\"\\s*:\\s*(-?\\d+)");
    private static final Pattern QUOTED_LEDGER = Pattern.compile(
            "\"ledgerName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private MappingResponseParser() {}

    static Parsed parse(String content) {
        if (content == null || content.isBlank()) {
            return new Parsed(List.of(), true, 0);
        }
        String text = stripFence(content.trim());
        int start = text.indexOf('[');
        if (start < 0) {
            return new Parsed(List.of(), true, 0);
        }
        List<LedgerMappingResult> rows = new ArrayList<>();
        int returned = 0;
        int i = start + 1;
        boolean closed = false;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            if (c == ']') {
                closed = true;
                break;
            }
            if (c != '{') {
                break;
            }
            int end = endOfObject(text, i);
            if (end < 0) {
                break;
            }
            returned++;
            LedgerMappingResult row = parseObject(text.substring(i, end));
            if (row != null) {
                rows.add(row);
            }
            i = end;
        }
        return new Parsed(List.copyOf(rows), !closed, returned);
    }

    private static String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int newline = text.indexOf('\n');
        String body = newline < 0 ? "" : text.substring(newline + 1);
        int fence = body.lastIndexOf("```");
        return fence < 0 ? body : body.substring(0, fence);
    }

    static int endOfObject(String text, int openBrace) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static LedgerMappingResult parseObject(String json) {
        String id = first(QUOTED_ID, json);
        if (id == null) {
            id = first(NUMERIC_ID, json);
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        String ledger = first(QUOTED_LEDGER, json);
        return new LedgerMappingResult(unescape(id.trim()), ledger == null ? "" : unescape(ledger).trim());
    }

    private static String first(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
