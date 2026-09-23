package com.flamingo.qa.junit;

/** Shared by every attempt of one retried test. */
final class RetryState {

    private final int maxAttempts;
    private final String displayName;
    private int started;
    private boolean retryRequested;

    RetryState(int maxAttempts, String displayName) {
        this.maxAttempts = maxAttempts;
        this.displayName = displayName;
    }

    int maxAttempts() {
        return maxAttempts;
    }

    String displayName() {
        return displayName;
    }

    /** The first attempt always runs; later ones only if the last asked for a retry. */
    boolean shouldStartAnotherAttempt() {
        return started == 0 || (retryRequested && started < maxAttempts);
    }

    int startAttempt() {
        retryRequested = false;
        return ++started;
    }

    void requestRetry() {
        retryRequested = true;
    }
}
