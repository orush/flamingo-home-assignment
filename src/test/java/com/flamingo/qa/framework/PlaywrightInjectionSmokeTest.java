package com.flamingo.qa.framework;

import com.flamingo.qa.config.Config;
import com.flamingo.qa.junit.Actor;
import com.flamingo.qa.junit.UiTest;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** Proves the UI plumbing itself: injection, isolation and network policy. */
@Tag("framework")
@Epic("Framework")
@Feature("UI plumbing")
class PlaywrightInjectionSmokeTest {

    @UiTest
    @DisplayName("An injected page loads the site with every third-party frame blocked")
    void injectsPageWithThirdPartyTrafficBlocked(Page page) {
        page.navigate(Config.uiBaseUrl() + "/webtables");

        assertSoftly(softly -> {
            softly.assertThat(page.title()).isNotBlank();
            softly.assertThat(page.url()).endsWith("/webtables");
            // Unblocked, this page embeds several ad iframes. Only the main frame
            // should remain.
            softly.assertThat(page.frames()).hasSize(1);
        });
    }

    @UiTest
    @DisplayName("Two actors in one test get fully isolated browser sessions")
    void actorsGetIsolatedSessions(@Actor("alice") Page alice, @Actor("bob") Page bob) {
        alice.navigate(Config.uiBaseUrl() + "/webtables");
        bob.navigate(Config.uiBaseUrl() + "/webtables");

        alice.evaluate("() => localStorage.setItem('owner', 'alice')");

        assertSoftly(softly -> {
            softly.assertThat(alice).isNotSameAs(bob);
            softly.assertThat(alice.context()).isNotSameAs(bob.context());
            softly.assertThat(bob.evaluate("() => localStorage.getItem('owner')"))
                    .as("storage written by one actor must be invisible to the other")
                    .isNull();
        });
    }
}
