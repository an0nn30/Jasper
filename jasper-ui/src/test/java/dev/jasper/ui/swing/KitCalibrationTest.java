package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Calibration is against visible source geometry under the actual shipped LAF. */
class KitCalibrationTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    @Test void kitIsTheOnlyPlaceholderPainterUnderRealFlatLaf() {
        Edt.run(() -> {
            var old = UIManager.getLookAndFeel(); Object font = UIManager.get("defaultFont"); UIManager.put("defaultFont", new Font(dev.jasper.ui.testsupport.KitFixtures.platformFamily(), Font.PLAIN, 13)); var context = KitFixtures.context();
            try {
                FlatLafInstaller.install(context.theme());
                try (var view = new KitView(context, new ViewSpec(new Field("field", ControlState.READY, FieldKind.TEXT, "", "Placeholder calibration", "Label", "")), e -> {})) {
                    var control = (TextFields.FieldControl)view.control("field");
                    var before = KitFixtures.render(view, UIScale.scale(400), UIScale.scale(70), 1);
                    control.input.putClientProperty("JTextField.placeholderText", null);
                    var after = KitFixtures.render(view, UIScale.scale(400), UIScale.scale(70), 1);
                    assertThat(java.util.Arrays.equals(before.getRGB(0, 0, before.getWidth(), before.getHeight(), null, 0, before.getWidth()), after.getRGB(0, 0, after.getWidth(), after.getHeight(), null, 0, after.getWidth()))).as("clearing vendor placeholder must not change kit pixels").isTrue();
                }
            } finally { context.binding().close(); context.icons().close(); restore(old); UIManager.put("defaultFont", font); }
        });
    }
    @Test void nominalButtonHeightIsTheVisibleFillAndRadiusIsTheControlRadius() {
        Edt.run(() -> {
            var old = UIManager.getLookAndFeel(); Object font = UIManager.get("defaultFont"); UIManager.put("defaultFont", new Font(dev.jasper.ui.testsupport.KitFixtures.platformFamily(), Font.PLAIN, 13)); var context = KitFixtures.context();
            try {
                FlatLafInstaller.install(context.theme());
                try (var view = new KitView(context, new ViewSpec(new Nodes.Button("button", ControlState.READY, "Continue", "", ButtonKind.PRIMARY, "continue", "")), e -> {})) {
                    var button = view.control("button").component;
                    int width = UIScale.scale(120), height = UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT));
                    button.setSize(width, height);
                    var image = ButtonsTest.paint(button);
                    int fill = context.theme().semantic().color(SemanticColor.ACCENT).getRGB();
                    assertThat(button.getPreferredSize().height).isEqualTo(height);
                    assertThat(image.getRGB(width / 2, 0)).as("visible top at nominal bounds, under the inset line").isEqualTo(ButtonsTest.inset(context.theme().semantic().color(SemanticColor.ACCENT)));
                    assertThat(image.getRGB(width / 2, height - 1)).as("visible bottom at nominal bounds").isEqualTo(fill);
                    assertThat(image.getRGB(0, 0)).as("the control radius corner is outside fill").isNotEqualTo(fill);
                }
            } finally { context.binding().close(); context.icons().close(); restore(old); UIManager.put("defaultFont", font); }
        });
    }
    @Test void realFormFocusHaloIsExternalVisibleAndDoesNotChangeControlBounds() {
        Edt.run(() -> {
            var old = UIManager.getLookAndFeel(); var priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            Object font = UIManager.get("defaultFont"); UIManager.put("defaultFont", new Font(dev.jasper.ui.testsupport.KitFixtures.platformFamily(), Font.PLAIN, 13)); var manager = new FocusManager(); var context = KitFixtures.context();
            try {
                FlatLafInstaller.install(context.theme()); KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
                var field = new Field("field", ControlState.READY, FieldKind.TEXT, "", "Value", "Label", "");
                var form = new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(new FormRow("row", ControlState.READY, "Label", "", field, Optional.empty())), Optional.empty(), List.of(), "cancel");
                try (var view = new KitView(context, new ViewSpec(form), e -> {})) {
                    var before = KitFixtures.render(view, UIScale.scale(540), UIScale.scale(180), 1);
                    var control = (TextFields.FieldControl)view.control("field");
                    var bounds = SwingUtilities.convertRectangle(control.surface.getParent(), control.surface.getBounds(), view);
                    assertThat(bounds.height).as("font=%s, typography=%s, line=%s, input=%s, surface=%s", control.input.getFont(), context.typography().family(), context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getLineMetrics("Ag", new java.awt.font.FontRenderContext(null,true,true)).getHeight(), control.input.getPreferredSize(), control.surface.getPreferredSize()).isEqualTo(UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT)));
                    manager.target = control.input;
                    var event = new FocusEvent(control.input, FocusEvent.FOCUS_GAINED, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD);
                    for (var listener : control.input.getFocusListeners()) listener.focusGained(event);
                    var after = KitFixtures.render(view, UIScale.scale(540), UIScale.scale(180), 1);
                    assertThat(SwingUtilities.convertRectangle(control.surface.getParent(), control.surface.getBounds(), view)).isEqualTo(bounds);
                    int x = bounds.x + bounds.width / 2, y = bounds.y - UIScale.scale(2);
                    assertThat(after.getRGB(x, y)).as("outer halo survives form/viewport clipping").isNotEqualTo(before.getRGB(x, y));
                    assertThat(after.getRGB(x, bounds.y + UIScale.scale(2))).as("focused fill keeps nominal bounds").isEqualTo(before.getRGB(x, bounds.y + UIScale.scale(2)));
                }
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus); context.binding().close(); context.icons().close(); restore(old); UIManager.put("defaultFont", font); }
        });
    }
    @Test void heroAndCardSegmentsHaveOneVisibleLabelAndSettingsChoicesFitTheirSourceRole() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("unlock-vault"), e -> {})) {
                var password = (SecretFields.SecretControl)view.control("password");
                assertThat(password.label.isVisible()).as("hero hint is the visible label").isFalse();
                assertThat(password.lock.isVisible()).as("hero source has no leading lock").isFalse();
                assertThat(password.input.getAccessibleContext().getAccessibleName()).isEqualTo("Master password");
            } finally { context.binding().close(); context.icons().close(); }
            var hostContext = KitFixtures.context();
            try (var view = new KitView(hostContext, dev.jasper.ui.gallery.FormFixtures.create("add-host"), e -> {})) {
                KitFixtures.render(view, UIScale.scale(540), UIScale.scale(760), 1);
                assertThat(((Choices.ChoiceControl)view.control("authentication-mode")).label.isVisible()).as("full-width card segments").isFalse();
                assertThat(view.control("group").component.getWidth()).as("settings dropdown trailing width").isEqualTo(UIScale.scale(160));
            } finally { hostContext.binding().close(); hostContext.icons().close(); }
        });
    }
    @Test void referenceCategoryTilesOrderedSurfaceAndPickerAlignmentAreRealRenderedOwners() {
        Edt.run(() -> {
            var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false);
            try (var view = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("add-host"), e -> {})) {
                var image = KitFixtures.render(view, UIScale.scale(540), UIScale.scale(760), 1);
                var keys = (ItemLists.ListControl)view.control("keys");
                var bounds = SwingUtilities.convertRectangle(keys.component.getParent(), keys.component.getBounds(), view);
                assertThat(image.getRGB(bounds.x + bounds.width / 2, bounds.y + UIScale.scale(2))).as("ordered bounded input surface").isEqualTo(context.theme().semantic().color(SemanticColor.SURFACE_INPUT).getRGB());
                var picker = (Choices.PickerButton)((Choices.ChoiceControl)view.control("credential")).input;
                var before = ButtonsTest.paint(picker); picker.setIcon(null); var after = ButtonsTest.paint(picker);
                boolean changed = false;
                for (int x = UIScale.scale(10); x < UIScale.scale(28); x++) for (int y = 0; y < before.getHeight(); y++) changed |= before.getRGB(x, y) != after.getRGB(x, y);
                assertThat(changed).as("picker glyph actually paints at leading edge").isTrue();
            } finally { context.icons().close(); base.icons().close(); base.binding().close(); }
            var b = KitFixtures.context();
            var c = new KitContext(b.binding(), b.route(), b.metrics(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false);
            try (var sheet = new KitView(c, dev.jasper.ui.gallery.FormFixtures.create("add-login-sheet"), e -> {});
                 var vault = new KitView(c, dev.jasper.ui.gallery.FormFixtures.create("credential-vault"), e -> {})) {
                KitFixtures.render(sheet, UIScale.scale(480), UIScale.scale(365), 1);
                KitFixtures.render(vault, UIScale.scale(900), UIScale.scale(555), 1);
                assertThat(((JLabel)sheet.control("sheet-icon").component).getIcon().getIconWidth()).as("sheet category tile").isEqualTo(UIScale.scale(32));
                assertThat(((JLabel)vault.control("type-icon").component).getIcon().getIconWidth()).as("detail category tile").isEqualTo(UIScale.scale(44));
                assertThat(((JLabel)vault.control("proxmox-icon").component).getIcon().getIconWidth()).as("selected two-line category tile").isEqualTo(UIScale.scale(28));
            } finally { c.icons().close(); b.icons().close(); b.binding().close(); }
        });
    }
    @Test void managerUsesThreeSemanticColumnsHiddenChromeAndPinnedTrailingAction() {
        Edt.run(() -> {
            var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false);
            try (var view = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("credential-vault"), e -> {})) {
                KitFixtures.render(view, UIScale.scale(900), UIScale.scale(555), 1);
                assertThat(view.control("manager-header").component.getHeight()).as("hidden native-chrome replacement").isZero();
                assertThat(view.control("categories").component.getWidth()).isEqualTo(UIScale.scale(192));
                assertThat(view.control("list-column").component.getWidth()).isEqualTo(UIScale.scale(272));
                var done = view.control("done").component;
                var bounds = SwingUtilities.convertRectangle(done.getParent(), done.getBounds(), view);
                assertThat(bounds.y).isGreaterThan(UIScale.scale(500));
                assertThat(bounds.x + bounds.width).as("the action's focus reservation overhangs the footer side by 2").isEqualTo(UIScale.scale(900 - m(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_FOOTER_SIDE) + 2));
            } finally { context.icons().close(); base.icons().close(); base.binding().close(); }
            var c = KitFixtures.context();
            try (var view = new KitView(c, dev.jasper.ui.gallery.PanelFixtures.create("fileservers"), e -> {})) {
                KitFixtures.render(view, UIScale.scale(264), UIScale.scale(424), 1);
                // A compact panel row's leading glyph is drawn at the panel-row glyph size (the plugin-panels reference's folder), untiled.
                assertThat(((JLabel)view.control("server-icon").component).getIcon().getIconWidth()).as("compact folder remains a plain, untiled glyph")
                    .isEqualTo(UIScale.scale(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_COMPACT_ROW_GLYPH)));
            } finally { c.icons().close(); c.binding().close(); }
        });
    }
    @Test void cardRadiusIsARadiusRatherThanAnArcDiameter() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(new Card("card", ControlState.READY, CardKind.FORM, List.of())), e -> {})) {
                var card = view.control("card").component; card.setSize(UIScale.scale(180), UIScale.scale(80));
                var image = ButtonsTest.paint(card);
                assertThat((image.getRGB(0, UIScale.scale(2)) >>> 24) & 255).as("the card radius corner remains outside the full fill").isLessThan(200);
            } finally { context.icons().close(); context.binding().close(); }
        });
    }
    @Test void panelAndPopupAndMenuItemUseFullNamedRadii() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (JComponent component : List.of(new Panels.Surface(context), new Menus.Popup(context))) {
                    component.setBorder(javax.swing.BorderFactory.createEmptyBorder()); component.setSize(UIScale.scale(180), UIScale.scale(80));
                    assertThat((ButtonsTest.paint(component).getRGB(0, UIScale.scale(2)) >>> 24) & 255).isLessThan(200);
                }
                var item = new Menus.Item(context); item.setText(""); item.setSize(UIScale.scale(180), UIScale.scale(28)); item.getModel().setArmed(true);
                assertThat((ButtonsTest.paint(item).getRGB(0, UIScale.scale(2)) >>> 24) & 255).as("a square highlight").isEqualTo(255);
            } finally { context.icons().close(); context.binding().close(); }
        });
    }
    @Test void sheetBottomCornersUseFullWindowRadius() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(new Form("sheet", ControlState.READY, FormKind.SHEET, "Sheet", Optional.empty(), List.of(), Optional.empty(), List.of())), e -> {})) {
                var surface = view.control("sheet").component; surface.setSize(UIScale.scale(180), UIScale.scale(80));
                assertThat((ButtonsTest.paint(surface).getRGB(0, UIScale.scale(80-3)) >>> 24) & 255).isLessThan(200);
            } finally { context.icons().close(); context.binding().close(); }
        });
    }
    private static final class FocusManager extends DefaultKeyboardFocusManager { Component target; @Override public Component getFocusOwner() { return target; } }
    @Test void formRowHelperWrapsAndDetailHasSourceInsets() {
        Edt.run(() -> {
            var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false);
            try (var server = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("new-file-server"), e -> {});
                 var vault = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("credential-vault"), e -> {})) {
                KitFixtures.render(server, UIScale.scale(540), UIScale.scale(730), 1);
                var helper = server.control("account-help").component;
                assertThat(helper.getHeight()).as("caption must wrap in its real assigned control column").isGreaterThan(helper.getFontMetrics(helper.getFont()).getHeight());
                KitFixtures.render(vault, UIScale.scale(900), UIScale.scale(555), 1);
                var identity = vault.control("identity").component;
                var bounds = SwingUtilities.convertRectangle(identity.getParent(), identity.getBounds(), vault);
                int detailX = m(dev.jasper.ui.metrics.DesignMetrics.Role.MANAGER_SIDEBAR_WIDTH) + m(dev.jasper.ui.metrics.DesignMetrics.Role.MANAGER_LIST_WIDTH) + m(dev.jasper.ui.metrics.DesignMetrics.Role.DETAIL_PADDING);
                assertThat(bounds.x).as("sidebar + list + detail inset").isEqualTo(UIScale.scale(detailX));
                assertThat(bounds.y).isEqualTo(UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.DETAIL_PADDING)));
                assertThat(bounds.width).isEqualTo(UIScale.scale(900 - detailX - m(dev.jasper.ui.metrics.DesignMetrics.Role.DETAIL_PADDING)));
            } finally { context.icons().close(); base.icons().close(); base.binding().close(); }
        });
    }
    @Test void menuCategoryHuesAreExplicitAndLegacyDefaultsRemainNeutral() {
        Edt.run(() -> {
            var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false);
            try (var view = new KitView(context, dev.jasper.ui.gallery.FormFixtures.create("vault-add-menu"), e -> {})) {
                var descriptors = ((Nodes.MenuButton)view.control("add-menu").node).items();
                assertThat(descriptors.stream().filter(i -> !i.separator()).map(dev.jasper.ui.model.MenuItem::hue)).containsExactly(Nodes.Hue.BLUE, Nodes.Hue.GREEN, Nodes.Hue.AMBER, Nodes.Hue.PURPLE);
                var popup = view.control("add-menu").component.getComponentPopupMenu();
                int index = 0;
                for (var item : descriptors) {
                    var component = popup.getComponent(index++);
                    if (component instanceof JMenuItem menu) {
                        var actual = new java.awt.image.BufferedImage(UIScale.scale(18), UIScale.scale(18), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var expected = new java.awt.image.BufferedImage(UIScale.scale(18), UIScale.scale(18), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var a = actual.createGraphics(); var b = expected.createGraphics();
                        try { menu.getIcon().paintIcon(menu, a, 0, 0); context.icons().tile(item.icon(), context.theme().semantic(), item.hue(), UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.MENU_TILE)), false).paintIcon(menu, b, 0, 0); }
                        finally { a.dispose(); b.dispose(); }
                        assertThat(actual.getRGB(0,0,actual.getWidth(),actual.getHeight(),null,0,actual.getWidth())).isEqualTo(expected.getRGB(0,0,expected.getWidth(),expected.getHeight(),null,0,expected.getWidth()));
                    }
                }
                assertThat(new dev.jasper.ui.model.MenuItem("old", "Old", "key", "old", true, false).hue()).isEqualTo(Nodes.Hue.NEUTRAL);
            } finally { context.icons().close(); base.icons().close(); base.binding().close(); }
        });
    }
    @Test void actualDirtyRegionRepaintsExternalHaloOnFocusMouseLossAndRemoval() {
        Edt.run(() -> {
            var old = UIManager.getLookAndFeel(); var oldFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var oldRepaint = RepaintManager.currentManager(null); var repaint = new DirtyRegions();
            var manager = new FocusManager(); var context = KitFixtures.context();
            try {
                FlatLafInstaller.install(context.theme()); KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
                RepaintManager.setCurrentManager(repaint);
                var field = new Field("field", ControlState.READY, FieldKind.TEXT, "", "Value", "Label", "");
                var form = new Form("form", ControlState.READY, FormKind.FULL, "Form", Optional.empty(), List.of(new FormRow("row", ControlState.READY, "Label", "", field, Optional.empty())), Optional.empty(), List.of(), "cancel");
                try (var view = new KitView(context, new ViewSpec(form), e -> {})) {
                    view.setOpaque(true); view.setBackground(context.theme().semantic().color(SemanticColor.SURFACE_PANEL));
                    var image = KitFixtures.render(view, UIScale.scale(540), UIScale.scale(180), 1);
                    var control = (TextFields.FieldControl)view.control("field");
                    var bounds = SwingUtilities.convertRectangle(control.surface.getParent(), control.surface.getBounds(), view);
                    var probe = new Point(bounds.x + bounds.width / 2, bounds.y - UIScale.scale(2));
                    repaint.clear(); manager.target = control.input;
                    fireFocus(control.input, FocusEvent.FOCUS_GAINED);
                    incremental(view, image, repaint, probe);
                    repaint.clear();
                    var mouse = new java.awt.event.MouseEvent(control.input, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, 2, 2, 1, false);
                    for (var listener : control.input.getMouseListeners()) listener.mousePressed(mouse);
                    incremental(view, image, repaint, probe);
                    repaint.clear(); fireFocus(control.input, FocusEvent.FOCUS_GAINED);
                    incremental(view, image, repaint, probe);
                    repaint.clear(); manager.target = null; fireFocus(control.input, FocusEvent.FOCUS_LOST);
                    incremental(view, image, repaint, probe);
                    repaint.clear(); manager.target = control.input; fireFocus(control.input, FocusEvent.FOCUS_GAINED);
                    incremental(view, image, repaint, probe);
                    repaint.clear(); view.update(new ViewSpec(new Nodes.Stack("replacement", ControlState.READY, LayoutRole.VERTICAL, List.of())));
                    incremental(view, image, repaint, probe);
                    manager.target = null; fireFocus(control.input, FocusEvent.FOCUS_LOST);
                }
            } finally { RepaintManager.setCurrentManager(oldRepaint); KeyboardFocusManager.setCurrentKeyboardFocusManager(oldFocus); context.icons().close(); context.binding().close(); restore(old); }
        });
    }
    private static void fireFocus(JComponent target, int id) {
        var event = new FocusEvent(target, id, false, null, FocusEvent.Cause.TRAVERSAL_FORWARD);
        for (var listener : target.getFocusListeners()) { if (id == FocusEvent.FOCUS_GAINED) listener.focusGained(event); else listener.focusLost(event); }
    }
    private static void incremental(KitView view, java.awt.image.BufferedImage image, DirtyRegions repaint, Point probe) {
        var dirty = repaint.getDirtyRegion(view);
        assertThat(dirty.contains(probe)).as("actual owning-view dirty region %s must include halo pixel %s", dirty, probe).isTrue();
        var g = image.createGraphics(); try { g.setClip(dirty); view.paint(g); } finally { g.dispose(); }
        var full = KitFixtures.render(view, view.getWidth(), view.getHeight(), 1);
        assertThat(image.getRGB(probe.x, probe.y)).as("incremental halo equals full paint").isEqualTo(full.getRGB(probe.x, probe.y));
    }
    /** Instrument actual JComponent.repaint traffic; headless undisplayable roots do not enqueue native paints. */
    private static final class DirtyRegions extends RepaintManager {
        final java.util.Map<JComponent, Rectangle> dirty = new java.util.IdentityHashMap<>();
        @Override public void addDirtyRegion(JComponent c, int x, int y, int w, int h) { if (w > 0 && h > 0) dirty.merge(c, new Rectangle(x,y,w,h), Rectangle::union); super.addDirtyRegion(c,x,y,w,h); }
        @Override public Rectangle getDirtyRegion(JComponent c) { return new Rectangle(dirty.getOrDefault(c, new Rectangle())); }
        void clear() { dirty.clear(); }
    }
    private static void restore(LookAndFeel laf) { try { UIManager.setLookAndFeel(laf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); } }
}
