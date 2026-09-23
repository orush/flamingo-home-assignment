package com.flamingo.qa.ui;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Framework")
@Feature("UI plumbing")
class ThirdPartyBlockerTest {

    @DisplayName("Only the first-party host and its subdomains are allowed through")
    @ParameterizedTest(name = "{0} -> allowed={1}")
    @CsvSource({
            "https://example.com/webtables,            true",
            "https://example.com:8443/app.js,          true",
            "https://static.example.com/logo.png,      true",
            "https://ads.tracker.net/pixel.gif,        false",
            "https://evil-example.com/phish.js,        false",
            "https://example.com.attacker.io/x.js,     false",
            "'data:image/png;base64,iVBORw0KGgo=', true",
            "blob:https://example.com/7d1e-uuid,       true"
    })
    void allowsOnlyFirstParty(String url, boolean allowed) {
        assertThat(ThirdPartyBlocker.isAllowed(url, "example.com")).isEqualTo(allowed);
    }
}
