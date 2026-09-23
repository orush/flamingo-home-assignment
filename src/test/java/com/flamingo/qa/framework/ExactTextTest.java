package com.flamingo.qa.framework;

import com.flamingo.qa.ui.ExactText;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("framework")
@Epic("Framework")
@Feature("UI plumbing")
class ExactTextTest {

    @Test
    @DisplayName("Never emits Java-only \\Q...\\E quoting, which JavaScript would misread")
    void usesNoJavaOnlyQuoting() {
        assertThat(ExactText.of("Female").pattern()).isEqualTo("^Female$");
    }

    @Test
    @DisplayName("Escapes metacharacters so they match literally")
    void escapesMetacharacters() {
        assertSoftly(softly -> {
            softly.assertThat(ExactText.of("C++ (Advanced) $5.00?").matcher("C++ (Advanced) $5.00?").matches()).isTrue();
            softly.assertThat(ExactText.of("a.c").matcher("abc").matches()).isFalse();
        });
    }

    @Test
    @DisplayName("Matches the whole text, not a fragment of it")
    void anchorsBothEnds() {
        assertSoftly(softly -> {
            softly.assertThat(ExactText.of("Music").matcher("Music").matches()).isTrue();
            softly.assertThat(ExactText.of("Music").matcher("Musicals").find()).isFalse();
        });
    }
}
