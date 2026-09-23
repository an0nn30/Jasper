package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import dev.jasper.app.palette.PaletteScope;

/**
 * This window's Swing side of the application-wide contributions model: one {@link Action} per
 * contributed action, registered as a palette command and bound to its shortcut, kept in step with the
 * model. Chrome and status rendering hang off the same change notifications. EDT only.
 */
final class WindowContributions implements AutoCloseable {
    private final WindowContent owner;
    private final Contributions model;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Subscription> commands = new LinkedHashMap<>();
    private Subscription listening;
        private static final System.Logger LOG = System.getLogger(WindowContributions.class.getName());
        private final Map<PaletteScope, Subscription> scopes = new LinkedHashMap<>();
        private Subscription paletteRequests;

    private static final class PanelInstance {
        final PanelEntry entry;
        PanelRegion region;
        boolean visible;
        int size;
        PanelSite site;
        JComponent component;
        PanelInstance(PanelEntry entry) { this.entry = entry; }
    }

    private final UiState state;
    private final Map<String, PanelInstance> panels = new LinkedHashMap<>();
    private Subscription panelRequests;

    WindowContributions(WindowContent owner, Contributions model, UiState state) {
        this.owner = owner;
        this.model = model;
        this.state = state;
        syncActions();
        owner.chrome().connect(this);
        renderStatus();
        owner.rail().onToggle = this::togglePanel;
        owner.rail().onMove = this::movePanel;
        panelRequests = model.onPanelRequest(this::requested);
        paletteRequests = model.onPaletteRequest(this::requestedPalette);
        syncScopes();
        syncPanels();
        renderRail();
        listening = model.onChanged(this::changed);
    }

    Contributions model() { return model; }

    /** The window's action for a contributed id, or null when the contributor removed it. */
    Action action(String id) { return actions.get(id); }

    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(new KeyBindings.Extension(entry.id(), entry.defaultBinding()));
        return result;
    }

    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
    }

    /** From a shortcut, the palette, a menu, the toolbar or a status item. */
    void invoke(String id) {
        if (dev.jasper.app.platform.WindowInput.blocked(owner)) return;
        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().isOpen()) return;
        Optional<java.util.UUID> pane = Optional.ofNullable(owner.currentPane()).map(TerminalPane::id);
        model.action(id).ifPresent(entry -> entry.invoke(new Contributions.Invocation(owner.id(), pane)));
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
        }
    }

    private void togglePanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null) return;
        if (panel.visible) hidePanel(id); else showPanel(id);
    }

    /** Adds instances for new panels, restoring their saved place, and discards instances of removed panels. */
    private void syncPanels() {
        Map<String, PanelEntry> current = new LinkedHashMap<>();
        for (PanelEntry entry : model.panels()) current.put(entry.id(), entry);
        for (String id : List.copyOf(panels.keySet())) {
            if (current.containsKey(id)) continue;
            PanelInstance gone = panels.remove(id);
            if (gone.visible) owner.regions().hide(gone.region);
            if (gone.site != null) gone.site.notifyClosed();
        }
        for (PanelEntry entry : current.values()) {
            if (panels.containsKey(entry.id())) continue;
            var panel = new PanelInstance(entry);
            var saved = state.panel(entry.id());
            panel.region = saved.map(value -> PanelRegion.valueOf(value.region())).orElse(entry.defaultRegion());
            panel.size = saved.map(UiState.Panel::size).orElse(owner.regions().size(panel.region));
            panels.put(entry.id(), panel);
            if (saved.map(UiState.Panel::visible).orElse(false)) showPanel(entry.id());
        }
    }

    void showPanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.visible) return;
        for (PanelInstance other : panels.values())
            if (other != panel && other.visible && other.region == panel.region) hidePanel(other.entry.id());
        if (panel.component == null) {
            panel.site = new PanelSite(owner.id(), () -> showPanel(id), () -> hidePanel(id), () -> panel.visible);
            JComponent built = null;
            try { built = panel.entry.factory().apply(panel.site); }
            catch (RuntimeException failure) { /* The contributor's adapter has logged it; the window only needs a placeholder. */ }
            panel.component = built != null ? built : new JLabel("This panel could not be loaded.", SwingConstants.CENTER);
        }
        owner.regions().show(panel.region, panel.component, panel.size);
        panel.visible = true;
        persist(panel);
        renderRail();
        panel.site.notifyVisibility(true);
    }

    void hidePanel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || !panel.visible) return;
        panel.size = owner.regions().size(panel.region);
        owner.regions().hide(panel.region);
        panel.visible = false;
        persist(panel);
        renderRail();
        panel.site.notifyVisibility(false);
        if (owner.currentTab() != null) owner.currentTab().focusTerminal();
    }

    void movePanel(String id, PanelRegion region) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.region == region) return;
        if (panel.visible) {
            // A move is not a change of visibility, so the instance hears nothing; only the region changes.
            for (PanelInstance other : panels.values())
                if (other != panel && other.visible && other.region == region) hidePanel(other.entry.id());
            owner.regions().hide(panel.region);
            panel.region = region;
            panel.size = owner.regions().size(region);
            owner.regions().show(region, panel.component, panel.size);
        } else {
            panel.region = region;
            panel.size = owner.regions().size(region);
        }
        persist(panel);
        renderRail();
    }

    private void persist(PanelInstance panel) {
        state.putPanel(panel.entry.id(), new UiState.Panel(panel.region.name(), panel.visible, Math.clamp(panel.size, 80, 4000)));
        state.save();
    }

    private void renderRail() {
        List<WindowRail.PanelButton> buttons = new ArrayList<>();
        for (PanelInstance panel : panels.values())
            buttons.add(new WindowRail.PanelButton(panel.entry.id(), panel.entry.title(), panel.entry.icon(), panel.region, panel.visible));
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        owner.rail().render(buttons, railActions);
        owner.syncRailVisibility();
    }

    /** Panel content that is not showing is outside the window's component tree and must be updated by hand. */
    void refreshTheme() {
        for (PanelInstance panel : panels.values())
            if (panel.component != null && !panel.visible) SwingUtilities.updateComponentTreeUI(panel.component);
    }

    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                if (syncActions()) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
                renderRail();
            }
            case PANELS -> { syncPanels(); renderRail(); }
            case RAIL -> renderRail();
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

    @Override public void close() {
        if (listening == null) return;
        listening.close();
        listening = null;
        commands.values().forEach(Subscription::close);
        commands.clear();
        scopes.values().forEach(Subscription::close);
        scopes.clear();
        if (paletteRequests != null) { paletteRequests.close(); paletteRequests = null; }
        actions.values().forEach(action -> action.setEnabled(false));
        if (panelRequests != null) { panelRequests.close(); panelRequests = null; }
        for (PanelInstance panel : panels.values()) {
            if (panel.visible) { panel.size = owner.regions().size(panel.region); state.putPanel(panel.entry.id(),
                new UiState.Panel(panel.region.name(), true, Math.clamp(panel.size, 80, 4000))); }
            if (panel.site != null) panel.site.notifyClosed();
        }
        state.save();
        panels.clear();
        owner.rail().onToggle = id -> { }; owner.rail().onMove = (id, region) -> { };
        owner.rail().render(List.of(), List.of());
        owner.syncRailVisibility();
        actions.clear();
        owner.chrome().connect(null);
        owner.status().setContributed(List.of(), id -> null);
    }
}
