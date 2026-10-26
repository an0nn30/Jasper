package dev.jasper.sdk.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Floating tool windows plan 3 Task 1: a panel's placement says whether it has a window of its own. */
class PanelPlacementTest {
    @Test void onlyFloatAndWindowAreAWindowOfItsOwn() {
        assertThat(new PanelPlacement(Anchor.LEFT, false, PanelMode.DOCKED).ownWindow()).isFalse();
        assertThat(new PanelPlacement(Anchor.RIGHT, true, PanelMode.FLOAT).ownWindow()).isTrue();
        assertThat(new PanelPlacement(Anchor.BOTTOM, false, PanelMode.WINDOW).ownWindow()).isTrue();
    }

    @Test void requiresTheAnchorAndTheMode() {
        assertThatNullPointerException().isThrownBy(() -> new PanelPlacement(null, false, PanelMode.DOCKED)).withMessage("anchor");
        assertThatNullPointerException().isThrownBy(() -> new PanelPlacement(Anchor.LEFT, false, null)).withMessage("mode");
    }
}
