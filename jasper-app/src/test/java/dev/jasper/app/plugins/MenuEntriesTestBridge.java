package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.MenuItem;
import java.util.List;
import java.util.Set;

/**
 * Test-only owner-package bridge (browser plan 7 Task 1b): one plugin's menu entries, written to a model as its context
 * writes them, and the incidents its code raises, for the window's tests in {@code workspace}, which name no SDK type.
 * UI thread.
 */
public final class MenuEntriesTestBridge {
    private final Containment containment = new Containment(() -> true);
    private final String pluginId;
    private final HostedUi ui;

    public MenuEntriesTestBridge(Contributions model, String pluginId) {
        this.pluginId = pluginId;
        var auxiliary = new dev.jasper.app.windows.AuxiliaryWindows(dev.jasper.app.persistence.UiState.inMemory(),
            surface -> new dev.jasper.app.windows.AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
                () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()));
        var terminals = new HostedTerminals(pluginId, new CapabilityGate(pluginId, Set.of()), new TerminalFixture().registry,
            Runnable::run, () -> true, () -> true,
            new HostedSessions(pluginId, containment, Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
        ui = new HostedUi(pluginId, model, containment, Runnable::run, javax.swing.SwingUtilities::isEventDispatchThread, () -> true,
            getClass().getClassLoader(), () -> Variant.DARK, handler -> () -> { }, auxiliary, terminals);
    }

    /** A new top-level menu of the plugin's, titled {@code title}, with one entry labelled {@code label} whose choice throws. */
    public void throwingEntry(String menuId, String title, String label) {
        ui.menus().create(menuId, title).addEntries(List.of(new MenuItem("fails", label, "", "fails", true, false)),
            id -> { throw new IllegalStateException("The entry " + id + " failed"); });
    }

    /** How many incidents the plugin's code has raised. */
    public int incidents() { return containment.failures(pluginId); }
}
