package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Connects the Settings dialog's Plugins page to the runtime's operations and to the restart conversation. Every change
 * takes effect at the next launch, so the manager's main job after an operation is to say whether a restart is due.
 * EDT only.
 */
public final class PluginManager {
    /**
     * What the manager needs from the application.
     *
     * @param chooseZip asks the user for a plugin zip over the Settings dialog; empty when cancelled
     * @param restarts the restart conversation
     * @param quit quits Jasper, for when it cannot restart itself
     * @param resident the resident process, probed for the standalone notice
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     * @param worker runs the blocking resident probe
     * @param browse opens a plugin's homepage in the browser
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,
                        java.util.function.Consumer<Path> openInEditor, java.util.function.Consumer<Path> reveal,
                        java.util.function.Consumer<java.net.URI> browse) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
            Objects.requireNonNull(openInEditor); Objects.requireNonNull(reveal); Objects.requireNonNull(browse);
        }
    }

    private final PluginRuntime runtime;
    private final AuxiliaryWindows windows;
    private final Hooks hooks;
    private Supplier<AuxiliarySurface> host;
    private java.util.function.Consumer<String> navigate = id -> { };
    private PluginManagerPanel panel;
    private PluginRuntime.Snapshot snapshot = new PluginRuntime.Snapshot(List.of(), false, false);
    private AuxiliarySurface consentDialog;
    private ConsentView consent;
    private ConfirmView confirm;
    private AuxiliarySurface confirmDialog;

    /**
     * Creates the manager; nothing is shown until Settings shows its Plugins page.
     *
     * @param runtime the plugin runtime
     * @param windows builds the consent and confirm dialogs
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

    /** The Plugins page for one opening of Settings; it lists the plugins afresh. */
    public SettingsPage page(PageContext context) { return new PluginsPage(this, context); }

    /** Builds the list for a page whose dialogs open over {@code host} and which moves Settings with {@code navigate}. */
    PluginManagerPanel attach(Supplier<AuxiliarySurface> host, java.util.function.Consumer<String> navigate) {
        this.host = Objects.requireNonNull(host);
        this.navigate = Objects.requireNonNull(navigate);
        panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(this::toggle, this::review, this::remove, this::discardInstall,
            this::install, this::openSettings, this::openFolder, this::openData, hooks.browse()));
        panel.show(snapshot);
        renderBanner();
        return panel;
    }

    /** The page is gone: forget its list and end any restart conversation. */
    void detach(PluginManagerPanel detached) {
        if (panel != detached) return;
        panel = null;
        host = null;
        navigate = id -> { };
        hooks.restarts().cancel();
    }

    /** The plugins as last listed. */
    List<PluginRuntime.Row> rows() { return snapshot.rows(); }

    /** Lists the plugins again: another process may have changed plugins.toml since the page was last shown. */
    void refresh() {
        runtime.snapshot(this::accept);
        if (panel == null || !hooks.standaloneNotice()) return;
        hooks.worker().execute(() -> {
            boolean live = hooks.resident().live().getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (panel != null) panel.notice(live
                    ? "Another Jasper process is still running with the plugin set it started with. Quit it for your changes to apply there." : "");
            });
        });
    }

    private void accept(PluginRuntime.Outcome outcome) { accept(outcome, ""); }

    private void accept(PluginRuntime.Outcome outcome, String success) {
        if (outcome.ok()) snapshot = outcome.snapshot();
        if (panel == null) return;
        panel.busy(false);
        if (!outcome.ok()) { panel.message(outcome.message(), true); return; }
        panel.show(snapshot);
        panel.message(success, false);
        renderBanner();
    }

    private void renderBanner() {
        if (panel == null) return;
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
        AuxiliarySurface dialog = windows.dialog("Remove " + row.name(), true, host.get());
        var view = new ConfirmView("Remove " + row.name() + " " + row.version() + "?\n\nIts jars, settings and data in " + row.directory()
            + " are deleted the next time Jasper starts. Keep undoes this until then.", "Remove",
            () -> { dialog.close(); panel.busy(true); runtime.remove(row.id(), true, this::accept); }, dialog::close);
        dialog.onClosed(() -> { if (confirmDialog == dialog) { confirmDialog = null; confirm = null; } });
        dialog.setContent(view);
        confirmDialog = dialog;
        confirm = view;
        dialog.show();
    }

    /** A running plugin with a Settings page goes there; otherwise, off the EDT, its file is seeded and opened. */
    private void openSettings(PluginRuntime.Row row) {
        if (runtime.hasSettingsPage(row.id())) { navigate.accept(PluginRuntime.settingsPageId(row.id())); return; }
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
        SwingUtilities.invokeLater(() -> { if (panel != null) panel.message(failure.getMessage() == null ? failure.toString() : failure.getMessage(), true); });
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
        Optional<Path> zip = hooks.chooseZip().apply(host.get());
        if (zip.isEmpty()) return;
        panel.busy(true);
        panel.message("", false);
        runtime.inspect(zip.get(), (inspection, problem) -> {
            if (panel == null) { if (inspection != null) runtime.discard(inspection); return; }
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
     * A modal consent dialog over Settings. The decision is taken in the button callbacks rather than after
     * {@code show()} returns: a modal show blocks until the dialog closes, and closing it by its title bar is a refusal.
     */
    private void ask(String title, String name, String version, String vendor, List<String> capabilities, boolean update,
                     String allowLabel, Runnable allowed, Runnable refused) {
        AuxiliarySurface dialog = windows.dialog(title, true, host.get());
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
