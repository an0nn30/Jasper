package dev.jasper.app.palette;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.testsupport.KitTestSupport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.1: the controller drives its view only through PaletteView; the card is that view's first implementation. */
class PaletteViewSeamTest {
    @Test void theControllerDrivesTheCardThroughThePaletteView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry(); var history = new CommandHistory()) {
                registry.register(PaletteTestSupport.scope("t.one", null));
                var controller = new PaletteController(KitTestSupport.context(), registry, history, true, () -> { }, () -> { },
                    () -> false, () -> { }, message -> { }, id -> { });
                try {
                    PaletteView view = controller.view();
                    assertThat(view).isInstanceOf(CardView.class);
                    assertThat(view.modeless()).as("the card dismisses as today").isFalse();
                    assertThat(controller.component()).isSameAs(view.component());
                    controller.open("t.one", PaletteTarget.none(), () -> true);
                    assertThat(view.component().isVisible()).isTrue();
                    assertThat(view.selectedKey()).isEqualTo("t.one/alpha");
                    view.selectRelative(1);
                    assertThat(view.selectedKey()).isEqualTo("t.one/beta");
                    controller.dismiss();
                    assertThat(view.component().isVisible()).isFalse();
                } finally { controller.close(); }
            }
        });
    }
}
