package com.flamingo.qa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the resolution mechanism, never a real value.
 *
 * <p>No endpoint or credential is stored in this repository, so these tests use
 * a throwaway key and the defaulted keys instead of asserting on live config.
 *
 * <p>It writes JVM-wide system properties, which the lock declares to JUnit's
 * parallel scheduler.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ConfigLoaderTest {

    private static final String SCRATCH_KEY = "scratch.test.key";

    @Test
    @DisplayName("A system property beats every other source")
    void systemPropertyTakesPrecedence() {
        System.setProperty(SCRATCH_KEY, "from-system-property");
        try {
            assertThat(ConfigLoader.get(SCRATCH_KEY)).isEqualTo("from-system-property");
        } finally {
            System.clearProperty(SCRATCH_KEY);
        }
    }

    @Test
    @DisplayName("A blank override is ignored rather than returned")
    void blankSystemPropertyFallsThrough() {
        // Uses the scratch key, never a real one: other test classes read real
        // keys concurrently, and must not see a value this test planted.
        System.setProperty(SCRATCH_KEY, "   ");
        try {
            assertThatThrownBy(() -> ConfigLoader.get(SCRATCH_KEY))
                    .as("the blank value is skipped, so the key counts as missing")
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            System.clearProperty(SCRATCH_KEY);
        }
    }

    @Test
    @DisplayName("Optional keys fall back to built-in defaults")
    void fallsBackToBuiltInDefaultForOptionalKeys() {
        assertThat(ConfigLoader.get("ui.browser")).isEqualTo("chromium");
        assertThat(ConfigLoader.get("ui.headless")).isEqualTo("true");
        assertThat(ConfigLoader.get("http.timeout.ms")).isEqualTo("30000");
    }

    @Test
    @DisplayName("A missing required key fails fast and says how to fix it")
    void failsFastWithGuidanceWhenRequiredKeyIsMissing() {
        assertThatThrownBy(() -> ConfigLoader.get("definitely.absent.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definitely.absent.key")
                .hasMessageContaining("DEFINITELY_ABSENT_KEY")
                .hasMessageContaining(".env.example");
    }

    @Test
    @DisplayName("Dotted keys map to the environment variable convention")
    void mapsDottedKeyToEnvironmentVariableName() {
        assertThat(ConfigLoader.toEnvKey("booker.base.url")).isEqualTo("BOOKER_BASE_URL");
        assertThat(ConfigLoader.toEnvKey("http.timeout.ms")).isEqualTo("HTTP_TIMEOUT_MS");
    }

    @Test
    @DisplayName("Typed accessors parse the defaults")
    void typedAccessorsParseDefaults() {
        assertThat(Config.headless()).isTrue();
        assertThat(Config.timeoutMillis()).isEqualTo(30_000);
        assertThat(Config.browser()).isEqualTo("chromium");
    }

    @Test
    @DisplayName("Required endpoints and credentials are resolvable")
    void requiredEndpointsAreResolvable() {
        // Proves .env (or the CI environment) is wired up, without asserting
        // any value. Fails loudly if the setup step was skipped.
        assertThat(Config.bookerBaseUrl()).startsWith("http");
        assertThat(Config.graphQlEndpoint()).startsWith("http");
        assertThat(Config.uiBaseUrl()).startsWith("http");
        assertThat(Config.bookerUsername().reveal()).isNotBlank();
        assertThat(Config.bookerPassword().reveal()).isNotBlank();
    }
}
