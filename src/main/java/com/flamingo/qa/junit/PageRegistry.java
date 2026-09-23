package com.flamingo.qa.junit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Every page one test opened, keyed by actor.
 *
 * <p>JUnit's extension store cannot be enumerated, so the registry keeps its own
 * ordered map; failure capture and cleanup then cover every page a test opened,
 * not just the first. It lives in the test method's store and is
 * {@link AutoCloseable}, so JUnit closes it when the method's context ends —
 * after {@code afterEach}, which is when failure capture happens.
 */
final class PageRegistry implements AutoCloseable {

    private final Map<String, ManagedPage> pages = new LinkedHashMap<>();

    ManagedPage pageFor(String actor) {
        return pages.computeIfAbsent(actor, key -> ManagedPage.create());
    }

    void forEach(BiConsumer<String, ManagedPage> action) {
        pages.forEach(action);
    }

    @Override
    public void close() {
        pages.values().forEach(managed -> managed.context().close());
        pages.clear();
    }
}
