package com.flamingo.qa.config;

import java.util.Objects;

/**
 * A credential that cannot be printed by accident.
 *
 * <p>Anything that stringifies a value — Allure step parameters, log lines,
 * assertion messages, string concatenation — sees only {@link #MASK}. The real
 * value is reachable solely through {@link #reveal()}, which is called at the
 * HTTP boundary and nowhere else. Allure's own parameter masking is not enough on
 * its own: it hides the value in the rendered report but still writes it in
 * plain text to the raw results that CI uploads.
 */
public final class Secret {

    public static final String MASK = "******";

    private final String value;

    private Secret(String value) {
        this.value = Objects.requireNonNull(value, "secret value");
    }

    public static Secret of(String value) {
        return new Secret(value);
    }

    /** The real value. Call only where it must leave the process, e.g. an HTTP request. */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return MASK;
    }
}
