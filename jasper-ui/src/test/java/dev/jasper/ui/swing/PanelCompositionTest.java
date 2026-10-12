package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import javax.swing.*;

class PanelCompositionTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Button button(String id, String label) {
        return new Nodes.Button(id, READY, label, "", ButtonKind.SECONDARY, id, "");
    }

    static Header header(int count) {
        return new Header(
                "header",
                READY,
                "Sessions",
                OptionalInt.of(count),
                Optional.empty(),
                Optional.of(button("add", "+")),
                List.of());
    }

    static Nodes.Panel panel(int count, Node body) {
        return new Nodes.Panel(
                "panel",
                READY,
                header(count),
                Optional.of(
                        new Field(
                                "query",
                                READY,
                                FieldKind.SEARCH,
                                "",
                                "Search sessions",
                                "Search sessions",
                                "")),
                body,
                Optional.of(
                        new Toggle(
                                "footer", READY, "Follow terminal folder", true, "follow", true)));
    }

    static Node empty() {
        return new Empty(
                "empty",
                READY,
                new Nodes.Icon(
                        "empty-icon", READY, "tunnel", Hue.NEUTRAL, IconSize.EMPTY, "Tunnels"),
                "No Tunnels",
                "Forward a local port to a remote host.",
                List.of(button("new", "New Tunnel")));
    }

    static void layout(Container c) {
        c.doLayout();
        for (var child : c.getComponents()) if (child instanceof Container cc) layout(cc);
    }

    static int s(int n) {
        return com.formdev.flatlaf.util.UIScale.scale(n);
    }

    static Rectangle bounds(Component c, Container parent) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), parent);
    }

    @Test
    void headerCountIsInlineAndEmptyActionCentersWithinFlexibleBody() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, new ViewSpec(panel(6, empty())), e -> {})) {
                        view.setSize(s(320), s(480));
                        layout(view);
                        var header = view.panelHeader("panel");
                        var title = find(header, "header.title", JLabel.class);
                        var count = find(header, "header.count", JLabel.class);
                        assertThat(title.getY() + title.getHeight() / 2)
                                .isEqualTo(count.getY() + count.getHeight() / 2);
                        assertThat(count.getX()).isEqualTo(title.getX() + title.getWidth() + s(6));
                        var body = find(view, "empty", JComponent.class);
                        var content = find(view, "empty.title", JComponent.class).getParent();
                        // A panel body's empty state sits its optical lift above the exact middle, as the panel references do.
                        assertThat(content.getY() + content.getHeight() / 2)
                                .isCloseTo(body.getHeight() / 2 - s(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_EMPTY_LIFT)), within(1));
                        var footer = find(view, "footer", JComponent.class);
                        var fb = bounds(footer, view);
                        assertThat(fb.y + fb.height).isLessThanOrEqualTo(view.getHeight());
                        assertThat(fb.y).isGreaterThan(s(420));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void countAndSearchHideOnlyForCollectionEmptyWhileIdentityAndQuerySurvive() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, new ViewSpec(panel(6, empty())), e -> {})) {
                        var h = view.panelHeader("panel");
                        var footer = find(view, "footer", JComponent.class);
                        var query = find(view, "query", JComponent.class);
                        var editor = editor(view, "query");
                        var doc = editor.getDocument();
                        editor.setText("missing");
                        view.update(
                                new ViewSpec(
                                        panel(
                                                6,
                                                new Text(
                                                        "filtered",
                                                        READY,
                                                        "No results",
                                                        TextRole.BODY))));
                        assertThat(query.isVisible()).isTrue();
                        assertThat(find(h, "header.count", JLabel.class).isVisible()).isTrue();
                        view.update(new ViewSpec(panel(0, empty())));
                        assertThat(query.isVisible()).isFalse();
                        assertThat(find(h, "header.count", JLabel.class).isVisible()).isFalse();
                        view.update(new ViewSpec(panel(6, empty())));
                        assertThat(query.isVisible()).isTrue();
                        assertThat(view.panelHeader("panel")).isSameAs(h);
                        assertThat(find(view, "footer", JComponent.class)).isSameAs(footer);
                        assertThat(editor(view, "query").getDocument()).isSameAs(doc);
                        assertThat(editor.getText()).isEqualTo("missing");
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void shortAndLargeFontPanelsKeepFooterReachableAndBodyScrollable() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var old = UIManager.get("defaultFont");
                    try (var view = new KitView(ctx, new ViewSpec(panel(0, empty())), e -> {})) {
                        for (int fontSize : new int[] {13, 24}) {
                            UIManager.put(
                                    "defaultFont", new Font(Font.DIALOG, Font.PLAIN, fontSize));
                            for (int[] size : new int[][] {{320, 480}, {240, 180}}) {
                                view.setSize(s(size[0]), s(size[1]));
                                layout(view);
                                var footer = find(view, "footer", JComponent.class);
                                Rectangle b = bounds(footer, view);
                                assertThat(b.y).isGreaterThanOrEqualTo(0);
                                assertThat(b.y + b.height).isLessThanOrEqualTo(view.getHeight());
                                assertThat(b.height)
                                        .isGreaterThanOrEqualTo(footer.getPreferredSize().height);
                                var scroll = find(view, "panel.body-scroll", JScrollPane.class);
                                assertThat(scroll.getHeight()).isPositive();
                                if (size[1] == 180) {
                                    assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
                                    scroll.getVerticalScrollBar()
                                            .setValue(scroll.getVerticalScrollBar().getMaximum());
                                    layout(view);
                                    var action = find(view, "new", JComponent.class);
                                    Rectangle ab = bounds(action, scroll.getViewport());
                                    assertThat(ab.y + ab.height)
                                            .isLessThanOrEqualTo(scroll.getViewport().getHeight());
                                }
                            }
                        }
                    } finally {
                        if (old == null) UIManager.getDefaults().remove("defaultFont");
                        else UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void hostActionsHaveSeparateCallbacksStableControlsAndOwnedTeardown() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var plugin = new ArrayList<UiEvent>();
                    var host = new ArrayList<UiEvent>();
                    var view = new KitView(ctx, new ViewSpec(panel(6, empty())), plugin::add);
                    try {
                        view.setPanelHostActions(
                                "panel", List.of(button("collapse", "Collapse")), host::add);
                        var b = find(view, "collapse", JButton.class);
                        b.doClick(0);
                        assertThat(host).containsExactly(new UiEvent.Action("collapse"));
                        assertThat(plugin).isEmpty();
                        view.setPanelHostActions(
                                "panel", List.of(button("collapse", "Restore")), host::add);
                        assertThat(find(view, "collapse", JButton.class)).isSameAs(b);
                        find(view, "add", JButton.class).doClick(0);
                        assertThat(plugin).containsExactly(new UiEvent.Action("add"));
                        view.setPanelHostActions("panel", List.of(), host::add);
                        b.doClick(0);
                        assertThat(host).hasSize(1);
                        assertThat(b.getActionListeners()).isEmpty();
                        view.setPanelHostActions(
                                "panel", List.of(button("placement", "Placement")), host::add);
                        var last = find(view, "placement", JButton.class);
                        view.close();
                        last.doClick(0);
                        assertThat(last.getActionListeners()).isEmpty();
                        assertThat(host).hasSize(1);
                    } finally {
                        view.close();
                        release(ctx);
                    }
                });
    }

    @Test
    void fixtureActionsChooseAddAndConfirmPendingFailureThroughRealControls() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try {
                        try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("tunnels", ctx)) {
                            var view = demo.view();
                            view.setSize(s(320), s(480));
                            layout(view);
                            var h = view.panelHeader("tunnels");
                            find(view, "tunnels-new", JButton.class).doClick(0);
                            layout(view);
                            assertThat(
                                            ((ItemList) ((Nodes.Panel) view.spec().root()).body())
                                                    .items())
                                    .hasSize(1);
                            assertThat(view.panelHeader("tunnels")).isSameAs(h);
                        }
                        try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("sftp", ctx)) {
                            var view = demo.view();
                            view.setSize(s(320), s(480));
                            layout(view);
                            var footer = find(view, "sftp-follow", JComponent.class);
                            var h = view.panelHeader("sftp");
                            var menu = (Menus.MenuControl) view.control("sftp-choose");
                            menu.main.doClick(0);
                            assertThat(view.popupOpen()).isTrue();
                            ((JMenuItem) menu.popup.getComponent(0)).doClick(0);
                            layout(view);
                            assertThat(((Nodes.Panel) view.spec().root()).body())
                                    .isInstanceOf(Nodes.Table.class);
                            assertThat(find(view, "sftp-follow", JComponent.class))
                                    .isSameAs(footer);
                            assertThat(view.panelHeader("sftp")).isSameAs(h);
                            assertThat(view.popupOpen()).isFalse();
                            ((Switches.ToggleControl) view.control("sftp-follow"))
                                    .button.doClick(0);
                            ctx.binding()
                                    .publish(
                                            new dev.jasper.ui.theme.ThemePair(
                                                    KitFixtures.light(), KitFixtures.light()));
                            assertThat(((Toggle) view.control("sftp-follow").node).checked())
                                    .isFalse();
                            assertThat(find(view, "sftp-follow", JComponent.class))
                                    .isSameAs(footer);
                        }
                        try (var demo =
                                dev.jasper.ui.gallery.PanelFixtures.mount("fileservers", ctx)) {
                            var view = demo.view();
                            view.setSize(s(240), s(180));
                            layout(view);
                            var toggle = (Switches.ToggleControl) view.control("server-toggle");
                            toggle.button.doClick(0);
                            assertThat(demo.pending()).isTrue();
                            assertThat(toggle.button.isEnabled()).isFalse();
                            assertThat(((Toggle) view.control("server-toggle").node).checked())
                                    .isFalse();
                            demo.completePending(false);
                            layout(view);
                            assertThat(demo.pending()).isFalse();
                            assertThat(((Toggle) view.control("server-toggle").node).checked())
                                    .isFalse();
                            assertThat(view.control("server-toggle").node.state().error())
                                    .isNotBlank();
                            ((Switches.ToggleControl) view.control("server-toggle"))
                                    .button.doClick(0);
                            demo.completePending(true);
                            layout(view);
                            assertThat(((Toggle) view.control("server-toggle").node).checked())
                                    .isTrue();
                            var count = find(view, "fileservers-header.count", JLabel.class);
                            assertThat(count.getText()).isEqualTo("1");
                            var title = find(view, "server-title", JComponent.class);
                            var switchBounds =
                                    bounds(view.control("server-toggle").component, view);
                            assertThat(bounds(title, view).x + bounds(title, view).width)
                                    .isLessThanOrEqualTo(switchBounds.x);
                        }
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void sessionsFilterAndHostCollapseRetainMountedQueryAndFooter() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("sessions", ctx)) {
                        var view = demo.view();
                        view.setSize(s(320), s(480));
                        layout(view);
                        var query = editor(view, "sessions-query");
                        var doc = query.getDocument();
                        var footer = find(view, "sessions-footer", JComponent.class);
                        query.setText("missing");
                        assertThat(find(view, "sessions-query", JComponent.class).isVisible())
                                .isTrue();
                        assertThat(((Nodes.Panel) view.spec().root()).header().count().getAsInt())
                                .isEqualTo(6);
                        find(view, "sessions-collapse", JButton.class).doClick(0);
                        find(view, "sessions-collapse", JButton.class).doClick(0);
                        assertThat(editor(view, "sessions-query").getDocument()).isSameAs(doc);
                        assertThat(query.getText()).isEqualTo("missing");
                        assertThat(find(view, "sessions-footer", JComponent.class))
                                .isSameAs(footer);
                        find(view, "sessions-add", JButton.class).doClick(0);
                        assertThat(((Nodes.Panel) view.spec().root()).header().count().getAsInt())
                                .isEqualTo(7);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void hiddenQueryStaysHiddenAcrossAppearanceRefreshAndAbsentCountShowsMountedQuery() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, new ViewSpec(panel(0, empty())), e -> {})) {
                        var query = find(view, "query", JComponent.class);
                        var doc = editor(view, "query").getDocument();
                        ctx.binding()
                                .publish(
                                        new dev.jasper.ui.theme.ThemePair(
                                                KitFixtures.light(), KitFixtures.light()));
                        assertThat(query.isVisible()).isFalse();
                        var p = panel(0, empty());
                        var h = p.header();
                        var unknown =
                                new Header(
                                        h.id(),
                                        h.state(),
                                        h.title(),
                                        OptionalInt.empty(),
                                        h.context(),
                                        h.primary(),
                                        h.overflow());
                        view.update(
                                new ViewSpec(
                                        new Nodes.Panel(
                                                p.id(),
                                                p.state(),
                                                unknown,
                                                p.searchOrNavigation(),
                                                p.body(),
                                                p.footer())));
                        assertThat(query.isVisible()).isTrue();
                        assertThat(editor(view, "query").getDocument()).isSameAs(doc);
                        view.update(new ViewSpec(panel(6, empty())));
                        assertThat(query.isVisible()).isTrue();
                        assertThat(editor(view, "query").getDocument()).isSameAs(doc);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void panelLeavesTenThousandDataRowsVirtualAndRoutesOffscreenInlineFocusAndPopups() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var menu =
                            new MenuButton(
                                    "row-menu",
                                    READY,
                                    "Menu",
                                    "",
                                    ButtonKind.SECONDARY,
                                    "",
                                    List.of(
                                            new dev.jasper.ui.model.MenuItem(
                                                    "run", "Run", "", "run", true, false)),
                                    "");
                    var items =
                            java.util.stream.IntStream.range(0, 10000)
                                    .mapToObj(
                                            i ->
                                                    new ListItem(
                                                            "row" + i,
                                                            Optional.empty(),
                                                            List.of(
                                                                    new Text(
                                                                            "title" + i,
                                                                            READY,
                                                                            "Row " + i,
                                                                            TextRole.BODY)),
                                                            List.of(),
                                                            i == 9999 ? List.of(menu) : List.of()))
                                    .toList();
                    var data =
                            new ItemList(
                                    "data",
                                    READY,
                                    ListKind.SINGLE,
                                    items,
                                    java.util.Set.of(),
                                    "",
                                    List.of());
                    try (var view = new KitView(ctx, new ViewSpec(panel(10000, data)), e -> {})) {
                        view.setSize(s(320), s(480));
                        layout(view);
                        var owner = (ItemLists.ListControl) view.control("data");
                        assertThat(owner.mounted.size()).isLessThan(25);
                        assertThat(ControlRegistry.children(data)).isEmpty();
                        assertThatThrownBy(() -> view.control("missing"))
                                .isInstanceOf(IllegalArgumentException.class);
                        view.focus("row-menu");
                        layout(view);
                        assertThat(owner.mounted.size()).isLessThan(25);
                        var control = (Menus.MenuControl) view.control("row-menu");
                        assertThat(control.component.getParent())
                                .isNotSameAs(find(view, "data", JComponent.class));
                        control.main.doClick(0);
                        assertThat(view.popupOpen()).isTrue();
                        assertThat(view.dismissPopup()).isTrue();
                        var overflow =
                                new Header(
                                        "header",
                                        READY,
                                        "Sessions",
                                        OptionalInt.of(10000),
                                        Optional.empty(),
                                        Optional.empty(),
                                        List.of(
                                                new dev.jasper.ui.model.MenuItem(
                                                        "run", "Run", "", "run", true, false)));
                        var original = panel(10000, data);
                        view.update(
                                new ViewSpec(
                                        new Nodes.Panel(
                                                original.id(),
                                                READY,
                                                overflow,
                                                original.searchOrNavigation(),
                                                data,
                                                original.footer())));
                        var hc = (PanelHeaders.HeaderControl) view.control("header");
                        hc.overflow.main.doClick(0);
                        assertThat(view.popupOpen()).isTrue();
                        assertThat(view.dismissPopup()).isTrue();
                        var popup = hc.overflow.popup;
                        view.update(
                                new ViewSpec(
                                        new Text("replacement", READY, "Closed", TextRole.BODY)));
                        assertThat(popup.isVisible()).isFalse();
                        assertThat(control.main.getActionListeners()).isEmpty();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void fourFixtureLayoutsRenderAtDefaultShortAndEnlargedFontWithPersistentSettingsFooter() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var old = UIManager.get("defaultFont");
                    try {
                        for (int fontSize : new int[] {13, 24}) {
                            UIManager.put(
                                    "defaultFont", new Font(Font.DIALOG, Font.PLAIN, fontSize));
                            for (int[] size : new int[][] {{320, 480}, {240, 180}}) {
                                var sheet =
                                        new java.awt.image.BufferedImage(
                                                4 * s(size[0]),
                                                s(size[1]),
                                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                var graphics = sheet.createGraphics();
                                try {
                                    int index = 0;
                                    for (String name :
                                            List.of("sessions", "tunnels", "sftp", "fileservers")) {
                                        try (var demo =
                                                dev.jasper.ui.gallery.PanelFixtures.mount(
                                                        name, ctx)) {
                                            var view = demo.view();
                                            var image =
                                                    KitFixtures.render(
                                                            view, s(size[0]), s(size[1]), 1);
                                            graphics.drawImage(
                                                    image, index++ * s(size[0]), 0, null);
                                            var header = view.panelHeader(name);
                                            assertThat(header.getHeight())
                                                    .isGreaterThanOrEqualTo(s(38));
                                            var root = (Nodes.Panel) view.spec().root();
                                            if (root.footer().isPresent()) {
                                                var footer =
                                                        find(
                                                                view,
                                                                root.footer().get().id(),
                                                                JComponent.class);
                                                var b = bounds((Component)SwingUtilities.getAncestorOfClass(JScrollPane.class,footer), view);
                                                assertThat(b.height).isPositive();
                                                assertThat(b.y + b.height)
                                                        .isLessThanOrEqualTo(view.getHeight());
                                            }
                                            if (name.equals("sftp")) {
                                                assertThat(
                                                                find(
                                                                        view,
                                                                        "sftp-footer.title",
                                                                        JComponent.class))
                                                        .isNotNull();
                                                assertThat(
                                                                bounds(
                                                                                find(
                                                                                        view,
                                                                                        "sftp-follow",
                                                                                        JComponent
                                                                                                .class),
                                                                                view)
                                                                        .x)
                                                        .isGreaterThan(s(size[0] - 65));
                                            }
                                        }
                                    }
                                } finally {
                                    graphics.dispose();
                                }
                                try {
                                    var path =
                                            java.nio.file.Path.of(
                                                    "build/panel-renders/panels-"
                                                            + size[0]
                                                            + "x"
                                                            + size[1]
                                                            + "-font"
                                                            + fontSize
                                                            + ".png");
                                    java.nio.file.Files.createDirectories(path.getParent());
                                    javax.imageio.ImageIO.write(sheet, "png", path.toFile());
                                } catch (java.io.IOException failure) {
                                    throw new AssertionError(failure);
                                }
                            }
                        }
                    } finally {
                        if (old == null) UIManager.getDefaults().remove("defaultFont");
                        else UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void enlargedShortSessionsScrollNavigationInsteadOfClippingItsControl() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var old = UIManager.get("defaultFont");
                    try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("sessions", ctx)) {
                        UIManager.put("defaultFont", new Font(Font.DIALOG, Font.PLAIN, 24));
                        var view = demo.view();
                        KitFixtures.render(view, s(240), s(180), 1);
                        var query = find(view, "sessions-query", JComponent.class);
                        assertThat(query.getHeight())
                                .isGreaterThanOrEqualTo(query.getPreferredSize().height);
                        var panel = (Panels.PanelControl) view.control("sessions");
                        assertThat(panel.scroll.getVerticalScrollBar().isVisible()).isTrue();
                        panel.scroll
                                .getVerticalScrollBar()
                                .setValue(panel.scroll.getVerticalScrollBar().getMaximum());
                        layout(view);
                        var row = (ItemLists.ListControl) view.control("sessions-body");
                        assertThat(row.scroll.getHeight()).isGreaterThanOrEqualTo(row.rowHeight);
                    } finally {
                        if (old == null) UIManager.getDefaults().remove("defaultFont");
                        else UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void emptyTileAndActionsStayCenteredAfterRecursiveValidation() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, new ViewSpec(panel(0, empty())), e -> {})) {
                        KitFixtures.render(view, s(320), s(480), 1);
                        var tile = find(view, "empty.tile", JComponent.class);
                        int panelTile = dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.PANEL_EMPTY_TILE);
                        assertThat(tile.getWidth()).as("a panel body's compact empty tile").isEqualTo(s(panelTile));
                        assertThat(tile.getHeight()).isEqualTo(s(panelTile));
                        var action = find(view, "new", JComponent.class);
                        var body = find(view, "empty", JComponent.class);
                        assertThat(bounds(action, body).x + action.getWidth() / 2)
                                .isCloseTo(body.getWidth() / 2, within(1));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void headerButtonsUseHeaderActionRoleAndOverflowHasOneGlyph() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, KitFixtures.panel("sessions"), e -> {})) {
                        view.setPanelHostActions(
                                "sessions",
                                List.of(
                                        new Nodes.Button(
                                                "host",
                                                READY,
                                                "",
                                                "collapse",
                                                ButtonKind.GHOST,
                                                "host",
                                                "Collapse")),
                                e -> {});
                        assertThat(
                                        find(view, "sessions-add", JButton.class)
                                                .getPreferredSize()
                                                .height)
                                .isEqualTo(
                                        ThemePainting.height(
                                                ctx,
                                                dev.jasper.ui.metrics.DesignMetrics.Role
                                                        .PANEL_HEADER_ACTION,
                                                dev.jasper.ui.metrics.Typography.Role.PANEL_DETAIL_LABEL));
                        assertThat(find(view, "host", JButton.class).getPreferredSize().height)
                                .isEqualTo(
                                        ThemePainting.height(
                                                ctx,
                                                dev.jasper.ui.metrics.DesignMetrics.Role
                                                        .PANEL_HEADER_ACTION,
                                                dev.jasper.ui.metrics.Typography.Role.PANEL_DETAIL_LABEL));
                        var header = (PanelHeaders.HeaderControl) view.control("sessions-header");
                        assertThat(((Menus.Main) header.overflow.main).menuOnly).isFalse();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void removingOptionalSlotsDetachesClosedWidgetsAndDenseFooterRestoresWhenMoved() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var original = (Nodes.Panel) KitFixtures.panel("sftp").root();
                    try (var view = new KitView(ctx, new ViewSpec(original), e -> {})) {
                        var row = (SettingRows.RowControl) view.control("sftp-footer");
                        var toggle = (Switches.ToggleControl) view.control("sftp-follow");
                        assertThat(row.panelFooter).isTrue();
                        view.update(
                                new ViewSpec(
                                        new Nodes.Panel(
                                                original.id(),
                                                READY,
                                                original.header(),
                                                Optional.empty(),
                                                original.body(),
                                                Optional.empty())));
                        assertThat(find(view, "sftp-navigation", JComponent.class)).isNull();
                        assertThat(find(view, "sftp-footer", JComponent.class)).isNull();
                        assertThat(toggle.button.getActionListeners()).isEmpty();
                        view.update(new ViewSpec(original));
                        var restored = (SettingRows.RowControl) view.control("sftp-footer");
                        view.update(
                                new ViewSpec(
                                        new Nodes.Stack(
                                                "root",
                                                READY,
                                                LayoutRole.VERTICAL,
                                                List.of(original.footer().orElseThrow()))));
                        assertThat(view.control("sftp-footer")).isSameAs(restored);
                        assertThat(restored.panelFooter).isFalse();
                        assertThat(restored.component.getInsets().left).isEqualTo(s(13));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void connectedShortSftpReservesColumnHeaderAndOneReachableDataRowAtEnlargedFont() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var old = UIManager.get("defaultFont");
                    try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("sftp", ctx)) {
                        UIManager.put("defaultFont", new Font(Font.DIALOG, Font.PLAIN, 24));
                        var view = demo.view();
                        var menu = (Menus.MenuControl) view.control("sftp-choose");
                        menu.main.doClick(0);
                        ((JMenuItem) menu.popup.getComponent(0)).doClick(0);
                        KitFixtures.render(view, s(240), s(180), 1);
                        var table = (Tables.TableControl) view.control("sftp-body");
                        assertThat(table.scroll.getViewport().getExtentSize().height)
                                .isGreaterThanOrEqualTo(table.rowHeight);
                        assertThat(table.scroll.getHeight())
                                .isGreaterThanOrEqualTo(
                                        table.header.getPreferredSize().height + table.rowHeight);
                        var panel = (Panels.PanelControl) view.control("sftp");
                        panel.scroll
                                .getVerticalScrollBar()
                                .setValue(panel.scroll.getVerticalScrollBar().getMaximum());
                        KitFixtures.render(view, s(240), s(180), 1);
                        var reachable =
                                SwingUtilities.convertRectangle(
                                        table.scroll.getViewport(),
                                        new Rectangle(
                                                0,
                                                0,
                                                table.scroll.getViewport().getWidth(),
                                                table.rowHeight),
                                        panel.scroll.getViewport());
                        assertThat(reachable.y).isGreaterThanOrEqualTo(0);
                        assertThat(reachable.y + reachable.height)
                                .isLessThanOrEqualTo(panel.scroll.getViewport().getHeight());
                        assertThat(table.mounted).isNotEmpty();
                    } finally {
                        if (old == null) UIManager.getDefaults().remove("defaultFont");
                        else UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void addedFileServerSwitchHasIndependentPendingFailureAndConfirmedState() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var demo = dev.jasper.ui.gallery.PanelFixtures.mount("fileservers", ctx)) {
                        var view = demo.view();
                        KitFixtures.render(view, s(320), s(480), 1);
                        find(view, "fileservers-add", JButton.class).doClick(0);
                        KitFixtures.render(view, s(320), s(480), 1);
                        var original = (Switches.ToggleControl) view.control("server-toggle");
                        var added = (Switches.ToggleControl) view.control("server-1-toggle");
                        added.button.doClick(0);
                        assertThat(demo.pending()).isTrue();
                        assertThat(view.control("server-1-toggle").node.state().busy()).isTrue();
                        assertThat(original.node.state().busy()).isFalse();
                        demo.completePending(false);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isFalse();
                        assertThat(view.control("server-1-toggle").node.state().error())
                                .isNotBlank();
                        assertThat(original.node.state().error()).isEmpty();
                        ((Switches.ToggleControl) view.control("server-1-toggle"))
                                .button.doClick(0);
                        demo.completePending(true);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isTrue();
                        assertThat(((Toggle) view.control("server-toggle").node).checked())
                                .isFalse();
                        ((Switches.ToggleControl) view.control("server-1-toggle"))
                                .button.doClick(0);
                        demo.completePending(false);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isTrue();
                        ((Switches.ToggleControl) view.control("server-1-toggle"))
                                .button.doClick(0);
                        demo.completePending(true);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isFalse();
                        assertThat(((Toggle) view.control("server-toggle").node).checked())
                                .isFalse();
                        original.button.doClick(0);
                        ((Switches.ToggleControl) view.control("server-1-toggle"))
                                .button.doClick(0);
                        assertThat(view.control("server-toggle").node.state().busy()).isTrue();
                        assertThat(view.control("server-1-toggle").node.state().busy()).isTrue();
                        demo.completePending(true);
                        assertThat(((Toggle) view.control("server-toggle").node).checked())
                                .isTrue();
                        assertThat(view.control("server-toggle").node.state().busy()).isFalse();
                        assertThat(view.control("server-1-toggle").node.state().busy()).isTrue();
                        demo.completePending(false);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isFalse();
                        assertThat(view.control("server-1-toggle").node.state().error())
                                .isNotBlank();
                        assertThat(view.control("server-toggle").node.state().error()).isEmpty();
                        ((Switches.ToggleControl) view.control("server-1-toggle"))
                                .button.doClick(0);
                        original.button.doClick(0);
                        demo.completePending(true);
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isTrue();
                        assertThat(((Toggle) view.control("server-toggle").node).checked())
                                .isTrue();
                        assertThat(view.control("server-toggle").node.state().busy()).isTrue();
                        demo.completePending(true);
                        assertThat(((Toggle) view.control("server-toggle").node).checked())
                                .isFalse();
                        assertThat(((Toggle) view.control("server-1-toggle").node).checked())
                                .isTrue();
                        assertThat(demo.pending()).isFalse();
                    } finally {
                        release(ctx);
                    }
                });
    }
    @Test void detailFooterUsesCompactContextWhileManagerDetailKeepsItsPadding() {
        Edt.run(() -> {
            var ctx = KitFixtures.context();
            var detail = new Nodes.Stack("detail", READY, LayoutRole.DETAIL, List.of(
                new Nodes.Stack("identity", READY, LayoutRole.NAVIGATION, List.of(
                    new Nodes.Icon("identity-icon", READY, "server", Hue.NEUTRAL, IconSize.HERO, "Host"),
                    new Nodes.Stack("identity-text", READY, LayoutRole.VERTICAL, List.of(
                        new Text("identity-title", READY, "amp", TextRole.BODY_STRONG),
                        new Text("identity-address", READY, "fixture@example.invalid:22", TextRole.MONO))))),
                new Nodes.Stack("detail-actions", READY, LayoutRole.NAVIGATION, List.of(
                    new Text("status", READY, "Vault locked", TextRole.CAPTION), button("edit", "Edit"), button("connect", "Connect")))));
            try (var view = new KitView(ctx, new ViewSpec(new Nodes.Panel("panel", READY, header(6), Optional.empty(), empty(), Optional.of(detail))), e -> {})) {
                view.setSize(s(260), s(395)); layout(view);
                var mounted = view.control("detail").component;
                assertThat(bounds(mounted, view).x + mounted.getInsets().left).as("one 12px inset, no outer duplicate").isEqualTo(s(12));
                assertThat(mounted.getInsets().top).isEqualTo(s(12));
                assertThat(view.control("identity-icon").component.getPreferredSize().width).isEqualTo(s(28));
                assertThat(view.control("identity-icon").component.getPreferredSize().height).isEqualTo(s(30));
                view.update(new ViewSpec(detail)); layout(view);
                assertThat(view.control("detail").component).isSameAs(mounted);
                assertThat(mounted.getInsets().left).as("manager DETAIL recovers its normal24 inset").isEqualTo(s(24));
                assertThat(view.control("identity-icon").component.getPreferredSize().width).isEqualTo(s(44));
            } finally { release(ctx); }
        });
    }

    @Test void normalHostActionsDoNotMakeTheHeaderWrapDuringPreferredMeasurement() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();
            try(var view=new KitView(ctx,new ViewSpec(panel(6,empty())),e -> {})) {
                view.setPanelHostActions("panel",List.of(button("options","Options"),button("hide","Hide")),e -> {});
                view.setSize(s(400),s(395));layout(view);
                assertThat(view.panelHeader("panel").getHeight()).as("normal action row stays the panel header height").isEqualTo(s(38));
                var title=find(view,"header.title",JLabel.class);
                assertThat(title.getY()).as("32px content lane leaves6px below it").isEqualTo(s(32)/2-title.getHeight()/2);
            }finally{release(ctx);}
        });
    }

    @Test void footerDividerIsOneDevicePixelAtEachCaptureScale() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();
            try {
                // An ordinary footer is the raised surface under its divider; a settings footer's rules belong to its rows.
                var footer=new PanelFooters.Footer(ctx,"footer");footer.setSize(40,20);
                for(int scale:new int[]{1,2}) {
                    var image=new java.awt.image.BufferedImage(40*scale,20*scale,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g=image.createGraphics();try{g.scale(scale,scale);footer.paint(g);}finally{g.dispose();}
                    int fill=ctx.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL_RAISED).getRGB();
                    int border=dev.jasper.ui.testsupport.KitFixtures.over(ctx.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE),new java.awt.Color(fill,true));
                    assertThat(image.getRGB(20*scale,0)).as("opaque divider at device scale %s",scale).isEqualTo(border);
                    assertThat(image.getRGB(20*scale,1)).as("fill immediately after one-device divider at scale %s",scale).isEqualTo(fill);
                }
            } finally {release(ctx);}
        });
    }

    @Test void virtualizedFooterReservesOneRowRatherThanTheWholeCollection() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();
            var rows=new ItemList("transfers",READY,ListKind.SINGLE,java.util.stream.IntStream.range(0,200).mapToObj(i->RemoteKitTest.contextual("transfer"+i)).toList(),java.util.Set.of(),"",List.of());
            try(var view=new KitView(ctx,new ViewSpec(new Nodes.Panel("panel",READY,header(1),Optional.empty(),empty(),Optional.of(rows))),e->{})) {
                view.setSize(s(260),s(90));layout(view);
                assertThat(view.panelHeader("panel").getHeight()).isEqualTo(s(38));
                var footer=(Panels.PanelControl)view.control("panel");
                var list=(ItemLists.ListControl)view.control("transfers");
                assertThat(footer.footer.interactiveHeight(footer.footer)).isLessThanOrEqualTo(list.rowHeight);
                assertThat(footer.footerScroll.getHeight()).isGreaterThanOrEqualTo(list.rowHeight);
            }finally{release(ctx);}
        });
    }

}
