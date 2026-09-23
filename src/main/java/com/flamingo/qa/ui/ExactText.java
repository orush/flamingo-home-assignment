package com.flamingo.qa.ui;

import java.util.regex.Pattern;

/**
 * Builds a pattern matching a whole text exactly, for Playwright text filters.
 *
 * <p>Playwright evaluates these patterns in the browser's JavaScript engine, not
 * Java's. {@code Pattern.quote} emits Java-only {@code \Q...\E} quoting, which
 * JavaScript reads as the literal letters {@code Q} and {@code E}: a filter for
 * "Female" becomes {@code /^QFemaleE$/} and matches nothing. So metacharacters
 * are escaped individually, in syntax both engines agree on.
 */
public final class ExactText {

    private static final Pattern METACHARACTERS = Pattern.compile("[.*+?^${}()|\\[\\]\\\\/]");

    private ExactText() {
    }

    public static Pattern of(String text) {
        return Pattern.compile("^" + METACHARACTERS.matcher(text).replaceAll("\\\\$0") + "$");
    }
}
