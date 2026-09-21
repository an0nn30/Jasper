package dev.jasper.app.plugins;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Icon;

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
    private final Set<String> ownActions = new HashSet<>();
    private final List<Runnable> closers = new ArrayList<>();

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber) {
        this.pluginId = pluginId; this.model = model; this.containment = containment; this.ui = ui; this.onUi = onUi;
        this.open = open; this.loader = loader; this.variant = variant; this.themeSubscriber = themeSubscriber;
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
                    handler.accept(new Context(invocation::windowId, invocation.paneId().map(id -> (PaneHandle) () -> id)))));
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
        return spec -> {
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
        };
    }

    Appearance appearance() {
        return new Appearance() {
            @Override public Variant variant() { return variant.get(); }
            @Override public Subscription onChanged(Consumer<Variant> handler) {
                guard("onChanged");
                return themeSubscriber.apply(java.util.Objects.requireNonNull(handler, "handler"));
            }
            @Override public Icon icon(String svgResourcePath) { return AppIcons.themed(loader, svgResourcePath); }
        };
    }
}
