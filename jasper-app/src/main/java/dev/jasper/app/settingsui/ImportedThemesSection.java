package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME;
import static dev.jasper.app.config.CoreSettings.UI_THEME_DARK;
import static dev.jasper.app.config.CoreSettings.UI_THEME_LIGHT;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Settings › Appearance's Imported themes group: Import… and each imported theme with what it offers and Remove.
 * Import and Remove are file operations that take effect at once; Remove also returns every pick of the removed theme
 * or its schemes, saved or staged, to its default. A message line under Import… reports the last outcome. EDT only;
 * the file work runs through {@link ThemeImports}.
 */
final class ImportedThemesSection {
    static final String ID = "imported-themes";
    private final ThemeImports hooks;
    private final PageContext context;
    private final Runnable render;
    private List<ThemeImports.Imported> entries;
    private String message = "";
    private boolean error, busy;

    private ImportedThemesSection(ThemeImports hooks, PageContext context, Runnable render) {
        this.hooks = hooks;
        this.context = context;
        this.render = render;
        entries = List.copyOf(hooks.imported().get());
    }

    /** The group as a page part. */
    static SchemaPage.Part part(ThemeImports hooks) {
        return new SchemaPage.Part(ID, "Imported themes", (context, render) -> {
            var section = new ImportedThemesSection(hooks, context, render);
            return new SchemaPage.Part.Section(section::node, section::event, section::refresh, () -> { });
        }, List.of("Import theme", "Import…", "Remove theme", "imported themes", "theme file", "Jasper theme"));
    }

    Nodes.Node node(String id) {
        var rows = new ArrayList<Nodes.Node>();
        var importButton = new Nodes.Button(id + ":import", new ControlState(true, !busy, busy, ""), "Import…", "",
            Nodes.ButtonKind.SECONDARY, id + ":import", "Import a Jasper theme file");
        rows.add(new Nodes.SettingRow(id + ":import-row", ControlState.READY, entries.isEmpty() ? "No imported themes" : "Jasper theme files",
            "Alacritty and IntelliJ themes can be converted with the theme converter in Jasper's source repository (tools/themes).",
            new Nodes.Stack(id + ":import-controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(importButton)), false, false));
        if (!message.isEmpty())
            rows.add(new Nodes.Text(id + ":message", ControlState.READY, message, Nodes.TextRole.SUPPORTING,
                error ? Optional.of(Nodes.Severity.DANGER) : Optional.empty(), OptionalInt.empty()));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var remove = new Nodes.Button(id + ":remove-" + i, new ControlState(true, !busy, false, ""), "Remove", "",
                Nodes.ButtonKind.SECONDARY, id + ":remove-" + i, "Remove " + entry.label());
            rows.add(new Nodes.SettingRow(id + ":theme-" + i, ControlState.READY, entry.label(), entry.offers(),
                new Nodes.Stack(id + ":theme-" + i + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(remove)), false, false));
        }
        return new Nodes.Card(id + ":card", ControlState.READY, Nodes.CardKind.SETTINGS, rows);
    }

    boolean event(UiEvent event) {
        if (!(event instanceof UiEvent.Action action) || busy) return false;
        if (action.id().equals(ID + ":import")) { chooseAndImport(); return true; }
        String prefix = ID + ":remove-";
        if (!action.id().startsWith(prefix)) return false;
        int index;
        try { index = Integer.parseInt(action.id().substring(prefix.length())); } catch (NumberFormatException ignored) { return false; }
        if (index < 0 || index >= entries.size()) return false;
        askRemove(entries.get(index));
        return true;
    }

    void refresh() { entries = List.copyOf(hooks.imported().get()); }

    private void chooseAndImport() {
        Optional<Path> chosen = hooks.chooseFile().apply(context.surface().get());
        if (chosen.isEmpty()) return;
        Path file = chosen.get();
        String fileName = String.valueOf(file.getFileName());
        working();
        hooks.worker().execute(() -> {
            try {
                var candidate = hooks.folder().prepare(file);
                hooks.ui().execute(() -> prepared(fileName, candidate));
            } catch (ThemeFolder.ImportFailure | RuntimeException failure) {
                hooks.ui().execute(() -> done("Could not import " + fileName + ": " + reason(failure), true));
            }
        });
    }

    private void prepared(String fileName, ThemeFolder.Candidate candidate) {
        if (!candidate.replaces()) { install(fileName, candidate); return; }
        done("", false);
        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Replace " + candidate.name(),
            "Replace " + candidate.name() + "?", "The imported " + candidate.name() + " is replaced by " + fileName + ".", "Replace",
            () -> install(fileName, candidate)));
    }

