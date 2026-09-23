package com.flamingo.qa.junit;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.util.List;

/** One attempt of a {@link RetryOnNetworkError} test. */
final class RetryInvocationContext implements TestTemplateInvocationContext {

    private final RetryState state;
    private final int attempt;

    RetryInvocationContext(RetryState state, int attempt) {
        this.state = state;
        this.attempt = attempt;
    }

    /** The test's own name first time; retries say so, so flakiness shows in reports. */
    @Override
    public String getDisplayName(int invocationIndex) {
        return attempt == 1
                ? state.displayName()
                : state.displayName() + " (retry " + (attempt - 1) + " of " + (state.maxAttempts() - 1) + ")";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        TestExecutionExceptionHandler retryOnNetworkError = (context, failure) -> {
            if (isNetworkError(failure) && attempt < state.maxAttempts()) {
                state.requestRetry();
                // Aborted, not passed and not silently swallowed: the report
                // shows the attempt and why it was repeated.
                throw new TestAbortedException(
                        "Attempt " + attempt + " of " + state.maxAttempts() + " hit a network error; retrying",
                        failure);
            }
            throw failure;
        };
        return List.of(retryOnNetworkError);
    }

    private static boolean isNetworkError(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
