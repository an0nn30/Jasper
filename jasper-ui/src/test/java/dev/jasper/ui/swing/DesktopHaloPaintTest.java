package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopHaloPaintTest {
    @Test void actualFieldsSwitchesRadiosAndSurfacePanelsPaintOpaqueOverridesWithoutMovingControls() {
        Edt.run(() -> {
            var previousLaf = UIManager.getLookAndFeel();
            var previousFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new FocusManager();
            try {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
                for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) for (boolean dark : List.of(false, true)) {
                    var base = JasperTheme.bundled().generated(dark);
                    Color ring = dark ? Color.WHITE : Color.BLACK;
                    var theme = new GeneratedTheme(base.name(), dark, base.author(), base.semantic(), base.terminal(),
                        base.editor(), base.syntax(), base.glass(), Map.of(DerivedColor.FOCUS_HALO, ring, DerivedColor.ERROR_HALO, ring));
                    FlatLafInstaller.install(theme, kit);
                    var context = KitFixtures.context(theme, kit);
                    try {
                        for (boolean error : List.of(false, true)) {
                            var state = error ? new ControlState(true, true, false, "Invalid value") : ControlState.READY;
                            var field = new Nodes.Field("field", state, Nodes.FieldKind.TEXT, "", "", "Field", "");
                            var toggle = new Nodes.Toggle("toggle", state, "Toggle", false, "toggle", false);
                            var choice = new Nodes.Choice("choice", ControlState.READY, Nodes.ChoiceKind.EXCLUSIVE,
                                List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty())), "a", "Choice", "choose");
                            var spec = new ViewSpec(new Nodes.Stack("root", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(field, toggle, choice)));
                            try (var view = new KitView(context, spec, ignored -> { })) {
                                KitFixtures.render(view, 400, 220, 1);
                                var input = (TextFields.FieldControl)view.control("field");
                                var toggleControl = (Switches.ToggleControl)view.control("toggle");
                                var choiceControl = (Choices.ChoiceControl)view.control("choice");
                                for (JComponent target : List.of(input.input, toggleControl.button, (JComponent)choiceControl.input.getComponent(0))) {
                                    manager.target = null;
                                    var before = KitFixtures.render(view, 400, 220, 1);
                                    Rectangle bounds = target.getBounds();
                                    manager.target = target;
                                    var gained = new FocusEvent(target, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD);
                                    for (var listener : target.getFocusListeners()) listener.focusGained(gained);
                                    var after = KitFixtures.render(view, 400, 220, 1);
                                    assertThat(target.getBounds()).isEqualTo(bounds);
                                    // Error halos are already visible in the before paint; focused radios always provide
                                    // a new focus halo. Error field/switch paints are checked against their authored theme.
                                    if (!error || target == choiceControl.input.getComponent(0)) assertNewRing(before, after, ring, kit.id());
                                    if (error && target != choiceControl.input.getComponent(0)) {
                                        context.binding().publish(new ThemePair(base, base));
                                        var authored = KitFixtures.render(view, 400, 220, 1);
                                        context.binding().publish(new ThemePair(theme, theme));
                                        var overridden = KitFixtures.render(view, 400, 220, 1);
                                        assertNewRing(authored, overridden, ring, kit.id()+" error");
                                    }
                                    var lost = new FocusEvent(target, FocusEvent.FOCUS_LOST);
                                    manager.target = null;
                                    for (var listener : target.getFocusListeners()) listener.focusLost(lost);
                                }
                            }
                        }
                        var surface = new SurfacePanel(context, SurfacePanel.Kind.FIELD);
                        surface.setSize(200, 30); surface.focusShown(true);
                        var image = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
                        var graphics = image.createGraphics();
                        try { surface.paint(graphics); } finally { graphics.dispose(); }
                        assertThat(new Color(image.getRGB(0,15), true)).isEqualTo(ring);
                        assertThat(ColorMath.contrast(ring, kit.colors().dialogSurface().color(theme))).isGreaterThanOrEqualTo(3);
                    } finally { context.binding().close(); context.icons().close(); }
                }
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocus);
                try { UIManager.setLookAndFeel(previousLaf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    private static void assertNewRing(BufferedImage before, BufferedImage after, Color ring, String where) {
        int pixels = 0;
        for (int y=0; y<after.getHeight(); y++) for (int x=0; x<after.getWidth(); x++)
            if (after.getRGB(x,y) == ring.getRGB() && before.getRGB(x,y) != ring.getRGB()) pixels++;
        assertThat(pixels).as(where+" real opaque halo pixels").isGreaterThan(4);
    }
    private static final class FocusManager extends DefaultKeyboardFocusManager {
        Component target;
        @Override public Component getFocusOwner() { return target; }
    }
}
