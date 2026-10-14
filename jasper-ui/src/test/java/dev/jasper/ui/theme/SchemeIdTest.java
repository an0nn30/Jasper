package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SchemeIdTest {
    @Test void anIdIsAThemeIdAndASchemeKey() {
        var id = new SchemeId("slate-steel", "high-contrast");
        assertThat(id.toString()).isEqualTo("slate-steel/high-contrast");
        assertThat(SchemeId.parse("slate-steel/high-contrast")).contains(id);
        assertThat(SchemeId.validKey("k".repeat(32))).isTrue();
        assertThat(SchemeId.validKey("k".repeat(33))).isFalse();
    }

    @Test void anythingElseIsNotAnId() {
        for (String bad : new String[] {"high-contrast", "slate-steel/", "/dark", "Slate/dark", "slate-steel/dark/x", "a//b", "", null})
            assertThat(SchemeId.parse(bad)).as(bad).isEmpty();
        assertThatThrownBy(() -> new SchemeId("slate-steel", "Dark")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SchemeId("Slate", "dark")).isInstanceOf(IllegalArgumentException.class);
    }
}
