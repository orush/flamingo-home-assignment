package com.flamingo.qa.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * A react-select dropdown or autocomplete.
 *
 * <p>Options are found by their accessible role and exact name rather than by
 * react-select's generated ids ({@code react-select-3-option-0}), whose numbers
 * depend on the order the widgets happened to mount.
 */
public class ReactSelect {

    private final Page page;
    private final Locator input;

    public ReactSelect(Page page, Locator input) {
        this.page = page;
        this.input = input;
    }

    /** Types the option's text to filter the menu, then picks the exact match. */
    public void choose(String option) {
        input.fill(option);
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true)).click();
    }
}
