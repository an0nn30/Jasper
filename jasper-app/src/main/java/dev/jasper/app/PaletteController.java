package dev.jasper.app;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.config.HistorySettings;
import dev.jasper.app.config.PaletteSettings;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/** EDT-owned palette interaction state; host callbacks contain all workspace integration. */
final class PaletteController implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PaletteController.class.getName());
    private final ScopeRegistry scopes;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Runnable layout, dismissed, updateActions;
    private final Function<String, String> shortcut;
    private final BooleanSupplier batching;
    private final Consumer<String> reportError, reopen;
    private final Subscription scopesListener;
    private Subscription scopeListener;
    private PaletteScope active;
    private PaletteStep step;
    private PaletteContext context;
    private BooleanSupplier originValid = () -> false;
    private boolean picker, open, closed, completing, dirty = true;
    private long generation;
    private int maxResults = PaletteSettings.DEFAULT_MAX_RESULTS;
    private java.util.List<String> trivialCommands = HistorySettings.defaults().trivialCommands();

    PaletteController(ScopeRegistry scopes, boolean macOs, Runnable layout, Runnable dismissed,
                      Function<String, String> shortcut, BooleanSupplier batching, Runnable updateActions,
                      Consumer<String> reportError, Consumer<String> reopen) {
        this.scopes = scopes; this.macOs = macOs; this.layout = layout; this.dismissed = dismissed;
        this.shortcut = shortcut; this.batching = batching; this.updateActions = updateActions;
        this.reportError = reportError; this.reopen = reopen;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::escape, this::openPicker);
        scopesListener = scopes.onChanged(this::changed);
    }

    boolean hasScope(String id) { return scopes.find(id).isPresent(); }

    /** Captures the origin only on first open; changing scopes retains that captured target. */
    boolean open(String scopeId, PaletteTarget target, BooleanSupplier valid) {
        if (closed) return false;
        PaletteScope scope = scopes.find(scopeId).orElse(null);
        if (scope == null) return false;
        if (open) {
            if (scope == active) { dismiss(); return false; }
            activate(scope, !picker); return true;
        }
        generation++;
        originValid = valid;
        context = new PaletteContext(macOs, target, maxResults, trivialCommands);
        open = true; palette.setVisible(true);
        activate(scope, false);
        return true;
    }

    private void activate(PaletteScope scope, boolean keepQuery) {
        generation++;
        if (step != null) { step = null; palette.hideStep(); }
        completing = false;
        if (scopeListener != null) scopeListener.close();
        active = scope; picker = false;
        scopeListener = scope.onChanged(this::changed);
        palette.setScope(scope.label(), scope.icon(), scope.placeholder(), scope.verbs(), maxResults, scope.monospaceRows());
        if (!keepQuery) palette.queryField().setText("");
        scope.activated(context);
        rebuild(false);
    }

    /** The hard cap every scope returns; a live change re-runs the open query under the new cap. */
    void setMaxResults(int value) {
        if (value == maxResults) return;
        maxResults = value;
        context = new PaletteContext(macOs, context.target(), maxResults, trivialCommands);
        if (open && active != null) {
            palette.setScope(active.label(), active.icon(), active.placeholder(), active.verbs(), maxResults, active.monospaceRows());
            rebuild(true);
        }
    }

    int maxResults() { return maxResults; }

    /** Live: the next query uses the new list, and an open palette re-runs its search. */
    void setTrivialCommands(java.util.List<String> value) {
        if (value.equals(trivialCommands)) return;
        trivialCommands = java.util.List.copyOf(value);
        context = new PaletteContext(macOs, context.target(), maxResults, trivialCommands);
        changed();
    }

    java.util.List<String> trivialCommands() { return trivialCommands; }

    void dismiss() { if (open) restoreAndHide(); }
    void openPicker() { if (open && step == null && !picker) palette.queryField().setText(">"); }
    boolean isOpen() { return open; }
    boolean pickerOpen() { return open && picker; }
    String activeScopeId() { return active == null ? null : active.id(); }
    boolean composing() { return palette.composing(); }
    CommandPalette component() { return palette; }

    /** Enter and its modifier variants: completes an open step, otherwise runs that verb on the selected row. */
    void enterPressed(int verb) {
        if (!open) return;
        if (step != null) { if (!completing) completeStep(); } else palette.executeSelected(verb);
    }

    void executeNumber(int number) { if (open && step == null) palette.executeNumber(number); }
    void moveSelection(int delta) { if (open && step == null) palette.selectRelative(delta); }
    boolean stepOpen() { return open && step != null; }

    /** Escape leaves a step, then the picker, and otherwise dismisses. */
    void escape() {
        if (step != null) { closeStep(); return; }
        if (pickerOpen()) palette.queryField().setText(""); else dismiss();
    }

    boolean tabPressed() { return tabPressed(false); }

    /** Tab moves between step fields, or commits the picker's highlighted scope; elsewhere it has no meaning. */
    boolean tabPressed(boolean backwards) {
        if (step != null) { palette.focusStepField(backwards ? -1 : 1); return true; }
        if (!pickerOpen()) return false;
        PaletteRow row = palette.resultList().getSelectedValue();
        if (row != null) scopes.find(row.id()).ifPresent(scope -> activate(scope, false));
        return true;
    }

    private void showStep(PaletteStep pending) {
        step = pending;
        palette.showStep(pending.title(), pending.fields());
        layout.run();
    }

    private void closeStep() {
        generation++;
        step = null;
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
            && originValid.getAsBoolean() && scopes.contains(active);
    }

    private void queryChanged(String query) {
        if (!open) return;
        // Stateless by design: JTextField.setText replaces its whole value as a remove
        // followed by an insert, so a transition-based (was picker, is query now ">")
        // check sees a transient empty string in between and can never recover. Deriving
        // picker fresh from the current text each call is immune to that split.
        picker = query.startsWith(">");
        rebuild(false);
    }

    private void changed() {
        dirty = true;
        if (!batching.getAsBoolean()) refreshIfChanged();
    }

    void refreshIfChanged() {
        if (!open) return;
        if (!originValid.getAsBoolean() || !scopes.contains(active)) { dismiss(); return; }
        if (dirty) refresh();
    }

    void refresh() {
        if (!open) return;
        if (!originValid.getAsBoolean() || !scopes.contains(active)) { dismiss(); return; }
        rebuild(true);
    }

    void refreshTheme() { palette.refreshTheme(); if (open) layout.run(); }

    private void rebuild(boolean preserve) {
        if (!open || active == null) return;
        if (step != null) { dirty = true; return; }
        dirty = false;
        PaletteRow selected = palette.resultList().getSelectedValue();
        String query = palette.queryField().getText();
        PaletteResults results = picker ? pickerResults(query.substring(1)) : active.search(query, context);
        String keep = preserve && selected != null ? selected.id() : results.initialSelectionId();
        palette.setResults(results.rows(), picker ? "Scopes" : results.sectionLabel(), keep);
        layout.run();
    }

    private PaletteResults pickerResults(String filter) {
        String q = CommandSearch.normalize(filter);
        var rows = new ArrayList<PaletteRow>();
        for (PaletteScope scope : scopes.scopes()) {
            if (!q.isEmpty() && !matchesScope(scope, q)) continue;
            rows.add(new PaletteRow(scope.id(), scope.label(), scope.description(), shortcut.apply(scope.id()),
                scope.icon(), true, scope));
        }
        return new PaletteResults(rows, "Scopes", null);
    }

    static boolean matchesScope(PaletteScope scope, String q) {
        String label = CommandSearch.normalize(scope.label());
        if (label.contains(q)) return true;
        for (String alias : scope.aliases()) if (CommandSearch.normalize(alias).startsWith(q)) return true;
        return false;
    }

    private void execute(PaletteRow row, int verbIndex) {
        if (!open) return;
        if (picker) { scopes.find(row.id()).ifPresent(scope -> activate(scope, false)); return; }
        PaletteScope scope = active;
        if (!originValid.getAsBoolean() || !scopes.contains(scope)) { dismiss(); return; }
        if (verbIndex < 0 || verbIndex >= scope.verbs().size()) return;
        PaletteVerb verb = scope.verbs().get(verbIndex);
        updateActions.run();
        if (!originValid.getAsBoolean() || !scopes.contains(scope) || !scope.available(row, verb, context)) { refresh(); return; }
        PaletteStep pending = scope.step(row, verb, context);
        if (pending != null) { showStep(pending); return; }
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
        open = false; picker = false; step = null; completing = false;
        palette.hideStep(); palette.setVisible(false);
        if (scopeListener != null) { scopeListener.close(); scopeListener = null; }
        active = null;
        context = new PaletteContext(macOs, PaletteTarget.none(), maxResults, trivialCommands);
        originValid = () -> false;
        dismissed.run();
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; generation++;
        scopesListener.close();
    }
}
