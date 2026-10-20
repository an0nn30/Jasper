package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SchemeIdTest {
    @Test void anIdIsAThemeIdAndASchemeKey() {
        var id = new SchemeId("slate-cobalt", "high-contrast");
        assertThat(id.toString()).isEqualTo("slate-cobalt/high-contrast");
        assertThat(SchemeId.parse("slate-cobalt/high-contrast")).contains(id);
        assertThat(SchemeId.validKey("k".repeat(32))).isTrue();
        assertThat(SchemeId.validKey("k".repeat(33))).isFalse();
    }

    @Test void anythingElseIsNotAnId() {
        for (String bad : new String[] {"high-contrast", "slate-cobalt/", "/dark", "Slate/dark", "slate-cobalt/dark/x", "a//b", "", null})
            assertThat(SchemeId.parse(bad)).as(bad).isEmpty();
        assertThatThrownBy(() -> new SchemeId("slate-cobalt", "Dark")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SchemeId("Slate", "dark")).isInstanceOf(IllegalArgumentException.class);
    }
}
