package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.app.config.*;
import dev.jasper.app.config.TerminalColors;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceThemeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void completeThemeCanCreateAndRefreshWindowWithOptionalToolWindowBackground(boolean authored) throws Exception {
        edt(() -> {
            var base = dev.jasper.app.appearance.ThemeFixtures.DARCULA.source().document();
            var ui = new java.util.LinkedHashMap<>(base.ui());
            ui.remove("ToolWindow.background");
            ui.put("Panel.background", "#17232f");
            if (authored) ui.put("ToolWindow.background", "#314159");
            var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), 0,
                base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(), ui, base.icons());
            var generated = ThemeGenerator.generate(document);
            var chrome = new Theme("optional-tool-window-" + authored, generated.name(), generated.dark(),
                dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(generated.terminal()), new Theme.UserFile(java.nio.file.Path.of("optional-tool-window.toml"), document));
            var themes = new ThemeController(chrome);
            var background = new java.awt.Color(authored ? 0x314159 : 0x17232f);
            assertThat(UIManager.getColor("ToolWindow.background")).isEqualTo(authored ? background : null);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            // The selected folder joins this workspace; the rest of the strip is recessed.
            assertStripSurfaces(owner);
            themes.configure(chrome, new WorkspaceThemeChoice("Jasper Light"), Theme.JASPER_LIGHT, UiFontConfig.defaults());
            assertStripSurfaces(owner);
            themes.configure(chrome, WorkspaceThemeChoice.MATCH, null, UiFontConfig.defaults());
            owner.update();
            assertStripSurfaces(owner);
            owner.windowTabs().setSize(600, 32);
            owner.windowTabs().doLayout();
            var image = new java.awt.image.BufferedImage(600, 32, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try { owner.windowTabs().paint(graphics); }
            finally { graphics.dispose(); }
        });
    }

    private static void assertStripSurfaces(WindowContent owner) {
        var strip = owner.windowTabs(); WindowTabsTest.layout(strip, 600, 30);
        var image = new java.awt.image.BufferedImage(600, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { strip.paint(g); } finally { g.dispose(); }
        var viewport = (java.awt.Container) WindowTabsTest.named(strip, "tabViewport");
        java.awt.Container entry = null;
        for (var child : viewport.getComponents()) {
            var candidate = (java.awt.Container) child;
            for (var control : candidate.getComponents())
                if (control instanceof AbstractButton button && button.isSelected()) entry = candidate;
        }
        assertThat(entry).as("one real selected tab").isNotNull();
        assertThat(new java.awt.Color(image.getRGB(entry.getX() + entry.getWidth() / 2, 29)))
            .as("active folder follows the actual workspace palette").isEqualTo(owner.theme().palette().background());
        assertThat(new java.awt.Color(image.getRGB(500, 15)))
            .as("negative space follows the workspace-derived recess").isEqualTo(strip.getBackground())
            .isNotEqualTo(owner.theme().palette().background());
    }

    @Test void independentWorkspaceMarksNewTabsAndPaintsStripWithoutChangingChrome() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            owner.newTab(HOME);
            var root = owner.currentPane();
            assertThat(root.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
            assertThat(((JLabel) root.getComponent(0)).getForeground()).isEqualTo(themes.current().palette().foreground());
            assertStripSurfaces(owner);
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            assertThat(new JPopupMenu().getBackground()).isEqualTo(UIManager.getColor("PopupMenu.background"));
            themes.configureTerminal(TerminalColors.MATCH);
            assertThat(root.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(root.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(root.getClientProperty("Jasper.workspace.dark")).isNull();
        });
    }
    @Test void titleSidebarStatusAndDialogContentKeepCapturedChromeDefaults() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var titleBackground = UIManager.getColor("Jasper.titleBackground");
            var statusBackground = UIManager.getColor("StatusBar.background");
            var panelBackground = UIManager.getColor("Panel.background");
            var labelForeground = UIManager.getColor("Label.foreground");
            var fieldBackground = UIManager.getColor("TextField.background");
            var fieldForeground = UIManager.getColor("TextField.foreground");
            var dialogBackground = UIManager.getColor("OptionPane.background");
            var root = new JRootPane();
            try (var title = WindowContent.installTitleBar(root, owner, true, text -> {})) {
                owner.installRootBindings(root);
                themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
                assertThat(themes.current().workspace().dark()).isTrue();
                assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).isFalse();
                title.setSize(400, 30);
                var image = new java.awt.image.BufferedImage(400, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); title.paint(graphics); graphics.dispose();
                assertThat(new java.awt.Color(image.getRGB(0, 2))).as("rendered title bar").isEqualTo(titleBackground);
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
                assertThat(owner.leftStripe().getBackground()).as("left stripe").isEqualTo(titleBackground);
                assertThat(owner.rightStripe().getBackground()).as("right stripe").isEqualTo(titleBackground);
                assertThat(owner.status().getBackground()).as("status bar").isEqualTo(statusBackground);
                // Build effective dialog contents without constructing a top-level window in headless mode.
                var dialogRoot = new JRootPane();
                var dialogPanel = new JPanel(); var label = new JLabel("Dialog"); var field = new JTextField("Value");
                var optionPane = new JOptionPane("Message");
                dialogPanel.add(label); dialogPanel.add(field); dialogPanel.add(optionPane);
                dialogRoot.setContentPane(dialogPanel);
                assertThat(dialogPanel.getBackground()).isEqualTo(panelBackground);
                assertThat(label.getForeground()).isEqualTo(labelForeground);
                assertThat(field.getBackground()).isEqualTo(fieldBackground);
                assertThat(field.getForeground()).isEqualTo(fieldForeground);
                assertThat(optionPane.getBackground()).isEqualTo(dialogBackground);
            }
        });
    }

    @Test void lookupFollowsReparentingButStopsAtChromePopupAndWindowRoots() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var workspace = new JPanel(); WorkspaceTheme.apply(workspace, themes.current());
            var child = new JLabel(); workspace.add(child);
            assertThat(WorkspaceTheme.color(child, Token.TEXT, "Label.foreground"))
                .isEqualTo(themes.current().workspace().tokens().get(Token.TEXT));
            var chrome = new JPanel(); chrome.putClientProperty("Jasper.zone", "chrome"); workspace.add(chrome); chrome.add(child);
            assertThat(WorkspaceTheme.color(child, Token.TEXT, "Label.foreground")).isEqualTo(UIManager.getColor("Label.foreground"));
            var popup = new JPopupMenu(); popup.setInvoker(workspace); popup.add(child);
            assertThat(WorkspaceTheme.color(child, Token.TEXT, "Label.foreground")).isEqualTo(UIManager.getColor("Label.foreground"));
            var dialogRoot = new JRootPane(); workspace.add(dialogRoot); dialogRoot.getContentPane().add(child);
            assertThat(WorkspaceTheme.color(child, Token.TEXT, "Label.foreground")).isEqualTo(UIManager.getColor("Label.foreground"));
            workspace.add(child); WorkspaceTheme.clear(workspace);
            assertThat(WorkspaceTheme.color(child, Token.TEXT, "Label.foreground")).isEqualTo(UIManager.getColor("Label.foreground"));
        });
    }

    @Test void hostedContentIsMarkedOnMountAndClearedOnRemovalWithoutChangingItsOwnStyle() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            var panel = new JPanel(); panel.putClientProperty("FlatLaf.style", "arc: 7");
            var entry = model.content().open("test", "file", "File", owner.id(), panel);
            assertThat(panel.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
            themes.configureTerminal(TerminalColors.MATCH);
            assertThat(panel.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(panel.getClientProperty("Jasper.workspaceTheme")).isNull();
            model.content().remove(entry);
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(panel.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(panel.getClientProperty("FlatLaf.style")).isEqualTo("arc: 7");
        });
    }

    @Test void dividerPaintUsesWorkspaceColorWhileChromeDefaultsStayLight() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            var chromeDivider = UIManager.getColor("Jasper.splitDivider");
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var split = new JSplitPane(); WorkspaceTheme.apply(split, themes.current());
            split.setSize(400, 300); split.setDividerLocation(180); split.doLayout();
            var divider = ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
            var image = new java.awt.image.BufferedImage(divider.getWidth(), divider.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); divider.paint(graphics); graphics.dispose();
            assertThat(new java.awt.Color(image.getRGB(divider.getWidth() / 2, 1)))
                .isEqualTo(themes.current().workspace().tokens().get(Token.DIVIDER));
            assertThat(UIManager.getColor("Jasper.splitDivider")).isEqualTo(chromeDivider);
        });
    }

    @Test void movingHostedContentReplacesItsWorkspaceZoneAndRetainsTheDocument() throws Exception {
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            try (var detached = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {}, () -> {},
                    new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT))) {
                detached.connectContributions(model); model.content().setFactory(separate -> detached.id());
                var text = new JTextArea("retained document"); var document = text.getDocument();
                var entry = model.content().open("test", "move", "Move", owner.id(), text);
                assertThat(text.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
                model.content().move(entry, true);
                assertThat(detached.selectedContent()).isSameAs(entry);
                assertThat(text.getClientProperty("Jasper.zone")).isEqualTo("workspace");
                assertThat(text.getClientProperty("Jasper.workspaceTheme")).isNull();
                assertThat(text.getDocument()).isSameAs(document);
                model.content().move(entry, false);
                assertThat(owner.selectedContent()).isSameAs(entry);
                assertThat(text.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
            }
        });
    }

    @Test void failedLaunchLabelRetainsWorkspaceTerminalForegroundAcrossThemeChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            owner[0] = content(new dev.jasper.app.launch.ShellLauncher(pending::add,
                path -> { throw new IllegalStateException("expected launch failure"); }, "fixture"), themes);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
        });
        pending.remove().run();
        edt(() -> {
            var pane = owner[0].currentPane(); var message = (JLabel) pane.getComponent(0);
            assertThat(message.getText()).contains("Could not start terminal");
            assertThat(message.getForeground()).isEqualTo(owner[0].theme().palette().foreground());
            owner[0].selectTerminalColors(TerminalColors.MATCH);
            assertThat(message.getForeground()).isEqualTo(owner[0].theme().palette().foreground());
        });
    }

}
