package com.flamingo.qa.junit;

import com.flamingo.qa.ui.PlaywrightFactory;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

/** A page together with the context that owns it, so both can be closed. */
final class ManagedPage {

    private final BrowserContext context;
    private final Page page;

    private ManagedPage(BrowserContext context, Page page) {
        this.context = context;
        this.page = page;
    }

    static ManagedPage create() {
        BrowserContext context = PlaywrightFactory.newContext();
        return new ManagedPage(context, context.newPage());
    }

    BrowserContext context() {
        return context;
    }

    Page page() {
        return page;
    }
}
