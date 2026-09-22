package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTestSupport;
import java.util.UUID;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowContributionsTest {
    private static final boolean MAC = System.getProperty("os.name").startsWith("Mac");

    private static KeyStroke stroke(String binding) { return KeyBindings.withOverrides(MAC, Map.of("new_tab", binding)).strokeFor("new_tab").orElseThrow(); }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    @Test void aContributedActionIsACommandAShortcutAndAnInvocationWithThisWindowsIdentity() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            ActionEntry early = model.addAction("dev.x.early", "Early", null, List.of(), Optional.empty(), seen::add);
            owner.connectContributions(model);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of("go"), Optional.of("cmd+alt+j"), seen::add);

            assertThat(owner.commands().find("dev.x.early")).as("actions registered before the window connected").isPresent();
            Command command = owner.commands().find("dev.x.run").orElseThrow();
            assertThat(command.action().getValue(Action.NAME)).isEqualTo("Run Tool");
            assertThat(command.keywords()).contains("go");
            assertThat(command.action().getValue(Action.ACCELERATOR_KEY)).isEqualTo(stroke("cmd+alt+j"));

            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).isTrue();
            owner.dispatchCommand(command);
            assertThat(seen).hasSize(2).allSatisfy(invocation -> {
                assertThat(invocation.windowId()).isEqualTo(owner.id());
                assertThat(invocation.paneId()).isEqualTo(Optional.ofNullable(owner.currentPane()).map(TerminalPane::id));
            });

            run.setEnabled(false);
            assertThat(command.action().isEnabled()).isFalse();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).as("a disabled app key is still not terminal input").isTrue();
            assertThat(seen).hasSize(2);
            run.setEnabled(true);
            run.setTitle("Run It");
            assertThat(command.action().getValue(Action.NAME)).isEqualTo("Run It");
            assertThat(command.action().getValue(Command.TITLE)).isEqualTo("Run It");
            early.close();
            assertThat(owner.commands().find("dev.x.early")).isEmpty();
        });
    }

    @Test void userBindingsWinRootBindingsFollowAndRemovalUnbinds() throws Exception {
        edt(() -> {
            var model = new Contributions();
            List<Contributions.Invocation> seen = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            owner.installRootBindings(root);
            owner.connectContributions(model);
            ActionEntry run = model.addAction("dev.x.run", "Run", null, List.of(), Optional.of("cmd+alt+j"), seen::add);
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+j"))).isEqualTo("dev.x.run");
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+t"))).isEqualTo("new_tab");

            owner.setBindings(KeyBindings.withOverrides(MAC, Map.of("dev.x.run", "cmd+alt+u")));
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+j"), null)).isFalse();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+u"), null)).isTrue();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+j"))).isNull();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+u"))).isEqualTo("dev.x.run");
            assertThat(seen).hasSize(1);

            run.close();
            assertThat(owner.dispatchShortcut(stroke("cmd+alt+u"), null)).isFalse();
            assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke("cmd+alt+u"))).isNull();
            assertThat(owner.dispatchShortcut(stroke("cmd+t"), null)).as("built-ins are untouched").isTrue();
        });
    }

    @Test void aClosedWindowStopsListening() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.close();
            model.addAction("dev.x.late", "Late", null, List.of(), Optional.empty(), invocation -> { });
            assertThat(owner.commands().find("dev.x.late")).isEmpty();
        });
    }

        @Test void aContributedScopeIsInThisWindowsPaletteItsShortcutSwitchesWhileOpenAndRequestsAreRouted() throws Exception {
            edt(() -> {
                try (WindowContent owner = CommandPaletteShortcutsTest.owner(true)) {
                var model = new Contributions();
                CommandPaletteShortcutsTest.install(owner);
                model.addAction("dev.x.open", "Open X", null, List.of(), Optional.of("cmd+alt+h"),
                    invocation -> owner.commandPalette().open("dev.x.scope"));
                var registration = model.addScope(PaletteTestSupport.scope("dev.x.scope", "dev.x.open"));
                owner.connectContributions(model);
                var palette = owner.commandPalette();
                assertThat(owner.scopes().find("dev.x.scope")).as("registered in the window on connect").isPresent();

                assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("closed: the plugin's handler runs").isTrue();
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
                palette.open(PaletteScope.COMMANDS_ID);
                assertThat(palette.activeScopeId()).isEqualTo(PaletteScope.COMMANDS_ID);
                assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("open: the router switches scope").isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
                assertThat(owner.dispatchShortcut(stroke("cmd+alt+h"), null)).as("again: dismisses").isTrue();
                assertThat(palette.isOpen()).isFalse();

                model.requestPalette(new Contributions.PaletteRequest(UUID.randomUUID(), "dev.x.scope", Optional.empty(), Optional.empty()));
                assertThat(palette.isOpen()).as("another window's request").isFalse();
                model.requestPalette(new Contributions.PaletteRequest(owner.id(), "dev.x.scope", Optional.of("al"), Optional.of("beta")));
                assertThat(palette.isOpen()).isTrue();
                assertThat(palette.activeScopeId()).isEqualTo("dev.x.scope");
                assertThat(palette.component().queryField().getText()).isEqualTo("al");
                assertThat(PaletteTestSupport.resultList(palette.component()).getSelectedValue().id()).isEqualTo("beta");
                model.requestPalette(new Contributions.PaletteRequest(owner.id(), "dev.x.scope", Optional.of("x"), Optional.empty()));
                assertThat(palette.isOpen()).as("a request for the showing scope dismisses").isFalse();

                var scope = model.addScope(PaletteTestSupport.scope("dev.x.late", null));
                assertThat(owner.scopes().find("dev.x.late")).as("added after connect").isPresent();
                scope.close(); registration.close();
                assertThat(owner.scopes().find("dev.x.late")).isEmpty();
                assertThat(owner.scopes().find("dev.x.scope")).isEmpty();
                }
            });
        }
}
