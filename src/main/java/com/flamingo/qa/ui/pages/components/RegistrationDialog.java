package com.flamingo.qa.ui.pages.components;

import com.flamingo.qa.ui.model.WebTableRecord;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

/** The add/edit record dialog on the web tables page. */
public class RegistrationDialog {

    /** Long enough for the dialog's fade-out to finish if it were going to close. */
    private static final double CLOSE_SETTLE_MS = 1_500;

    private final Page page;
    private final Locator dialog;

    public RegistrationDialog(Page page) {
        this.page = page;
        this.dialog = page.getByRole(AriaRole.DIALOG);
    }

    public RegistrationDialog waitUntilOpen() {
        dialog.waitFor();
        return this;
    }

    /** Replaces every field with the record's values. */
    public RegistrationDialog fill(WebTableRecord record) {
        dialog.locator("#firstName").fill(record.getFirstName());
        dialog.locator("#lastName").fill(record.getLastName());
        dialog.locator("#userEmail").fill(record.getEmail());
        dialog.locator("#age").fill(record.getAge());
        dialog.locator("#salary").fill(record.getSalary());
        dialog.locator("#department").fill(record.getDepartment());
        return this;
    }

    public void submit() {
        dialog.locator("#submit").click();
    }

    public void submitAndWaitUntilClosed() {
        submit();
        dialog.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    /**
     * True if the dialog is still open once it has had time to close. Checking
     * visibility straight after a submit would pass even for a dialog that is
     * about to fade out, so this waits for it to disappear and reports whether it
     * did not.
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

    /** Ids of the inputs the browser's form validation currently flags as invalid. */
    @SuppressWarnings("unchecked")
    public List<String> invalidFields() {
        return (List<String>) dialog.locator("input:invalid").evaluateAll("inputs => inputs.map(i => i.id)");
    }
}
