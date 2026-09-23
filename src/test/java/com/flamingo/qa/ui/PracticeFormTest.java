package com.flamingo.qa.ui;

import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.PlaywrightExtension;
import com.flamingo.qa.junit.UiTest;
import com.flamingo.qa.ui.model.Hobby;
import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.pages.PracticeFormPage;
import com.flamingo.qa.ui.pages.components.SubmissionModal;
import com.microsoft.playwright.Page;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.flamingo.qa.ui.Eventually.eventually;
import static com.flamingo.qa.ui.Eventually.eventuallySoftly;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("DemoQA")
@Feature("Practice form")
class PracticeFormTest {

    /** How the confirmation renders a date: zero-padded day, no space after the comma. */
    private static final DateTimeFormatter CONFIRMATION_DATE =
            DateTimeFormatter.ofPattern("dd MMMM,yyyy", Locale.ENGLISH);

    @UiTest
    @Story("Submission")
    @DisplayName("A complete registration is accepted and every value is echoed in the confirmation")
    void submitsCompleteRegistration(Page page) {
        StudentRegistration registration = TestDataFactory.completeRegistration();

        SubmissionModal confirmation = new PracticeFormPage(page)
                .open()
                .fill(registration)
                .submit();

        eventuallySoftly(softly -> {
            softly.assertThat(confirmation.title()).isEqualTo("Thanks for submitting the form");
            softly.assertThat(confirmation.values()).containsExactlyEntriesOf(expectedConfirmation(registration));
        });
    }

    @UiTest
    @Story("Submission")
    @DisplayName("Only the required fields are needed; the others are left blank and the date defaults to today")
    void acceptsRegistrationWithOnlyRequiredFields(Page page) {
        StudentRegistration registration = TestDataFactory.minimalRegistration();

        PracticeFormPage form = new PracticeFormPage(page).open().fill(registration).attemptSubmit();

        assertThat(form.confirmationAppears()).isTrue();
        eventually(() -> assertThat(form.confirmation().values())
                .containsExactlyEntriesOf(expectedConfirmation(registration)));
    }

    @UiTest
    @Story("Validation")
    @DisplayName("An empty submission is refused and exactly the required fields are flagged")
    void rejectsEmptySubmission(Page page) {
        PracticeFormPage form = new PracticeFormPage(page).open().attemptSubmit();

        // Hard and outside the retry: it is a bounded wait of its own.
        assertThat(form.confirmationAppears()).isFalse();
        eventuallySoftly(softly -> {
            softly.assertThat(form.showsValidationErrors()).isTrue();
            softly.assertThat(form.invalidFields())
                    .containsExactlyInAnyOrder("firstName", "lastName", "gender", "userNumber");
        });
    }

    @Tag("ui")
    @ExtendWith(PlaywrightExtension.class)
    @Story("Validation")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "mobile with nine digits,   mobile,   555000123,  userNumber",
            "mobile with letters,       mobile,   55500abcde, userNumber",
            "email without a domain,    email,    grace@,     userEmail",
            "missing last name,         lastName, '',         lastName"
    })
    void rejectsInvalidField(String scenario, String field, String value, String flaggedField, Page page) {
        StudentRegistration invalid = TestDataFactory.minimalRegistration().with(field, value);

        PracticeFormPage form = new PracticeFormPage(page).open().fill(invalid).attemptSubmit();

        // Hard and outside the retry: it is a bounded wait of its own.
        assertThat(form.confirmationAppears()).as("%s: no confirmation", scenario).isFalse();
        eventually(() -> assertThat(form.invalidFields())
                .as("%s: flagged field", scenario)
                .containsExactly(flaggedField));
    }

    // ===============================================================
    // FINDING — asserts correct behaviour and therefore FAILS.
    // ===============================================================

    @UiTest
    @Tag("finding")
    @Story("Submission")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: the confirmation's Close button must close the dialog")
    @Description("After a registration is accepted, clicking Close in the confirmation does nothing: "
            + "the dialog and its backdrop stay over the form. Only Escape closes it.")
    void closeButtonClosesConfirmation(Page page) {
        SubmissionModal confirmation = new PracticeFormPage(page)
                .open()
                .fill(TestDataFactory.completeRegistration())
                .submit();

        confirmation.close();

        // Hard and outside the retry: it is a bounded wait of its own.
        assertThat(confirmation.staysOpen())
                .as("after a complete registration is submitted, clicking Close in the confirmation must "
                        + "close it; the button is visible, enabled and receives the click, but the dialog "
                        + "and its backdrop stay over the form, so the user cannot get back to it without "
                        + "pressing Escape")
                .isFalse();
    }

    /**
     * The confirmation a correct form shows for a registration. Kept in the test,
     * not the page object: the page reports what is displayed, the test decides
     * what should be.
     */
    private static Map<String, String> expectedConfirmation(StudentRegistration r) {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Student Name", r.getFirstName() + " " + r.getLastName());
        expected.put("Student Email", orEmpty(r.getEmail()));
        expected.put("Gender", r.getGender().label());
        expected.put("Mobile", r.getMobile());
        expected.put("Date of Birth", CONFIRMATION_DATE.format(
                r.getDateOfBirth() != null ? r.getDateOfBirth() : LocalDate.now()));
        expected.put("Subjects", String.join(", ", r.getSubjects()));
        expected.put("Hobbies", r.getHobbies().stream().map(Hobby::label).collect(Collectors.joining(", ")));
        expected.put("Picture", r.getPicture() == null ? "" : r.getPicture().getFileName().toString());
        expected.put("Address", orEmpty(r.getCurrentAddress()));
        expected.put("State and City", r.getState() == null ? "" : r.getState() + " " + r.getCity());
        return expected;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
