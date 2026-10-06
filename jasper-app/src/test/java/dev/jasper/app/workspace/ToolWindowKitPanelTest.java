package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.dock.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowKitPanelTest {
    @Test void kitPanelRetainsOneHeaderAndHostActionsAcrossHideAndRemount() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Host action leaked"); })) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var sources = new java.util.IdentityHashMap<JComponent, String>();
            area.dragSource = (component, id) -> { assertThat(sources.put(component, id)).isNull(); };
            area.releaseDragSource = component -> assertThat(sources.remove(component)).isNotNull();
            var hidden = new ArrayList<String>();
            area.onHide = hidden::add;
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            assertThat(area.headerOf(Slot.LEFT_TOP)).as("kit header replaces outer legacy header").isNull();
            var header = view.panelHeader("panel");
            assertThat(buttons(header)).hasSize(2);
            assertThat(sources).containsKey(header);
            buttons(header).stream().filter(b -> "Hide".equals(b.getToolTipText())).findFirst().orElseThrow().doClick();
            assertThat(hidden).containsExactly(DockTestSupport.id(Slot.LEFT_TOP));
            DockTestSupport.hide(area, Slot.LEFT_TOP);
            assertThat(buttons(header)).isEmpty();
            assertThat(sources).isEmpty();
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            area.sync(); area.sync();
            assertThat(view.panelHeader("panel")).isSameAs(header);
            assertThat(buttons(header)).hasSize(2);
            buttons(header).stream().filter(b -> "Hide".equals(b.getToolTipText())).findFirst().orElseThrow().doClick();
            assertThat(hidden).hasSize(2);
            view.update(spec("replacement", "replacement-header"));
            var replacement = view.panelHeader("replacement");
            assertThat(replacement).isNotSameAs(header);
            assertThat(sources).doesNotContainKey(header).containsKey(replacement);
            assertThat(buttons(replacement)).hasSize(2);
            view.close(); area.sync(); area.close();
            assertThat(sources).isEmpty();
        }
    }
    @Test void everyHostActionRunsExactlyOnceAndOneTitleAndCountRowShows() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Host action leaked"); })) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var hidden = new ArrayList<String>();
            var options = new ArrayList<JPopupMenu>();
            var anchors = new ArrayList<JComponent>();
            area.onHide = hidden::add;
            area.showOptions = (anchor, menu) -> { anchors.add(anchor); options.add(menu); };
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            var header = view.panelHeader("panel");
            assertThat(texts(area, "Hosts")).as("one visible title").hasSize(1);
            assertThat(texts(area, "3")).as("one visible count").hasSize(1);
            assertThat(texts(header, "Hosts")).as("the title row is the borrowed kit header").hasSize(1);
            var hostButtons = buttons(header);
            assertThat(hostButtons).extracting(AbstractButton::getToolTipText).containsExactly("Options", "Hide");
            hostButtons.get(0).doClick();
            assertThat(options).as("Options ran once").hasSize(1);
            assertThat(hidden).as("Options does not hide").isEmpty();
            assertThat(anchors).containsExactly(hostButtons.get(0));
            assertThat(ToolWindowHeaderTest.item(options.getFirst(), "Hide").isEnabled()).isTrue();
            hostButtons.get(1).doClick();
            assertThat(hidden).as("Hide ran once").containsExactly(DockTestSupport.id(Slot.LEFT_TOP));
            assertThat(options).as("Hide does not open Options").hasSize(1);
            area.close();
        }
    }

    @Test void contentOnlyUpdatesKeepTheBorrowedHeaderWithoutRelayingOutTheDock() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> {})) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            int[] relayouts = {0};
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            area.onMinimumSizeChanged = () -> relayouts[0]++;
            var header = view.panelHeader("panel");
            var host = header.getParent();
            for (int i = 0; i < 5; i++) view.update(spec("panel", "header", "Progress " + i));
            assertThat(relayouts[0]).as("content-only updates leave the dock alone").isZero();
            assertThat(view.panelHeader("panel")).isSameAs(header);
            assertThat(header.getParent()).isSameAs(host);
            assertThat(buttons(header)).hasSize(2);
            view.update(spec("replacement", "replacement-header", "Done"));
            assertThat(relayouts[0]).as("a new header remounts once").isEqualTo(1);
            assertThat(buttons(view.panelHeader("replacement"))).hasSize(2);
            area.close();
        }
    }

    private static final Icon ARTWORK = new Icon() {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) { g.fillRect(x, y, 16, 16); }
    };

    private static dev.jasper.app.contributions.PanelHeaderState metadata(String detail, boolean active, List<String> calls, String... labels) {
        var actions = new ArrayList<dev.jasper.app.contributions.PanelHeaderState.Action>();
        for (String label : labels) actions.add(new dev.jasper.app.contributions.PanelHeaderState.Action(label, ARTWORK, () -> calls.add(label + ":" + detail)));
        return new dev.jasper.app.contributions.PanelHeaderState(detail, active, actions);
    }

    private static AbstractButton button(java.awt.Container root, String name) {
        return buttons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElse(null);
    }

    private static JLabel detail(java.awt.Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && label.getName() != null && label.getName().endsWith(".host-detail")) return label;
            if (child instanceof java.awt.Container nested) { var found = detail(nested); if (found != null) return found; }
        }
        return null;
    }

    private static java.awt.Color dot(JLabel detail) {
        var icon = detail.getIcon();
        var image = new java.awt.image.BufferedImage(icon.getIconWidth() + 2, icon.getIconHeight() + 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(detail, g, 0, 0); } finally { g.dispose(); }
        return new java.awt.Color(image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2), true);
    }

    /** SDK 0.14.2 PanelHost.setHeader on a kit panel: main's native header detail, dot and actions in the one borrowed row. */
    @Test void panelHeaderMetadataShowsInTheBorrowedKitHeaderBeforeTheHostActions() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Header action leaked"); })) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var calls = new ArrayList<String>();
            String id = DockTestSupport.id(Slot.LEFT_TOP);
            area.setHeader(id, metadata("prod", true, calls, "New session", "Import sessions"));
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            var header = view.panelHeader("panel");
            assertThat(area.headerOf(Slot.LEFT_TOP)).as("no native header beside the borrowed one").isNull();
            assertThat(texts(area, "Hosts")).as("one title row").hasSize(1);
            assertThat(texts(area, "prod")).as("one detail").hasSize(1);
            assertThat(texts(header, "prod")).as("the detail is in the borrowed header").hasSize(1);
            var palette = bindings.context(ThemeRoute.CHROME).theme().semantic();
            var detail = detail(header);
            assertThat(detail.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
            assertThat(dot(detail)).isEqualTo(SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
            assertThat(buttons(header)).extracting(b -> b.getAccessibleContext().getAccessibleName())
                .containsExactly("New session", "Import sessions", "Options", "Hide");
            var created = button(header, "New session");
            assertThat(created.isFocusable()).isTrue();
            assertThat(created.getIcon()).isSameAs(ARTWORK);
            created.doClick();
            assertThat(calls).as("invoked once").containsExactly("New session:prod");

            area.setHeader(id, metadata("staging", false, calls, "New session", "Import sessions"));
            assertThat(button(header, "New session")).as("a refresh keeps the control").isSameAs(created);
            assertThat(detail.getText()).isEqualTo("staging");
            assertThat(detail.getIcon()).isNull();
            created.doClick();
            assertThat(calls).containsExactly("New session:prod", "New session:staging");

            area.setHeader(id, dev.jasper.app.contributions.PanelHeaderState.EMPTY);
            assertThat(buttons(header)).extracting(AbstractButton::getToolTipText).containsExactly("Options", "Hide");
            assertThat(detail.isVisible()).isFalse();
            assertThat(created.getActionListeners()).isEmpty();
            created.doClick();
            assertThat(calls).hasSize(2);
            area.close();
        }
    }

    @Test void panelHeaderMetadataIsReleasedOnHideReappliedOnReshowAndReplacementAndClearedOnClose() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> {})) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var calls = new ArrayList<String>();
            String id = DockTestSupport.id(Slot.LEFT_TOP);
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            area.setHeader(id, metadata("3", false, calls, "New session"));
            var header = view.panelHeader("panel");
            var first = button(header, "New session");
            assertThat(first).as("metadata set while showing reaches the borrowed header").isNotNull();

            DockTestSupport.hide(area, Slot.LEFT_TOP);
            assertThat(buttons(header)).as("hiding releases host and header actions").isEmpty();
            assertThat(first.getActionListeners()).isEmpty();
            assertThat(detail(header).isVisible()).isFalse();
            area.setHeader(id, metadata("4", true, calls, "New session"));
            assertThat(buttons(header)).as("metadata is retained, not shown, while hidden").isEmpty();

            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            area.sync(); area.sync();
            assertThat(buttons(header)).extracting(b -> b.getAccessibleContext().getAccessibleName())
                .containsExactly("New session", "Options", "Hide");
            assertThat(detail(header).getText()).isEqualTo("4");
            button(header, "New session").doClick();
            assertThat(calls).containsExactly("New session:4");

            view.update(spec("replacement", "replacement-header"));
            var replacement = view.panelHeader("replacement");
            assertThat(buttons(replacement)).extracting(b -> b.getAccessibleContext().getAccessibleName())
                .as("a replacement header is borrowed with the metadata").containsExactly("New session", "Options", "Hide");
            assertThat(texts(area, "4")).hasSize(1);
            var current = button(replacement, "New session");
            current.doClick();
            assertThat(calls).hasSize(2);

            area.removeContent(id);
            area.sync();
            assertThat(buttons(replacement)).isEmpty();
            assertThat(current.getActionListeners()).isEmpty();
            area.setContent(id, "Hosts", view);
            area.sync();
            assertThat(buttons(replacement)).as("removed content forgets its metadata").extracting(AbstractButton::getToolTipText)
                .containsExactly("Options", "Hide");

            area.setHeader(id, metadata("5", false, calls, "New session"));
            var last = button(replacement, "New session");
            view.close();
            area.setHeader(id, metadata("6", false, calls, "New session"));
            area.sync();
            area.close();
            assertThat(last.getActionListeners()).isEmpty();
            last.doClick();
            assertThat(calls).hasSize(2);
        }
    }

    @Test void aThemeChangeRestylesTheBorrowedHeaderDetailAndKeepsItsActions() {
        ThemeManager.install(Theme.DARK);
        var dark = ThemeManager.generated(Theme.DARK);
        var light = ThemeManager.generated(Theme.JASPER_LIGHT);
        try (var bindings = new AppearanceBindings(new ThemePair(dark, dark), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> {})) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var calls = new ArrayList<String>();
            String id = DockTestSupport.id(Slot.LEFT_TOP);
            area.setHeader(id, metadata("prod", true, calls, "New session"));
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            var header = view.panelHeader("panel");
            var created = button(header, "New session");
            bindings.binding().publish(new ThemePair(light, light));
            area.refreshTheme();
            var palette = light.semantic();
            var detail = detail(header);
            assertThat(detail.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_SECONDARY));
            assertThat(dot(detail)).isEqualTo(SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
            assertThat(button(header, "New session")).isSameAs(created);
            created.doClick();
            assertThat(calls).containsExactly("New session:prod");
            area.close();
        }
    }

    private static List<JComponent> texts(java.awt.Container root, String text) {
        var result = new ArrayList<JComponent>();
        for (var child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JLabel label && text.equals(label.getText())) result.add(label);
            if (child instanceof javax.swing.text.JTextComponent field && text.equals(field.getText())) result.add(field);
            if (child instanceof java.awt.Container container) result.addAll(texts(container, text));
        }
        return result;
    }
    private static List<AbstractButton> buttons(java.awt.Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button) result.add(button);
            else if (child instanceof java.awt.Container container) result.addAll(buttons(container));
        }
        return result;
    }
    private static ViewSpec spec() { return spec("panel", "header"); }
    private static ViewSpec spec(String panel, String header) { return spec(panel, header, "Contents"); }
    private static ViewSpec spec(String panel, String header, String body) {
        return new ViewSpec(new Nodes.Panel(panel, ControlState.READY,
            new Nodes.Header(header, ControlState.READY, "Hosts", OptionalInt.of(3), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("body", ControlState.READY, body, Nodes.TextRole.BODY), Optional.empty()));
    }
}
