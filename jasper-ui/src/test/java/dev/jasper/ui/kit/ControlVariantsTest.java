package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.theme.SemanticColor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.2 and 5.7: the 14 control points and the two colour slots. */
class ControlVariantsTest {
    @Test void theFourteenPointsAreSpecFiveTwosInOrderWithTheirValues() {
        assertThat(Arrays.stream(ControlVariants.class.getRecordComponents()).map(c -> c.getName()).toList()).containsExactly(
            "toggle", "exclusive", "tabs", "group", "formLabel", "empty", "table", "tree", "stepper", "orderedList", "buttons", "tag",
            "route", "prompt");
        assertThat(ControlVariants.DEFAULT.keys()).containsExactly("switch", "segments", "segments", "card", "plain", "tile", "plain",
            "plain", "stepper", "inline", "raised", "tint", "tiles", "tile");
        assertThat(ControlVariants.CLASSIC.keys()).containsExactly("checkbox", "radios", "folder", "titled_box", "colon", "text",
            "classic", "classic", "spinner", "side_column", "classic", "boxed", "edged", "plain");
    }

    @Test void flagsRoundTripAndTheComplementFlipsEveryPoint() {
        boolean[] none = new boolean[14], all = new boolean[14];
        Arrays.fill(all, true);
        assertThat(ControlVariants.of(none)).isEqualTo(ControlVariants.DEFAULT);
        assertThat(ControlVariants.of(all)).isEqualTo(ControlVariants.CLASSIC);
        assertThat(ControlVariants.DEFAULT.complement()).isEqualTo(ControlVariants.CLASSIC);
        assertThat(ControlVariants.CLASSIC.complement()).isEqualTo(ControlVariants.DEFAULT);
        boolean[] mixed = {true, false, true, false, true, false, true, false, true, false, true, false, true, false};
        assertThat(ControlVariants.of(mixed).flags()).containsExactly(mixed);
        assertThat(ControlVariants.of(mixed).complement().flags()).containsExactly(
            false, true, false, true, false, true, false, true, false, true, false, true, false, true);
        assertThatIllegalArgumentException().isThrownBy(() -> ControlVariants.of(new boolean[13]))
            .withMessage("Expected 14 control points, got 13");
        assertThatNullPointerException().isThrownBy(() -> new ControlVariants(null, ControlVariants.Exclusive.SEGMENTS,
            ControlVariants.Tabs.SEGMENTS, ControlVariants.Group.CARD, ControlVariants.FormLabel.PLAIN, ControlVariants.Empty.TILE,
            ControlVariants.Table.PLAIN, ControlVariants.Tree.PLAIN, ControlVariants.Stepper.STEPPER, ControlVariants.OrderedList.INLINE,
            ControlVariants.Buttons.RAISED, ControlVariants.Tag.TINT, ControlVariants.Route.TILES, ControlVariants.Prompt.TILE));
    }

    @Test void theColourSlotsTakeOnlyTheirAllowedRoles() {
        assertThat(ColorSlots.DEFAULT).isEqualTo(new ColorSlots(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_SIDEBAR));
        assertThat(ColorSlots.CLASSIC).isEqualTo(new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_CARD));
        assertThatIllegalArgumentException().isThrownBy(() -> new ColorSlots(SemanticColor.SURFACE_CARD, SemanticColor.SURFACE_CARD))
            .withMessage("dialog_surface must be surface.dialog or surface.app, not surface.card");
        assertThatIllegalArgumentException().isThrownBy(() -> new ColorSlots(SemanticColor.SURFACE_APP, SemanticColor.SURFACE_APP))
            .withMessage("navigation_surface must be surface.sidebar or surface.card, not surface.app");
        assertThat(ColorSlots.DIALOG_SURFACES).containsExactly(SemanticColor.SURFACE_DIALOG, SemanticColor.SURFACE_APP);
        assertThat(ColorSlots.NAVIGATION_SURFACES).containsExactly(SemanticColor.SURFACE_SIDEBAR, SemanticColor.SURFACE_CARD);
    }
}
