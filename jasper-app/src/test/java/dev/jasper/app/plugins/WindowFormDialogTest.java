package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * A modeless plugin dialog showing a WINDOW form is a window (the Credential Vault manager): at its first show HostedUi
 * makes it window-sized, keyed by its plugin and title, so it can be resized and remembers its bounds; a modal one and a
 * modeless one showing another form stay packed to their content. Every plugin dialog closes with its Jasper window.
 */
class WindowFormDialogTest {
    private static Nodes.Form form(Nodes.FormKind kind) {
        return new Nodes.Form("root", ControlState.READY, kind, "Manager", Optional.empty(),
            List.of(new Nodes.Text("body", ControlState.READY, "Body", Nodes.TextRole.BODY)), Optional.empty(), List.of());
    }

    /** The host's UI for one test plugin over headless windows and a terminal fixture. UI thread. */
    record Host(AuxiliaryWindows windows, TerminalFixture fixture, HostedTerminals terminals, HostedUi ui, HostedKit kit) implements AutoCloseable {
        static Host start() {
            var containment = new Containment(java.awt.EventQueue::isDispatchThread);
            var windows = AppContractTest.headlessWindows();
            var fixture = new TerminalFixture();
            var terminals = new HostedTerminals("test.kit", new CapabilityGate("test.kit", java.util.Set.of()), fixture.registry, Runnable::run, () -> true,
                () -> true, new HostedSessions("test.kit", containment, Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, Host.class.getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, terminals);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            return new Host(windows, fixture, terminals, ui, kit);
        }
        AuxiliarySurface surface(String title) { return windows.open().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow(); }
        @Override public void close() { ui.closeAll(); kit.close(); }
    }

    @Test void onlyAModelessWindowFormDialogOpensWindowSized() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var host = Host.start()) {
                var owner = host.terminals().windowHandle(host.fixture().addWindow());
                record Case(String title, boolean modal, Nodes.FormKind kind) { }
                for (var each : List.of(new Case("Credential Vault", false, Nodes.FormKind.WINDOW), new Case("Errors", true, Nodes.FormKind.WINDOW),
                        new Case("Add Host", false, Nodes.FormKind.FULL))) {
                    var dialog = host.ui().windows().dialog(new DialogSpec(each.title(), owner, each.modal()));
                    dialog.setContent(host.kit().create(new ViewSpec(form(each.kind())), event -> { }));
                    AuxiliarySurface surface = host.surface(each.title());
                    assertThat(surface.windowSized()).as("%s before its first show", each.title()).isFalse();
                    dialog.show();
                    boolean window = !each.modal() && each.kind() == Nodes.FormKind.WINDOW;
                    assertThat(surface.windowSized()).as(each.title()).isEqualTo(window);
                    assertThat(surface.id()).as("%s's remembered key", each.title()).isEqualTo(window ? "test.kit|" + each.title() : "");
                    dialog.show();
                    assertThat(surface.id()).as("a second show changes nothing").isEqualTo(window ? "test.kit|" + each.title() : "");
                    dialog.close();
                }
            }
        });
    }

    /** Review fix round 1 (M1): a view the plugin closed before the first show is no WINDOW form to size; show still works. */
    @Test void aClosedViewOpensPacked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var host = Host.start()) {
                var owner = host.terminals().windowHandle(host.fixture().addWindow());
                var dialog = host.ui().windows().dialog(new DialogSpec("Credential Vault", owner, false));
                var view = host.kit().create(new ViewSpec(form(Nodes.FormKind.WINDOW)), event -> { });
                dialog.setContent(view);
                view.close();
                assertThatCode(dialog::show).doesNotThrowAnyException();
                assertThat(host.surface("Credential Vault").windowSized()).isFalse();
                dialog.close();
            }
        });
    }

    /**
     * Review fix round 1 (M3): the remembered key keeps to the 255 characters UiState keeps, its title cut short, and never
     * ends in half of a surrogate pair.
     */
    @Test void aLongTitleIsCutToTheKeyUiStateKeeps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var host = Host.start()) {
                var owner = host.terminals().windowHandle(host.fixture().addWindow());
                String smile = new String(new char[] {(char) 0xD83D, (char) 0xDE00});
                // "test.kit|" is 9 characters, so the pair would straddle the 255th.
                for (String title : List.of("t".repeat(300), "t".repeat(245) + smile + "t".repeat(20))) {
                    var dialog = host.ui().windows().dialog(new DialogSpec(title, owner, false));
                    dialog.setContent(host.kit().create(new ViewSpec(form(Nodes.FormKind.WINDOW)), event -> { }));
                    dialog.show();
                    String key = host.surface(title).id();
                    assertThat(key).startsWith("test.kit|ttt").hasSizeLessThanOrEqualTo(dev.jasper.app.persistence.UiState.WINDOW_KEY_LENGTH);
                    assertThat(Character.isHighSurrogate(key.charAt(key.length() - 1))).as("no half pair at the cut").isFalse();
                    assertThat(key.length()).isEqualTo(title.contains(smile) ? 254 : 255);
                    dialog.close();
                }
            }
        });
    }

    /**
     * Review fix round 1 (I1): every plugin dialog dies with its Jasper window, as its native dialog does, so nothing keeps
     * an invisible surface open; here a modeless one over a child window that closes with its last tab.
     */
    @Test void aDialogClosesWithItsWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var host = Host.start()) {
                UUID child = host.fixture().addWindow();
                UUID shell = host.fixture().addTab(child, "shell");
                var owner = host.terminals().windowHandle(child);
                int[] closed = {0};
                for (boolean modal : List.of(false, true)) {
                    var dialog = host.ui().windows().dialog(new DialogSpec(modal ? "Modal" : "Modeless", owner, modal));
                    dialog.setContent(host.kit().create(new ViewSpec(form(Nodes.FormKind.WINDOW)), event -> { }));
                    dialog.onClosed(() -> closed[0]++);
                    dialog.show();
                }
                var modeless = host.surface("Modeless"); var modal = host.surface("Modal");
                host.fixture().closeTab(shell);
                assertThat(modeless.closed()).as("the modeless dialog").isTrue();
                assertThat(modal.closed()).as("the modal dialog").isTrue();
                assertThat(closed[0]).as("the plugin's close handlers ran").isEqualTo(2);
                assertThat(host.windows().open()).as("no surface is left open").doesNotContain(modeless, modal);
            }
        });
    }
}
