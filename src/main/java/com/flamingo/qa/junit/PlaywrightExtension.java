package com.flamingo.qa.junit;

import com.flamingo.qa.ui.PlaywrightFactory;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Injects isolated Playwright pages into test methods, and captures a screenshot
 * and a trace of every page when a test fails.
 *
 * <p>Capture runs in {@code afterEach} rather than in {@code TestWatcher.testFailed},
 * because {@code testFailed} fires only after the test's extension store has been
 * closed — by which point the browser contexts are gone and there is nothing left
 * to photograph.
 */
public class PlaywrightExtension implements ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PlaywrightExtension.class);

    private static final Path SCREENSHOTS = Paths.get("target", "screenshots");
    private static final Path TRACES = Paths.get("target", "traces");

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
        return parameterContext.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        registerShutdown(context);
        String actor = parameterContext.findAnnotation(Actor.class)
                .map(Actor::value)
                .orElseGet(() -> "page-" + parameterContext.getIndex());
        return registry(context).pageFor(actor).page();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        PageRegistry registry = context.getStore(NAMESPACE).get(PageRegistry.class, PageRegistry.class);
        if (registry != null && context.getExecutionException().isPresent()) {
            capture(context, registry);
        }
        // Closing is left to the store: PageRegistry is AutoCloseable.
    }

    private void capture(ExtensionContext context, PageRegistry registry) {
        String base = context.getRequiredTestClass().getSimpleName() + "."
                + context.getRequiredTestMethod().getName()
                // parameterized and retried invocations share a method name
                + "-" + Integer.toHexString(context.getUniqueId().hashCode());

        registry.forEach((actor, managed) -> {
            String name = base + "-" + actor;

            Path screenshot = SCREENSHOTS.resolve(name + ".png");
            byte[] png = managed.page().screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setPath(screenshot));
            Allure.addAttachment("Screenshot [" + actor + "]", "image/png",
                    new java.io.ByteArrayInputStream(png), ".png");

            Path trace = TRACES.resolve(name + ".zip");
            managed.context().tracing().stop(new Tracing.StopOptions().setPath(trace));
            try (InputStream zip = Files.newInputStream(trace)) {
                Allure.addAttachment("Playwright trace [" + actor + "]", "application/zip", zip, ".zip");
            } catch (IOException e) {
                throw new UncheckedIOException("Could not attach trace " + trace, e);
            }
        });
    }

    private PageRegistry registry(ExtensionContext context) {
        return context.getStore(NAMESPACE)
                .getOrComputeIfAbsent(PageRegistry.class, key -> new PageRegistry(), PageRegistry.class);
    }

    /**
     * Puts a shutdown hook in the root store, which JUnit closes once after the
     * whole run. JUnit has no "after test run" callback; this is the standard
     * workaround, and the one Playwright's own JUnit integration uses.
     */
    private static void registerShutdown(ExtensionContext context) {
        context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(BrowserShutdown.class, key -> new BrowserShutdown(), BrowserShutdown.class);
    }

    private static final class BrowserShutdown implements AutoCloseable {
        @Override
        public void close() {
            PlaywrightFactory.closeAll();
        }
    }
}
