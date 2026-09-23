package com.flamingo.qa.ui.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * What a student enters on the practice form. A null field is left untouched:
 * optional fields stay empty, and a null date keeps the form's default of today.
 */
@Value
@Builder(toBuilder = true)
public class StudentRegistration {
    String firstName;
    String lastName;
    String email;
    Gender gender;
    String mobile;
    LocalDate dateOfBirth;
    @Singular List<String> subjects;
    @Singular List<Hobby> hobbies;
    Path picture;
    String currentAddress;
    String state;
    String city;

    /** A copy with one text field replaced, named as in the form. For data-driven tests. */
    public StudentRegistration with(String field, String value) {
        switch (field) {
            case "firstName": return toBuilder().firstName(value).build();
            case "lastName": return toBuilder().lastName(value).build();
            case "email": return toBuilder().email(value).build();
            case "mobile": return toBuilder().mobile(value).build();
            case "currentAddress": return toBuilder().currentAddress(value).build();
            default: throw new IllegalArgumentException("Unknown or non-text field: " + field);
        }
    }
}
