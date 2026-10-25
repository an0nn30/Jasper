package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.OverlaySpec;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.PanelFactory;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.Panels;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusProgress;
import dev.jasper.sdk.ui.StatusProgressState;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.Windows;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** One fake plugin's recorded chrome contributions, with the same rules as the application. */
final class FakeUi {
    static final class Action {
        final ActionSpec spec; final Consumer<ActionContext> handler; String title; boolean enabled = true;
        Action(ActionSpec spec, Consumer<ActionContext> handler) { this.spec = spec; this.handler = handler; this.title = spec.title(); }
    }

    static final class Menu implements PluginMenu {
        final String target; final String title; final Menu parent; final FakeUi ui;
        final List<Object> children = new ArrayList<>();
        Menu(FakeUi ui, Menu parent, String target, String title) { this.ui = ui; this.parent = parent; this.target = target; this.title = title; }

        @Override public Subscription add(String actionId) {
            ui.context.requireOpen();
            ui.requireOwn(actionId);
            Object leaf = new String[]{actionId};
            children.add(leaf);
            return () -> children.remove(leaf);
        }
        @Override public Subscription addSeparator() {
            ui.context.requireOpen();
            Object leaf = new String[]{null};
            children.add(leaf);
            return () -> children.remove(leaf);
        }
        @Override public PluginMenu submenu(String submenuTitle) {
            ui.context.requireOpen();
            if (submenuTitle == null || submenuTitle.isBlank()) throw new IllegalArgumentException("A submenu needs a title");
            var menu = new Menu(ui, this, target, submenuTitle);
            children.add(menu);
            return menu;
        }
        @Override public void clear() { ui.context.requireOpen(); children.clear(); }
        @Override public void close() {
            if (parent == null) { ui.sections.remove(this); ui.topLevelIds.remove(target); }
            else parent.children.remove(this);
        }

        void render(List<String> lines, String indent) {
            for (Object child : children) {
                if (child instanceof Menu menu) { lines.add(indent + "submenu:" + menu.title); menu.render(lines, indent + "  "); }
                else {
                    String actionId = ((String[]) child)[0];
                    if (actionId == null) lines.add(indent + "---");
                    else if (ui.host.actionExists(actionId)) lines.add(indent + "item:" + actionId);
                }
            }
        }
    }

    static final class Status implements StatusItem, StatusProgress {
        final StatusItemSpec spec; final FakeUi ui;
        StatusProgressState progress;
        String text = ""; String tooltip; String actionId; boolean visible = true; boolean closed;
        Status(FakeUi ui, StatusItemSpec spec) { this.ui = ui; this.spec = spec; }
        @Override public void update(StatusProgressState value) {
            if (closed) return;
            java.util.Objects.requireNonNull(value);
            if (value.actionId() != null) ui.requireOwn(value.actionId());
            if (value.secondaryActionId() != null) ui.requireOwn(value.secondaryActionId());
            progress = value; text = value.text(); tooltip = value.detail(); actionId = value.actionId();
        }
        @Override public void setText(String value) { if (!closed) text = value == null ? "" : value.replace("\r", "").replace("\n", " "); }
        @Override public void setIcon(String icon) { if(!closed) ui.context.kit().validateIcon(icon); }
        @Override public void setTooltip(String value) { if (!closed) tooltip = value; }
        @Override public void setAction(String value) { if (value != null) ui.requireOwn(value); if (!closed) actionId = value; }
        @Override public void setVisible(boolean value) { if (!closed) visible = value; }
        @Override public void close() { closed = true; ui.status.remove(this); }
    }

