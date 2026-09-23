package com.flamingo.qa.framework;

import com.flamingo.qa.api.RestClientFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("framework")
@Epic("Framework")
@Feature("HTTP client")
class RestClientTimeoutTest {

    @Test
    @DisplayName("A server that never answers makes the request fail fast instead of hanging the build")
    // SEPARATE_THREAD: a blocked socket read ignores interrupts, so without a
    // configured timeout this test would otherwise hang rather than fail.
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void silentServerTimesOut() throws Exception {
        // The OS accepts the connection into the listen backlog; nothing ever replies.
        try (ServerSocket silent = new ServerSocket(0)) {
            String url = "http://localhost:" + silent.getLocalPort();
            // Untimed warm-up: REST Assured's first request loads Groovy and is
            // woven by AspectJ, which took up to 5 s on a busy CI runner and would
            // otherwise be measured as if it were the timeout.
            catchThrowable(() -> RestClientFactory.jsonSpec(url, 500).get("/"));
            long start = System.nanoTime();

            Throwable thrown = catchThrowable(() -> RestClientFactory.jsonSpec(url, 500).get("/"));

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertSoftly(softly -> {
                softly.assertThat(causeChainContains(thrown, SocketTimeoutException.class))
                        .as("expected a socket timeout, got %s", thrown)
                        .isTrue();
                softly.assertThat(elapsedMs).as("gave up after the configured timeout").isLessThan(5_000);
            });
        }
    }

    private static boolean causeChainContains(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
