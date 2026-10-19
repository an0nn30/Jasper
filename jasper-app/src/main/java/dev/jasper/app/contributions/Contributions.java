package dev.jasper.app.contributions;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.files.ChooserSource;

/**
 * What extensions have contributed, in registration order. Windows subscribe and re-render the part
 * that changed. A {@code BADGES} change updates the existing stripe buttons in place instead. Everything here is
 * EDT-only; callbacks into contributors are their concern to contain.
 */
public final class Contributions {
    private final ContentRegistry content = new ContentRegistry();
    public ContentRegistry content() { return content; }
    private final QuitGuards quitGuards = new QuitGuards();
    public QuitGuards quitGuards() { return quitGuards; }

    /**
     * Which part changed. {@code RAIL} is the rail actions' placement and {@code BADGES} a panel's unread mark, which a
     * window applies to its existing stripe buttons without a render.
     */
    public enum Kind { ACTIONS, TOOLBAR, MENUS, STATUS, PANELS, RAIL, BADGES, SCOPES }

    /** Where an action was invoked: the window, and the pane it concerns if the window has one. */
    public record Invocation(UUID windowId, Optional<UUID> paneId) {
        public Invocation {
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(paneId, "paneId");
        }
    }

    private static final System.Logger LOG = System.getLogger(Contributions.class.getName());
    private final Map<String, ActionEntry> actions = new LinkedHashMap<>();
    private final List<ToolbarEntry> toolbar = new ArrayList<>();
    private final List<MenuSection> menus = new ArrayList<>();
    private final List<StatusEntry> status = new ArrayList<>();
    private final List<Consumer<Kind>> listeners = new ArrayList<>();

    /** A request to show, hide or toggle a panel in one window; each window answers only its own. */
    public record PanelRequest(UUID windowId, String panelId, Op op) {
        public enum Op {
            SHOW, HIDE, TOGGLE,
            /** A toggle by the user's View action: a panel it shows becomes the active tool window under flush frames (Enterprise plan 12 Rulings 18). */
            ACTIVATING_TOGGLE
        }
    }