    final List<FakeContent> contents = new ArrayList<>();
    final List<java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>>> quitGuards = new ArrayList<>();
    /** This plugin's restorers by kind (workspace tabs spec 13). */
    final java.util.Map<String, dev.jasper.sdk.ui.TabRestorer> restorers = new java.util.LinkedHashMap<>();
    java.util.function.BiFunction<List<dev.jasper.sdk.ui.ContentTab>,Boolean, java.util.concurrent.CompletableFuture<Boolean>> contentGuard = (tabs,batch) -> java.util.concurrent.CompletableFuture.completedFuture(true);
    java.util.concurrent.CompletableFuture<Boolean> pendingContent;
    List<FakeContent> pendingTabs = List.of();
    java.util.concurrent.CompletableFuture<Boolean> closeContent(List<FakeContent> tabs, boolean remove) {
        if (pendingContent != null) return tabs.equals(pendingTabs) ? pendingContent : java.util.concurrent.CompletableFuture.completedFuture(false);
        if (tabs.stream().anyMatch(tab -> tab.pins > 0)) return java.util.concurrent.CompletableFuture.completedFuture(false);
        var result = new java.util.concurrent.CompletableFuture<Boolean>(); pendingContent = result; pendingTabs = List.copyOf(tabs);
        try { contentGuard.apply(new ArrayList<dev.jasper.sdk.ui.ContentTab>(tabs), !remove).whenComplete((allowed, error) -> {
            if (error == null && Boolean.TRUE.equals(allowed) && remove) for(var tab:List.copyOf(tabs))tab.close();
            pendingContent = null; pendingTabs = List.of(); result.complete(error == null && Boolean.TRUE.equals(allowed));
        }); } catch(RuntimeException failure) { pendingContent=null;pendingTabs=List.of();result.complete(false); }
        return result;
    }
    final FakePluginHost host;
    final FakePluginContext context;
    final Map<String, Action> actions = new LinkedHashMap<>();
    final List<ToolbarItem> toolbar = new ArrayList<>();
    /** Each toolbar view's built views, per Jasper window; empty where the factory threw or the tree was refused. */
    final Map<ToolbarItem.View, Map<UUID, Optional<FakeKitView>>> toolbarBuilt = new java.util.IdentityHashMap<>();
    /** A toolbar view's mount: the toolbar of one Jasper window. */
    record ToolbarMount(UUID windowId) { }
    final List<Menu> sections = new ArrayList<>();
    final List<String> topLevelIds = new ArrayList<>();
    final List<Status> status = new ArrayList<>();

    FakeUi(FakePluginHost host, FakePluginContext context) { this.host = host; this.context = context; }

    private String pluginId() { return context.plugin().id(); }

    void requireOwn(String actionId) {
        if (!actions.containsKey(actionId))
            throw new IllegalArgumentException(actionId + " is not an action registered by " + pluginId());
    }

    private void requireNamespace(String id, String what) {
        if (!id.startsWith(pluginId() + "."))
            throw new IllegalArgumentException(what + " id must start with " + pluginId() + ".: " + id);
    }

    void closeAll() {
        quitGuards.clear();
        for (var content : List.copyOf(contents)) content.close();
        for (var item : status) item.closed = true;
        for (var built : toolbarBuilt.values()) for (var view : built.values()) view.ifPresent(FakeKitView::close);
        toolbarBuilt.clear();
        host.splitItems.removeIf(item -> item.pluginId().equals(pluginId()));
        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();
        for (Panel panel : List.copyOf(panels)) if (panel.instance != null) panel.instance.close();
        panels.clear(); railActions.clear();
        for(var popup:List.copyOf(popovers))popup.close();
        for (FakeWindow window : List.copyOf(windows)) window.close();
    }

    boolean invoke(String actionId, UUID windowId, UUID paneIdOrNull) {
        Action action = actions.get(actionId);
        if (action == null || !action.enabled) return false;
        WindowHandle window = context.terminals.windowHandle(windowId);
        Optional<PaneHandle> pane = Optional.ofNullable(paneIdOrNull).map(context.terminals::paneHandle);
        try {
            action.handler.accept(new ActionContext() {
                @Override public WindowHandle window() { return window; }
                @Override public Optional<PaneHandle> pane() { return pane; }
            });
        } catch (RuntimeException | LinkageError failure) { host.recordFailure(pluginId() + " action " + actionId + ": " + failure); }
        return true;
    }

    /** One of a split item's two actions, as the application registers it: no default, the words the palette finds it by. */
    private Action splitAction(dev.jasper.sdk.ui.SplitItemSpec spec, String id, String side,
                               java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener,
                               dev.jasper.sdk.ui.TabPlacement placement) {
        var keywords = new ArrayList<>(List.of("split", side.toLowerCase(java.util.Locale.ROOT), "new", "pane"));
        for (String word : spec.label().toLowerCase(java.util.Locale.ROOT).split(" ")) if (!word.isBlank()) keywords.add(word.strip());
        // An item that asks first ends its actions' titles with an ellipsis, as the application's do.
        var action = new Action(new ActionSpec(id, "Split " + side + " with " + spec.label() + (spec.asks() ? "…" : ""), spec.icon(),
            keywords, Optional.empty()),
            invoked -> opener.accept(invoked.window(), placement));
        actions.put(id, action);
        return action;
    }

