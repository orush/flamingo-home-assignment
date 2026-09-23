package com.flamingo.qa.ui.pages;

import io.qameta.allure.Step;
import com.flamingo.qa.ui.ExactText;
import com.flamingo.qa.ui.model.WebTableRecord;
import com.flamingo.qa.ui.pages.components.RegistrationDialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;
import java.util.stream.Collectors;

public class WebTablesPage extends BasePage {

    private static final String ROWS = "table tbody tr";
    private static final String HEADERS = "table thead th";
    private static final String EMAIL = "Email";

    public WebTablesPage(Page page) {
        super(page);
    }

    @Step("Open the web tables page")
    public WebTablesPage open() {
        openPath("/webtables");
        page.locator(ROWS).first().waitFor();
        return this;
    }

    /** Every row currently rendered, in display order, read from its visible cells. */
    public List<WebTableRecord> records() {
        return page.locator(ROWS).all().stream()
                .map(row -> WebTableRecord.fromCells(row.locator("td")
                        .filter(new Locator.FilterOptions().setVisible(true))
                        .allInnerTexts()))
                .collect(Collectors.toList());
    }

    public List<String> firstNames() {
        return records().stream().map(WebTableRecord::getFirstName).collect(Collectors.toList());
    }

    @Step("Open the add-record dialog")
    public RegistrationDialog openAddDialog() {
        page.locator("#addNewRecordButton").click();
        return new RegistrationDialog(page).waitUntilOpen();
    }

    @Step("Add record {record.email}")
    public WebTablesPage addRecord(WebTableRecord record) {
        openAddDialog().fill(record).submitAndWaitUntilClosed();
        rowFor(record.getEmail()).waitFor();
        return this;
    }

    @Step("Edit record {email}")
    public WebTablesPage editRecord(String email, WebTableRecord updated) {
        rowFor(email).locator("[id^='edit-record-']").click();
        new RegistrationDialog(page).waitUntilOpen().fill(updated).submitAndWaitUntilClosed();
        rowFor(updated.getEmail()).waitFor();
        return this;
    }

    @Step("Delete record {email}")
    public WebTablesPage deleteRecord(String email) {
        Locator row = rowFor(email);
        row.locator("[id^='delete-record-']").click();
        row.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
        return this;
    }

    /**
     * Types into the search box and waits until every rendered row contains the
     * term, so callers read the filtered table rather than a mid-update one.
     */
    @Step("Search for '{term}'")
    public WebTablesPage search(String term) {
        page.locator("#searchBox").fill(term);
        page.waitForFunction(
                "([selector, term]) => [...document.querySelectorAll(selector)]"
                        + ".every(row => row.innerText.toLowerCase().includes(term.toLowerCase()))",
                List.of(ROWS, term));
        return this;
    }

    @Step("Sort by '{columnHeader}'")
    public WebTablesPage sortBy(String columnHeader) {
        page.getByRole(AriaRole.COLUMNHEADER,
                new Page.GetByRoleOptions().setName(columnHeader).setExact(true)).click();
        return this;
    }

    /** The row whose Email cell is exactly {@code email}; other columns are not searched. */
    private Locator rowFor(String email) {
        return rowWhere(EMAIL, email);
    }

    private Locator rowWhere(String columnHeader, String value) {
        Locator cell = page.locator("td:nth-child(" + (columnIndex(columnHeader) + 1) + ")")
                .filter(new Locator.FilterOptions().setHasText(ExactText.of(value)));
        return page.locator(ROWS).filter(new Locator.FilterOptions().setHas(cell));
    }

    /** Zero-based position of the column under {@code columnHeader}, read from the rendered header. */
    private int columnIndex(String columnHeader) {
        List<String> headers = page.locator(HEADERS).allInnerTexts().stream()
                .map(String::trim)
                .collect(Collectors.toList());
        int index = headers.indexOf(columnHeader);
        if (index < 0) {
            throw new IllegalStateException("No column '" + columnHeader + "' in " + headers);
        }
        return index;
    }
}
