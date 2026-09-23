package com.flamingo.qa.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a {@code Page} parameter so one test can drive several independent
 * browser sessions: {@code void t(@Actor("alice") Page a, @Actor("bob") Page b)}.
 * Each actor gets its own context, so cookies and storage are never shared.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Actor {
    String value();
}
