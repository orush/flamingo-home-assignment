package com.flamingo.qa.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.LinkedHashMap;
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

    /**
     * Each row's label mapped to its value, in display order. Every row and cell
     * is waited on until visible before it is read, so a table still rendering
     * is not read half-drawn.
     */
    public Map<String, String> values() {
        Locator rows = dialog.locator("tbody tr");
        rows.first().waitFor();
        Map<String, String> values = new LinkedHashMap<>();
        for (Locator row : rows.all()) {
            row.waitFor();
            values.put(visibleText(row.locator("td").nth(0)), visibleText(row.locator("td").nth(1)));
        }
        return values;
    }

    private static String visibleText(Locator cell) {
        cell.waitFor();
        return cell.innerText().trim();
    }
}
