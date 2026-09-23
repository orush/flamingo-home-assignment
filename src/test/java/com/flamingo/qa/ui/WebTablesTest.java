package com.flamingo.qa.ui;

import com.flamingo.qa.data.TestDataFactory;
import com.flamingo.qa.junit.PlaywrightExtension;
import com.flamingo.qa.junit.UiTest;
import com.flamingo.qa.ui.model.WebTableRecord;
import com.flamingo.qa.ui.pages.WebTablesPage;
import com.flamingo.qa.ui.pages.components.RegistrationDialog;
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

import java.util.List;

import static com.flamingo.qa.ui.Eventually.eventually;
import static com.flamingo.qa.ui.Eventually.eventuallySoftly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Web tables: add, edit, delete, search and sort.
 *
 * <p>Every test opens a fresh browser context, and the table's data lives in the
 * page, so each test starts from the site's seed rows. Tests read the seed rows
 * at runtime rather than hard-coding them, so a change to the demo data does not
 * break them.
 */
@Epic("DemoQA")
@Feature("Web tables")
class WebTablesTest {

    @UiTest
    @Story("Add")
    @DisplayName("A new record is appended to the table with exactly the values entered")
    void addsRecord(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        List<WebTableRecord> before = tables.records();
        WebTableRecord record = TestDataFactory.randomWebTableRecord();

        tables.addRecord(record);

        eventually(() -> assertThat(tables.records())
                .hasSize(before.size() + 1)
                .containsAll(before)
                .endsWith(record));
    }

    @UiTest
    @Story("Edit")
    @DisplayName("Editing an existing record updates that row and leaves the others untouched")
    void editsExistingRecord(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        List<WebTableRecord> before = tables.records();
        WebTableRecord original = before.get(0);
        WebTableRecord edited = original.toBuilder()
                .firstName("Augusta")
                .salary("150000")
                .department("Research")
                .build();

        tables.editRecord(original.getEmail(), edited);

        eventually(() -> {
            List<WebTableRecord> after = tables.records();
            // Hard, because subList below throws on a table that lost its rows.
            assertThat(after).hasSameSizeAs(before);
            assertSoftly(softly -> {
                softly.assertThat(after).contains(edited).doesNotContain(original);
                softly.assertThat(after.subList(1, after.size()))
                        .as("rows other than the edited one are unchanged")
                        .isEqualTo(before.subList(1, before.size()));
            });
        });
    }

    @UiTest
    @Story("Delete")
    @DisplayName("Deleting an existing record removes exactly that row")
    void deletesExistingRecord(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        List<WebTableRecord> before = tables.records();
        WebTableRecord doomed = before.get(before.size() - 1);

        tables.deleteRecord(doomed.getEmail());

        eventually(() -> assertThat(tables.records())
                .hasSize(before.size() - 1)
                .doesNotContain(doomed)
                .containsExactlyElementsOf(before.subList(0, before.size() - 1)));
    }

    @UiTest
    @Story("Search")
    @DisplayName("Search is case-insensitive, matches any column, and narrows the table to matches")
    void searchNarrowsToMatchingRecords(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        WebTableRecord target = tables.records().get(1);
        // Search on the department, in a different case, to exercise both
        // cross-column matching and case-insensitivity.
        String term = target.getDepartment().toLowerCase();

        tables.search(term);

        eventuallySoftly(softly -> {
            List<WebTableRecord> matches = tables.records();
            softly.assertThat(matches).contains(target);
            softly.assertThat(matches).allSatisfy(record ->
                    assertThat(record.cells()).anySatisfy(cell ->
                            assertThat(cell).containsIgnoringCase(term)));
        });
    }

    @UiTest
    @Story("Search")
    @DisplayName("A search with no match empties the table, and clearing it restores every row")
    void searchWithNoMatchEmptiesTableUntilCleared(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        List<WebTableRecord> all = tables.records();

        // Sequential rather than soft: the second check needs the first search undone.
        tables.search("no-such-record-zzz");
        eventually(() -> assertThat(tables.records()).as("no match").isEmpty());
        tables.search("");
        eventually(() -> assertThat(tables.records()).as("cleared").isEqualTo(all));
    }

    @Tag("ui")
    @ExtendWith(PlaywrightExtension.class)
    @Story("Validation")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "email without a domain,        email,     not-an-email, userEmail",
            "non-numeric age,               age,       thirty,       age",
            "non-numeric salary,            salary,    lots,         salary",
            "missing first name,            firstName, '',           firstName"
    })
    void rejectsInvalidRecord(String scenario, String field, String value, String invalidInput,
                              Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        int rowsBefore = tables.records().size();
        WebTableRecord invalid = TestDataFactory.randomWebTableRecord().with(field, value);

        RegistrationDialog dialog = tables.openAddDialog();
        dialog.fill(invalid);
        dialog.submit();

        // Hard and outside the retry: it is a bounded wait of its own.
        assertThat(dialog.staysOpen()).as("%s: dialog stays open", scenario).isTrue();
        eventuallySoftly(softly -> {
            softly.assertThat(dialog.invalidFields()).as("%s: flagged field", scenario).containsExactly(invalidInput);
            softly.assertThat(tables.records()).as("%s: nothing added", scenario).hasSize(rowsBefore);
        });
    }

    // ===============================================================
    // FINDING — asserts correct behaviour and therefore FAILS.
    // ===============================================================

    @UiTest
    @Tag("finding")
    @Story("Sort")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("FINDING: clicking a column header must sort the rows by that column")
    @Description("The brief requires sorting validation. On the current site no column header "
            + "has a click handler, and the row order never changes.")
    void clickingColumnHeaderSortsRows(Page page) {
        WebTablesPage tables = new WebTablesPage(page).open();
        // Append a record that sorts first, so the table is guaranteed to start
        // unsorted and the assertion below cannot pass by luck of the seed data.
        tables.addRecord(TestDataFactory.randomWebTableRecord().toBuilder().firstName("Aaron").build());
        List<String> before = tables.firstNames();

        tables.sortBy("First Name");

        eventually(() -> {
            List<String> after = tables.firstNames();
            assertThat(after)
                    .as("clicking 'First Name' must sort rows ascending; order before the click was %s, "
                            + "after it %s — the column header does not respond to clicks", before, after)
                    .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        });
    }
}
