package com.flamingo.qa.ui.pages.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;

import java.time.LocalDate;

/**
 * A react-datepicker, driven through its month and year selects and a day click.
 *
 * <p>After a day is picked, the widget closes and then, about ten milliseconds
 * later, moves focus back to its own input. Whatever the test focuses in that
 * window loses focus — measured, and reproducible every time. A react-select
 * clears its text on blur, so typing a subject straight after picking a date
 * silently produced an empty field. {@link #pick} therefore waits for that
 * focus return before handing control back.
 */
public class DatePicker {

    private static final double FOCUS_RETURN_TIMEOUT_MS = 2_000;

    private final Page page;
    private final Locator input;

    public DatePicker(Page page, Locator input) {
        this.page = page;
        this.input = input;
    }

    public void pick(LocalDate date) {
        input.click();
        Locator calendar = page.locator(".react-datepicker");
        calendar.locator(".react-datepicker__month-select")
                .selectOption(String.valueOf(date.getMonthValue() - 1)); // options are 0-based
        calendar.locator(".react-datepicker__year-select")
                .selectOption(String.valueOf(date.getYear()));
        // Day classes are zero-padded to three digits; excluding outside-month
        // days stops a click landing on the same number in a neighbouring month.
        calendar.locator(String.format(
                ".react-datepicker__day--%03d:not(.react-datepicker__day--outside-month)",
                date.getDayOfMonth())).click();
        awaitFocusReturn();
    }

    private void awaitFocusReturn() {
        String inputId = input.getAttribute("id");
        try {
            page.waitForFunction("id => document.activeElement !== null && document.activeElement.id === id",
                    inputId, new Page.WaitForFunctionOptions().setTimeout(FOCUS_RETURN_TIMEOUT_MS));
        } catch (TimeoutError e) {
            throw new IllegalStateException("The date picker no longer returns focus to #" + inputId
                    + " after a pick. DatePicker.pick() waits for that to avoid a focus race;"
                    + " the widget's behaviour has changed and the wait needs revisiting.", e);
        }
    }
}
