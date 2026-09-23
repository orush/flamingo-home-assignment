package com.flamingo.qa.framework;

import com.flamingo.qa.config.Secret;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("framework")
@Epic("Framework")
@Feature("Configuration")
class SecretTest {

    @Test
    @DisplayName("A secret never prints its value, however it is stringified")
    void neverPrintsItsValue() {
        Secret secret = Secret.of("hunter2");

        assertSoftly(softly -> {
            softly.assertThat(secret.toString()).isEqualTo(Secret.MASK);
            softly.assertThat(String.valueOf(secret)).doesNotContain("hunter2");
            softly.assertThat("token=" + secret).doesNotContain("hunter2");
            softly.assertThat(String.format("%s", secret)).doesNotContain("hunter2");
        });
    }

    @Test
    @DisplayName("The value is reachable only by explicitly revealing it")
    void revealsOnlyOnRequest() {
        assertThat(Secret.of("hunter2").reveal()).isEqualTo("hunter2");
    }

    @Test
    @DisplayName("A secret cannot be created empty")
    void rejectsNull() {
        assertThatThrownBy(() -> Secret.of(null)).isInstanceOf(NullPointerException.class);
    }
}
