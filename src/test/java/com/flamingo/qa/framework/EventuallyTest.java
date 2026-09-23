package com.flamingo.qa.framework;

import com.microsoft.playwright.PlaywrightException;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.assertj.core.error.AssertJMultipleFailuresError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.flamingo.qa.ui.Eventually.eventually;
import static com.flamingo.qa.ui.Eventually.eventuallySoftly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("framework")
@Epic("Framework")
@Feature("UI plumbing")
class EventuallyTest {

    private static final Duration SHORT = Duration.ofMillis(600);

    @Test
    @DisplayName("Retries a failing check until it passes, then stops")
    void retriesUntilCheckPasses() {
        AtomicInteger attempts = new AtomicInteger();

        eventually(() -> assertThat(attempts.incrementAndGet()).isEqualTo(3));

        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("Retries a Playwright read failure, such as a destroyed execution context")
    void retriesPlaywrightException() {
        AtomicInteger attempts = new AtomicInteger();

        eventually(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new PlaywrightException("Execution context was destroyed");
            }
        });

        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("Fails on the first attempt for an exception that is neither an assertion nor a Playwright error")
    void doesNotRetryOtherExceptions() {
        AtomicInteger attempts = new AtomicInteger();

        Throwable thrown = catchThrowable(() -> eventually(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("bug in the test");
        }));

        assertSoftly(softly -> {
            softly.assertThat(thrown).isInstanceOf(IllegalStateException.class);
            softly.assertThat(attempts).hasValue(1);
        });
    }

    @Test
    @DisplayName("Gives up at the deadline and rethrows the last attempt's error unchanged")
    void rethrowsLastErrorAtDeadline() {
        AtomicInteger attempts = new AtomicInteger();
        long start = System.nanoTime();

        Throwable thrown = catchThrowable(() -> eventually(SHORT, () ->
                fail("attempt %d", attempts.incrementAndGet())));

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        // Hard, because the lines below read the error's message and suppressed list.
        assertThat(thrown).isInstanceOf(AssertionError.class);
        assertSoftly(softly -> {
            softly.assertThat(attempts.get()).as("attempts").isGreaterThan(1);
            softly.assertThat(thrown).hasMessage("attempt %d", attempts.get());
            softly.assertThat(thrown.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .satisfiesExactly(note -> assertThat(note).contains(attempts.get() + " attempts"));
            softly.assertThat(elapsed).as("elapsed").isBetween(SHORT, SHORT.plusSeconds(2));
        });
    }

    @Test
    @DisplayName("Soft variant retries the whole block and passes once every check does")
    void softlyRetriesWholeBlock() {
        AtomicInteger attempts = new AtomicInteger();

        eventuallySoftly(softly -> {
            int attempt = attempts.incrementAndGet();
            softly.assertThat(attempt).isGreaterThanOrEqualTo(2);
            softly.assertThat(attempt).isGreaterThanOrEqualTo(3);
        });

        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("Soft variant reports every failure of the last attempt, not those of earlier attempts")
    void softlyReportsOnlyLastAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        Throwable thrown = catchThrowable(() -> eventuallySoftly(SHORT, softly -> {
            int attempt = attempts.incrementAndGet();
            softly.assertThat(attempt).as("first").isNegative();
            softly.assertThat(attempt).as("second").isNegative();
        }));

        // Hard, because the line below casts to read the collected failures.
        assertThat(thrown).isInstanceOf(AssertJMultipleFailuresError.class);
        assertThat(((AssertJMultipleFailuresError) thrown).getFailures())
                .extracting(Throwable::getMessage)
                .satisfiesExactly(
                        first -> assertThat(first).contains("[first]").contains(String.valueOf(attempts.get())),
                        second -> assertThat(second).contains("[second]").contains(String.valueOf(attempts.get())));
    }
}
