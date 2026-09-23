package com.flamingo.qa.framework;

import com.flamingo.qa.reporting.Redactor;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** Secrets must never reach a report. These tests hold the redaction rules to that. */
@Tag("framework")
@Epic("Framework")
@Feature("Report redaction")
class RedactorTest {

    @Test
    @DisplayName("Both halves of a login payload are masked")
    void masksCredentialsInJson() {
        String redacted = Redactor.redactJson("{\"username\":\"someone\",\"password\":\"s3cret\"}");

        assertThat(redacted)
                .doesNotContain("someone")
                .doesNotContain("s3cret")
                .contains(Redactor.MASK);
    }

    @Test
    @DisplayName("A token nested anywhere in a JSON body is masked")
    void masksNestedToken() {
        String redacted = Redactor.redactJson("{\"session\":{\"token\":\"abc123\"},\"items\":[{\"token\":\"def456\"}]}");

        assertThat(redacted).doesNotContain("abc123").doesNotContain("def456");
    }

    @Test
    @DisplayName("Sensitive key matching ignores case")
    void matchesKeysCaseInsensitively() {
        String redacted = Redactor.redactJson("{\"Password\":\"a\",\"TOKEN\":\"b\"}");

        assertThat(redacted).doesNotContain("\"a\"").doesNotContain("\"b\"");
    }

    @Test
    @DisplayName("Ordinary fields are left exactly as they were")
    void leavesOrdinaryFieldsAlone() {
        String redacted = Redactor.redactJson("{\"firstname\":\"Ada\",\"totalprice\":150}");

        assertThat(redacted).contains("\"Ada\"").contains("150").doesNotContain(Redactor.MASK);
    }

    @Test
    @DisplayName("A body that is not JSON passes through unchanged")
    void passesNonJsonThrough() {
        assertSoftly(softly -> {
            softly.assertThat(Redactor.redactJson("Forbidden")).isEqualTo("Forbidden");
            softly.assertThat(Redactor.redactJson("{\"firstname\":")).isEqualTo("{\"firstname\":");
            softly.assertThat(Redactor.redactJson(null)).isNull();
        });
    }

    @Test
    @DisplayName("Credential-bearing headers and cookies are recognised by name")
    void recognisesSensitiveNames() {
        assertSoftly(softly -> {
            softly.assertThat(Redactor.isSensitive("token")).isTrue();
            softly.assertThat(Redactor.isSensitive("Cookie")).isTrue();
            softly.assertThat(Redactor.isSensitive("set-cookie")).isTrue();
            softly.assertThat(Redactor.isSensitive("Authorization")).isTrue();
            softly.assertThat(Redactor.isSensitive("Content-Type")).isFalse();
        });
    }
}
