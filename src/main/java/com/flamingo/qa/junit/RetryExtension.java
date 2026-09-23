package com.flamingo.qa.junit;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Supplies attempts for {@link RetryOnNetworkError} one at a time.
 *
 * <p>The stream is lazy: JUnit pulls the next attempt only after the previous
 * one has finished, and a new attempt is produced only if the previous one
 * asked for a retry.
 */
public class RetryExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> AnnotationSupport.isAnnotated(method, RetryOnNetworkError.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {
        int maxAttempts = context.getRequiredTestMethod()
                .getAnnotation(RetryOnNetworkError.class).maxAttempts();
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
        RetryState state = new RetryState(maxAttempts, context.getDisplayName());

        Spliterator<TestTemplateInvocationContext> attempts =
                new Spliterators.AbstractSpliterator<>(maxAttempts, Spliterator.NONNULL | Spliterator.ORDERED) {
                    @Override
                    public boolean tryAdvance(Consumer<? super TestTemplateInvocationContext> action) {
                        if (!state.shouldStartAnotherAttempt()) {
                            return false;
                        }
                        action.accept(new RetryInvocationContext(state, state.startAttempt()));
                        return true;
                    }
                };
        return StreamSupport.stream(attempts, false);
    }
}
