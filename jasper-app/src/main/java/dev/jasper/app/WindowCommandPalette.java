package dev.jasper.app;

import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Window-local palette ownership: one active scope, the scope picker, validated dispatch and focus restore. */
final class WindowCommandPalette implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(WindowCommandPalette.class.getName());
    private final WindowContent owner;
    private final ScopeRegistry scopes;
    private final String defaultScopeId;
    private final boolean macOs;
    private final CommandPalette palette;
    private final Overlay overlay = new Overlay();
    private final CommandRegistry.Subscription scopesListener;
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutOverlay(); }
        @Override public void componentMoved(ComponentEvent event) { layoutOverlay(); }
    };
    private CommandRegistry.Subscription scopeListener;
    private PaletteScope active;
    private PaletteStep step;
    private PaletteContext context;
    private boolean picker;
    private JRootPane root;
    private TerminalTab originTab;
    private TerminalPane originPane;
    private Component priorFocus;
    private boolean open, closed, dirty = true;
    // A step's complete() may deliver asynchronously; without this, two quick Enter presses
    // would call complete() twice before the first result arrives.
    private boolean completing;
    private int maxResults = PaletteContext.DEFAULT_MAX_RESULTS;

    WindowCommandPalette(WindowContent owner, ScopeRegistry scopes, String defaultScopeId, boolean macOs) {
        this.owner = owner; this.scopes = scopes; this.defaultScopeId = defaultScopeId; this.macOs = macOs;
        context = new PaletteContext(macOs, PaletteTarget.none());
        palette = new CommandPalette(macOs, this::queryChanged, this::execute, this::escape, this::openPicker);
        palette.setFocusCycleRoot(true);
        palette.setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return palette.queryField(); }
            @Override public Component getComponentBefore(Container root, Component current) { return palette.queryField(); }
            @Override public Component getFirstComponent(Container root) { return palette.queryField(); }
            @Override public Component getLastComponent(Container root) { return palette.queryField(); }
            @Override public Component getDefaultComponent(Container root) { return palette.queryField(); }
        });
        overlay.setLayout(null); overlay.setOpaque(false); overlay.add(palette); overlay.setVisible(false);
        scopesListener = scopes.onChanged(this::changed);
    }

    void install(JRootPane replacement) {
        if (closed || root == replacement) return;
        uninstall(); root = replacement;
        root.getLayeredPane().add(overlay, JLayeredPane.MODAL_LAYER);
        root.addComponentListener(resize); root.getLayeredPane().addComponentListener(resize);
        owner.addComponentListener(resize); owner.tabStrip().addComponentListener(resize);
        layoutOverlay();
    }

    void toggle() { open(defaultScopeId); }

    /** Whether a scope ID is currently registered; a shortcut targeting an unregistered scope is inert. */
    boolean hasScope(String scopeId) { return scopes.find(scopeId).isPresent(); }

    /**
     * Opens in a scope, or dismisses when its shortcut is pressed while it is already active
     * (regardless of the picker). Switching to a different scope keeps the query, except from
     * the picker, where the raw {@code >…} filter text is dropped.
     */
    void open(String scopeId) {
        PaletteScope scope = scopes.find(scopeId).orElse(null);
        if (scope == null) return;
        if (open) {
            if (scope == active) { dismiss(); return; }
            activate(scope, !picker);
            return;
        }
        if (closed || root == null || !owner.isActiveAndOpen()
            || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
        owner.updateActions();
        originTab = owner.currentTab(); originPane = owner.currentPane();
        priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        context = new PaletteContext(macOs, originPane == null ? PaletteTarget.none() : PaletteTarget.of(originPane), maxResults);
        open = true; overlay.swallowing = false; palette.setVisible(true); overlay.setVisible(true);
        activate(scope, false);
        palette.queryField().requestFocusInWindow();
    }

    private void activate(PaletteScope scope, boolean keepQuery) {
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
        context = new PaletteContext(macOs, context.target(), maxResults);
        if (open && active != null) {
            palette.setScope(active.label(), active.icon(), active.placeholder(), active.verbs(), maxResults, active.monospaceRows());
            rebuild(true);
        }
    }

    int maxResults() { return maxResults; }

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
        layoutOverlay();
    }

    private void closeStep() {
        step = null;
        completing = false;
        palette.hideStep();
        rebuild(true);
        palette.queryField().requestFocusInWindow();
    }

    private void completeStep() {
        PaletteStep current = step;
        completing = true;
        palette.setStepError(null);
        try {
            current.complete().accept(palette.stepValues(), result -> {
                completing = false;
                if (step != current || !open) return;
                if (result.error() != null) { palette.setStepError(result.error()); layoutOverlay(); return; }
                step = null;
                palette.hideStep();
                restoreAndHide();
                if (result.reopenScopeId() != null) {
                    open(result.reopenScopeId());
                    if (open && result.reopenQuery() != null) palette.queryField().setText(result.reopenQuery());
                    if (open && result.reopenRowId() != null) palette.selectRow(result.reopenRowId());
                }
            });
        } catch (RuntimeException failure) {
            completing = false;
            LOG.log(System.Logger.Level.ERROR, "Palette step completion failed", failure);
            palette.setStepError("Could not complete: " + failure.getMessage());
        }
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
        if (!owner.updatingActions()) refreshIfChanged();
    }

    void refreshIfChanged() {
        if (!open) return;
        if (!validOrigin() || !scopes.contains(active)) { dismiss(); return; }
        if (dirty) refresh();
    }

    void refresh() {
        if (!open) return;
        if (!validOrigin() || !scopes.contains(active)) { dismiss(); return; }
        rebuild(true);
    }

    void refreshTheme() { palette.refreshTheme(); if (open) { layoutOverlay(); overlay.repaint(); } }

    private void rebuild(boolean preserve) {
        if (!open || active == null) return;
        if (step != null) { dirty = true; return; }
        dirty = false;
        PaletteRow selected = palette.resultList().getSelectedValue();
        String query = palette.queryField().getText();
        PaletteResults results = picker ? pickerResults(query.substring(1)) : active.search(query, context);
        String keep = preserve && selected != null ? selected.id() : results.initialSelectionId();
        palette.setResults(results.rows(), picker ? "Scopes" : results.sectionLabel(), keep);
        layoutOverlay();
    }

    private PaletteResults pickerResults(String filter) {
        String q = CommandSearch.normalize(filter);
        var rows = new ArrayList<PaletteRow>();
        for (PaletteScope scope : scopes.scopes()) {
            if (!q.isEmpty() && !matchesScope(scope, q)) continue;
            rows.add(new PaletteRow(scope.id(), scope.label(), scope.description(), owner.scopeShortcut(scope.id()),
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

    private boolean validOrigin() {
        return owner.isActiveAndOpen() && owner.currentTab() == originTab
            && owner.currentPane() == originPane
            && (originPane == null || originTab != null && originTab.panes().contains(originPane));
    }

    private void execute(PaletteRow row, int verbIndex) {
        if (!open) return;
        if (picker) { scopes.find(row.id()).ifPresent(scope -> activate(scope, false)); return; }
        PaletteScope scope = active;
        if (!validOrigin() || !scopes.contains(scope)) { dismiss(); return; }
        if (verbIndex < 0 || verbIndex >= scope.verbs().size()) return;
        PaletteVerb verb = scope.verbs().get(verbIndex);
        owner.updateActions();
        if (!validOrigin() || !scopes.contains(scope) || !scope.available(row, verb, context)) { refresh(); return; }
        PaletteStep pending = scope.step(row, verb, context);
        if (pending != null) { showStep(pending); return; }
        PaletteContext target = context;
        restoreAndHide();
        try {
            scope.execute(row, verb, target);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.ERROR, "Palette action failed: " + scope.id() + " " + row.id(), failure);
            owner.onError.accept("Could not run " + row.title() + ". See the application log for details.");
        }
    }

    private void restoreAndHide() {
        // Pane changes notify us after choosing the new logical target. A captured component
        // in the old pane may still be showing, but restoring it would undo that transition.
        boolean restorePriorFocus = validOrigin();
        open = false; picker = false;
        step = null; completing = false; palette.hideStep();
        palette.setVisible(false); overlay.setVisible(overlay.swallowing);
        if (scopeListener != null) { scopeListener.close(); scopeListener = null; }
        active = null;
        if (restorePriorFocus && priorFocus != null && priorFocus.isShowing()
            && SwingUtilities.isDescendingFrom(priorFocus, owner))
            priorFocus.requestFocusInWindow();
        else if (owner.currentPane() != null) owner.currentPane().focusTerminal();
        priorFocus = null; originTab = null; originPane = null;
    }

    private void layoutOverlay() {
        if (root == null) return;
        // Include Swing title/menu chrome, but never the native window controls.
        overlay.setBounds(0, 0, root.getLayeredPane().getWidth(), root.getLayeredPane().getHeight());
        overlay.doLayout(); overlay.repaint();
    }

    static Rectangle positioned(Rectangle terminal, Dimension preferred, Rectangle available, int margin) {
        int insetX = Math.min(margin, Math.max(0, available.width / 2));
        int insetY = Math.min(margin, Math.max(0, available.height / 2));
        int width = Math.min(preferred.width, Math.max(0, available.width - 2 * insetX));
        int height = Math.min(preferred.height, Math.max(0, available.height - 2 * insetY));
        int x = terminal.x + (terminal.width - width) / 2;
        // Anchor the card in the upper half while keeping clearance in small windows.
        int y = terminal.y + terminal.height / 3 - height / 2;
        x = Math.max(available.x + insetX, Math.min(x, available.x + available.width - insetX - width));
        y = Math.max(available.y + insetY, Math.min(y, available.y + available.height - insetY - height));
        return new Rectangle(x, y, width, height);
    }

    private void uninstall() {
        if (root == null) return;
        root.removeComponentListener(resize); root.getLayeredPane().removeComponentListener(resize);
        owner.removeComponentListener(resize); owner.tabStrip().removeComponentListener(resize);
        root.getLayeredPane().remove(overlay); root.repaint(); root = null;
    }

    @Override public void close() {
        if (closed) return;
        dismiss(); closed = true; scopesListener.close(); uninstall();
    }

    private final class Overlay extends JComponent {
        private boolean swallowing;
        Overlay() {
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    event.consume(); swallowing = true; dismiss();
                }
                @Override public void mouseReleased(MouseEvent event) { event.consume(); }
                @Override public void mouseClicked(MouseEvent event) {
                    event.consume(); swallowing = false; if (!open) setVisible(false);
                }
                @Override public void mouseMoved(MouseEvent event) {
                    if (swallowing && !open) { swallowing = false; setVisible(false); }
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
            addMouseWheelListener(MouseEvent::consume);
        }
        @Override public void doLayout() {
            if (root == null || !SwingUtilities.isDescendingFrom(owner, root.getLayeredPane())) return;
            Rectangle terminal = SwingUtilities.convertRectangle(owner.tabStrip().getParent(), owner.tabStrip().getBounds(), this);
            Rectangle available = terminal;
            if (terminal.height < palette.getPreferredSize().height + UIScale.scale(32))
                available = SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), this);
            palette.setBounds(positioned(terminal, palette.getPreferredSize(), available, UIScale.scale(16)));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (!open) return;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle card = palette.getBounds();
                g.setColor(new Color(0, 0, 0, owner.theme().chrome() == BuiltinTheme.LIGHT ? 2 : 4));
                for (int i = 12; i >= 1; i--) {
                    int expansion = UIScale.scale(i), arc = UIScale.scale(24) + expansion * 2;
                    g.fillRoundRect(card.x - expansion, card.y - expansion, card.width + expansion * 2,
                        card.height + expansion * 2, arc, arc);
                }
            } finally { g.dispose(); }
        }
    }
}
