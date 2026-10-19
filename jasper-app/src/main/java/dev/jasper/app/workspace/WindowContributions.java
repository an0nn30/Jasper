package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.persistence.UiState;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * This window's Swing side of the application-wide contributions model: one {@link Action} per
 * contributed action, registered as a palette command and bound to its shortcut, kept in step with the
 * model. Chrome and status rendering hang off the same change notifications. Panels dock in the window's
 * {@link DockState}; the one listener on it builds what starts showing, lays the area out, renders the
 * stripes, tells the panels and saves. EDT only.
 */
final class WindowContributions implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(WindowContributions.class.getName());
    private final WindowContent owner;
    private final Contributions model;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Subscription> commands = new LinkedHashMap<>();
    private final Map<PaletteScope, Subscription> scopes = new LinkedHashMap<>();
    private Subscription listening;
    private Subscription paletteRequests;

    /** One panel's instance in this window, built the first time it shows; where it docks lives in the dock state. */
    private static final class PanelInstance {
        final PanelEntry entry;
        PanelSite site;
        JComponent component;
        /** What the panel last heard: a nested change can overtake a notification, which then must not be sent. */
        boolean notified;
        PanelInstance(PanelEntry entry) { this.entry = entry; }
    }

    private final UiState state;
    private final DockState dock;
    private final Consumer<DockState.Change> docked = this::docked;
    private final Map<String, PanelInstance> panels = new LinkedHashMap<>();
    /** In Zen, the panels that showed before it, which show again when it ends and stay saved as showing; else null. */
    private Set<String> beforeZen;
    private Subscription panelRequests;

    WindowContributions(WindowContent owner, Contributions model, UiState state) {
        this.owner = owner;
        this.model = model;
        this.state = state;
        this.dock = owner.dock();
        // A window restored in Zen remembers its saved panels for leaving it instead of showing them.
        if (owner.zenMode()) beforeZen = new LinkedHashSet<>();
        syncActions();
        owner.chrome().connect(this);
        renderStatus();
        restoreSizes();
        for (DockStripe stripe : owner.stripes().all()) {
            stripe.onToggle = this::toggleAndActivate;
            stripe.onMove = this::movePanel;
            stripe.onHide = this::hidePanel;
        }
        owner.area().onMove = this::movePanel;
        owner.area().onHide = this::hidePanel;
        owner.dockDrag().onDrop = this::dropped;
        owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;
        owner.dockDrag().labels = this::stripeLabel;
        dock.addListener(docked);
        panelRequests = model.onPanelRequest(this::requested);
        paletteRequests = model.onPaletteRequest(this::requestedPalette);
        syncScopes();
        syncPanels();
        renderStripes();
        listening = model.onChanged(this::changed);
    }

    Contributions model() { return model; }

    /** The window's action for a contributed id, or null when the contributor removed it. */
    Action action(String id) { return actions.get(id); }

    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(entry.extension());
        return result;
    }

    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
        // A rebind that changes a toggle's digit renames its labelled stripe's label at once (spec 10.7); under the
        // icon rails every number is empty, so this never re-renders them.
        if (!numbers().equals(numbers)) renderStripes();
    }

    /** The digits the stripes last showed, by panel id. */
    private Map<String, java.util.OptionalInt> numbers = Map.of();

    /** Each docked panel's stripe digit under the window's keymap now. */
    private Map<String, java.util.OptionalInt> numbers() {
        var current = new LinkedHashMap<String, java.util.OptionalInt>();
        for (PanelInstance panel : panels.values()) current.put(panel.entry.id(), number(panel));
        return current;
    }

    /**
     * The digit a panel's label shows: its toggle's under the window's keymap, on labelled stripes only. The icon rails
     * show no digit, so under them every number is empty and a rebind never re-renders (and so never cancels a drag).
     */
    private java.util.OptionalInt number(PanelInstance panel) {
        if (!(owner.stripes() instanceof StripeSet.LabelledStripes)) return java.util.OptionalInt.empty();
        return StripeNumbers.of(owner.bindings().strokeFor(panel.entry.toggleActionId()));
    }

    /** A panel's stripe label, as a drag's gap measures it. */
    private String stripeLabel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null) return "";
        return StripeLabels.text(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), Slot.LEFT_TOP, false,
            panel.entry.badge(), number(panel)));
    }

    /** From a shortcut, the palette, a menu, the toolbar or a status item. */
    void invoke(String id) {
        if (dev.jasper.app.platform.WindowInput.blocked(owner)) return;
        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().blocksActions()) return;
        Optional<java.util.UUID> pane = Optional.ofNullable(owner.currentPane()).map(TerminalPane::id);
        model.action(id).ifPresent(entry -> entry.invoke(new Contributions.Invocation(owner.id(), pane)));
    }

    /** Whether the contributed action exists and is enabled, so its shortcut takes the key. */
    boolean enabled(String id) { return model.action(id).map(ActionEntry::enabled).orElse(false); }

    /** Whether focus is inside this window's instance of the panel. */
    boolean panelContains(String panelId, java.awt.Component focus) {
        PanelInstance panel = panels.get(panelId);
        return focus != null && panel != null && panel.component != null && SwingUtilities.isDescendingFrom(focus, panel.component);
    }

    private void renderStatus() { owner.status().setContributed(model.status(), this::action); }

    private void requestedPalette(Contributions.PaletteRequest request) {
        if (!request.windowId().equals(owner.id()) || owner.commandPalette() == null) return;
        owner.commandPalette().open(request.scopeId(), request.query().orElse(null), request.rowId().orElse(null));
    }

    /** Registers contributed scopes this window does not have yet and drops the ones that left the model. */
    private void syncScopes() {
        List<PaletteScope> current = model.scopes();
        for (PaletteScope scope : List.copyOf(scopes.keySet()))
            if (!current.contains(scope)) scopes.remove(scope).close();
        for (PaletteScope scope : current) {
            if (scopes.containsKey(scope)) continue;
            try { scopes.put(scope, owner.scopes().register(scope)); }
            catch (IllegalArgumentException clash) {
                LOG.log(System.Logger.Level.WARNING, "A contributed scope is not shown in this window: " + clash.getMessage());
            }
        }
    }

    private void requested(Contributions.PanelRequest request) {
        if (!request.windowId().equals(owner.id())) return;
        switch (request.op()) {
            case SHOW -> showPanel(request.panelId());
            case HIDE -> hidePanel(request.panelId());
            case TOGGLE -> togglePanel(request.panelId());
            case ACTIVATING_TOGGLE -> toggleAndActivate(request.panelId());
        }
    }

    private void togglePanel(String id) { if (panels.containsKey(id)) dock.toggle(id); }

    /** A toggle by a user gesture: a panel it shows becomes the active tool window under flush frames (plan 12 Rulings 18). */
    private void toggleAndActivate(String id) {
        if (!panels.containsKey(id)) return;
        dock.toggle(id);
        if (dock.visible(id)) owner.area().activate(id);
    }

    private void showPanel(String id) { if (panels.containsKey(id)) dock.show(id); }

    private void hidePanel(String id) { if (panels.containsKey(id)) dock.hide(id); }

    /**
     * A drop from the dock drag. Over a stripe the window moves and keeps its visibility; over the workspace it moves
     * and shows, and the slot's previously showing window hides but stays on its stripe. A workspace drop on the
     * window's own slot keeps its place in the group.
     */
    private void dropped(String id, DockDropResolver.Target target) {
        if (!panels.containsKey(id)) return;
        Slot slot = target.slot();
        if (target.onStripe()) {
            dock.move(id, slot, target.index().getAsInt());
            return;
        }
        dock.moveAndShow(id, slot, dock.dropIndex(id, slot));
    }

    /** From a Move to menu: the end of the target slot's group, keeping visibility. */
    private void movePanel(String id, Slot slot) {
        if (!panels.containsKey(id) || dock.slot(id).filter(slot::equals).isPresent()) return;
        dock.move(id, slot, dock.ids(slot).size());
    }

    /** The window opens at the saved sizes and ratios; nothing listens yet, so this saves nothing. */
    private void restoreSizes() {
        UiState.Dock saved = state.dock();
        dock.resize(Side.LEFT, saved.leftWidth());
        dock.resize(Side.RIGHT, saved.rightWidth());
        dock.resize(Side.BOTTOM, saved.bottomHeight());
        dock.setRatio(Side.LEFT, saved.leftRatio());
        dock.setRatio(Side.RIGHT, saved.rightRatio());
        dock.setRatio(Side.BOTTOM, saved.bottomRatio());
    }

    /**
     * Docks new panels where they were saved (a saved slot wins over the default) or at the end of their default
     * slot's group, showing those that showed (in Zen, remembering them for when it ends); undocks the panels that left
     * the model and discards their instances.
     */
    private void syncPanels() {
        Map<String, PanelEntry> current = new LinkedHashMap<>();
        for (PanelEntry entry : model.panels()) current.put(entry.id(), entry);
        for (String id : List.copyOf(panels.keySet())) {
            if (current.containsKey(id)) continue;
            PanelInstance gone = panels.remove(id);
            dock.remove(id);
            if (gone.site != null) gone.site.notifyClosed();
            owner.area().removeContent(id);
        }
        for (PanelEntry entry : current.values()) {
            if (panels.containsKey(entry.id())) continue;
            panels.put(entry.id(), new PanelInstance(entry));
            Optional<UiState.Panel> saved = state.panel(entry.id());
            Slot slot = saved.map(UiState.Panel::slot).orElse(entry.defaultSlot());
            boolean showed = saved.map(UiState.Panel::visible).orElse(false);
            // Remembered before docking: docking saves, and in Zen the saved visibility is the remembered one.
            if (showed && beforeZen != null) beforeZen.add(entry.id());
            dock.add(entry.id(), slot, insertionIndex(slot, saved.map(UiState.Panel::order).orElse(UiState.Panel.LAST)));
            if (showed && beforeZen == null) dock.show(entry.id());
        }
    }

    /** Where a panel goes back into its slot: before the panels saved after it. One never placed goes last. */
    private int insertionIndex(Slot slot, int order) {
        List<String> ids = dock.ids(slot);
        if (order >= UiState.Panel.LAST) return ids.size();
        int index = 0;
        for (String other : ids)
            if (state.panel(other).map(UiState.Panel::order).orElse(UiState.Panel.LAST) < order) index++;
        return index;
    }

    /**
     * The one place a dock change takes effect: build what now shows, lay out, render the stripes, tell the hidden
     * panels, return focus to the terminal if something hid, tell the shown panels, save. Focus returns before the
     * shown panels hear it, so a window that takes focus when it shows keeps it when it replaces another.
     */
    private void docked(DockState.Change change) {
        // A dock change mid-drag (a plugin unloading, a panel showing itself) ends the drag: what it drew renders again.
        owner.dockDrag().cancel();
        for (String id : change.shown()) build(id);
        if (change.layout()) owner.area().sync();
        if (change.stripes()) renderStripes();
        for (String id : change.hidden()) notifyVisibility(id, false);
        // Hiding a window by any route returns focus to the terminal.
        if (!change.hidden().isEmpty()) owner.focusSelected();
        for (String id : change.shown()) notifyVisibility(id, true);
        persist();
    }

    /**
     * Entering Zen hides the panels that show and remembers them; any panel opened in Zen then shows in its slot as
     * usual, without the rail. Leaving Zen hides those and shows the remembered panels again.
     */
    void zenChanged() {
        if (owner.zenMode() == (beforeZen != null)) return;
        if (owner.zenMode()) {
            beforeZen = new LinkedHashSet<>();
            for (Slot slot : Slot.values()) dock.active(slot).ifPresent(beforeZen::add);
            for (String id : List.copyOf(beforeZen)) dock.hide(id);
            return;
        }
        Set<String> restore = beforeZen;
        beforeZen = null;
        for (Slot slot : Slot.values()) dock.active(slot).filter(id -> !restore.contains(id)).ifPresent(dock::hide);
        for (String id : restore) showPanel(id);
    }

    /** Whether the saved layout has the panel showing: in Zen, whether it showed before Zen. */
    private boolean savedVisible(String id) { return beforeZen != null ? beforeZen.contains(id) : dock.visible(id); }

    /** Builds the panel's instance the first time it shows in this window; a failing factory yields a placeholder. */
    private void build(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.component != null) return;
        panel.site = new PanelSite(owner.id(), () -> showPanel(id), () -> hidePanel(id), () -> dock.visible(id), header -> owner.area().setHeader(id, header));
        JComponent built = null;
        try { built = panel.entry.factory().apply(panel.site); }
        catch (RuntimeException failure) {
            // A plugin's adapter turns its own failures into null, so an exception reaching here came from no adapter.
            LOG.log(System.Logger.Level.WARNING, "The panel " + id + " could not be built; showing a placeholder.", failure);
        }
        if (built == null) panel.site.setHeader(dev.jasper.app.contributions.PanelHeaderState.EMPTY);
        panel.component = built != null ? built : new JLabel("This panel could not be loaded.", SwingConstants.CENTER);
        if (built == null) dev.jasper.ui.swing.NativeStyles.installed().label((JLabel) panel.component, dev.jasper.ui.metrics.Typography.Role.LABEL,
            dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY, () -> dev.jasper.app.platform.ThemeZone.theme(panel.component));
        owner.area().setContent(id, panel.entry.title(), panel.component);
    }

    /** Tells the panel only what is still true and not yet told: a listener's own show or hide may have changed it. */
    private void notifyVisibility(String id, boolean visible) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.site == null || dock.visible(id) != visible || panel.notified == visible) return;
        panel.notified = visible;
        panel.site.notifyVisibility(visible);
    }

    /**
     * Saves every panel's place and the dock's sizes. A slot's saved order also keeps a place for its panels that are
     * not in this window right now (a plugin still loading, or turned off), so each goes back where it was; a panel
     * that never had a place keeps none.
     */
    private void persist() {
        for (Slot slot : Slot.values()) {
            List<String> merged = new ArrayList<>();
            for (String id : dock.ids(slot)) if (panels.containsKey(id)) merged.add(id);
            state.panels().entrySet().stream()
                .filter(entry -> entry.getValue().slot() == slot && entry.getValue().order() < UiState.Panel.LAST)
                .filter(entry -> !panels.containsKey(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().order()))
                .forEach(entry -> merged.add(Math.min(entry.getValue().order(), merged.size()), entry.getKey()));
            for (int i = 0; i < merged.size(); i++) {
                String id = merged.get(i);
                boolean visible = panels.containsKey(id) ? savedVisible(id) : state.panel(id).map(UiState.Panel::visible).orElse(false);
                state.putPanel(id, new UiState.Panel(slot, Math.min(i, UiState.Panel.LAST), visible));
            }
        }
        state.setRailVisible(owner.railVisible());
        state.setZenMode(owner.zenMode());
        state.setDock(new UiState.Dock(dock.size(Side.LEFT), dock.size(Side.RIGHT), dock.size(Side.BOTTOM),
            dock.ratio(Side.LEFT), dock.ratio(Side.RIGHT), dock.ratio(Side.BOTTOM)));
        state.save();
    }

    private void renderStripes() {
        owner.dockDrag().cancel();
        List<ToolStripe.Button> windows = new ArrayList<>();
        for (Slot slot : Slot.values())
            for (String id : dock.ids(slot)) {
                PanelInstance panel = panels.get(id);
                if (panel == null) continue;
                windows.add(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id),
                    panel.entry.badge(), number(panel)));
            }
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        // Every stripe keeps the windows of the slots it shows.
        owner.leftStripe().render(windows, railActions);
        owner.rightStripe().render(windows, List.of());
        owner.bottomStrip().ifPresent(strip -> strip.render(windows, List.of()));
        numbers = numbers();
        owner.syncRailVisibility();
    }

    /**
     * Gives each panel's stripe button its panel's current badge, without a render: a render rebuilds every button,
     * which would end a drag, and a badge changes neither the layout nor which windows there are. The stripe that does
     * not show a panel ignores it.
     */
    private void updateBadges() {
        for (PanelInstance panel : panels.values()) {
            for (DockStripe stripe : owner.stripes().all()) stripe.updateBadge(panel.entry.id(), panel.entry.badge());
        }
    }

    /** Panel content that is not showing is outside the window's component tree and must be updated by hand. */
    void refreshTheme() {
        for (PanelInstance panel : panels.values())
            if (panel.component != null && !dock.visible(panel.entry.id())) SwingUtilities.updateComponentTreeUI(panel.component);
    }

    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                boolean structural = syncActions();
                if (structural) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
                // Which actions the stripe shows changes only when one is added or removed, which rebuilds it. A title or
                // enabled change renames and repaints the buttons it has, so that it does not end a drag.
                if (structural) renderStripes();
                else for (DockStripe stripe : owner.stripes().all()) stripe.refreshActions();
            }
            case PANELS -> { syncPanels(); renderStripes(); }
            case RAIL -> renderStripes();
            case BADGES -> updateBadges();
            case SCOPES -> syncScopes();
            case TOOLBAR -> owner.chrome().renderContributedToolbar();
            case MENUS -> owner.chrome().renderContributedMenus();
            case STATUS -> renderStatus();
        }
    }

    /** Returns whether the set of actions changed, which is when shortcuts must be recomputed. */
    private boolean syncActions() {
        boolean structural = false;
        Map<String, ActionEntry> current = new LinkedHashMap<>();
        for (ActionEntry entry : model.actions()) current.put(entry.id(), entry);
        for (String id : List.copyOf(actions.keySet())) {
            if (current.containsKey(id)) continue;
            commands.remove(id).close();
            actions.remove(id).setEnabled(false);
            structural = true;
        }
        for (ActionEntry entry : current.values()) {
            Action action = actions.get(entry.id());
            if (action == null) {
                String id = entry.id();
                action = new AbstractAction(entry.title()) {
                    @Override public void actionPerformed(ActionEvent event) { invoke(id); }
                };
                if (entry.icon() != null) {
                    action.putValue(Command.ICON, entry.icon());
                    action.putValue(Action.SMALL_ICON, entry.icon());
                }
                actions.put(id, action);
                List<String> keywords = new ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
                keywords.addAll(entry.keywords());
                // The name is set before registration: a command needs a non-blank Action.NAME.
                commands.put(id, owner.commands().register(new Command(id, action, keywords)));
                structural = true;
            }
            action.putValue(Action.NAME, entry.title());
            action.putValue(Command.TITLE, entry.title());
            action.setEnabled(entry.enabled());
        }
        return structural;
    }

    @Override public void close() { close(true); }

    /**
     * As {@link #close()}. With {@code remember} false the window's layout is not saved first: the window is discarded
     * (it failed to compose, or it is the warm-up rehearsal's), so it must not become the last window closed.
     */
    void close(boolean remember) {
        if (listening == null) return;
        if (remember) persist();
        listening.close();
        listening = null;
        // Snapshot before teardown: the last window closed wins; disposal must not save panels as hidden.
        owner.dockDrag().cancel();
        owner.dockDrag().onDrop = (id, target) -> { };
        owner.dockDrag().icons = id -> null;
        owner.dockDrag().labels = id -> "";
        dock.removeListener(docked);
        commands.values().forEach(Subscription::close);
        commands.clear();
        scopes.values().forEach(Subscription::close);
        scopes.clear();
        if (paletteRequests != null) { paletteRequests.close(); paletteRequests = null; }
        actions.values().forEach(action -> action.setEnabled(false));
        if (panelRequests != null) { panelRequests.close(); panelRequests = null; }
        for (PanelInstance panel : panels.values()) if (panel.site != null) panel.site.notifyClosed();
        panels.clear();
        for (DockStripe stripe : owner.stripes().all()) {
            stripe.onToggle = id -> { };
            stripe.onMove = (id, slot) -> { };
            stripe.onHide = id -> { };
            stripe.render(List.of(), List.of());
        }
        owner.area().onMove = (id, slot) -> { };
        owner.area().onHide = id -> { };
        owner.syncRailVisibility();
        actions.clear();
        owner.chrome().connect(null);
        owner.status().setContributed(List.of(), id -> null);
    }
}
