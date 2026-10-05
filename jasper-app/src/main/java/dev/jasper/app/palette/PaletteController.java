package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * EDT-owned palette state: the chip (none, or one scope), the query, a step and queued completions. With
 * no chip an empty query lists the scopes (the mode list) and any other query searches every scope that
 * takes part in All, a section each; the list scrolls through every row. Host callbacks contain all workspace
 * integration.
 */
public final class PaletteController implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PaletteController.class.getName());
    /** Rows the empty query offers: this many recently used, then ranked ones up to the total. */
    static final int RECENT_ROWS = 2, SUGGESTED_ROWS = 10;
    private final ScopeRegistry scopes;
    private final dev.jasper.app.commands.CommandHistory history;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Runnable layout, dismissed, updateActions;
    private final BooleanSupplier batching;
    private final Consumer<String> reportError, reopen;
    private final Subscription scopesListener;
    private final List<Subscription> tabListeners = new ArrayList<>();
    /** The chip: {@link PaletteScope#ALL_ID} for none, or a scope id; null while closed. */
    private String tab;
    /** The chip's scope; null for none. */
    private PaletteScope active;
    private PaletteStep step;
    private PaletteScope stepScope;
    private PaletteContext context;
    private BooleanSupplier originValid = () -> false;
    private boolean open, closed, completing, dirty = true, rosterChanged;
    private long generation;

    public PaletteController(dev.jasper.ui.swing.KitContext kit, ScopeRegistry scopes, dev.jasper.app.commands.CommandHistory history, boolean macOs,
                      Runnable layout, Runnable dismissed, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this.scopes = scopes; this.history = java.util.Objects.requireNonNull(history); this.macOs = macOs; this.layout = layout; this.dismissed = dismissed;
        this.batching = batching; this.updateActions = updateActions;
        this.reportError = reportError; this.reopen = reopen;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(kit, macOs, this::queryChanged, this::execute, this::prefix, scope -> selectTab(scope.id()));
        scopesListener = scopes.onChanged(this::rosterChanged);
    }

    /** Whether {@code id} can be opened: no chip (All), or a registered scope. */
    public boolean hasScope(String id) { return PaletteScope.ALL_ID.equals(id) || scopes.find(id).isPresent(); }

    /** The registered scope whose shortcut action is {@code actionId}. */
    public Optional<String> scopeForShortcutAction(String actionId) {
        return scopes.byShortcutAction(actionId).map(PaletteScope::id);
    }

    /** Captures the origin only on first open; changing chips retains that captured target. */
    public boolean open(String tabId, PaletteTarget target, BooleanSupplier valid) { return open(tabId, target, valid, null, null); }

    /**
     * As {@link #open(String, PaletteTarget, BooleanSupplier)}; afterwards {@code queryOrNull} replaces the
     * query text and {@code rowIdOrNull} selects a row of the resulting list. Opening the chip that is
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
            context = new PaletteContext(macOs, target);
            palette.setQueryText("");
            open = true;
            palette.setVisible(true);
            activate(tabId);
        }
        if (queryOrNull != null) palette.setQueryText(queryOrNull);
        if (rowIdOrNull != null) palette.selectRow(rowIdOrNull);
        return true;
    }

    /** Sets the chip {@code id}, keeping the query; the current chip again does nothing. */
    public void selectTab(String id) {
        if (!open || id.equals(tab) || !hasScope(id)) return;
        activate(id);
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
        showChip();
        rebuild(false);
    }

    /** The scopes the open tab searches: its own scope, or every scope that takes part in All. */
    private List<PaletteScope> tabScopes() {
        if (active != null) return List.of(active);
        return scopes.scopes().stream().filter(PaletteScope::inAll).toList();
    }

    private void showChip() {
        palette.setChip(active == null ? null : active.label(), active == null ? null : active.icon());
        palette.setPlaceholder(active == null ? "Search everything" : active.placeholder());
    }

    /** How many result rows the list shows before it scrolls; a live change resizes an open palette. */
    public void setVisibleRows(int value) {
        if (value == palette.visibleRows()) return;
        palette.setVisibleRows(value);
        if (open) layout.run();
    }

    public int visibleRows() { return palette.visibleRows(); }
    public void dismiss() { if (open) restoreAndHide(); }
    public boolean isOpen() { return open; }
    /** The chip: {@link PaletteScope#ALL_ID} for none, or a scope id; null while closed. */
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

    /** Tab moves between step fields; elsewhere the palette claims it and does nothing. */
    public boolean tabPressed(boolean backwards) {
        if (!open) return false;
        if (step != null) palette.focusStepField(backwards ? -1 : 1);
        return true;
    }

    /** Backspace in an empty query removes the chip; false leaves the key to the field. */
    public boolean backspacePressed() {
        if (!open || step != null || active == null || !palette.queryText().isEmpty()) return false;
        activate(PaletteScope.ALL_ID);
        return true;
    }

    /** A typed prefix becomes a chip only while no chip or step is showing. */
    private Optional<ScopePrefix> prefix(String text) {
        if (!open || active != null || step != null) return Optional.empty();
        return ScopePrefix.parse(text, scopes.scopes());
    }

    /** Commands first, then every other scope in registry order. */
    private List<PaletteScope> modeScopes() {
        var ordered = new ArrayList<PaletteScope>();
        scopes.find(PaletteScope.COMMANDS_ID).ifPresent(ordered::add);
        for (PaletteScope scope : scopes.scopes()) if (!PaletteScope.COMMANDS_ID.equals(scope.id())) ordered.add(scope);
        return ordered;
    }

    /**
     * The empty query's rows: the last {@link #RECENT_ROWS} rows run, then rows ranked by use (frecency), topped up with
     * each scope's own empty-query rows. Only scopes that allow it ({@link PaletteScope#inRecents()}) appear; a row that
     * its scope no longer finds drops out.
     */
    private void suggestions(List<PaletteEntry> entries) {
        long now = System.currentTimeMillis();
        var seen = new java.util.HashSet<String>();
        var recent = new ArrayList<PaletteEntry.Item>();
        var uses = history.uses();
        for (int i = 0; i < uses.size() && i < 20 && recent.size() < RECENT_ROWS; i++)
            resolve(uses.get(i)).filter(item -> seen.add(item.key())).ifPresent(recent::add);
        var ranked = new ArrayList<PaletteEntry.Item>();
        var byScore = new ArrayList<>(uses);
        byScore.sort(java.util.Comparator.comparingDouble((dev.jasper.app.commands.PaletteUse use) -> use.score(now)).reversed());
        for (int i = 0; i < byScore.size() && i < 30 && ranked.size() < SUGGESTED_ROWS - recent.size(); i++)
            resolve(byScore.get(i)).filter(item -> seen.add(item.key())).ifPresent(ranked::add);
        boolean used = !ranked.isEmpty();
        for (PaletteScope scope : modeScopes()) {
            if (recent.size() + ranked.size() >= SUGGESTED_ROWS) break;
            if (!scope.inRecents()) continue;
            PaletteResults results;
            try { results = scope.search("", context); }
            catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Palette scope failed to search: " + scope.id(), failure);
                continue;
            }
            for (PaletteRow row : results.rows()) {
                if (recent.size() + ranked.size() >= SUGGESTED_ROWS) break;
                var item = new PaletteEntry.Item(scope, row);
                if (seen.add(item.key())) ranked.add(item);
            }
        }
        if (!recent.isEmpty()) { entries.add(new PaletteEntry.Header("Recently used")); entries.addAll(recent); }
        if (!ranked.isEmpty()) { entries.add(new PaletteEntry.Header(used ? "Most used" : "Suggested")); entries.addAll(ranked); }
    }

    /** The row a use names, found by searching its title in its scope; empty when the scope or row is gone. */
    private Optional<PaletteEntry.Item> resolve(dev.jasper.app.commands.PaletteUse use) {
        Optional<PaletteScope> scope = scopes.find(use.scopeId()).filter(PaletteScope::inRecents);
        if (scope.isEmpty()) return Optional.empty();
        PaletteResults results;
        try { results = scope.get().search(use.title(), new PaletteContext(macOs, context.target(), 50)); }
        catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR, "Palette scope failed to search: " + use.scopeId(), failure);
            return Optional.empty();
        }
        return results.rows().stream().filter(row -> row.id().equals(use.rowId())).findFirst()
            .map(row -> new PaletteEntry.Item(scope.get(), row));
    }

    static String prefixText(PaletteScope scope) {
        return PaletteScope.COMMANDS_ID.equals(scope.id()) ? ">" : scope.label().toLowerCase(Locale.ROOT) + " >";
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
        palette.focusQuery();
    }

    private void completeStep() {
        if (!valid()) { dismiss(); return; }
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
                        if (open && result.reopenQuery() != null) palette.setQueryText(result.reopenQuery());
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

    private boolean valid() {
        return originValid.getAsBoolean() && (active == null || scopes.contains(active))
            && (stepScope == null || scopes.contains(stepScope));
    }

    /** A scope came or went while open: show the chip and, with none, listen to the new roster. */
    private void applyRoster() {
        rosterChanged = false;
        if (active == null) {
            tabListeners.forEach(Subscription::close);
            tabListeners.clear();
            for (PaletteScope scope : tabScopes()) tabListeners.add(scope.onChanged(this::changed));
        }
        showChip();
    }

    public void refreshTheme() { palette.refreshTheme(); if (open) layout.run(); }

    private void rebuild(boolean preserve) {
        if (!open || tab == null) return;
        if (step != null) { dirty = true; return; }
        dirty = false;
        String keep = preserve ? palette.selectedKey() : null;
        String query = palette.queryText();
        var entries = new ArrayList<PaletteEntry>();
        String initial = null;
        if (active != null) {
            PaletteResults results = active.search(query, context);
            if (results.sectionLabel() != null && !results.rows().isEmpty()) entries.add(new PaletteEntry.Header(results.sectionLabel()));
            for (PaletteRow row : results.rows()) entries.add(new PaletteEntry.Item(active, row));
            if (results.initialSelectionId() != null) initial = PaletteEntry.key(active, results.initialSelectionId());
        } else if (query.isBlank()) {
            suggestions(entries);
        } else {
            // Every match goes into the list, which scrolls, but the list holds at most 200 rows overall
            // (PaletteResults.MAX_ROWS). Budget rows across sections in tab order; a scope cut short by the
            // budget gets a "More in..." row, and a scope reached after the budget is spent gets no section.
            int remaining = PaletteResults.MAX_ROWS;
            for (PaletteScope scope : tabScopes()) {
                if (remaining <= 0) break;
                // One row more than the budget left tells whether the budget cut this scope short.
                var probe = new PaletteContext(macOs, context.target(), Math.min(remaining + 1, PaletteResults.MAX_ROWS));
                PaletteResults results;
                try { results = scope.search(query, probe); }
                catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.ERROR, "Palette scope failed to search: " + scope.id(), failure);
                    continue;
                }
                if (results.rows().isEmpty()) continue;
                List<PaletteRow> shown = results.rows().subList(0, Math.min(remaining, results.rows().size()));
                entries.add(new PaletteEntry.Header(scope.label()));
                for (PaletteRow row : shown) entries.add(new PaletteEntry.Item(scope, row));
                if (results.rows().size() > shown.size()) entries.add(new PaletteEntry.More(scope));
                remaining -= shown.size();
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
        if (scope.inRecents()) history.recordUse(scope.id(), row.id(), row.title(), System.currentTimeMillis());
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
        context = new PaletteContext(macOs, PaletteTarget.none());
        originValid = () -> false;
        dismissed.run();
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; generation++;
        scopesListener.close();
        palette.close();
    }
}
