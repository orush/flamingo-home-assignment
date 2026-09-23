package com.flamingo.qa.ui;

import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.PlaywrightExtension;
import com.flamingo.qa.junit.UiTest;
import com.flamingo.qa.ui.model.Hobby;
import com.flamingo.qa.ui.model.StudentRegistration;
import com.flamingo.qa.ui.pages.PracticeFormPage;
import com.flamingo.qa.ui.pages.components.SubmissionModal;
import com.microsoft.playwright.Page;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

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

        assertSoftly(softly -> {
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
        assertThat(form.confirmation().values()).containsExactlyEntriesOf(expectedConfirmation(registration));
    }

    @UiTest
    @Story("Validation")
    @DisplayName("An empty submission is refused and exactly the required fields are flagged")
    void rejectsEmptySubmission(Page page) {
        PracticeFormPage form = new PracticeFormPage(page).open().attemptSubmit();

        assertSoftly(softly -> {
            softly.assertThat(form.confirmationAppears()).isFalse();
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

        assertSoftly(softly -> {
            softly.assertThat(form.confirmationAppears()).as("%s: no confirmation", scenario).isFalse();
            softly.assertThat(form.invalidFields()).as("%s: flagged field", scenario).containsExactly(flaggedField);
        });
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
