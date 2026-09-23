package dev.jasper.app.plugins;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.OverlaySpec;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.Panels;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusProgress;
import dev.jasper.sdk.ui.StatusProgressState;
import dev.jasper.app.contributions.ProgressState;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.WindowSpec;
import dev.jasper.sdk.ui.WindowSurface;
import dev.jasper.sdk.ui.Windows;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;

/**
 * One plugin's chrome contributions, adapted onto the app-native model. It enforces what the model
 * cannot know: ids belong to this plugin's namespace, placements name this plugin's own actions,
 * registration happens on the UI thread through an open context, and handlers are contained.
 */
final class HostedUi {
    private record Context(WindowHandle window, Optional<PaneHandle> pane) implements ActionContext { }

    private final String pluginId;
    private final Contributions model;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final BooleanSupplier open;
    private final ClassLoader loader;
    private final Supplier<Variant> variant;
    private final Function<Consumer<Variant>, Subscription> themeSubscriber;
    private final AuxiliaryWindows windows;
    private final HostedTerminals terminals;
    private final Set<String> ownActions = new HashSet<>();
    private final List<Runnable> closers = new ArrayList<>();

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals) {
        this.pluginId = pluginId; this.model = model; this.containment = containment; this.ui = ui; this.onUi = onUi;
        this.open = open; this.loader = loader; this.variant = variant; this.themeSubscriber = themeSubscriber;
        this.windows = windows;
        this.terminals = terminals;
    }

    /** Registration needs an open context and the UI thread. */
    void guard(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        requireUi(what);
    }

    private void requireUi(String what) {
        if (!onUi.getAsBoolean()) throw new IllegalStateException(what + " must be called on the UI thread: " + pluginId);
    }

    void requireOwn(String actionId) {
        if (!ownActions.contains(actionId))
            throw new IllegalArgumentException(actionId + " is not an action registered by " + pluginId);
    }

    private void requireNamespace(String id, String what) {
        if (!id.startsWith(pluginId + "."))
            throw new IllegalArgumentException(what + " id must start with " + pluginId + ".: " + id);
    }

    /** Idempotent and safe from any thread: off the UI thread the removal is posted to it. */
    Subscription subscription(Runnable removal) {
        var closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (onUi.getAsBoolean()) removal.run(); else ui.accept(removal);
        };
    }

    /**
     * A top-level registration that {@link #closeAll} also removes. Menu leaves are not tracked: a
     * menu rebuilt many times must not grow this list, and closing its section removes them anyway.
     */
    Subscription tracked(Runnable removal) {
        closers.add(removal);
        return subscription(() -> { closers.remove(removal); removal.run(); });
    }

    /** UI thread: removes whatever the plugin left registered, newest first. Removals are idempotent. */
    void closeAll() {
        List<Runnable> pending = new ArrayList<>(closers);
        closers.clear();
        for (int i = pending.size() - 1; i >= 0; i--) pending.get(i).run();
        ownActions.clear();
    }

    Actions actions() {
        return (spec, handler) -> {
            guard("register");
            java.util.Objects.requireNonNull(handler, "handler");
            requireNamespace(spec.id(), "An action");
            ActionEntry entry = model.addAction(spec.id(), spec.title(), spec.icon().orElse(null), spec.keywords(),
                spec.defaultBinding(), invocation -> containment.run(pluginId, "action " + spec.id(), () ->
                    handler.accept(new Context(terminals.windowHandle(invocation.windowId()), invocation.paneId().map(terminals::paneHandle)))));
            ownActions.add(spec.id());
            Subscription removal = tracked(() -> { ownActions.remove(spec.id()); entry.close(); });
            return new PluginAction() {
                @Override public void setEnabled(boolean enabled) { requireUi("setEnabled"); entry.setEnabled(enabled); }
                @Override public void setTitle(String title) { requireUi("setTitle"); entry.setTitle(title); }
                @Override public void close() { removal.close(); }
            };
        };
    }

    Toolbar toolbar() {
        return item -> {
            guard("add");
            ToolbarEntry entry = switch (item) {
                case ToolbarItem.Button button -> { requireOwn(button.actionId()); yield new ToolbarEntry.Button(button.actionId()); }
                case ToolbarItem.Dropdown dropdown -> {
                    dropdown.actionIds().forEach(this::requireOwn);
                    yield new ToolbarEntry.Dropdown(dropdown.icon(), dropdown.title(), dropdown.actionIds());
                }
            };
            var registration = model.addToolbar(entry);
            return tracked(registration::close);
        };
    }

    Menus menus() {
        return new Menus() {
            @Override public PluginMenu standard(StandardMenu menu) {
                guard("standard");
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.valueOf(menu.name()))));
            }
            @Override public PluginMenu create(String menuId, String title) {
                guard("create");
                if (menuId == null || !dev.jasper.sdk.PluginInfo.validId(menuId))
                    throw new IllegalArgumentException("Not a namespaced menu id: " + menuId);
                requireNamespace(menuId, "A menu");
                if (title == null || title.isBlank()) throw new IllegalArgumentException("A menu needs a title");
                // The model rejects a duplicate top-level id and frees it again when the menu closes.
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.topLevel(menuId, title)));
            }
            @Override public PluginMenu terminalContext() {
                guard("terminalContext");
                return HostedMenu.root(HostedUi.this, model.addMenuSection(MenuTarget.terminalContext()));
            }
        };
    }

    StatusBar statusBar() {
        return new StatusBar() {
          @Override public StatusItem add(StatusItemSpec spec) {
            guard("add");
            requireNamespace(spec.id(), "A status item");
            StatusEntry entry = model.addStatus(spec.id(), spec.side() == Side.LEFT, spec.priority());
            Subscription removal = tracked(entry::close);
            return new StatusItem() {
                @Override public void setText(String text) { requireUi("setText"); entry.setText(text); }
                @Override public void setIcon(Icon icon) { requireUi("setIcon"); entry.setIcon(icon); }
                @Override public void setTooltip(String text) { requireUi("setTooltip"); entry.setTooltip(text); }
                @Override public void setAction(String actionId) {
                    requireUi("setAction");
                    if (actionId != null) requireOwn(actionId);
                    entry.setActionId(actionId);
                }
                @Override public void setVisible(boolean visible) { requireUi("setVisible"); entry.setVisible(visible); }
                @Override public void close() { removal.close(); }
            };
          }
          @Override public StatusProgress addProgress(StatusItemSpec spec) {
            guard("addProgress");
            requireNamespace(spec.id(), "A status item");
            StatusEntry entry = model.addStatus(spec.id(), spec.side() == Side.LEFT, spec.priority());
            entry.setProgress(new ProgressState("", "", "", java.util.OptionalDouble.empty(), null, null));
            var closed = new AtomicBoolean();
            Subscription removal = tracked(() -> { closed.set(true); entry.close(); });
            return new StatusProgress() {
                @Override public void update(StatusProgressState state) {
                    requireUi("update");
                    if (closed.get()) return;
                    java.util.Objects.requireNonNull(state, "state");
                    if (state.actionId() != null) requireOwn(state.actionId());
                    if (state.secondaryActionId() != null) requireOwn(state.secondaryActionId());
                    entry.setProgress(new ProgressState(state.text(), state.detail(), state.accessibleDescription(),
                        state.fraction(), state.actionId(), state.secondaryActionId()));
                }
                @Override public void setVisible(boolean value) { requireUi("setVisible"); if (!closed.get()) entry.setVisible(value); }
                @Override public void close() { closed.set(true); removal.close(); }
            };
          }
        };
    }

    Appearance appearance() {
        return new Appearance() {
            @Override public Variant variant() { return variant.get(); }
            @Override public Subscription onChanged(Consumer<Variant> handler) {
                guard("onChanged");
                return themeSubscriber.apply(java.util.Objects.requireNonNull(handler, "handler"));
            }
            @Override public Icon icon(dev.jasper.sdk.ui.IconName name) {
                return AppIcons.named(java.util.Objects.requireNonNull(name, "name").name());
            }
            @Override public Icon icon(String svgResourcePath) { return AppIcons.themed(loader, svgResourcePath); }
            @Override public Icon icon(String modernSvgResourcePath, dev.jasper.sdk.ui.OldGnomeIcon retroIcon) {
                java.util.Objects.requireNonNull(retroIcon, "retroIcon");
                return AppIcons.skin(loader, modernSvgResourcePath, retroIcon.name());
            }
        };
    }

    /** Wraps an application subscription so closing it is contained to the UI thread like every other registration. */
    private Subscription wrap(dev.jasper.app.lifecycle.Subscription registration) { return subscription(registration::close); }

    Panels panels() {
        return new Panels() {
          @Override public Subscription register(dev.jasper.sdk.ui.PanelSpec spec, dev.jasper.sdk.ui.PanelFactory factory) {
            guard("register");
            java.util.Objects.requireNonNull(factory, "factory");
            requireNamespace(spec.id(), "A panel");
            var entry = model.addPanel(spec.id(), spec.title(), spec.icon(), PanelRegion.valueOf(spec.defaultAnchor().name()), site -> {
                JComponent[] built = {null};
                containment.run(pluginId, "panel " + spec.id(), () -> built[0] = factory.create(host(site)));
                return built[0];
            });
            return tracked(entry::close);
          }
          @Override public void toggle(String panelId, WindowHandle window) {
            guard("toggle");
            requireNamespace(panelId, "A panel");
            if (model.panels().stream().noneMatch(panel -> panel.id().equals(panelId)))
                throw new IllegalArgumentException("Panel not registered: " + panelId);
            model.requestPanel(new Contributions.PanelRequest(window.id(), panelId, Contributions.PanelRequest.Op.TOGGLE));
          }
        };
    }

    private PanelHost host(PanelSite site) {
        return new PanelHost() {
            @Override public WindowHandle window() { return terminals.windowHandle(site.windowId()); }
            @Override public void show() { requireUi("show"); site.show(); }
            @Override public void hide() { requireUi("hide"); site.hide(); }
            @Override public boolean visible() { return site.visible(); }
            @Override public Subscription onVisibility(Consumer<Boolean> handler) {
                requireUi("onVisibility");
                return wrap(site.onVisibility(value -> containment.run(pluginId, "panel visibility", () -> handler.accept(value))));
            }
            @Override public Subscription onClosed(Runnable handler) {
                requireUi("onClosed");
                return wrap(site.onClosed(() -> containment.run(pluginId, "panel closed", handler)));
            }
        };
    }

    Rail rail() {
        return actionId -> {
            guard("add");
            requireOwn(actionId);
            var registration = model.addRailAction(actionId);
            return tracked(registration::close);
        };
    }

    /** One SDK view of an application surface; windows and dialogs differ only in their SDK type. */
    private class Surface implements WindowSurface {
        final AuxiliarySurface surface;
        Surface(AuxiliarySurface surface) { this.surface = surface; }
        @Override public void setContent(JComponent content) { requireUi("setContent"); surface.setContent(content); }
        @Override public void show() { requireUi("show"); surface.show(); }
        @Override public Optional<Path> chooseFile(String title, Optional<Path> initialPath) {
            guard("chooseFile");
            return surface.chooseFile(title, initialPath);
        }
        @Override public void toFront() { requireUi("toFront"); surface.toFront(); }
        @Override public void setTitle(String title) { requireUi("setTitle"); if (!surface.closed()) surface.setTitle(title); }
        @Override public Subscription onClosing(BooleanSupplier guard) {
            requireUi("onClosing");
            return wrap(surface.onClosing(() -> {
                boolean[] allowed = {true};
                containment.run(pluginId, "closing guard", () -> allowed[0] = guard.getAsBoolean());
                return allowed[0];
            }));
        }
        @Override public Subscription onClosed(Runnable handler) {
            requireUi("onClosed");
            return wrap(surface.onClosed(() -> containment.run(pluginId, "window closed", handler)));
        }
        @Override public void close() { subscription(surface::close).close(); }
    }

    private final class OwnedWindow extends Surface implements PluginWindow { OwnedWindow(AuxiliarySurface surface) { super(surface); } }
    private final class OwnedDialog extends Surface implements PluginDialog { OwnedDialog(AuxiliarySurface surface) { super(surface); } }

    private final java.util.Map<AuxiliarySurface, OwnedWindow> ownedWindows = new java.util.HashMap<>();

    private java.util.List<Path> choosePaths(WindowOwner owner, String title, Optional<Path> initial, boolean directory) {
        guard("choosePaths");
        java.util.Objects.requireNonNull(title); java.util.Objects.requireNonNull(initial);
        var cancelled = new AtomicBoolean();
        var cancelAction = new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
        Runnable cancel = () -> { if (cancelled.compareAndSet(false, true)) cancelAction.get().run(); };
        dev.jasper.app.windows.PathChoice choice;
        Subscription ownerClosed;
        if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window && window.surface.shown()) {
            choice = new dev.jasper.app.windows.PathChoice(null, window.surface, title, initial, directory);
            ownerClosed = wrap(window.surface.onClosed(cancel));
        } else if (owner instanceof WindowHandle terminal && terminals.ownsOpenWindow(terminal)) {
            choice = new dev.jasper.app.windows.PathChoice(terminal.id(), null, title, initial, directory);
            ownerClosed = wrap(terminals.onWindowClosed(terminal.id(), cancel));
        } else throw new IllegalArgumentException("Picker requires a shown plugin window or live terminal owner from this host");
        Subscription stopped = tracked(cancel);
        try {
            var paths = windows.choose(choice, action -> {
                cancelAction.set(action);
                if (cancelled.get()) action.run();
            });
            if (cancelled.get() || !open.getAsBoolean()) return java.util.List.of();
            return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        } finally { stopped.close(); ownerClosed.close(); }
    }

    Windows windows() {
        return new Windows() {
            @Override public java.util.List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
                return choosePaths(owner, title, initial, false);
            }
            @Override public Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
                return choosePaths(owner, title, initial, true).stream().findFirst();
            }
            @Override public PluginWindow create(WindowSpec spec) {
                guard("create");
                requireNamespace(spec.id(), "A window");
                AuxiliarySurface surface = windows.window(spec.id(), spec.title(), spec.preferredSize(), spec.singleton());
                OwnedWindow existing = ownedWindows.get(surface);
                if (existing != null) return existing;
                var window = new OwnedWindow(surface);
                ownedWindows.put(surface, window);
                surface.onClosed(() -> ownedWindows.remove(surface));
                closers.add(surface::close);
                return window;
            }
            @Override public PluginDialog overlay(OverlaySpec spec) {
                guard("overlay");
                if (!terminals.ownsOpenWindow(spec.owner()))
                    throw new IllegalArgumentException("An overlay needs an open terminal window from this host");
                AuxiliarySurface surface = windows.overlay(spec.title(), spec.owner().id());
                var ownerClosed = terminals.onWindowClosed(spec.owner().id(), () -> windows.closeOwned(spec.owner().id()));
                surface.onClosed(ownerClosed::close);
                closers.add(surface::close);
                return new OwnedDialog(surface);
            }
            @Override public PluginDialog dialog(DialogSpec spec) {
                guard("dialog");
                WindowOwner owner = spec.owner();
                AuxiliarySurface surface;
                if (owner instanceof OwnedWindow window && ownedWindows.get(window.surface) == window)
                    surface = windows.dialog(spec.title(), spec.modal(), window.surface);
                else if (owner instanceof WindowHandle terminalWindow) surface = windows.dialog(spec.title(), spec.modal(), terminalWindow.id());
                else throw new IllegalArgumentException("A dialog's owner must be an open window of this plugin or a terminal window");
                closers.add(surface::close);
                return new OwnedDialog(surface);
            }
        };
    }
}