    private final Map<String, PanelEntry> panels = new LinkedHashMap<>();
    private final Map<String, ActionEntry> panelToggles = new LinkedHashMap<>();
    /** Single-element holders: the same action may be placed twice, and removal is by identity. */
    private final List<String[]> railActions = new ArrayList<>();
    private final List<Consumer<PanelRequest>> panelListeners = new ArrayList<>();
    private MenuSection panelsMenu;


    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
    }

    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords,
                                 Optional<String> defaultBinding, Consumer<Invocation> handler) {
        return addAction(id, title, iconOrNull, keywords, defaultBinding, Optional.empty(), KeyContext.GLOBAL, handler);
    }

    /** An action with a separate Linux and Windows default (empty: derived) and the context its shortcut applies in. */
    public ActionEntry addAction(String id, String title, Icon iconOrNull, List<String> keywords, Optional<String> defaultBinding,
                                 Optional<String> otherDefault, KeyContext context, Consumer<Invocation> handler) {
        requireEdt();
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(otherDefault, "otherDefault");
        Objects.requireNonNull(context, "context");
        if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
        var entry = new ActionEntry(this, id, title, iconOrNull, keywords, defaultBinding, otherDefault, context, handler);
        actions.put(id, entry);
        changed(Kind.ACTIONS);
        return entry;
    }

    public Subscription addToolbar(ToolbarEntry entry) {
        requireEdt();
        toolbar.add(Objects.requireNonNull(entry, "entry"));
        changed(Kind.TOOLBAR);
        // Identity removal: two equal records are still two toolbar controls.
        return new Subscription(() -> {
            for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == entry) { toolbar.remove(i); break; }
            changed(Kind.TOOLBAR);
        });
    }

    public MenuSection addMenuSection(MenuTarget target) {
        requireEdt();
        if (target.type() == MenuTarget.Type.TOP_LEVEL)
            for (MenuSection existing : menus)
                if (existing.target().type() == MenuTarget.Type.TOP_LEVEL && existing.target().key().equals(target.key()))
                    throw new IllegalArgumentException("Menu already exists: " + target.key());
        var section = new MenuSection(this, target);
        menus.add(section);
        changed(Kind.MENUS);
        return section;
    }

    public StatusEntry addStatus(String id, boolean left, int priority) {
        requireEdt();
        for (StatusEntry existing : status)
            if (existing.id().equals(id)) throw new IllegalArgumentException("Status item already exists: " + id);
        var entry = new StatusEntry(this, id, left, priority);
        status.add(entry);
        changed(Kind.STATUS);
        return entry;
    }

    public List<ActionEntry> actions() { return List.copyOf(actions.values()); }
    public Optional<ActionEntry> action(String id) { return Optional.ofNullable(actions.get(id)); }
    public List<ToolbarEntry> toolbar() { return List.copyOf(toolbar); }
    public List<MenuSection> menus() { return List.copyOf(menus); }

    /** Ascending priority; equal priorities keep registration order. */
    public List<StatusEntry> status() {
        return status.stream().sorted(Comparator.comparingInt(StatusEntry::priority)).toList();
    }

    public Subscription onChanged(Consumer<Kind> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    public PanelEntry addPanel(String id, String title, Icon icon, Slot defaultSlot,
                               Function<PanelSite, JComponent> factory) {
        return addPanel(id, title, icon, defaultSlot, Optional.empty(), factory);
    }

    /** A panel toggled by {@code toggleActionId} when present; otherwise the model registers {@code <id>.toggle}. */
    public PanelEntry addPanel(String id, String title, Icon icon, Slot defaultSlot, Optional<String> toggleActionId,
                               Function<PanelSite, JComponent> factory) {
        requireEdt();
        Objects.requireNonNull(factory, "factory");
        if (panels.containsKey(id)) throw new IllegalArgumentException("Panel already registered: " + id);
        String toggle = toggleActionId.orElse(id + ".toggle");
        var entry = new PanelEntry(this, id, title, icon, Objects.requireNonNull(defaultSlot, "defaultSlot"),
            toggle, toggleActionId.isPresent(), factory);
        panels.put(id, entry);
        if (toggleActionId.isEmpty())
            panelToggles.put(id, addAction(toggle, "Toggle " + title, icon, List.of("panel", "show", "hide"), Optional.empty(),
                invocation -> requestPanel(new PanelRequest(invocation.windowId(), id, PanelRequest.Op.ACTIVATING_TOGGLE))));
        rebuildPanelsMenu();
        changed(Kind.PANELS);
        return entry;
    }

    public List<PanelEntry> panels() { return List.copyOf(panels.values()); }

    /**
     * The app's View section listing every panel's toggle ("Tool Windows"), while there are panels.
     *
     * @return the section, or empty while there are no panels
     */
    public Optional<MenuSection> toolWindowsSection() { return Optional.ofNullable(panelsMenu); }

    public Subscription addRailAction(String actionId) {
        requireEdt();
        String[] placed = {Objects.requireNonNull(actionId, "actionId")};
        railActions.add(placed);
        changed(Kind.RAIL);
        return new Subscription(() -> {
            for (int i = 0; i < railActions.size(); i++) if (railActions.get(i) == placed) { railActions.remove(i); break; }
            changed(Kind.RAIL);
        });
    }

    public List<String> railActions() { return railActions.stream().map(placed -> placed[0]).toList(); }

        /** Asks one window to open its palette. */
        public record PaletteRequest(UUID windowId, String scopeId, Optional<String> query, Optional<String> rowId) {
            public PaletteRequest {
                Objects.requireNonNull(windowId, "windowId"); Objects.requireNonNull(scopeId, "scopeId");
                Objects.requireNonNull(query, "query"); Objects.requireNonNull(rowId, "rowId");
            }
        }

        private final List<PaletteScope> scopes = new ArrayList<>();

        private final List<ChooserSource> fileSources = new ArrayList<>();
        private final List<Consumer<PaletteRequest>> paletteListeners = new ArrayList<>();

        /**
         * A scope every window registers in its palette, one instance shared by all of them. Ids are
         * unique among contributed scopes; a window also refuses a clash with one of its built-in ids.
         */
        public Subscription addScope(PaletteScope scope) {
            requireEdt();
            String id = PaletteScope.requireValidId(Objects.requireNonNull(scope, "scope").id());
            if (scope.verbs().isEmpty()) throw new IllegalArgumentException("Scope needs at least one verb: " + id);
            for (PaletteScope existing : scopes)
                if (existing.id().equals(id)) throw new IllegalArgumentException("Scope already contributed: " + id);
            scopes.add(scope);
            changed(Kind.SCOPES);
            return new Subscription(() -> { if (scopes.remove(scope)) changed(Kind.SCOPES); });
        }

        public List<PaletteScope> scopes() { return List.copyOf(scopes); }

        /** A place the file chooser offers besides the local disk; ids are unique. */
        public Subscription addFileSource(ChooserSource source) {
            requireEdt();
            Objects.requireNonNull(source, "source");
            for (ChooserSource existing : fileSources)
                if (existing.id().equals(source.id())) throw new IllegalArgumentException("File source already contributed: " + source.id());
            fileSources.add(source);
            return new Subscription(() -> fileSources.remove(source));
        }

        public List<ChooserSource> fileSources() { return List.copyOf(fileSources); }

        public Subscription onPaletteRequest(Consumer<PaletteRequest> listener) {
            requireEdt();
            paletteListeners.add(Objects.requireNonNull(listener, "listener"));
            return new Subscription(() -> paletteListeners.remove(listener));
        }

        public void requestPalette(PaletteRequest request) {
            requireEdt();
            for (Consumer<PaletteRequest> listener : List.copyOf(paletteListeners)) {
                try { listener.accept(request); }
                catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A palette request listener failed", failure); }
            }
        }

    public Subscription onPanelRequest(Consumer<PanelRequest> listener) {
        requireEdt();
        panelListeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> panelListeners.remove(listener));
    }

    public void requestPanel(PanelRequest request) {
        requireEdt();
        for (Consumer<PanelRequest> listener : List.copyOf(panelListeners)) {
            try { listener.accept(request); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel request listener failed", failure); }
        }
    }

    void remove(PanelEntry entry) {
        if (!panels.remove(entry.id(), entry)) return;
        ActionEntry toggle = panelToggles.remove(entry.id());
        if (toggle != null) toggle.close();
        rebuildPanelsMenu();
        changed(Kind.PANELS);
    }

    /** One app-owned View section, "Tool Windows", listing every panel's toggle action; gone when there are no panels. */
    private void rebuildPanelsMenu() {
        if (panels.isEmpty()) {
            if (panelsMenu != null) { panelsMenu.close(); panelsMenu = null; }
            return;
        }
        if (panelsMenu == null) panelsMenu = addMenuSection(MenuTarget.standard(MenuTarget.Slot.VIEW));
        List<MenuEntry> toggles = new ArrayList<>();
        for (PanelEntry panel : panels.values()) toggles.add(new MenuEntry.Item(panel.toggleActionId()));
        panelsMenu.set(List.of(new MenuEntry.Submenu("Tool Windows", toggles)));
    }

    void remove(ActionEntry entry) { if (actions.remove(entry.id(), entry)) changed(Kind.ACTIONS); }
    void remove(MenuSection section) { if (menus.remove(section)) changed(Kind.MENUS); }
    void remove(StatusEntry entry) { if (status.remove(entry)) changed(Kind.STATUS); }

    void changed(Kind kind) {
        for (Consumer<Kind> listener : List.copyOf(listeners)) {
            try { listener.accept(kind); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A contributions listener failed", failure); }
        }
    }
}
