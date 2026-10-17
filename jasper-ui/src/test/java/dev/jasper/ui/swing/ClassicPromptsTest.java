package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's prompt point as amended: the classic prompt hides its body title and puts a 30 px accent tile beside the message. */
@SuppressWarnings("try")
class ClassicPromptsTest {
    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicPromptIsATileBesideItsMessage() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                KitFixtures.capture(context, view.spec(), 440, 200, "prompt");
                KitFixtures.render(view, 440, 200, 1);
                var title = find(view, "unlock-title", JComponent.class);
                assertThat(title.isVisible()).as("the header strip shows the title").isFalse();
                var tile = find(view, "unlock-hero.tile", Stacks.Tile.class);
                var message = find(view, "unlock-description", JComponent.class);
                int size = ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE);
                assertThat(tile.getWidth()).isEqualTo(size);
                assertThat(message.getX()).as("beside the tile").isGreaterThanOrEqualTo(tile.getX() + size);
                assertThat(((Labels.LiteralLabel) message).wrapped.centered).as("the message reads from the tile").isFalse();
                var image = paint(tile);
                assertThat(new Color(image.getRGB(2, size / 2), true)).isEqualTo(ThemePainting.color(context, SemanticColor.ACCENT));
                assertThat(new Color(image.getRGB(0, size / 2), true)).isEqualTo(ThemePainting.painted(context, context.faces().prompt.pairs().get(0)));
                view.update(KitFixtures.form("unlock"));
                assertThat(find(view, "unlock-title", JComponent.class).isVisible()).as("still hidden after an update").isFalse();
                assertThat(find(view, "unlock-description", Labels.LiteralLabel.class).wrapped.centered).as("still left-aligned after an update").isFalse();
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheTileAndTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                KitFixtures.render(view, 440, 260, 1);
                assertThat(find(view, "unlock-title", JComponent.class).isVisible()).isTrue();
                assertThat(find(view, "unlock-hero.tile", Stacks.Tile.class).getWidth())
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.PROMPT_TILE));
            } finally { release(context); }
        });
    }
}
