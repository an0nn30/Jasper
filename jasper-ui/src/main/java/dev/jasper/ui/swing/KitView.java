package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.MotionPolicy;
import dev.jasper.ui.model.*;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * EDT-confined descriptor view. The view owns controls and subscriptions; the host owns its context.
 * Ordinary controls and explicitly registered specialized content share the same owned lifetime.
 */
public final class KitView extends JPanel implements AutoCloseable {
    private final KitContext context;
    private final Consumer<UiEvent> events;
    private final ViewReconciler reconciler;
    private final MotionPolicy motion;
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private ViewSpec spec;
    private boolean updating;
    private int availableHeight = Integer.MAX_VALUE;
    private AutoCloseable appearance;
    private final UIDefaults defaults;
    private final PropertyChangeListener fontChanges;

    private final boolean pointerOnly;
    private final java.awt.event.ContainerListener pointerOnlyMounts = new java.awt.event.ContainerAdapter() {
        @Override public void componentAdded(java.awt.event.ContainerEvent event) { unfocusable(event.getChild()); }
    };

    /** Mount a validated descriptor tree; every operation except close requires EDT. */
    public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events) { this(context, spec, events, false); }

    /**
     * Mount a descriptor tree whose controls never take keyboard focus when {@code pointerOnly}: a host overlay (for
     * example notification cards over a terminal) keeps the focus where it is while its controls stay operable by pointer
     * and keep their accessible names and actions. App-only; not part of the plugin SDK.
     */
    public KitView(KitContext context, ViewSpec spec, Consumer<UiEvent> events, boolean pointerOnly) {
        requireEdt(); this.pointerOnly = pointerOnly; this.context = new KitContext(Objects.requireNonNull(context)); this.events = Objects.requireNonNull(events);
        this.reconciler = new ViewReconciler(this); this.motion = context.motionPolicy();
        setLayout(new BorderLayout()); setOpaque(false); KitOwnership.mark(this, "view");
        defaults = UIManager.getDefaults();
        fontChanges = event -> {
            if (!SwingTypography.fontProperty(event.getPropertyName())) return;
            if (EventQueue.isDispatchThread()) refresh(); else EventQueue.invokeLater(this::refresh);
        };
        try {
            if (pointerOnly) unfocusable(this);
            update(spec); defaults.addPropertyChangeListener(fontChanges);
            appearance = context.binding().subscribe(pair -> refresh());
        } catch (Throwable failure) {
            defaults.removePropertyChangeListener(fontChanges); artworkPresentations.clear(); reconciler.close(); motion.close(); throw failure;
        }
    }
    /** Reconcile by stable id/kind; unchanged descriptor values preserve pending input. */
    public void update(ViewSpec spec) { requireOpen(); Objects.requireNonNull(spec);
        if (updating) throw new IllegalStateException("Reentrant view update");
        reconciler.validateFieldTrailing(spec, null, null);
        ViewSpec previous = this.spec;
        updating = true; try { reconciler.update(spec); this.spec = spec; } finally { updating = false; }
        hideHostedHeading();
        artworkPresentations.keySet().removeIf(id -> !hasIcon(spec.root(),id));
        if (pointerOnly) unfocusable(this);
        // Native panel hosts reborrow a replacement header after reconciliation, never during it.
        try { firePropertyChange("viewSpec", previous, spec); } catch (Throwable failure) { report(failure); }
    }
    private static boolean hasStack(Nodes.Node node,String id) {
        if(node.id().equals(id))return node instanceof Nodes.Stack;
        for(var child:ControlRegistry.children(node))if(hasStack(child,id))return true;
        return false;
    }
    private static boolean hasIcon(Nodes.Node node,String id) {
        if(node.id().equals(id))return node instanceof Nodes.Icon;
        if(node instanceof Nodes.ItemList list)for(var item:list.items()) {
            if(item.leading().filter(child->hasIcon(child,id)).isPresent())return true;
            for(var child:item.title())if(hasIcon(child,id))return true;
            for(var child:item.subtitle())if(hasIcon(child,id))return true;
            for(var child:item.trailing())if(hasIcon(child,id))return true;
        }
        for(var child:ControlRegistry.children(node))if(hasIcon(child,id))return true;
        return false;
    }
    /** Whether a field of the current descriptor names {@code listId} as the list its keys move ({@code Field.listId}). */
    boolean namesList(String listId) { return spec != null && namesList(spec.root(), listId); }
    private static boolean namesList(Nodes.Node node, String listId) {
        if (node instanceof Nodes.Field field && field.listId().equals(listId)) return true;
        for (var child : ControlRegistry.children(node)) if (namesList(child, listId)) return true;
        return false;
    }
    /** Host attachment seam: only live enabled PICKER/action controls may issue an attachment. */
    public JComponent anchorTarget(String id) {
        requireOpen();
        var control = reconciler.control(Objects.requireNonNull(id));
        if (!(control.node instanceof Nodes.Button || control.node instanceof Nodes.Choice c && c.kind() == Nodes.ChoiceKind.PICKER)
                || !eligible(spec.root(), id, true)) throw new IllegalArgumentException("Not an eligible attachment control");
        return control.focusTarget();
    }
    private static boolean eligible(Nodes.Node node, String id, boolean parent) {
        boolean live = parent && node.state().visible() && node.state().enabled() && !node.state().busy();
        if (node.id().equals(id)) return live;
        for (var child : ControlRegistry.children(node)) if (eligible(child, id, live)) return true;
        return false;
    }
    private final java.util.Map<String,ArtworkPresentation> artworkPresentations=new java.util.HashMap<>();
    /** Named receiving treatment, retained across theme and same-id model changes. */
    public void setArtworkPresentation(String iconId,ArtworkPresentation presentation) {
        requireOpen();Objects.requireNonNull(presentation);
        if(!hasIcon(spec.root(),Objects.requireNonNull(iconId)))throw new IllegalArgumentException("Unknown icon");
        if(presentation==ArtworkPresentation.DEFAULT)artworkPresentations.remove(iconId);else artworkPresentations.put(iconId,presentation);
        reconciler.refreshArtwork(iconId);
    }
    ArtworkPresentation artworkPresentation(String id){return artworkPresentations.getOrDefault(id,ArtworkPresentation.DEFAULT);}
    private final java.util.Set<String> hintStacks=new java.util.HashSet<>();
    /**
     * App-only compact hint face for one stack (the command palette's footer): its caption text and embedded link actions
     * use the hint typography and its children the hint gap. Same-id updates retain it; the view's close retires it.
     * Not part of the plugin SDK.
     */
    public void setHintPresentation(String stackId, boolean hint) {
        requireOpen();
        if(!hasStack(spec.root(),Objects.requireNonNull(stackId)))throw new IllegalArgumentException("Not a stack");
        if(hint ? hintStacks.add(stackId) : hintStacks.remove(stackId)) update(spec);
    }
    boolean hintStack(String id){return hintStacks.contains(id);}
    private boolean settingsSurface, settingsHeader;
    /**
     * Kit-internal: this view is part of the Settings shell, so it composes with the Settings roles; the header view also
     * lays out its history buttons and breadcrumb as the Settings header. Re-reconciles the current descriptor.
     */
    void settingsChrome(boolean header) {
        requireOpen();
        if (settingsSurface && settingsHeader == header) return;
        settingsSurface = true; settingsHeader = header; update(spec);
    }
    boolean settingsSurface() { return settingsSurface; }
    boolean settingsHeader() { return settingsHeader; }
    private boolean keycapDetail;
    /** Kit-internal: this view is the inline editor of a keycap tree row, whose rows sit closer together. */
    void markKeycapDetail() { requireOpen(); if (keycapDetail) return; keycapDetail = true; update(spec); }
    boolean keycapDetail() { return keycapDetail; }
    /**
     * App-only owned action inside a mounted TEXT or SEARCH field. The action has this view's normal
     * event, focus and accessibility routing. Validation is transactional, including ids in unmounted rows.
     * Same-id/kind field updates retain it; removal, replacement and close release its listeners.
     */
    public void setFieldTrailing(String fieldId, java.util.Optional<Nodes.Button> action) {
        requireOpen(); Objects.requireNonNull(action);
        var field = textField(fieldId);
        var kind = ((Nodes.Field)field.node).kind();
        if (kind != Nodes.FieldKind.TEXT && kind != Nodes.FieldKind.SEARCH) throw new IllegalArgumentException("Trailing action requires TEXT or SEARCH");
        reconciler.validateFieldTrailing(spec, field, action.orElse(null));
        action.ifPresent(button -> {
            if (!button.icon().isEmpty() && !context.icons().names().contains(button.icon())) throw new IllegalArgumentException("Unknown action icon");
        });
        field.trailing(action.orElse(null));
        if (pointerOnly) unfocusable(this);
    }
    private TextFields.FieldControl textField(String id) {
        var control = reconciler.mounted(Objects.requireNonNull(id));
        if (!(control instanceof TextFields.FieldControl field)) throw new IllegalArgumentException("Not a mounted ordinary field: " + id);
        return field;
    }
    /** Stable tree-row contexts and passive keycaps; unknown ids reject before publication. */
    public void setTreeRowPresentation(String treeId,java.util.Map<String,TreeRowPresentation> rows) {
        requireOpen();
        if(!(reconciler.control(treeId) instanceof Trees.TreeControl tree))throw new IllegalArgumentException("Not a tree");
        var copy=java.util.Map.copyOf(rows);
        if(!tree.itemIds.containsAll(copy.keySet()))throw new IllegalArgumentException("Unknown tree item");
        tree.presentation=copy;tree.refresh();
    }
    /** Select the app-only value receiver face. Same-id updates retain it; removal/replacement/close retire it. */
    public void setButtonValuePresentation(String buttonId, ButtonValuePresentation presentation) {
        requireOpen(); Objects.requireNonNull(presentation);
        if (!(reconciler.control(buttonId) instanceof Buttons.ButtonControl button)) throw new IllegalArgumentException("Not a button");
        button.button.valuePresentation = presentation; button.refresh();
    }
    /** Replace only the visual value of one actionable button, including a tree-owned detail. */
    public void setButtonKeycaps(String buttonId,java.util.Optional<KeycapSequence> sequence) {
        requireOpen();Objects.requireNonNull(sequence);
        if(!(reconciler.control(buttonId) instanceof Buttons.ButtonControl button))throw new IllegalArgumentException("Not a button");
        button.keycaps=sequence.orElse(null);button.refresh();
    }
    /** Attach one standard-descriptor detail below a visible leaf, owned by the tree rather than a recycled row. */
    public TreeDetailHandle attachTreeDetail(String treeId, String itemId, ViewSpec body, Consumer<UiEvent> events, Runnable detached) {
        requireOpen();
        if (!(reconciler.control(Objects.requireNonNull(treeId)) instanceof Trees.TreeControl tree)) throw new IllegalArgumentException("Not a tree");
        return tree.attach(Objects.requireNonNull(itemId),Objects.requireNonNull(body),Objects.requireNonNull(events),Objects.requireNonNull(detached));
    }
    /** Current immutable descriptor; it never contains mutable input or secret content. */
    public ViewSpec spec() { requireOpen(); return spec; }
    /**
     * Use a temporary secret copy, erased in finally even if the consumer fails. An async owner
     * must explicitly clone/transfer what it needs and erase that independently owned copy.
     */
    public void withSecret(String fieldId, Consumer<char[]> use) {
        requireOpen(); Objects.requireNonNull(use);
        char[] copy = secret(fieldId).snapshot();
        try { use.accept(copy); } finally { java.util.Arrays.fill(copy, (char)0); }
    }
    /** Erase live mutable storage; callbacks may request erasure while an edit is being delivered. */
    public void clearSecret(String fieldId) { requireOpen(); secret(fieldId).clear(); }
    /** Copy a caller-owned value into live storage without emitting an edit. Caller remains responsible for its array. */
    public void replaceSecret(String fieldId, char[] value) { requireOpen(); secret(fieldId).replace(Objects.requireNonNull(value)); }
    private SecretFields.SecretControl secret(String id) {
        var control = reconciler.control(Objects.requireNonNull(id));
        if (control instanceof SecretFields.SecretControl secret) return secret;
        throw new IllegalArgumentException("Node is not a secret field: " + id);
    }
    void revealSecrets(SecretFields.SecretControl source, boolean revealed) { reconciler.revealSecrets(source, revealed); }
    /** Focus a control; an offscreen inline row control is first scrolled into view and mounted. */
    public void focus(String id) { requireOpen(); reconciler.control(Objects.requireNonNull(id)).focusTarget().requestFocusInWindow(); }
    /** Select all live text of an ordinary field without emitting an edit or moving focus. */
    public void selectAll(String fieldId) {
        requireOpen();
        var control = reconciler.control(Objects.requireNonNull(fieldId));
        if (!(control instanceof TextFields.FieldControl field)) throw new IllegalArgumentException("Node is not a field: " + fieldId);
        field.input.selectAll();
    }
    /**
     * Scroll a node into view without moving keyboard focus, for example the first row matching a search while the
     * search field keeps focus and caret. An offscreen inline row control is first mounted.
     */
    public void reveal(String id) {
        requireOpen();
        Objects.requireNonNull(id);
        if (reconciler.revealInline(id)) return;
        var component = reconciler.control(id).component;
        component.scrollRectToVisible(new Rectangle(0, 0, component.getWidth(), component.getHeight()));
    }
    /**
     * Whether an input method is composing text in a field (uncommitted text an IME has not yet committed). A host that
     * routes keys around the field leaves them to the input method while this is true.
     */
    public boolean composing(String fieldId) {
        requireOpen();
        if (reconciler.control(Objects.requireNonNull(fieldId)) instanceof TextFields.FieldControl field) return field.composing;
        throw new IllegalArgumentException("Node is not a field: " + fieldId);
    }
    /** Register by Custom.contentId (not node id); ordinary Swing trees cannot enter through this method. */
    public void registerContent(String id, JComponent component, AutoCloseable lifetime) { requireOpen(); reconciler.registerContent(id, Objects.requireNonNull(component), Objects.requireNonNull(lifetime)); }
    /** Borrow the mounted kit header; ownership remains with the view. */
    public JComponent panelHeader(String panelId) { requireOpen(); return panel(panelId).header.component; }
    /** Host integration slot, separate from the descriptor event callback. */
    public void setPanelHostActions(String panelId, List<Nodes.Button> actions, Consumer<UiEvent> events) { requireOpen(); panel(panelId).header.hostActions(actions, events); revalidate(); repaint(); }
    /**
     * Host header metadata (detail, connected dot, contributed icon actions) shown in the borrowed header's one row,
     * contributed actions before the host actions. Replacing it keeps controls by position; {@link PanelHostHeader#EMPTY}
     * clears it. A replacement panel header starts empty; closing the view releases every contributed action.
     */
    public void setPanelHostHeader(String panelId, PanelHostHeader header) { requireOpen(); panel(panelId).header.hostHeader(Objects.requireNonNull(header)); revalidate(); repaint(); }
    private Panels.PanelControl panel(String id) {
        var control = reconciler.control(Objects.requireNonNull(id));
        if (control instanceof Panels.PanelControl panel) return panel;
        throw new IllegalArgumentException("Node is not a panel: " + id);
    }
    /**
     * App-only: moves this view to another theme route (a plugin view mounted as workspace content takes the workspace
     * route, as terminals and editors do, and a toolbar view the title bar's), re-applying every control's appearance in place without replacing controls,
     * input or registered content. Other views built from the same caller context keep their route. EDT confined.
     */
    public void route(ThemeRoute route) {
        requireOpen(); Objects.requireNonNull(route);
        if (context.route() == route) return;
        context.route(route); refresh(); repaint();
    }
    /** The theme slot this view currently paints from. */
    public ThemeRoute route() { return context.route(); }
    /**
     * App-only: whether this view paints its route's panel surface behind its controls. A workspace content tab does,
     * opaque, as its former raw panels did; registered specialized content paints its own background (or glass) above it.
     * Off by default: a view is otherwise transparent over its host. EDT confined.
     */
    public void paintsSurface(boolean value) {
        requireOpen();
        if (surface == value) return;
        surface = value; setOpaque(value); repaint();
    }
    private boolean surface;
    /**
     * App-only: whether this view's panels (outside manager columns) paint their own rounded panel surface (the default).
     * A native tool window that already paints the dock surface behind an app-owned body turns it off, so a body panel
     * (an empty state) sits on that surface as the rest of the body does. EDT confined.
     */
    public void panelSurface(boolean painted) {
        requireOpen();
        if (panelSurface == painted) return;
        panelSurface = painted; refresh(); repaint();
    }
    /** Whether this view's panels paint their own surface. */
    public boolean panelSurface() { return panelSurface; }
    private boolean panelSurface = true;
    /** The manager column a host's native arrangement places a whole view in (see {@link #managerColumn(ManagerColumn)}). */
    public enum ManagerColumn { NONE, RAIL, LIST }
    /**
     * App-only: presents this view's rows as one column of a manager window, for a native shell that arranges its own
     * columns (a file chooser's places and listing): RAIL rows are the manager rail's (one-line rows, rounded control
     * fill, branches as section headings), LIST rows the list column's (rounded selection, one-line rows at the rail
     * height, a table's header on the row inset). The host supplies the column's outer insets. EDT confined.
     */
    public void managerColumn(ManagerColumn column) {
        requireOpen(); Objects.requireNonNull(column);
        if (managerColumn == column) return;
        managerColumn = column; update(spec);
    }
    /** The host-assigned manager column, NONE by default. */
    public ManagerColumn managerColumn() { return managerColumn; }
    private ManagerColumn managerColumn = ManagerColumn.NONE;
    /** The reconciler's column index for the host-assigned column: -1 none, 0 rail, 1 list. */
    int hostColumn() { return switch (managerColumn) { case NONE -> -1; case RAIL -> 0; case LIST -> 1; }; }
    /**
     * App-only: this view is a native window's title action strip. Its icon-only menu buttons show their menu
     * indicator on the borderless control surface, as the window title references paint them. EDT confined.
     */
    public void titleStrip(boolean value) {
        requireOpen();
        if (titleStrip == value) return;
        titleStrip = value; refresh(); repaint();
    }
    /** Whether this view is presented as a window title action strip. */
    public boolean titleStrip() { return titleStrip; }
    private boolean titleStrip;
    /**
     * App-only: the host shows this view's form title (a classic dialog's header strip, or a title bar the system draws),
     * so the form draws neither its title label nor a hero that only repeats it (Enterprise presentation spec 10.8). EDT
     * confined.
     */
    public void hostTitled(boolean value) {
        requireOpen();
        if (hostTitled == value) return;
        hostTitled = value; refresh(); repaint();
    }
    /** Whether the host shows this view's form title. */
    public boolean hostTitled() { return hostTitled; }
    private boolean hostTitled;
    /**
     * App-only: this view's form is a dialog's content under the classic mapping, so its footer is the dialog's band on
     * {@code surface.footer} under a {@code border.window} rule (spec 10.8), in a classic dialog or a native one. EDT
     * confined.
     */
    public void dialogBand(boolean value) {
        requireOpen();
        if (dialogBand == value) return;
        dialogBand = value; refresh(); repaint();
    }
    /** Whether this view's form footer is a classic dialog's band. */
    public boolean dialogBand() { return dialogBand; }
    private boolean dialogBand;
    /**
     * App-only: this view's form is a classic dialog's content, so the dialog's card paints the form's fill and frame
     * (spec 10.8). EDT confined.
     */
    public void dialogCard(boolean value) {
        requireOpen();
        if (dialogCard == value) return;
        dialogCard = value; refresh(); repaint();
    }
    /** Whether a classic dialog's card paints this view's form fill and frame. */
    public boolean dialogCard() { return dialogCard; }
    private boolean dialogCard;
    /** A hero hidden because it only repeats a host-titled form's title. */
    private static final java.util.function.Consumer<javax.swing.JLabel> HOSTED_HEADING = label -> label.setVisible(false);
    /**
     * Under {@link #hostTitled()} a SHEET or FULL form's hero that is a text repeating the form's title is the host's
     * title too: it stays hidden through every later refresh through {@code TextControl.styled}, as the classic prompt
     * face hides a prompt's title (plan 4b). A prompt's hero (its tile, heading and message) is the prompt face's, and
     * a hero that says more than the title stays. Re-applied after each update, which clears the hook.
     */
    private void hideHostedHeading() {
        if (!(spec.root() instanceof dev.jasper.ui.model.Nodes.Form form) || form.hero().isEmpty()) return;
        if (form.kind() != dev.jasper.ui.model.Nodes.FormKind.SHEET && form.kind() != dev.jasper.ui.model.Nodes.FormKind.FULL) return;
        if (!(form.hero().get() instanceof dev.jasper.ui.model.Nodes.Text text) || !text.text().strip().equals(form.title().strip())) return;
        if (!(reconciler.mounted(text.id()) instanceof Labels.TextControl hero)) return;
        if (hostTitled && hero.styled == null) { hero.styled = HOSTED_HEADING; hero.component.setVisible(false); }
        else if (!hostTitled && hero.styled == HOSTED_HEADING) { hero.styled = null; hero.refresh(); }
    }
    /**
     * App-only: this view is part of the Run window (Enterprise presentation spec 11.2). Its RESULTS lists take the Run
     * window's rows, heading bands and counts, its PROMPT_HEADER tile the Run window's tile, its transparent fields the
     * data face and no ring of their own (the host's field box shows focus), and its text the Run window's colours.
     * Re-reconciles the current descriptor. EDT confined.
     */
    public void runWindow(boolean value) {
        requireOpen();
        if (runWindow == value) return;
        runWindow = value; update(spec);
    }
    /** Whether this view is presented as part of the Run window. */
    public boolean runWindow() { return runWindow; }
    private boolean runWindow;
    /**
     * App-only: this view is a toolbar strip, a contributed view in a window's main toolbar. Its icon-only menu buttons
     * show their menu indicator on the borderless control surface, as a title strip's do, and its controls keep the
     * kit's control height, which the toolbar row centres. EDT confined.
     */
    public void toolbarStrip(boolean value) {
        requireOpen();
        if (toolbarStrip == value) return;
        toolbarStrip = value; refresh(); repaint();
    }
    /** Whether this view is presented as a toolbar strip. */
    public boolean toolbarStrip() { return toolbarStrip; }
    private boolean toolbarStrip;
    @Override protected void paintComponent(Graphics graphics) {
        if (!surface) { super.paintComponent(graphics); return; }
        graphics.setColor(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
        graphics.fillRect(0, 0, getWidth(), getHeight());
    }
    /** App-only available content height excluding native chrome, in logical pixels; EDT confined. */
    public void setAvailableHeight(int logicalPixels) {
        requireOpen(); if (logicalPixels <= 0) throw new IllegalArgumentException("Available height must be positive");
        availableHeight = logicalPixels; refresh();
    }
    int availableHeight() { return availableHeight == Integer.MAX_VALUE ? Integer.MAX_VALUE : com.formdev.flatlaf.util.UIScale.scale(availableHeight); }
    boolean dismissPopup() { return reconciler.dismissPopup(); }
    boolean popupOpen() { return reconciler.popupOpen(); }
    ControlRegistry.Control control(String id) { return reconciler.control(id); }
    /** The control mounted for a node id, or null; never moves a row's keyboard focus. */
    ControlRegistry.Control controlOrNull(String id) { return reconciler.mounted(id); }
    void inheritedEnabled(boolean enabled) { reconciler.inheritedEnabled(enabled); }
    KitContext context() { return context; }
    MotionPolicy motion() { return motion; }
    void dispatch(UiEvent event) {
        if (closeRequested.get()) return;
        try { events.accept(event); } catch (Throwable failure) { report(failure); }
    }
    void report(Throwable failure) {
        Thread thread = Thread.currentThread();
        try { thread.getUncaughtExceptionHandler().uncaughtException(thread, failure); }
        catch (Throwable ignored) { /* diagnostics must not break view ownership or EDT event delivery */ }
    }
    @Override protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        ThemePainting.halos(this, graphics);
    }
    private void refresh() {
        if (closeRequested.get()) return;
        try { motion.enabled(); reconciler.refresh(); hideHostedHeading(); if (pointerOnly) unfocusable(this); } catch (Throwable failure) { report(failure); }
    }
    /** Whether this view's controls never take keyboard focus. */
    public boolean pointerOnly() { return pointerOnly; }
    /** Makes a mounted subtree unfocusable, and anything later mounted into it (lazily mounted rows included). */
    private void unfocusable(Component component) {
        component.setFocusable(false);
        if (component instanceof Container container) {
            if (container instanceof JComponent owned && owned.getClientProperty(pointerOnlyMounts) == null) {
                owned.putClientProperty(pointerOnlyMounts, Boolean.TRUE);
                container.addContainerListener(pointerOnlyMounts);
            }
            for (Component child : container.getComponents()) unfocusable(child);
        }
    }
    static void requireEdt() { if (!EventQueue.isDispatchThread()) throw new IllegalStateException("KitView requires EDT"); }
    private void requireOpen() { requireEdt(); if (closeRequested.get()) throw new IllegalStateException("KitView is closed"); }
    /** Whether close has been requested; host teardown may outlive plugin disposal. */
    public boolean isClosed() { return closeRequested.get(); }
    /** Idempotent; callers outside EDT enqueue the owned teardown. */
    @Override public void close() {
        if (!closeRequested.compareAndSet(false, true)) return;
        if (EventQueue.isDispatchThread()) dispose(); else EventQueue.invokeLater(this::dispose);
    }
    private void dispose() {
        artworkPresentations.clear();
        defaults.removePropertyChangeListener(fontChanges);
        if (appearance != null) try { appearance.close(); } catch (Throwable failure) { report(failure); }
        reconciler.close(); try { motion.close(); } catch (Throwable failure) { report(failure); }
    }
}
