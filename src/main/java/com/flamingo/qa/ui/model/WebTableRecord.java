package com.flamingo.qa.ui.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** One row of the DemoQA web table. Every field is a string, exactly as the UI renders it. */
@Value
@Builder(toBuilder = true)
public class WebTableRecord {
    String firstName;
    String lastName;
    String age;
    String email;
    String salary;
    String department;

    /** Builds a record from a row's cells, in column order. */
    public static WebTableRecord fromCells(List<String> cells) {
        if (cells.size() < 6) {
            throw new IllegalArgumentException("Expected at least 6 cells, got " + cells);
        }
        return builder()
                .firstName(cells.get(0).trim())
                .lastName(cells.get(1).trim())
                .age(cells.get(2).trim())
                .email(cells.get(3).trim())
                .salary(cells.get(4).trim())
                .department(cells.get(5).trim())
                .build();
    }

    /** The values in column order, as they appear in the row. */
    public List<String> cells() {
        return List.of(firstName, lastName, age, email, salary, department);
    }

    /** A copy with one field replaced, named as in the table. For data-driven tests. */
    public WebTableRecord with(String field, String value) {
        switch (field) {
            case "firstName": return toBuilder().firstName(value).build();
            case "lastName": return toBuilder().lastName(value).build();
            case "age": return toBuilder().age(value).build();
            case "email": return toBuilder().email(value).build();
            case "salary": return toBuilder().salary(value).build();
            case "department": return toBuilder().department(value).build();
            default: throw new IllegalArgumentException("Unknown field: " + field);
        }
    }
}
