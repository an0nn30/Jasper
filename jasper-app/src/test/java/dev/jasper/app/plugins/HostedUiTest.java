package dev.jasper.app.plugins;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
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
        new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null));
    private final HostedUi ui = new HostedUi("dev.x.tool", model, containment, Runnable::run, () -> true, open::get,
        HostedUiTest.class.getClassLoader(), () -> variant,
        handler -> { themeHandlers.add(handler); return () -> themeHandlers.remove(handler); }, auxiliary, terminals);

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

    @Test void placementsAcceptOnlyThisPluginsActions() {
        ui.actions().register(ActionSpec.of("dev.x.tool.run", "Run"), context -> { });
        Subscription button = ui.toolbar().add(ToolbarItem.action("dev.x.tool.run"));
        ui.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run")));
        assertThatIllegalArgumentException().isThrownBy(() -> ui.toolbar().add(ToolbarItem.action("dev.x.tool.absent")));
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Tool", List.of("dev.x.tool.run", "new_tab"))));
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
        assertThat(ui.appearance().icon("dev/jasper/app/icons/search.svg").getIconWidth()).isPositive();
        assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon("nope.svg"));
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
        Subscription panel = ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.hosts", "Hosts", new javax.swing.ImageIcon(),
            dev.jasper.sdk.ui.Anchor.RIGHT), host -> { hosts.add(host); return new javax.swing.JLabel("hosts"); });
        ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.broken", "Broken", new javax.swing.ImageIcon(),
            dev.jasper.sdk.ui.Anchor.LEFT), host -> { throw new IllegalStateException("factory failure"); });
        assertThatIllegalArgumentException().isThrownBy(() -> ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.other.panel", "Foreign",
            new javax.swing.ImageIcon(), dev.jasper.sdk.ui.Anchor.LEFT), host -> new javax.swing.JLabel()));

        var entry = model.panels().get(0);
        assertThat(entry.defaultRegion()).isEqualTo(dev.jasper.app.contributions.PanelRegion.RIGHT);
        UUID window = UUID.randomUUID();
        boolean[] visible = {true};
        var site = new dev.jasper.app.contributions.PanelSite(window, () -> visible[0] = true, () -> visible[0] = false, () -> visible[0]);
        assertThat(entry.factory().apply(site)).isInstanceOf(javax.swing.JLabel.class);
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
        assertThat(model.panels()).hasSize(1);
        ui.closeAll();
        assertThat(model.panels()).isEmpty();
        assertThat(model.railActions()).isEmpty();
    }

    @Test void windowsAndDialogsAreBuiltByTheApplicationAndClosedWithThePlugin() {
        var manager = ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true));
        assertThat(ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.x.tool.manager", "Manager", new java.awt.Dimension(640, 480), true)))
            .as("an open singleton is the same window").isSameAs(manager);
        assertThatIllegalArgumentException().isThrownBy(() ->
            ui.windows().create(new dev.jasper.sdk.ui.WindowSpec("dev.other.window", "Foreign", new java.awt.Dimension(10, 10), false)));
        manager.setContent(new javax.swing.JLabel("content"));
        manager.show();
        List<String> events = new ArrayList<>();
        manager.onClosing(() -> { throw new IllegalStateException("guard failure"); });
        manager.onClosed(() -> events.add("closed"));
        var prompt = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Unlock", manager, true));
        var trust = ui.windows().dialog(new dev.jasper.sdk.ui.DialogSpec("Trust?", terminals.windowHandle(UUID.randomUUID()), false));
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
@Test void metalKeepsPluginSvgAndSubscriptionContracts() {
    new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.RETRO,
        dev.jasper.app.config.Appearance.DARK);
    try {
        variant = Variant.LIGHT;
        assertThat(ui.appearance().variant()).isEqualTo(Variant.LIGHT);
        var icon = ui.appearance().icon("dev/jasper/app/icons/search.svg");
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); }
        finally { graphics.dispose(); }
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon("absent.svg"));
        var events = new java.util.ArrayList<Variant>();
        var subscription = ui.appearance().onChanged(events::add);
        subscription.close();
        assertThat(themeHandlers).isEmpty();
    } finally { ui.closeAll(); new dev.jasper.app.appearance.ThemeController(); }
}


    @Test void selectsEverySdkCatalogChoiceAndValidatesBothArguments() throws Exception {
        for (boolean retro : new boolean[]{false, true}) {
        new dev.jasper.app.appearance.ThemeController(retro ? dev.jasper.app.config.ThemeStyle.RETRO
            : dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.LIGHT);
        try {
            for (var choice : dev.jasper.sdk.ui.OldGnomeIcon.values()) {
                var icon = ui.appearance().icon("dev/jasper/app/icons/search.svg", choice);
                assertThat(icon.getIconWidth()).isEqualTo(16);
                assertThat(dev.jasper.app.platform.AppIcons.forToolbar(icon).getIconWidth()).isEqualTo(retro ? 28 : 16);
                if (retro) {
                    try (var input = getClass().getResourceAsStream("/dev/jasper/app/icons/oldgnome-sdk/16/" + choice.name() + ".png")) {
                        var source = javax.imageio.ImageIO.read(input);
                        var actual = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var expected = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var g = actual.createGraphics();
                        try { icon.paintIcon(new javax.swing.JLabel(), g, 0, 0); } finally { g.dispose(); }
                        g = expected.createGraphics();
                        try { g.drawImage(source, 0, 0, null); } finally { g.dispose(); }
                        assertThat(actual.getRGB(0,0,16,16,null,0,16)).as(choice.name())
                            .isEqualTo(expected.getRGB(0,0,16,16,null,0,16));
                    }
                }
            }
            assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon("missing.svg", dev.jasper.sdk.ui.OldGnomeIcon.LOCK));
            assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon(null, dev.jasper.sdk.ui.OldGnomeIcon.LOCK));
            assertThatNullPointerException().isThrownBy(() -> ui.appearance().icon("dev/jasper/app/icons/search.svg", null));
            var legacy = ui.appearance().icon("dev/jasper/app/icons/search.svg");
            assertThat(dev.jasper.app.platform.AppIcons.forToolbar(legacy)).isSameAs(legacy);
        } finally { new dev.jasper.app.appearance.ThemeController(); }
        }
    }

    @Test void namedIconsBelongToHostRatherThanPluginLoader() {
        var isolated = new HostedUi("dev.x.tool", model, containment, Runnable::run, () -> true, open::get,
            new ClassLoader(null) {}, () -> variant,
            handler -> () -> {}, auxiliary, terminals);
        try {
            for (boolean retro : new boolean[]{false, true}) {
                new dev.jasper.app.appearance.ThemeController(retro ? dev.jasper.app.config.ThemeStyle.RETRO
                    : dev.jasper.app.config.ThemeStyle.MODERN, dev.jasper.app.config.Appearance.LIGHT);
                for (var name : dev.jasper.sdk.ui.IconName.values()) {
                    var icon = isolated.appearance().icon(name);
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getIconHeight()).isEqualTo(16);
                    assertThat(dev.jasper.app.platform.AppIcons.forToolbar(icon).getIconWidth()).isEqualTo(retro ? 28 : 16);
                }
                assertThatNullPointerException().isThrownBy(() -> isolated.appearance().icon((dev.jasper.sdk.ui.IconName) null));
                assertThatIllegalArgumentException().isThrownBy(() -> isolated.appearance().icon("dev/jasper/app/icons/search.svg"));
            }
        } finally { isolated.closeAll(); new dev.jasper.app.appearance.ThemeController(); }
    }
}
