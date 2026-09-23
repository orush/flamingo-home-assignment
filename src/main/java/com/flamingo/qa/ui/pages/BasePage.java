package com.flamingo.qa.ui.pages;

import com.flamingo.qa.config.Config;
import com.microsoft.playwright.Page;

/**
 * Shared page behaviour.
 *
 * <p>Page objects expose actions and data. They never assert: every check lives
 * in a test, so a page object can serve positive and negative tests alike.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    protected void openPath(String path) {
        page.navigate(Config.uiBaseUrl() + path);
    }
}
