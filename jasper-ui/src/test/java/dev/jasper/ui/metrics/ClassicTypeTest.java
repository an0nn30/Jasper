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
