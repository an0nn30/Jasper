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
        onUi.set(false);
        assertThatIllegalStateException().isThrownBy(() -> other.actions().register(ActionSpec.of("dev.x.tool.b", "B"), c -> { }))
            .withMessageContaining("UI thread");
        assertThatIllegalStateException().isThrownBy(() -> action.setEnabled(false));
        action.close();
        assertThat(model.action("dev.x.tool.run")).as("removal waits for the UI thread").isPresent();
        onUi.set(true);
        posted.forEach(Runnable::run);
        assertThat(model.action("dev.x.tool.run")).isEmpty();
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
}
