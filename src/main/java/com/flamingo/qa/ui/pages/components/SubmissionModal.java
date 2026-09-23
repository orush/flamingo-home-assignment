package com.flamingo.qa.ui.pages.components;

import io.qameta.allure.Step;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.LinkedHashMap;
import java.util.Map;

/** The "Thanks for submitting the form" confirmation and its label/value table. */
public class SubmissionModal {

    /** Long enough for the dialog's fade-out to finish if it were going to close. */
    private static final double CLOSE_SETTLE_MS = 1_500;

    private final Locator dialog;

    public SubmissionModal(Page page) {
        this.dialog = page.getByRole(AriaRole.DIALOG);
    }

    public SubmissionModal waitUntilVisible() {
        dialog.waitFor();
        return this;
    }

    @Step("Close the confirmation")
    public void close() {
        dialog.locator("#closeLargeModal").click();
    }

    /**
     * True if the confirmation is still open once it has had time to close.
     * Checking visibility straight after a click would pass even for a dialog
     * that is about to fade out, so this waits for it to disappear and reports
     * whether it did not.
     */
    public boolean staysOpen() {
        try {
            dialog.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.DETACHED)
                    .setTimeout(CLOSE_SETTLE_MS));
            return false;
        } catch (TimeoutError stillOpen) {
            return true;
        }
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
