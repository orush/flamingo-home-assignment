package com.flamingo.qa.ui.pages;

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

    public WebTablesPage(Page page) {
        super(page);
    }

    public WebTablesPage open() {
        openPath("/webtables");
        page.locator(ROWS).first().waitFor();
        return this;
    }

    /** Every row currently rendered, in display order. */
    public List<WebTableRecord> records() {
        return page.locator(ROWS).all().stream()
                .map(row -> WebTableRecord.fromCells(row.locator("td").allInnerTexts()))
                .collect(Collectors.toList());
    }

    public List<String> firstNames() {
        return records().stream().map(WebTableRecord::getFirstName).collect(Collectors.toList());
    }

    public RegistrationDialog openAddDialog() {
        page.locator("#addNewRecordButton").click();
        return new RegistrationDialog(page).waitUntilOpen();
    }

    public WebTablesPage addRecord(WebTableRecord record) {
        openAddDialog().fill(record).submitAndWaitUntilClosed();
        rowFor(record.getEmail()).waitFor();
        return this;
    }

    public WebTablesPage editRecord(String email, WebTableRecord updated) {
        rowFor(email).locator("[id^='edit-record-']").click();
        new RegistrationDialog(page).waitUntilOpen().fill(updated).submitAndWaitUntilClosed();
        rowFor(updated.getEmail()).waitFor();
        return this;
    }

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
    public WebTablesPage search(String term) {
        page.locator("#searchBox").fill(term);
        page.waitForFunction(
                "([selector, term]) => [...document.querySelectorAll(selector)]"
                        + ".every(row => row.innerText.toLowerCase().includes(term.toLowerCase()))",
                List.of(ROWS, term));
        return this;
    }

    public WebTablesPage sortBy(String columnHeader) {
        page.getByRole(AriaRole.COLUMNHEADER,
                new Page.GetByRoleOptions().setName(columnHeader).setExact(true)).click();
        return this;
    }

    private Locator rowFor(String email) {
        return page.locator(ROWS).filter(new Locator.FilterOptions().setHasText(email));
    }
}
