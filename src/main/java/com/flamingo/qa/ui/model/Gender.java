package com.flamingo.qa.ui.model;

public enum Gender {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    /** The text shown on the form and in the confirmation. */
    public String label() {
        return label;
    }
}