    Actions actions() {
        return (spec, handler) -> {
            context.requireOpen();
            java.util.Objects.requireNonNull(handler, "handler");
            requireNamespace(spec.id(), "An action");
            spec.icon().ifPresent(context.kit()::validateIcon);
            spec.context().id().ifPresent(owned -> requireNamespace(owned, "A panel context"));
            if (actions.containsKey(spec.id())) throw new IllegalArgumentException("Action already registered: " + spec.id());
            var action = new Action(spec, handler);
            actions.put(spec.id(), action);
            return new PluginAction() {
                @Override public void setEnabled(boolean enabled) { action.enabled = enabled; }
                @Override public void setTitle(String title) {
                    if (title == null || title.isBlank()) throw new IllegalArgumentException("An action needs a title");
                    action.title = title;
                }
                @Override public void close() { actions.remove(spec.id(), action); }
            };
        };
    }

    Toolbar toolbar() {
        return item -> {
            context.requireOpen();
            switch (item) {
                case ToolbarItem.Button button -> requireOwn(button.actionId());
                case ToolbarItem.Dropdown dropdown -> { context.kit().validateIcon(dropdown.icon());dropdown.actionIds().forEach(this::requireOwn); }
                // A view's tree is admitted when a window builds it, and on every update after that.
                case ToolbarItem.View view -> { }
            }
            toolbar.add(item);
            return () -> {
                for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == item) { toolbar.remove(i); break; }
                if (item instanceof ToolbarItem.View view) {
                    var built = toolbarBuilt.remove(view);
                    if (built != null) for (var each : built.values()) each.ifPresent(FakeKitView::close);
                }
            };
        };
    }

    /** UI thread: the window's toolbar views in toolbar order, each built once for the window, as it builds them when it opens. */
    List<FakeKitView> toolbarViews(UUID windowId) {
        List<FakeKitView> views = new ArrayList<>();
        for (ToolbarItem item : List.copyOf(toolbar)) {
            if (!(item instanceof ToolbarItem.View view)) continue;
            var built = toolbarBuilt.computeIfAbsent(view, key -> new LinkedHashMap<>());
            if (!built.containsKey(windowId)) built.put(windowId, buildToolbarView(view, windowId));
            built.get(windowId).ifPresent(views::add);
        }
        return views;
    }

    /** Closes the window's toolbar views, as the window does when it closes. */
    void closeToolbarViews(UUID windowId) {
        for (var built : toolbarBuilt.values()) {
            var view = built.remove(windowId);
            if (view != null) view.ifPresent(FakeKitView::close);
        }
    }

    /** One window's view, admitted and mounted; empty, with the failure recorded, when the factory or the admission failed. */
    private Optional<FakeKitView> buildToolbarView(ToolbarItem.View item, UUID windowId) {
        try {
            var view = context.kit().requireOwned(java.util.Objects.requireNonNull(item.factory().create(context.terminals.windowHandle(windowId)), "view"));
            view.mount(new ToolbarMount(windowId));
            try {
                ToolbarViewAdmission.require(view.spec());
                view.hostSpec = spec -> { ToolbarViewAdmission.require(spec); return spec; };
            } catch (RuntimeException refused) { view.close(); throw refused; }
            return Optional.of(view);
        } catch (RuntimeException | LinkageError failure) {
            host.recordFailure(pluginId() + " toolbar view: " + failure);
            return Optional.empty();
        }
    }

    Menus menus() {
        return new Menus() {
            @Override public PluginMenu standard(StandardMenu menu) { return section(menu.name(), ""); }
            @Override public PluginMenu create(String menuId, String title) {
                context.requireOpen();
                if (menuId == null || !PluginInfo.validId(menuId)) throw new IllegalArgumentException("Not a namespaced menu id: " + menuId);
                requireNamespace(menuId, "A menu");
                if (title == null || title.isBlank()) throw new IllegalArgumentException("A menu needs a title");
                if (topLevelIds.contains("top:" + menuId)) throw new IllegalArgumentException("Menu already exists: " + menuId);
                topLevelIds.add("top:" + menuId);
                return section("top:" + menuId, title);
            }
            @Override public PluginMenu terminalContext() { return section("context", ""); }
        };
    }

    private Menu section(String target, String title) {
        context.requireOpen();
        var menu = new Menu(this, null, target, title);
        sections.add(menu);
        return menu;
    }

    StatusBar statusBar() {
        return new StatusBar() {
            @Override public StatusItem add(StatusItemSpec spec) { return addStatus(spec); }
            @Override public StatusProgress addProgress(StatusItemSpec spec) {
                var item = addStatus(spec);
                item.update(new StatusProgressState("", "", "", java.util.OptionalDouble.empty(), null, null));
                return item;
            }
        };
    }

    private Status addStatus(StatusItemSpec spec) {
        context.requireOpen();
        requireNamespace(spec.id(), "A status item");
        for (Status existing : status)
            if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Status item already exists: " + spec.id());
        var item = new Status(this, spec);
        status.add(item);
        return item;
    }

    /** A registered panel and its instance in the main window, while one is built. */
    static final class Panel { final PanelSpec spec; final PanelFactory factory; PanelInstance instance; Panel(PanelSpec spec, PanelFactory factory) { this.spec = spec; this.factory = factory; } }

    record SurfaceMount(FakeWindow surface) { }

    /** A plugin's dialog, or a {@code Windows.overlay}. */
    final class FakeWindow implements PluginDialog {
        final String id; final FakeWindow owner; String title; boolean shown; boolean closed; JComponent content;
        final List<BooleanSupplier> guards = new ArrayList<>(); final List<Runnable> closedHandlers = new ArrayList<>();
        FakeWindow(String id, String title, FakeWindow owner) { this.id = id; this.title = title; this.owner = owner; }
        final SurfaceMount bodyMount = new SurfaceMount(this);
        FakeKitView mounted;
        boolean attachedSheet;
        /** The presentation a dialog asked for; plain windows and overlays have none. */
        DialogSpec.Presentation presentation;
        /** The hue of the square a dialog asked for before its title; NONE for none. */
        dev.jasper.sdk.ui.Nodes.Hue swatch = dev.jasper.sdk.ui.Nodes.Hue.NONE;
        java.util.function.BooleanSupplier ownerLive = () -> true;
        @Override public void setContent(dev.jasper.sdk.ui.UiView content) {
            context.requireOpen();var next=context.kit().requireOwned(content);
            if(closed)throw new IllegalStateException("Surface is closed");next.mount(bodyMount);
            if(mounted==next)return;var old=mounted;this.content=next.component;mounted=next;if(old!=null)old.close();
        }
        @Override public void show() {
            if (closed) return;
            if (attachedSheet && !ownerLive.getAsBoolean()) { close(); throw new IllegalArgumentException("Attached sheets need a shown owner"); }
            shown = true;
        }
        @Override public Optional<Path> chooseFile(String title, Optional<Path> initialPath) {
            context.requireOpen();
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A file chooser needs a title");
            java.util.Objects.requireNonNull(initialPath, "initialPath");
            if (!shown || closed) throw new IllegalStateException("File selection needs a shown, open window");
            var result=host.takeFileSelection();return closed || context.state == FakePluginContext.State.CLOSED ? Optional.empty() : result;
        }
        @Override public void toFront() { }
        @Override public void setTitle(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("A window needs a title");
            if (!closed) title = value;
        }
        @Override public Subscription onClosing(BooleanSupplier guard) { guards.add(guard); return () -> guards.remove(guard); }
        @Override public Subscription onClosed(Runnable handler) { if (closed) { handler.run(); return () -> {}; } closedHandlers.add(handler); return () -> closedHandlers.remove(handler); }
        boolean mayClose() {
            for (BooleanSupplier guard : List.copyOf(guards)) {
                try { if (!guard.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " closing guard: " + failure); }
            }
            return true;
        }
        boolean requestClose() {
            if (id.equals("overlay")) return false;
            if (!mayClose()) return false;
            close();
            return true;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if(mounted!=null){mounted.close();mounted=null;}
            windows.remove(this);
            for (FakeWindow other : List.copyOf(windows)) if (other.owner == this) other.close();
            for (Runnable handler : List.copyOf(closedHandlers)) {
                try { handler.run(); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " window closed: " + failure); }
            }
            content = null;
        }
    }

    final List<Panel> panels = new ArrayList<>();
    final List<String[]> railActions = new ArrayList<>();
    final List<FakeWindow> windows = new ArrayList<>();

    final class PanelInstance implements PanelHost {
        final UUID windowId;
        JComponent content;
        boolean visible = true;
        boolean done;
        dev.jasper.sdk.ui.PanelHeader header = dev.jasper.sdk.ui.PanelHeader.EMPTY;
        /** Where the panel is: its spec's anchor and half, docked, until a placement is driven. */
        dev.jasper.sdk.ui.PanelPlacement placement;
        final List<Consumer<Boolean>> visibility = new ArrayList<>();
        final List<Consumer<dev.jasper.sdk.ui.PanelPlacement>> placements = new ArrayList<>();
        final List<Runnable> closed = new ArrayList<>();
        PanelInstance(UUID windowId, PanelSpec spec) {
            this.windowId = windowId;
            placement = new dev.jasper.sdk.ui.PanelPlacement(spec.defaultAnchor(), spec.split(), dev.jasper.sdk.ui.PanelMode.DOCKED);
        }
        @Override public WindowHandle window() { return context.terminals.windowHandle(windowId); }
        @Override public void setHeader(dev.jasper.sdk.ui.PanelHeader value) {
            java.util.Objects.requireNonNull(value);
            if (done) return;
            value.actions().forEach(action -> context.kit().validateIcon(action.icon()));
            header = new dev.jasper.sdk.ui.PanelHeader(value.detail(), value.active(), value.actions().stream()
                .map(action -> new dev.jasper.sdk.ui.PanelHeader.Action(action.label(), action.icon(), () -> {
                    if (done) return;
                    try { action.invoke().run(); }
                    catch (RuntimeException | LinkageError failure) { host.recordFailure(pluginId() + " panel header action: " + failure); }
                })).toList());
        }
        @Override public void show() { setVisible(true); }
        @Override public void hide() { setVisible(false); }
        void setVisible(boolean value) {
            if (done || visible == value) return;
            visible = value;
            for (var listener : List.copyOf(visibility)) {
                try { listener.accept(value); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " panel visibility: " + failure); }
            }
        }
        @Override public boolean visible() { return !done && visible; }
        @Override public Subscription onVisibility(Consumer<Boolean> handler) { visibility.add(handler); return () -> visibility.remove(handler); }
        @Override public dev.jasper.sdk.ui.PanelPlacement placement() { return placement; }
        @Override public Subscription onPlacement(Consumer<dev.jasper.sdk.ui.PanelPlacement> handler) {
            java.util.Objects.requireNonNull(handler, "handler");
            placements.add(handler);
            return () -> placements.remove(handler);
        }
        @Override public Subscription onClosed(Runnable handler) { closed.add(handler); return () -> closed.remove(handler); }
        void close() {
            if (done) return;
            done = true;
            header = dev.jasper.sdk.ui.PanelHeader.EMPTY;
            for (var handler : List.copyOf(closed)) {
                try { handler.run(); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " panel closed: " + failure); }
            }
            closed.clear(); visibility.clear(); placements.clear();
        }
    }

    Panels panels() {
        return new Panels() {
          @Override public Subscription register(PanelSpec spec, PanelFactory factory) {
            context.requireOpen();
            java.util.Objects.requireNonNull(factory, "factory");
            requireNamespace(spec.id(), "A panel");
            context.kit().validateIcon(spec.icon());
            spec.toggleActionId().ifPresent(FakeUi.this::requireOwn);
            for (Panel existing : panels) if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Panel already registered: " + spec.id());
            var panel = new Panel(spec, factory);
            panels.add(panel);
            return () -> { if (panels.remove(panel) && panel.instance != null) { panel.instance.close(); panel.instance = null; } };
          }
          @Override public void toggle(String panelId, WindowHandle window) {
            context.requireOpen();
            requireNamespace(panelId, "A panel");
            Panel panel = panels.stream().filter(p -> p.spec.id().equals(panelId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Panel not registered: " + panelId));
            if (!window.isOpen()) return;
            // Panels live in the main window (workspace tabs spec 14): a child window's toggle raises the main window and
            // shows the panel there, as the application's does (workspace plan 4a's final review); the main window's
            // toggles it.
            if (!window.isMain()) host.workspace.main().ifPresent(main -> host.workspace.openRequests.add("front|" + main.id));
            if (panel.instance == null) openPanel(panelId);
            else if (!window.isMain()) panel.instance.show();
            else panel.instance.setVisible(!panel.instance.visible());
          }
        };
    }

    /** Builds the panel in the main window, or shows it there; null without a main window or when the factory fails. */
    JComponent openPanel(String panelId) {
        var main = host.workspace.main().orElse(null);
        if (main == null) return null;
        for (Panel panel : panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var existing = panel.instance;
            if (existing != null) { existing.show(); return existing.content; }
            try {
                var instance = new PanelInstance(main.id, panel.spec);
                var view=context.kit().requireOwned(panel.factory.create(instance));view.mount(instance);instance.onClosed(view::close);instance.content = view.component;
                if (instance.content != null) panel.instance = instance;
                return instance.content;
            } catch (RuntimeException | LinkageError failure) {
                host.recordFailure(pluginId() + " panel " + panelId + ": " + failure);
                return null;
            }
        }
        return null;
    }

    Rail rail() {
        return actionId -> {
            context.requireOpen();
            requireOwn(actionId);
            String[] placed = {actionId};
            railActions.add(placed);
            return () -> { for (int i = 0; i < railActions.size(); i++) if (railActions.get(i) == placed) { railActions.remove(i); break; } };
        };
    }

    private boolean liveChooserOwner(WindowOwner owner) {
        if (owner instanceof FakeContent content) return contents.contains(content) && !content.closed;
        return owner instanceof WindowHandle terminal && context.terminals.ownsOpenWindow(terminal);
    }
    private List<java.nio.file.Path> choosePaths(WindowOwner owner, String title, java.util.Optional<java.nio.file.Path> initial) {
        context.requireOpen(); java.util.Objects.requireNonNull(title); java.util.Objects.requireNonNull(initial);
        if (!liveChooserOwner(owner)) throw new IllegalArgumentException("Picker requires an open Jasper window or content tab from this host");
        FakeContent document = owner instanceof FakeContent tab ? tab : null;
        if (document != null) document.pins++;
        try {
            var paths = host.takePathSelection();
            return context.state == FakePluginContext.State.CLOSED || !liveChooserOwner(owner) ? List.of() : paths;
        } finally { if (document != null) document.pins--; }
    }

    private FakeKitView.Attachment requireAnchor(dev.jasper.sdk.ui.UiAnchor value){
        if(!(value instanceof FakeKitView.Attachment anchor)||anchor.host!=host||!anchor.isOpen())throw new IllegalArgumentException("Foreign or expired anchor");return anchor;
    }
    final class FakePopover implements dev.jasper.sdk.ui.PluginPopover {
        final FakeKitView.Attachment anchor;final List<Runnable> listeners=new ArrayList<>();
        FakeKitView content;boolean closed,shown;final Subscription lifetime;
        FakePopover(FakeKitView.Attachment anchor){this.anchor=anchor;lifetime=anchor.onClosed(this::close);}
        public void setContent(dev.jasper.sdk.ui.UiView view){context.requireOpen();if(closed)throw new IllegalStateException("Popover closed");var next=context.kit().requireOwned(view);next.mount(this);if(next==content)return;var previous=content;content=next;if(previous!=null)previous.close();}
        public void show(){context.requireOpen();if(closed||!anchor.isOpen())throw new IllegalArgumentException("Expired anchor");if(content==null)throw new IllegalStateException("Missing popup content");shown=true;}
        public Subscription onClosed(Runnable handler){java.util.Objects.requireNonNull(handler);if(closed){handler.run();return () -> {}; }listeners.add(handler);return () -> listeners.remove(handler);}
        public void close(){if(closed)return;closed=true;if(lifetime!=null)lifetime.close();if(content!=null)content.close();popovers.remove(this);for(var listener:List.copyOf(listeners))listener.run();listeners.clear();}
    }
    final List<FakePopover> popovers=new ArrayList<>();

    /**
     * The Split lists' choices (tabs and panes spec 2.10): the terminal, then every split item, the bundled plugins' first.
     * Unguarded, as a closed content tab's {@code split} reads them and does nothing.
     */
    List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
        var choices = new ArrayList<dev.jasper.sdk.ui.SplitChoice>(List.of(dev.jasper.sdk.ui.SplitChoice.TERMINAL));
        host.splitItems.stream()
            .sorted(java.util.Comparator.comparing((FakePluginHost.SplitItem item) -> !host.bundledPlugins.contains(item.pluginId())))
            .forEach(item -> choices.add(new dev.jasper.sdk.ui.SplitChoice(item.spec().label(), item.spec().rightAction(),
                item.spec().downAction(), item.spec().asks())));
        return List.copyOf(choices);
    }

    Windows windows() {
        return new Windows() {
            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                context.requireOpen();java.util.Objects.requireNonNull(placement,"placement");var view=context.kit().requireOwned(content);
                for(var entry:contents)if(entry.key.equals(spec.key())){entry.select();return entry;}
                view.mount(spec);view.route="WORKSPACE";try{var tab=(FakeContent)openComponent(spec,owner,view.component,placement);tab.view=view;tab.onClosed(view::close);return tab;}catch(RuntimeException | Error failure){view.route="CHROME";view.unmount(spec);throw failure;}
            }
            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                context.requireOpen();
                for(var entry:contents)if(entry.key.equals(spec.key())){entry.select();return entry;}
                if (owner instanceof FakeContent tab) {
                    if (!contents.contains(tab)) throw new IllegalArgumentException("Closed or foreign content owner");
                    owner = tab.owner;
                }
                if (owner != null && (!(owner instanceof WindowHandle handle) || !context.terminals.ownsWindow(handle)))
                    throw new IllegalArgumentException("Content requires a workspace owner");
                // A saved tab being restored comes back in the window being restored, whatever window or placement it named,
                // placed and not shown: Jasper shows it where it was saved (workspace tabs spec 13).
                var slot = host.workspace.takeSlot(context);
                if (slot.isPresent()) {
                    var window = slot.get().window;
                    var restored = new FakeContent(FakeUi.this, spec, context.terminals.windowHandle(window.id), component,
                        host.workspace.placeTab(window, dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT, spec.title()));
                    contents.add(restored);
                    slot.get().taken = restored.tab;
                    return restored;
                }
                // Meanwhile the window being restored takes no other tab: it goes where an owner-less one goes (workspace
                // plan 6b Rulings 24).
                if (owner != null && host.workspace.slot != null && ((WindowHandle) owner).id().equals(host.workspace.slot.window.id)) owner = null;
                if (owner == null || !((WindowHandle)owner).isOpen()) {
                    // The main window, as the application restores it first when none is open (workspace tabs spec 6.4).
                    var window = host.workspace.main().orElseGet(host.workspace::addWindow);
                    owner = context.terminals.windowHandle(window.id);
                }
                // A plugin's content is a content tab of its window (workspace tabs spec 14), placed as asked: in a new child
                // window of its own, opened for it, the owner's window untouched.
                var shown = placement == dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW ? host.workspace.addWindow()
                    : host.workspace.window(((WindowHandle) owner).id()).orElseThrow();
                if (placement == dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW) owner = context.terminals.windowHandle(shown.id);
                // A split placement opens a new pane right after the window's selected item; NEW_TAB opens a new tab.
                var beside = placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT || placement == dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN ? shown.selected : null;
                var entry=new FakeContent(FakeUi.this,spec,owner,component,host.workspace.addTab(shown,dev.jasper.sdk.terminal.TabHandle.Kind.CONTENT,spec.title(),beside));
                contents.add(entry);entry.select();
                return entry;
            }
            @Override public Subscription splitItem(dev.jasper.sdk.ui.SplitItemSpec spec,
                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener) {
                context.requireOpen();
                java.util.Objects.requireNonNull(spec, "spec");
                java.util.Objects.requireNonNull(opener, "opener");
                requireNamespace(spec.id(), "A split item");
                spec.icon().ifPresent(context.kit()::validateIcon);
                if (host.splitItems.stream().anyMatch(item -> item.spec().id().equals(spec.id())))
                    throw new IllegalArgumentException("Split item already registered: " + spec.id());
                for (String id : List.of(spec.rightAction(), spec.downAction()))
                    if (actions.containsKey(id)) throw new IllegalArgumentException("Action already registered: " + id);
                var right = splitAction(spec, spec.rightAction(), "Right", opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                var down = splitAction(spec, spec.downAction(), "Down", opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
                var listed = new FakePluginHost.SplitItem(pluginId(), spec);
                host.splitItems.add(listed);
                return () -> {
                    actions.remove(spec.rightAction(), right);
                    actions.remove(spec.downAction(), down);
                    // By identity: after a re-register, a repeated close leaves the new item listed.
                    host.splitItems.removeIf(item -> item == listed);
                };
            }
            @Override public List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
                context.requireOpen();
                return FakeUi.this.splitChoices();
            }
            @Override public Subscription onTabsClosing(java.util.function.BiFunction<List<dev.jasper.sdk.ui.ContentTab>,Boolean,java.util.concurrent.CompletableFuture<Boolean>> guard) {
                context.requireOpen();contentGuard=guard;return ()->{if(contentGuard==guard)contentGuard=(tabs,batch)->java.util.concurrent.CompletableFuture.completedFuture(true);};
            }
            @Override public Subscription onQuitting(java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {
                context.requireOpen();
                java.util.Objects.requireNonNull(guard, "guard");
                quitGuards.add(guard);
                return () -> quitGuards.remove(guard);
            }
            @Override public Subscription restorer(String kind, dev.jasper.sdk.ui.TabRestorer restorer) {
                context.requireOpen();
                java.util.Objects.requireNonNull(restorer, "restorer");
                // The kind's rules are the restore state's.
                new dev.jasper.sdk.RestoreState(kind, java.util.Map.of());
                if (restorers.putIfAbsent(kind, restorer) != null) throw new IllegalArgumentException("This plugin already restores " + kind);
                return () -> restorers.remove(kind, restorer);
            }

            @Override public List<java.nio.file.Path> chooseFiles(WindowOwner owner, String title, java.util.Optional<java.nio.file.Path> initial) {
                return choosePaths(owner, title, initial);
            }
            @Override public java.util.Optional<java.nio.file.Path> chooseDirectory(WindowOwner owner, String title, java.util.Optional<java.nio.file.Path> initial) {
                return choosePaths(owner, title, initial).stream().findFirst();
            }
            @Override public java.util.Optional<java.nio.file.Path> chooseSaveFile(WindowOwner owner, String title, java.util.Optional<java.nio.file.Path> initial) {
                return choosePaths(owner, title, initial).stream().findFirst();
            }
            @Override public List<dev.jasper.sdk.files.FileLocation> choose(dev.jasper.sdk.files.FileChoice choice) {
                context.requireOpen();
                java.util.Objects.requireNonNull(choice, "choice");
                WindowOwner owner = choice.owner();
                if (!liveChooserOwner(owner)) throw new IllegalArgumentException("Picker requires an open Jasper window or content tab from this host");
                var chosen = host.takeFileChoice(choice);
                return context.state == FakePluginContext.State.CLOSED || !liveChooserOwner(owner) ? List.of() : chosen;
            }
            @Override public PluginDialog overlay(OverlaySpec spec) {
                context.requireOpen();
                if (!context.terminals.ownsOpenWindow(spec.owner()))
                    throw new IllegalArgumentException("An overlay needs an open Jasper window from this host");
                if (host.overlays.containsKey(spec.owner().id())) throw new IllegalStateException("This window already has an overlay");
                var overlay = new FakeWindow("overlay", spec.title(), null);
                host.overlays.put(spec.owner().id(), overlay);
                overlay.onClosed(() -> host.overlays.remove(spec.owner().id(), overlay));
                windows.add(overlay);
                return overlay;
            }
            @Override public dev.jasper.sdk.ui.PluginPopover popover(dev.jasper.sdk.ui.UiAnchor value){context.requireOpen();var popup=new FakePopover(requireAnchor(value));popovers.add(popup);return popup;}
            @Override public PluginDialog dialog(DialogSpec spec) {
                context.requireOpen();
                WindowOwner owner = spec.owner();
                FakeWindow parent = null;
                if(owner instanceof dev.jasper.sdk.ui.UiAnchor value){var anchor=requireAnchor(value);parent=anchor.ownerSurface();
                } else if (!(owner instanceof WindowHandle) && !(owner instanceof FakeContent content && contents.contains(content)))
                    throw new IllegalArgumentException("A dialog's owner must be a Jasper window, a content tab or an anchor from this host");
                var dialog = new FakeWindow("dialog", spec.title(), parent);
                dialog.attachedSheet = spec.presentation() == DialogSpec.Presentation.ATTACHED_SHEET;
                dialog.presentation = spec.presentation();
                dialog.swatch = spec.swatch();
                dialog.ownerLive = () -> owner instanceof dev.jasper.sdk.ui.UiAnchor anchor ? anchor.isOpen() : liveChooserOwner(owner);
                if(owner instanceof dev.jasper.sdk.ui.UiAnchor value){var registration=value.onClosed(dialog::close);dialog.onClosed(registration::close);}
                if (owner instanceof FakeContent content) {
                    if (spec.modal()) content.pins++;
                    var registration = content.onClosed(dialog::close);
                    dialog.onClosed(() -> { registration.close(); if (spec.modal()) content.pins--; });
                }
                windows.add(dialog);
                return dialog;
            }
        };
    }

    Variant variant() { return host.variant(); }
}
