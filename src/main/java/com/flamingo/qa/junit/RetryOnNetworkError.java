package com.flamingo.qa.junit;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Retries a test whose failure was a network error, and nothing else.
 *
 * <p>Used in place of {@code @Test}. An attempt that fails with an
 * {@link java.io.IOException} anywhere in its cause chain — a timeout, a refused
 * connection, a DNS failure — is reported as <em>aborted</em>, with its cause,
 * and the test runs again. Any other failure, including every assertion, fails
 * the test on the spot: a wrong status code or wrong data may be a real defect,
 * and retrying until a lucky run passes would hide it.
 *
 * <p>Attempts always run one after another on one thread, whatever the suite's
 * parallel settings: whether to run another attempt depends on how the last
 * one ended.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(RetryExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
public @interface RetryOnNetworkError {

    /** Total attempts, including the first. */
    int maxAttempts() default 2;
}
