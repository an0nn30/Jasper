package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceThemeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test void independentWorkspaceMarksNewTabsAndPaintsStripWithoutChangingChrome() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            owner.newTab(HOME);
            var root = owner.currentPane();
            assertThat(root.getClientProperty("Jasper.workspaceTheme")).isSameAs(themes.current().workspace());
            assertThat(((JLabel) root.getComponent(0)).getForeground()).isEqualTo(themes.current().palette().foreground());
            assertThat(owner.windowTabs().getBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.TAB_BAR));
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            assertThat(new JPopupMenu().getBackground()).isEqualTo(UIManager.getColor("PopupMenu.background"));
            themes.configureTerminal(TerminalColors.MATCH);
            assertThat(root.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(root.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(root.getClientProperty("Jasper.workspace.dark")).isNull();
        });
    }
    @Test void lookupFollowsReparentingButStopsAtChromePopupAndWindowRoots() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
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
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
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
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            var chromeDivider = UIManager.getColor("Jasper.splitDivider");
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
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
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            try (var detached = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {}, () -> {},
                    new ThemeController(Theme.INTELLIJ_LIGHT))) {
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
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            owner[0] = content(new dev.jasper.app.launch.ShellLauncher(pending::add,
                path -> { throw new IllegalStateException("expected launch failure"); }, "fixture"), themes);
            themes.configure(Theme.INTELLIJ_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
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
