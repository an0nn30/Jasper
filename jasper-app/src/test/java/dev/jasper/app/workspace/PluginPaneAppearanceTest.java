package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.themeio.*;
import dev.jasper.app.config.*;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class PluginPaneAppearanceTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test void failedMountReleasesPartialStylerAndZone() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel() {
                boolean fail = true;
                @Override public java.awt.Component[] getComponents() {
                    if (fail) { fail = false; throw new IllegalStateException("fixture traversal failure"); }
                    return super.getComponents();
                }
            };
            int tabs = owner.tabStrip().getTabCount();
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                contributions.content().open("fixture", "failed", "Failed", owner.id(), panel))
                .isInstanceOf(IllegalStateException.class).hasMessage("fixture traversal failure");
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(panel.getClientProperty("Jasper.workspaceStylerRoot")).isNull();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(tabs);
            assertThat(contributions.content().entries()).isEmpty();
            var retry = contributions.content().open("fixture", "failed", "Recovered", owner.id(), panel);
            assertThat(contributions.content().entries()).containsExactly(retry);
        });
    }

    @Test void failureWhilePublishingZoneAlsoRollsBackMount() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel();
            var fail = new boolean[] {true};
            panel.addPropertyChangeListener("Jasper.zone", event -> {
                if (fail[0]) { fail[0] = false; throw new IllegalStateException("fixture zone failure"); }
            });
            int tabs = owner.tabStrip().getTabCount();
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                contributions.content().open("fixture", "failed-zone", "Failed", owner.id(), panel))
                .isInstanceOf(IllegalStateException.class).hasMessage("fixture zone failure");
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(panel.getClientProperty("Jasper.workspaceStylerRoot")).isNull();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(tabs);
            assertThat(contributions.content().entries()).isEmpty();
            var retry = contributions.content().open("fixture", "failed-zone", "Recovered", owner.id(), panel);
            assertThat(contributions.content().entries()).containsExactly(retry);
        });
    }

    @Test void newHostedContentUsesCurrentWorkspaceImmediately() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            var entry = contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
            assertThat(list.getSelectionBackground()).as("style %s generated %s", list.getClientProperty("FlatLaf.style"),
                list.getClientProperty("Jasper.workspaceStyle"))
                .isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION));
            contributions.content().remove(entry);
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
        });
    }

    @Test void sameDarknessUpdateRecolorsRetainedControls() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            assertThat(list.getSelectionBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION));
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA, UiFontConfig.defaults());
            assertThat(list.getBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SURFACE_SUNKEN));
            assertThat(list.getSelectionBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION_INACTIVE));
            assertThat(((java.util.Map<?, ?>) list.getClientProperty("Jasper.workspaceStyle")).get("selectionBackground"))
                .isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION));
        });
    }

    @Test void themePublicationCanRemovePaneWithoutRestylingDisposedOwner() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var removed = new JPanel(); var removedList = new JList<>(new String[] {"remove"}); removed.add(removedList);
            var entry = contributions.content().open("fixture", "remove", "Remove", owner.id(), removed);
            var retained = new JPanel(); var retainedList = new JList<>(new String[] {"keep"}); retained.add(retainedList);
            contributions.content().open("fixture", "keep", "Keep", owner.id(), retained);
            var armed = new boolean[] {true};
            removed.addPropertyChangeListener("Jasper.workspaceTheme", event -> {
                if (armed[0]) { armed[0] = false; contributions.content().remove(entry); }
            });
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA, UiFontConfig.defaults());
            assertThat(contributions.content().entries()).doesNotContain(entry);
            assertThat(removed.getClientProperty("Jasper.zone")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspace.foreground")).isNull();
            assertThat(removedList.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(retainedList.getBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SURFACE_SUNKEN));
        });
    }

    @Test void themePublicationCanMovePaneWithoutOverwritingDestinationZone() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var detached = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {}, () -> {},
                new ThemeController(Theme.JASPER_LIGHT));
            var contributions = new Contributions(); owner.connectContributions(contributions);
            detached.connectContributions(contributions);
            contributions.content().setFactory(separate -> detached.id());
            var moved = new JPanel(); var movedList = new JList<>(new String[] {"move"}); moved.add(movedList);
            var entry = contributions.content().open("fixture", "move", "Move", owner.id(), moved);
            var retained = new JPanel(); var retainedList = new JList<>(new String[] {"keep"}); retained.add(retainedList);
            contributions.content().open("fixture", "keep", "Keep", owner.id(), retained);
            var armed = new boolean[] {true};
            moved.addPropertyChangeListener("Jasper.workspaceTheme", event -> {
                if (armed[0]) { armed[0] = false; contributions.content().move(entry, true); }
            });
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA, UiFontConfig.defaults());
            assertThat(entry.window()).isEqualTo(detached.id());
            assertThat(moved.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(moved.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(moved.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(moved.getClientProperty("Jasper.workspace.foreground")).isNull();
            assertThat(movedList.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(retainedList.getBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SURFACE_SUNKEN));
            detached.close();
        });
    }

    @Test void movingContentBetweenWindowsRestoresThenRestyles() throws Exception {
        edt(() -> {
            var firstTheme = new ThemeController(Theme.JASPER_LIGHT);
            firstTheme.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var first = content(launcher(new ArrayDeque<>()), firstTheme);
            var second = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {}, () -> {},
                new ThemeController(Theme.JASPER_LIGHT));
            var contributions = new Contributions(); first.connectContributions(contributions); second.connectContributions(contributions);
            contributions.content().setFactory(separate -> second.id());
            var text = new JTextArea("before"); var document = text.getDocument();
            var undo = new javax.swing.undo.UndoManager(); document.addUndoableEditListener(undo);
            text.append(" after"); text.select(1, 4);
            var entry = contributions.content().open("fixture", "pane", "Pane", first.id(), text);
            assertThat(text.getClientProperty("Jasper.workspaceStyle")).isNotNull();
            contributions.content().move(entry, true);
            assertThat(text.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(text.getDocument()).isSameAs(document);
            assertThat(undo.canUndo()).isTrue();
            assertThat(text.getSelectedText()).isEqualTo("efo");
            contributions.content().move(entry, false);
            assertThat(text.getClientProperty("Jasper.workspaceStyle")).isNotNull();
            assertThat(text.getDocument()).isSameAs(document);
            assertThat(undo.canUndo()).isTrue();
            second.close();
        });
    }

    @Test void completeChromeWithoutOptionalToolWindowKeyStillStylesHostedPane() throws Exception {
        edt(() -> {
            var base = dev.jasper.app.appearance.ThemeFixtures.DARCULA.source().document();
            var ui = new java.util.LinkedHashMap<>(base.ui());
            ui.remove("ToolWindow.background");
            ui.put("Panel.background", "#17232f");
            var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), 0,
                base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(), ui, base.icons());
            var chrome = new Theme("optional-pane", "Optional Pane", true, dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(ThemeGenerator.generate(document).terminal()),
                new Theme.UserFile(java.nio.file.Path.of("optional-pane.toml"), document));
            var themes = new ThemeController(chrome);
            themes.configure(chrome, new WorkspaceThemeChoice("Jasper Light"), Theme.JASPER_LIGHT, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
            assertThat(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR)).isNotNull();
            assertThat(list.getBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SURFACE_SUNKEN));
            assertThat(list.getSelectionBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION_INACTIVE));
        });
    }

    @Test void pluginStopAndWindowCloseReleaseAllStyles() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            var entry = contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
            contributions.content().remove(entry);
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            var next = contributions.content().open("fixture", "pane2", "Pane", owner.id(), panel);
            owner.close();
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            themes.configure(Theme.JASPER_LIGHT, WorkspaceThemeChoice.MATCH, null, UiFontConfig.defaults());
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
        });
    }

    @Test void chromeRebuildThenMatchUsesCurrentChrome() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            contributions.content().open("fixture", "pane", "Pane", owner.id(), panel);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            themes.configure(Theme.DARK, new WorkspaceThemeChoice("Jasper Light"), Theme.JASPER_LIGHT, UiFontConfig.defaults());
            themes.configure(Theme.DARK, WorkspaceThemeChoice.MATCH, null, UiFontConfig.defaults());
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(list.getSelectionBackground()).isEqualTo(UIManager.getColor("List.selectionBackground"));
        });
    }

    @Test void sidebarAndAuxiliaryContentRemainChrome() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var railBackground = owner.leftStripe().getBackground();
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            for (var stripe : java.util.List.of(owner.leftStripe(), owner.rightStripe())) {
                assertThat(stripe.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(stripe.getBackground()).isEqualTo(railBackground);
            }
            var nativeRoot = new JRootPane(); var auxiliary = new JList<>(new String[] {"entry"});
            nativeRoot.getContentPane().add(auxiliary);
            assertThat(auxiliary.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(auxiliary.getSelectionBackground()).isEqualTo(new JList<>(new String[] {"entry"}).getSelectionBackground());
        });
    }

    @Test void exemptEditorSubtreeKeepsOwnColorsButNearbyControlsAreStyled() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.JASPER_LIGHT);
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var root = new JPanel(); var editor = new JPanel(); editor.putClientProperty("Jasper.workspace.exempt", true);
            var area = new JTextArea(); editor.add(area);
            var nearby = new JList<>(new String[] {"entry"}); root.add(editor); root.add(nearby);
            contributions.content().open("fixture", "editor", "Editor", owner.id(), root);
            assertThat(area.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(nearby.getSelectionBackground()).isEqualTo(themes.current().workspace().tokens().get(Token.SELECTION));
        });
    }
}
