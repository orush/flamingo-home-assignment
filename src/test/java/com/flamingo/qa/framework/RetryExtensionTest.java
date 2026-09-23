package com.flamingo.qa.framework;

import com.flamingo.qa.junit.RetryOnNetworkError;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Proves the retry semantics by running example tests in an isolated engine and
 * inspecting what happened. The examples are static nested classes, which
 * Surefire does not run on its own, because several of them fail on purpose.
 */
@Tag("framework")
@Epic("Framework")
@Feature("Retry")
class RetryExtensionTest {

    private static Events run(Class<?> example) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(example))
                .execute()
                .testEvents();
    }

    @Test
    @DisplayName("A network error is retried, and the test passes once an attempt succeeds")
    void retriesNetworkErrorUntilAnAttemptPasses() {
        NetworkErrorTwiceThenPass.CALLS.set(0);

        Events events = run(NetworkErrorTwiceThenPass.class);

        events.assertStatistics(stats -> stats.started(3).aborted(2).succeeded(1).failed(0));
        assertThat(NetworkErrorTwiceThenPass.CALLS).hasValue(3);
    }

    @Test
    @DisplayName("An assertion failure is never retried: it could be a real defect")
    void neverRetriesAssertionFailure() {
        AssertionFailure.CALLS.set(0);

        Events events = run(AssertionFailure.class);

        events.assertStatistics(stats -> stats.started(1).failed(1).aborted(0));
        assertThat(AssertionFailure.CALLS).hasValue(1);
    }

    @Test
    @DisplayName("A network error on every attempt fails the test after the last one")
    void failsAfterFinalAttempt() {
        Events events = run(NetworkErrorEveryTime.class);

        events.assertStatistics(stats -> stats.started(3).aborted(2).failed(1).succeeded(0));
    }

    @Test
    @DisplayName("A test that passes first time runs exactly once")
    void runsOnceWhenFirstAttemptPasses() {
        PassesFirstTime.CALLS.set(0);

        run(PassesFirstTime.class).assertStatistics(stats -> stats.started(1).succeeded(1));
        assertThat(PassesFirstTime.CALLS).hasValue(1);
    }

    @Test
    @DisplayName("A network error wrapped in another exception is still recognised")
    void findsNetworkErrorAnywhereInCauseChain() {
        WrappedNetworkErrorOnce.CALLS.set(0);

        run(WrappedNetworkErrorOnce.class)
                .assertStatistics(stats -> stats.started(2).aborted(1).succeeded(1));
    }

    @Test
    @DisplayName("Retries are labelled, so flakiness stays visible in reports")
    void labelsRetries() {
        NetworkErrorTwiceThenPass.CALLS.set(0);

        List<String> names = run(NetworkErrorTwiceThenPass.class).started().stream()
                .map(Event::getTestDescriptor)
                .map(descriptor -> descriptor.getDisplayName())
                .collect(Collectors.toList());

        assertThat(names).containsExactly(
                "flaky call",
                "flaky call (retry 1 of 2)",
                "flaky call (retry 2 of 2)");
    }

    // ---------------------------------------------------------------
    // Examples, run only through EngineTestKit above
    // ---------------------------------------------------------------

    static class NetworkErrorTwiceThenPass {
        static final AtomicInteger CALLS = new AtomicInteger();

        @RetryOnNetworkError(maxAttempts = 3)
        @DisplayName("flaky call")
        void test() throws IOException {
            if (CALLS.incrementAndGet() < 3) {
                throw new SocketTimeoutException("simulated read timeout");
            }
        }
    }

    static class AssertionFailure {
        static final AtomicInteger CALLS = new AtomicInteger();

        @RetryOnNetworkError(maxAttempts = 3)
        void test() {
            CALLS.incrementAndGet();
            fail("a genuine defect must not be retried away");
        }
    }

    static class NetworkErrorEveryTime {
        @RetryOnNetworkError(maxAttempts = 3)
        void test() throws IOException {
            throw new ConnectException("service down");
        }
    }

    static class PassesFirstTime {
        static final AtomicInteger CALLS = new AtomicInteger();

        @RetryOnNetworkError(maxAttempts = 3)
        void test() {
            CALLS.incrementAndGet();
        }
    }

    static class WrappedNetworkErrorOnce {
        static final AtomicInteger CALLS = new AtomicInteger();

        @RetryOnNetworkError(maxAttempts = 3)
        void test() {
            if (CALLS.incrementAndGet() == 1) {
                throw new UncheckedIOException(new SocketTimeoutException("wrapped"));
            }
        }
    }
}
