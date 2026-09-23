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
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.WindowSpec;
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
import javax.swing.Icon;
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

    static final class Status implements StatusItem {
        final StatusItemSpec spec; final FakeUi ui;
        String text = ""; String tooltip; String actionId; boolean visible = true; boolean closed;
        Status(FakeUi ui, StatusItemSpec spec) { this.ui = ui; this.spec = spec; }
        @Override public void setText(String value) { if (!closed) text = value == null ? "" : value.replace("\r", "").replace("\n", " "); }
        @Override public void setIcon(Icon icon) { }
        @Override public void setTooltip(String value) { if (!closed) tooltip = value; }
        @Override public void setAction(String value) { if (value != null) ui.requireOwn(value); if (!closed) actionId = value; }
        @Override public void setVisible(boolean value) { if (!closed) visible = value; }
        @Override public void close() { closed = true; ui.status.remove(this); }
    }

    final FakePluginHost host;
    final FakePluginContext context;
    final Map<String, Action> actions = new LinkedHashMap<>();
    final List<ToolbarItem> toolbar = new ArrayList<>();
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
        actions.clear(); toolbar.clear(); sections.clear(); topLevelIds.clear(); status.clear();
        for (Panel panel : List.copyOf(panels)) panel.instances.values().forEach(PanelInstance::close);
        panels.clear(); railActions.clear();
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

    Actions actions() {
        return (spec, handler) -> {
            context.requireOpen();
            java.util.Objects.requireNonNull(handler, "handler");
            requireNamespace(spec.id(), "An action");
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
                case ToolbarItem.Dropdown dropdown -> dropdown.actionIds().forEach(this::requireOwn);
            }
            toolbar.add(item);
            return () -> { for (int i = 0; i < toolbar.size(); i++) if (toolbar.get(i) == item) { toolbar.remove(i); break; } };
        };
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
        return spec -> {
            context.requireOpen();
            requireNamespace(spec.id(), "A status item");
            for (Status existing : status)
                if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Status item already exists: " + spec.id());
            var item = new Status(this, spec);
            status.add(item);
            return item;
        };
    }

    static final class Panel { final PanelSpec spec; final PanelFactory factory; final Map<UUID, PanelInstance> instances = new LinkedHashMap<>(); Panel(PanelSpec spec, PanelFactory factory) { this.spec = spec; this.factory = factory; } }

    final class FakeWindow implements PluginWindow, PluginDialog {
        final String id; final FakeWindow owner; String title; boolean shown; boolean closed;
        final List<BooleanSupplier> guards = new ArrayList<>(); final List<Runnable> closedHandlers = new ArrayList<>();
        FakeWindow(String id, String title, FakeWindow owner) { this.id = id; this.title = title; this.owner = owner; }
        @Override public void setContent(JComponent content) { }
        @Override public void show() { if (!closed) shown = true; }
        @Override public Optional<Path> chooseFile(String title, Optional<Path> initialPath) {
            context.requireOpen();
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A file chooser needs a title");
            java.util.Objects.requireNonNull(initialPath, "initialPath");
            if (!shown || closed) throw new IllegalStateException("File selection needs a shown, open window");
            return host.takeFileSelection();
        }
        @Override public void toFront() { }
        @Override public void setTitle(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("A window needs a title");
            if (!closed) title = value;
        }
        @Override public Subscription onClosing(BooleanSupplier guard) { guards.add(guard); return () -> guards.remove(guard); }
        @Override public Subscription onClosed(Runnable handler) { closedHandlers.add(handler); return () -> closedHandlers.remove(handler); }
        boolean requestClose() {
            if (id.equals("overlay")) return false;
            for (BooleanSupplier guard : List.copyOf(guards)) {
                try { if (!guard.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " closing guard: " + failure); }
            }
            close();
            return true;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            windows.remove(this);
            for (FakeWindow other : List.copyOf(windows)) if (other.owner == this) other.close();
            for (Runnable handler : List.copyOf(closedHandlers)) {
                try { handler.run(); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " window closed: " + failure); }
            }
        }
    }

    final List<Panel> panels = new ArrayList<>();
    final List<String[]> railActions = new ArrayList<>();
    final List<FakeWindow> windows = new ArrayList<>();

    final class PanelInstance implements PanelHost {
        final UUID windowId;
        JComponent content;
        boolean visible = true;
        final List<Consumer<Boolean>> visibility = new ArrayList<>();
        final List<Runnable> closed = new ArrayList<>();
        PanelInstance(UUID windowId) { this.windowId = windowId; }
        @Override public WindowHandle window() { return context.terminals.windowHandle(windowId); }
        @Override public void show() { setVisible(true); }
        @Override public void hide() { setVisible(false); }
        void setVisible(boolean value) {
            if (visible == value) return;
            visible = value;
            for (var listener : List.copyOf(visibility)) {
                try { listener.accept(value); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " panel visibility: " + failure); }
            }
        }
        @Override public boolean visible() { return visible; }
        @Override public Subscription onVisibility(Consumer<Boolean> handler) { visibility.add(handler); return () -> visibility.remove(handler); }
        @Override public Subscription onClosed(Runnable handler) { closed.add(handler); return () -> closed.remove(handler); }
        void close() {
            for (var handler : List.copyOf(closed)) {
                try { handler.run(); } catch (RuntimeException failure) { host.recordFailure(pluginId() + " panel closed: " + failure); }
            }
            closed.clear(); visibility.clear();
        }
    }

    Panels panels() {
        return new Panels() {
          @Override public Subscription register(PanelSpec spec, PanelFactory factory) {
            context.requireOpen();
            java.util.Objects.requireNonNull(factory, "factory");
            requireNamespace(spec.id(), "A panel");
            for (Panel existing : panels) if (existing.spec.id().equals(spec.id())) throw new IllegalArgumentException("Panel already registered: " + spec.id());
            var panel = new Panel(spec, factory);
            panels.add(panel);
            return () -> { if (panels.remove(panel)) { panel.instances.values().forEach(PanelInstance::close); panel.instances.clear(); } };
          }
          @Override public void toggle(String panelId, WindowHandle window) {
            context.requireOpen();
            requireNamespace(panelId, "A panel");
            Panel panel = panels.stream().filter(p -> p.spec.id().equals(panelId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Panel not registered: " + panelId));
            if (!window.isOpen()) return;
            var instance = panel.instances.get(window.id());
            if (instance == null) openPanel(panelId, window.id());
            else instance.setVisible(!instance.visible());
          }
        };
    }

    JComponent openPanel(String panelId, UUID windowId) {
        for (Panel panel : panels) {
            if (!panel.spec.id().equals(panelId)) continue;
            var existing = panel.instances.get(windowId);
            if (existing != null) { existing.show(); return existing.content; }
            try {
                var instance = new PanelInstance(windowId);
                instance.content = panel.factory.create(instance);
                if (instance.content != null) panel.instances.put(windowId, instance);
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

    Windows windows() {
        return new Windows() {
            @Override public PluginWindow create(WindowSpec spec) {
                context.requireOpen();
                requireNamespace(spec.id(), "A window");
                if (spec.singleton()) for (FakeWindow open : windows) if (open.id.equals(spec.id())) return open;
                var window = new FakeWindow(spec.id(), spec.title(), null);
                windows.add(window);
                return window;
            }
            @Override public PluginDialog overlay(OverlaySpec spec) {
                context.requireOpen();
                if (!context.terminals.ownsOpenWindow(spec.owner()))
                    throw new IllegalArgumentException("An overlay needs an open terminal window from this host");
                if (host.overlays.containsKey(spec.owner().id())) throw new IllegalStateException("This window already has an overlay");
                var overlay = new FakeWindow("overlay", spec.title(), null);
                host.overlays.put(spec.owner().id(), overlay);
                overlay.onClosed(() -> host.overlays.remove(spec.owner().id(), overlay));
                windows.add(overlay);
                return overlay;
            }
            @Override public PluginDialog dialog(DialogSpec spec) {
                context.requireOpen();
                WindowOwner owner = spec.owner();
                FakeWindow parent = null;
                if (owner instanceof FakeWindow window) {
                    if (window.closed || !windows.contains(window)) throw new IllegalArgumentException("A dialog needs an open owner window");
                    parent = window;
                } else if (!(owner instanceof WindowHandle))
                    throw new IllegalArgumentException("A dialog's owner must be an open window of this plugin or a terminal window");
                var dialog = new FakeWindow("dialog", spec.title(), parent);
                windows.add(dialog);
                return dialog;
            }
        };
    }

    static Icon blankIcon() {
        return new Icon() {
            @Override public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) { }
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
        };
    }

    Variant variant() { return host.variant(); }
}
