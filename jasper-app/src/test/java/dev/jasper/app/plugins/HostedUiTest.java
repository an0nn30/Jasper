package dev.jasper.app.plugins;

import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.ShortcutContext;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedUiTest {
    private final Contributions model = new Contributions();
    private final Containment containment = new Containment(() -> true);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final List<Consumer<Variant>> themeHandlers = new ArrayList<>();
    private Variant variant = Variant.DARK;
    private final dev.jasper.app.persistence.UiState uiState = dev.jasper.app.persistence.UiState.inMemory();
    private final dev.jasper.app.windows.AuxiliaryWindows auxiliary = new dev.jasper.app.windows.AuxiliaryWindows(uiState,
        surface -> new dev.jasper.app.windows.AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { },
            () -> new java.awt.Rectangle(0, 0, 10, 10), (title, initial) -> java.util.Optional.empty()));
    private final TerminalFixture terminalFixture = new TerminalFixture();
    private final HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", java.util.Set.of()),
        terminalFixture.registry, Runnable::run, () -> true, () -> true,
        new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null, name -> new javax.swing.ImageIcon()));
    private final HostedUi ui = new HostedUi("dev.x.tool", model, containment, Runnable::run, () -> true, open::get,
        HostedUiTest.class.getClassLoader(), () -> variant,
        handler -> { themeHandlers.add(handler); return () -> themeHandlers.remove(handler); }, auxiliary, terminals);

    private final HostedKit kit = new HostedKit("dev.x.tool",open::get,ui::kitThemes,ui.appearance(),containment);
    { ui.kit(kit); }
    private dev.jasper.sdk.ui.UiView view(){return kit.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",dev.jasper.sdk.ui.ControlState.READY,"Content",dev.jasper.sdk.ui.Nodes.TextRole.BODY)),e -> {});}

    @Test void workspaceChangesPublishRichValuesWithoutChangingChromeAndDisposeListeners() {
        var themes = new dev.jasper.app.appearance.ThemeController();
        var hosted = new HostedUi("dev.x.tool", model, containment, Runnable::run,
            javax.swing.SwingUtilities::isEventDispatchThread, open::get, getClass().getClassLoader(), () -> Variant.DARK,
            handler -> { themeHandlers.add(handler); return () -> themeHandlers.remove(handler); }, auxiliary, terminals,
            themes::current, handler -> themes.subscribe((value, changed) -> handler.accept(value)));
        var appearance = hosted.appearance();
        var seen = new ArrayList<dev.jasper.sdk.ui.WorkspacePalette>();
        appearance.onWorkspaceChanged(value -> { seen.add(appearance.workspacePalette());
            assertThat(appearance.syntaxPalette()).isEqualTo(HostedPalettes.syntax(themes.current().workspace())); });
        assertThat(seen).isEmpty();
        themes.configure(dev.jasper.app.appearance.Theme.DARK,
            new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA,
            dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(seen).hasSize(1);
        assertThat(appearance.variant()).isEqualTo(Variant.DARK);
        assertThat(appearance.workspaceVariant()).isEqualTo(Variant.DARK);
        assertThat(appearance.workspacePalette()).isEqualTo(HostedPalettes.workspace(themes.current().workspace()));
        assertThat(appearance.editorPalette()).isEqualTo(HostedPalettes.editor(themes.current().workspace()));
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(appearance::workspaceVariant).join()).isEqualTo(Variant.DARK);
        assertThatThrownBy(() -> java.util.concurrent.CompletableFuture.supplyAsync(appearance::workspacePalette).join())
            .hasCauseInstanceOf(IllegalStateException.class);
        hosted.closeAll();
        themes.configure(dev.jasper.app.appearance.Theme.DARK, dev.jasper.app.config.WorkspaceThemeChoice.MATCH, null,
            dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(seen).hasSize(1);
    }

    @Test void workspaceOpacityFollowsTheGlassSourceAndItsChangesAreWorkspaceChanges() {
        var themes = new dev.jasper.app.appearance.ThemeController();
        double[] value = {0.6};
        var listeners = new ArrayList<Runnable>();
        var glass = new GlassSource(component -> value[0], listener -> {
            listeners.add(listener);
            return new dev.jasper.app.lifecycle.Subscription(() -> listeners.remove(listener));
        });
        var hosted = new HostedUi("dev.x.tool", model, containment, Runnable::run,
            javax.swing.SwingUtilities::isEventDispatchThread, open::get, getClass().getClassLoader(), () -> Variant.DARK,
            handler -> { themeHandlers.add(handler); return () -> themeHandlers.remove(handler); }, auxiliary, terminals,
            themes::current, handler -> themes.subscribe((next, changed) -> handler.accept(next)), glass);
        var appearance = hosted.appearance();
        var component = new javax.swing.JPanel();
        assertThat(listeners).hasSize(1);
        assertThat(appearance.workspaceOpacity(component)).isEqualTo(0.6);
        assertThatNullPointerException().isThrownBy(() -> appearance.workspaceOpacity(null));
        var chrome = new java.util.concurrent.atomic.AtomicInteger();
        var seen = new ArrayList<Double>();
        appearance.onChanged(variant -> chrome.incrementAndGet());
        var subscription = appearance.onWorkspaceChanged(variant -> seen.add(appearance.workspaceOpacity(component)));
        value[0] = 0.4;
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(seen).containsExactly(0.4);
        assertThat(chrome).hasValue(0);
        subscription.close();
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(seen).containsExactly(0.4);
        var late = new java.util.concurrent.atomic.AtomicInteger();
        appearance.onWorkspaceChanged(variant -> late.incrementAndGet());
        open.set(false);
        List.copyOf(listeners).forEach(Runnable::run);
        assertThat(late).hasValue(0);
        hosted.closeAll();
        assertThat(listeners).isEmpty();
    }

    @Test void bothVariantsReadThePublishedNativeSnapshotInsideWorkspaceCallbacks() {
        var themes = new dev.jasper.app.appearance.ThemeController();
        var hosted = new HostedUi("dev.x.tool", model, containment, Runnable::run,
            javax.swing.SwingUtilities::isEventDispatchThread, open::get, getClass().getClassLoader(), () -> Variant.DARK,
            handler -> () -> {}, auxiliary, terminals, themes::current,
            handler -> themes.subscribe((value, changed) -> handler.accept(value)));
        var seen = new ArrayList<Variant>();
        hosted.appearance().onWorkspaceChanged(value -> seen.add(hosted.appearance().variant()));
        themes.configure(dev.jasper.app.appearance.Theme.JASPER_LIGHT);
        assertThat(seen).containsExactly(Variant.LIGHT);
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> hosted.appearance().variant()).join())
            .isEqualTo(Variant.LIGHT);
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> hosted.appearance().workspaceVariant()).join())
            .isEqualTo(Variant.LIGHT);
        hosted.closeAll();
    }

    @Test void semanticDefaultsSurviveChromeRebuildAndMatchKeepsRichSyntax() {
        var themes = new dev.jasper.app.appearance.ThemeController();
        var match = themes.current().chrome();
        assertThat(HostedPalettes.syntax(themes.current().workspace())).isEqualTo(HostedPalettes.syntax(match));
        assertThat(HostedPalettes.syntax(themes.current().workspace()).style(dev.jasper.sdk.ui.SyntaxRole.KEYWORD).color())
            .isNotEqualTo(HostedPalettes.editor(themes.current().workspace()).foreground());
        themes.configure(dev.jasper.app.appearance.Theme.DARK,
            new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA,
            dev.jasper.app.config.UiFontConfig.defaults());
        var workspace = HostedPalettes.workspace(themes.current().workspace());
        var editor = HostedPalettes.editor(themes.current().workspace());
        var syntax = HostedPalettes.syntax(themes.current().workspace());
        themes.configure(dev.jasper.app.appearance.Theme.JASPER_LIGHT);
        assertThat(HostedPalettes.workspace(themes.current().workspace())).isEqualTo(workspace);
        assertThat(HostedPalettes.editor(themes.current().workspace())).isEqualTo(editor);
        assertThat(HostedPalettes.syntax(themes.current().workspace())).isEqualTo(syntax);
        var rejected = new dev.jasper.app.appearance.ThemeController(value -> value == dev.jasper.app.appearance.Theme.DARK);
        var before = HostedPalettes.workspace(themes.current().workspace());
        assertThatThrownBy(() -> rejected.configure(dev.jasper.app.appearance.Theme.JASPER_LIGHT))
            .isInstanceOf(dev.jasper.app.appearance.ThemeController.InstallationFailure.class);
        assertThat(HostedPalettes.workspace(themes.current().workspace())).isEqualTo(before);
    }

    @Test void closingWorkspaceSubscriptionOffThreadSuppressesDeliveryBeforeQueuedRemoval() {
        var themes = new dev.jasper.app.appearance.ThemeController();
        var hosted = new HostedUi("dev.x.tool", model, containment, javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, open::get, getClass().getClassLoader(), () -> Variant.DARK,
            handler -> () -> {}, auxiliary, terminals, themes::current,
            handler -> themes.subscribe((value, changed) -> handler.accept(value)));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var subscription = hosted.appearance().onWorkspaceChanged(value -> calls.incrementAndGet());
        java.util.concurrent.CompletableFuture.runAsync(subscription::close).join();
        themes.configure(dev.jasper.app.appearance.Theme.DARK,
            new dev.jasper.app.config.WorkspaceThemeChoice("Darcula"), dev.jasper.app.appearance.ThemeFixtures.DARCULA,
            dev.jasper.app.config.UiFontConfig.defaults());
        assertThat(calls).hasValue(0);
        hosted.closeAll();
    }

    @Test void fileChooserIsAvailableOnBothSurfacesAndStopsWithThePlugin() {
        var window = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        var dialog = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Edit", window, true));
        for (var surface : List.<dev.jasper.sdk.ui.WindowSurface>of(window, dialog)) {
            assertThatIllegalStateException().isThrownBy(() -> surface.chooseFile("Choose key", Optional.empty()));
            surface.show();
            assertThat(surface.chooseFile("Choose key", Optional.empty())).isEmpty();
        }
        open.set(false);
        assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", Optional.empty()));
        ui.closeAll();
    }

    @Test void actionsReachTheModelWithContainedHandlersAndVerifiedContext() {
        List<ActionContext> seen = new ArrayList<>();
        PluginAction run = ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run").withDefaultBinding("cmd+alt+j"), seen::add);
        ui.actions().register(ActionSpec.of("dev.x.tool.boom", "Boom"), context -> { throw new IllegalStateException("handler"); });
        assertThatIllegalArgumentException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.other.run", "Foreign"), seen::add));
        assertThatIllegalArgumentException().as("a longer id sharing the prefix is another namespace")
            .isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.toolbox.run", "Foreign"), seen::add));

        UUID window = UUID.randomUUID(), pane = UUID.randomUUID();
        ActionEntry entry = model.action("dev.x.tool.run").orElseThrow();
        assertThat(entry.defaultBinding()).hasValue("cmd+alt+j");
        entry.invoke(new Contributions.Invocation(window, Optional.of(pane)));
        model.action("dev.x.tool.boom").orElseThrow().invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(seen).singleElement().satisfies(context -> {
            assertThat(context.window().id()).isEqualTo(window);
            assertThat(context.pane()).get().extracting(handle -> handle.id()).isEqualTo(pane);
        });
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);

        run.setTitle("Run Now");
        run.setEnabled(false);
        assertThat(entry.title()).isEqualTo("Run Now");
        assertThat(entry.enabled()).isFalse();
        run.close();
        assertThat(model.action("dev.x.tool.run")).isEmpty();
        assertThatIllegalArgumentException().as("a closed action can no longer be placed")
            .isThrownBy(() -> ui.toolbar().add(ToolbarItem.action("dev.x.tool.run")));
    }

    @Test void actionsCarryTheirShortcutContextAndPlatformDefaults() {
        ui.actions().register(ActionSpec.of("dev.x.tool.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
            .withContext(ShortcutContext.panel("dev.x.tool.hosts")), context -> { });
        ui.actions().register(ActionSpec.of("dev.x.tool.save", "Save").withDefaultBinding("cmd+s")
            .withContext(ShortcutContext.content()), context -> { });
        ui.actions().register(ActionSpec.of("dev.x.tool.here", "Here").withContext(ShortcutContext.terminal()), context -> { });
        ActionEntry created = model.action("dev.x.tool.new").orElseThrow();
        assertThat(created.defaultBinding()).contains("cmd+n");
        assertThat(created.otherDefault()).contains("ctrl+n");
        assertThat(created.context()).isEqualTo(KeyContext.panel("dev.x.tool.hosts"));
        assertThat(created.extension().defaultFor(false)).contains("ctrl+n");
        assertThat(model.action("dev.x.tool.save").orElseThrow().context()).as("content means this plugin's tabs")
            .isEqualTo(KeyContext.content("dev.x.tool"));
        assertThat(model.action("dev.x.tool.save").orElseThrow().extension().defaultFor(false)).contains("cmd+s");
        assertThat(model.action("dev.x.tool.here").orElseThrow().context()).isEqualTo(KeyContext.TERMINAL);
        assertThatIllegalArgumentException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.tool.far", "Far")
            .withContext(ShortcutContext.panel("dev.y.hosts")), context -> { }));
    }

    @Test void placementsAcceptOnlyThisPluginsActions() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        Subscription button = ui.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
        ui.toolbar().add(ToolbarItem.menu("file", "Tool", List.of("dev.x.tool.run")));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.toolbar().add(ToolbarItem.action("dev.x.tool.absent")));
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.toolbar().add(ToolbarItem.menu("file", "Tool", List.of("dev.x.tool.run", "new_tab"))));
        assertThat(model.toolbar()).hasSize(2);
        button.close();
        button.close();
        assertThat(model.toolbar()).singleElement().isInstanceOf(ToolbarEntry.Dropdown.class);
    }

    @Test void menusAreMutableTreesPushedAsSnapshots() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        PluginMenu view = ui.menus().standard(StandardMenu.VIEW);
        Subscription first = view.add("dev.x.tool.run");
        view.addSeparator();
        PluginMenu more = view.submenu("More");
        more.add("dev.x.tool.run");
        assertThat(model.menus()).singleElement().satisfies(section -> assertThat(section.entries()).containsExactly(
            new MenuEntry.Item("dev.x.tool.run"), new MenuEntry.Separator(),
            new MenuEntry.Submenu("More", List.of(new MenuEntry.Item("dev.x.tool.run")))));
        first.close();
        more.close();
        assertThat(model.menus().get(0).entries()).containsExactly(new MenuEntry.Separator());
        view.clear();
        assertThat(model.menus().get(0).entries()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> view.add("new_tab"));
        assertThatIllegalArgumentException().isThrownBy(() -> view.submenu(" "));

        ui.menus().create("dev.x.tool.menu", "Tool");
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.x.tool.menu", "Again"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.other.menu", "Foreign"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.menus().create("dev.x.tool.other", " "));
        ui.menus().terminalContext().add("dev.x.tool.run");
        assertThat(model.menus()).hasSize(3);
        view.close();
        assertThat(model.menus()).hasSize(2);
    }

    @Test void progressUsesOwnedActionsAndIgnoresClosedUpdates() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), c -> {});
        var progress = ui.statusBar().addProgress(new StatusItemSpec("dev.x.tool.progress", Side.RIGHT, 5));
        var state = new dev.jasper.sdk.ui.StatusProgressState("Copying", "20 MiB left", "Half complete",
            java.util.OptionalDouble.of(.5), "dev.x.tool.run", null);
        progress.update(state);
        assertThat(model.status().getFirst().progress().fraction()).hasValue(.5);
        assertThat(model.status().getFirst().progress().accessibleDescription()).isEqualTo("Half complete");
        assertThatIllegalArgumentException().isThrownBy(() -> progress.update(new dev.jasper.sdk.ui.StatusProgressState(
            "", "", "", java.util.OptionalDouble.empty(), null, "foreign.cancel")));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.statusBar().add(new StatusItemSpec("dev.x.tool.progress", Side.RIGHT, 0)));
        progress.close();
        progress.update(state);
        assertThat(model.status()).isEmpty();
    }

    @Test void statusItemsAndAppearance() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        StatusItem item = ui.statusBar().add(new StatusItemSpec("dev.x.tool.state", Side.RIGHT, 5));
        item.setText("Idle");
        item.setTooltip("Tool state");
        item.setAction("dev.x.tool.run");
        assertThatIllegalArgumentException().isThrownBy(() -> item.setAction("new_tab"));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.statusBar().add(new StatusItemSpec("dev.other.state", Side.LEFT, 0)));
        assertThat(model.status()).singleElement().satisfies(entry -> {
            assertThat(entry.left()).isFalse();
            assertThat(entry.text()).isEqualTo("Idle");
            assertThat(entry.actionId()).isEqualTo("dev.x.tool.run");
        });
        item.setAction(null);
        assertThat(model.status().get(0).actionId()).isNull();

        List<Variant> changes = new ArrayList<>();
        Subscription watching = ui.appearance().onChanged(changes::add);
        assertThat(ui.appearance().variant()).isEqualTo(Variant.DARK);
        variant = Variant.LIGHT;
        List.copyOf(themeHandlers).forEach(handler -> handler.accept(Variant.LIGHT));
        assertThat(ui.appearance().variant()).isEqualTo(Variant.LIGHT);
        assertThat(changes).containsExactly(Variant.LIGHT);
        watching.close();
        assertThat(themeHandlers).isEmpty();
        assertThat(ui.icon("search").getIconWidth()).isPositive();
        assertThatIllegalArgumentException().isThrownBy(() -> ui.icon("missing"));
    }

    @Test void closeAllRemovesEverythingAndAClosedContextRejectsRegistration() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        ui.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
        ui.menus().standard(StandardMenu.FILE).add("dev.x.tool.run");
        StatusItem item = ui.statusBar().add(new StatusItemSpec("dev.x.tool.state", Side.LEFT, 0));
        ui.closeAll();
        assertThat(model.actions()).isEmpty();
        assertThat(model.toolbar()).isEmpty();
        assertThat(model.menus()).isEmpty();
        assertThat(model.status()).isEmpty();
        assertThatCode(() -> item.setText("after close")).doesNotThrowAnyException();
        open.set(false);
        assertThatIllegalStateException().isThrownBy(() -> ui.actions().register(ActionSpec.of("dev.x.tool.late", "Late"), c -> { }));
        assertThatIllegalStateException().isThrownBy(() -> ui.menus().standard(StandardMenu.FILE));
    }

    @Test void registrationOffTheUiThreadIsRejectedAndCloseIsPosted() {
        List<Runnable> posted = new ArrayList<>();
        AtomicBoolean onUi = new AtomicBoolean(true);
        var other = new HostedUi("dev.x.tool", model, containment, posted::add, onUi::get, () -> true,
            HostedUiTest.class.getClassLoader(), () -> Variant.DARK, handler -> () -> { }, auxiliary, terminals);
        PluginAction action = other.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        var progress = other.statusBar().addProgress(new StatusItemSpec("dev.x.tool.progress", Side.RIGHT, 0));
        onUi.set(false);
        assertThatIllegalStateException().isThrownBy(() -> other.statusBar().addProgress(new StatusItemSpec("dev.x.tool.second", Side.RIGHT, 0)));
        assertThatIllegalStateException().isThrownBy(() -> progress.update(new dev.jasper.sdk.ui.StatusProgressState(
            "", "", "", java.util.OptionalDouble.empty(), null, null)));
        assertThatIllegalStateException().isThrownBy(() -> progress.setVisible(false));
        progress.close();
        assertThat(model.status()).hasSize(1);
        assertThatIllegalStateException().isThrownBy(() -> other.actions().register(ActionSpec.of("dev.x.tool.b", "B"), c -> { }))
            .withMessageContaining("UI thread");
        assertThatIllegalStateException().isThrownBy(() -> action.setEnabled(false));
        action.close();
        assertThat(model.action("dev.x.tool.run")).as("removal waits for the UI thread").isPresent();
        onUi.set(true);
        posted.forEach(Runnable::run);
        assertThat(model.action("dev.x.tool.run")).isEmpty();
        assertThat(model.status()).isEmpty();
    }

    @Test void panelsAndRailReachTheModelWithContainedFactories() {
        ui.actions().register(ActionSpec.of("dev.x.tool.open", "Open"), context -> { });
        List<dev.jasper.sdk.ui.PanelHost> hosts = new ArrayList<>();
        Subscription panel = ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.hosts", "Hosts", "file",
            dev.jasper.sdk.ui.Anchor.RIGHT), host -> { hosts.add(host); return view(); });
        ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.broken", "Broken", "file",
            dev.jasper.sdk.ui.Anchor.LEFT), host -> { throw new IllegalStateException("factory failure"); });
        assertThatIllegalArgumentException().isThrownBy(() -> ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.other.panel", "Foreign",
            "file", dev.jasper.sdk.ui.Anchor.LEFT), host -> view()));

        ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.files", "Files", "file",
            dev.jasper.sdk.ui.Anchor.BOTTOM).withSplit(), host -> view());

        var entry = model.panels().get(0);
        assertThat(entry.defaultSlot()).isEqualTo(dev.jasper.app.dock.Slot.RIGHT_TOP);
        assertThat(model.panels().get(1).defaultSlot()).isEqualTo(dev.jasper.app.dock.Slot.LEFT_TOP);
        assertThat(model.panels().get(2).defaultSlot()).as("a split panel starts in its side's secondary half")
            .isEqualTo(dev.jasper.app.dock.Slot.BOTTOM_RIGHT);
        UUID window = UUID.randomUUID();
        boolean[] visible = {true};
        var site = new dev.jasper.app.contributions.PanelSite(window, () -> visible[0] = true, () -> visible[0] = false, () -> visible[0]);
        assertThat(entry.factory().apply(site)).isNotNull();
        assertThat(hosts).singleElement().satisfies(host -> {
            assertThat(host.window().id()).isEqualTo(window);
            host.hide();
            assertThat(host.visible()).isFalse();
        });
        assertThat(model.panels().get(1).factory().apply(site)).as("a failed factory yields no component").isNull();
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);

        ui.rail().add("dev.x.tool.open");
        assertThatIllegalArgumentException().isThrownBy(() -> ui.rail().add("new_tab"));
        assertThat(model.railActions()).containsExactly("dev.x.tool.open");
        var requests = new ArrayList<dev.jasper.app.contributions.Contributions.PanelRequest>();
        model.onPanelRequest(requests::add);
        ui.panels().toggle("dev.x.tool.hosts", hosts.getFirst().window());
        assertThat(requests).containsExactly(new dev.jasper.app.contributions.Contributions.PanelRequest(window, "dev.x.tool.hosts", dev.jasper.app.contributions.Contributions.PanelRequest.Op.TOGGLE));
        panel.close();
        assertThat(model.panels()).hasSize(2);
        ui.closeAll();
        assertThat(model.panels()).isEmpty();
        assertThat(model.railActions()).isEmpty();
    }

    @Test void panelHeadersAreWindowOwnedContainFailuresAndBecomeInertWhenDiscarded() {
        var hosts = new ArrayList<dev.jasper.sdk.ui.PanelHost>();
        var calls = new ArrayList<String>();
        ui.panels().register(new PanelSpec("dev.x.tool.header", "Header", "file", Anchor.LEFT),
            host -> { hosts.add(host); return view(); });
        var first = new ArrayList<dev.jasper.app.contributions.PanelHeaderState>();
        var second = new ArrayList<dev.jasper.app.contributions.PanelHeaderState>();
        var firstSite = new dev.jasper.app.contributions.PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> true, first::add);
        var secondSite = new dev.jasper.app.contributions.PanelSite(UUID.randomUUID(), () -> {}, () -> {}, () -> true, second::add);
        var factory = model.panels().getFirst().factory();
        factory.apply(firstSite); factory.apply(secondSite);
        var icon = "file";
        hosts.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("5", false, List.of(
            new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("first")),
            new dev.jasper.sdk.ui.PanelHeader.Action("Failure", icon, () -> { throw new IllegalStateException("header failed"); }),
            new dev.jasper.sdk.ui.PanelHeader.Action("Linkage failure", icon, () -> { throw new NoClassDefFoundError("header dependency"); }))));
        hosts.getLast().setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", true, List.of(
            new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("second")))));
        assertThat(first.getLast().detail()).isEqualTo("5");
        assertThat(second.getLast().detail()).isEqualTo("amp");
        var stale = first.getLast();
        stale.actions().forEach(action -> action.invoke().run());
        assertThat(containment.failures("dev.x.tool")).isEqualTo(2);
        firstSite.notifyClosed();
        assertThat(first.getLast()).isEqualTo(dev.jasper.app.contributions.PanelHeaderState.EMPTY);
        hosts.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("late", false, List.of()));
        stale.actions().forEach(action -> action.invoke().run());
        second.getLast().actions().getFirst().invoke().run();
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1);
        assertThat(calls).containsExactly("first", "second");
        assertThat(containment.failures("dev.x.tool")).isEqualTo(2);
    }

    @Test void everyAnchorAndSplitCombinationMapsToItsOwnSlot() {
        record Case(String id, Anchor anchor, boolean split, Slot slot) { }
        var cases = List.of(
            new Case("dev.x.tool.left", Anchor.LEFT, false, Slot.LEFT_TOP),
            new Case("dev.x.tool.leftsplit", Anchor.LEFT, true, Slot.LEFT_BOTTOM),
            new Case("dev.x.tool.right", Anchor.RIGHT, false, Slot.RIGHT_TOP),
            new Case("dev.x.tool.rightsplit", Anchor.RIGHT, true, Slot.RIGHT_BOTTOM),
            new Case("dev.x.tool.bottom", Anchor.BOTTOM, false, Slot.BOTTOM_LEFT),
            new Case("dev.x.tool.bottomsplit", Anchor.BOTTOM, true, Slot.BOTTOM_RIGHT));
        for (Case each : cases) {
            var spec = new PanelSpec(each.id(), "Panel", "file", each.anchor());
            ui.panels().register(each.split() ? spec.withSplit() : spec, host -> view());
        }
        assertThat(model.panels()).hasSize(6);
        for (Case each : cases)
            assertThat(model.panels()).filteredOn(panel -> panel.id().equals(each.id())).singleElement()
                .satisfies(panel -> assertThat(panel.defaultSlot()).as("%s anchor, split %s", each.anchor(), each.split())
                    .isEqualTo(each.slot()));
    }

    @Test void windowsAndDialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {
        var manager = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        assertThat(ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true)))
            .as("an open singleton is the same window").isSameAs(manager);
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.other.window", "Foreign", new java.awt.Dimension(10, 10), false)));
        manager.setContent(view());
        manager.show();
        List<String> events = new ArrayList<>();
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        var prompt = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", manager, true));
        var trust = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Trust?", terminals.windowHandle(terminalFixture.addWindow()), false));
        assertThatIllegalArgumentException().as("an owner this application did not create")
            .isThrownBy(() -> ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Bad", new dev.jasper.sdk.WindowOwner() { }, true)));
        assertThat(auxiliary.open()).hasSize(3);
        assertThat(auxiliary.open().get(0).requestClose()).as("a throwing guard is contained and allows the close").isTrue();
        assertThat(events).containsExactly("closed");
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);
        assertThat(auxiliary.open()).as("the owner took its dialog with it").hasSize(1);
        ui.closeAll();
        assertThat(auxiliary.open()).isEmpty();
        assertThatCode(() -> { prompt.close(); trust.close(); manager.setTitle("after close"); }).doesNotThrowAnyException();
    }


}
