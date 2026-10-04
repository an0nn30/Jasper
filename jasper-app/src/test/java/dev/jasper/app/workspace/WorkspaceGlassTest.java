package dev.jasper.app.workspace;

import dev.jasper.app.appearance.GlassLevels;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.GlassSettings;
import dev.jasper.app.config.WorkspaceBlur;
import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceGlassTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> new ThemeController()); }

    private static BufferedImage render(JComponent root) {
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { root.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    private static int alpha(BufferedImage image, JComponent root, Component component, int x, int y) {
        Point point = SwingUtilities.convertPoint(component, x, y, root);
        return new Color(image.getRGB(point.x, point.y), true).getAlpha();
    }

    @Test void theWorkspaceHostFillsOnlyItsSurfaceWithGlassWhenGlassIsOn() throws Exception {
        edt(() -> {
            var deck = new JPanel();
            deck.setOpaque(false);
            var area = new ToolWindowArea(deck, new DockState());
            area.setStripesVisible(true);
            area.setTerminalBackground(new Color(0x212734));
            var side = new JPanel();
            DockTestSupport.show(area, Slot.LEFT_TOP, side, 200);
            area.setSize(1000, 600);
            LayoutTestSupport.layoutTree(area);
            LayoutTestSupport.layoutTree(area);
            var host = (JComponent) deck.getParent();
            var insets = host.getInsets();
            assertThat(alpha(render(area), area, deck, 300, 200)).isEqualTo(255);

            area.setGlass(0.5f);
            var glass = render(area);
            assertThat(alpha(glass, area, deck, 300, 200)).as("inside the rounded surface").isEqualTo(128);
            assertThat(alpha(glass, area, host, host.getWidth() / 2, insets.top / 2)).as("the top gutter is chrome").isEqualTo(255);
            assertThat(alpha(glass, area, host, insets.left, insets.top)).as("the rounded corner is chrome").isEqualTo(255);
            assertThat(alpha(glass, area, side, side.getWidth() / 2, side.getHeight() / 2)).as("a tool window stays solid").isEqualTo(255);

            area.setGlass(1f);
            assertThat(alpha(render(area), area, deck, 300, 200)).isEqualTo(255);
        });
    }

    /** A deck that paints like a translucent terminal pane: one Src fill of the background at that opacity. */
    private static JPanel translucentDeck(Color background, float opacity) {
        var deck = new JPanel() {
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                var g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setComposite(java.awt.AlphaComposite.Src);
                    g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(),
                        Math.round(255 * opacity)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                } finally { g.dispose(); }
            }
        };
        deck.setOpaque(false);
        return deck;
    }

    @Test void everySurfacePixelIsGlassWithTheStripesVisible() throws Exception {
        edt(() -> {
            var background = new Color(0x212734);
            var deck = translucentDeck(background, 0.5f);
            var area = new ToolWindowArea(deck, new DockState());
            area.setStripesVisible(true);
            area.setTerminalBackground(background);
            area.setSize(1000, 600);
            LayoutTestSupport.layoutTree(area);
            LayoutTestSupport.layoutTree(area);
            var host = (JComponent) deck.getParent();
            var insets = host.getInsets();
            int arc = com.formdev.flatlaf.util.UIScale.scale(16);
            var surface = new java.awt.geom.RoundRectangle2D.Double(insets.left, insets.top,
                host.getWidth() - insets.left - insets.right, host.getHeight() - insets.top - insets.bottom, arc, arc);

            area.setGlass(0.5f);
            var image = render(area);
            int inside = 0;
            var wrongSurface = new ArrayList<String>();
            var wrongGutter = new ArrayList<String>();
            var wrongEdge = new ArrayList<String>();
            for (int y = 0; y < host.getHeight(); y++) for (int x = 0; x < host.getWidth(); x++) {
                int alpha = alpha(image, area, host, x, y);
                String pixel = "(" + x + ", " + y + ") = " + alpha;
                if (surface.contains(x, y, 1, 1)) {
                    inside++;
                    if (alpha != 128) wrongSurface.add(pixel);
                } else if (!surface.intersects(x, y, 1, 1)) {
                    if (alpha != 255) wrongGutter.add(pixel);
                } else if (alpha < 128) wrongEdge.add(pixel);
            }
            assertThat(inside).isPositive();
            assertThat(wrongSurface).as("surface pixels with another alpha than one glass fill").isEmpty();
            assertThat(wrongGutter).as("gutter pixels that are not solid").isEmpty();
            assertThat(wrongEdge).as("rounded-edge pixels below the glass alpha").isEmpty();
        });
    }

    @Test void splitsKeepASolidDividerBetweenTranslucentPanes() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        TerminalTab[] created = new TerminalTab[1];
        edt(() -> created[0] = new TerminalTab(DesktopTestSupport.HOME, DesktopTestSupport.launcher(pending)));
        TerminalTab tab = created[0];
        edt(tab::start);
        try {
            pending.remove().run();
            edt(() -> {
                tab.setBackgroundOpacity(0.5f);
                tab.split(SplitTree.Axis.RIGHT);
            });
            pending.remove().run();
            edt(() -> {
                assertThat(tab.isOpaque()).isFalse();
                assertThat(tab.panes()).hasSize(2).allMatch(pane -> !pane.isOpaque());
                var split = (TerminalTab.PaneSplit) tab.getComponent(0);
                assertThat(split.isOpaque()).isFalse();
                tab.setSize(600, 300);
                LayoutTestSupport.layoutTree(tab);
                LayoutTestSupport.layoutTree(tab);
                var image = render(tab);
                var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
                assertThat(alpha(image, tab, divider, divider.getWidth() / 2, divider.getHeight() / 2)).as("divider").isEqualTo(255);
                for (TerminalPane pane : tab.panes())
                    assertThat(alpha(image, tab, pane, 2, 2)).as("pane padding").isEqualTo(128);

                tab.setBackgroundOpacity(1f);
                assertThat(tab.isOpaque()).isTrue();
                assertThat(split.isOpaque()).isTrue();
                assertThat(tab.panes()).allMatch(JComponent::isOpaque);
            });
        } finally { edt(tab::close); }
    }

    @Test void aTranslucentWindowLeavesItsChromeSolid() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.setSize(900, 600);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            TerminalPane pane = owner.currentPane();
            assertThat(alpha(render(owner), owner, pane, 2, 2)).isEqualTo(255);

            owner.applyBackgroundOpacity(0.5);
            assertThat(owner.backgroundOpacity()).isEqualTo(0.5f);
            var glass = render(owner);
            assertThat(alpha(glass, owner, pane, 2, 2)).as("pane padding").isEqualTo(128);
            var status = owner.status();
            assertThat(status.getHeight()).as("the status bar is laid out").isPositive();
            assertThat(alpha(glass, owner, status, status.getWidth() / 2, status.getHeight() / 2)).as("status bar").isEqualTo(255);
            var header = owner.chrome().header();
            assertThat(alpha(glass, owner, header, header.getWidth() / 2, header.getHeight() / 2)).as("header").isEqualTo(255);

            owner.applyBackgroundOpacity(1);
            assertThat(alpha(render(owner), owner, pane, 2, 2)).isEqualTo(255);
        });
    }

    @Test void newTabsFollowTheWindowsOpacity() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.applyBackgroundOpacity(0.7);
            TerminalTab tab = owner.openTab(DesktopTestSupport.HOME);
            assertThat(tab.isOpaque()).isFalse();
            assertThat(tab.panes()).allMatch(pane -> !pane.isOpaque());
        });
    }

    @Test void theGlassListenerRunsOncePerRealOpacityChange() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            int[] runs = {0};
            owner.setGlassListener(() -> runs[0]++);
            owner.applyBackgroundOpacity(0.5);
            assertThat(runs[0]).isEqualTo(1);
            owner.applyBackgroundOpacity(0.5);
            assertThat(runs[0]).as("an unchanged opacity is quiet").isEqualTo(1);
            owner.applyBackgroundOpacity(1);
            assertThat(runs[0]).isEqualTo(2);
        });
    }

    @Test void hostedContentEnteringOrLeavingATranslucentWindowRunsTheGlassListener() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            int[] runs = {0};
            owner.setGlassListener(() -> runs[0]++);
            model.content().open("dev.editor", "opaque", "Opaque", owner.id(), new JPanel()).requestClose();
            assertThat(runs[0]).as("an opaque window has no glass to announce").isZero();
            owner.applyBackgroundOpacity(0.6);
            runs[0] = 0;
            var entry = model.content().open("dev.editor", "glass", "Glass", owner.id(), new JPanel());
            assertThat(runs[0]).as("mounted").isEqualTo(1);
            entry.requestClose();
            assertThat(runs[0]).as("unmounted").isEqualTo(2);
        });
    }

    @Test void onlyTheWorkspaceCentreReportsTheWindowsOpacity() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            var hosted = new JPanel();
            var inside = new JPanel();
            hosted.add(inside);
            model.content().open("dev.editor", "glass", "Glass", owner.id(), hosted);
            var side = new JPanel();
            var sideChild = new JPanel();
            side.add(sideChild);
            DockTestSupport.show(owner, Slot.LEFT_TOP, side, 200);
            assertThat(owner.workspaceOpacity(inside)).as("opaque window").isEqualTo(1.0);
            var terminalTab = owner.tabStrip().getComponentAt(0);

            owner.applyBackgroundOpacity(0.5);
            assertThat(owner.workspaceOpacity(hosted)).as("hosted centre content").isEqualTo(0.5);
            assertThat(owner.workspaceOpacity(inside)).as("inside hosted centre content").isEqualTo(0.5);
            assertThat(owner.workspaceOpacity(terminalTab)).as("a terminal tab").isEqualTo(0.5);
            assertThat(owner.workspaceOpacity(side)).as("a side panel").isEqualTo(1.0);
            assertThat(owner.workspaceOpacity(sideChild)).as("inside a side panel").isEqualTo(1.0);
            assertThat(owner.workspaceOpacity(owner.status())).as("the status bar").isEqualTo(1.0);
            assertThat(owner.workspaceOpacity(owner.chrome().header())).as("the header").isEqualTo(1.0);
            assertThat(owner.workspaceOpacity(new JPanel())).as("a detached component").isEqualTo(1.0);
        });
    }

    @Test void theOpacityKeepsTheExactValueItWasGiven() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.applyBackgroundOpacity(0.8);
            assertThat(owner.backgroundOpacity()).isEqualTo(0.8);
            assertThat(owner.workspaceOpacity(owner.currentPane())).isEqualTo(0.8);
            assertThat(owner.currentTab().isOpaque()).as("terminal tabs still follow").isFalse();
        });
    }

    @Test void anErrorFromTheGlassListenerAfterAMountLeavesNoHalfMountedContent() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            owner.applyBackgroundOpacity(0.6);
            owner.setGlassListener(() -> { throw new AssertionError("a plugin handler failed"); });
            var component = new JPanel();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.content().open("dev.editor", "glass", "Glass", owner.id(), component))
                .as("errors other than contained ones propagate").isInstanceOf(AssertionError.class);
            assertThat(owner.tabStrip().indexOfComponent(component)).as("the registry rolled the mount back").isEqualTo(-1);
            assertThat(owner.selectedContent()).isNull();
            assertThat(model.content().entries()).isEmpty();
        });
    }

    @Test void requestsCombineSavedSettingsWithTheWorkspaceTheme() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
            var requests = new ArrayList<GlassLevels>();
            var dark = new ArrayList<Boolean>();
            owner.setGlassRequests((levels, isDark) -> { requests.add(levels); dark.add(isDark); });
            assertThat(requests.getLast()).isEqualTo(GlassLevels.OPAQUE);

            var clear = ThemeCatalog.builtIn().scan().find("Clear Dark").orElseThrow();
            themes.configureWorkspace(new WorkspaceThemeChoice("Clear Dark"), clear);
            assertThat(requests.getLast()).isEqualTo(new GlassLevels(0.95, true));
            assertThat(dark.getLast()).as("Clear Dark is a dark workspace").isTrue();

            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder()
                .glass(new GlassSettings(80f, WorkspaceBlur.OFF)).build(), true);
            assertThat(requests.getLast()).isEqualTo(new GlassLevels(0.8, false));
            assertThat(dark.getLast()).isTrue();

            var light = ThemeCatalog.builtIn().scan().find("Clear Light").orElseThrow();
            themes.configureWorkspace(new WorkspaceThemeChoice("Clear Light"), light);
            assertThat(requests.getLast()).isEqualTo(new GlassLevels(0.8, false));
            assertThat(dark.getLast()).as("Clear Light is a light workspace").isFalse();
        });
    }
}
