package com.flamingo.qa.ui.model;

/** Declared in the order the form lists them. */
public enum Hobby {
    SPORTS("Sports"),
    READING("Reading"),
    MUSIC("Music");

    private final String label;

    Hobby(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
