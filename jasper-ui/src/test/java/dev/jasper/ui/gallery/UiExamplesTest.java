package dev.jasper.ui.gallery;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.ThemeToml;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiExamplesTest {
    @Test void colorEngineWorksWithoutSwing() {
        // example:palette:start
        var document = ThemeToml.parse("""
                name = "Example"
                [colors]
                background = "#18202b"
                foreground = "#e6edf5"
                accent = "#79afff"
                [colors.normal]
                black = "#313b49"
                red = "#d75461"
                green = "#4b9a6c"
                yellow = "#c89d42"
                blue = "#4b82cf"
                magenta = "#9e6bc4"
                cyan = "#448fa4"
                white = "#c7ced7"
                [semantic]
                "surface.card" = "#20242b"
                """, "example.toml");
        var generated = ThemeGenerator.generate(ThemeResolver.resolve(document));
        var cardColor = generated.semantic().color(SemanticColor.SURFACE_CARD);
        // example:palette:end
        assertThat(cardColor).isEqualTo(new java.awt.Color(0x20242b));
    }
    @Test void viewOwnsItsControlsAndCallerOwnsBindingAndIcons() {
        Edt.run(() -> {
            var generated = KitFixtures.dark();
            var events = new ArrayList<UiEvent>();
            // example:view:start
            try (var binding = new ThemeBinding(new ThemePair(generated, generated), failure -> {});
                 var icons = new IconRegistry()) {
                var context = new KitContext(binding, ThemeRoute.WORKSPACE,
                        DesignMetrics.DEFAULT, icons, () -> false);
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
