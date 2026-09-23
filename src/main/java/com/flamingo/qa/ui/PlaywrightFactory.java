package com.flamingo.qa.ui;

import com.flamingo.qa.config.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates isolated browser contexts.
 *
 * <p>Playwright objects are unsynchronised, so they must never be used from two
 * threads at once. Each worker thread therefore gets its own {@code Playwright}
 * and {@code Browser}, reused across that thread's tests, and each test gets its
 * own {@code BrowserContext} — cheap, and fully isolated in cookies and storage.
 *
 * <p>Every {@code Playwright} created is also recorded, so {@link #closeAll()} can
 * shut them down once the run has finished. Playwright's own JUnit integration
 * uses the same pattern.
 */
public final class PlaywrightFactory {

    private static final List<Playwright> CREATED = new CopyOnWriteArrayList<>();

    private static final ThreadLocal<Playwright> PLAYWRIGHT = ThreadLocal.withInitial(() -> {
        Playwright playwright = Playwright.create();
        CREATED.add(playwright);
        return playwright;
    });

    private static final ThreadLocal<Browser> BROWSER = ThreadLocal.withInitial(() ->
            browserType(PLAYWRIGHT.get())
                    .launch(new BrowserType.LaunchOptions().setHeadless(Config.headless())));

    private PlaywrightFactory() {
    }

    /** A fresh context: first-party traffic only, default timeout set, tracing on. */
    public static BrowserContext newContext() {
        BrowserContext context = BROWSER.get().newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080));
        context.setDefaultTimeout(Config.timeoutMillis());
        ThirdPartyBlocker.install(context, URI.create(Config.uiBaseUrl()).getHost());
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        return context;
    }

    /**
     * Closes every Playwright instance, and with it every browser, created by any
     * thread. Call once, after all tests have finished: nothing else may be using
     * them, which is what makes a cross-thread close safe for these objects.
     */
    public static void closeAll() {
        CREATED.forEach(Playwright::close);
        CREATED.clear();
    }

    private static BrowserType browserType(Playwright playwright) {
        String name = Config.browser();
        switch (name.toLowerCase()) {
            case "chromium":
                return playwright.chromium();
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            default:
                throw new IllegalArgumentException(
                        "Unsupported browser '" + name + "'; use chromium, firefox or webkit");
        }
    }
}
