package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.app.config.*;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceThemeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test void lookupFollowsReparentingButStopsAtChromePopupAndWindowRoots() throws Exception {
        edt(() -> {
            ThemeTestSupport.controller(false);
            // A dark workspace inside the light chrome, as hosting code may receive any pair (Decision 5).
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(false), ThemeTestSupport.generated(true));
            var workspace = new JPanel(); WorkspaceTheme.apply(workspace, split);
            var child = new JLabel(); workspace.add(child);
            assertThat(dev.jasper.app.platform.ThemeZone.color(child, SemanticColor.TEXT_PRIMARY))
                .isEqualTo(split.workspace().semantic().color(SemanticColor.TEXT_PRIMARY));
            var chrome = new JPanel(); chrome.putClientProperty("Jasper.zone", "chrome"); workspace.add(chrome); chrome.add(child);
            assertThat(dev.jasper.app.platform.ThemeZone.color(child, SemanticColor.TEXT_PRIMARY)).isEqualTo(UIManager.getColor("Label.foreground"));
            var popup = new JPopupMenu(); popup.setInvoker(workspace); popup.add(child);
            assertThat(dev.jasper.app.platform.ThemeZone.color(child, SemanticColor.TEXT_PRIMARY)).isEqualTo(UIManager.getColor("Label.foreground"));
            var dialogRoot = new JRootPane(); workspace.add(dialogRoot); dialogRoot.getContentPane().add(child);
            assertThat(dev.jasper.app.platform.ThemeZone.color(child, SemanticColor.TEXT_PRIMARY)).isEqualTo(UIManager.getColor("Label.foreground"));
            workspace.add(child); WorkspaceTheme.clear(workspace);
            assertThat(dev.jasper.app.platform.ThemeZone.color(child, SemanticColor.TEXT_PRIMARY)).isEqualTo(UIManager.getColor("Label.foreground"));
        });
    }

    @Test void hostedContentIsMarkedOnMountAndClearedOnRemovalWithoutChangingItsOwnStyle() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            var panel = new JPanel(); panel.putClientProperty("FlatLaf.style", "arc: 7");
            var entry = model.tabs().open("test", "file", "File", owner.id(), panel);
            assertThat(panel.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(panel.getClientProperty("Jasper.workspaceTheme")).isNull();
            model.tabs().remove(entry);
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(panel.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(panel.getClientProperty("FlatLaf.style")).isEqualTo("arc: 7");
        });
    }

    @Test void dividerPaintUsesWorkspaceColorWhileChromeDefaultsStayLight() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var chromeDivider = dev.jasper.app.platform.ThemeZone.color(null, SemanticColor.BORDER_SUBTLE);
            var pair = ThemeTestSupport.mixed(ThemeTestSupport.generated(false), ThemeTestSupport.generated(true));
            var split = new JSplitPane(); WorkspaceTheme.apply(split, pair);
            split.setSize(400, 300); split.setDividerLocation(180); split.doLayout();
            var divider = ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
            var image = new java.awt.image.BufferedImage(divider.getWidth(), divider.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); divider.paint(graphics); graphics.dispose();
            assertThat(new java.awt.Color(image.getRGB(divider.getWidth() / 2, 1)))
                .isEqualTo(pair.workspace().semantic().color(SemanticColor.BORDER_WINDOW));
            assertThat(dev.jasper.app.platform.ThemeZone.color(null, SemanticColor.BORDER_SUBTLE)).isEqualTo(chromeDivider);
        });
    }

    @Test void movingHostedContentReplacesItsWorkspaceZoneAndRetainsTheDocument() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var model = new dev.jasper.app.contributions.Contributions(); owner.connectContributions(model);
            try (var detached = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {}, () -> {},
                    ThemeTestSupport.controller(false))) {
                detached.connectContributions(model); model.tabs().setFactory(separate -> detached.id());
                var text = new JTextArea("retained document"); var document = text.getDocument();
                var entry = model.tabs().open("test", "move", "Move", owner.id(), text);
                assertThat(text.getClientProperty("Jasper.zone")).isEqualTo("workspace");
                model.tabs().move(entry, true);
                assertThat(detached.selectedContent()).isSameAs(entry);
                assertThat(text.getClientProperty("Jasper.zone")).isEqualTo("workspace");
                assertThat(text.getClientProperty("Jasper.workspaceTheme")).isNull();
                assertThat(text.getDocument()).isSameAs(document);
                model.tabs().move(entry, false);
                assertThat(owner.selectedContent()).isSameAs(entry);
                assertThat(text.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            }
        });
    }

    @Test void failedLaunchLabelRetainsWorkspaceTerminalForegroundAcrossThemeChanges() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            owner[0] = content(new dev.jasper.app.launch.ShellLauncher(pending::add,
                path -> { throw new IllegalStateException("expected launch failure"); }, "fixture"), themes);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
        });
        pending.remove().run();
        edt(() -> {
            var pane = owner[0].currentPane(); var message = (JLabel) pane.getComponent(0);
            assertThat(message.getText()).contains("Could not start terminal");
            assertThat(message.getForeground()).isEqualTo(owner[0].theme().palette().foreground());
            owner[0].selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(message.getForeground()).isEqualTo(owner[0].theme().palette().foreground());
        });
    }

}
