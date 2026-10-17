package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.6: the classic 12-based type scale, frozen whole. */
class ClassicTypeTest {
    @Test void everyRoleHasItsClassicSizeAndWeight() throws Exception {
        var table = new LinkedHashMap<String, float[]>();
        try (var in = ClassicTypeTest.class.getResourceAsStream("classic-type.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], new float[] {Float.parseFloat(cells[1]), Integer.parseInt(cells[2])});
            }
        }
        assertThat(Arrays.stream(Typography.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : Typography.Role.values()) {
            assertThat(TypeScale.CLASSIC.size(role)).as(role.name()).isEqualTo(table.get(role.name())[0]);
            assertThat(TypeScale.CLASSIC.weight(role)).as(role.name()).isEqualTo((int) table.get(role.name())[1]);
        }
        assertThat(TypeScale.CLASSIC.base()).isEqualTo(12f);
        assertThat(TypeScale.CLASSIC.body()).isEqualTo(12f);
    }

    /** Spec 5.6's table, read from the spec rather than classic-type.tsv: each listed role's size and weight. */
    @Test void theSpecsTableHolds() {
        var groups = new LinkedHashMap<String, java.util.List<String>>();
        groups.put("12/400", java.util.List.of("BODY", "LABEL", "SETTINGS_BODY", "PANEL_SEARCH", "TITLE_CONTEXT", "TOOLBAR_LABEL",
            "STATUS_TEXT", "STATUS_PATH", "MONO"));
        groups.put("11/400", java.util.List.of("DESCRIPTION", "SUPPORTING", "CAPTION", "SETTING_DESCRIPTION", "COUNT", "RESULTS_HEADING",
            "PALETTE_HINT", "PANEL_HEADER_COUNT", "MONO_SMALL", "MONO_CAPTION"));
        groups.put("12/600", java.util.List.of("PANEL_TITLE", "PANEL_HEADER_TITLE", "WINDOW_TITLE", "SECTION_LABEL"));
        groups.put("12/500", java.util.List.of("BODY_STRONG", "PANEL_ROW_SELECTED"));
        groups.put("15/600", java.util.List.of("DETAIL_TITLE", "PROMPT_TITLE"));
        groups.put("10/500", java.util.List.of("CHIP"));
        groups.forEach((expected, names) -> {
            for (var name : names) {
                var role = Typography.Role.valueOf(name);
                assertThat((int) TypeScale.CLASSIC.size(role) + "/" + TypeScale.CLASSIC.weight(role)).as(name).isEqualTo(expected);
            }
        });
    }

    /** Rulings 13: group titles carry no extra tracking under the classic scale; the default keeps its own. */
    @Test void onlyTheDefaultScaleTracksItsSectionLabels() {
        assertThat(TypeScale.DEFAULT.labelTracking()).isTrue();
        assertThat(TypeScale.CLASSIC.labelTracking()).isFalse();
        assertThat(TypeScale.CLASSIC.withBody(14).labelTracking()).isFalse();
        assertThat(TypeScale.CLASSIC.withBody(12)).isEqualTo(TypeScale.CLASSIC);
        assertThat(TypeScale.CLASSIC).isNotEqualTo(TypeScale.DEFAULT);
    }

    /** A role draws at its size times the body over the base: a user size of 14 scales every classic role by 14/12. */
    @Test void aUserSizeScalesTheClassicRoles() {
        var typography = Typography.of(TypeScale.CLASSIC, FaceSet.PLATFORM,
            new Typography.UserFont(java.util.Optional.of("SansSerif"), java.util.Optional.of(14f)));
        assertThat(typography.font(Typography.Role.CAPTION).getSize2D()).isEqualTo(11f * 14f / 12f);
        assertThat(typography.font(Typography.Role.PROMPT_TITLE).getSize2D()).isEqualTo(15f * 14f / 12f);
    }
}
