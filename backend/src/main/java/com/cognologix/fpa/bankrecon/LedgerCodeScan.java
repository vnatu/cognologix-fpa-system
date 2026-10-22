package com.cognologix.fpa.bankrecon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words in a narration that may hold a ledger code. The bank appends an account
 * number of 9 or more digits; only the text before that run is searched.
 */
final class LedgerCodeScan {

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{9,}");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

    record Word(String text, int position) {}

    private LedgerCodeScan() {}

    static List<Word> words(String rawDescription) {
        if (rawDescription == null || rawDescription.isEmpty()) {
            return List.of();
        }
        String text = rawDescription;
        Matcher account = ACCOUNT_NUMBER.matcher(rawDescription);
        if (account.find()) {
            text = rawDescription.substring(0, account.start());
        }
        List<Word> words = new ArrayList<>();
        Matcher word = WORD.matcher(text);
        int position = 1;
        while (word.find()) {
            words.add(new Word(word.group(), position++));
        }
        return words;
    }
}
