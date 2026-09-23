package com.flamingo.qa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretTest {

    @Test
    @DisplayName("A secret never prints its value, however it is stringified")
    void neverPrintsItsValue() {
        Secret secret = Secret.of("hunter2");

        assertThat(secret.toString()).isEqualTo(Secret.MASK);
        assertThat(String.valueOf(secret)).doesNotContain("hunter2");
        assertThat("token=" + secret).doesNotContain("hunter2");
        assertThat(String.format("%s", secret)).doesNotContain("hunter2");
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
