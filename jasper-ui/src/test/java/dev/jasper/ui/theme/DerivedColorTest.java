package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Spec 7.1: the derived roles, in the order they are computed, and the eight a variant may author. */
class DerivedColorTest {
    @Test void theRolesAreSpecSevenOnesAndTheEightStarredOnesAreOverridable() {
        assertThat(Arrays.stream(DerivedColor.values()).map(DerivedColor::key)).containsExactly("surface.header",
            "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover", "warning.ground",
            "warning.border", "danger.ground", "danger.border", "info.ground", "info.border", "surface.footer", "border.divider",
            "border.row", "border.hover", "accent.edge", "button.end", "tag.border.blue", "tag.border.green", "tag.border.amber",
            "tag.border.purple", "tag.border.neutral");
        assertThat(Arrays.stream(DerivedColor.values()).filter(DerivedColor::overridable).map(DerivedColor::key)).containsExactly(
            "surface.header", "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover",
            "warning.ground", "warning.border");
        for (var role : DerivedColor.values())
            assertThat(SemanticColor.of(role.key())).as("%s is not a semantic role", role.key()).isEmpty();
    }
}
