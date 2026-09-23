package com.flamingo.qa.ui.pages;

import io.qameta.allure.Step;
import com.flamingo.qa.ui.ExactText;
import com.flamingo.qa.ui.model.Hobby;
import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.pages.components.DatePicker;
import com.flamingo.qa.ui.pages.components.ReactSelect;
import com.flamingo.qa.ui.pages.components.SubmissionModal;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;

/** The student registration form. */
public class PracticeFormPage extends BasePage {

    /** Long enough for the confirmation to have appeared if the form was accepted. */
    private static final double CONFIRMATION_SETTLE_MS = 1_500;

    private final Locator form;

    public PracticeFormPage(Page page) {
        super(page);
        this.form = page.locator("#userForm");
    }

    @Step("Open the practice form")
    public PracticeFormPage open() {
        openPath("/automation-practice-form");
        form.locator("#firstName").waitFor();
        return this;
    }

    /** Enters every non-null field of the registration. */
    @Step("Fill in the registration form")
    public PracticeFormPage fill(StudentRegistration registration) {
        type("#firstName", registration.getFirstName());
        type("#lastName", registration.getLastName());
        type("#userEmail", registration.getEmail());
        if (registration.getGender() != null) {
            clickLabel("gender-radio", registration.getGender().label());
        }
        type("#userNumber", registration.getMobile());
        if (registration.getDateOfBirth() != null) {
            new DatePicker(page, form.locator("#dateOfBirthInput")).pick(registration.getDateOfBirth());
        }
        ReactSelect subjects = new ReactSelect(page, form.locator("#subjectsInput"));
        registration.getSubjects().forEach(subjects::choose);
        for (Hobby hobby : registration.getHobbies()) {
            clickLabel("hobbies-checkbox", hobby.label());
        }
        if (registration.getPicture() != null) {
            form.locator("#uploadPicture").setInputFiles(registration.getPicture());
        }
        type("#currentAddress", registration.getCurrentAddress());
        if (registration.getState() != null) {
            new ReactSelect(page, form.locator("#state input")).choose(registration.getState());
        }
        if (registration.getCity() != null) {
            new ReactSelect(page, form.locator("#city input")).choose(registration.getCity());
        }
        return this;
    }

    /** Submits a form expected to be accepted, and returns the confirmation. */
    public SubmissionModal submit() {
        attemptSubmit();
        return confirmation().waitUntilVisible();
    }

    /** Clicks submit without assuming the outcome. */
    @Step("Submit the form")
    public PracticeFormPage attemptSubmit() {
        form.locator("#submit").click();
        return this;
    }

    /**
     * True if the confirmation appears. Waits a bounded time rather than checking
     * once, so a refused submission is not mistaken for one still rendering.
     */
    public boolean confirmationAppears() {
        try {
            page.getByRole(AriaRole.DIALOG).waitFor(
                    new Locator.WaitForOptions().setTimeout(CONFIRMATION_SETTLE_MS));
            return true;
        } catch (TimeoutError notShown) {
            return false;
        }
    }

    public SubmissionModal confirmation() {
        return new SubmissionModal(page);
    }

    /** True once the form has run its validation and is showing the result. */
    public boolean showsValidationErrors() {
        return String.valueOf(form.getAttribute("class")).contains("was-validated");
    }

    /**
     * Fields the browser's validation flags as invalid. A radio group counts
     * once, under its name.
     */
    @SuppressWarnings("unchecked")
    public List<String> invalidFields() {
        return (List<String>) form.evaluate("form => [...new Set([...form.querySelectorAll(':invalid')]"
                + ".map(field => field.type === 'radio' ? field.name : field.id))]");
    }

    private void type(String selector, String value) {
        if (value != null) {
            form.locator(selector).fill(value);
        }
    }

    /**
     * Radios and checkboxes here are styled controls whose label sits over the
     * input, so the label is what a user — and the test — clicks.
     */
    private void clickLabel(String inputIdPrefix, String text) {
        form.locator("label[for^='" + inputIdPrefix + "-']")
                .filter(new Locator.FilterOptions().setHasText(ExactText.of(text)))
                .click();
    }
}
