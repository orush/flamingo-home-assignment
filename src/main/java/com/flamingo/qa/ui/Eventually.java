package com.flamingo.qa.ui;

import com.microsoft.playwright.PlaywrightException;
import org.assertj.core.api.SoftAssertions;

import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Assertions on UI state that retry until they pass or a deadline is reached,
 * so a page still rendering, or a read interrupted by a brief network hiccup,
 * does not fail a test that would pass a moment later.
 *
 * <p>The block is run again from the start on every attempt, so it must
 * <em>read the page inside the lambda</em>: a value read before the call is
 * never refreshed, and retrying an assertion on it proves nothing. Actions do
 * not belong inside either: a retry would repeat them.
 *
 * <p>Only {@link AssertionError} (soft-assertion reports included) and
 * {@link PlaywrightException} are retried. Anything else is a bug in the test
 * and fails at once. At the deadline the last attempt's error is rethrown
 * unchanged, so its {@code .as(...)} description, and a finding's defect report,
 * reads exactly as it would without the retry; a check that is wrong every time
 * still fails, only later. Negative checks such as "the dialog stays open" are
 * bounded waits of their own and stay outside: an absence that holds once proves
 * nothing more on a second look.
 */
public final class Eventually {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private Eventually() {
    }

    /** Runs {@code check} until it passes, for up to {@link #DEFAULT_TIMEOUT}. */
    public static void eventually(Runnable check) {
        eventually(DEFAULT_TIMEOUT, check);
    }

    /** Runs {@code check} until it passes, for up to {@code timeout}. */
    public static void eventually(Duration timeout, Runnable check) {
        long start = System.nanoTime();
        long deadline = start + timeout.toNanos();
        for (int attempt = 1; ; attempt++) {
            try {
                check.run();
                return;
            } catch (AssertionError | PlaywrightException retryable) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !pause(Math.min(POLL_INTERVAL.toNanos(), remaining))) {
                    retryable.addSuppressed(new GaveUp(attempt, Duration.ofNanos(System.nanoTime() - start)));
                    throw retryable;
                }
            }
        }
    }

    /**
     * Runs the soft assertions until every one passes, for up to
     * {@link #DEFAULT_TIMEOUT}. Each attempt collects afresh, so the final report
     * lists the failures of the last attempt only.
     */
    public static void eventuallySoftly(Consumer<SoftAssertions> checks) {
        eventuallySoftly(DEFAULT_TIMEOUT, checks);
    }

    public static void eventuallySoftly(Duration timeout, Consumer<SoftAssertions> checks) {
        eventually(timeout, () -> assertSoftly(checks));
    }

    /** Sleeps for {@code nanos}; false if interrupted, with the flag restored. */
    private static boolean pause(long nanos) {
        try {
            Thread.sleep(Duration.ofNanos(nanos).toMillis(), (int) (nanos % 1_000_000));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Attached to the rethrown error as a suppressed note, so a report shows how long was waited. */
    private static final class GaveUp extends RuntimeException {

        GaveUp(int attempts, Duration elapsed) {
            super("Still failing after " + attempts + " attempts over " + elapsed.toMillis() + " ms",
                    null, false, false);
        }
    }
}