    private void install(String fileName, ThemeFolder.Candidate candidate) {
        working();
        hooks.worker().execute(() -> {
            try {
                hooks.folder().install(candidate);
                hooks.ui().execute(() -> reloaded(hooks.reload().get(), "Imported " + candidate.name() + ".", false));
            } catch (IOException | RuntimeException failure) {
                hooks.ui().execute(() -> done("Could not import " + fileName + ": " + reason(failure), true));
            }
        });
    }

    private void askRemove(ThemeImports.Imported entry) {
        var uses = new ArrayList<String>();
        if (names(UI_THEME_DARK, context.draft().value(UI_THEME_DARK), entry.id())) uses.add("It is your Dark theme; Slate Steel will be used instead.");
        if (names(UI_THEME_LIGHT, context.draft().value(UI_THEME_LIGHT), entry.id())) uses.add("It is your Light theme; Slate Steel will be used instead.");
        if (names(TERMINAL_SCHEME, context.draft().value(TERMINAL_SCHEME), entry.id()))
            uses.add("Its scheme is your terminal scheme; Same as UI theme will be used instead.");
        String consequence = "Its file, " + entry.id() + ".json, is deleted from the themes folder."
            + (uses.isEmpty() ? "" : " " + String.join(" ", uses));
        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Remove " + entry.label(),
            "Remove " + entry.label() + "?", consequence, "Remove", () -> remove(entry)));
    }

    private void remove(ThemeImports.Imported entry) {
        working();
        hooks.worker().execute(() -> {
            try {
                hooks.folder().remove(entry.id());
                hooks.ui().execute(() -> forget(entry));
            } catch (IOException | RuntimeException failure) {
                hooks.ui().execute(() -> done("Could not remove " + entry.id() + ".json: " + reason(failure), true));
            }
        });
    }

    /** Returns every pick of the removed theme or its schemes to its default: staged ones in the draft, saved ones at once. */
    private void forget(ThemeImports.Imported entry) {
        var draft = context.draft();
        var changes = new LinkedHashMap<Setting<?>, Optional<?>>();
        var expected = new LinkedHashMap<Setting<?>, Optional<?>>();
        for (Setting<String> setting : List.of(UI_THEME_DARK, UI_THEME_LIGHT, TERMINAL_SCHEME)) {
            Optional<String> saved = draft.saved(setting);
            if (saved.map(value -> names(setting, value, entry.id())).orElse(false)) {
                changes.put(setting, Optional.empty());
                expected.put(setting, saved);
            }
            if (names(setting, draft.value(setting), entry.id())) draft.stage(setting, Optional.empty());
        }
        context.changed().run();
        var written = changes.isEmpty() ? hooks.reload().get() : hooks.save().apply(new SettingsEdit(changes, expected));
        reloaded(written, "Removed " + entry.label() + ".", !changes.isEmpty());
    }

    /**
     * Shows {@code success} when {@code work} (a save or a reload of the configuration) completes, refreshing the group.
     * A save that fails reads nothing back, so the application's library would still list a theme whose file is gone:
     * when {@code saving}, the group has the application read the folder before it shows the failure.
     */
    private void reloaded(CompletableFuture<?> work, String success, boolean saving) {
        work.whenComplete((ignored, failure) -> hooks.ui().execute(() -> {
            if (failure == null) { refresh(); done(success, false); return; }
            String failed = success + " Its settings were not saved: " + reason(cause(failure));
            if (!saving) { refresh(); done(failed, true); return; }
            CompletableFuture<?> reread;
            try { reread = hooks.reload().get(); } catch (RuntimeException again) { reread = CompletableFuture.failedFuture(again); }
            reread.whenComplete((value, problem) -> hooks.ui().execute(() -> { refresh(); done(failed, true); }));
        }));
    }

    private static Throwable cause(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /** Why a file or settings operation failed, in words Settings shows after its "Could not …: ", never the bare path. */
    private static String reason(Throwable failure) {
        if (failure instanceof AccessDeniedException) return "permission denied.";
        if (failure instanceof NoSuchFileException) return "the file is no longer there.";
        if (failure instanceof FileSystemException system)
            return (system.getReason() == null ? system.getClass().getSimpleName() : system.getReason()) + ".";
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() + "." : message;
    }

    private static boolean names(Setting<String> setting, String value, String id) {
        return setting == TERMINAL_SCHEME ? value.startsWith(id + "/") : value.equals(id);
    }

    private void working() { busy = true; message = ""; error = false; render.run(); }

    private void done(String text, boolean failed) { busy = false; message = text; error = failed; render.run(); }
}
