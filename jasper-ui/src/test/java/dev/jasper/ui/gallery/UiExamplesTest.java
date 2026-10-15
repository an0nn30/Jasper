package dev.jasper.ui.gallery;

import dev.jasper.ui.theme.JasperTheme;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiExamplesTest {
    @Test void colorEngineWorksWithoutSwing() {
        // example:palette:start
        var theme = JasperTheme.bundled().generated(true);
        var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
        // example:palette:end
        assertThat(cardColor).isEqualTo(JasperTheme.bundled().palette(true).color(SemanticColor.SURFACE_CARD));
    }
    @Test void viewOwnsItsControlsAndCallerOwnsBindingAndIcons() {
        Edt.run(() -> {
            var generated = KitFixtures.dark();
            var events = new ArrayList<UiEvent>();
            // example:view:start
            try (var binding = new ThemeBinding(new ThemePair(generated, generated), failure -> {});
                 var icons = new IconRegistry()) {
                var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.WORKSPACE,
                        icons, () -> false, action -> "");
                var root = new Nodes.Stack("editor", ControlState.READY, LayoutRole.VERTICAL,
                        List.of(new Field("name", ControlState.READY, FieldKind.TEXT,
                                "", "Name", "Name", ""),
                                new Secret("password", ControlState.READY,
                                        "Password", "Password", "")));
                try (var view = new KitView(context, new ViewSpec(root), events::add)) {
                    view.withSecret("password", temporary -> {
                        // Consume synchronously; the kit erases this temporary array on return.
                    });
                    view.clearSecret("password");
                    binding.publish(new ThemePair(generated, generated));
                }
            }
            // example:view:end
            assertThat(events).isEmpty();
        });
    }
}
