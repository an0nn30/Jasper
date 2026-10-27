package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.ui.theme.*;
import dev.jasper.app.config.*;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class PluginPaneAppearanceTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }

    @Test void failureWhilePublishingZoneAlsoRollsBackMount() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel();
            var fail = new boolean[] {true};
            panel.addPropertyChangeListener("Jasper.zone", event -> {
                if (fail[0]) { fail[0] = false; throw new IllegalStateException("fixture zone failure"); }
            });
            int tabs = owner.tabCount();
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                contributions.tabs().open("fixture", "failed-zone", "Failed", owner.id(), panel))
                .isInstanceOf(IllegalStateException.class).hasMessage("fixture zone failure");
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(panel.getClientProperty("Jasper.workspaceStylerRoot")).isNull();
            assertThat(owner.tabCount()).isEqualTo(tabs);
            assertThat(contributions.tabs().hosted()).isEmpty();
            var retry = contributions.tabs().open("fixture", "failed-zone", "Recovered", owner.id(), panel);
            assertThat(contributions.tabs().hosted()).containsExactly(retry);
        });
    }

    @Test void newHostedContentUsesCurrentWorkspaceImmediately() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            var entry = contributions.tabs().open("fixture", "pane", "Pane", owner.id(), panel);
            assertThat(dev.jasper.app.platform.ThemeZone.theme(list)).isSameAs(themes.current().workspace());
            assertThat(list.getClientProperty("FlatLaf.style")).isNull();
            contributions.tabs().remove(entry);
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
        });
    }

    @Test void sameDarknessUpdatePublishesToRetainedRoutes() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            contributions.tabs().open("fixture", "pane", "Pane", owner.id(), panel);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            assertThat(dev.jasper.app.platform.ThemeZone.theme(list)).isSameAs(themes.current().workspace());
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            assertThat(dev.jasper.app.platform.ThemeZone.theme(list)).isSameAs(themes.current().workspace());
            assertThat(list.getClientProperty("FlatLaf.style")).isNull();
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
        });
    }

    @Test void themePublicationCanRemovePaneWithoutRestylingDisposedOwner() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var removed = new JPanel(); var removedList = new JList<>(new String[] {"remove"}); removed.add(removedList);
            var entry = contributions.tabs().open("fixture", "remove", "Remove", owner.id(), removed);
            var retained = new JPanel(); var retainedList = new JList<>(new String[] {"keep"}); retained.add(retainedList);
            contributions.tabs().open("fixture", "keep", "Keep", owner.id(), retained);
            var armed = new boolean[] {true};
            removed.addPropertyChangeListener("Jasper.workspaceTheme", event -> {
                if (armed[0]) { armed[0] = false; contributions.tabs().remove(entry); }
            });
            // A light workspace inside the dark chrome, as hosting code may receive any pair (Decision 5).
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(true), ThemeTestSupport.generated(false));
            owner.applyTheme(split, true);
            assertThat(contributions.tabs().hosted()).doesNotContain(entry);
            assertThat(removed.getClientProperty("Jasper.zone")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(removed.getClientProperty("Jasper.workspace.foreground")).isNull();
            assertThat(removedList.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(dev.jasper.app.platform.ThemeZone.theme(retainedList)).isSameAs(split.workspace());
        });
    }

    @Test void themePublicationCanMovePaneWithoutOverwritingDestinationZone() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var detached = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {},
                ThemeTestSupport.controller(true));
            var contributions = new Contributions(); owner.connectContributions(contributions);
            detached.connectContributions(contributions);
            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(owner::id,
                tab -> contributions.tabs().move(tab, detached.id())));
            var moved = new JPanel(); var movedList = new JList<>(new String[] {"move"}); moved.add(movedList);
            var entry = contributions.tabs().open("fixture", "move", "Move", owner.id(), moved);
            var retained = new JPanel(); var retainedList = new JList<>(new String[] {"keep"}); retained.add(retainedList);
            contributions.tabs().open("fixture", "keep", "Keep", owner.id(), retained);
            var armed = new boolean[] {true};
            moved.addPropertyChangeListener("Jasper.workspaceTheme", event -> {
                if (armed[0]) { armed[0] = false; contributions.tabs().moveToNewWindow(entry); }
            });
            // A light workspace inside the dark chrome, as hosting code may receive any pair (Decision 5).
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(true), ThemeTestSupport.generated(false));
            owner.applyTheme(split, true);
            assertThat(entry.window()).isEqualTo(detached.id());
            assertThat(moved.getClientProperty("Jasper.zone")).isEqualTo("workspace");
            assertThat(moved.getClientProperty("Jasper.workspaceTheme")).isNull();
            assertThat(moved.getClientProperty("Jasper.workspace.dark")).isNull();
            assertThat(moved.getClientProperty("Jasper.workspace.foreground")).isNull();
            assertThat(movedList.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(dev.jasper.app.platform.ThemeZone.theme(retainedList)).isSameAs(split.workspace());
            detached.close();
        });
    }

    @Test void movingContentBetweenWindowsReplacesRouteAndRetainsDocument() throws Exception {
        edt(() -> {
            var firstTheme = ThemeTestSupport.controller(false);
            firstTheme.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var first = content(launcher(new ArrayDeque<>()), firstTheme);
            var second = new WindowContent(launcher(new ArrayDeque<>()), null, path -> {}, () -> {},
                // One installed look per process: the second window shows the same variant.
                ThemeTestSupport.controller(true));
            var contributions = new Contributions(); first.connectContributions(contributions); second.connectContributions(contributions);
            contributions.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(first::id,
                tab -> contributions.tabs().move(tab, second.id())));
            var text = new JTextArea("before"); var document = text.getDocument();
            var undo = new javax.swing.undo.UndoManager(); document.addUndoableEditListener(undo);
            text.append(" after"); text.select(1, 4);
            var entry = contributions.tabs().open("fixture", "pane", "Pane", first.id(), text);
            assertThat(dev.jasper.app.platform.ThemeZone.theme(text)).isSameAs(firstTheme.current().workspace());
            contributions.tabs().moveToNewWindow(entry);
            assertThat(text.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(text.getDocument()).isSameAs(document);
            assertThat(undo.canUndo()).isTrue();
            assertThat(text.getSelectedText()).isEqualTo("efo");
            contributions.tabs().moveToWindow(entry, first.id());
            assertThat(dev.jasper.app.platform.ThemeZone.theme(text)).isSameAs(firstTheme.current().workspace());
            assertThat(text.getDocument()).isSameAs(document);
            assertThat(undo.canUndo()).isTrue();
            second.close();
        });
    }

    @Test void pluginStopAndWindowCloseReleaseRoutes() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            var entry = contributions.tabs().open("fixture", "pane", "Pane", owner.id(), panel);
            contributions.tabs().remove(entry);
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            var next = contributions.tabs().open("fixture", "pane2", "Pane", owner.id(), panel);
            owner.close();
            assertThat(panel.getClientProperty("Jasper.zone")).isNull();
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
        });
    }

    @Test void chromeRebuildThenMatchUsesCurrentChrome() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var panel = new JPanel(); var list = new JList<>(new String[] {"entry"}); panel.add(list);
            contributions.tabs().open("fixture", "pane", "Pane", owner.id(), panel);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            assertThat(list.getClientProperty("Jasper.workspaceStyle")).isNull();
            // An unfocused list shows the look and feel's inactive selection, as a fresh list does.
            assertThat(list.getSelectionBackground()).isEqualTo(new JList<>(new String[] {"entry"}).getSelectionBackground());
        });
    }

    @Test void sidebarAndAuxiliaryContentRemainChrome() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var railBackground = owner.leftStripe().getBackground();
            // A dark workspace inside the light chrome (Decision 5): the rails stay chrome.
            owner.applyTheme(ThemeTestSupport.mixed(ThemeTestSupport.generated(false), ThemeTestSupport.generated(true)), true);
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

    @Test void hostedDescendantsKeepTheirOwnPresentationWithoutExemption() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var contributions = new Contributions(); owner.connectContributions(contributions);
            var root = new JPanel(); var editor = new JPanel(); editor.putClientProperty("Jasper.workspace.exempt", true);
            var area = new JTextArea(); editor.add(area);
            var nearby = new JList<>(new String[] {"entry"}); root.add(editor); root.add(nearby);
            contributions.tabs().open("fixture", "editor", "Editor", owner.id(), root);
            assertThat(area.getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(dev.jasper.app.platform.ThemeZone.theme(nearby)).isSameAs(themes.current().workspace());
            assertThat(nearby.getClientProperty("FlatLaf.style")).isNull();
        });
    }
}
