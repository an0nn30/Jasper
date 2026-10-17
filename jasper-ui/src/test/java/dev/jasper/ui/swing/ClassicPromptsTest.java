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

    /** Spec 7.3 for the glyph: the on-accent glyph is painted as its declared guard makes it on the tile, over every palette. */
    @Test void theGlyphPaintsItsDeclaredPairOverEveryPalette() {
        var palettes = new java.util.ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                KitFixtures.render(view, 440, 200, 1);
                var tile = find(view, "unlock-hero.tile", Stacks.Tile.class);
                var image = paint(tile);
                var glyph = ThemePainting.painted(context, context.faces().prompt.pairs().get(1));
                int found = 0, inset = 2 * ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                for (int y = inset; y < image.getHeight() - inset; y++)
                    for (int x = inset; x < image.getWidth() - inset; x++) if (image.getRGB(x, y) == glyph.getRGB()) found++;
                assertThat(found).as(theme.name() + (theme.dark() ? " Dark" : " Light") + " glyph pixels in " + glyph).isPositive();
            } finally { release(context); }
        });
    }

    /** A prompt header without an icon mounts no tile, so its lines start at the leading edge, as a hero or a section. */
    @Test void aHeaderWithoutAnIconLaysItsLinesFromTheLeadingEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var ready = dev.jasper.ui.model.ControlState.READY;
            var hero = new dev.jasper.ui.model.Nodes.Stack("about-hero", ready, dev.jasper.ui.model.Nodes.LayoutRole.PROMPT_HEADER, java.util.List.of(
                new dev.jasper.ui.model.Nodes.Text("about-name", ready, "Jasper", dev.jasper.ui.model.Nodes.TextRole.PROMPT_TITLE),
                new dev.jasper.ui.model.Nodes.Text("about-version", ready, "Version 1.0", dev.jasper.ui.model.Nodes.TextRole.DESCRIPTION)));
            var facts = new dev.jasper.ui.model.Nodes.Stack("about-facts", ready, dev.jasper.ui.model.Nodes.LayoutRole.PROMPT_HEADER, java.util.List.of(
                new dev.jasper.ui.model.Nodes.Text("about-license", ready, "PolyForm Noncommercial 1.0.0", dev.jasper.ui.model.Nodes.TextRole.CAPTION),
                new dev.jasper.ui.model.Nodes.Text("about-copyright", ready, "Copyright 2026", dev.jasper.ui.model.Nodes.TextRole.CAPTION)));
            var spec = new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Form("about", ready, dev.jasper.ui.model.Nodes.FormKind.PROMPT_COMPACT,
                "About Jasper", java.util.Optional.of(hero), java.util.List.of(facts), java.util.Optional.empty(),
                java.util.List.of(new dev.jasper.ui.model.Nodes.Button("copy", ready, "Copy", "", dev.jasper.ui.model.Nodes.ButtonKind.SECONDARY, "copy", "")),
                "close", dev.jasper.ui.model.Nodes.FormSubmission.NONE));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 440, 260, 1);
                for (var stack : java.util.List.of("about-hero", "about-facts"))
                    assertThat(((Stacks.StackControl) view.control(stack)).tile.getParent()).as(stack + " mounts no tile").isNull();
                for (var id : java.util.List.of("about-version", "about-license", "about-copyright"))
                    assertThat(find(view, id, JComponent.class).getX()).as(id).isZero();
                var line = find(view, "about-license", JComponent.class);
                assertThat(line.getWidth()).as("the full width").isEqualTo(line.getParent().getWidth());
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
