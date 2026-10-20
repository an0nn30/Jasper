package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plan 9a: the Settings frame's roles, appended together after plan 4a's, with their default and classic values. */
class SettingsRolesTest {
    /** Each role's default value, then its classic value, in enum order. */
    static final Map<String, List<Integer>> ROLES = new LinkedHashMap<>();
    static {
        ROLES.put("SETTINGS_HEADER_INSET", List.of(20, 14));
        ROLES.put("SETTINGS_NAV_CHILD_INDENT", List.of(0, 15));
        ROLES.put("SETTINGS_NAV_TEXT_GAP", List.of(6, 6));
        ROLES.put("SETTINGS_TRAILING_EMPTY_GAP", List.of(10, 0));
        ROLES.put("KEYMAP_SCOPE_COLUMN", List.of(107, 107));
        ROLES.put("KEYMAP_SHORTCUT_COLUMN", List.of(137, 137));
        ROLES.put("KEYMAP_KEYS_INSET", List.of(25, 25));
        ROLES.put("KEYMAP_SCOPE_GAP", List.of(30, 30));
        ROLES.put("KEYCAP_DETAIL_EDGE_ALPHA", List.of(0, 49));
        ROLES.put("LIST_HEADING_HEIGHT", List.of(0, 24));
        ROLES.put("LIST_TWO_LINE_ROW_HEIGHT", List.of(0, 36));
    }

    @Test void theSettingsRolesFollowEachOtherWithTheirValuesInBothCatalogs() {
        var names = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf(ROLES.keySet().iterator().next());
        assertThat(first).as("appended after plan 4a's roles").isGreaterThan(names.indexOf("DOCK_RULE_HIT"));
        assertThat(names.subList(first, first + ROLES.size())).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, values) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as("default " + name).isEqualTo(values.get(0));
            assertThat(DesignMetrics.CLASSIC.value(role)).as("classic " + name).isEqualTo(values.get(1));
        });
    }
}
