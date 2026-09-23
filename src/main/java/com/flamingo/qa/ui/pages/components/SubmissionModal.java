package com.flamingo.qa.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The "Thanks for submitting the form" confirmation and its label/value table. */
public class SubmissionModal {

    private final Locator dialog;

    public SubmissionModal(Page page) {
        this.dialog = page.getByRole(AriaRole.DIALOG);
    }

    public SubmissionModal waitUntilVisible() {
        dialog.waitFor();
        return this;
    }

    public String title() {
        return dialog.locator(".modal-title").innerText().trim();
    }

    /** Each row's label mapped to its value, in display order. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Locator row : dialog.locator("tbody tr").all()) {
            List<String> cells = row.locator("td").allInnerTexts();
            values.put(cells.get(0).trim(), cells.get(1).trim());
        }
        return values;
    }
}
