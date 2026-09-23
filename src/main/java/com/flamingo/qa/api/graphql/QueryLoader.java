package com.flamingo.qa.api.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads {@code .graphql} documents from the classpath.
 *
 * <p>Valid, reusable queries live as files so they stay readable and
 * syntax-highlighted. Deliberately broken queries live inline in the test that
 * breaks them, next to the explanation of why.
 */
public final class QueryLoader {

    private static final String BASE = "/graphql/";

    private QueryLoader() {
    }

    public static String load(String fileName) {
        try (InputStream in = QueryLoader.class.getResourceAsStream(BASE + fileName)) {
            if (in == null) {
                throw new IllegalArgumentException("GraphQL document not found: " + BASE + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + fileName, e);
        }
    }
}
