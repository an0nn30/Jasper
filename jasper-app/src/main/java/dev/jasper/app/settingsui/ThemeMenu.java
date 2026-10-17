package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME;
import static dev.jasper.app.config.CoreSettings.UI_THEME_DARK;
import static dev.jasper.app.config.CoreSettings.UI_THEME_LIGHT;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * The gear beside one of Settings › Appearance's theme dropdowns. Import Theme… copies a checked Jasper theme file into
 * the themes folder and picks it in that dropdown, staged like any pick; Remove deletes the theme the dropdown shows and
 * returns every pick of it or its schemes, saved or staged, to its default. Both take effect at once, outside Apply; a
 * failure is reported in a sheet. EDT only; the file work runs through {@link ThemeImports}.
 */
final class ThemeMenu {
    /** The dropdown a gear sits beside: what Import picks and whose theme Remove removes. */
    enum Target { DARK, LIGHT, TERMINAL }

    private final ThemeImports hooks;
    private final Supplier<InstalledThemes> themes;
    private final PageContext context;
    private final Target target;
    private final Runnable render;
    private final String icon;
    private boolean busy;

    /** A menu whose button shows {@code icon}: the gear, or the ⋯ of the classic composition (spec 12.2). */
    ThemeMenu(ThemeImports hooks, Supplier<InstalledThemes> themes, PageContext context, Target target, Runnable render, String icon) {
        this.icon = icon;
        this.hooks = hooks;
        this.themes = themes;
        this.context = context;
        this.target = target;
        this.render = render;
    }

    /** The gear {@code id} for a dropdown showing {@code shown}: a theme id, or for the terminal a scheme id or {@code match}. */
    Nodes.Node gear(String id, String shown) {
        var removable = removable(shown);
        var items = List.of(
            new MenuItem(id + ":import", "Import Theme…", "", id + ":import", !busy, false),
            new MenuItem(id + ":separator", "", "", "", false, true),
            new MenuItem(id + ":remove", removable.map(theme -> "Remove " + label(theme)).orElse("Remove Theme"), "", id + ":remove",
                removable.isPresent() && !busy, false));
        return new Nodes.MenuButton(id, new ControlState(true, !busy, busy, ""), "", icon, Nodes.ButtonKind.GHOST, "", items,
            "Import or remove themes");
    }

    /** Runs the item of gear {@code id} that {@code event} chose for a dropdown showing {@code shown}; false for any other event. */
    boolean event(String id, UiEvent event, String shown) {
        if (!(event instanceof UiEvent.Action action) || busy) return false;
        if (action.id().equals(id + ":import")) { chooseAndImport(); return true; }
        if (!action.id().equals(id + ":remove")) return false;
        removable(shown).ifPresent(this::askRemove);
        return true;
    }

    /** The imported theme {@code shown} belongs to, if there is one to remove. */
    private Optional<String> removable(String shown) {
        String theme = target != Target.TERMINAL ? shown : shown.contains("/") ? shown.substring(0, shown.indexOf('/')) : "";
        return theme.isEmpty() || hooks.folder().fixed(theme) || !themes.get().names().containsKey(theme) ? Optional.empty() : Optional.of(theme);
    }

