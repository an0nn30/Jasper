package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.5: the classic catalog, frozen whole; a retune edits classic-roles.tsv in the same change. */
class ClassicCatalogTest {
    static LinkedHashMap<String, Integer> table(String name) throws Exception {
        var table = new LinkedHashMap<String, Integer>();
        try (var in = ClassicCatalogTest.class.getResourceAsStream(name)) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], Integer.parseInt(cells[1]));
            }
        }
        return table;
    }

    @Test void everyRoleHasItsClassicValue() throws Exception {
        var table = table("classic-roles.tsv");
        assertThat(Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : DesignMetrics.Role.values())
            assertThat(DesignMetrics.CLASSIC.value(role)).as(role.name()).isEqualTo(table.get(role.name()));
    }

    /** Spec 5.5's named classic values, and the roles only classic faces read share their default value (Rulings 6). */
    @Test void theSpecsNamedValuesHold() {
        var c = DesignMetrics.CLASSIC;
        assertThat(c.value(DesignMetrics.Role.RUN_WINDOW_WIDTH)).isEqualTo(460);
        assertThat(c.value(DesignMetrics.Role.PALETTE_WINDOW_MARGIN)).isEqualTo(8);
        for (var role : new DesignMetrics.Role[] {DesignMetrics.Role.WORKSPACE_GUTTER, DesignMetrics.Role.WINDOW_RADIUS,
                DesignMetrics.Role.WORKSPACE_SURFACE_ARC, DesignMetrics.Role.PALETTE_CORNER_ARC, DesignMetrics.Role.DOCK_DROP_ARC,
                DesignMetrics.Role.RAIL_BUTTON_ARC, DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC, DesignMetrics.Role.TITLE_SEARCH_ARC,
                DesignMetrics.Role.PROMPT_TILE_RADIUS})
            assertThat(c.value(role)).as(role.name()).isZero();
        assertThat(c.value(DesignMetrics.Role.PROMPT_TILE)).isEqualTo(30);
        for (var name : NewRolesTest.ADDED) {
            var role = DesignMetrics.Role.valueOf(name);
            if (role != DesignMetrics.Role.ROW_HEIGHT && role != DesignMetrics.Role.FIELD_RADIUS)
                assertThat(c.value(role)).as(name).isEqualTo(DesignMetrics.DEFAULT.value(role));
        }
        assertThat(c).isNotEqualTo(DesignMetrics.DEFAULT);
    }
}
