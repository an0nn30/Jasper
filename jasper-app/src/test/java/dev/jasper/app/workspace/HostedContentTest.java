package dev.jasper.app.workspace;

import dev.jasper.app.contributions.*;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.persistence.UiState;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class HostedContentTest {
    @AfterEach void cleanup() throws Exception {closeOwners();}
    @Test void fileSelectionHasNoTerminalAndUsesTheSharedTabStrip() throws Exception {
        edt(()->{
            var owner=content(launcher(new ArrayDeque<>())); var terminal=owner.currentTerminal();
            var model=new Contributions();owner.connectContributions(model);
            var panel=new JTextArea("text");
            var entry=model.tabs().open("dev.editor","file","File",owner.id(),panel);
            entry.presentation("File","host",new ImageIcon(),true,"/etc/file","host");
            assertThat(owner.currentTerminal()).isNull(); assertThat(owner.currentPane()).isNull();
            assertThat(owner.terminalTabs()).containsExactly(terminal);
            assertThat(owner.selectedContent()).isSameAs(entry);
            assertThat(owner.windowTabs().tabList().getComponentCount()).isEqualTo(2);
            assertThat(owner.action(ActionId.SPLIT_RIGHT).isEnabled()).as("a split beside a plugin view (Rulings 11)").isTrue();
            int[] find={0};entry.commands(List.of(new ContentCommand("find","Find",KeyStroke.getKeyStroke("meta F"),true,()->find[0]++)));
            owner.invoke(ActionId.FIND);assertThat(find[0]).isEqualTo(1);
            owner.reorderParentTab(owner.parentTabOf(owner.tabs().get(1)).orElseThrow(), 0);assertThat(owner.selectedContent()).isSameAs(entry);
            owner.selectTab(terminal);assertThat(owner.currentPane()).isNotNull();
            entry.select();assertThat(owner.selectedContent()).isSameAs(entry);
            entry.requestClose();assertThat(owner.currentTerminal()).isSameAs(terminal);
        });
    }
    @Test void documentOnlyShortcutIsInstalledAndRemovedOnSelectionChange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var terminal = owner.currentTerminal();
            var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
            var model = new Contributions(); owner.connectContributions(model);
            var entry = model.tabs().open("dev.editor", "a", "A", owner.id(), new JPanel());
            int[] saves = {0};
            var stroke = KeyStroke.getKeyStroke("meta S");
            entry.commands(List.of(new ContentCommand("save", "Save", stroke, true, () -> saves[0]++)));
            var key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
            assertThat(key).isNotNull();
            root.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(root, 0, "save"));
            assertThat(saves[0]).isEqualTo(1);
            owner.selectTab(terminal);
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke)).isNull();
        });
    }
    @Test void staleContentMenuResolvesCurrentHostSelectionAfterSwitchMoveAndClose() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var owner = content(launcher(new ArrayDeque<>())); owner.connectContributions(model);
            var other = content(launcher(new ArrayDeque<>())); other.connectContributions(model);
            int[] calls = {0, 0};
            var a = model.tabs().open("dev.editor", "a", "A", owner.id(), new JPanel());
            a.commands(List.of(new ContentCommand("preview", "Preview", null, true, () -> calls[0]++)));
            var menu = owner.tabMenu(a);
            var item = java.util.Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                .filter(entry -> entry.getText().equals("Preview")).findFirst().orElseThrow();
            var b = model.tabs().open("dev.editor", "b", "B", owner.id(), new JPanel());
            b.commands(List.of(new ContentCommand("preview", "Preview", null, true, () -> calls[1]++)));
            item.doClick(); assertThat(calls).containsExactly(0, 1);
            model.tabs().move(a, other.id());
            item.doClick(); assertThat(calls).containsExactly(0, 2);
            b.commands(List.of(new ContentCommand("preview", "Preview", null, false, () -> calls[1]++)));
            item.doClick(); assertThat(calls).containsExactly(0, 2);
            b.requestClose(); item.doClick(); assertThat(calls).containsExactly(0, 2);
            assertThat(other.selectedContent()).isSameAs(a);
        });
    }

    @Test void previewClipboardKeysStayWithNativeReadOnlyTextComponent() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var model = new Contributions(); owner.connectContributions(model);
            var preview = new JEditorPane(); preview.setEditable(false);
            var entry = model.tabs().open("dev.editor", "preview", "Preview", owner.id(), preview);
            int[] mutations = {0};
            var paste = KeyStroke.getKeyStroke("meta V");
            var copy = KeyStroke.getKeyStroke("meta C");
            entry.commands(List.of(new ContentCommand("paste", "Paste", paste, true, () -> mutations[0]++),
                new ContentCommand("copy", "Copy", copy, true, () -> mutations[0]++)));
            assertThat(owner.dispatchShortcut(paste, preview)).isFalse();
            assertThat(owner.dispatchShortcut(copy, preview)).isFalse();
            assertThat(mutations).containsExactly(0);
        });
    }

    @Test void aWindowConnectedWithoutHostingContentNeverReceivesPluginContent() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var real = content(launcher(new ArrayDeque<>()));
            real.connectContributions(model, UiState.inMemory());
            // The warm-up rehearsal: composed and wired like a window, but not a place where content opens.
            var rehearsal = content(launcher(new ArrayDeque<>()));
            rehearsal.connectContributions(model, UiState.inMemory(), false);
            rehearsal.setActive(true);
            var entry = model.tabs().open("dev.editor", "a", "A", null, new JPanel());
            assertThat(entry.window()).as("the main host stays the real window").isEqualTo(real.id());
            assertThat(real.selectedContent()).isSameAs(entry);
            assertThat(rehearsal.selectedContent()).isNull();
            assertThat(rehearsal.mayClose()).isTrue();
            rehearsal.close();
            assertThat(model.tabs().hosted()).as("closing the rehearsal takes nothing with it").containsExactly(entry);
            assertThat(real.selectedContent()).isSameAs(entry);
        });
    }
    @Test void contentOpenedWhileOnlyAnUnhostedWindowExistsAsksTheApplicationForAHost() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var rehearsal = content(launcher(new ArrayDeque<>()));
            rehearsal.connectContributions(model, UiState.inMemory(), false);
            var created = new WindowContent[1];
            model.tabs().windows(new dev.jasper.app.contributions.TabRegistry.Windows(() -> {
                created[0] = content(launcher(new ArrayDeque<>()));
                created[0].connectContributions(model, UiState.inMemory());
                return created[0].id();
            }, id -> created[0] != null && created[0].id().equals(id), tab -> { }));
            var entry = model.tabs().open("dev.editor", "a", "A", null, new JPanel());
            assertThat(created[0]).as("a real window was asked for").isNotNull();
            assertThat(entry.window()).isEqualTo(created[0].id());
            assertThat(rehearsal.selectedContent()).isNull();
        });
    }
    @Test void anEmptyWorkspaceDoesNotScheduleATerminalLaunch() throws Exception {
        edt(() -> {
            var pending = new ArrayDeque<Runnable>();
            try (var owner = new WindowContent(launcher(pending), null, path -> {}, () -> {}, () -> {})) {
                assertThat(pending).isEmpty();
                assertThat(owner.terminalTabs()).isEmpty();
                assertThat(owner.currentPane()).isNull();
                var model = new Contributions(); owner.connectContributions(model);
                var entry = model.tabs().open("dev.editor", "a", "A", owner.id(), new JPanel());
                assertThat(entry.window()).isEqualTo(owner.id());
                assertThat(owner.selectedContent()).isSameAs(entry);
            }
        });
    }
}
