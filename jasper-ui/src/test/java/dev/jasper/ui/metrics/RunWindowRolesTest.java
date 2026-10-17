package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise presentation plan 8a: the Run window's geometry and type, measured on the 06 Run Dialog mocks. */
class RunWindowRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        String[] rows = {"RADIUS 4", "TITLE_HEIGHT 24", "TITLE_SIDE 10", "CLOSE 16", "CLOSE_SIDE 5", "SHORTCUT_GAP 10", "SIDE 12",
            "HELP_TOP 10", "TILE 32", "TILE_GLYPH 13", "TILE_GAP 10", "HELP_LINE 18", "FIELD_TOP 11", "LABEL_COLUMN 48", "FIELD_SIDE 6",
            "MENU_WIDTH 20", "LIST_TOP 10", "ROW_HEIGHT 21", "HEADING_HEIGHT 18", "ROW_SIDE 8", "SWATCH 7", "ROW_GAP 7", "DETAIL_GAP 7",
            "DETAIL_TOP 10", "DETAIL_LABEL_GAP 6", "CHECK_TOP 10", "BOTTOM 10", "FOOTER_HEIGHT 40", "BUTTON_WIDTH 70", "BUTTON_GAP 6"};
        for (String row : rows) ROLES.put("RUN_WINDOW_" + row.split(" ")[0], Integer.parseInt(row.split(" ")[1]));
    }

    /** One block in order (later plans may append their own roles after it), with the mocks' values in both catalogs. */
    @Test void theRunWindowRolesAreOneBlockWithTheMocksValuesInBothCatalogs() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int start = names.indexOf("RUN_WINDOW_RADIUS");
        assertThat(start).as("the block's first role").isNotNegative();
        assertThat(names.subList(start, Math.min(names.size(), start + ROLES.size()))).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as(name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as(name + " (only the Run window reads it)").isEqualTo(value);
        });
    }

    /** Plan 8b: the home's insets, measured on the mocks, follow 5b's help-text role, equal in both catalogs. */
    @Test void theHomeInsetsAreTheMocksSixAndFive() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int at = names.indexOf("RUN_WINDOW_HELP_TEXT_TOP");
        assertThat(names.subList(at + 1, at + 3)).containsExactly("RUN_WINDOW_HOME_SIDE", "RUN_WINDOW_HOME_BOTTOM");
        for (var catalog : java.util.List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            assertThat(catalog.value(DesignMetrics.Role.RUN_WINDOW_HOME_SIDE)).isEqualTo(6);
            assertThat(catalog.value(DesignMetrics.Role.RUN_WINDOW_HOME_BOTTOM)).isEqualTo(5);
        }
    }

    @Test void theRunWindowTypeIsSemiboldHeadingsAndASmallDataFace() {
        assertThat(TypeScale.CLASSIC.size(Typography.Role.RUN_HEADING)).isEqualTo(11f);
        assertThat(TypeScale.CLASSIC.weight(Typography.Role.RUN_HEADING)).isEqualTo(600);
        assertThat(TypeScale.CLASSIC.size(Typography.Role.RUN_DETAIL)).isEqualTo(10.5f);
        assertThat(TypeScale.CLASSIC.weight(Typography.Role.RUN_DETAIL)).isEqualTo(400);
        assertThat(TypeScale.DEFAULT.size(Typography.Role.RUN_HEADING)).isEqualTo(12f);
        assertThat(TypeScale.DEFAULT.size(Typography.Role.RUN_DETAIL)).isEqualTo(11.5f);
        var font = Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.RUN_DETAIL);
        assertThat(font.getFamily(java.util.Locale.ROOT)).as("the data face").isEqualTo(
            Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.MONO).getFamily(java.util.Locale.ROOT));
    }
}
