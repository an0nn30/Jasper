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
            var owner=content(launcher(new ArrayDeque<>())); var terminal=owner.currentTab();
            var model=new Contributions();owner.connectContributions(model);
            var panel=new JTextArea("text");
            var entry=model.content().open("dev.editor","file","File",owner.id(),panel);
            entry.presentation("File","host",new ImageIcon(),true,"/etc/file","host");
            assertThat(owner.currentTab()).isNull(); assertThat(owner.currentPane()).isNull();
            assertThat(owner.terminalTabs()).containsExactly(terminal);
            assertThat(owner.selectedContent()).isSameAs(entry);
            assertThat(owner.windowTabs().tabList().getComponentCount()).isEqualTo(2);
            assertThat(owner.action(ActionId.SPLIT_RIGHT).isEnabled()).isFalse();
            int[] find={0};entry.commands(List.of(new ContentCommand("find","Find",KeyStroke.getKeyStroke("meta F"),true,()->find[0]++)));
            owner.invoke(ActionId.FIND);assertThat(find[0]).isEqualTo(1);
            owner.reorderTab(1,0);assertThat(owner.selectedContent()).isSameAs(entry);
            owner.selectTab(terminal);assertThat(owner.currentPane()).isNotNull();
            entry.select();assertThat(owner.selectedContent()).isSameAs(entry);
            entry.requestClose();assertThat(owner.currentTab()).isSameAs(terminal);
        });
    }
    @Test void documentOnlyShortcutIsInstalledAndRemovedOnSelectionChange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var terminal = owner.currentTab();
            var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
            var model = new Contributions(); owner.connectContributions(model);
            var entry = model.content().open("dev.editor", "a", "A", owner.id(), new JPanel());
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
    @Test void aWindowConnectedWithoutHostingContentNeverReceivesPluginContent() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var real = content(launcher(new ArrayDeque<>()));
            real.connectContributions(model, UiState.inMemory());
            // The warm-up rehearsal: composed and wired like a window, but not a place where content opens.
            var rehearsal = content(launcher(new ArrayDeque<>()));
            rehearsal.connectContributions(model, UiState.inMemory(), false);
            rehearsal.setActive(true);
            var entry = model.content().open("dev.editor", "a", "A", null, new JPanel());
            assertThat(entry.window()).as("the main host stays the real window").isEqualTo(real.id());
            assertThat(real.selectedContent()).isSameAs(entry);
            assertThat(rehearsal.selectedContent()).isNull();
            assertThat(rehearsal.mayClose()).isTrue();
            rehearsal.close();
            assertThat(model.content().entries()).as("closing the rehearsal takes nothing with it").containsExactly(entry);
            assertThat(real.selectedContent()).isSameAs(entry);
        });
    }
    @Test void contentOpenedWhileOnlyAnUnhostedWindowExistsAsksTheApplicationForAHost() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var rehearsal = content(launcher(new ArrayDeque<>()));
            rehearsal.connectContributions(model, UiState.inMemory(), false);
            var created = new WindowContent[1];
            model.content().setFactory(separate -> {
                created[0] = content(launcher(new ArrayDeque<>()));
                created[0].connectContributions(model, UiState.inMemory());
                return created[0].id();
            });
            var entry = model.content().open("dev.editor", "a", "A", null, new JPanel());
            assertThat(created[0]).as("a real window was asked for").isNotNull();
            assertThat(entry.window()).isEqualTo(created[0].id());
            assertThat(rehearsal.selectedContent()).isNull();
        });
    }
    @Test void detachedWorkspaceDoesNotScheduleATerminalLaunch() throws Exception {
        edt(() -> {
            var pending = new ArrayDeque<Runnable>();
            try (var owner = new WindowContent(launcher(pending), null, path -> {}, () -> {}, () -> {})) {
                assertThat(pending).isEmpty();
                assertThat(owner.terminalTabs()).isEmpty();
                assertThat(owner.currentPane()).isNull();
                var model = new Contributions(); owner.connectContributions(model);
                var entry = model.content().open("dev.editor", "a", "A", owner.id(), new JPanel());
                assertThat(entry.detached()).isTrue();
                assertThat(owner.selectedContent()).isSameAs(entry);
            }
        });
    }
}
