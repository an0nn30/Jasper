package dev.jasper.app.plugins;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.Location;
import dev.jasper.sdk.files.FileChoice;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.contributions.StatusEntry;
import dev.jasper.app.contributions.ToolbarEntry;
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
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.ShortcutContext;
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

    private volatile dev.jasper.app.appearance.ResolvedTheme appearanceSnapshot;
    private final List<Consumer<Variant>> themeHandlers = new ArrayList<>();
    private final List<Consumer<Variant>> workspaceHandlers = new ArrayList<>();

    private HostedKit kit;
    void kit(HostedKit value) { kit = java.util.Objects.requireNonNull(value); kit.anchorHost=windows; }
    /** Where this plugin's appearance reads the presentation, on each call and from any thread. */
    private volatile Supplier<dev.jasper.sdk.ui.Presentation> presentation = HostedPresentation::standard;
    void presentation(Supplier<dev.jasper.sdk.ui.Presentation> source) { presentation = java.util.Objects.requireNonNull(source, "source"); }
    /** The installed themes, read on the UI thread; the bundled ones until the host gives its own. */
    private Supplier<dev.jasper.app.appearance.ThemeLibrary> themes = dev.jasper.app.appearance.ThemeLibrary::bundledOnly;
    /** The library the schemes were converted from, and the one the last appearance publication found. */
    private dev.jasper.app.appearance.ThemeLibrary schemesFrom, seenLibrary;
    private List<dev.jasper.sdk.ui.EditorScheme> schemes = List.of();
    void themes(Supplier<dev.jasper.app.appearance.ThemeLibrary> source) {
        themes = java.util.Objects.requireNonNull(source, "source");
        seenLibrary = null;
    }

    /** The installed editor schemes, converted again only when the library changes. */
    private List<dev.jasper.sdk.ui.EditorScheme> editorSchemes() {
        var library = themes.get();
        if (seenLibrary == null) seenLibrary = library;
        if (library != schemesFrom) { schemes = HostedPalettes.editorSchemes(library); schemesFrom = library; }
        return schemes;
    }

    /** Whether the installed themes changed since the last publication or read. */
    private boolean libraryChanged() {
        var library = themes.get();
        boolean changed = seenLibrary != null && library != seenLibrary;
        seenLibrary = library;
        return changed;
    }
    private final String pluginId;
    private final Contributions model;
    private final Containment containment;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final BooleanSupplier open;
    private final Supplier<Variant> variant;
    private final Function<Consumer<Variant>, Subscription> themeSubscriber;
    private final AuxiliaryWindows windows;
    private final HostedTerminals terminals;
    private final GlassSource glass;
    private final HostedTypography typography;
    private final Set<String> ownActions = new HashSet<>();
    private final List<Runnable> closers = new ArrayList<>();
    /** Whether this plugin ships with Jasper, which puts its split items first (workspace plan 8b Rulings 5); its context sets it. */
    BooleanSupplier bundled = () -> false;
    /**
     * The {@code terminal.open} gate for a terminal a content tab opens beside itself (workspace plan 8b Rulings 19):
     * applying it requires the capability at once and gives what audits {@code what} once the terminal has opened. Its
     * context sets it; unset, it refuses every such terminal.
     */
    java.util.function.Function<String, Runnable> terminalOpen = what -> { throw new IllegalStateException("terminal.open gate not set: " + what); };
    /**
     * Whether this plugin may read the title of a tab whose shown item is not its own content: it holds
     * {@code terminal.observe}, as {@code TabHandle.title()} requires (pane flexibility spec 4, for the choices of
     * {@code ContentTab.tabChoices()}). Asking it for an answer that carries titles audits the plugin's first such
     * answer. Its context sets it; unset, no plugin may.
     */
    java.util.function.BooleanSupplier tabTitles = () -> false;
    /**
     * The id of this plugin's action whose handler is running on the UI thread, or null: a toggle or show its panel's
     * own toggle action makes as it runs activates the panel (plan 15b Rulings 1).
     */
    private String running;

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals) {
        this(pluginId, model, containment, ui, onUi, open, loader, variant, themeSubscriber, windows, terminals, null, null);
    }

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals,
             Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
             Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe) {
        this(pluginId, model, containment, ui, onUi, open, loader, variant, themeSubscriber, windows, terminals,
            appearance, subscribe, GlassSource.NONE);
    }

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals,
             Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
             Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe,
             GlassSource glass) {
        this(pluginId, model, containment, ui, onUi, open, loader, variant, themeSubscriber, windows, terminals,
            appearance, subscribe, glass, new HostedTypography());
    }

    HostedUi(String pluginId, Contributions model, Containment containment, Consumer<Runnable> ui, BooleanSupplier onUi,
             BooleanSupplier open, ClassLoader loader, Supplier<Variant> variant,
             Function<Consumer<Variant>, Subscription> themeSubscriber, AuxiliaryWindows windows, HostedTerminals terminals,
             Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
             Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> subscribe,
             GlassSource glass, HostedTypography typography) {
        this.typography = java.util.Objects.requireNonNull(typography, "typography");
        this.pluginId = pluginId; this.model = model; this.containment = containment; this.ui = ui; this.onUi = onUi;
        this.open = open; this.variant = variant; this.themeSubscriber = themeSubscriber;
        this.windows = windows;
        this.terminals = terminals;
        this.glass = glass == null ? GlassSource.NONE : glass;
        if (appearance != null) {
            appearanceSnapshot = java.util.Objects.requireNonNull(appearance.get());
            var registration = subscribe.apply(next -> {
                var previous = appearanceSnapshot;
                appearanceSnapshot = next;
                if (!previous.equals(next)) for(var handler:List.copyOf(themeHandlers)) if(themeHandlers.contains(handler) && open.getAsBoolean()) containment.run(pluginId,"appearance",() -> handler.accept(next.chrome().dark()?Variant.DARK:Variant.LIGHT));
                // An imported or removed theme changes the editor schemes, so it is a workspace change too.
                boolean library = libraryChanged();
                if (!previous.workspace().equals(next.workspace()) || library) announceWorkspace();
            });
            closers.add(registration::close);
        }
        var glassRegistration = this.glass.subscribe().apply(this::announceWorkspace);
        closers.add(glassRegistration::close);
    }

    private void announceWorkspace() {
        for (var handler : List.copyOf(workspaceHandlers)) {
            if (workspaceHandlers.contains(handler) && open.getAsBoolean())
                containment.run(pluginId, "workspace appearance", () -> handler.accept(workspaceVariant()));
        }
    }

    private Variant workspaceVariant() {
        var snapshot = appearanceSnapshot;
        return snapshot == null ? variant.get() : snapshot.workspace().dark() ? Variant.DARK : Variant.LIGHT;
    }

    /** Registration needs an open context and the UI thread. */
    void guard(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        requireUi(what);
    }

    private void requireUi(String what) {
        if (!onUi.getAsBoolean()) throw new IllegalStateException(what + " must be called on the UI thread: " + pluginId);
    }

    /** Whether this plugin registered the action and has not closed it. */
    boolean owns(String actionId) { return ownActions.contains(actionId); }

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
            spec.context().id().ifPresent(owned -> requireNamespace(owned, "A panel context"));
            ActionEntry entry = model.addAction(spec.id(), spec.title(), spec.icon().map(HostedUi.this::icon).orElse(null), spec.keywords(),
                spec.defaultBinding(), spec.otherDefaultBinding(), keyContext(spec.context()),
                invocation -> containment.run(pluginId, "action " + spec.id(), () -> {
                    String previous = running;
                    running = spec.id();
                    try { handler.accept(new Context(terminals.windowHandle(invocation.windowId()), invocation.paneId().map(terminals::paneHandle))); }
                    finally { running = previous; }
                }));
            ownActions.add(spec.id());
            Subscription removal = tracked(() -> { ownActions.remove(spec.id()); entry.close(); });
            return new PluginAction() {
                @Override public void setEnabled(boolean enabled) { requireUi("setEnabled"); entry.setEnabled(enabled); }
                @Override public void setTitle(String title) { requireUi("setTitle"); entry.setTitle(title); }
                @Override public void close() { removal.close(); }
            };
        };
    }

    /** The app's form of a plugin action's shortcut context; content means this plugin's own tabs. */
    private KeyContext keyContext(ShortcutContext context) {
        return switch (context.kind()) {
            case GLOBAL -> KeyContext.GLOBAL;
            case TERMINAL -> KeyContext.TERMINAL;
            case CONTENT -> KeyContext.content(pluginId);
            case PANEL -> KeyContext.panel(context.id().orElseThrow());
        };
    }

    Toolbar toolbar() {
        return item -> {
            guard("add");
            return switch (item) {
                case ToolbarItem.Button button -> { requireOwn(button.actionId()); yield placed(new ToolbarEntry.Button(button.actionId())); }
                case ToolbarItem.Dropdown dropdown -> {
                    dropdown.actionIds().forEach(this::requireOwn);
                    yield placed(new ToolbarEntry.Dropdown(icon(dropdown.icon()), dropdown.title(), dropdown.actionIds()));
                }
                case ToolbarItem.View view -> toolbarView(view.factory());
            };
        };
    }

    private Subscription placed(ToolbarEntry entry) {
        var registration = model.addToolbar(entry);
        return tracked(registration::close);
    }

    /** A toolbar view's mount: the main window's toolbar. */
    private record ToolbarMount(java.util.UUID windowId) { }

    /**
     * A toolbar view: each window builds its own once, when it opens, and closing the registration closes every window's.
     * A view is admitted ({@link ToolbarViewAdmission}) when it is built and on every update after that.
     */
    private Subscription toolbarView(dev.jasper.sdk.ui.ToolbarViewFactory factory) {
        var built = new java.util.LinkedHashMap<java.util.UUID, HostedKitView>();
        var registration = model.addToolbar(new ToolbarEntry.View(windowId -> buildToolbarView(factory, windowId, built)));
        return tracked(() -> {
            registration.close();
            for (var view : List.copyOf(built.values())) view.close();
            built.clear();
        });
    }

    /**
     * One window's view, contained: empty when the factory threw or the toolbar does not admit its tree, and empty for
     * a window that already holds this registration's view (one built while the factory ran is kept, the newer closed).
     */
    private Optional<ToolbarEntry.Mounted> buildToolbarView(dev.jasper.sdk.ui.ToolbarViewFactory factory, java.util.UUID windowId,
                                                            java.util.Map<java.util.UUID, HostedKitView> built) {
        if (built.containsKey(windowId)) return Optional.empty();
        var mounted = new java.util.concurrent.atomic.AtomicReference<HostedKitView>();
        containment.run(pluginId, "toolbar view", () -> {
            var view = kit.requireOwned(java.util.Objects.requireNonNull(factory.create(terminals.windowHandle(windowId)), "view"));
            if (built.containsKey(windowId)) { view.close(); return; }
            view.mount(new ToolbarMount(windowId));
            try {
                ToolbarViewAdmission.require(view.spec());
                view.hostSpec = spec -> { ToolbarViewAdmission.require(spec); return spec; };
            } catch (RuntimeException refused) { view.close(); throw refused; }
            mounted.set(view);
        });
        var hosted = mounted.get();
        if (hosted == null) return Optional.empty();
        built.put(windowId, hosted);
        return Optional.of(new ToolbarEntry.Mounted(hosted.component(), () -> { built.remove(windowId, hosted); hosted.close(); }));
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
            var closed = new AtomicBoolean();
            Subscription removal = tracked(() -> {closed.set(true);entry.close();});
            return new StatusItem() {
                @Override public void setText(String text) { requireUi("setText"); entry.setText(text); }
                @Override public void setIcon(String icon) { requireUi("setIcon"); if(!closed.get())entry.setIcon(icon(icon)); }
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

    dev.jasper.ui.theme.ThemePair kitThemes() {
        var source = appearanceSnapshot;
        if (source != null) return source.pair();
        var theme = dev.jasper.ui.theme.JasperTheme.bundled().generated(variant.get() == Variant.DARK);
        return new dev.jasper.ui.theme.ThemePair(theme, theme);
    }

    Icon icon(String name) { return name == null ? null : kit.icon(name); }
    void validateIcon(String name) { if(name != null) kit.validateIcon(name); }

    Appearance appearance() {
        return new Appearance() {
            @Override public java.awt.Color color(dev.jasper.sdk.ui.SemanticColor role) { requireUi("color"); return kitThemes().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.valueOf(java.util.Objects.requireNonNull(role).name())); }
            @Override public Variant variant() {
                var snapshot = appearanceSnapshot;
                return snapshot == null ? variant.get() : snapshot.chrome().dark() ? Variant.DARK : Variant.LIGHT;
            }
            @Override public Variant workspaceVariant() { return HostedUi.this.workspaceVariant(); }
            @Override public dev.jasper.sdk.ui.WorkspacePalette workspacePalette() {
                requireUi("workspacePalette");
                return HostedPalettes.workspace(kitThemes().workspace());
            }
            @Override public dev.jasper.sdk.ui.EditorPalette editorPalette() {
                requireUi("editorPalette");
                return HostedPalettes.editor(kitThemes().workspace());
            }
            @Override public dev.jasper.sdk.ui.SyntaxPalette syntaxPalette() {
                requireUi("syntaxPalette");
                return HostedPalettes.syntax(kitThemes().workspace());
            }
            @Override public List<dev.jasper.sdk.ui.EditorScheme> editorSchemes() {
                requireUi("editorSchemes");
                return HostedUi.this.editorSchemes();
            }
            @Override public double workspaceOpacity(java.awt.Component component) {
                requireUi("workspaceOpacity");
                return glass.opacity().applyAsDouble(java.util.Objects.requireNonNull(component, "component"));
            }
            @Override public Subscription onWorkspaceChanged(Consumer<Variant> handler) {
                guard("onWorkspaceChanged"); java.util.Objects.requireNonNull(handler, "handler");
                // Only test hosts lack an appearance (production always supplies one), so glass changes reach no handler here.
                if (appearanceSnapshot == null) return onChanged(handler);
                var closed = new AtomicBoolean();
                Consumer<Variant> guarded = value -> { if (!closed.get()) handler.accept(value); };
                workspaceHandlers.add(guarded);
                var removal = tracked(() -> { closed.set(true); workspaceHandlers.remove(guarded); });
                return () -> { closed.set(true); removal.close(); };
            }
            @Override public Subscription onChanged(Consumer<Variant> handler) {
                guard("onChanged");
                java.util.Objects.requireNonNull(handler,"handler");
                if(appearanceSnapshot==null)return themeSubscriber.apply(handler);
                var closed=new AtomicBoolean();Consumer<Variant> guarded=v -> {if(!closed.get())handler.accept(v);};themeHandlers.add(guarded);
                var removal=tracked(() -> {closed.set(true);themeHandlers.remove(guarded);});return () -> {closed.set(true);removal.close();};
            }
            @Override public dev.jasper.sdk.ui.TerminalTypography terminalTypography() {
                return typography.current();
            }
            @Override public Subscription onTerminalTypographyChanged(Consumer<dev.jasper.sdk.ui.TerminalTypography> handler) {
                guard("onTerminalTypographyChanged"); java.util.Objects.requireNonNull(handler, "handler");
                var registration = typography.subscribe(value -> {
                    if (open.getAsBoolean()) containment.run(pluginId, "terminal typography", () -> handler.accept(value));
                });
                var removal = tracked(registration::close);
                return () -> { registration.close(); removal.close(); };
            }
            @Override public dev.jasper.sdk.ui.Presentation presentation() { return presentation.get(); }
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
            spec.toggleActionId().ifPresent(HostedUi.this::requireOwn);
            // Anchor plus split is IntelliJ's anchor plus split: one of the six slots. The SDK's own Side is imported here.
            var entry = model.addPanel(spec.id(), spec.title(), icon(spec.icon()),
                Slot.of(dev.jasper.app.dock.Side.valueOf(spec.defaultAnchor().name()), spec.split()),
                spec.toggleActionId(), site -> {
                JComponent[] built = {null};
                containment.run(pluginId, "panel " + spec.id(), () -> { var host=host(site, spec);var view=kit.requireOwned(factory.create(host));view.mount(host);host.onClosed(view::close);built[0]=view.component(); });
                return built[0];
            });
            return tracked(entry::close);
          }
          @Override public void toggle(String panelId, WindowHandle window) {
            guard("toggle");
            requireNamespace(panelId, "A panel");
            var entry = model.panels().stream().filter(panel -> panel.id().equals(panelId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Panel not registered: " + panelId));
            // The panel's own declared toggle action, as its handler runs, is the user's gesture: IntelliJ's Cmd+1.
            var op = entry.namedToggle() && entry.toggleActionId().equals(running)
                ? Contributions.PanelRequest.Op.ACTIVATING_TOGGLE : Contributions.PanelRequest.Op.TOGGLE;
            model.requestPanel(new Contributions.PanelRequest(window.id(), panelId, op));
          }
        };
    }

    private PanelHost host(PanelSite site, dev.jasper.sdk.ui.PanelSpec spec) {
        return new PanelHost() {
            private boolean closed;
            { site.onClosed(() -> closed=true); }
            @Override public WindowHandle window() { return terminals.windowHandle(site.windowId()); }
            @Override public void setHeader(dev.jasper.sdk.ui.PanelHeader header) {
                requireUi("setHeader");
                java.util.Objects.requireNonNull(header);if(closed)return;
                site.setHeader(new dev.jasper.app.contributions.PanelHeaderState(header.detail(), header.active(),
                    header.actions().stream().map(action -> new dev.jasper.app.contributions.PanelHeaderState.Action(
                        action.label(), icon(action.icon()), () -> containment.run(pluginId, "panel header action", action.invoke()))).toList()));
            }
            @Override public void show() {
                requireUi("show");
                // Inside the panel's own toggle action the show activates, as that action's toggle does.
                if (!closed && spec.toggleActionId().filter(id -> id.equals(running)).isPresent())
                    model.requestPanel(new Contributions.PanelRequest(site.windowId(), spec.id(), Contributions.PanelRequest.Op.ACTIVATING_SHOW));
                else site.show();
            }
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

    /** One SDK view of an application surface: a plugin's dialog or overlay. */
    private class Surface implements WindowSurface {
        final AuxiliarySurface surface;
        HostedKitView mounted;
        final SurfaceMount bodyMount;
        Surface(AuxiliarySurface surface) { this.surface = surface; bodyMount = new SurfaceMount(surface); surface.onClosed(() -> { if(mounted!=null){mounted.close();mounted=null;} }); }
        @Override public void setContent(dev.jasper.sdk.ui.UiView content) {
            guard("setContent");
            var next = kit.requireOwned(content);
            if(surface.closed())throw new IllegalStateException("Surface is closed");
            next.mount(bodyMount);
            if(mounted==next)return;
            var previous=mounted;
            // A host-packed dialog follows its kit view's size (DialogFit), as plugin dialogs did with pack().
            var component = next.component();
            // Removing an old anchor may synchronously close this surface through an owned child.
            mounted = next; next.updated = surface::contentChanged;
            surface.setContent(component); surface.fitContent(true);
            if(previous!=null)previous.close();
        }
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

    private final class OwnedDialog extends Surface implements PluginDialog {
        OwnedDialog(AuxiliarySurface surface) { super(surface); }
        /**
         * A modeless dialog showing a WINDOW form is a window (the Credential Vault manager): from its first show it can be
         * resized, opens at least a window's size and remembers its bounds under this plugin's id and its title.
         */
        @Override public void show() {
            requireUi("show");
            if (surface.kind() == AuxiliarySurface.Kind.DIALOG && !surface.modal() && !surface.shown() && mounted != null && !mounted.closed()
                    && mounted.component().spec().root() instanceof dev.jasper.ui.model.Nodes.Form form
                    && form.kind() == dev.jasper.ui.model.Nodes.FormKind.WINDOW)
                surface.windowSized(windowKey(surface.title()));
            surface.show();
        }
    }

    /** A window-sized dialog's key: this plugin's id and the title, cut to what UiState keeps, never inside a surrogate pair. */
    private String windowKey(String title) {
        String key = pluginId + "|" + title;
        int length = dev.jasper.app.persistence.UiState.WINDOW_KEY_LENGTH;
        if (key.length() <= length) return key;
        return key.substring(0, Character.isHighSurrogate(key.charAt(length - 1)) ? length - 1 : length);
    }

    private final java.util.Map<dev.jasper.app.contributions.Tab, OwnedContent> ownedContent = new java.util.IdentityHashMap<>();
    private final class OwnedContent implements dev.jasper.sdk.ui.ContentTab {
        final dev.jasper.app.contributions.Tab entry;
        OwnedContent(dev.jasper.app.contributions.Tab entry) { this.entry = entry; }
        public void setPresentation(dev.jasper.sdk.ui.ContentPresentation v) { requireUi("content presentation"); if(entry.closed())return; entry.presentation(v.title(), v.detail(), icon(v.icon()), v.dirty(), v.location(), v.host()); }
        public void setCommands(List<dev.jasper.sdk.ui.ContentCommand> values) { requireUi("content commands"); entry.commands(values.stream().map(v ->
            new dev.jasper.app.contributions.ContentCommand(v.id(), v.title(), v.shortcut(), v.enabled(), () -> containment.run(pluginId, "content command", v.run()))).toList()); }
        public void setStatus(List<dev.jasper.sdk.ui.ContentStatus> values) { requireUi("content status"); if(entry.closed())return; entry.status(values.stream().map(v ->
            new dev.jasper.app.contributions.ContentStatus(v.text(), v.tooltip(), contained(v.run()), v.icon().isEmpty() ? null : icon(v.icon()), v.dimmed(),
                v.menu().stream().map(e -> new dev.jasper.app.contributions.ContentStatus.MenuEntry(e.label(), contained(e.run()), e.checkable(), e.checked(), e.separator())).toList())).toList()); }
        private Runnable contained(Runnable run) { return run == null ? null : () -> containment.run(pluginId, "content status", run); }
        public void setFocusTarget(Runnable focus) { requireUi("content focus"); entry.focusTarget(() -> containment.run(pluginId, "content focus", focus)); }
        public void setRestore(Optional<dev.jasper.sdk.RestoreState> state) { requireUi("content restore"); entry.restore(state.map(HostedUi::restore)); }
        public void select() { requireUi("select content"); entry.select(); }
        public void moveToNewWindow() { requireUi("move content"); entry.moveToNewWindow(); }
        public void moveToMainWindow() { requireUi("move content"); entry.moveToMainWindow(); }
        public void moveToNewTab() { requireUi("move content"); model.tabs().moveToNewTab(entry); }
        public boolean mayMoveToNewTab() { requireUi("move content"); return model.tabs().mayMoveToNewTab(entry); }
        /** Each tab's title goes to a plugin that observes terminals or whose own content the tab shows, and no other. */
        public List<dev.jasper.sdk.ui.TabChoice> tabChoices() {
            requireUi("move content");
            var others = model.tabs().tabChoices(entry);
            boolean observes = !others.isEmpty() && tabTitles.getAsBoolean();
            return others.stream().map(other -> new dev.jasper.sdk.ui.TabChoice(other.number(),
                observes || ownContent(other.shown()) ? other.title() : "")).toList();
        }
        private boolean ownContent(dev.jasper.app.layout.TabId shown) {
            return model.tabs().tab(shown).filter(tab -> tab.kind() == dev.jasper.app.contributions.Tab.Kind.HOSTED
                && tab.plugin().equals(pluginId)).isPresent();
        }
        public void moveToTab(int number) { requireUi("move content"); model.tabs().moveToTab(entry, number); }
        public void split(dev.jasper.sdk.ui.SplitChoice choice, dev.jasper.sdk.terminal.Direction direction) {
            requireUi("split content");
            java.util.Objects.requireNonNull(choice, "choice");
            java.util.Objects.requireNonNull(direction, "direction");
            if (!splitChoices().contains(choice)) throw new IllegalArgumentException("Not one of Jasper's split choices: " + choice.label());
            boolean right = direction == dev.jasper.sdk.terminal.Direction.RIGHT;
            if (choice.equals(dev.jasper.sdk.ui.SplitChoice.TERMINAL)) {
                // A local shell, as Terminals.split opens one: terminal.open before anything else, whatever the tab's
                // state (workspace plan 8b Rulings 19), and audited only once a window opened it, as Terminals.split is
                // once its target resolves. A closed tab, or one being closed, opens nothing.
                Runnable audit = terminalOpen.apply("opened a terminal beside content tab " + entry.id().value());
                if (model.tabs().splitTerminal(entry, right ? dev.jasper.app.layout.Side.RIGHT : dev.jasper.app.layout.Side.DOWN)) audit.run();
                return;
            }
            // A closed tab, or one waiting on a close decision, splits nothing, as it moves nowhere.
            if (!model.tabs().splittable(entry)) return;
            // A plugin's choice opens beside the focused pane, so the item is selected first (workspace plan 8b Rulings 18).
            model.tabs().select(entry);
            java.util.UUID window = entry.window();
            String action = right ? choice.rightAction() : choice.downAction();
            if (window != null) model.action(action).ifPresent(found -> found.invoke(new Contributions.Invocation(window, Optional.empty())));
        }
        public WindowHandle window() {
            requireUi("content window");
            // A tab no window could take back (its move failed everywhere) names no window.
            return terminals.windowHandle(entry.window() != null ? entry.window() : new java.util.UUID(0, 0));
        }
        public boolean isActive() { requireUi("content active"); return entry.active(); }
        public boolean isClosed() { requireUi("content closed"); return entry.closed(); }
        public java.util.concurrent.CompletableFuture<Boolean> requestClose() { requireUi("close content"); return entry.requestClose(); }
        public Subscription onActive(Consumer<Boolean> listener) { requireUi("content activation"); return wrap(entry.onActive(value -> containment.run(pluginId,"content activation",()->listener.accept(value)))); }
        public Subscription onClosed(Runnable listener) { requireUi("content disposal"); return wrap(entry.onClosed(()->containment.run(pluginId,"content disposal",listener))); }
        public void close() { subscription(entry::close).close(); }
    }
    /** The application's twin of a plugin's restore state (workspace tabs spec 13). */
    private static dev.jasper.app.layout.TabRestore restore(dev.jasper.sdk.RestoreState state) {
        return new dev.jasper.app.layout.TabRestore(state.kind(), state.values());
    }

    /** Why a restorer that threw restored nothing, as a placeholder words it. */
    private static String problem(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private WindowOwner resolveContentOwner(WindowOwner owner) {
        if (owner instanceof OwnedContent content) {
            if (ownedContent.get(content.entry) != content || content.entry.closed()) throw new IllegalArgumentException("Closed or foreign content owner");
            return terminals.window(content.entry.window()).orElseThrow(() -> new IllegalArgumentException("Closed content host"));
        }
        return owner;
    }

    private java.util.List<Location> chooseLocations(WindowOwner owner, String title, Optional<Location> initial,
                                                     dev.jasper.app.windows.PathChoice.Mode mode, boolean multiple,
                                                     Optional<String> suggestedName, Set<String> sourceIds) {
        guard("choose");
        OwnedContent document = owner instanceof OwnedContent content ? content : null;
        owner = resolveContentOwner(owner);
        java.util.Objects.requireNonNull(title); java.util.Objects.requireNonNull(initial); java.util.Objects.requireNonNull(suggestedName);
        List<ChooserSource> sources = model.fileSources().stream().filter(source -> sourceIds.contains(source.id())).toList();
        var cancelled = new AtomicBoolean();
        var cancelAction = new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
        Runnable cancel = () -> { if (cancelled.compareAndSet(false, true)) cancelAction.get().run(); };
        dev.jasper.app.windows.PathChoice choice;
        Subscription ownerClosed;
        if (owner instanceof WindowHandle terminal && terminals.ownsOpenWindow(terminal)) {
            choice = new dev.jasper.app.windows.PathChoice(terminal.id(), null, title, initial, mode, multiple,
                terminalStart(terminal.id(), sources), suggestedName, sources);
            ownerClosed = wrap(terminals.onWindowClosed(terminal.id(), cancel));
        } else throw new IllegalArgumentException("Picker requires an open Jasper window or content tab from this host");
        Subscription stopped = tracked(cancel);
        var pin = document == null ? null : document.entry.pin();
        var documentClosed = document == null ? null : document.entry.onClosed(cancel);
        try {
            var chosen = windows.choose(choice, action -> {
                cancelAction.set(action);
                if (cancelled.get()) action.run();
            });
            if (cancelled.get() || !open.getAsBoolean()) return java.util.List.of();
            return java.util.List.copyOf(chosen);
        } finally {
            stopped.close(); ownerClosed.close();
            if (documentClosed != null) documentClosed.close();
            if (pin != null) pin.close();
        }
    }

    /** Where the owner's focused terminal is: a source's location when one recognises the pane, else the pane's local folder. */
    private Optional<Location> terminalStart(java.util.UUID window, List<ChooserSource> sources) {
        Optional<java.util.UUID> pane = terminals.focusedPane(window);
        for (ChooserSource source : sources) {
            Optional<Location> found;
            try { found = pane.flatMap(source::locate); }
            catch (RuntimeException failed) { found = Optional.empty(); }
            if (found.isPresent()) return found;
        }
        return terminals.workingDirectory(window).map(Location::local);
    }

    private static java.util.List<Path> localPaths(java.util.List<Location> chosen) {
        return chosen.stream().filter(Location::isLocal).map(location -> location.localPath().toAbsolutePath().normalize()).toList();
    }

    static Location location(FileLocation location) {
        return switch (location) {
            case FileLocation.Local local -> Location.local(local.path());
            case FileLocation.Sourced sourced -> new Location(sourced.sourceId(), sourced.rootId(), sourced.path());
        };
    }

    static FileLocation fileLocation(Location location) {
        return location.isLocal() ? new FileLocation.Local(location.localPath())
            : new FileLocation.Sourced(location.source(), location.root(), location.path());
    }

    /**
     * One of a split item's two actions (workspace plan 8b Rulings 3): no default, the palette's words for it, and a
     * contained call of the opener with the window it ran in and {@code placement}.
     */
    private ActionEntry splitAction(String id, String title, Icon icon, String side, String label,
                                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener,
                                    dev.jasper.sdk.ui.TabPlacement placement) {
        var keywords = new ArrayList<>(List.of("split", side, "new", "pane"));
        for (String word : label.toLowerCase(java.util.Locale.ROOT).split(" ")) if (!word.isBlank()) keywords.add(word.strip());
        return model.addAction(id, title, icon, keywords, Optional.empty(), invocation -> containment.run(pluginId, "split item " + id,
            () -> opener.accept(terminals.windowHandle(invocation.windowId()), placement)));
    }

    /** The Split lists' choices (tabs and panes spec 2.10; workspace plan 8b Rulings 4 and 5): the terminal, then every split item. */
    private List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
        var choices = new ArrayList<dev.jasper.sdk.ui.SplitChoice>();
        choices.add(dev.jasper.sdk.ui.SplitChoice.TERMINAL);
        for (var item : model.splitItems())
            choices.add(new dev.jasper.sdk.ui.SplitChoice(item.label(), item.rightAction(), item.downAction(), item.asks()));
        return List.copyOf(choices);
    }

    Windows windows() {
        return new Windows() {
            @Override public dev.jasper.sdk.ui.ContentTab openTab(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, dev.jasper.sdk.ui.UiView content,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                guard("openTab");
                java.util.Objects.requireNonNull(placement, "placement");
                var view = kit.requireOwned(content);
                var existing = ownedContent.values().stream().filter(c -> c.entry.key().equals(spec.key())).findFirst();
                if(existing.isPresent()) return openComponent(spec, owner, existing.get().entry.component(), placement);
                // Reserve before publishing: a view cannot be owned by two surface lifetimes.
                view.mount(spec);
                // Workspace content takes the workspace theme slot, as terminals and editors do, on an opaque workspace
                // surface; its specialized content paints its own background or glass above that. Moves keep both.
                view.route(dev.jasper.ui.theme.ThemeRoute.WORKSPACE, true);
                try {
                    var tab = openComponent(spec, owner, view.component(), placement);
                    tab.onClosed(view::close);
                    return tab;
                } catch(RuntimeException | Error failure) {
                    view.route(dev.jasper.ui.theme.ThemeRoute.CHROME, false);
                    view.unmount(spec);throw failure;
                }
            }
            private dev.jasper.sdk.ui.ContentTab openComponent(dev.jasper.sdk.ui.ContentSpec spec, WindowOwner owner, JComponent component,
                    dev.jasper.sdk.ui.TabPlacement placement) {
                guard("openTab");
                java.util.UUID window = null;
                if (owner != null) {
                    owner = resolveContentOwner(owner);
                    if (!(owner instanceof WindowHandle handle)) throw new IllegalArgumentException("Content needs a workspace owner");
                    if (terminals.ownsOpenWindow(handle)) window = handle.id();
                    else if (!terminals.ownsWindow(handle)) throw new IllegalArgumentException("Foreign workspace owner");
                }
                var entry = model.tabs().open(pluginId, spec.key(), spec.title(), window, component, switch (placement) {
                    case NEW_TAB -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_TAB;
                    case SPLIT_RIGHT -> dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_RIGHT;
                    case SPLIT_DOWN -> dev.jasper.app.contributions.TabRegistry.Placement.SPLIT_DOWN;
                    case NEW_WINDOW -> dev.jasper.app.contributions.TabRegistry.Placement.NEW_WINDOW;
                });
                var existing = ownedContent.get(entry); if (existing != null) return existing;
                var owned = new OwnedContent(entry); ownedContent.put(entry, owned);
                entry.restore(spec.restore().map(HostedUi::restore));
                Runnable cleanup = entry::close;
                closers.add(cleanup);
                entry.onClosed(() -> { ownedContent.remove(entry); closers.remove(cleanup); });
                return owned;
            }
            @Override public Subscription splitItem(dev.jasper.sdk.ui.SplitItemSpec spec,
                    java.util.function.BiConsumer<WindowHandle, dev.jasper.sdk.ui.TabPlacement> opener) {
                guard("splitItem");
                java.util.Objects.requireNonNull(spec, "spec");
                java.util.Objects.requireNonNull(opener, "opener");
                requireNamespace(spec.id(), "A split item");
                Icon icon = spec.icon().map(HostedUi.this::icon).orElse(null);
                if (model.splitItems().stream().anyMatch(item -> item.id().equals(spec.id())))
                    throw new IllegalArgumentException("Split item already registered: " + spec.id());
                // An item that asks first ends its actions' titles with an ellipsis (workspace plan 9a Rulings 1).
                String asks = spec.asks() ? "…" : "";
                ActionEntry right = splitAction(spec.rightAction(), "Split Right with " + spec.label() + asks, icon, "right",
                    spec.label(), opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
                ActionEntry down;
                try {
                    down = splitAction(spec.downAction(), "Split Down with " + spec.label() + asks, icon, "down", spec.label(),
                        opener, dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
                } catch (RuntimeException failure) { right.close(); throw failure; }
                var listed = model.addSplitItem(new dev.jasper.app.contributions.SplitItem(spec.id(), spec.label(), icon,
                    spec.rightAction(), spec.downAction(), bundled.getAsBoolean(), spec.asks()));
                ownActions.add(spec.rightAction());
                ownActions.add(spec.downAction());
                return tracked(() -> {
                    ownActions.remove(spec.rightAction());
                    ownActions.remove(spec.downAction());
                    listed.close();
                    right.close();
                    down.close();
                });
            }
            @Override public List<dev.jasper.sdk.ui.SplitChoice> splitChoices() {
                guard("splitChoices");
                return HostedUi.this.splitChoices();
            }
            @Override public Subscription onTabsClosing(java.util.function.BiFunction<List<dev.jasper.sdk.ui.ContentTab>,Boolean,java.util.concurrent.CompletableFuture<Boolean>> guard) {
                HostedUi.this.guard("content close guard");
                var registration = model.tabs().guard(pluginId, (entries,batch) -> {
                    var result = new java.util.concurrent.CompletableFuture<Boolean>();
                    boolean invoked = containment.run(pluginId, "content close guard", () -> {
                        try { guard.apply(entries.stream().map(ownedContent::get).map(v -> (dev.jasper.sdk.ui.ContentTab)v).toList(),batch)
                            .whenComplete((allowed, error) -> { if (error != null) result.complete(false); else result.complete(Boolean.TRUE.equals(allowed)); }); }
                        catch (RuntimeException failure) { result.complete(false); }
                    });
                    if (!invoked) result.complete(false);
                    return result;
                });
                return tracked(registration::close);
            }
            @Override public Subscription restorer(String kind, dev.jasper.sdk.ui.TabRestorer restorer) {
                HostedUi.this.guard("tab restorer");
                java.util.Objects.requireNonNull(restorer, "restorer");
                // The kind's rules are the restore state's (workspace tabs spec 14).
                new dev.jasper.sdk.RestoreState(kind, java.util.Map.of());
                var registration = model.tabs().restorer(pluginId, kind, saved -> {
                    dev.jasper.sdk.RestoreState state;
                    try {
                        state = new dev.jasper.sdk.RestoreState(saved.state().kind(), saved.state().state());
                    } catch (IllegalArgumentException | NullPointerException invalid) {
                        // A file this build did not write may hold a state the SDK refuses: no restorer sees it, and it
                        // is no failure (workspace plan 6b Rulings 30).
                        return Optional.of("its saved state is not valid");
                    }
                    var request = new dev.jasper.sdk.ui.RestoreRequest(terminals.windowHandle(saved.window()), saved.title(), state);
                    // A reason the restorer gives is expected, and no failure; what it throws is the plugin's failure.
                    var said = new java.util.concurrent.atomic.AtomicReference<Optional<String>>(Optional.empty());
                    Throwable thrown;
                    try {
                        thrown = containment.attempt(pluginId, "tab restorer", () -> {
                            Optional<String> declined = restorer.restore(request);
                            said.set(declined == null ? Optional.empty() : declined.filter(reason -> !reason.isBlank()));
                            return null;
                        });
                    } catch (VirtualMachineError fatal) {
                        throw fatal;
                    } catch (Error failure) {
                        // The plugin's restore failure, reported as a throw is; the registry closes the item it opened and
                        // the rest of the windows restore (workspace plan 6b's final review, M1).
                        containment.record(pluginId, "tab restorer", failure);
                        throw failure;
                    }
                    return thrown == null ? said.get() : Optional.of(problem(thrown));
                });
                return tracked(registration::close);
            }
            @Override public Subscription onQuitting(Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {
                HostedUi.this.guard("quit guard");
                java.util.Objects.requireNonNull(guard, "guard");
                var registration = model.quitGuards().add(() -> {
                    var result = new java.util.concurrent.CompletableFuture<Boolean>();
                    boolean invoked = containment.run(pluginId, "quit guard", () -> {
                        var decision = guard.get();
                        if (decision == null) { result.complete(true); return; }
                        decision.whenComplete((allowed, error) -> result.complete(error != null || !Boolean.FALSE.equals(allowed)));
                    });
                    if (!invoked) result.complete(true);
                    return result;
                });
                return tracked(registration::close);
            }

            @Override public java.util.List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.OPEN, true, Optional.empty(), Set.of()));
            }
            @Override public Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.DIRECTORY, false, Optional.empty(), Set.of())).stream().findFirst();
            }
            @Override public Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
                return localPaths(chooseLocations(owner, title, initial.map(Location::local), dev.jasper.app.windows.PathChoice.Mode.SAVE, false, Optional.empty(), Set.of())).stream().findFirst();
            }
            @Override public java.util.List<FileLocation> choose(FileChoice choice) {
                java.util.Objects.requireNonNull(choice, "choice");
                var mode = switch (choice.mode()) {
                    case OPEN -> dev.jasper.app.windows.PathChoice.Mode.OPEN;
                    case SAVE -> dev.jasper.app.windows.PathChoice.Mode.SAVE;
                    case DIRECTORY -> dev.jasper.app.windows.PathChoice.Mode.DIRECTORY;
                };
                return chooseLocations(choice.owner(), choice.title(), choice.initial().map(HostedUi::location), mode, choice.multiple(),
                    choice.suggestedName(), choice.sources()).stream().map(HostedUi::fileLocation).toList();
            }
            @Override public PluginDialog overlay(OverlaySpec spec) {
                guard("overlay");
                if (!terminals.ownsOpenWindow(spec.owner()))
                    throw new IllegalArgumentException("An overlay needs an open Jasper window from this host");
                AuxiliarySurface surface = windows.overlay(spec.title(), spec.owner().id());
                var ownerClosed = terminals.onWindowClosed(spec.owner().id(), () -> windows.closeOwned(spec.owner().id()));
                surface.onClosed(ownerClosed::close);
                closers.add(surface::close);
                return new OwnedDialog(surface);
            }
            @Override public dev.jasper.sdk.ui.PluginPopover popover(dev.jasper.sdk.ui.UiAnchor value) {
                guard("popover");var anchor=HostedAnchor.require(value,windows);
                var popup=new HostedPopover(kit,anchor);var stop=tracked(popup::close);popup.onClosed(stop::close);return popup;
            }
            @Override public PluginDialog dialog(DialogSpec spec) {
                guard("dialog");
                WindowOwner owner = resolveContentOwner(spec.owner());
                // An overlay is the kit's modal overlay inside the owner's window; the others are native dialogs.
                boolean overlay = spec.presentation() == DialogSpec.Presentation.OVERLAY;
                AuxiliarySurface surface;
                if (owner instanceof dev.jasper.sdk.ui.UiAnchor value) {
                    var anchor=HostedAnchor.require(value,windows);
                    surface=anchor.ownerSurface()!=null
                        ? overlay ? windows.modalOverlay(spec.title(),anchor.ownerSurface()) : windows.dialog(spec.title(),spec.modal(),anchor.ownerSurface())
                        : overlay ? windows.modalOverlay(spec.title(),anchor.target) : windows.dialog(spec.title(),spec.modal(),anchor.target);
                    var registration=anchor.onClosed(surface::close);surface.onClosed(registration::close);
                } else if (owner instanceof WindowHandle jasperWindow && terminals.ownsOpenWindow(jasperWindow)) {
                    surface = overlay ? windows.modalOverlay(spec.title(), jasperWindow.id()) : windows.dialog(spec.title(), spec.modal(), jasperWindow.id());
                    // A dialog dies with its Jasper window, as an overlay does (its native dialog already has).
                    var ownerClosed = terminals.onWindowClosed(jasperWindow.id(), surface::close);
                    surface.onClosed(ownerClosed::close);
                }
                else throw new IllegalArgumentException("A dialog's owner must be a Jasper window, a content tab or an anchor from this host");
                surface.attachedSheet(spec.presentation() == DialogSpec.Presentation.ATTACHED_SHEET);
                surface.swatch(dev.jasper.ui.model.Nodes.Hue.valueOf(spec.swatch().name()));
                if (spec.owner() instanceof OwnedContent content) {
                    var pin = spec.modal() ? content.entry.pin() : null;
                    var registration = content.entry.onClosed(surface::close);
                    surface.onClosed(() -> { registration.close(); if (pin != null) pin.close(); });
                }
                closers.add(surface::close);
                return new OwnedDialog(surface);
            }
        };
    }
}
