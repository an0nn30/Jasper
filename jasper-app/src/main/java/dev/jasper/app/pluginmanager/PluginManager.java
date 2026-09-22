package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Opens the Plugins manager window and connects its view to the runtime's operations and to the restart
 * conversation. Every change takes effect at the next launch, so the manager's main job after an operation is
 * to say whether a restart is due. EDT only.
 */
public final class PluginManager {
    /** The auxiliary window id, which is also the key its bounds are remembered under. */
    public static final String WINDOW_ID = "app.plugins.manager";

    /**
     * What the manager needs from the application.
     *
     * @param chooseZip asks the user for a plugin zip over the manager's window; empty when cancelled
     * @param restarts the restart conversation
     * @param quit quits Jasper, for when it cannot restart itself
     * @param resident the resident process, probed for the standalone notice
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     * @param worker runs the blocking resident probe
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,
                        java.util.function.Consumer<Path> openInEditor, java.util.function.Consumer<Path> reveal) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
            Objects.requireNonNull(openInEditor); Objects.requireNonNull(reveal);
        }
    }

    private final PluginRuntime runtime;
    private final AuxiliaryWindows windows;
    private final Hooks hooks;
    private AuxiliarySurface surface;
    private PluginManagerPanel panel;
    private PluginRuntime.Snapshot snapshot = new PluginRuntime.Snapshot(List.of(), false, false);
    private AuxiliarySurface consentDialog;
    private ConsentView consent;
    private ConfirmView confirm;
    private AuxiliarySurface confirmDialog;

    /**
     * Creates the manager; nothing is shown until {@link #open}.
     *
     * @param runtime the plugin runtime
     * @param windows builds the manager's window and its dialogs
     * @param hooks what the application provides
     */
    public PluginManager(PluginRuntime runtime, AuxiliaryWindows windows, Hooks hooks) {
        this.runtime = Objects.requireNonNull(runtime); this.windows = Objects.requireNonNull(windows);
        this.hooks = Objects.requireNonNull(hooks);
        hooks.restarts().onChanged(this::renderBanner);
    }

    PluginManagerPanel panel() { return panel; }
    ConsentView consent() { return consent; }
    ConfirmView confirm() { return confirm; }

