package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.palette.CardView;
import dev.jasper.app.palette.RunWindowView;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import org.junit.jupiter.api.Test;

/** Spec 16.1.8: every palette strategy builds, opens and closes for every PaletteLook value. */
class PaletteStrategiesTest {
    @Test void everyViewAndHomeBuildsForEveryValue() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var view : PaletteLook.View.values()) for (var home : PaletteLook.Home.values()) {
                try (var owner = UpperHomeTest.owner(new PaletteLook(view, home))) {
                    var root = WindowCommandPaletteTest.install(owner); root.setSize(1200, 800); LayoutTestSupport.layoutTree(root);
                    owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                    assertThat(owner.commandPalette().view()).as(view + "/" + home)
                        .isInstanceOf(view == PaletteLook.View.CARD ? CardView.class : RunWindowView.class);
                    assertThat(owner.commandPalette().component().getWidth()).as(view + "/" + home).isPositive();
                    owner.commandPalette().dismiss();
                }
            }
        });
    }
}
