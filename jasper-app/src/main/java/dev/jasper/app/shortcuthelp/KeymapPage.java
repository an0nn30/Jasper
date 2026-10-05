package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The Keymap page of Settings: the shortcut editor. Set stages an edit; Apply writes every staged edit in one save.
 * SettingsDialog owns each page and disposes it, which closes its subscription and key recorder. EDT only.
 */
public final class KeymapPage implements SettingsPage {
    /** The page id. */
    public static final String ID = "keymap";

    /**
     * What the page reads and writes.
     *
     * @param contributions the contributed actions; their changes refresh the rows
     * @param overrides the saved {@code [keybindings]} overrides
     * @param pluginNames plugin ids to display names, for grouping plugin actions
     * @param macOs the platform the bindings are parsed for
     * @param save writes shortcut edits (action id to a binding, or empty for the default); completes on any thread
     */
    public record Hooks(Contributions contributions, Supplier<Map<String, String>> overrides,
                        Supplier<Map<String, String>> pluginNames, boolean macOs,
                        Function<Map<String, Optional<String>>, CompletableFuture<Void>> save) {
        public Hooks {
            Objects.requireNonNull(contributions); Objects.requireNonNull(overrides);
            Objects.requireNonNull(pluginNames); Objects.requireNonNull(save);
        }
    }

    private final Hooks hooks;
    private final PageContext context;
    private final KeymapDraft draft = new KeymapDraft();
    private final Subscription listening;
    private ShortcutPanel panel;
    private FillingPage body;
    private String highlighted;

    public KeymapPage(Hooks hooks, PageContext context) {
        this.hooks = Objects.requireNonNull(hooks);
        this.context = Objects.requireNonNull(context);
        listening = hooks.contributions().onChanged(kind -> { if (kind == Contributions.Kind.ACTIONS) refreshRows(); });
    }

    ShortcutPanel panel() { return panel; }

    @Override public String id() { return ID; }
    @Override public String title() { return "Keymap"; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public String helpAnchor() { return "shortcuts"; }

    /** The page title, every group name, and each action's name with its binding. */
    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>(List.of("Keymap", "keyboard shortcuts keys bindings"));
        var groups = new LinkedHashSet<String>();
        for (ShortcutEntry row : rows()) {
            groups.add(row.group());
            terms.add(row.name() + " " + ShortcutText.format(row.stroke()));
        }
        terms.addAll(groups);
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        panel = new ShortcutPanel(context.kit().get(), hooks.macOs(), this::stage);
        panel.setRows(rows());
        body = new FillingPage(panel);
        return body;
    }

    @Override public boolean modified() { return draft.modified(); }

    @Override public void reset() {
        draft.clear();
        refreshRows();
        context.changed().run();
    }

    /** Writes every staged edit at once; edits staged after this call stay staged. */
    @Override public CompletableFuture<Void> apply() {
        if (!draft.modified()) return CompletableFuture.completedFuture(null);
        var edits = draft.edits();
        var done = new CompletableFuture<Void>();
        CompletableFuture<Void> saving;
        try { saving = hooks.save().apply(edits); }
        catch (RuntimeException failure) { saving = CompletableFuture.failedFuture(failure); }
        saving.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null) { done.completeExceptionally(failure); return; }
            draft.saved(edits);
            refreshRows();
            done.complete(null);
        }));
        return done;
    }

    /** Puts a Settings search into the page's own search field, and takes it out again when Settings' search clears. */
    @Override public void highlight(String query) {
        if (panel == null) return;
        String text = query == null ? "" : query.strip();
        if (!text.isEmpty()) {
            panel.setQuery(text);
            highlighted = text;
        } else if (highlighted != null && panel.query().equals(highlighted)) {
            panel.setQuery("");
            highlighted = null;
        }
    }

    /** The saved bindings changed: staged edits they now hold are dropped and the rows are rebuilt. */
    @Override public void refresh() {
        draft.prune(hooks.overrides().get());
        refreshRows();
        context.changed().run();
    }

    @Override public void dispose() {
        listening.close();
        if (panel != null) panel.close();
    }

    /** The edit card's Set: refuses a staging that would collide, otherwise stages it and shows it. */
    private CompletableFuture<Void> stage(Map<String, Optional<String>> changes) {
        var saved = hooks.overrides().get();
        try { KeyBindings.withOverrides(hooks.macOs(), draft.preview(saved, changes)); }
        catch (IllegalArgumentException collision) { return CompletableFuture.failedFuture(collision); }
        draft.stage(changes, saved);
        refreshRows();
        context.changed().run();
        return CompletableFuture.completedFuture(null);
    }

    /** The catalog over the saved overrides plus staged edits; the saved ones alone if a later reload made those collide. */
    private List<ShortcutEntry> rows() {
        var saved = hooks.overrides().get();
        KeyBindings base;
        try { base = KeyBindings.withOverrides(hooks.macOs(), draft.overrides(saved)); }
        catch (IllegalArgumentException collision) { base = KeyBindings.withOverrides(hooks.macOs(), saved); }
        var panels = new java.util.LinkedHashMap<String, String>();
        for (var entry : hooks.contributions().panels()) panels.put(entry.id(), entry.title());
        return ShortcutCatalog.rows(base, hooks.contributions().actions(), hooks.pluginNames().get(), panels, hooks.macOs());
    }

    private void refreshRows() { if (panel != null) panel.setRows(rows()); }
}
