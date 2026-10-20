package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise plan 7: the dock chrome's roles, measured on the Workstation mock, the same in both catalogs. */
class DockRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        Object[][] rows = {{"STRIPE_PILL_HEIGHT", 14}, {"STRIPE_PILL_WIDTH", 16}, {"STRIPE_PILL_PADDING", 5}, {"STRIPE_PILL_GAP", 5},
            {"TOOL_HEADER_ACTION", 16}, {"HEADER_STRIP_TITLE_GAP", 8}, {"HEADER_STRIP_DOT_GAP", 6}, {"HEADER_STRIP_COUNT_GAP", 6},
            {"BOTTOM_STRIP_HEIGHT", 24}};
        for (Object[] row : rows) ROLES.put((String) row[0], (Integer) row[1]);
    }

    @Test void theDockRolesFollowTheChromeRolesInOrderWithTheirMeasuredValuesInBothCatalogs() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf("STRIPE_PILL_HEIGHT");
        assertThat(first).as("appended after plan 6b's chrome roles").isGreaterThan(names.indexOf("DIALOG_FOOTER_VERTICAL"));
        assertThat(names.subList(first, first + ROLES.size())).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as("default " + name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as("classic " + name).isEqualTo(value);
        });
    }
}
