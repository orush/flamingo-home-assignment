package com.flamingo.qa.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API test. Bundles {@code @Test} and the "api" tag.
 *
 * <p>Implies {@code @Test}, so it cannot be combined with
 * {@code @ParameterizedTest}; data-driven tests apply {@code @Tag("api")}
 * directly instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("api")
public @interface ApiTest {
}
