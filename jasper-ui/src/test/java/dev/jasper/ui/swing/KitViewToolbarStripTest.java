package dev.jasper.ui.swing;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Plan 3d: a contributed toolbar view is a toolbar strip, which presents an icon-only menu as a title strip does. */
class KitViewToolbarStripTest {
    static ViewSpec iconMenu() {
        return new ViewSpec(new Nodes.MenuButton("more", ControlState.READY, "", "more", Nodes.ButtonKind.SECONDARY, "",
            List.of(new MenuItem("manage", "Manage Sessions", "", "manage", true, false)), "More"));
    }

    static int[] pixels(Consumer<KitView> presentation) {
        var context = KitFixtures.context();
        try (var view = new KitView(context, iconMenu(), event -> { })) {
            presentation.accept(view);
            return KitFixtures.render(view, 120, 30, 1).getRGB(0, 0, 120, 30, null, 0, 120);
        } finally { context.binding().close(); context.icons().close(); }
    }

    @Test void aToolbarStripPresentsAnIconOnlyMenuAsATitleStripDoes() {
        Edt.run(() -> {
            int[] plain = pixels(view -> { }), title = pixels(view -> view.titleStrip(true)), toolbar = pixels(view -> view.toolbarStrip(true));
            assertThat(title).as("a strip changes the icon-only menu").isNotEqualTo(plain);
            assertThat(toolbar).isEqualTo(title);
        });
    }

    @Test void theFlagIsReportedAndAClosedViewRefusesIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                var view = new KitView(context, iconMenu(), event -> { });
                assertThat(view.toolbarStrip()).isFalse();
                view.toolbarStrip(true);
                assertThat(view.toolbarStrip()).isTrue();
                assertThat(view.titleStrip()).as("a separate flag").isFalse();
                view.close();
                assertThatIllegalStateException().isThrownBy(() -> view.toolbarStrip(false));
            } finally { context.binding().close(); context.icons().close(); }
        });
    }
}
