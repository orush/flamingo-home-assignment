package com.flamingo.qa.junit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a UI test. Bundles {@code @Test}, the "ui" tag, page injection and
 * failure capture, so a test needs no base class and no setup code:
 * {@code @UiTest void addsRecord(Page page) { ... }}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("ui")
@ExtendWith(PlaywrightExtension.class)
public @interface UiTest {
}
