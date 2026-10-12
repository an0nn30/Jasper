package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

@SuppressWarnings("try")
class SwitchesTest {
    static Toggle toggle(boolean checked, ControlState state, boolean compact) { return new Toggle("run", state, "Run server", checked, "start-stop", compact); }
    @Test void toggleReportsIntentWhileDescriptorOwnsConfirmationBusyAndFailure() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>(); KitView[] owner = {null};
            try (var view = new KitView(context, stack(toggle(false, ControlState.READY, false)), events::add)) {
                owner[0] = view; var button = find(view, "run", JButton.class); button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Toggled("run", true)); assertThat(button.isSelected()).isFalse();
                view.update(stack(toggle(false, new ControlState(true, true, true, ""), false))); button.doClick(0); assertThat(events).hasSize(1);
                view.update(stack(toggle(false, new ControlState(true, true, false, "Start failed"), false)));
                assertThat(button.isSelected()).isFalse(); assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("Start failed");
                view.update(stack(toggle(true, ControlState.READY, false))); assertThat(button.isSelected()).isTrue();
                button.doClick(0); assertThat(events).endsWith(new UiEvent.Toggled("run", false)); assertThat(button.isSelected()).isTrue();
            } finally { release(context); }
        });
    }
    @Test void pointerAndKeyboardControlUseFixedCompactAndFormTracks() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(toggle(false, ControlState.READY, false)), events::add)) {
                var button = find(view, "run", JButton.class); var form = button.getPreferredSize();
                button.setSize(form); var off = ButtonsTest.paint(button);
                view.update(stack(toggle(true, ControlState.READY, false))); var on = ButtonsTest.paint(button);
                assertThat(on.getRGB(form.width / 2, form.height / 2)).isNotEqualTo(off.getRGB(form.width / 2, form.height / 2));
                view.update(stack(toggle(true, ControlState.READY, true))); var compact = button.getPreferredSize();
                assertThat(compact.width).isLessThan(form.width); assertThat(compact.height).isLessThan(form.height);
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Run server");
                assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("pressed SPACE"))).isNotNull();
            } finally { release(context); }
        });
    }
    @Test void synchronousToggleConfirmationAndCloseInCallbackAreSafe() {
        Edt.run(() -> {
            var context = KitFixtures.context(); KitView[] owner = {null}; int[] count = {0};
            try (var view = new KitView(context, stack(toggle(false, ControlState.READY, true)), e -> {
                count[0]++; owner[0].update(stack(toggle(((UiEvent.Toggled)e).checked(), ControlState.READY, true))); owner[0].close();
            })) {
                owner[0] = view; var button = find(view, "run", JButton.class); button.doClick(0); button.doClick(0);
                assertThat(count[0]).isEqualTo(1); assertThat(button.isSelected()).isTrue(); assertThat(button.getActionListeners()).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void disablingMotionSettlesRunningSwitchAndProgressAndCloseStopsOwnedTimers() {
        Edt.run(() -> {
            var enabled = new java.util.concurrent.atomic.AtomicBoolean(true); var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), base.icons(), enabled::get);
            try (var view = new KitView(context, stack(toggle(false, ControlState.READY, false), new Progress("progress", ControlState.READY, -1, "Loading")), e -> {})) {
                var button = find(view, "run", Switches.SwitchButton.class);
                view.update(stack(toggle(true, ControlState.READY, false), new Progress("progress", ControlState.READY, -1, "Loading")));
                assertThat(button.position).isLessThan(1); enabled.set(false);
                context.binding().publish(new ThemePair(KitFixtures.custom(), KitFixtures.custom()));
                assertThat(button.position).isEqualTo(1);
                var progress = find(view, "progress", Indicators.ProgressBar.class); assertThat(progress.phase).isZero();
                enabled.set(true); view.update(stack(toggle(false, ControlState.READY, false), new Progress("progress", ControlState.READY, -1, "Loading")));
                view.close(); assertThat(button.position).isZero(); assertThat(progress.phase).isZero();
            } finally { release(context); }
        });
    }
    /**
     * The indeterminate segment enters past the left end, crosses the track and leaves past the right end, so the loop
     * restarts out of sight; it used to stop at the right end and jump back to the left.
     */
    @Test void theIndeterminateSegmentCrossesTheWholeTrackSoItsLoopRestartsOutOfSight() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Progress("progress", ControlState.READY, -1, "")), e -> {})) {
                var bar = find(view, "progress", Indicators.ProgressBar.class);
                bar.setSize(300, bar.getPreferredSize().height);
                Color accent = context.theme().semantic().color(SemanticColor.ACCENT);
                assertThat(accentColumns(bar, accent, 0)).as("at rest the segment waits at the start").isBetween(95, 100);
                bar.animation.start();
                assertThat(accentColumns(bar, accent, 0)).as("where the loop restarts").isZero();
                assertThat(accentColumns(bar, accent, .999)).as("just before it restarts").isZero();
                assertThat(accentColumns(bar, accent, .5)).as("midway, the whole segment but its antialiased ends").isBetween(95, 100);
                bar.animation.stop();
                int previous = Indicators.ProgressBar.indeterminateX(300, 100, 0);
                for (int step = 1; step < 100; step++) {
                    int x = Indicators.ProgressBar.indeterminateX(300, 100, step / 100.0);
                    assertThat(x - previous).as("moves forward smoothly within a pass").isBetween(0, 5);
                    previous = x;
                }
            } finally { release(context); }
        });
    }

    /** How many columns of the bar's track row the segment paints in the accent color at {@code phase}. */
    static int accentColumns(Indicators.ProgressBar bar, Color accent, double phase) {
        bar.phase = phase;
        var image = new java.awt.image.BufferedImage(bar.getWidth(), bar.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { bar.paint(g); } finally { g.dispose(); }
        int row = ThemePainting.metric(bar.context, dev.jasper.ui.metrics.DesignMetrics.Role.PROGRESS_HEIGHT) / 2, columns = 0;
        for (int x = 0; x < image.getWidth(); x++) if ((image.getRGB(x, row) & 0xFFFFFF) == (accent.getRGB() & 0xFFFFFF)) columns++;
        return columns;
    }

    @Test void translucentTrackKeepsAuthoredAlphaWithSingleDisabledTreatment() {
        Edt.run(() -> {
            var base = KitFixtures.dark(); var values = new java.util.EnumMap<SemanticColor,Color>(SemanticColor.class); for (var role : SemanticColor.values()) values.put(role, base.semantic().color(role));
            values.put(SemanticColor.ACCENT, new Color(10, 20, 30, 128));
            var theme = new GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(), base.terminal(), base.uiKeys(), base.icons(), base.glass(), new SemanticPalette(values));
            var context = KitFixtures.context(); context.binding().publish(new ThemePair(theme, theme));
            try (var view = new KitView(context, stack(toggle(true, new ControlState(true, false, false, ""), false)), e -> {})) {
                var button = find(view, "run", JButton.class); button.setSize(button.getPreferredSize());
                var image = ButtonsTest.paint(button); int x=com.formdev.flatlaf.util.UIScale.scale(10), y=button.getHeight()/2;
                assertThat((image.getRGB(x, y) >>> 24) & 255).isBetween(53, 55);
            } finally { release(context); }
        });
    }
    @Test void indicatorsPaintSemanticHuesStatusProgressAndAllStrengthLevels() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Chip("chip", ControlState.READY, "<html>SFTP", Hue.BLUE),
                    new StatusDot("dot", ControlState.READY, StatusKind.SUCCESS), new Progress("progress", ControlState.READY, .5, "Loading"),
                    new Strength("strength", ControlState.READY, 0, "Strength")), e -> {})) {
                var chip = find(view, "chip", JComponent.class); assertThat(chip.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.HUE_BLUE)); assertThat(chip.getClientProperty("html")).isNull();
                var dot = find(view, "dot", JComponent.class); dot.setSize(dot.getPreferredSize());
                assertThat(ButtonsTest.paint(dot).getRGB(dot.getWidth()/2, dot.getHeight()/2)).isEqualTo(context.theme().semantic().color(SemanticColor.STATUS_SUCCESS).getRGB());
                var progress = find(view, "progress", JProgressBar.class); assertThat(progress.getValue()).isEqualTo(500);
                for (int i = 0; i <= 4; i++) {
                    view.update(stack(new Strength("strength", ControlState.READY, i, "Strength")));
                    var meter = find(view, "strength", JComponent.class); meter.setSize(140, 30); var painted = ButtonsTest.paint(meter);
                    assertThat(meter.getAccessibleContext().getAccessibleDescription()).isEqualTo(i + " of 4");
                    assertThat(painted.getRGB(2, 2)).isNotEqualTo(0);
                }
                view.update(stack(new Progress("progress", ControlState.READY, -1, "Loading")));
                assertThat(find(view, "progress", JProgressBar.class).isIndeterminate()).isTrue();
            } finally { release(context); }
        });
    }
}
