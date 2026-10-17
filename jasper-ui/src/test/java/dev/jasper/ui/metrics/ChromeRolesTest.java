package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plans 6a and 6b: the classic chrome's roles, measured on the Enterprise mocks, the same in both catalogs. */
class ChromeRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        Object[][] rows = {{"MENU_BAR_SIDE", 5}, {"MENU_BAR_ITEM_PADDING", 9}, {"MENU_BAR_HEIGHT", 24}, {"CHROME_BUTTON_PADDING", 8},
            {"CHROME_BUTTON_GAP", 8}, {"CHROME_SEPARATOR_GAP", 10}, {"CHROME_SEPARATOR_INSET", 8}, {"CHROME_GLYPH", 7},
            {"CHROME_GLYPH_GAP", 6}, {"CHROME_CARET", 5}, {"CHROME_CARET_GAP", 7}, {"CHROME_TRAILING_GAP", 6},
            {"TOOLBAR_SEARCH_WIDTH", 300}, {"TOOLBAR_SEARCH_PADDING", 8}, {"HINT_KEYCAP_HEIGHT", 16}, {"HINT_KEYCAP_PADDING", 4},
            {"CLASSIC_TAB_LEADING", 12}, {"CLASSIC_TAB_GAP", 9}, {"CLASSIC_TAB_TRAILING", 10}, {"CLASSIC_TAB_PLUS_INSET", 11},
            {"CLASSIC_TAB_MIN_LABEL", 24}, {"STATUS_CELL_PADDING", 10}, {"STATUS_CELL_DOT_GAP", 7}, {"RUN_GLYPH_GAP", 5},
            {"DIALOG_RADIUS", 4}, {"DIALOG_HEADER_HEIGHT", 26}, {"DIALOG_HEADER_INSET", 10}, {"DIALOG_SWATCH", 8},
            {"DIALOG_SWATCH_GAP", 7}, {"DIALOG_FOOTER_VERTICAL", 8}};
        for (Object[] row : rows) ROLES.put((String) row[0], (Integer) row[1]);
    }

    @Test void theChromeRolesFollowPlanFoursRolesInOrderWithTheirMeasuredValuesInBothCatalogs() {
        var names = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf("MENU_BAR_SIDE");
        assertThat(first).as("appended after plan 4a's roles").isGreaterThan(names.indexOf("DOCK_RULE_HIT"));
        assertThat(names.subList(first, first + ROLES.size())).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as("default " + name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as("classic " + name).isEqualTo(value);
        });
    }
}
