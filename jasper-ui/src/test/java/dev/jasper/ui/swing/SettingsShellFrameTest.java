package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1 and plan 9a's "Mock measurements": the classic frame's geometry and surfaces. */
class SettingsShellFrameTest {
    static final SettingsShell.Frame CLASSIC = new SettingsShell.Frame(false, true);

    /** A shell of the Settings dialog's own views, as the app's SettingsView builds them. */
    record Shell(SettingsShell shell, List<KitView> views) implements AutoCloseable {
        @Override public void close() { shell.close(); views.forEach(KitView::close); }
    }

    static Shell shell(KitContext context, SettingsShell.Frame frame) {
        var search = new KitView(context, new ViewSpec(new Nodes.Field("search", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search settings",
            "Search settings", "")), event -> { });
        var tree = new Nodes.Tree("pages", ControlState.READY, List.of(new TreeItem("ab", "Appearance & Behavior", Optional.empty(), false,
            List.of(new TreeItem("appearance", "Appearance", Optional.empty(), true, List.of()))), new TreeItem("keymap", "Keymap", Optional.empty(), true, List.of())),
            Set.of("ab"), Set.of("appearance"), "");
        var navigation = new KitView(context, new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree))), event -> { });
        var header = new KitView(context, new ViewSpec(new Nodes.Stack("header", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Stack("history", ControlState.READY, Nodes.LayoutRole.JOINED_ACTIONS, List.of(
                new Nodes.Button("back", ControlState.READY, "", "chevron.left", Nodes.ButtonKind.GHOST, "back", "Back"),
                new Nodes.Button("forward", new ControlState(true, false, false, ""), "", "chevron.right", Nodes.ButtonKind.GHOST, "forward", "Forward"))),
            new Nodes.Text("breadcrumb-parent", ControlState.READY, "Appearance & Behavior", Nodes.TextRole.DESCRIPTION),
            new Nodes.Icon("breadcrumb-separator", ControlState.READY, "chevron.right", Nodes.Hue.NONE, Nodes.IconSize.CAPTION, "Breadcrumb separator"),
            new Nodes.Text("breadcrumb", ControlState.READY, "Appearance", Nodes.TextRole.WINDOW_TITLE)))), event -> { });
        var banner = new KitView(context, new ViewSpec(new Nodes.Text("banner-text", ControlState.READY, " ", Nodes.TextRole.BODY)), event -> { });
        banner.setVisible(false);
        var footer = new KitView(context, new ViewSpec(new Nodes.Stack("footer", ControlState.READY, Nodes.LayoutRole.SETTINGS_FOOTER, List.of(
            new Nodes.Button("help", ControlState.READY, "", "question", Nodes.ButtonKind.GHOST, "help", "Help", Nodes.ButtonPresentation.HELP),
            new Nodes.Stack("message-line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(new Nodes.Text("message", ControlState.READY, "", Nodes.TextRole.BODY))),
            new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
            new Nodes.Button("apply", new ControlState(true, false, false, ""), "Apply", "", Nodes.ButtonKind.SECONDARY, "apply", ""),
            new Nodes.Button("ok", ControlState.READY, "OK", "", Nodes.ButtonKind.PRIMARY, "ok", "")))), event -> { });
        JScrollPane body = SettingsShell.pageScroller(context);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder()); body.setOpaque(false); body.getViewport().setOpaque(false);
        var shell = new SettingsShell(context, frame, search, navigation, header, banner, body, footer);
        return new Shell(shell, List.of(search, navigation, header, banner, footer));
    }

    static void layout(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) layout(child);
    }

    static BufferedImage render(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    @Test void theClassicFramePlacesItsPartsAsTheMocksDo() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var built = shell(context, CLASSIC)) {
                var shell = built.shell();
                shell.setSize(1040, 691);
                for (int i = 0; i < 3; i++) layout(shell);
                var views = built.views();
                assertThat(views.get(0).getBounds()).as("search").isEqualTo(new Rectangle(8, 8, 214, 22));
                assertThat(views.get(1).getY()).as("navigation below the strip rule and the gap").isEqualTo(43);
                assertThat(views.get(1).getWidth()).isEqualTo(230);
                assertThat(views.get(2).getBounds()).as("header").isEqualTo(new Rectangle(231 + 14, 0, 1040 - 231 - 28, 34));
                assertThat(views.get(4).getBounds()).as("footer").isEqualTo(new Rectangle(14, 691 - 45 + 1, 1040 - 28, 44));
                var ok = KitViewTest.find(views.get(4), "ok", JComponent.class);
                var cancel = KitViewTest.find(views.get(4), "cancel", JComponent.class);
                assertThat(ok.getWidth()).isEqualTo(98); assertThat(ok.getHeight()).isEqualTo(24);
                assertThat(ok.getX() + ok.getWidth()).as("OK ends at the footer's side").isEqualTo(1040 - 28);
                assertThat(ok.getY()).as("centred below the rule").isEqualTo(10);
                assertThat(cancel.getWidth()).isEqualTo(98);
                assertThat(KitViewTest.find(views.get(4), "help", JComponent.class).getWidth()).isEqualTo(22);
                var back = KitViewTest.find(views.get(2), "back", JComponent.class);
                var forward = KitViewTest.find(views.get(2), "forward", JComponent.class);
                assertThat(back.getParent().getWidth()).as("the mocks' 51 px history pair").isEqualTo(51);
                assertThat(forward.getX() - back.getX()).as("the halves share one border").isEqualTo(back.getWidth() - 1);
                var image = render(shell);
                var pair = javax.swing.SwingUtilities.convertPoint(back.getParent(), 0, back.getHeight() / 2, shell);
                int outline = image.getRGB(pair.x, pair.y) & 0xFFFFFF;
                assertThat(image.getRGB(pair.x + 25, pair.y) & 0xFFFFFF).as("one shared divider in the pair's outline").isEqualTo(outline);
                assertThat(image.getRGB(pair.x + 26, pair.y) & 0xFFFFFF).as("the disabled half's face beside it").isNotEqualTo(outline);
                assertThat(image.getRGB(pair.x + 50, pair.y) & 0xFFFFFF).as("the disabled half keeps the outline").isEqualTo(outline);
                var theme = context.theme();
                assertThat(image.getRGB(115, 4) & 0xFFFFFF).as("strip").isEqualTo(SemanticColor.SURFACE_SIDEBAR.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(115, 38) & 0xFFFFFF).as("strip rule").isEqualTo(DerivedColor.BORDER_ROW.color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(200, 400) & 0xFFFFFF).as("navigation slot").isEqualTo(context.kit().colors().navigationSurface().color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(230, 400) & 0xFFFFFF).as("sidebar rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, context.kit().colors().navigationSurface(), SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 34) & 0xFFFFFF).as("header rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, context.kit().colors().dialogSurface(), SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 300) & 0xFFFFFF).as("body").isEqualTo(context.kit().colors().dialogSurface().color(theme).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(300, 691 - 45) & 0xFFFFFF).as("footer rule").isEqualTo(ThemePainting.painted(context,
                    new dev.jasper.ui.theme.PaintPair(SemanticColor.BORDER_WINDOW, DerivedColor.SURFACE_FOOTER, SurfaceContrast.Guard.STRUCTURAL)).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(300, 691 - 5) & 0xFFFFFF).as("footer band").isEqualTo(DerivedColor.SURFACE_FOOTER.color(theme).getRGB() & 0xFFFFFF);
            } finally { release(context); }
        });
    }

    @Test void theFrameFollowsATallerSearchFieldAndHeader() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            var parts = new JComponent[6];
            for (int i = 0; i < parts.length; i++) { parts[i] = new javax.swing.JPanel(); parts[i].setOpaque(false); }
            parts[0].setPreferredSize(new java.awt.Dimension(214, 30));
            parts[2].setPreferredSize(new java.awt.Dimension(400, 40));
            parts[3].setVisible(false);
            parts[5].setPreferredSize(new java.awt.Dimension(400, 30));
            var shell = new SettingsShell(context, CLASSIC, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
            try {
                shell.setSize(760, 691);
                shell.doLayout();
                assertThat(parts[1].getY()).as("8 + 30 + 8, the rule, the gap").isEqualTo(8 + 30 + 8 + 1 + 4);
                assertThat(shell.titleRowHeight()).isEqualTo(40);
                assertThat(parts[2].getHeight()).isEqualTo(40);
                assertThat(parts[4].getY()).as("the body below the taller header's rule").isEqualTo(41);
                assertThat(parts[5].getY() + parts[5].getHeight()).as("the footer inside the shell").isEqualTo(691);
                var image = render(shell);
                assertThat(image.getRGB(115, 46) & 0xFFFFFF).as("the strip rule moved").isEqualTo(DerivedColor.BORDER_ROW.color(context.theme()).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(600, 40) & 0xFFFFFF).as("the header rule moved").isNotEqualTo(image.getRGB(600, 300) & 0xFFFFFF);
            } finally { shell.close(); release(context); }
        });
    }

    @Test void theRulesMeetTheirGuardOverEveryPalette() {
        Edt.run(() -> {
            var palettes = new java.util.ArrayList<>(KitFixtures.themes());
            palettes.addAll(KitFixtures.degenerate());
            for (var theme : palettes) {
                var context = KitFixtures.context(theme, KitFixtures.enterprise());
                try {
                    for (var pair : SettingsShell.framePairs(context))
                        assertThat(pair.guard().holds(ThemePainting.painted(context, pair), pair.against().color(theme))).as(pair.toString()).isTrue();
                } finally { release(context); }
            }
        });
    }

    @Test void theIntegratedFrameIsTodaysShell() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var before = shell(context, SettingsShell.Frame.INTEGRATED)) {
                var shell = before.shell();
                shell.setSize(1000, 700);
                for (int i = 0; i < 3; i++) layout(shell);
                int top = shell.titleRowHeight();
                assertThat(before.views().get(0).getY()).as("the search sits under the title row").isEqualTo(top);
                assertThat(before.views().get(2).getX()).isEqualTo(Math.min(context.metrics().value(DesignMetrics.Role.SETTINGS_SIDEBAR_WIDTH), 1000 / 3)
                    + context.metrics().value(DesignMetrics.Role.SETTINGS_HEADER_INSET));
                assertThat(context.metrics().value(DesignMetrics.Role.SETTINGS_HEADER_INSET)).as("today's header inset")
                    .isEqualTo(context.metrics().value(DesignMetrics.Role.SETTINGS_PAGE_INSET));
            } finally { release(context); }
        });
    }
}
