package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

class ButtonsTest {
    /** Plan 1 finding 2: the inset line reaches the face's outermost pixels, antialiased into its rounded corners. */
    @Test void thePrimaryInsetLineReachesTheFacesOutermostPixels() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var action = new Nodes.Button("connect", ControlState.READY, "Connect", "", ButtonKind.PRIMARY, "connect", "Connect");
            var detail = new Nodes.Stack("detail", ControlState.READY, LayoutRole.DETAIL, List.of(action));
            var panel = new Nodes.Panel("panel", ControlState.READY, new Header("header", ControlState.READY, "Panel", java.util.OptionalInt.empty(), java.util.Optional.empty(), java.util.Optional.empty(), List.of()),
                java.util.Optional.empty(), new Text("body", ControlState.READY, "Body", TextRole.BODY), java.util.Optional.of(detail));
            try (var view = new KitView(ctx, new ViewSpec(panel), e -> {})) {
                view.setSize(260, 160); InteractiveRowsTest.layout(view);
                var button = find(view, "connect", JButton.class); button.setSize(74, 28);
                var image = new BufferedImage(148, 56, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                try { g.scale(2, 2); button.paint(g); } finally { g.dispose(); }
                int accent = ctx.theme().semantic().color(SemanticColor.ACCENT).getRGB();
                for (int row = 6; row < 8; row++) {
                    int x = 0;
                    while (x < image.getWidth() && (image.getRGB(x, row) >>> 24) != 255) x++;
                    assertThat(image.getRGB(x, row)).as("row %s's outermost opaque face pixel carries the inset line", row).isNotEqualTo(accent);
                }
            }
        });
    }

    @Test void compactDetailFaceMatchesSourceExtentAndCornersWithoutShrinkingItsHitBox() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var action = new Nodes.Button("connect", ControlState.READY, "Connect", "", ButtonKind.PRIMARY, "connect", "Connect");
            var detail = new Nodes.Stack("detail", ControlState.READY, LayoutRole.DETAIL, List.of(action));
            var panel = new Nodes.Panel("panel", ControlState.READY, new Header("header", ControlState.READY, "Panel", java.util.OptionalInt.empty(), java.util.Optional.empty(), java.util.Optional.empty(), List.of()),
                java.util.Optional.empty(), new Text("body", ControlState.READY, "Body", TextRole.BODY), java.util.Optional.of(detail));
            try (var view = new KitView(ctx, new ViewSpec(panel), e -> {})) {
                view.setSize(260, 160); InteractiveRowsTest.layout(view);
                var button = find(view, "connect", JButton.class); button.setSize(74, 28);
                assertThat(button.getPreferredSize().height).isEqualTo(28);
                // Original Sessions crop168,144,520,790 at2x; SHA2566cb0edee...585.
                // Source face354,722,136,42; its first 10 opaque row widths at the compact detail arc. No source resize.
                int[] sourceTopRows = {120, 126, 130, 132, 132, 134, 134, 134, 136, 136};
                var image = new BufferedImage(148, 56, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                try { g.scale(2, 2); button.paint(g); } finally { g.dispose(); }
                int accent = ctx.theme().semantic().color(SemanticColor.ACCENT).getRGB();
                assertThat(image.getRGB(74, 47)).as("source centerline ends at42device face pixels").isEqualTo(accent);
                assertThat(image.getRGB(74, 48) >>> 24).as("hit/focus reservation is not fill").isZero();
                for (int row = 0; row < sourceTopRows.length; row++) {
                    // The top rows carry theme.md's white inset line, antialiased into the rounded corners, so the face is
                    // its fully covered (opaque) pixels rather than one exact colour.
                    int count = 0; for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, row + 6) >>> 24 == 255) count++;
                    assertThat(count).as("source top face row %s", row).isEqualTo(sourceTopRows[row]);
                }
                var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return button; }
                });
                try {
                    for (var listener : button.getFocusListeners()) listener.focusGained(new java.awt.event.FocusEvent(button,
                        java.awt.event.FocusEvent.FOCUS_GAINED, false, null, java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD));
                    for (int scale : new int[] {1, 2, 3}) {
                        var painted = new BufferedImage(74 * scale, 28 * scale, BufferedImage.TYPE_INT_ARGB);
                        var graphics = painted.createGraphics();
                        try { graphics.scale(scale, scale); button.paint(graphics); } finally { graphics.dispose(); }
                        assertThat(painted.getRGB(37 * scale, 24 * scale) >>> 24).as("painted face retains bottom focus reservation at%sx", scale).isZero();
                        var halo = new BufferedImage(260 * scale, 160 * scale, BufferedImage.TYPE_INT_ARGB);
                        graphics = halo.createGraphics();
                        try { graphics.scale(scale, scale); ThemePainting.halos(view, graphics); } finally { graphics.dispose(); }
                        var box = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), view);
                        var hit = new Rectangle(box.x * scale, box.y * scale, box.width * scale, box.height * scale);
                        int ink = 0;
                        for (int y = 0; y < halo.getHeight(); y++) for (int x = 0; x < halo.getWidth(); x++) if ((halo.getRGB(x, y) >>> 24) != 0) {
                            assertThat(hit.contains(x, y)).as("focus halo stays inside full hitbox at%sx", scale).isTrue(); ink++;
                        }
                        assertThat(ink).isPositive();
                    }
                } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); }
                view.update(stack(action)); button.setSize(74, 28);
                assertThat(find(view, "connect", JButton.class)).isSameAs(button);
                assertThat(paint(button).getRGB(37, 27)).as("nonpanel face retains its full height").isEqualTo(accent);
            } finally { release(ctx); }
        });
    }
    @Test void fiveKindsDispatchOnceAfterTenUpdatesAndUseSemanticColors() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            Node[] nodes = java.util.Arrays.stream(ButtonKind.values()).map(k -> (Node)new Nodes.Button(k.name(), ControlState.READY, "Run", "add", k, k.name(), "Run action")).toArray(Node[]::new);
            try (var view = new KitView(context, stack(nodes), events::add)) {
                for (int i = 0; i < 10; i++) view.update(stack(nodes));
                for (ButtonKind kind : ButtonKind.values()) find(view, kind.name(), JButton.class).doClick(0);
                assertThat(events).containsExactlyElementsOf(java.util.Arrays.stream(ButtonKind.values()).map(k -> new UiEvent.Action(k.name())).toList());
                assertThat(find(view, "PRIMARY", JButton.class).getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_ONACCENT));
                assertThat(find(view, "LINK", JButton.class).getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_LINK));
            } finally { release(context); }
        });
    }
    @Test void disabledAndBusyButtonsCannotHoverOrDispatchAndVisibleStateApplies() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(new Nodes.Button("b", new ControlState(true, false, false, ""), "Go", "", ButtonKind.PRIMARY, "go", "")), events::add)) {
                var b = find(view, "b", JButton.class); b.setSize(b.getPreferredSize());
                BufferedImage before = paint(b); b.getModel().setRollover(true); BufferedImage after = paint(b);
                assertThat(before.getRGB(0, 0, before.getWidth(), before.getHeight(), null, 0, before.getWidth()))
                    .containsExactly(after.getRGB(0, 0, after.getWidth(), after.getHeight(), null, 0, after.getWidth()));
                b.doClick(0); assertThat(events).isEmpty();
                view.update(stack(new Nodes.Button("b", new ControlState(false, true, true, ""), "Go", "", ButtonKind.PRIMARY, "go", "")));
                assertThat(b.isVisible()).isFalse(); assertThat(b.isEnabled()).isFalse(); b.doClick(0); assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void iconButtonsHaveTooltipAccessibleNameAndLiteralText() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Nodes.Button("b", ControlState.READY, "", "copy", ButtonKind.GHOST, "copy", "Copy Link"),
                    new Nodes.Button("literal", ControlState.READY, "<html>literal", "", ButtonKind.SECONDARY, "go", "")), e -> {})) {
                var b = find(view, "b", JButton.class); assertThat(b.getIcon()).isNotNull();
                assertThat(b.getToolTipText()).isEqualTo("Copy Link"); assertThat(b.getAccessibleContext().getAccessibleName()).isEqualTo("Copy Link");
                assertThat(find(view, "literal", JButton.class).getClientProperty("html")).isNull();
            } finally { release(context); }
        });
    }
    @Test void tooltipContentNeverActivatesHtml() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Nodes.Button("b", ControlState.READY, "", "copy", ButtonKind.GHOST, "copy", "<html>Copy")), e -> {})) {
                var b = find(view, "b", JButton.class); var tip = b.createToolTip(); tip.setTipText(b.getToolTipText());
                assertThat(tip.getClientProperty("html")).isNull();
                assertThat(tip.getTipText()).isEqualTo("<html>Copy");
            } finally { release(context); }
        });
    }
    @Test void compactFooterActionReservesItsFaceFocusAndHitAreaAcrossThemesAndFontOverrides() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var events=new ArrayList<UiEvent>();var old=UIManager.get("defaultFont");
            var action=new Nodes.Button("connect",ControlState.READY,"Connect","",ButtonKind.PRIMARY,"connect","Connect");
            var detail=new Nodes.Stack("detail",ControlState.READY,LayoutRole.DETAIL,List.of(action));
            var panel=new Nodes.Panel("panel",ControlState.READY,new Header("header",ControlState.READY,"Panel",java.util.OptionalInt.empty(),java.util.Optional.empty(),java.util.Optional.empty(),List.of()),java.util.Optional.empty(),new Text("body",ControlState.READY,"Body",TextRole.BODY),java.util.Optional.of(detail));
            try(var view=new KitView(ctx,new ViewSpec(panel),events::add)) {
                var button=((Buttons.ButtonControl)view.control("connect")).button;
                for(var theme:List.of(KitFixtures.dark(),KitFixtures.light(),KitFixtures.custom())) {
                    ctx.binding().publish(new ThemePair(theme,theme));
                    view.setSize(260,160);InteractiveRowsTest.layout(view);
                    assertThat(button.getPreferredSize().height).isEqualTo(28);
                    button.setSize(button.getPreferredSize());var before=paint(button);
                    assertThat(before.getRGB(button.getWidth()/2,2)>>>24).isZero();
                    assertThat(before.getRGB(button.getWidth()/2,3)).as("the face's top row, under the inset line").isEqualTo(inset(theme.semantic().color(SemanticColor.ACCENT)));
                    button.getModel().setRollover(true);var hover=paint(button);
                    assertThat(hover.getRGB(button.getWidth()/2,3)).isEqualTo(inset(theme.semantic().color(SemanticColor.ACCENT_HOVER)));
                    button.getModel().setRollover(false);button.setEnabled(false);var disabled=paint(button);button.getModel().setRollover(true);
                    assertThat(paint(button).getRGB(0,0,button.getWidth(),button.getHeight(),null,0,button.getWidth())).containsExactly(disabled.getRGB(0,0,button.getWidth(),button.getHeight(),null,0,button.getWidth()));
                    button.doClick(0);assertThat(events).isEmpty();button.setEnabled(true);button.getModel().setRollover(false);
                }
                UIManager.put("defaultFont",new Font(Font.MONOSPACED,Font.PLAIN,24));view.update(new ViewSpec(panel));InteractiveRowsTest.layout(view);
                assertThat(button.getFont().getFamily()).isEqualTo(Font.MONOSPACED);assertThat(button.getPreferredSize().height).isGreaterThan(28);
                assertThat(((Buttons.ButtonControl)view.control("connect")).button).isSameAs(button);
                button.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("connect"));
            } finally {if(old==null)UIManager.getDefaults().remove("defaultFont");else UIManager.put("defaultFont",old);release(ctx);}
        });
    }
    /** A primary face's top row: theme.md's white inset line over {@code face}, as Java2D composes it. */
    static int inset(java.awt.Color face) {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try {
            g.setColor(face); g.fillRect(0, 0, 1, 1);
            g.setColor(dev.jasper.ui.theme.ColorMath.opacity(java.awt.Color.WHITE, dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(
                dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_INSET_PERCENT) / 100.0)); g.fillRect(0, 0, 1, 1);
        } finally { g.dispose(); }
        return image.getRGB(0, 0);
    }

    /**
     * design-system-source.md: a disabled control is the whole control at 40-45% opacity, as CSS opacity draws it. Over
     * any background, every pixel is the enabled button's at 42% over that background, so text keeps its contrast with
     * its own fill (a dark label on a light accent included) instead of fading again over the faded fill.
     */
    @Test void aDisabledButtonFadesAsOneLayerOverWhateverIsBeneathIt() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var action = new Nodes.Button("create", ControlState.READY, "Create Vault", "", ButtonKind.PRIMARY, "create", "Create Vault");
            var detail = new Nodes.Stack("detail", ControlState.READY, LayoutRole.DETAIL, List.of(action));
            var panel = new Nodes.Panel("panel", ControlState.READY, new Header("header", ControlState.READY, "Panel", java.util.OptionalInt.empty(), java.util.Optional.empty(), java.util.Optional.empty(), List.of()),
                java.util.Optional.empty(), new Text("body", ControlState.READY, "Body", TextRole.BODY), java.util.Optional.of(detail));
            try (var view = new KitView(ctx, new ViewSpec(panel), e -> {})) {
                view.setSize(260, 160); InteractiveRowsTest.layout(view);
                var button = find(view, "create", JButton.class); button.setSize(120, 28);
                var beneath = new Color(0x181825);
                var enabled = paintOver(button, beneath);
                button.setEnabled(false);
                var disabled = paintOver(button, beneath);
                int worst = 0;
                for (int y = 0; y < enabled.getHeight(); y++) for (int x = 0; x < enabled.getWidth(); x++) {
                    var expected = ColorMath.mix(new Color(enabled.getRGB(x, y)), beneath, .42);
                    var actual = new Color(disabled.getRGB(x, y));
                    worst = Math.max(worst, Math.max(Math.abs(expected.getRed() - actual.getRed()),
                        Math.max(Math.abs(expected.getGreen() - actual.getGreen()), Math.abs(expected.getBlue() - actual.getBlue()))));
                }
                assertThat(worst).as("largest channel difference from the enabled face at 42%").isLessThanOrEqualTo(2);
            }
        });
    }

    /** {@code c} painted at 2x over an opaque {@code beneath}. */
    static BufferedImage paintOver(JComponent c, Color beneath) {
        var image = new BufferedImage(c.getWidth() * 2, c.getHeight() * 2, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        try { g.setColor(beneath); g.fillRect(0, 0, image.getWidth(), image.getHeight()); g.scale(2, 2); c.paint(g); } finally { g.dispose(); }
        return image;
    }

    static BufferedImage paint(JComponent c) {
        var image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { c.paint(g); } finally { g.dispose(); } return image;
    }
}
