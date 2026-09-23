package com.flamingo.qa.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves configuration without storing any of it in the repository.
 *
 * <p>Precedence: system property, then OS environment variable, then {@code .env},
 * then a built-in default. Only presentation settings have defaults — every URL
 * and credential is required, and a missing one fails fast rather than silently
 * falling back to a hard-coded endpoint.
 */
public final class ConfigLoader {

    /** Non-sensitive defaults only. Never add a URL or credential here. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "ui.browser", "chromium",
            "ui.headless", "true",
            "http.timeout.ms", "30000");

    // ignoreIfMissing: CI supplies everything as environment variables, with no file.
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

    private ConfigLoader() {
    }

    public static String get(String key) {
        String envKey = toEnvKey(key);

        String fromSystem = System.getProperty(key);
        if (isPresent(fromSystem)) {
            return fromSystem;
        }
        String fromEnvironment = System.getenv(envKey);
        if (isPresent(fromEnvironment)) {
            return fromEnvironment;
        }
        String fromDotenv = DOTENV.get(envKey);
        if (isPresent(fromDotenv)) {
            return fromDotenv;
        }
        String fallback = DEFAULTS.get(key);
        if (isPresent(fallback)) {
            return fallback;
        }
        throw new IllegalStateException(
                "Missing required configuration '" + key + "' (" + envKey + "). "
                        + "Copy .env.example to .env and fill it in, or set " + envKey
                        + " in the environment.");
    }

    public static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