    /** Shows the manager, or brings it forward, and lists the plugins afresh. */
    public void open() {
        AuxiliarySurface window = windows.window(WINDOW_ID, "Plugins", new Dimension(760, 520), true);
        if (window != surface) {
            surface = window;
            panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(this::toggle, this::review, this::remove, this::discardInstall, this::install,
                this::openSettings, this::openFolder, this::openData));
            window.setContent(panel);
            // Another process may have changed plugins.toml while this window was in the background.
            window.onActivated(this::refresh);
            window.onClosed(() -> { if (surface == window) { surface = null; hooks.restarts().cancel(); } });
        }
        window.show();
        window.toFront();
        refresh();
    }

    private void refresh() {
        if (surface == null) return;
        runtime.snapshot(this::accept);
        if (!hooks.standaloneNotice()) return;
        hooks.worker().execute(() -> {
            boolean live = hooks.resident().live().getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (surface != null) panel.notice(live
                    ? "Another Jasper process is still running with the plugin set it started with. Quit it for your changes to apply there." : "");
            });
        });
    }

    private void accept(PluginRuntime.Outcome outcome) { accept(outcome, ""); }

    private void accept(PluginRuntime.Outcome outcome, String success) {
        if (surface == null) return;
        panel.busy(false);
        if (!outcome.ok()) { panel.message(outcome.message(), true); return; }
        snapshot = outcome.snapshot();
        panel.show(snapshot);
        panel.message(success, false);
        renderBanner();
    }

    private void renderBanner() {
        if (surface == null) return;
        RestartFlow flow = hooks.restarts();
        var launchAnyway = new PluginManagerPanel.BannerAction("Launch Anyway", flow::launchAnyway);
        var cancel = new PluginManagerPanel.BannerAction("Cancel", flow::cancel);
        switch (flow.state()) {
            case UNAVAILABLE -> panel.banner("Jasper could not work out how it was started. Quit and reopen it to apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit Jasper", hooks.quit())));
            case PROBING -> panel.banner("Looking for another Jasper process…", List.of());
            case RESIDENT_FOUND -> panel.banner("Another Jasper process is running with the previous plugin set. It has to quit before a normal restart can apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit It and Restart", flow::askResidentToQuit), launchAnyway, cancel));
            case WAITING -> panel.banner("Waiting for the other Jasper process to quit…", List.of(launchAnyway, cancel));
            case RESIDENT_STUCK -> panel.banner("The other Jasper process did not quit. Launch Anyway starts a separate Jasper with your changes; the other one keeps the previous plugin set until you quit it.",
                List.of(new PluginManagerPanel.BannerAction("Try Again", flow::askResidentToQuit), launchAnyway, cancel));
            case IDLE -> {
                if (snapshot.safeMode()) panel.banner("Safe mode: installed plugins are not loaded.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Normally", flow::restartNormally)));
                else if (snapshot.restartNeeded()) panel.banner("Restart Jasper to apply your changes.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Now", flow::restartNow)));
                else panel.banner("", List.of());
            }
        }
    }

    private void toggle(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.setEnabled(row.id(), !row.enabled(), this::accept);
    }

    private void remove(PluginRuntime.Row row) {
        if (row.pendingRemoval()) { panel.busy(true); runtime.remove(row.id(), false, this::accept); return; }
        AuxiliarySurface dialog = windows.dialog("Remove " + row.name(), true, surface);
        var view = new ConfirmView("Remove " + row.name() + " " + row.version() + "?\n\nIts jars, settings and data in " + row.directory()
            + " are deleted the next time Jasper starts. Keep undoes this until then.", "Remove",
            () -> { dialog.close(); panel.busy(true); runtime.remove(row.id(), true, this::accept); }, dialog::close);
        dialog.onClosed(() -> { if (confirmDialog == dialog) { confirmDialog = null; confirm = null; } });
        dialog.setContent(view);
        confirmDialog = dialog;
        confirm = view;
        dialog.show();
    }

    /** Off the EDT: seeds the file if needed, then hands it to the editor; a failure is shown in the window. */
    private void openSettings(PluginRuntime.Row row) {
        hooks.worker().execute(() -> {
            try { runtime.prepareSettings(row.id()); hooks.openInEditor().accept(row.settingsFile()); }
            catch (java.io.IOException | RuntimeException failure) { failed(failure); }
        });
    }

    private void openFolder(PluginRuntime.Row row) { reveal(row.directory()); }
    private void openData(PluginRuntime.Row row) { reveal(row.dataDirectory()); }

    private void reveal(Path directory) {
        hooks.worker().execute(() -> {
            try { java.nio.file.Files.createDirectories(directory); hooks.reveal().accept(directory); }
            catch (java.io.IOException | RuntimeException failure) { failed(failure); }
        });
    }

    private void failed(Exception failure) {
        javax.swing.SwingUtilities.invokeLater(() -> { if (surface != null) panel.message(failure.getMessage() == null ? failure.toString() : failure.getMessage(), true); });
    }

    private void discardInstall(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.discardInstall(row.id(), this::accept);
    }

    private void review(PluginRuntime.Row row) {
        ask("Review " + row.name(), row.name(), row.version(), row.vendor(), row.capabilities(), false, "Allow and Enable", () -> {
            panel.busy(true);
            runtime.consent(row.id(), row.capabilities(), this::accept);
        }, () -> { });
    }

    private void install() {
        Optional<Path> zip = hooks.chooseZip().apply(surface);
        if (zip.isEmpty()) return;
        panel.busy(true);
        panel.message("", false);
        runtime.inspect(zip.get(), (inspection, problem) -> {
            if (surface == null) { if (inspection != null) runtime.discard(inspection); return; }
            panel.busy(false);
            if (inspection == null) { panel.message(problem, true); return; }
            ask("Install " + inspection.name(), inspection.name(), inspection.version(), inspection.vendor(),
                inspection.capabilities(), inspection.update(), "Install", () -> {
                    panel.busy(true);
                    runtime.install(inspection, outcome -> accept(outcome,
                        inspection.name() + " " + inspection.version() + " will be installed when Jasper restarts."));
                }, () -> runtime.discard(inspection));
        });
    }

    /**
     * A modal consent dialog over the manager. The decision is taken in the button callbacks rather than after
     * {@code show()} returns: a modal show blocks until the dialog closes, and closing it by its title bar is a refusal.
     */
    private void ask(String title, String name, String version, String vendor, List<String> capabilities, boolean update,
                     String allowLabel, Runnable allowed, Runnable refused) {
        AuxiliarySurface dialog = windows.dialog(title, true, surface);
        boolean[] decided = {false};
        var view = new ConsentView(name, version, vendor, capabilities, update, allowLabel,
            () -> { decided[0] = true; dialog.close(); allowed.run(); }, dialog::close);
        dialog.onClosed(() -> {
            if (consentDialog == dialog) { consentDialog = null; consent = null; }
            if (!decided[0]) refused.run();
        });
        dialog.setContent(view);
        consentDialog = dialog;
        consent = view;
        dialog.show();
    }
}
