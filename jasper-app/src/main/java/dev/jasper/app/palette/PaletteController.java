package dev.jasper.app.palette;

import dev.jasper.app.config.PaletteSettings;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * EDT-owned palette state: the open tab (All or one scope), the query, a step and queued completions.
 * All searches every scope that takes part in it, a section each, capped at the result limit. Host
 * callbacks contain all workspace integration.
 */
public final class PaletteController implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PaletteController.class.getName());
    private final ScopeRegistry scopes;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Runnable layout, dismissed, updateActions;
    private final Function<String, String> shortcut;
    private final BooleanSupplier batching;
    private final Consumer<String> reportError, reopen;
    private final Subscription scopesListener;
    private final List<Subscription> tabListeners = new ArrayList<>();
    /** The open tab, {@link PaletteScope#ALL_ID} or a scope id; null while closed. */
    private String tab;
    /** The open tab's scope; null for All. */
    private PaletteScope active;
    private PaletteStep step;
    private PaletteScope stepScope;
    private PaletteContext context;
    private BooleanSupplier originValid = () -> false;
    private boolean open, closed, completing, dirty = true, rosterChanged;
    private long generation;
    private int maxResults = PaletteSettings.DEFAULT_MAX_RESULTS;

    public PaletteController(ScopeRegistry scopes, boolean macOs, Runnable layout, Runnable dismissed,
                      Function<String, String> shortcut, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this.scopes = scopes; this.macOs = macOs; this.layout = layout; this.dismissed = dismissed;
        this.shortcut = shortcut; this.batching = batching; this.updateActions = updateActions;
        this.reportError = reportError; this.reopen = reopen;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::selectTab);
        scopesListener = scopes.onChanged(this::rosterChanged);
    }

    /** Whether {@code id} is a tab: All, or a registered scope. */
    public boolean hasScope(String id) { return PaletteScope.ALL_ID.equals(id) || scopes.find(id).isPresent(); }

    /** The registered scope whose shortcut action is {@code actionId}. */
    public Optional<String> scopeForShortcutAction(String actionId) {
        return scopes.byShortcutAction(actionId).map(PaletteScope::id);
    }

    /** Captures the origin only on first open; changing tabs retains that captured target. */
    public boolean open(String tabId, PaletteTarget target, BooleanSupplier valid) { return open(tabId, target, valid, null, null); }

    /**
     * As {@link #open(String, PaletteTarget, BooleanSupplier)}; afterwards {@code queryOrNull} replaces the
     * query text and {@code rowIdOrNull} selects a row of the resulting list. Opening the tab that is
     * already showing dismisses instead, and then neither is applied.
     */
    public boolean open(String tabId, PaletteTarget target, BooleanSupplier valid, String queryOrNull, String rowIdOrNull) {
        if (closed || !hasScope(tabId)) return false;
        if (open) {
            if (tabId.equals(tab)) { dismiss(); return false; }
            activate(tabId);
        } else {
            generation++;
            originValid = valid;
            context = new PaletteContext(macOs, target, maxResults);
            palette.queryField().setText("");
            open = true;
            palette.setVisible(true);
            activate(tabId);
        }
        if (queryOrNull != null) palette.queryField().setText(queryOrNull);
        if (rowIdOrNull != null) palette.selectRow(rowIdOrNull);
        return true;
    }

    /** Selects the tab {@code id}, keeping the query; the open tab again does nothing. */
    public void selectTab(String id) {
        if (!open || id.equals(tab) || !hasScope(id)) return;
        activate(id);
    }

    /** Selects the next ({@code delta} 1) or previous ({@code -1}) tab, wrapping, and keeps the query. */
    public void cycleTab(int delta) {
        if (!open) return;
        var ids = new ArrayList<String>();
        ids.add(PaletteScope.ALL_ID);
        scopes.scopes().forEach(scope -> ids.add(scope.id()));
        int index = Math.max(0, ids.indexOf(tab));
        activate(ids.get(Math.floorMod(index + delta, ids.size())));
    }

    private void activate(String tabId) {
        generation++;
        if (step != null) { step = null; stepScope = null; palette.hideStep(); }
        completing = false;
        rosterChanged = false;
        tabListeners.forEach(Subscription::close);
        tabListeners.clear();
        tab = tabId;
        active = PaletteScope.ALL_ID.equals(tabId) ? null : scopes.find(tabId).orElseThrow();
        for (PaletteScope scope : tabScopes()) {
            tabListeners.add(scope.onChanged(this::changed));
            scope.activated(context);
        }
        showTabs();
        rebuild(false);
    }

    /** The scopes the open tab searches: its own scope, or every scope that takes part in All. */
    private List<PaletteScope> tabScopes() {
        if (active != null) return List.of(active);
        return scopes.scopes().stream().filter(PaletteScope::inAll).toList();
    }

    private void showTabs() {
        var tabs = new ArrayList<CommandPalette.Tab>();
        tabs.add(new CommandPalette.Tab(PaletteScope.ALL_ID, "All", null, tooltip("Search everywhere", PaletteScope.ALL_ID)));
        for (PaletteScope scope : scopes.scopes())
            tabs.add(new CommandPalette.Tab(scope.id(), scope.label(), scope.icon(), tooltip(scope.description(), scope.id())));
        palette.setTabs(tabs, tab);
        palette.setPlaceholder(active == null ? "Search everywhere" : active.placeholder());
    }

    private String tooltip(String description, String id) {
        String keys = shortcut.apply(id);
        if (keys == null || keys.isBlank()) return description.isBlank() ? null : description;
        return description.isBlank() ? keys : description + " (" + keys + ")";
    }

    /** The hard cap every scope returns; a live change re-runs the open query under the new cap. */
    public void setMaxResults(int value) {
        if (value == maxResults) return;
        maxResults = value;
        context = new PaletteContext(macOs, context.target(), maxResults);
        if (open) rebuild(true);
    }

    public int maxResults() { return maxResults; }
    public void dismiss() { if (open) restoreAndHide(); }
    public boolean isOpen() { return open; }
    /** The open tab: {@link PaletteScope#ALL_ID} or a scope id; null while closed. */
    public String activeScopeId() { return tab; }
    public boolean composing() { return palette.composing(); }
    public CommandPalette component() { return palette; }

    /** Enter and its modifier variants: completes an open step, otherwise acts on the selected entry. */
    public void enterPressed(int verb) {
        if (!open) return;
        if (step != null) { if (!completing) completeStep(); } else palette.executeSelected(verb);
    }

    public void moveSelection(int delta) { if (open && step == null) palette.selectRelative(delta); }
    public boolean stepOpen() { return open && step != null; }

    /** Escape leaves a step, and otherwise dismisses. */
    public void escape() {
        if (step != null) { closeStep(); return; }
        dismiss();
    }

    public boolean tabPressed() { return tabPressed(false); }

    /** Tab moves between step fields, and otherwise to the next tab; Shift+Tab goes back. */
    public boolean tabPressed(boolean backwards) {
        if (!open) return false;
        if (step != null) { palette.focusStepField(backwards ? -1 : 1); return true; }
        cycleTab(backwards ? -1 : 1);
        return true;
    }

    private void showStep(PaletteStep pending, PaletteScope owner) {
        step = pending;
        stepScope = owner;
        palette.showStep(pending.title(), pending.fields());
        layout.run();
    }

    private void closeStep() {
        generation++;
        step = null;
        stepScope = null;
        completing = false;
        palette.hideStep();
        rebuild(true);
        palette.queryField().requestFocusInWindow();
    }

    private void completeStep() {
        PaletteStep current = step;
        long submitted = ++generation;
        completing = true;
        palette.setStepError(null);
        try {
            current.complete().accept(palette.stepValues(), result -> {
                Runnable apply = () -> {
                    if (!acceptsCompletion(submitted, current)) return;
                    completing = false;
                    if (result.error() != null) { palette.setStepError(result.error()); layout.run(); return; }
                    step = null;
                    stepScope = null;
                    palette.hideStep();
                    restoreAndHide();
                    if (result.reopenScopeId() != null) {
                        reopen.accept(result.reopenScopeId());
                        if (open && result.reopenQuery() != null) palette.queryField().setText(result.reopenQuery());
                        if (open && result.reopenRowId() != null) palette.selectRow(result.reopenRowId());
                    }
                };
                if (SwingUtilities.isEventDispatchThread()) apply.run();
                else SwingUtilities.invokeLater(apply);
            });
        } catch (RuntimeException failure) {
            if (!acceptsCompletion(submitted, current)) return;
            completing = false;
            LOG.log(System.Logger.Level.ERROR, "Palette step completion failed", failure);
            palette.setStepError("Could not complete: " + failure.getMessage());
        }
    }

    private boolean acceptsCompletion(long submitted, PaletteStep current) {
        return !closed && open && generation == submitted && step == current
            && originValid.getAsBoolean() && scopes.contains(stepScope);
    }

    private void queryChanged(String query) {
        if (open) rebuild(false);
    }

    private void changed() {
        dirty = true;
        if (!batching.getAsBoolean()) refreshIfChanged();
    }

    private void rosterChanged() {
        rosterChanged = true;
        changed();
    }

    public void refreshIfChanged() {
        if (!open) return;
        if (!valid()) { dismiss(); return; }
        if (rosterChanged) applyRoster();
        if (dirty) rebuild(true);
    }

    public void refresh() {
        if (!open) return;
        if (!valid()) { dismiss(); return; }
        if (rosterChanged) applyRoster();
        rebuild(true);
    }

    private boolean valid() { return originValid.getAsBoolean() && (active == null || scopes.contains(active)); }

    /** A scope came or went while open: show the new tabs and, on All, listen to the new roster. */
    private void applyRoster() {
        rosterChanged = false;
        if (active == null) {
            tabListeners.forEach(Subscription::close);
            tabListeners.clear();
            for (PaletteScope scope : tabScopes()) tabListeners.add(scope.onChanged(this::changed));
        }
        showTabs();
    }

    public void refreshTheme() { palette.refreshTheme(); if (open) layout.run(); }

    private void rebuild(boolean preserve) {
        if (!open || tab == null) return;
        if (step != null) { dirty = true; return; }
        dirty = false;
        String keep = preserve ? palette.selectedKey() : null;
        String query = palette.queryField().getText();
        var entries = new ArrayList<PaletteEntry>();
        String initial = null;
        if (active != null) {
            PaletteResults results = active.search(query, context);
            if (results.sectionLabel() != null && !results.rows().isEmpty()) entries.add(new PaletteEntry.Header(results.sectionLabel()));
            for (PaletteRow row : results.rows()) entries.add(new PaletteEntry.Item(active, row));
            if (results.initialSelectionId() != null) initial = PaletteEntry.key(active, results.initialSelectionId());
        } else {
            // One row more than shown tells whether a scope has more matches than All has room for.
            var probe = new PaletteContext(macOs, context.target(), maxResults + 1);
            for (PaletteScope scope : tabScopes()) {
                PaletteResults results;
                try { results = scope.search(query, probe); }
                catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.ERROR, "Palette scope failed to search: " + scope.id(), failure);
                    continue;
                }
                if (results.rows().isEmpty()) continue;
                entries.add(new PaletteEntry.Header(scope.label()));
                List<PaletteRow> shown = results.rows().subList(0, Math.min(maxResults, results.rows().size()));
                for (PaletteRow row : shown) entries.add(new PaletteEntry.Item(scope, row));
                if (results.rows().size() > maxResults) entries.add(new PaletteEntry.More(scope));
                String first = results.initialSelectionId();
                if (initial == null && first != null && shown.stream().anyMatch(row -> row.id().equals(first)))
                    initial = PaletteEntry.key(scope, first);
            }
        }
        palette.setEntries(entries, keep != null ? keep : initial,
            active == null ? "Nothing found" : "No matching " + active.label().toLowerCase(Locale.ROOT));
        layout.run();
    }

    private void execute(PaletteEntry entry, int verbIndex) {
        if (!open) return;
        if (entry instanceof PaletteEntry.More more) { selectTab(more.scope().id()); return; }
        if (!(entry instanceof PaletteEntry.Item item)) return;
        PaletteScope scope = item.scope();
        PaletteRow row = item.row();
        if (!valid() || !scopes.contains(scope)) { dismiss(); return; }
        if (verbIndex < 0 || verbIndex >= scope.verbs().size()) return;
        PaletteVerb verb = scope.verbs().get(verbIndex);
        updateActions.run();
        if (!valid() || !scopes.contains(scope) || !scope.available(row, verb, context)) { refresh(); return; }
        PaletteStep pending = scope.step(row, verb, context);
        if (pending != null) { showStep(pending, scope); return; }
        PaletteContext target = context;
        restoreAndHide();
        try {
            scope.execute(row, verb, target);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR, "Palette action failed: " + scope.id() + " " + row.id(), failure);
            reportError.accept("Could not run " + row.title() + ". See the application log for details.");
        }
    }

    private void restoreAndHide() {
        generation++;
        open = false; step = null; stepScope = null; completing = false;
        palette.hideStep(); palette.setVisible(false);
        tabListeners.forEach(Subscription::close);
        tabListeners.clear();
        tab = null;
        active = null;
        context = new PaletteContext(macOs, PaletteTarget.none(), maxResults);
        originValid = () -> false;
        dismissed.run();
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; generation++;
        scopesListener.close();
    }
}
