package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * A modeless plugin dialog showing a WINDOW form is a window (the Credential Vault manager): at its first show HostedUi
 * makes it window-sized, keyed by its plugin and title, so it can be resized and remembers its bounds; a modal one and a
 * modeless one showing another form stay packed to their content.
 */
class WindowFormDialogTest {
    private static Nodes.Form form(Nodes.FormKind kind) {
        return new Nodes.Form("root", ControlState.READY, kind, "Manager", Optional.empty(),
            List.of(new Nodes.Text("body", ControlState.READY, "Body", Nodes.TextRole.BODY)), Optional.empty(), List.of());
    }

    @Test void onlyAModelessWindowFormDialogOpensWindowSized() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var containment = new Containment(java.awt.EventQueue::isDispatchThread);
            var windows = AppContractTest.headlessWindows();
            var fixture = new TerminalFixture();
            var terminals = new HostedTerminals("test.kit", new CapabilityGate("test.kit", java.util.Set.of()), fixture.registry, Runnable::run, () -> true,
                () -> true, new HostedSessions("test.kit", containment, Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
            var ui = new HostedUi("test.kit", new dev.jasper.app.contributions.Contributions(), containment,
                Runnable::run, java.awt.EventQueue::isDispatchThread, () -> true, getClass().getClassLoader(),
                () -> dev.jasper.sdk.Variant.DARK, h -> () -> {}, windows, terminals);
            var kit = new HostedKit("test.kit", () -> true, ui::kitThemes, ui.appearance(), containment);
            ui.kit(kit);
            try {
                var owner = terminals.windowHandle(fixture.addWindow());
                record Case(String title, boolean modal, Nodes.FormKind kind) { }
                for (var each : List.of(new Case("Credential Vault", false, Nodes.FormKind.WINDOW), new Case("Errors", true, Nodes.FormKind.WINDOW),
                        new Case("Add Host", false, Nodes.FormKind.FULL))) {
                    var dialog = ui.windows().dialog(new DialogSpec(each.title(), owner, each.modal()));
                    dialog.setContent(kit.create(new ViewSpec(form(each.kind())), event -> { }));
                    AuxiliarySurface surface = windows.open().stream().filter(s -> s.title().equals(each.title())).findFirst().orElseThrow();
                    assertThat(surface.windowSized()).as("%s before its first show", each.title()).isFalse();
                    dialog.show();
                    boolean window = !each.modal() && each.kind() == Nodes.FormKind.WINDOW;
                    assertThat(surface.windowSized()).as(each.title()).isEqualTo(window);
                    assertThat(surface.id()).as("%s's remembered key", each.title()).isEqualTo(window ? "test.kit|" + each.title() : "");
                    dialog.show();
                    assertThat(surface.id()).as("a second show changes nothing").isEqualTo(window ? "test.kit|" + each.title() : "");
                    dialog.close();
                }
            } finally { ui.closeAll(); kit.close(); }
        });
    }
}