    private String label(String theme) { return themes.get().names().getOrDefault(theme, theme); }

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
                hooks.ui().execute(() -> failed("Could not import " + fileName, failure instanceof ThemeFolder.ImportFailure ? failure.getMessage() : reason(failure)));
            }
        });
    }

    private void prepared(String fileName, ThemeFolder.Candidate candidate) {
        if (!candidate.replaces()) { install(fileName, candidate); return; }
        done();
        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Replace " + candidate.name(),
            "Replace " + candidate.name() + "?", "The imported " + candidate.name() + " is replaced by " + fileName + ".", "Replace",
            () -> install(fileName, candidate)));
    }

    private void install(String fileName, ThemeFolder.Candidate candidate) {
        working();
        hooks.worker().execute(() -> {
            try {
                hooks.folder().install(candidate);
                hooks.ui().execute(() -> hooks.reload().get().whenComplete((ignored, failure) -> hooks.ui().execute(() -> {
                    if (failure == null) pick(candidate);
                    else failed("Imported " + candidate.name(), "Settings could not read the themes folder again: " + reason(cause(failure)));
                })));
            } catch (IOException | RuntimeException failure) {
                hooks.ui().execute(() -> failed("Could not import " + fileName, reason(failure)));
            }
        });
    }

    /** Picks the imported theme in this gear's dropdown, staged, or says where it is when it has nothing for this dropdown. */
    private void pick(ThemeFolder.Candidate candidate) {
        var installed = themes.get();
        String id = candidate.id();
        Optional<String> pick = switch (target) {
            case DARK -> installed.dark().stream().anyMatch(theme -> theme.id().equals(id)) ? Optional.of(id) : Optional.empty();
            case LIGHT -> installed.light().stream().anyMatch(theme -> theme.id().equals(id)) ? Optional.of(id) : Optional.empty();
            case TERMINAL -> installed.schemes().stream().map(InstalledThemes.SchemeOption::key).filter(key -> key.startsWith(id + "/")).findFirst();
        };
        if (pick.isEmpty()) { failed("Imported " + candidate.name(), elsewhere(installed, id, candidate.name())); return; }
        context.draft().stage(setting(), pick);
        context.changed().run();
        done();
    }

    /** Where an imported theme with nothing for this gear's dropdown can be picked. */
    private String elsewhere(InstalledThemes installed, String id, String name) {
        if (!installed.names().containsKey(id)) return name + " was copied into the themes folder, but Settings cannot list it.";
        boolean dark = installed.dark().stream().anyMatch(theme -> theme.id().equals(id));
        boolean light = installed.light().stream().anyMatch(theme -> theme.id().equals(id));
        if (target == Target.DARK && light) return name + " has no Dark variant; pick it under Light theme.";
        if (target == Target.LIGHT && dark) return name + " has no Light variant; pick it under Dark theme.";
        if (target != Target.TERMINAL) return name + " is a terminal scheme; pick it under Terminal scheme.";
        return name + " has no terminal scheme.";
    }

    private Setting<String> setting() {
        return switch (target) { case DARK -> UI_THEME_DARK; case LIGHT -> UI_THEME_LIGHT; case TERMINAL -> TERMINAL_SCHEME; };
    }

    private void askRemove(String theme) {
        String label = label(theme);
        var uses = new ArrayList<String>();
        // Removal saves no value, so the effective default replaces it: the look's theme under a kit's defaults (spec 5.8).
        if (names(UI_THEME_DARK, context.draft().value(UI_THEME_DARK), theme))
            uses.add("It is your Dark theme; " + label(context.draft().defaultValue(UI_THEME_DARK)) + " will be used instead.");
        if (names(UI_THEME_LIGHT, context.draft().value(UI_THEME_LIGHT), theme))
            uses.add("It is your Light theme; " + label(context.draft().defaultValue(UI_THEME_LIGHT)) + " will be used instead.");
        if (names(TERMINAL_SCHEME, context.draft().value(TERMINAL_SCHEME), theme))
            uses.add("Its scheme is your terminal scheme; Same as UI theme will be used instead.");
        String consequence = "Its file, " + theme + ".json, is deleted from the themes folder."
            + (uses.isEmpty() ? "" : " " + String.join(" ", uses));
        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Remove " + label,
            "Remove " + label + "?", consequence, "Remove", () -> remove(theme, label)));
    }

    private void remove(String theme, String label) {
        working();
        hooks.worker().execute(() -> {
            try {
                hooks.folder().remove(theme);
                hooks.ui().execute(() -> forget(theme, label));
            } catch (IOException | RuntimeException failure) {
                hooks.ui().execute(() -> failed("Could not remove " + label, reason(failure)));
            }
        });
    }

    /** Returns every pick of the removed theme or its schemes to its default: staged ones in the draft, saved ones at once. */
    private void forget(String theme, String label) {
        var draft = context.draft();
        var changes = new LinkedHashMap<Setting<?>, Optional<?>>();
        var expected = new LinkedHashMap<Setting<?>, Optional<?>>();
        for (Setting<String> setting : List.of(UI_THEME_DARK, UI_THEME_LIGHT, TERMINAL_SCHEME)) {
            Optional<String> saved = draft.saved(setting);
            if (saved.map(value -> names(setting, value, theme)).orElse(false)) {
                changes.put(setting, Optional.empty());
                expected.put(setting, saved);
            }
            if (names(setting, draft.value(setting), theme)) draft.stage(setting, Optional.empty());
        }
        context.changed().run();
        boolean saving = !changes.isEmpty();
        var written = saving ? hooks.save().apply(new SettingsEdit(changes, expected)) : hooks.reload().get();
        written.whenComplete((ignored, failure) -> hooks.ui().execute(() -> {
            if (failure == null) { done(); return; }
            var notice = new ThemeImports.Notice("Removed " + label, (saving ? "Its settings were not saved: " : "Settings could not read the themes folder again: ")
                + reason(cause(failure)));
            if (!saving) { failed(notice); return; }
            // A save that fails reads nothing back, so the application's library would still list the removed theme.
            CompletableFuture<?> reread;
            try { reread = hooks.reload().get(); } catch (RuntimeException again) { reread = CompletableFuture.failedFuture(again); }
            reread.whenComplete((value, problem) -> hooks.ui().execute(() -> failed(notice)));
        }));
    }

    private static Throwable cause(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    /** Why a file or settings operation failed, in words a notice shows, never the bare path. */
    private static String reason(Throwable failure) {
        if (failure instanceof AccessDeniedException) return "permission denied.";
        if (failure instanceof NoSuchFileException) return "the file is no longer there.";
        if (failure instanceof FileSystemException system)
            return (system.getReason() == null ? system.getClass().getSimpleName() : system.getReason()) + ".";
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() + "." : message;
    }

    private static boolean names(Setting<String> setting, String value, String theme) {
        return setting == TERMINAL_SCHEME ? value.startsWith(theme + "/") : value.equals(theme);
    }

    private void working() { busy = true; render.run(); }

    private void done() { busy = false; render.run(); }

    private void failed(String title, String message) { failed(new ThemeImports.Notice(title, message)); }

    private void failed(ThemeImports.Notice notice) {
        done();
        hooks.inform().accept(context.surface().get(), notice);
    }
}
