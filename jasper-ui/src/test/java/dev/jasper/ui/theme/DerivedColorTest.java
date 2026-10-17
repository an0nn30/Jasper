package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.1's roles and spec 16.1.1's static closure: every recipe reads only semantic roles, which every theme that loads
 * has, and derived roles computed before it, so the order is acyclic; and the roles each reads are spec 7.1's.
 */
class DerivedColorTest {
    /** Spec 7.1: the roles each recipe reads. */
    static final Map<DerivedColor, Set<ColorRole>> INPUTS = inputs();

    private static Map<DerivedColor, Set<ColorRole>> inputs() {
        var inputs = new EnumMap<DerivedColor, Set<ColorRole>>(DerivedColor.class);
        Set<ColorRole> onApp = Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_APP);
        Set<ColorRole> onCard = Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD);
        inputs.put(SURFACE_HEADER, onApp);
        inputs.put(SURFACE_HEADER_ACTIVE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_APP, SURFACE_HEADER,
            SemanticColor.TEXT_PRIMARY));
        inputs.put(SURFACE_STRIPE, onApp);
        inputs.put(SURFACE_TABLE_HEADER, onCard);
        inputs.put(SURFACE_ZEBRA, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SURFACE_TABLE_HEADER));
        inputs.put(SURFACE_ROW_HOVER, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(WARNING_GROUND, Set.of(SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_CARD));
        inputs.put(WARNING_BORDER, Set.of(SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_CARD));
        inputs.put(DANGER_GROUND, Set.of(SemanticColor.STATUS_DANGER, SemanticColor.SURFACE_CARD));
        inputs.put(DANGER_BORDER, Set.of(SemanticColor.STATUS_DANGER, SemanticColor.SURFACE_CARD));
        inputs.put(INFO_GROUND, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(INFO_BORDER, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(SURFACE_FOOTER, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_DIALOG));
        inputs.put(BORDER_DIVIDER, Set.of(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(BORDER_ROW, Set.of(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(BORDER_HOVER, Set.of(SemanticColor.BORDER_CONTROL, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(ACCENT_EDGE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(BUTTON_END, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.BUTTON_SECONDARY_BG));
        inputs.put(TAG_BORDER_BLUE, Set.of(SemanticColor.HUE_BLUE, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_GREEN, Set.of(SemanticColor.HUE_GREEN, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_AMBER, Set.of(SemanticColor.HUE_AMBER, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_PURPLE, Set.of(SemanticColor.HUE_PURPLE, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_NEUTRAL, Set.of(SemanticColor.HUE_NEUTRAL, SemanticColor.SURFACE_CARD));
        return inputs;
    }

    @Test void theRolesAreSpecSevenOnesAndTheStarredOnesAndTheDividerAreOverridable() {
        assertThat(Arrays.stream(DerivedColor.values()).map(DerivedColor::key)).containsExactly("surface.header",
            "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover", "warning.ground",
            "warning.border", "danger.ground", "danger.border", "info.ground", "info.border", "surface.footer", "border.divider",
            "border.row", "border.hover", "accent.edge", "button.end", "tag.border.blue", "tag.border.green", "tag.border.amber",
            "tag.border.purple", "tag.border.neutral");
        assertThat(Arrays.stream(DerivedColor.values()).filter(DerivedColor::overridable).map(DerivedColor::key)).containsExactly(
            "surface.header", "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover",
            "warning.ground", "warning.border", "border.divider");
        for (var role : DerivedColor.values())
            assertThat(SemanticColor.of(role.key())).as("%s is not a semantic role", role.key()).isEmpty();
    }

    @Test void everyRecipeReadsItsSpecInputsOnlySemanticRolesAndDerivedRolesBeforeItOverEveryPalette() {
        var palettes = new ArrayList<SemanticPalette>();
        for (var theme : KitFixtures.themes()) palettes.add(theme.semantic());
        for (var theme : KitFixtures.degenerate()) palettes.add(theme.semantic());
        palettes.add(KitFixtures.marked().semantic());
        assertThat(INPUTS).hasSize(DerivedColor.values().length);
        for (var palette : palettes) for (var role : DerivedColor.values()) {
            var inputs = DerivedPalette.inputs(role, palette);
            assertThat(inputs).as(role.name()).containsExactlyInAnyOrderElementsOf(INPUTS.get(role));
            for (var input : inputs)
                if (input instanceof DerivedColor derived) assertThat(derived.ordinal()).as(role + " reads " + derived).isLessThan(role.ordinal());
        }
    }
}
