package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelMode;
import dev.jasper.sdk.ui.PanelPlacement;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Presentation;
import dev.jasper.sdk.ui.ShortcutContext;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.TreeItem;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TabNamePiece;
import dev.jasper.sdk.terminal.TabNamePieceSpec;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.nio.file.Path;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import dev.jasper.sdk.terminal.RemoteDirectory;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.files.FileBrowser;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.files.FileSource;
import dev.jasper.sdk.files.FileSourceSpec;
import java.util.concurrent.CompletionStage;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.SettingsPanel;

import static dev.jasper.sdk.activity.ActivityEvent.State.FAILED;
import static dev.jasper.sdk.activity.ActivityEvent.State.PROGRESS;
import static dev.jasper.sdk.activity.ActivityEvent.State.STARTED;
import static dev.jasper.sdk.activity.ActivityEvent.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runtime semantics every implementation of the SDK must share. Test bodies run off the UI thread. */
public abstract class PluginContractTest {
    /** A service interface owned by this package, which harnesses register as the provider's export. */
    public interface Greeter { String greet(String name); }

    /** A plugin-owned payload. */
    public record Ping(int n) { }

    static final Topic<Ping> PING = Topic.of("test.alpha.ping", Ping.class);

    private ContractHarness h;

    /** A fresh runtime; the suite closes it after each test. */
    protected abstract ContractHarness newHarness();

    @BeforeEach void open() { h = newHarness(); }
    @AfterEach void close() { h.close(); }

    private static PluginInfo info(String id) { return new PluginInfo(id, id, "1.0.0", Set.of()); }

    private PluginContext started(String id, Set<String> requires) {
        var context = new AtomicReference<PluginContext>();
        h.start(info(id), requires, Set.of(), context::set);
        assertThat(h.active(id)).as("%s started", id).isTrue();
        return context.get();
    }

    /**
     * Spec 16.1.9: a plugin's kit admits SDK 0.18's controls; the SDK refuses a misplaced Notice or Separator, a NONE
     * swatch and a miscounted choice when the view is built, before any kit sees it.
     */
    @Test void aPluginsKitAdmitsZeroEighteenControlsAndTheSdkRefusesMisplacedOnes() {
        var context = started("test.controls", Set.of());
        h.ui(() -> {
            var spec = dev.jasper.sdk.testing.KitContractTest.zeroEighteen("Plain FTP sends the password unencrypted");
            var view = context.kit().create(spec, event -> { });
            assertThat(view.spec()).isEqualTo(spec);
            view.close();
            var ready = ControlState.READY;
            var text = new Nodes.Text("a", ready, "A", Nodes.TextRole.BODY);
            var separator = new Nodes.Separator("separator", ready);
            var one = List.of(new Option("one", "One", Optional.empty()));
            var five = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new Option("o" + i, "Option " + i, Optional.empty())).toList();
            List<java.util.function.Supplier<ViewSpec>> refused = List.of(
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(separator, text))),
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.VERTICAL, List.of(text, separator, new Nodes.Text("b", ready, "B", Nodes.TextRole.BODY)))),
                () -> new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(new Nodes.Notice("notice", ready, Nodes.Severity.INFO, "Saved")))),
                () -> new ViewSpec(new Nodes.Icon("swatch", ready, "file", Nodes.Hue.NONE, Nodes.IconSize.SWATCH, "None")),
                () -> new ViewSpec(new Nodes.Choice("pick", ready, Nodes.ChoiceKind.EXCLUSIVE, one, "one", "Pick", "")),
                () -> new ViewSpec(new Nodes.Choice("pick", ready, Nodes.ChoiceKind.EXCLUSIVE, five, "o0", "Pick", "")),
                () -> new ViewSpec(new Nodes.Choice("tabs", ready, Nodes.ChoiceKind.TABS, one, "one", "Tabs", "")));
            for (var bad : refused) assertThatIllegalArgumentException().isThrownBy(bad::get);
        });
    }

    /** Spec 16.1.9 (plan 3c): a plugin's kit admits SDK 0.18's table vocabulary and refuses misplaced cell nodes, a rich-faced cell dropdown, selected headings, doubled empty states and extra tree values. */
    @Test void aPluginsKitAdmitsZeroEighteenTablesAndRefusesMisplacedOnes() {
        var context = started("test.tables", Set.of());
        h.ui(() -> {
            for (boolean rows : new boolean[] {false, true}) {
                var spec = dev.jasper.sdk.testing.KitContractTest.tables("card", rows);
                var view = context.kit().create(spec, event -> { });
                assertThat(view.spec()).isEqualTo(spec);
                view.close();
            }
            var ready = ControlState.READY;
            var columns = List.of(new Column("c", "C", Nodes.TextRole.BODY, false), new Column("d", "D", Nodes.TextRole.BODY, false));
            var go = new Nodes.Button("go", ready, "Go", "", Nodes.ButtonKind.SECONDARY, "go", "");
            var more = new Nodes.Button("more", ready, "More", "", Nodes.ButtonKind.SECONDARY, "more", "");
            var text = new Nodes.Text("t", ready, "T", Nodes.TextRole.BODY);
            var heading = TableRow.heading("h", "Group", java.util.OptionalInt.of(1), "group");
            var icon = new Nodes.Icon("e.icon", ready, "server", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Servers");
            var rich = new Nodes.Choice("rich", ready, Nodes.ChoiceKind.DROPDOWN, List.of(new dev.jasper.sdk.ui.Option("card", "Card", Optional.empty())), "card", "Display", "", "",
                Optional.empty(), Optional.of(new Nodes.PickerFace("Card", "", "", Nodes.Hue.NONE, Nodes.PickerDensity.DEFAULT)));
            var three = new TreeItem("x", "X", Optional.empty(), true, List.of(), java.util.OptionalInt.empty(), false, "", Optional.empty(), List.of("1", "2", "3"), Optional.empty());
            List<java.util.function.Supplier<ViewSpec>> refused = List.of(
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "",
                    Map.of("c", go), Optional.empty())), Set.of(), "", List.of())),
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(new TableRow("r", Map.of("c", "x"), Optional.empty(), false, Optional.empty(), "",
                    Map.of("c", text), Optional.empty())), Set.of(), "", List.of())),
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(new TableRow("r", Map.of(), Optional.empty(), false, Optional.empty(), "",
                    Map.of("c", rich), Optional.empty())), Set.of(), "", List.of())),
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(heading), Set.of("h"), "", List.of())),
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(), Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD,
                    Optional.of(new Nodes.Empty("e", ready, icon, "Nothing", "", List.of())), "Nothing here")),
                () -> new ViewSpec(new Nodes.Table("t", ready, columns, List.of(), Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.STANDARD,
                    Optional.of(new Nodes.Empty("e", ready, icon, "Nothing", "", List.of(go, more))), "")),
                () -> new ViewSpec(new Nodes.Tree("tree", ready, List.of(three), Set.of(), Set.of(), "", columns)));
            for (var bad : refused)
                assertThatIllegalArgumentException().isThrownBy(() -> context.kit().create(bad.get(), event -> { }));
        });
    }

    @Test void semanticPresentationIconsValidateOwnershipAtEveryBoundary() {
        var started=new AtomicReference<PluginContext>();
        h.start(info("test.icons",Capabilities.PALETTE_CONTRIBUTE,Capabilities.SESSION_PROVIDE),Set.of(),Set.of(),started::set);
        var context=started.get();
        UUID window=h.addTerminalWindow();
        h.ui(() -> {
            var registration=context.kit().registerIcon(new dev.jasper.sdk.ui.IconAsset("test.icons:own","M 2 2 L 14 14"));
            context.actions().register(ActionSpec.of("test.icons.menuaction","Menu action"),e -> {});
            var status=context.statusBar().add(new StatusItemSpec("test.icons.status",Side.RIGHT,0));
            var tab=context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("content","Content"),null,kitView(context));
            var panel=new AtomicReference<PanelHost>();
            context.panels().register(new PanelSpec("test.icons.panel","Panel","file",Anchor.LEFT),host -> {panel.set(host);return kitView(context);});
            h.openPanel("test.icons.panel");
            for(String name:List.of("missing","foreign:own")) {
                assertThatIllegalArgumentException().as("action "+name).isThrownBy(() -> context.actions().register(ActionSpec.of("test.icons.bad","Bad").withIcon(name),e -> {}));
                assertThatIllegalArgumentException().as("panel "+name).isThrownBy(() -> context.panels().register(new PanelSpec("test.icons.badpanel","Bad",name,Anchor.LEFT),host -> kitView(context)));
                assertThatIllegalArgumentException().as("toolbar "+name).isThrownBy(() -> context.toolbar().add(ToolbarItem.menu(name,"Menu",List.of("test.icons.menuaction"))));
                assertThatIllegalArgumentException().as("status "+name).isThrownBy(() -> status.setIcon(name));
                assertThatIllegalArgumentException().as("content "+name).isThrownBy(() -> tab.setPresentation(new dev.jasper.sdk.ui.ContentPresentation("Content","",name,false,"","")));
                assertThatIllegalArgumentException().as("header "+name).isThrownBy(() -> panel.get().setHeader(new dev.jasper.sdk.ui.PanelHeader("",false,List.of(new dev.jasper.sdk.ui.PanelHeader.Action("Action",name,() -> {})))));
                assertThatIllegalArgumentException().as("notification "+name).isThrownBy(() -> context.notifications().group().post(NotificationSpec.of(Severity.INFO,"Title").withContent("Body").withIcon(name)));
                assertThatIllegalArgumentException().as("scope "+name).isThrownBy(() -> context.palette().register(iconScope(name)));
                assertThatIllegalArgumentException().as("session "+name).isThrownBy(() -> context.terminals().openTab(context.terminals().window(window).orElseThrow(),OpenRequest.session(new SessionSpec("Bad",Optional.of(name),ExitPolicy.KEEP_OPEN,pending -> {}))));
            }
            assertThat(h.openRequests()).as("invalid icons must not create terminal panes").isEmpty();
            for(String name:List.of("file","test.icons:own")) {
                context.actions().register(ActionSpec.of("test.icons.action"+(name.equals("file")?"1":"2"),"Action").withIcon(name),e -> {});
                status.setIcon(name);tab.setPresentation(new dev.jasper.sdk.ui.ContentPresentation("Content","",name,false,"",""));
                context.toolbar().add(ToolbarItem.menu(name,"Menu",List.of("test.icons.menuaction")));
                panel.get().setHeader(new dev.jasper.sdk.ui.PanelHeader("",false,List.of(new dev.jasper.sdk.ui.PanelHeader.Action("Action",name,() -> {}))));
                context.notifications().group().post(NotificationSpec.of(Severity.INFO,"Title").withContent("Body").withIcon(name));
            }
            status.setIcon(null);tab.setPresentation(new dev.jasper.sdk.ui.ContentPresentation("Content","",null,false,"",""));
            registration.close();
            assertThatIllegalArgumentException().isThrownBy(() -> status.setIcon("test.icons:own"));
            status.close();tab.close();h.closePanel("test.icons.panel");
            assertThatCode(() -> {
                status.setIcon("missing");
                tab.setPresentation(new dev.jasper.sdk.ui.ContentPresentation("Content","","missing",false,"",""));
                panel.get().setHeader(new dev.jasper.sdk.ui.PanelHeader("",false,List.of(new dev.jasper.sdk.ui.PanelHeader.Action("Action","missing",() -> {}))));
            }).doesNotThrowAnyException();
        });
    }
    private static PaletteScope iconScope(String icon) {
        return new PaletteScope() {
            public ScopeSpec spec(){return ScopeSpec.of("test.icons.scope","Scope","Search",List.of(PASTE)).withIcon(icon);}
            public PaletteResults search(String query,PaletteQuery context){return PaletteResults.none();}
            public void execute(PaletteRow row,PaletteVerb verb,PaletteQuery context){}
            public Subscription onChanged(Runnable listener){return () -> {};}
        };
    }

    /** A content tab in {@code window}, owned by {@code context}'s plugin, which shows. */
    private static dev.jasper.sdk.ui.ContentTab openContent(PluginContext context, UUID window, String key, String title) {
        return context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec(key, title), context.terminals().window(window).orElseThrow(), kitView(context));
    }

    private static dev.jasper.sdk.ui.UiView kitView(PluginContext context) {
        return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("body",dev.jasper.sdk.ui.ControlState.READY,"Content",dev.jasper.sdk.ui.Nodes.TextRole.BODY)), e -> {});
    }

    @Test void ownedViewsMountReplaceAndMoveWithExistingGuards() throws Exception {
        var context = new AtomicReference<PluginContext>();
        var other = new AtomicReference<PluginContext>();
        h.start(info("test.owned"),Set.of(),Set.of(),context::set);
        h.start(info("test.foreign"),Set.of(),Set.of(),other::set);
        javax.swing.SwingUtilities.invokeAndWait(() -> h.ui(() -> {
            var ctx=context.get();var disposed=new AtomicInteger();
            var descriptor=new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Custom("node",dev.jasper.sdk.ui.ControlState.READY,"document",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,"Document"));
            var view=ctx.kit().create(descriptor,e -> {});
            view.registerContent(new dev.jasper.sdk.ui.CustomContent("document",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,new javax.swing.JTextArea("retain"),disposed::incrementAndGet));
            var alien=other.get().kit().create(descriptor,e -> {});
            var owner=ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("owner","Owner"),null,kitView(ctx));
            var window=ctx.windows().dialog(new DialogSpec("Window",owner,false));
            assertThatThrownBy(() -> window.setContent(alien)).isInstanceOf(IllegalArgumentException.class);
            window.setContent(view);window.setContent(view);
            assertThat(h.viewRoute(view)).as("dialog content keeps the chrome slot").isEqualTo("CHROME");
            var veto=window.onClosing(() -> false);
            assertThat(h.requestClose("dialog")).isFalse();assertThat(disposed.get()).isZero();
            veto.close();window.close();window.close();assertThat(disposed.get()).isEqualTo(1);
            var document=ctx.kit().create(descriptor,e -> {});
            document.registerContent(new dev.jasper.sdk.ui.CustomContent("document",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,new javax.swing.JTextArea("retained"),disposed::incrementAndGet));
            assertThatThrownBy(() -> ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("foreign","Foreign"),null,alien)).isInstanceOf(IllegalArgumentException.class);
            assertThat(h.viewRoute(document)).as("a view before it is mounted").isEqualTo("CHROME");
            var tab=ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("doc","Document"),null,document);
            assertThat(h.viewRoute(document)).as("workspace content takes the workspace slot").isEqualTo("WORKSPACE");
            var home=tab.window();
            tab.moveToNewWindow();assertThat(tab.window()).isNotEqualTo(home);assertThat(h.viewRoute(document)).as("a move keeps it").isEqualTo("WORKSPACE");
            tab.moveToWindow(home);assertThat(tab.window()).isEqualTo(home);assertThat(h.viewRoute(document)).isEqualTo("WORKSPACE");
            var guard=ctx.windows().onTabsClosing((tabs,batch) -> CompletableFuture.completedFuture(false));
            assertThat(tab.requestClose().join()).isFalse();assertThat(disposed.get()).isEqualTo(1);
            guard.close();assertThat(tab.requestClose().join()).isTrue();assertThat(disposed.get()).isEqualTo(2);
            assertThatThrownBy(document::spec).isInstanceOf(IllegalStateException.class);
            alien.close();
        }));
    }

    @Test void pluginStopClosesOverlaysAndExplicitCloseReleasesTheirReservation() {
        var context = started("test.overlay", Set.of());
        UUID window = h.addTerminalWindow();
        var closed = new AtomicInteger();
        h.ui(() -> {
            var owner = context.terminals().window(window).orElseThrow();
            var first = context.windows().overlay(new dev.jasper.sdk.ui.OverlaySpec("First", owner));
            first.onClosed(closed::incrementAndGet); first.show(); first.close(); first.close();
            var second = context.windows().overlay(new dev.jasper.sdk.ui.OverlaySpec("Second", owner));
            second.onClosed(closed::incrementAndGet); second.show();
        });
        assertThat(closed.get()).isEqualTo(1);
        h.stopAll(); h.flush();
        assertThat(closed.get()).isEqualTo(2);
        assertThat(h.windows()).isEmpty();
    }

    @Test void overlaysReserveAnOwnerAcrossPluginsAndCloseWithTheOwner() {
        var first = new AtomicReference<PluginContext>();
        var second = new AtomicReference<PluginContext>();
        h.start(new PluginInfo("test.overlay1", "First", "1.0.0", Set.of()), Set.of(), Set.of(), first::set);
        h.start(new PluginInfo("test.overlay2", "Second", "1.0.0", Set.of()), Set.of(), Set.of(), second::set);
        UUID stays = h.addTerminalWindow();
        // The overlay's owner closes when a move takes its only tab to another window (full windows spec 5.3).
        UUID window = h.addTerminalWindow();
        UUID tab = h.addTerminalTab(window, "local");
        h.addTerminalPane(tab, "shell", Path.of("/"));
        var closed = new AtomicInteger();
        var ownerRef = new AtomicReference<dev.jasper.sdk.terminal.WindowHandle>();
        h.ui(() -> {
            var owner = first.get().terminals().window(window).orElseThrow();
            ownerRef.set(owner);
            var overlay = first.get().windows().overlay(new dev.jasper.sdk.ui.OverlaySpec("Connecting", owner));
            overlay.onClosed(closed::incrementAndGet);
            assertThatIllegalStateException().isThrownBy(() -> second.get().windows().overlay(
                new dev.jasper.sdk.ui.OverlaySpec("Other", second.get().terminals().window(window).orElseThrow())));
        });
        h.moveTab(tab, stays); h.flush();
        assertThat(closed.get()).isEqualTo(1);
        assertThat(h.windows()).isEmpty();
        h.ui(() -> assertThatIllegalArgumentException().isThrownBy(() -> first.get().windows().overlay(
            new dev.jasper.sdk.ui.OverlaySpec("Closed", ownerRef.get()))));
    }

    @Test void deliveryIsQueuedOrderedAndNeverReentrant() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> {
                seen.add("in:" + ping.n());
                if (ping.n() == 1) context.events().publish(PING, new Ping(3));
                seen.add("out:" + ping.n());
            });
        });
        h.ui(() -> {
            alpha.get().events().publish(PING, new Ping(1));
            alpha.get().events().publish(PING, new Ping(2));
            seen.add("published");
        });
        h.flush();
        assertThat(seen).containsExactly("published", "in:1", "out:1", "in:2", "out:2", "in:3", "out:3");
    }

    @Test void onlyTheOwnerPublishesAndPayloadTypesCannotConflict() {
        PluginContext alpha = started("test.alpha", Set.of());
        PluginContext beta = started("test.beta", Set.of());
        assertThatIllegalArgumentException().isThrownBy(() -> beta.events().publish(PING, new Ping(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
            alpha.events().publish(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded()));
        assertThatIllegalArgumentException().isThrownBy(() -> alpha.events().publish(Activities.TOPIC,
            new ActivityEvent(UUID.randomUUID(), "test.alpha", "forged", STARTED, OptionalDouble.empty(), "", Optional.empty())));
        assertThatIllegalArgumentException().as("a longer id sharing the prefix is another namespace").isThrownBy(() ->
            alpha.events().publish(Topic.of("test.alphabet.x", Ping.class), new Ping(1)));
        h.ui(() -> {
            beta.events().subscribe(PING, ping -> { });
            assertThatIllegalArgumentException().isThrownBy(() ->
                beta.events().subscribe(Topic.of("test.alpha.ping", String.class), text -> { }));
        });
    }

    @Test void closedSubscriptionsReceiveNothingAndCloseIsIdempotentFromAnyThread() {
        List<Integer> first = Collections.synchronizedList(new ArrayList<>());
        List<Integer> second = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        var subscriptions = new ArrayList<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            subscriptions.add(context.events().subscribe(PING, ping -> first.add(ping.n())));
            subscriptions.add(context.events().subscribe(PING, ping -> second.add(ping.n())));
        });
        alpha.get().events().publish(PING, new Ping(1));
        h.flush();
        h.ui(() -> { subscriptions.get(0).close(); subscriptions.get(0).close(); });
        subscriptions.get(1).close();
        subscriptions.get(1).close();
        alpha.get().events().publish(PING, new Ping(2));
        h.flush();
        assertThat(first).containsExactly(1);
        assertThat(second).containsExactly(1);
    }

    @Test void aFailingHandlerDoesNotStopOtherSubscribers() {
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> { throw new IllegalStateException("handler failure"); });
            context.events().subscribe(PING, ping -> seen.add(ping.n()));
        });
        alpha.get().events().publish(PING, new Ping(7));
        h.flush();
        assertThat(seen).containsExactly(7);
    }

    @Test void activitiesAreStampedCoalescedAndEndExactlyOnce() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = new AtomicReference<ActivityHandle>();
        h.ui(() -> {
            ActivityHandle activity = alpha.activities().begin(
                new ActivitySpec("Upload", "starting", Optional.empty(), Optional.empty()));
            handle.set(activity);
            activity.progress(0.1, "a");
            activity.progress(0.2, "b");
            activity.progress(0.9, "almost");
            assertThatIllegalArgumentException().isThrownBy(() -> activity.progress(1.5, "too far"));
        });
        assertThat(alpha.activities().current()).singleElement()
            .satisfies(event -> assertThat(event.detail()).isEqualTo("almost"));
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, PROGRESS);
        ActivityEvent progress = h.activityLog().get(1);
        assertThat(progress.id()).isEqualTo(handle.get().id());
        assertThat(progress.sourcePluginId()).isEqualTo("test.alpha");
        assertThat(progress.title()).isEqualTo("Upload");
        assertThat(progress.fraction()).hasValue(0.9);

        handle.get().detail("still going");
        h.flush();
        assertThat(h.activityLog().get(2).fraction()).hasValue(0.9);
        assertThat(h.activityLog().get(2).detail()).isEqualTo("still going");

        h.ui(() -> {
            handle.get().progress(1.0, "dropped because the end arrives first");
            handle.get().succeed("done");
            handle.get().fail("ignored");
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state)
            .containsExactly(STARTED, PROGRESS, PROGRESS, SUCCEEDED);
        assertThat(h.activityLog().get(3).detail()).isEqualTo("done");
        assertThat(alpha.activities().current()).isEmpty();
    }

    @Test void activityInputPhasesRetainIdentityFractionAndAction() {
        PluginContext alpha = started("test.alpha", Set.of());
        var task = alpha.activities().begin(new ActivitySpec("Upload", "start", Optional.of("test.alpha.open"), Optional.empty()));
        h.flush();
        task.progress(.4, "uploaded"); h.flush();
        task.needsInput("choose destination"); h.flush();
        task.detail("still waiting"); h.flush();
        assertThat(alpha.activities().current()).singleElement().satisfies(event -> {
            assertThat(event.state()).isEqualTo(ActivityEvent.State.NEEDS_INPUT);
            assertThat(event.fraction()).hasValue(.4);
            assertThat(event.activateActionId()).contains("test.alpha.open");
        });
        task.resume("continuing"); h.flush();
        assertThat(h.notifications()).as("waiting and resuming are live work, not outcomes").isEmpty();
        task.progress(.8, "nearly"); h.flush();
        task.succeed("done"); h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, PROGRESS,
            ActivityEvent.State.NEEDS_INPUT, ActivityEvent.State.NEEDS_INPUT, ActivityEvent.State.RESUMED, PROGRESS, SUCCEEDED);
        assertThat(h.activityLog()).allSatisfy(event -> assertThat(event.id()).isEqualTo(task.id()));
    }

    @Test void pendingCallbacksCannotConsumeReportsAcrossPhaseBoundaries() {
        PluginContext alpha = started("test.alpha", Set.of());
        h.ui(() -> {
            var task = alpha.activities().begin(ActivitySpec.of("Upload"));
            task.progress(.1, "old running");
            task.needsInput("first wait");
            task.detail("old waiting");
            task.resume("first resume");
            task.progress(.4, "old resumed");
            task.needsInput("second wait");
            task.needsInput("latest waiting");
            task.progress(.5, "latest fraction");
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED,
            ActivityEvent.State.NEEDS_INPUT, ActivityEvent.State.RESUMED, ActivityEvent.State.NEEDS_INPUT,
            ActivityEvent.State.NEEDS_INPUT);
        assertThat(h.activityLog()).extracting(ActivityEvent::detail).containsExactly("", "first wait", "first resume", "second wait", "latest fraction");
        assertThat(alpha.activities().current()).singleElement().satisfies(event -> assertThat(event.fraction()).hasValue(.5));
    }

    @Test void repeatingRunningPhaseCoalescesDetailWithoutAnotherTransition() {
        PluginContext alpha = started("test.alpha", Set.of());
        h.ui(() -> {
            var task = alpha.activities().begin(ActivitySpec.of("Upload"));
            task.needsInput("choose");
            task.resume("continue");
            task.resume("first detail");
            task.resume("last detail");
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED,
            ActivityEvent.State.NEEDS_INPUT, ActivityEvent.State.RESUMED, PROGRESS);
        assertThat(h.activityLog().getLast().detail()).isEqualTo("last detail");
    }

    @Test void stoppedWaitingHandleCannotDeliverPendingReportsOrResurrect() {
        PluginContext alpha = started("test.alpha", Set.of());
        var task = alpha.activities().begin(ActivitySpec.of("Upload"));
        h.flush();
        h.ui(() -> {
            task.needsInput("input");
            task.progress(.4, "pending");
            h.stopAll();
            task.resume("late"); task.needsInput("late"); task.detail("late");
            task.progress(.9, "late"); task.succeed("late"); task.fail("late"); task.cancelled();
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, ActivityEvent.State.NEEDS_INPUT, FAILED);
        assertThat(alpha.activities().current()).isEmpty();
    }

    @Test void activitiesLeftOpenFailWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = alpha.activities().begin(ActivitySpec.of("Sync"));
        h.flush();
        h.stopAll();
        handle.detail("late"); handle.needsInput("late"); handle.resume("late");
        handle.succeed("late"); handle.fail("late"); handle.cancelled();
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);
        assertThat(h.active("test.alpha")).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> alpha.activities().begin(ActivitySpec.of("late")));
    }

    @Test void connectionsAreStampedOrderedCoalescedAndCloseOnce() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = new AtomicReference<ConnectionHandle>();
        h.ui(() -> {
            ConnectionHandle connection = alpha.activities().connection(new ConnectionSpec("Tunnels via db", "reconnecting",
                ConnectionEvent.State.DEGRADED, Optional.of("test.alpha.show")));
            handle.set(connection);
            connection.degraded("attempt 2");
            connection.degraded("attempt 3");
            connection.down("gave up");
            connection.up("back");
            connection.up("back again");
        });
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state)
            .containsExactly(ConnectionEvent.State.DEGRADED, ConnectionEvent.State.DOWN, ConnectionEvent.State.UP);
        assertThat(h.connectionLog()).extracting(ConnectionEvent::detail).containsExactly("attempt 3", "gave up", "back again");
        ConnectionEvent first = h.connectionLog().getFirst();
        assertThat(first.id()).isEqualTo(handle.get().id());
        assertThat(first.sourcePluginId()).isEqualTo("test.alpha");
        assertThat(first.title()).isEqualTo("Tunnels via db");
        assertThat(first.activateActionId()).contains("test.alpha.show");

        handle.get().degraded("delivered on its own");
        h.flush();
        h.ui(() -> {
            handle.get().close();
            handle.get().close();
            handle.get().up("ignored after close");
        });
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state).containsExactly(ConnectionEvent.State.DEGRADED,
            ConnectionEvent.State.DOWN, ConnectionEvent.State.UP, ConnectionEvent.State.DEGRADED, ConnectionEvent.State.CLOSED);
        assertThat(h.connectionLog().getLast().detail()).isEmpty();
    }

    @Test void connectionsLeftOpenCloseWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        alpha.activities().connection(ConnectionSpec.of("Tunnel", ConnectionEvent.State.UP));
        h.flush();
        h.stopAll();
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state)
            .containsExactly(ConnectionEvent.State.UP, ConnectionEvent.State.CLOSED);
        assertThatIllegalStateException().isThrownBy(() -> alpha.activities().connection(ConnectionSpec.of("late", ConnectionEvent.State.UP)));
    }

    @Test void servicesCommitAfterStartAndAreBuiltOncePerRequirer() {
        List<String> built = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            context.services().publishPerConsumer(Greeter.class, consumer -> {
                built.add(consumer.id());
                return name -> "hi " + name + " from " + consumer.id();
            });
            assertThat(context.services().find(Greeter.class)).as("not visible before commit").isEmpty();
        });
        var greeting = new AtomicReference<String>();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> {
            greeting.set(context.services().require(Greeter.class).greet("x"));
            assertThat(context.services().require(Greeter.class))
                .isSameAs(context.services().find(Greeter.class).orElseThrow());
        });
        assertThat(greeting).hasValue("hi x from test.consumer");
        assertThat(built).containsExactly("test.consumer");

        PluginContext stranger = started("test.stranger", Set.of());
        assertThat(stranger.services().find(Greeter.class)).as("no requires, no service").isEmpty();
        assertThatThrownBy(() -> stranger.services().require(Greeter.class))
            .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test void publicationIsLimitedToStartAndToInterfacesPublishedOnce() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(String.class, "text"));
            context.services().publish(Greeter.class, name -> name);
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(Greeter.class, name -> name));
        });
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.late"), Set.of(), Set.of(), alpha::set);
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            alpha.get().services().publish(Runnable.class, () -> { })));
    }

    @Test void aFailedStartRollsEverythingBackAndClosesTheContext() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var failed = new AtomicReference<PluginContext>();
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            failed.set(context);
            context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> seen.add("handler ran"));
            context.services().publish(Greeter.class, name -> name);
            context.activities().begin(ActivitySpec.of("Doomed"));
            throw new IllegalStateException("start failure");
        });
        assertThat(h.active("test.provider")).isFalse();

        var hardStarted = new AtomicBoolean();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> hardStarted.set(true));
        assertThat(hardStarted).as("hard dependents are skipped").isFalse();
        assertThat(h.active("test.consumer")).isFalse();

        var optional = new AtomicReference<Optional<Greeter>>();
        h.start(info("test.optional"), Set.of(), Set.of("test.provider"),
            context -> optional.set(context.services().find(Greeter.class)));
        assertThat(h.active("test.optional")).isTrue();
        assertThat(optional.get()).as("the publication was discarded").isEmpty();

        h.publishApp(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
        h.flush();
        assertThat(seen).isEmpty();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);

        PluginContext closed = failed.get();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> closed.events().subscribe(PING, ping -> { })));
        assertThatIllegalStateException().isThrownBy(() -> closed.activities().begin(ActivitySpec.of("late")));
        assertThatThrownBy(() -> closed.background().execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(closed.log()).isNotNull();
    }

    @Test void actionsAreNamespacedUniqueInvocableAndContained() {
        List<String> ran = Collections.synchronizedList(new ArrayList<>());
        var run = new AtomicReference<PluginAction>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            run.set(context.actions().register(ActionSpec.of("test.alpha.run", "Run").withDefaultBinding("cmd+alt+j"),
                invoked -> ran.add(invoked.window().id() + "/" + invoked.pane().map(pane -> pane.id().toString()).orElse("none"))));
            context.actions().register(ActionSpec.of("test.alpha.boom", "Boom"), invoked -> { throw new IllegalStateException("handler failure"); });
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.run", "Twice"), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.beta.run", "Foreign"), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alphabet.run", "Foreign"), invoked -> { }));
        });
        assertThat(h.actions()).containsExactly("test.alpha.run|Run|true", "test.alpha.boom|Boom|true");
        UUID window = UUID.randomUUID(), pane = UUID.randomUUID();
        h.ui(() -> {
            assertThat(h.invoke("test.alpha.run", window, pane)).isTrue();
            assertThat(h.invoke("test.alpha.run", window, null)).isTrue();
            assertThat(h.invoke("test.alpha.boom", window, null)).as("a throwing handler is contained").isTrue();
            assertThat(h.invoke("test.alpha.absent", window, null)).isFalse();
            run.get().setTitle("Run Now");
            run.get().setEnabled(false);
            assertThat(h.invoke("test.alpha.run", window, null)).isFalse();
        });
        assertThat(ran).containsExactly(window + "/" + pane, window + "/none");
        assertThat(h.actions()).containsExactly("test.alpha.run|Run Now|false", "test.alpha.boom|Boom|true");
    }

    @Test void shortcutContextsNameOnlyThePluginsOwnPanels() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
                .withContext(ShortcutContext.panel("test.alpha.hosts")), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.save", "Save").withContext(ShortcutContext.content()), invoked -> { });
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.foreign", "Foreign")
                .withContext(ShortcutContext.panel("test.beta.hosts")), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.near", "Near")
                .withContext(ShortcutContext.panel("test.alphabet.hosts")), invoked -> { }));
        });
        assertThat(h.active("test.alpha")).isTrue();
        assertThat(h.actions()).containsExactly("test.alpha.new|New|true", "test.alpha.save|Save|true");
    }

    @Test void placementsNameOnlyThePluginsOwnActionsAndVanishWithThem() {
        var run = new AtomicReference<PluginAction>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            run.set(context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { }));
            context.actions().register(ActionSpec.of("test.alpha.stop", "Stop"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("test.alpha.run"));
            context.toolbar().add(ToolbarItem.menu("file", "Alpha", List.of("test.alpha.run", "test.alpha.stop")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.toolbar().add(ToolbarItem.action("new_tab")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().standard(StandardMenu.FILE).add("test.alpha.absent"));
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context ->
            assertThatIllegalArgumentException().as("another plugin's action").isThrownBy(() -> context.toolbar().add(ToolbarItem.action("test.alpha.run"))));
        assertThat(h.toolbar()).containsExactly("button:test.alpha.run", "menu:Alpha:test.alpha.run,test.alpha.stop");
        h.ui(() -> run.get().close());
        assertThat(h.toolbar()).containsExactly("menu:Alpha:test.alpha.stop");
        h.ui(() -> assertThatIllegalArgumentException().as("a closed action cannot be placed again")
            .isThrownBy(() -> alpha.get().toolbar().add(ToolbarItem.action("test.alpha.run"))));
    }

    @Test void menusAreMutableTreesInIndependentSections() {
        var view = new AtomicReference<PluginMenu>();
        var first = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            PluginMenu section = context.menus().standard(StandardMenu.VIEW);
            view.set(section);
            first.set(section.add("test.alpha.run"));
            section.addSeparator();
            section.submenu("More").add("test.alpha.run");
            context.menus().standard(StandardMenu.VIEW).add("test.alpha.run");
            context.menus().create("test.alpha.menu", "Alpha").add("test.alpha.run");
            context.menus().terminalContext().add("test.alpha.run");
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().create("test.alpha.menu", "Again"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.menus().create("test.beta.menu", "Foreign"));
        });
        assertThat(h.menu("VIEW")).containsExactly("item:test.alpha.run", "---", "submenu:More", "  item:test.alpha.run",
            "===", "item:test.alpha.run");
        assertThat(h.menu("top:test.alpha.menu")).containsExactly("item:test.alpha.run");
        assertThat(h.menu("context")).containsExactly("item:test.alpha.run");
        assertThat(h.menu("FILE")).isEmpty();
        h.ui(() -> { first.get().close(); first.get().close(); });
        assertThat(h.menu("VIEW")).containsExactly("---", "submenu:More", "  item:test.alpha.run", "===", "item:test.alpha.run");
        h.ui(() -> view.get().clear());
        assertThat(h.menu("VIEW")).containsExactly("===", "item:test.alpha.run");
        h.ui(() -> view.get().close());
        assertThat(h.menu("VIEW")).containsExactly("item:test.alpha.run");
    }

    @Test void theToolsMenuTakesSectionsLikeTheOtherStandardMenus() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TOOLS).add("test.alpha.run");
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.beta.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TOOLS).add("test.beta.run");
        });
        assertThat(h.menu("TOOLS")).containsExactly("item:test.alpha.run", "===", "item:test.beta.run");
    }

    /** SDK 0.18: Session and Transfer are built-in menus with no items of their own, taking sections as Tools does. */
    @Test void theSessionAndTransferMenusTakeSectionsLikeTools() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.connect", "Connect"), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.upload", "Upload"), invoked -> { });
            context.menus().standard(StandardMenu.SESSION).add("test.alpha.connect");
            context.menus().standard(StandardMenu.TRANSFER).add("test.alpha.upload");
        });
        h.start(info("test.beta"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.beta.connect", "Connect"), invoked -> { });
            context.menus().standard(StandardMenu.SESSION).add("test.beta.connect");
        });
        assertThat(h.menu("SESSION")).containsExactly("item:test.alpha.connect", "===", "item:test.beta.connect");
        assertThat(h.menu("TRANSFER")).containsExactly("item:test.alpha.upload");
        assertThat(h.menu("TOOLS")).isEmpty();
    }

    @Test void statusItemsAreOrderedGlobalAndInertAfterClose() {
        var late = new AtomicReference<StatusItem>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            late.set(context.statusBar().add(new StatusItemSpec("test.alpha.late", Side.RIGHT, 20)));
            StatusItem early = context.statusBar().add(new StatusItemSpec("test.alpha.early", Side.LEFT, 10));
            late.get().setText("Late");
            early.setText("two\nlines");
            early.setTooltip("tip");
            early.setAction("test.alpha.run");
            assertThatIllegalArgumentException().isThrownBy(() -> early.setAction("new_tab"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.statusBar().add(new StatusItemSpec("test.alpha.early", Side.LEFT, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> context.statusBar().add(new StatusItemSpec("test.beta.item", Side.LEFT, 0)));
        });
        assertThat(h.status()).containsExactly("test.alpha.early|LEFT|two lines|tip|test.alpha.run", "test.alpha.late|RIGHT|Late||");
        h.ui(() -> late.get().setVisible(false));
        assertThat(h.status()).containsExactly("test.alpha.early|LEFT|two lines|tip|test.alpha.run");
        h.ui(() -> { late.get().close(); late.get().close(); assertThatCode(() -> late.get().setText("after close")).doesNotThrowAnyException(); });
        assertThat(h.status()).hasSize(1);
    }

    @Test void editorSchemesAreUiThreadValuesWithUniqueIds() {
        var a = started("test.schemes", Set.of()).appearance();
        h.ui(() -> {
            var schemes = a.editorSchemes();
            assertThat(schemes).extracting(dev.jasper.sdk.ui.EditorScheme::id).doesNotHaveDuplicates();
            for (var scheme : schemes)
                assertThat(scheme.id()).endsWith(scheme.variant() == Variant.DARK ? "/dark" : "/light");
            assertThatThrownBy(schemes::clear).as("unmodifiable").isInstanceOf(UnsupportedOperationException.class);
        });
        assertThatThrownBy(() -> java.util.concurrent.CompletableFuture.runAsync(a::editorSchemes).join())
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test void independentWorkspaceChangesExposeValuesInsideCallbacksAndRespectLifetime() {
        var context = started("test.workspace", Set.of());
        var a = context.appearance();
        var seen = new AtomicInteger();
        var disposed = new AtomicInteger();
        var chrome = new AtomicInteger();
        var subscription = new AtomicReference<dev.jasper.sdk.Subscription>();
        java.awt.Color first = java.awt.Color.RED, second = java.awt.Color.BLUE;
        var palette = new dev.jasper.sdk.ui.WorkspacePalette(first, first, first, first, first, first, first, first, first, first, first, first, first);
        var next = new dev.jasper.sdk.ui.WorkspacePalette(second, first, first, first, first, first, first, first, first, first, first, first, first);
        var editor = new dev.jasper.sdk.ui.EditorPalette(first, first, first, first, first, first, first);
        var styles = new java.util.EnumMap<dev.jasper.sdk.ui.SyntaxRole, dev.jasper.sdk.ui.SyntaxStyle>(dev.jasper.sdk.ui.SyntaxRole.class);
        for (var role : dev.jasper.sdk.ui.SyntaxRole.values()) styles.put(role, new dev.jasper.sdk.ui.SyntaxStyle(first, true, false));
        var syntax = new dev.jasper.sdk.ui.SyntaxPalette(styles);
        h.setWorkspace(Variant.DARK, palette, editor, syntax); h.flush();
        h.ui(() -> {
            a.onChanged(value -> chrome.incrementAndGet());
            a.onWorkspaceChanged(value -> { throw new IllegalStateException("contained appearance failure"); });
            subscription.set(a.onWorkspaceChanged(value -> {
                seen.incrementAndGet();
                assertThat(value).isEqualTo(Variant.DARK);
                assertThat(a.workspacePalette()).isEqualTo(next);
                assertThat(a.editorPalette()).isEqualTo(editor);
                assertThat(a.syntaxPalette()).isEqualTo(syntax);
            }));
            var closed = a.onWorkspaceChanged(value -> disposed.incrementAndGet()); closed.close();
        });
        h.setWorkspace(Variant.DARK, next, editor, syntax); h.flush();
        assertThat(seen).hasValue(1); assertThat(chrome).hasValue(1); assertThat(disposed).hasValue(0);
        h.setWorkspace(Variant.DARK, next, editor, syntax); h.flush();
        assertThat(seen).hasValue(1);
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(a::workspaceVariant).join()).isEqualTo(Variant.DARK);
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(a::variant).join()).isEqualTo(Variant.DARK);
        for (var getter : java.util.List.<Runnable>of(() -> a.workspacePalette(), () -> a.editorPalette(), () -> a.syntaxPalette()))
            assertThatThrownBy(() -> java.util.concurrent.CompletableFuture.runAsync(getter).join()).hasCauseInstanceOf(IllegalStateException.class);
        subscription.get().close(); h.flush();
        h.ui(() -> a.onWorkspaceChanged(value -> disposed.incrementAndGet()));
        h.stopAll(); h.setWorkspace(Variant.LIGHT, palette, editor, syntax); h.flush();
        assertThat(seen).hasValue(1); assertThat(disposed).hasValue(0);
    }

    @Test void workspaceOpacityStartsOpaqueAndChangesAreWorkspaceChanges() {
        var context = started("test.glass", Set.of());
        var a = context.appearance();
        var seen = new AtomicInteger();
        var chrome = new AtomicInteger();
        var subscription = new AtomicReference<dev.jasper.sdk.Subscription>();
        var component = new javax.swing.JPanel();
        h.ui(() -> {
            assertThat(a.workspaceOpacity(new javax.swing.JPanel())).isEqualTo(1.0);
            assertThatNullPointerException().isThrownBy(() -> a.workspaceOpacity(null));
            a.onChanged(value -> chrome.incrementAndGet());
            subscription.set(a.onWorkspaceChanged(value -> {
                seen.incrementAndGet();
                assertThat(a.workspaceOpacity(component)).isEqualTo(0.8);
            }));
        });
        h.setWorkspaceOpacity(0.8); h.flush();
        assertThat(seen).hasValue(1); assertThat(chrome).hasValue(0);
        h.setWorkspaceOpacity(0.8); h.flush();
        assertThat(seen).hasValue(1);
        subscription.get().close();
        h.setWorkspaceOpacity(1.0); h.flush();
        assertThat(seen).hasValue(1); assertThat(chrome).hasValue(0);
    }

    @Test void stoppingOrFailingRemovesEveryContributionAndAppearanceFollowsTheHost() {
        List<Variant> seen = Collections.synchronizedList(new ArrayList<>());
        var failed = new AtomicReference<PluginContext>();
        h.start(info("test.doomed"), Set.of(), Set.of(), context -> {
            failed.set(context);
            context.actions().register(ActionSpec.of("test.doomed.run", "Run"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("test.doomed.run"));
            context.statusBar().add(new StatusItemSpec("test.doomed.item", Side.LEFT, 0)).setText("Doomed");
            throw new IllegalStateException("start failure");
        });
        assertThat(h.actions()).isEmpty();
        assertThat(h.toolbar()).isEmpty();
        assertThat(h.status()).isEmpty();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            failed.get().actions().register(ActionSpec.of("test.doomed.late", "Late"), invoked -> { })));

        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { });
            context.menus().standard(StandardMenu.TAB).add("test.alpha.run");
            context.appearance().onChanged(seen::add);
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.bad", "Bad").withIcon("missing"), e -> {}));
        });
        assertThat(alpha.get().appearance().variant()).isEqualTo(Variant.DARK);
        h.setVariant(Variant.LIGHT);
        h.flush();
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> alpha.get().appearance().variant()).join()).isEqualTo(Variant.LIGHT);
        assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> alpha.get().appearance().workspaceVariant()).join()).isEqualTo(Variant.LIGHT);
        assertThat(seen).containsExactly(Variant.LIGHT);
        h.stopAll();
        assertThat(h.actions()).isEmpty();
        assertThat(h.menu("TAB")).isEmpty();
    }

    @Test void terminalTypographyIsPublishedBeforeCallbacksAcrossPluginsAndThreads() throws Exception {
        var initial = new dev.jasper.sdk.ui.TerminalTypography("DialogInput", 13.5, List.of("Monospaced"), true, 1.25);
        var next = new dev.jasper.sdk.ui.TerminalTypography("Monospaced", 15.25, List.of(), false, 1.5);
        h.ui(() -> h.setTerminalTypography(initial)); h.flush();
        var first = new AtomicReference<PluginContext>();
        var second = new AtomicReference<PluginContext>();
        var seen = new ArrayList<dev.jasper.sdk.ui.TerminalTypography>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            first.set(context);
            context.appearance().onTerminalTypographyChanged(value -> {
                assertThat(javax.swing.SwingUtilities.isEventDispatchThread()).isTrue();
                assertThat(context.appearance().terminalTypography()).isEqualTo(value);
                seen.add(value);
            });
        });
        h.start(info("test.beta"), Set.of(), Set.of(), second::set);
        assertThat(first.get().appearance().terminalTypography()).isEqualTo(initial);
        assertThat(second.get().appearance().terminalTypography()).isEqualTo(initial);
        assertThat(CompletableFuture.supplyAsync(() -> first.get().appearance().terminalTypography()).get(5, TimeUnit.SECONDS)).isEqualTo(initial);
        assertThat(seen).isEmpty();
        h.ui(() -> h.setTerminalTypography(new dev.jasper.sdk.ui.TerminalTypography(
            "DialogInput", 13.5, List.of("Monospaced"), true, 1.25))); h.flush();
        assertThat(seen).isEmpty();
        h.ui(() -> h.setTerminalTypography(next)); h.flush();
        assertThat(seen).containsExactly(next);
        assertThat(second.get().appearance().terminalTypography()).isEqualTo(next);
        h.stopAll();
        h.ui(() -> h.setTerminalTypography(initial)); h.flush();
        assertThat(seen).containsExactly(next);
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            first.get().appearance().onTerminalTypographyChanged(value -> { })));
    }

    @Test void terminalTypographyCallbacksCanUnsubscribeAndFailuresDoNotBlockOthers() {
        var next = new dev.jasper.sdk.ui.TerminalTypography("DialogInput", 14, List.of(), false, 1);
        var later = new dev.jasper.sdk.ui.TerminalTypography("DialogInput", 16, List.of(), false, 1);
        var seen = new ArrayList<String>();
        var self = new AtomicReference<Subscription>();
        var skipped = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            self.set(context.appearance().onTerminalTypographyChanged(value -> {
                seen.add("first"); self.get().close(); skipped.get().close();
            }));
            skipped.set(context.appearance().onTerminalTypographyChanged(value -> seen.add("skipped")));
            context.appearance().onTerminalTypographyChanged(value -> { throw new IllegalStateException("bad typography callback"); });
            context.appearance().onTerminalTypographyChanged(value -> seen.add("last"));
        });
        h.ui(() -> h.setTerminalTypography(next)); h.flush();
        assertThat(seen).containsExactly("first", "last");
        h.ui(() -> h.setTerminalTypography(later)); h.flush();
        assertThat(seen).containsExactly("first", "last", "last");
    }

    /** SDK 0.18.2: the presentation is the host's, the same for every plugin and on any thread, and a theme change keeps it. */
    @Test void presentationIsTheHostsForEveryPluginOnAnyThreadAndThroughThemeChanges() throws Exception {
        var classic = new Presentation(true, Presentation.Icons.CLASSIC, Presentation.ToolWindows.STRIPES, Presentation.TitleBar.NATIVE,
            Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD, Optional.of("IBM Plex Sans"), Optional.of("IBM Plex Mono"), 12, 28);
        h.setPresentation(classic);
        var first = new AtomicReference<PluginContext>(); var second = new AtomicReference<PluginContext>();
        h.start(new PluginInfo("test.lookone", "One", "1.0.0", Set.of()), Set.of(), Set.of(), first::set);
        h.start(new PluginInfo("test.looktwo", "Two", "1.0.0", Set.of()), Set.of(), Set.of(), second::set);
        assertThat(first.get().appearance().presentation()).isEqualTo(classic);
        assertThat(second.get().appearance().presentation()).isEqualTo(classic);
        var offThread = CompletableFuture.supplyAsync(() -> first.get().appearance().presentation()).get(5, TimeUnit.SECONDS);
        assertThat(offThread).isEqualTo(classic);
        h.setVariant(Variant.LIGHT); h.flush();
        assertThat(first.get().appearance().presentation()).isEqualTo(classic);
    }

    @Test void aPanelMayNameOneOfThePluginsOwnActionsAsItsToggle() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.show", "Toggle Hosts"), invoked -> { });
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "file", Anchor.LEFT)
                .withToggleActionId("test.alpha.show"), host -> kitView(context));
            context.panels().register(new PanelSpec("test.alpha.files", "Files", "file", Anchor.RIGHT), host -> kitView(context));
            for (String toggle : List.of("test.alpha.absent", "test.beta.show", "new_tab"))
                assertThatIllegalArgumentException().as(toggle).isThrownBy(() -> context.panels().register(
                    new PanelSpec("test.alpha.other", "Other", "file", Anchor.LEFT).withToggleActionId(toggle), host -> kitView(context)));
        });
        assertThat(h.panels()).as("a failed assertion above stops the plugin and removes its panels")
            .containsExactly("test.alpha.hosts|Hosts|LEFT|test.alpha.show", "test.alpha.files|Files|RIGHT");
    }

    @Test void aSplitPanelKeepsItsSplitBesideItsToggleAndStartsInTheSecondaryHalf() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.show", "Toggle Files"), invoked -> { });
            context.panels().register(new PanelSpec("test.alpha.files", "Files", "file", Anchor.LEFT).withSplit()
                .withToggleActionId("test.alpha.show"), host -> kitView(context));
            context.panels().register(new PanelSpec("test.alpha.log", "Log", "file", Anchor.BOTTOM).withSplit(),
                host -> kitView(context));
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "file", Anchor.RIGHT), host -> kitView(context));
        });
        assertThat(h.panels()).containsExactly("test.alpha.files|Files|LEFT+split|test.alpha.show",
            "test.alpha.log|Log|BOTTOM+split", "test.alpha.hosts|Hosts|RIGHT");
    }

    @Test void panelsAreNamespacedLazyInAWindowAndContained() {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            registration.set(context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "file", Anchor.RIGHT),
                host -> { hosts.add(host); return kitView(context); }));
            context.panels().register(new PanelSpec("test.alpha.broken", "Broken", "file", Anchor.BOTTOM),
                host -> { throw new IllegalStateException("factory failure"); });
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.alpha.hosts", "Twice", "file", Anchor.LEFT), host -> kitView(context)));
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.beta.panel", "Foreign", "file", Anchor.LEFT), host -> kitView(context)));
        });
        assertThat(h.panels()).containsExactly("test.alpha.hosts|Hosts|RIGHT", "test.alpha.broken|Broken|BOTTOM");
        assertThat(hosts).as("no instance until a window shows the panel").isEmpty();
        UUID window = h.addTerminalWindow();
        h.ui(() -> {
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(h.openPanel("test.alpha.broken")).as("a throwing factory is contained").isNull();
            assertThat(h.openPanel("test.alpha.absent")).isNull();
        });
        assertThat(hosts).singleElement().satisfies(host -> assertThat(host.window().id()).isEqualTo(window));
        h.ui(() -> registration.get().close());
        assertThat(h.panels()).containsExactly("test.alpha.broken|Broken|BOTTOM");
        h.stopAll();
        assertThat(h.panels()).isEmpty();
    }

    /** Full windows spec 9: a panel shown from a window is built in that window's dock, once, and not in another's. */
    @Test void panelsShowInTheWindowTheyAreAskedFrom() {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "file", Anchor.RIGHT),
                host -> { hosts.add(host); return kitView(context); });
        });
        UUID a = h.addTerminalWindow(), b = h.addTerminalWindow();
        h.addTerminalPane(h.addTerminalTab(a, "zsh"), "zsh", Path.of("/"));
        h.addTerminalPane(h.addTerminalTab(b, "zsh"), "zsh", Path.of("/"));
        h.ui(() -> {
            var second = alpha.get().terminals().window(b).orElseThrow();
            alpha.get().panels().show("test.alpha.hosts", second);
            alpha.get().panels().show("test.alpha.hosts", second);
            assertThat(hosts).as("b's instance, built once; a has none").singleElement().satisfies(host -> {
                assertThat(host.window().id()).isEqualTo(b);
                assertThat(host.visible()).isTrue();
            });
            assertThatIllegalArgumentException().isThrownBy(() -> alpha.get().panels().show("test.alpha.absent", second));
            assertThatIllegalArgumentException().isThrownBy(() -> alpha.get().panels().show("test.beta.hosts", second));
        });
    }

    /** Full windows spec 9: no window is special, so a window handle has no main-window query. */
    @Test void windowHandlesHaveNoMainWindow() {
        assertThat(java.util.Arrays.stream(dev.jasper.sdk.terminal.WindowHandle.class.getMethods()).map(java.lang.reflect.Method::getName))
            .doesNotContain("isMain");
    }

    /**
     * PanelHost.setHeader (0.14.2) on a kit (ViewSpec) panel: metadata the host shows in the panel's one header row,
     * replaced wholesale, cleared by EMPTY, contained when an action fails and ignored after the panel closes. The panel
     * lives in its window's dock, so opening it again there shows the same instance.
     */
    @Test void aKitPanelsHeaderIsReplacedContainedAndIgnoredAfterClose() throws Exception {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        var context = started("test.alpha", Set.of());
        var icon = "file";
        UUID window = h.addTerminalWindow();
        javax.swing.SwingUtilities.invokeAndWait(() -> h.ui(() -> {
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "folder", Anchor.LEFT), host -> {
                hosts.add(host);
                host.setHeader(new dev.jasper.sdk.ui.PanelHeader(String.valueOf(hosts.size() + 2), true, List.of(
                    new dev.jasper.sdk.ui.PanelHeader.Action("New session", icon, () -> calls.add("new")),
                    new dev.jasper.sdk.ui.PanelHeader.Action("Broken", icon, () -> { throw new IllegalStateException("header failed"); }))));
                var C = dev.jasper.sdk.ui.ControlState.READY;
                return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Panel("hosts", C,
                    new dev.jasper.sdk.ui.Nodes.Header("hosts-header", C, "Hosts", java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                    Optional.empty(), new dev.jasper.sdk.ui.Nodes.Text("body", C, "Body", dev.jasper.sdk.ui.Nodes.TextRole.BODY), Optional.empty())), e -> { });
            });
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(hosts).as("one instance, in the window").singleElement()
                .satisfies(host -> assertThat(host.window().id()).isEqualTo(window));
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("3|true|New session;Broken");

            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "New session")).isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Broken")).as("a failing action is contained").isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Absent")).isFalse();
            assertThat(calls).containsExactly("new");

            hosts.get(0).setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("import")))));
            assertThat(h.panelHeader("test.alpha.hosts")).as("replaced wholesale").isEqualTo("amp|false|Import");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Import")).isTrue();
            assertThat(calls).containsExactly("new", "import");

            hosts.get(0).setHeader(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");
            assertThatNullPointerException().isThrownBy(() -> hosts.get(0).setHeader(null));
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");

            h.closePanel("test.alpha.hosts");
            assertThat(h.panelHeader("test.alpha.hosts")).isEqualTo("|false|-");
            hosts.get(0).setHeader(new dev.jasper.sdk.ui.PanelHeader("late", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("late")))));
            assertThat(h.panelHeader("test.alpha.hosts")).as("a closed panel publishes nothing").isEqualTo("|false|-");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", "Import")).isFalse();
            assertThat(calls).containsExactly("new", "import");
        }));
    }

    /**
     * A panel reads its placement in its factory and hears each change once. Its place is the dock's, built or not: a
     * panel moved while it is not open is built there and hears nothing, and one closed and opened again is built where
     * it last was, as its window's dock keeps it across the window's reopening. A closed instance reads the place it
     * closed in.
     */
    @Test void aPanelReadsItsPlacementAndHearsItChange() throws Exception {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> inFactory = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> heard = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> atClose = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> afterThrower = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> late = Collections.synchronizedList(new ArrayList<>());
        List<PanelPlacement> cancelled = Collections.synchronizedList(new ArrayList<>());
        var context = started("test.alpha", Set.of());
        h.addTerminalWindow();
        javax.swing.SwingUtilities.invokeAndWait(() -> h.ui(() -> {
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", "folder", Anchor.RIGHT).withSplit(), host -> {
                hosts.add(host);
                inFactory.add(host.placement());
                host.onPlacement(heard::add);
                host.onClosed(() -> atClose.add(host.placement()));
                var C = dev.jasper.sdk.ui.ControlState.READY;
                return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Panel("hosts", C,
                    new dev.jasper.sdk.ui.Nodes.Header("hosts-header", C, "Hosts", java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                    Optional.empty(), new dev.jasper.sdk.ui.Nodes.Text("body", C, "Body", dev.jasper.sdk.ui.Nodes.TextRole.BODY), Optional.empty())), e -> { });
            });
            var docked = new PanelPlacement(Anchor.RIGHT, true, PanelMode.DOCKED);
            var floating = new PanelPlacement(Anchor.RIGHT, true, PanelMode.FLOAT);
            var left = new PanelPlacement(Anchor.LEFT, false, PanelMode.DOCKED);
            var window = new PanelPlacement(Anchor.BOTTOM, false, PanelMode.WINDOW);

            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            var host = hosts.get(0);
            assertThat(inFactory).as("the spec's side and half, docked, in the factory").containsExactly(docked);
            assertThat(host.placement()).isEqualTo(docked);
            assertThat(host.placement().ownWindow()).isFalse();

            h.placePanel("test.alpha.hosts", floating);
            assertThat(heard).containsExactly(floating);
            assertThat(host.placement()).isEqualTo(floating);
            assertThat(host.placement().ownWindow()).isTrue();
            h.placePanel("test.alpha.hosts", floating);
            assertThat(heard).as("the same placement is silent").containsExactly(floating);

            host.onPlacement(placement -> { throw new IllegalStateException("placement failed"); });
            host.onPlacement(afterThrower::add);
            var unsubscribed = host.onPlacement(cancelled::add);
            h.placePanel("test.alpha.hosts", left);
            assertThat(host.placement()).isEqualTo(left);
            assertThat(host.placement().ownWindow()).isFalse();
            assertThat(heard).as("a failing handler is contained and the first still hears").containsExactly(floating, left);
            assertThat(afterThrower).as("and the ones after it").containsExactly(left);
            assertThat(cancelled).containsExactly(left);

            unsubscribed.close();
            host.onPlacement(late::add);
            assertThat(late).as("a handler added late hears nothing yet").isEmpty();
            h.placePanel("test.alpha.hosts", window);
            assertThat(host.placement()).isEqualTo(window);
            assertThat(host.placement().ownWindow()).isTrue();
            assertThat(late).containsExactly(window);
            assertThat(heard).containsExactly(floating, left, window);
            assertThat(cancelled).as("a closed registration hears no more").containsExactly(left);

            h.closePanel("test.alpha.hosts");
            assertThat(atClose).as("a panel that closes reads where it was").containsExactly(window);
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(inFactory).as("opened again, it is built where it last was").containsExactly(docked, window);
            assertThat(hosts.get(1).placement()).isEqualTo(window);
            assertThat(heard).as("and hears nothing for it").containsExactly(floating, left, window);

            h.closePanel("test.alpha.hosts");
            h.placePanel("test.alpha.hosts", floating);
            assertThat(heard).as("a panel that is not open hears nothing").containsExactly(floating, left, window);
            assertThat(late).containsExactly(window);
            assertThat(host.placement()).as("a closed instance reads the place it closed in").isEqualTo(window);
            assertThat(h.openPanel("test.alpha.hosts")).isNotNull();
            assertThat(inFactory).as("moved while it was not open, it is built there").containsExactly(docked, window, floating);
            assertThat(hosts.get(2).placement()).isEqualTo(floating);
            assertThat(hosts.get(2).placement().ownWindow()).isTrue();
            assertThat(heard).as("and no handler hears the move").containsExactly(floating, left, window);
            h.placePanel("test.alpha.hosts", floating);
            assertThat(heard).as("the same placement is silent").containsExactly(floating, left, window);
            h.closePanel("test.alpha.hosts");
            assertThat(atClose).containsExactly(window, window, floating);
        }));
    }

    /** A toolbar view tree the toolbar admits: a label, a host choice, a separator and a PRIMARY Connect. */
    private static dev.jasper.sdk.ui.ViewSpec sessionBar(String id) {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        return new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack(id, ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Text(id + ".label", ready, "Session", dev.jasper.sdk.ui.Nodes.TextRole.LABEL),
            new dev.jasper.sdk.ui.Nodes.Choice(id + ".host", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.DROPDOWN,
                List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty())), "mac", "Session", "host"),
            new dev.jasper.sdk.ui.Nodes.Separator(id + ".rule", ready),
            new dev.jasper.sdk.ui.Nodes.Button(id + ".connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", ""))));
    }

    /** SDK 0.18: a toolbar view is built once per open window and closes with its window and its registration. */
    @Test void aToolbarViewIsBuiltOncePerWindowAndClosesWithItsWindowAndItsRegistration() {
        List<UUID> asked = Collections.synchronizedList(new ArrayList<>());
        List<dev.jasper.sdk.ui.UiView> made = Collections.synchronizedList(new ArrayList<>());
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> registration.set(context.toolbar().add(ToolbarItem.view(window -> {
            asked.add(window.id());
            var view = context.kit().create(sessionBar("bar"), event -> { });
            made.add(view);
            return view;
        }))));
        assertThat(h.toolbar()).containsExactly("view");
        var first = new AtomicReference<UUID>();
        var second = new AtomicReference<UUID>();
        h.ui(() -> { first.set(h.addTerminalWindow()); second.set(h.addTerminalWindow()); });
        assertThat(asked).as("nothing is built before a window asks").isEmpty();
        h.ui(() -> {
            assertThat(h.openToolbarViews(first.get())).containsExactly("bar");
            assertThat(h.openToolbarViews(first.get())).as("once per window").containsExactly("bar");
            assertThat(h.openToolbarViews(second.get())).containsExactly("bar");
        });
        assertThat(asked).containsExactly(first.get(), second.get());
        h.ui(() -> {
            h.closeToolbarViews(first.get());
            assertThatIllegalStateException().as("the view closes with its window").isThrownBy(() -> made.get(0).spec());
            assertThat(made.get(1).spec().root().id()).isEqualTo("bar");
            assertThat(h.openToolbarViews(first.get())).as("a closed window builds nothing again").isEmpty();
            assertThat(h.openToolbarViews(UUID.randomUUID())).as("nor does a window that never opened").isEmpty();
        });
        assertThat(asked).as("no factory runs for a dead window").containsExactly(first.get(), second.get());
        h.ui(() -> registration.get().close());
        assertThat(h.toolbar()).isEmpty();
        h.ui(() -> assertThatIllegalStateException().as("and with its registration").isThrownBy(() -> made.get(1).spec()));
    }

    /** SDK 0.18: the toolbar admits an INLINE stack of labels, choices, one PRIMARY button, menu buttons and separators. */
    @Test void aToolbarViewHoldsOnlyAnInlineStackOfAdmittedControls() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        List<dev.jasper.sdk.ui.UiView> refused = Collections.synchronizedList(new ArrayList<>());
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("plain", ready, "Session",
                    dev.jasper.sdk.ui.Nodes.TextRole.LABEL)), event -> { });
                refused.add(view);
                return view;
            }));
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("quiet", ready,
                    dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(new dev.jasper.sdk.ui.Nodes.Button("quiet.edit", ready, "Edit", "",
                        dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY, "edit", "")))), event -> { });
                refused.add(view);
                return view;
            }));
            context.toolbar().add(ToolbarItem.view(window -> { throw new IllegalStateException("factory failure"); }));
            context.toolbar().add(ToolbarItem.view(window -> {
                var view = context.kit().create(sessionBar("bar"), event -> { });
                admitted.set(view);
                return view;
            }));
        });
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).as("refused trees and a throwing factory show nothing").containsExactly("bar");
            assertThat(refused).hasSize(2);
            for (var view : refused) assertThatIllegalStateException().as("a refused view is closed").isThrownBy(view::spec);
            var twoPrimaries = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
                new dev.jasper.sdk.ui.Nodes.Button("bar.connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", ""),
                new dev.jasper.sdk.ui.Nodes.Button("bar.split", ready, "Split", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "split", ""))));
            assertThatIllegalArgumentException().isThrownBy(() -> admitted.get().update(twoPrimaries)).withMessageContaining("bar.split");
            assertThat(admitted.get().spec()).as("a refused update keeps the tree").isEqualTo(sessionBar("bar"));
        });
        assertThat(h.active("test.alpha")).as("refusals are contained").isTrue();
    }

    /** SDK 0.18: a toolbar view's controls report to the plugin that built it. */
    @Test void aToolbarViewsControlsReportToThePluginThatBuiltIt() {
        List<dev.jasper.sdk.ui.UiEvent> seen = new CopyOnWriteArrayList<>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context ->
            context.toolbar().add(ToolbarItem.view(window -> context.kit().create(sessionBar("bar"), seen::add))));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            h.clickToolbarViewButton(window.get(), "bar.connect");
        });
        assertThat(seen).containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("connect"));
    }

    /** Review fix (plan 3d): every branch of the admission rule, on update, so that the host and testkit copies agree. */
    @Test void aToolbarViewRefusesEveryOtherTreeOnUpdateAndKeepsItsTree() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> context.toolbar().add(ToolbarItem.view(window -> {
            var view = context.kit().create(sessionBar("bar"), event -> { });
            admitted.set(view);
            return view;
        })));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        var label = new dev.jasper.sdk.ui.Nodes.Text("bar.label", ready, "Session", dev.jasper.sdk.ui.Nodes.TextRole.LABEL);
        var options = List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty()));
        java.util.function.Function<dev.jasper.sdk.ui.Nodes.Node, dev.jasper.sdk.ui.ViewSpec> inline = child -> new dev.jasper.sdk.ui.ViewSpec(
            new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(label, child)));
        var refused = new java.util.LinkedHashMap<String, dev.jasper.sdk.ui.ViewSpec>();
        refused.put("column", new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("column", ready,
            dev.jasper.sdk.ui.Nodes.LayoutRole.VERTICAL, List.of(label))));
        refused.put("plain", new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("plain", ready, "Session",
            dev.jasper.sdk.ui.Nodes.TextRole.LABEL)));
        refused.put("bar.body", inline.apply(new dev.jasper.sdk.ui.Nodes.Text("bar.body", ready, "Body", dev.jasper.sdk.ui.Nodes.TextRole.BODY)));
        // An EXCLUSIVE or TABS choice needs two options before the SDK makes the view at all.
        var pair = List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty()), new dev.jasper.sdk.ui.Option("linux", "Linux", Optional.empty()));
        refused.put("bar.mode", inline.apply(new dev.jasper.sdk.ui.Nodes.Choice("bar.mode", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.EXCLUSIVE,
            pair, "mac", "Mode", "mode")));
        refused.put("bar.tabs", inline.apply(new dev.jasper.sdk.ui.Nodes.Choice("bar.tabs", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.TABS,
            pair, "mac", "Tabs", "tabs")));
        refused.put("bar.facts", inline.apply(new dev.jasper.sdk.ui.Nodes.Properties("bar.facts", ready, "", List.of(
            new dev.jasper.sdk.ui.Nodes.PropertyRow("Host", "mac", false, Optional.empty())))));
        refused.put("bar.look", inline.apply(new dev.jasper.sdk.ui.Nodes.Choice("bar.look", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.PREVIEWS,
            options, "mac", "Look", "look")));
        for (var kind : List.of(dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY, dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST,
                dev.jasper.sdk.ui.Nodes.ButtonKind.DESTRUCTIVE_GHOST, dev.jasper.sdk.ui.Nodes.ButtonKind.LINK)) {
            String id = "bar." + kind.name().toLowerCase(java.util.Locale.ROOT);
            refused.put(id, inline.apply(new dev.jasper.sdk.ui.Nodes.Button(id, ready, "Edit", "", kind, "edit", "")));
        }
        refused.put("bar.find", inline.apply(new dev.jasper.sdk.ui.Nodes.Field("bar.find", ready, dev.jasper.sdk.ui.Nodes.FieldKind.TEXT,
            "", "Find", "Find", "")));
        refused.put("bar.inner", inline.apply(new dev.jasper.sdk.ui.Nodes.Stack("bar.inner", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE,
            List.of(new dev.jasper.sdk.ui.Nodes.Text("bar.inner.label", ready, "Inner", dev.jasper.sdk.ui.Nodes.TextRole.LABEL)))));
        refused.put("bar.split", new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Button("bar.connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", ""),
            new dev.jasper.sdk.ui.Nodes.Button("bar.split", ready, "Split", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "split", "")))));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            refused.forEach((node, tree) -> {
                assertThatIllegalArgumentException().as(node).isThrownBy(() -> admitted.get().update(tree)).withMessageContaining(node);
                assertThat(admitted.get().spec()).as("a refused update keeps the tree: " + node).isEqualTo(sessionBar("bar"));
            });
        });
        assertThat(h.active("test.alpha")).isTrue();
    }

    /** SDK 0.18.1: a toolbar view also admits icon-only GHOST buttons; a labelled GHOST is still refused. */
    @Test void aToolbarViewAdmitsIconOnlyGhostButtons() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        List<dev.jasper.sdk.ui.UiEvent> seen = new CopyOnWriteArrayList<>();
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        var run = new dev.jasper.sdk.ui.Nodes.Button("bar.run", ready, "", "run", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "run", "Connect");
        var spec = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Choice("bar.host", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.DROPDOWN,
                List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty())), "mac", "Session", "host"),
            run,
            new dev.jasper.sdk.ui.Nodes.Button("bar.stop", ready, "", "close", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "stop", "Stop"))));
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> context.toolbar().add(ToolbarItem.view(window -> {
            var view = context.kit().create(spec, seen::add);
            admitted.set(view);
            return view;
        })));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            h.clickToolbarViewButton(window.get(), "bar.run");
            var labelled = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
                new dev.jasper.sdk.ui.Nodes.Button("bar.edit", ready, "Edit", "", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "edit", ""))));
            assertThatIllegalArgumentException().isThrownBy(() -> admitted.get().update(labelled)).withMessageContaining("bar.edit");
            assertThat(admitted.get().spec()).isEqualTo(spec);
        });
        assertThat(seen).containsExactly(new dev.jasper.sdk.ui.UiEvent.Action("run"));
    }

    /** Review fix (plan 3d): SEARCHABLE and PICKER choices and a menu button are admitted beside a label and a DROPDOWN. */
    @Test void aToolbarViewAdmitsSearchableAndPickerChoicesAndMenuButtons() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        var options = List.of(new dev.jasper.sdk.ui.Option("mac", "Mac", Optional.empty()));
        var rich = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Stack("rich", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, List.of(
            new dev.jasper.sdk.ui.Nodes.Text("rich.label", ready, "Session", dev.jasper.sdk.ui.Nodes.TextRole.LABEL),
            new dev.jasper.sdk.ui.Nodes.Choice("rich.host", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.SEARCHABLE, options, "mac", "Host", "host", "",
                Optional.of(new dev.jasper.sdk.ui.Nodes.ChoiceSearch(0, "No hosts", Optional.empty()))),
            new dev.jasper.sdk.ui.Nodes.Choice("rich.login", ready, dev.jasper.sdk.ui.Nodes.ChoiceKind.PICKER, options, "mac", "Login", "login", ""),
            new dev.jasper.sdk.ui.Nodes.MenuButton("rich.more", ready, "More", "", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "more",
                List.of(new dev.jasper.sdk.ui.MenuItem("rich.more.edit", "Edit", "", "edit", true, false)), ""))));
        h.start(info("test.alpha"), Set.of(), Set.of(), context ->
            context.toolbar().add(ToolbarItem.view(window -> context.kit().create(rich, event -> { }))));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> assertThat(h.openToolbarViews(window.get())).containsExactly("rich"));
    }

    /**
     * Plans 3b and 3d merged (3d, R1): separators between admitted controls are admitted on update too, and a separator
     * the SDK would place first, last or beside another never reaches a toolbar, since the SDK refuses the view itself.
     */
    @Test void aToolbarViewAdmitsSeparatorsBetweenItsControlsOnUpdate() {
        var ready = dev.jasper.sdk.ui.ControlState.READY;
        var admitted = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> context.toolbar().add(ToolbarItem.view(window -> {
            var view = context.kit().create(sessionBar("bar"), event -> { });
            admitted.set(view);
            return view;
        })));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        var label = new dev.jasper.sdk.ui.Nodes.Text("bar.label", ready, "Session", dev.jasper.sdk.ui.Nodes.TextRole.LABEL);
        var connect = new dev.jasper.sdk.ui.Nodes.Button("bar.connect", ready, "Connect", "", dev.jasper.sdk.ui.Nodes.ButtonKind.PRIMARY, "connect", "");
        var more = new dev.jasper.sdk.ui.Nodes.MenuButton("bar.more", ready, "More", "", dev.jasper.sdk.ui.Nodes.ButtonKind.GHOST, "more",
            List.of(new dev.jasper.sdk.ui.MenuItem("bar.more.edit", "Edit", "", "edit", true, false)), "");
        java.util.function.Function<List<dev.jasper.sdk.ui.Nodes.Node>, dev.jasper.sdk.ui.ViewSpec> inline = children -> new dev.jasper.sdk.ui.ViewSpec(
            new dev.jasper.sdk.ui.Nodes.Stack("bar", ready, dev.jasper.sdk.ui.Nodes.LayoutRole.INLINE, children));
        var ruled = inline.apply(List.of(label, new dev.jasper.sdk.ui.Nodes.Separator("bar.first", ready), connect,
            new dev.jasper.sdk.ui.Nodes.Separator("bar.second", ready), more));
        h.ui(() -> {
            assertThat(h.openToolbarViews(window.get())).containsExactly("bar");
            admitted.get().update(ruled);
            assertThat(admitted.get().spec()).isEqualTo(ruled);
        });
        var rule = new dev.jasper.sdk.ui.Nodes.Separator("bar.rule", ready);
        var twice = new dev.jasper.sdk.ui.Nodes.Separator("bar.again", ready);
        List<List<dev.jasper.sdk.ui.Nodes.Node>> misplaced = List.of(List.of(rule, label, connect), List.of(label, connect, rule),
            List.of(label, rule, twice, connect));
        for (var children : misplaced)
            assertThatIllegalArgumentException().as(children.toString()).isThrownBy(() -> inline.apply(children)).withMessageContaining("Separator");
        assertThat(h.active("test.alpha")).isTrue();
    }

    /** Review fix (plan 3d): stopping the plugin closes the views its windows built and removes the entry. */
    @Test void stoppingThePluginClosesItsBuiltToolbarViews() {
        var made = new AtomicReference<dev.jasper.sdk.ui.UiView>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> context.toolbar().add(ToolbarItem.view(window -> {
            var view = context.kit().create(sessionBar("bar"), event -> { });
            made.set(view);
            return view;
        })));
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        h.ui(() -> assertThat(h.openToolbarViews(window.get())).containsExactly("bar"));
        h.stopAll(); h.flush();
        h.ui(() -> assertThatIllegalStateException().as("the built view closed").isThrownBy(() -> made.get().spec()));
        assertThat(h.toolbar()).isEmpty();
    }

    /** Review fix (plan 3d): a plugin whose start fails after adding a toolbar view leaves no entry and builds nothing. */
    @Test void aPluginThatFailsAfterAddingAToolbarViewLeavesNone() {
        var window = new AtomicReference<UUID>();
        h.ui(() -> window.set(h.addTerminalWindow()));
        List<UUID> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.doomed"), Set.of(), Set.of(), context -> {
            context.toolbar().add(ToolbarItem.view(opened -> {
                asked.add(opened.id());
                return context.kit().create(sessionBar("bar"), event -> { });
            }));
            throw new IllegalStateException("start failure");
        });
        assertThat(h.toolbar()).isEmpty();
        h.ui(() -> assertThat(h.openToolbarViews(window.get())).isEmpty());
        assertThat(asked).as("the failed plugin's factory never runs afterwards").isEmpty();
    }

    @Test void railButtonsNameThePluginsOwnActions() {
        var open = new AtomicReference<PluginAction>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            open.set(context.actions().register(ActionSpec.of("test.alpha.open", "Open"), invoked -> { }));
            context.rail().add("test.alpha.open");
            assertThatIllegalArgumentException().isThrownBy(() -> context.rail().add("new_tab"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.rail().add("test.alpha.absent"));
        });
        assertThat(h.rail()).containsExactly("test.alpha.open");
        h.ui(() -> open.get().close());
        assertThat(h.rail()).isEmpty();
    }

    /** SDK 0.18: a dialog's swatch reaches the host with the dialog; NONE asks for no square. */
    @Test void aDialogCarriesItsSwatchToTheHost() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            var manager = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, kitView(context));
            var other = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("other", "Other"), null, kitView(context));
            context.windows().dialog(new DialogSpec("Add Login", manager, true, DialogSpec.Presentation.CENTERED, dev.jasper.sdk.ui.Nodes.Hue.BLUE));
            context.windows().dialog(new DialogSpec("Details", other, true));
        });
        h.ui(() -> {
            assertThat(h.dialogSwatch("test.alpha", "Add Login")).isEqualTo("BLUE");
            assertThat(h.dialogSwatch("test.alpha", "Details")).isEqualTo("NONE");
            assertThat(h.dialogSwatch("test.alpha", "Absent")).isEqualTo("-");
        });
    }

    /** Quit asks the content tabs' guards and closes nothing; a dialog's guard is not asked. */
    @Test void quitAsksThePluginsContentTabGuardAndClosesNothing() {
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("first", "First"), null, kitView(context));
            var second = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("second", "Second"), null, kitView(context));
            context.windows().onTabsClosing((tabs, batch) -> { asked.add(String.valueOf(tabs.size())); return CompletableFuture.completedFuture(allow.get()); });
            context.windows().dialog(new DialogSpec("Details", second, false)).onClosing(() -> { asked.add("dialog"); return false; });
        });
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("2");
        allow.set(true);
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("dialogs are not asked").isTrue());
        assertThat(asked).containsExactly("2");
        assertThat(h.windows()).as("asking closes nothing").hasSize(1);
    }

    @Test void quitGuardsAreAskedInTurnAndOnlyAFalseDecisionRefuses() {
        var alpha = new AtomicReference<CompletableFuture<Boolean>>(CompletableFuture.completedFuture(false));
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context ->
            context.windows().onQuitting(() -> { asked.add("alpha"); return alpha.get(); }));
        h.start(info("test.beta"), Set.of(), Set.of(), context ->
            context.windows().onQuitting(() -> { asked.add("beta"); throw new IllegalStateException("broken guard"); }));
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("alpha");
        alpha.set(CompletableFuture.completedFuture(true));
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("a guard that throws allows the quit").isTrue());
        assertThat(asked).containsExactly("alpha", "beta");
        alpha.set(CompletableFuture.failedFuture(new IllegalStateException("failed decision")));
        h.ui(() -> assertThat(h.requestQuit()).as("a failed decision allows the quit").isTrue());
    }

    @Test void aClosedQuitGuardIsNotAsked() {
        var registration = new AtomicReference<Subscription>();
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> registration.set(
            context.windows().onQuitting(() -> { asked.add("alpha"); return CompletableFuture.completedFuture(false); })));
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        h.ui(() -> registration.get().close());
        h.ui(() -> assertThat(h.requestQuit()).isTrue());
        assertThat(asked).containsExactly("alpha");
    }

    private static PluginInfo info(String id, String... capabilities) { return new PluginInfo(id, id, "1.0.0", Set.of(capabilities)); }

    @Test void terminalsAreFoundThroughTheContextAndHandlesAreEqualById() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), left = h.addTerminalPane(tab, "make", Path.of("/src"));
        UUID other = h.addTerminalTab(window, "top"), right = h.addTerminalPane(other, "top", Path.of("/"));
        h.focusTerminalPane(left);
        h.activateTerminalWindow(window);
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.windows()).extracting(handle -> handle.id()).containsExactly(window);
            assertThat(terminals.activeWindow()).contains(terminals.windows().get(0));
            assertThat(terminals.windows().get(0).tabs()).extracting(TabHandle::id).containsExactly(tab, other);
            var tabHandle = terminals.windows().get(0).activeTab().orElseThrow();
            assertThat(tabHandle.id()).isEqualTo(tab);
            assertThat(tabHandle.kind()).isEqualTo(TabHandle.Kind.TERMINAL);
            assertThat(tabHandle.title()).isEqualTo("build");
            PaneHandle pane = terminals.activePane().orElseThrow();
            assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.terminal().orElseThrow());
            assertThat(pane.tab()).isEqualTo(tabHandle);
            assertThat(pane.tab().window().isActive()).isTrue();
            assertThat(pane.info().title()).isEqualTo("make");
            assertThat(pane.info().workingDirectory()).contains(Path.of("/src"));
            assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
            terminals.pane(right).orElseThrow().focus();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
            assertThat(terminals.windows().get(0).activeTab().map(TabHandle::id)).as("focusing a pane selects its tab").contains(other);
        });
        h.closeTerminalPane(right);
        h.ui(() -> {
            assertThat(alpha.get().terminals().activePane().map(PaneHandle::id)).contains(left);
            assertThat(alpha.get().terminals().tab(other)).as("a pane closes with its tab").isEmpty();
        });
    }

    @Test void gatedTerminalCallsNameTheMissingCapability() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), context -> {
            bare.set(context);
            assertThatThrownBy(() -> context.events().subscribe(TerminalEvents.COMMAND_FINISHED, event -> { }))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                    assertThat(failure.pluginId()).isEqualTo("test.bare");
                    assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OBSERVE);
                });
        });
        assertThat(h.active("test.bare")).isTrue();
        h.ui(() -> {
            PaneHandle pane = bare.get().terminals().pane(only).orElseThrow();
            assertThat(pane.isOpen()).as("structure needs no capability").isTrue();
            assertThatThrownBy(pane::info).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> pane.tab().title()).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(pane::selection).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> pane.sendText("x")).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> bare.get().terminals().split(pane, Direction.RIGHT, OpenRequest.local())).isInstanceOf(MissingCapabilityException.class);
        });
        assertThat(h.sentToPane(only)).isEmpty();
        assertThat(h.openRequests()).isEmpty();
    }

    /** Workspace tabs spec 13: a restored terminal tab not yet shown has not started, drops text, and starts when shown. */
    @Test void aRestoredPaneReportsNotStartedAndDropsText() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT), Set.of(), Set.of(), alpha::set);
        h.holdTerminalPane(only);
        h.ui(() -> {
            PaneHandle pane = alpha.get().terminals().pane(only).orElseThrow();
            assertThat(pane.info().state()).isEqualTo(SessionState.NOT_STARTED);
            assertThat(pane.info().columns()).as("no grid until it starts").isZero();
            assertThat(pane.info().rows()).isZero();
            assertThat(pane.info().shellIntegration()).isFalse();
            pane.sendText("dropped");
        });
        h.flush();
        assertThat(h.sentToPane(only)).isEmpty();
        h.focusTerminalPane(only);
        h.ui(() -> {
            PaneHandle pane = alpha.get().terminals().pane(only).orElseThrow();
            assertThat(pane.info().state()).as("shown, it starts").isEqualTo(SessionState.RUNNING);
            pane.sendText("kept");
        });
        h.flush();
        assertThat(h.sentToPane(only)).containsExactly("write:kept");
    }

    /** Workspace tabs spec 13: a held pane starts when its tab is selected, once, after the selection is reported. */
    @Test void aRestoredPaneStartsWhenItsTabIsSelected() {
        UUID window = h.addTerminalWindow(), build = h.addTerminalTab(window, "build"), only = h.addTerminalPane(build, "make", Path.of("/src"));
        UUID logs = h.addTerminalTab(window, "logs");
        h.addTerminalPane(logs, "zsh", Path.of("/"));
        List<Object> ordered = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_SELECTED, TerminalEvents.SESSION_STATE_CHANGED))
                context.events().subscribe(topic, event -> ordered.add(Map.entry(topic, event)));
        });
        h.holdTerminalPane(only);
        h.flush();
        ordered.clear();
        h.ui(() -> alpha.get().terminals().pane(only).orElseThrow().tab().select());
        h.flush();
        assertThat(ordered).as("Jasper selects the tab first and starts its pane after").containsExactly(
            Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window, build)),
            Map.entry(TerminalEvents.SESSION_STATE_CHANGED,
                new TerminalEvents.SessionStateChanged(only, SessionState.RUNNING, java.util.OptionalInt.empty())));
        h.ui(() -> {
            var started = alpha.get().terminals().pane(only).orElseThrow().info();
            assertThat(started.state()).isEqualTo(SessionState.RUNNING);
            assertThat(started.columns()).as("started, it has a grid").isPositive();
        });
        ordered.clear();
        h.ui(() -> alpha.get().terminals().pane(only).orElseThrow().tab().select());
        h.flush();
        assertThat(ordered).as("selected again, it starts nothing more").isEmpty();
    }

    @Test void injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt() throws Exception {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID only = h.addTerminalPane(tab, "make", Path.of("/src")), other = h.addTerminalPane(h.addTerminalTab(window, "top"), "top", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION), Set.of(), Set.of(), alpha::set);
        var pane = new AtomicReference<PaneHandle>();
        h.ui(() -> {
            pane.set(alpha.get().terminals().pane(only).orElseThrow());
            pane.get().sendText("ls\n");
            pane.get().paste("pasted");
        });
        Thread worker = new Thread(() -> { pane.get().sendText("one"); pane.get().sendBytes("two".getBytes(java.nio.charset.StandardCharsets.UTF_8)); });
        worker.start();
        worker.join();
        h.flush();
        assertThat(h.sentToPane(only)).containsExactly("write:ls\n", "paste:pasted", "write:one", "write:two");
        h.selectInPane(only, "selected");
        h.ui(() -> assertThat(pane.get().selection()).contains("selected"));
        h.closeTerminalPane(only);
        h.ui(() -> {
            assertThat(pane.get().isOpen()).isFalse();
            assertThatCode(() -> pane.get().sendText("late")).doesNotThrowAnyException();
            assertThat(pane.get().selection()).isEmpty();
        });
        h.flush();
        assertThat(h.sentToPane(other)).isEmpty();
    }

    @Test void terminalEventsNameIdsAndFollowTheActivePane() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src"));
        UUID other = h.addTerminalTab(window, "top"), right = h.addTerminalPane(other, "top", Path.of("/"));
        h.focusTerminalPane(left);
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
            context.events().subscribe(TerminalEvents.COMMAND_FINISHED, heard::add);
            context.events().subscribe(TerminalEvents.PANE_CLOSED, heard::add);
        });
        h.flush();
        heard.clear();
        h.activateTerminalWindow(window);
        h.focusTerminalPane(right);
        h.finishCommand(left, "make test", 2);
        h.closeTerminalPane(right);
        h.flush();
        assertThat(heard).hasSize(5);
        assertThat(heard.get(0)).isEqualTo(new TerminalEvents.ActivePaneChanged(Optional.of(left)));
        assertThat(heard.get(1)).isEqualTo(new TerminalEvents.ActivePaneChanged(Optional.of(right)));
        assertThat(heard.get(2)).isInstanceOfSatisfying(TerminalEvents.CommandFinished.class, finished -> {
            assertThat(finished.paneId()).isEqualTo(left);
            assertThat(finished.command()).isEqualTo("make test");
            assertThat(finished.exitStatus()).hasValue(2);
            assertThat(finished.workingDirectory()).contains(Path.of("/src"));
        });
        assertThat(heard.subList(3, 5)).containsExactlyInAnyOrder(new TerminalEvents.PaneEvent(other, right),
            new TerminalEvents.ActivePaneChanged(Optional.of(left)));
    }

    @Test void aContentTabIsATabOfItsWindowWithNoTerminal() {
        UUID window = h.addTerminalWindow(), shell = h.addTerminalTab(window, "zsh"), pane = h.addTerminalPane(shell, "zsh", Path.of("/src"));
        h.activateTerminalWindow(window);
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(TerminalEvents.TAB_OPENED, heard::add);
            context.events().subscribe(TerminalEvents.TAB_SELECTED, heard::add);
            context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
        });
        h.flush();
        heard.clear();
        UUID notes = h.addContentTab(window, "notes.txt");
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabEvent(window, notes), new TerminalEvents.TabEvent(window, notes),
            new TerminalEvents.ActivePaneChanged(Optional.empty()));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            var handle = terminals.window(window).orElseThrow();
            assertThat(handle.tabs()).extracting(TabHandle::id).containsExactly(shell, notes);
            assertThat(handle.tabs()).extracting(TabHandle::kind).containsExactly(TabHandle.Kind.TERMINAL, TabHandle.Kind.CONTENT);
            TabHandle content = handle.activeTab().orElseThrow();
            assertThat(content.id()).isEqualTo(notes);
            assertThat(content.terminal()).isEmpty();
            assertThat(content.title()).isEqualTo("notes.txt");
            assertThat(content.window()).isEqualTo(handle);
            assertThat(terminals.tab(notes)).contains(content);
            assertThat(terminals.activePane()).as("a content tab has no terminal").isEmpty();
            terminals.tab(shell).orElseThrow().select();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(pane);
        });
    }

    @Test void aTabMovedToAnotherWindowKeepsItsPaneAndReportsOnlyTheMove() {
        UUID first = h.addTerminalWindow(), stays = h.addTerminalTab(first, "stays");
        h.addTerminalPane(stays, "zsh", Path.of("/"));
        UUID moving = h.addTerminalTab(first, "build"), pane = h.addTerminalPane(moving, "make", Path.of("/src"));
        UUID second = h.addTerminalWindow(), other = h.addTerminalTab(second, "other");
        h.addTerminalPane(other, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        List<Object> ordered = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_CLOSED, TerminalEvents.TAB_MOVED,
                    TerminalEvents.PANE_OPENED, TerminalEvents.PANE_CLOSED, TerminalEvents.WINDOW_CLOSED))
                context.events().subscribe(topic, heard::add);
            for (var topic : List.of(TerminalEvents.ACTIVE_PANE_CHANGED, TerminalEvents.TAB_MOVED, TerminalEvents.TAB_SELECTED,
                    TerminalEvents.WINDOW_CLOSED))
                context.events().subscribe(topic, event -> ordered.add(java.util.Map.entry(topic, event)));
        });
        h.flush();
        heard.clear();
        h.moveTab(moving, second);
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabMoved(moving, first, second));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle handle = terminals.pane(pane).orElseThrow();
            assertThat(handle.tab().id()).isEqualTo(moving);
            assertThat(handle.tab().window().id()).isEqualTo(second);
            assertThat(terminals.window(second).orElseThrow().tabs()).extracting(TabHandle::id).containsExactly(other, moving);
            assertThat(terminals.window(second).orElseThrow().activeTab().map(TabHandle::id)).as("it arrives selected").contains(moving);
            assertThat(terminals.window(first).orElseThrow().tabs()).extracting(TabHandle::id).containsExactly(stays);
        });
        // The active window a move empties reports no active pane as it lets the tab go, then the move, the arrival's
        // selection and, last, its close, as the application's window does (workspace plan 4a's real-window probe).
        h.activateTerminalWindow(first);
        h.flush();
        heard.clear();
        ordered.clear();
        h.moveTab(stays, second);
        h.flush();
        assertThat(heard).as("the window the move empties closes after the move").containsExactly(
            new TerminalEvents.TabMoved(stays, first, second), new TerminalEvents.WindowEvent(first));
        assertThat(ordered).containsExactly(
            java.util.Map.entry(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.empty())),
            java.util.Map.entry(TerminalEvents.TAB_MOVED, new TerminalEvents.TabMoved(stays, first, second)),
            java.util.Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(second, stays)),
            java.util.Map.entry(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(first)));
        h.ui(() -> assertThat(alpha.get().terminals().window(first)).isEmpty());
    }

    @Test void aPluginsContentTabOpensMovesAndClosesOnTheTabTopics() {
        UUID window = h.addTerminalWindow(), shell = h.addTerminalTab(window, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.TAB_MOVED, TerminalEvents.TAB_CLOSED,
                    TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_CLOSED))
                context.events().subscribe(topic, heard::add);
        });
        h.flush();
        heard.clear();
        var tab = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.ui(() -> tab.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"),
            alpha.get().terminals().window(window).orElseThrow(), kitView(alpha.get()))));
        h.flush();
        assertThat(heard).singleElement().isInstanceOfSatisfying(TerminalEvents.TabEvent.class,
            opened -> assertThat(opened.windowId()).isEqualTo(window));
        UUID id = ((TerminalEvents.TabEvent) heard.getFirst()).tabId();
        h.ui(() -> {
            var opened = alpha.get().terminals().tab(id).orElseThrow();
            assertThat(opened.kind()).isEqualTo(TabHandle.Kind.CONTENT);
            assertThat(opened.terminal()).isEmpty();
            assertThat(opened.title()).isEqualTo("Notes");
            assertThat(alpha.get().terminals().window(window).orElseThrow().activeTab()).contains(opened);
        });
        heard.clear();
        h.ui(() -> tab.get().moveToNewWindow());
        h.flush();
        assertThat(heard).hasSize(2).first().isInstanceOf(TerminalEvents.WindowEvent.class);
        UUID separate = ((TerminalEvents.WindowEvent) heard.getFirst()).windowId();
        assertThat(heard.get(1)).as("the window opened for the move reports first").isEqualTo(new TerminalEvents.TabMoved(id, window, separate));
        heard.clear();
        h.ui(() -> assertThat(tab.get().requestClose().join()).isTrue());
        h.flush();
        assertThat(heard).containsExactly(new TerminalEvents.TabEvent(separate, id));
        h.ui(() -> {
            assertThat(alpha.get().terminals().tab(id)).isEmpty();
            assertThat(alpha.get().terminals().window(separate)).as("a window keeps its empty workspace").isPresent();
            assertThat(alpha.get().terminals().window(window)).isPresent();
        });
    }

    /**
     * Full windows spec 5.3: every window keeps an empty workspace when its last tab closes, and closes when a move takes
     * its last tab to another window.
     */
    @Test void aWindowOutlivesItsLastTabAndClosesWhenAMoveEmptiesIt() {
        UUID a = h.addTerminalWindow(), first = h.addTerminalTab(a, "zsh");
        UUID firstPane = h.addTerminalPane(first, "zsh", Path.of("/"));
        UUID b = h.addTerminalWindow(), second = h.addTerminalTab(b, "make");
        UUID secondPane = h.addTerminalPane(second, "make", Path.of("/src"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_CLOSED)) context.events().subscribe(topic, heard::add);
        });
        h.flush();
        heard.clear();
        h.closeTerminalPane(firstPane);
        h.closeTerminalPane(secondPane);
        UUID notes = h.addContentTab(b, "notes.txt");
        h.closeTab(notes);
        h.flush();
        assertThat(heard).as("no window closes with its last tab").isEmpty();
        h.ui(() -> {
            for (UUID id : List.of(a, b)) {
                var window = alpha.get().terminals().window(id).orElseThrow();
                assertThat(window.tabs()).isEmpty();
                assertThat(window.activeTab()).as("an empty workspace").isEmpty();
            }
        });
        UUID moving = h.addTerminalTab(a, "build");
        h.flush();
        heard.clear();
        h.moveTab(moving, b);
        h.flush();
        assertThat(heard).as("the window the move empties closes").containsExactly(new TerminalEvents.WindowEvent(a));
        h.ui(() -> assertThat(alpha.get().terminals().window(a)).isEmpty());
    }

    /** Full windows spec 5.4 and 9: a plugin's tab moves to a new window and to another window, and says which window holds it. */
    @Test void aContentTabMovesToANewWindowAndToAnotherWindow() {
        UUID home = h.addTerminalWindow(), shell = h.addTerminalTab(home, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var tab = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), null, kitView(alpha.get()));
            var first = tab.window();
            assertThat(first.id()).as("no owner: the most recently active window").isEqualTo(home);
            tab.moveToWindow(first);
            assertThat(tab.window()).as("already there").isEqualTo(first);
            tab.moveToNewWindow();
            var separate = tab.window();
            assertThat(separate).isNotEqualTo(first);
            assertThat(separate.tabs()).singleElement().satisfies(only -> assertThat(only.kind()).isEqualTo(TabHandle.Kind.CONTENT));
            assertThat(first.tabs()).extracting(TabHandle::id).containsExactly(shell);
            tab.moveToNewWindow();
            assertThat(tab.window()).as("a window's only tab stays").isEqualTo(separate);
            tab.moveToWindow(first);
            assertThat(tab.window()).isEqualTo(first);
            assertThat(first.tabs()).as("at the end of its strip").extracting(TabHandle::id).last().isNotEqualTo(shell);
            assertThat(first.activeTab().map(TabHandle::id)).as("shown").contains(first.tabs().getLast().id());
            assertThat(separate.isOpen()).as("the window the move emptied closed").isFalse();
            tab.moveToWindow(separate);
            assertThat(tab.window()).as("a closed window takes nothing").isEqualTo(first);
            tab.close();
            assertThat(tab.window()).as("a closed tab names the last window that held it").isEqualTo(first);
        });
    }

    /** Workspace tabs spec 13 and 14: a content tab saved with restore state comes back through its plugin's restorer. */
    @Test void aRestorerOpensItsSavedContentTabAgainInTheWindowBeingRestored() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        var requests = new ArrayList<dev.jasper.sdk.ui.RestoreRequest>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("note", request -> {
                requests.add(request);
                // Whatever window or placement it names, the saved tab comes back in the window being restored.
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:" + request.state().values().get("name"), request.title())
                    .withRestore(request.state()), null, kitView(context), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW);
                return Optional.empty();
            });
        });
        var state = new dev.jasper.sdk.RestoreState("note", java.util.Map.of("name", "todo"));
        h.ui(() -> {
            var window = alpha.get().terminals().window(main).orElseThrow();
            var first = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:first", "First").withRestore(state), window,
                kitView(alpha.get()));
            UUID firstTab = window.activeTab().orElseThrow().id();
            assertThat(h.restoreState(firstTab)).contains(state);
            first.setRestore(Optional.empty());
            assertThat(h.restoreState(firstTab)).as("a tab that gives no restore state is not saved").isEmpty();
            Optional<UUID> back = h.restoreTab("test.alpha", main, "Todo", state);
            assertThat(back).isPresent();
            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(main);
                assertThat(request.title()).isEqualTo("Todo");
                assertThat(request.state()).isEqualTo(state);
            });
            var reopened = alpha.get().terminals().tab(back.get()).orElseThrow();
            assertThat(reopened.kind()).isEqualTo(TabHandle.Kind.CONTENT);
            assertThat(reopened.window().id()).as("the window being restored, not a new one").isEqualTo(main);
            assertThat(alpha.get().terminals().windows()).hasSize(1);
            assertThat(reopened.title()).isEqualTo("Todo");
            assertThat(h.restoreState(back.get())).contains(state);
        });
    }

    /**
     * Workspace tabs spec 13: a restorer that declines, throws or opens nothing restores no tab; a plugin has one restorer
     * per kind.
     */
    @Test void aRestorerThatFailsRestoresNothingAndAKindHasOneRestorer() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var windows = alpha.get().windows();
            windows.restorer("broken", request -> { throw new IllegalStateException("the file is gone"); });
            windows.restorer("declined", request -> Optional.of("its file is gone"));
            Subscription empty = windows.restorer("empty", request -> Optional.empty());
            assertThatIllegalArgumentException().as("one restorer per kind").isThrownBy(() -> windows.restorer("empty", request -> Optional.empty()));
            assertThatIllegalArgumentException().as("not a kind").isThrownBy(() -> windows.restorer("no:colons", request -> Optional.empty()));
            assertThat(h.restoreTab("test.alpha", main, "Gone", new dev.jasper.sdk.RestoreState("broken", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Declined", new dev.jasper.sdk.RestoreState("declined", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Nothing", new dev.jasper.sdk.RestoreState("empty", java.util.Map.of()))).isEmpty();
            assertThat(h.restoreTab("test.alpha", main, "Unknown", new dev.jasper.sdk.RestoreState("unknown", java.util.Map.of()))).isEmpty();
            empty.close();
            windows.restorer("empty", request -> {
                windows.openTab(new dev.jasper.sdk.ui.ContentSpec("again", request.title()), request.window(), kitView(alpha.get()));
                return Optional.empty();
            });
            assertThat(h.restoreTab("test.alpha", main, "Again", new dev.jasper.sdk.RestoreState("empty", java.util.Map.of())))
                .as("a closed registration frees its kind").isPresent();
            assertThat(alpha.get().terminals().window(main).orElseThrow().tabs()).hasSize(1);
        });
    }

    /**
     * Workspace plan 6b's final review M1: an Error a restorer throws, other than a VirtualMachineError, is that plugin's
     * restore failure, as an exception is: the tab it opened closes, it restores nothing, and the next saved tab restores.
     */
    @Test void aRestorersErrorClosesTheTabItOpenedAndTheNextTabStillRestores() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        var broken = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("broken", request -> {
                broken.set(context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("broken", request.title()), request.window(),
                    kitView(context)));
                throw new AssertionError("the restorer broke");
            });
            context.windows().restorer("note", request -> {
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note", request.title()), request.window(), kitView(context));
                return Optional.empty();
            });
        });
        h.ui(() -> {
            assertThat(h.restoreTab("test.alpha", main, "Broken", new dev.jasper.sdk.RestoreState("broken", java.util.Map.of())))
                .as("restores nothing").isEmpty();
            assertThat(broken.get().isClosed()).as("the tab it opened closed").isTrue();
            assertThat(h.restoreTab("test.alpha", main, "Note", new dev.jasper.sdk.RestoreState("note", java.util.Map.of())))
                .as("the next saved tab restores").isPresent();
            assertThat(alpha.get().terminals().window(main).orElseThrow().tabs()).hasSize(1);
        });
    }

    /** Workspace tabs spec 13 and 14: a provided session's tab gives the restore state its spec carries. */
    @Test void aSessionsTabGivesTheRestoreStateOfItsSpec() {
        UUID main = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        var state = new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of("host", "build-host"));
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle saved = terminals.openTab(terminals.window(main).orElseThrow(),
                OpenRequest.session(dev.jasper.sdk.terminal.SessionSpec.of("build-host", pending -> { }).withRestore(state))).orElseThrow();
            PaneHandle plain = terminals.openTab(terminals.window(main).orElseThrow(),
                OpenRequest.session(dev.jasper.sdk.terminal.SessionSpec.of("other", pending -> { }))).orElseThrow();
            assertThat(h.restoreState(saved.tab().id())).contains(state);
            assertThat(h.restoreState(plain.tab().id())).isEmpty();
        });
    }

    /**
     * Workspace plan 6b Rulings 24: while a restorer runs, the window being restored takes only the saved tab; another
     * content tab the restorer opens there lands where an owner-less tab would, in the most recently active window.
     */
    @Test void aRestorersOtherContentTabLandsWhereAnOwnerlessTabWouldNotInTheWindowBeingRestored() {
        UUID main = h.addTerminalWindow(), child = h.addTerminalWindow();
        h.activateTerminalWindow(main);
        var alpha = new AtomicReference<PluginContext>();
        var other = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("note", request -> {
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:saved", request.title()), request.window(), kitView(context));
                other.set(context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:other", "Other"), request.window(), kitView(context)));
                return Optional.empty();
            });
        });
        h.ui(() -> {
            Optional<UUID> back = h.restoreTab("test.alpha", child, "Saved", new dev.jasper.sdk.RestoreState("note", java.util.Map.of()));
            assertThat(back).isPresent();
            assertThat(alpha.get().terminals().tab(back.get()).orElseThrow().window().id()).as("the saved tab").isEqualTo(child);
            assertThat(other.get().window().id()).as("where an owner-less tab goes").isEqualTo(main);
        });
    }

    /**
     * Workspace plan 6b Rulings 30 (6b Task 1's review): while its restorer runs, the saved tab stays in the window being
     * restored, whatever move the restorer asks of it, and comes back there.
     */
    @Test void aRestorersTabStaysInTheWindowBeingRestoredWhateverMoveItAsksFor() {
        UUID main = h.addTerminalWindow(), child = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        var movable = new ArrayList<Boolean>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().restorer("note", request -> {
                var tab = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("note:" + request.title(), request.title()),
                    request.window(), kitView(context));
                movable.add(tab.mayMoveToNewTab());
                tab.moveToNewWindow();
                for (var window : context.terminals().windows()) tab.moveToWindow(window);
                tab.moveToNewTab();
                return Optional.empty();
            });
        });
        h.ui(() -> {
            for (UUID window : List.of(main, child)) {
                Optional<UUID> back = h.restoreTab("test.alpha", window, window.equals(main) ? "Main" : "Child",
                    new dev.jasper.sdk.RestoreState("note", java.util.Map.of()));
                assertThat(back).as("restored").isPresent();
                assertThat(alpha.get().terminals().tab(back.get()).orElseThrow().window().id()).as("where it was restored").isEqualTo(window);
            }
            assertThat(alpha.get().terminals().windows()).as("no window opened for a move").hasSize(2);
            assertThat(movable).containsExactly(false, false);
        });
    }

    /**
     * Full windows spec 5.2: a content tab placed in a new window while no window is open opens a window first, as a
     * restore does, then a window of its own for the tab.
     */
    @Test void aTabPlacedInANewWindowWhileNoWindowIsOpenOpensAWindowAndAnotherForIt() {
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var tab = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("away", "Away"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW);
            var windows = alpha.get().terminals().windows();
            assertThat(windows).as("a window, then the tab's own").hasSize(2);
            assertThat(tab.window().tabs()).as("a window of its own").hasSize(1);
            assertThat(windows).filteredOn(window -> !window.equals(tab.window())).singleElement()
                .satisfies(first -> assertThat(first.tabs()).isEmpty());
        });
    }

    /**
     * Tabs and panes spec 11's TabPlacement: NEW_TAB opens a new tab at the end of the window's strip, SPLIT_RIGHT and
     * SPLIT_DOWN a new pane right after the focused pane, in its tab, and NEW_WINDOW a new window.
     */
    @Test void aPlacementOpensANewTabANewPaneBesideTheFocusedOneOrANewWindow() {
        UUID main = h.addTerminalWindow(), left = h.addTerminalTab(main, "left");
        UUID pane = h.addTerminalPane(left, "zsh", Path.of("/"));
        UUID right = h.addTerminalTab(main, "right");
        h.addTerminalPane(right, "zsh", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.TAB_OPENED, TerminalEvents.TAB_SELECTED))
                context.events().subscribe(topic, event -> heard.add(java.util.Map.entry(topic, event)));
        });
        var below = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var placed = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            var windows = alpha.get().windows();
            var owner = terminals.window(main).orElseThrow();
            UUID split = terminals.split(terminals.pane(pane).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow().tab().id();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("here", "Here"), owner, kitView(alpha.get()));
            UUID here = owner.activeTab().orElseThrow().id();
            below.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Below"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN));
            UUID under = owner.activeTab().orElseThrow().id();
            terminals.tab(left).orElseThrow().select();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("beside", "Beside"), owner, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
            UUID beside = owner.activeTab().orElseThrow().id();
            windows.openTab(new dev.jasper.sdk.ui.ContentSpec("next", "Next"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_TAB);
            UUID next = owner.activeTab().orElseThrow().id();
            placed.set(List.of(left, beside, split, right, here, under, next));
            assertThat(owner.tabs()).extracting(TabHandle::id).as("tab by tab, then pane by pane").containsExactlyElementsOf(placed.get());
        });
        List<UUID> ids = placed.get();
        assertThat(h.windowTabs(main)).as("a new tab at the end of the strip, a new pane right after the focused one").containsExactly(
            List.of(ids.get(0), ids.get(1), ids.get(2)), List.of(ids.get(3)), List.of(ids.get(4), ids.get(5)), List.of(ids.get(6)));
        h.flush();
        heard.clear();
        var away = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.ui(() -> away.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("away", "Away"),
            alpha.get().terminals().window(main).orElseThrow(), kitView(alpha.get()), dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW)));
        h.flush();
        var child = new AtomicReference<List<UUID>>();
        h.ui(() -> child.set(List.of(away.get().window().id(), away.get().window().tabs().getFirst().id())));
        assertThat(heard).as("the new window opens, then the tab, selected in it").containsExactly(
            java.util.Map.entry(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(child.get().get(0))),
            java.util.Map.entry(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(child.get().get(0), child.get().get(1))),
            java.util.Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(child.get().get(0), child.get().get(1))));
        assertThat(h.windowTabs(child.get().get(0))).containsExactly(List.of(child.get().get(1)));
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            assertThat(away.get().window()).as("a new window").isNotEqualTo(owner);
            assertThat(owner.tabs()).as("the owner's window is not touched").hasSize(7);
            assertThat(owner.activeTab().map(TabHandle::id)).as("nor its active tab").contains(ids.get(6));
            assertThat(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("below", "Again"), null, kitView(alpha.get()),
                dev.jasper.sdk.ui.TabPlacement.NEW_WINDOW)).as("an open key is selected where it is").isSameAs(below.get());
            assertThat(below.get().window().id()).isEqualTo(main);
        });
    }

    /**
     * Tabs and panes spec 4 and 13: Terminals.openTab opens a new tab, a split a new pane right after its target, in its
     * tab, and a tab moved in arrives as a tab of its own at the end of the strip.
     */
    @Test void openTabMakesANewTabASplitAPaneOfItsTargetsTabAndAMovedTabATabOfItsOwn() {
        UUID main = h.addTerminalWindow(), first = h.addTerminalTab(main, "first");
        UUID pane = h.addTerminalPane(first, "zsh", Path.of("/"));
        UUID other = h.addTerminalWindow(), stays = h.addTerminalTab(other, "stays");
        h.addTerminalPane(stays, "zsh", Path.of("/"));
        UUID moving = h.addTerminalTab(other, "moving");
        h.addTerminalPane(moving, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        var opened = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            UUID down = terminals.split(terminals.pane(pane).orElseThrow(), Direction.DOWN, OpenRequest.local()).orElseThrow().tab().id();
            UUID right = terminals.split(terminals.pane(pane).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow().tab().id();
            UUID tab = terminals.openTab(terminals.window(main).orElseThrow(), OpenRequest.local()).orElseThrow().tab().id();
            opened.set(List.of(down, right, tab));
        });
        UUID down = opened.get().get(0), right = opened.get().get(1), tab = opened.get().get(2);
        assertThat(h.windowTabs(main)).as("splits in their target's tab, right after it; openTab a tab of its own")
            .containsExactly(List.of(first, right, down), List.of(tab));
        h.moveTab(moving, main);
        assertThat(h.windowTabs(main)).as("a moved tab arrives as a tab of its own")
            .containsExactly(List.of(first, right, down), List.of(tab), List.of(moving));
        assertThat(h.windowTabs(other)).containsExactly(List.of(stays));
        h.closeTab(stays);
        assertThat(h.windowTabs(other)).as("a window whose last tab closed has none").isEmpty();
    }

    /**
     * Tabs and panes spec 8 and 11: a split item joins every Split list after the terminal, the bundled plugins' first,
     * and Jasper registers its right and down actions, which the plugin may place and which call its opener with the
     * window and the placement.
     */
    @Test void aSplitItemJoinsTheSplitListsWithRightAndDownActionsThatCallItsOpener() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "zsh");
        UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/"));
        List<String> opened = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        var notes = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            notes.set(context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Notes"),
                (shown, placement) -> opened.add(shown.id() + " " + placement)));
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.boom", "Boom"),
                (shown, placement) -> { throw new IllegalStateException("opener failure"); });
            assertThatIllegalArgumentException().as("one item per id").isThrownBy(() -> context.windows().splitItem(
                dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Again"), (shown, placement) -> { }));
            assertThatIllegalArgumentException().as("another plugin's namespace").isThrownBy(() -> context.windows().splitItem(
                dev.jasper.sdk.ui.SplitItemSpec.of("test.beta.notes", "Foreign"), (shown, placement) -> { }));
            context.menus().standard(StandardMenu.TAB).add("test.alpha.split_right_notes");
        });
        h.declareBundled("test.bundled");
        h.start(info("test.bundled"), Set.of(), Set.of(), context -> context.windows().splitItem(
            dev.jasper.sdk.ui.SplitItemSpec.of("test.bundled.sketch", "Sketch"), (shown, placement) -> { }));
        assertThat(h.actions()).containsExactly(
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true",
            "test.alpha.split_right_boom|Split Right with Boom|true", "test.alpha.split_down_boom|Split Down with Boom|true",
            "test.bundled.split_right_sketch|Split Right with Sketch|true", "test.bundled.split_down_sketch|Split Down with Sketch|true");
        assertThat(h.menu("TAB")).as("the plugin places them as its own").containsExactly("item:test.alpha.split_right_notes");
        h.ui(() -> assertThat(alpha.get().windows().splitChoices()).as("the terminal, then the bundled plugins' items first")
            .containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Sketch", "test.bundled.split_right_sketch", "test.bundled.split_down_sketch", false),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes", false),
                new dev.jasper.sdk.ui.SplitChoice("Boom", "test.alpha.split_right_boom", "test.alpha.split_down_boom", false)));
        h.ui(() -> {
            assertThat(h.invoke("test.alpha.split_right_notes", window, pane)).isTrue();
            assertThat(h.invoke("test.alpha.split_down_notes", window, null)).isTrue();
            assertThat(h.invoke("test.alpha.split_right_boom", window, null)).as("a throwing opener is contained").isTrue();
        });
        assertThat(opened).containsExactly(window + " SPLIT_RIGHT", window + " SPLIT_DOWN");
        h.ui(() -> notes.get().close());
        assertThat(h.actions()).as("its actions go with it").doesNotContain(
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true");
        h.ui(() -> assertThat(alpha.get().windows().splitChoices()).extracting(dev.jasper.sdk.ui.SplitChoice::label)
            .containsExactly("Terminal", "Sketch", "Boom"));
        assertThat(h.menu("TAB")).as("a placement of a closed action vanishes").isEmpty();
        h.stopAll();
        var fresh = new AtomicReference<PluginContext>();
        h.start(info("test.gamma"), Set.of(), Set.of(), fresh::set);
        h.ui(() -> assertThat(fresh.get().windows().splitChoices()).as("the stopped plugins' items go with them")
            .containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL));
    }

    /**
     * Pane flexibility spec 3 and workspace plan 9a Rulings 1: a split item whose actions ask something first ends its
     * actions' titles and its Split entries with an ellipsis; one that opens at once has none.
     */
    @Test void anAskingSplitItemEndsItsEntriesAndItsActionsTitlesWithAnEllipsis() {
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.open-file", "Open File").asking(),
                (shown, placement) -> { });
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.notes", "Notes"), (shown, placement) -> { });
        });
        assertThat(h.actions()).containsExactly(
            "test.alpha.split_right_open-file|Split Right with Open File…|true",
            "test.alpha.split_down_open-file|Split Down with Open File…|true",
            "test.alpha.split_right_notes|Split Right with Notes|true", "test.alpha.split_down_notes|Split Down with Notes|true");
        h.ui(() -> {
            var choices = alpha.get().windows().splitChoices();
            assertThat(choices).containsExactly(dev.jasper.sdk.ui.SplitChoice.TERMINAL,
                new dev.jasper.sdk.ui.SplitChoice("Open File", "test.alpha.split_right_open-file", "test.alpha.split_down_open-file", true),
                new dev.jasper.sdk.ui.SplitChoice("Notes", "test.alpha.split_right_notes", "test.alpha.split_down_notes", false));
            assertThat(choices).extracting(dev.jasper.sdk.ui.SplitChoice::rightLabel)
                .containsExactly("Terminal Right", "Open File Right…", "Notes Right");
            assertThat(choices).extracting(dev.jasper.sdk.ui.SplitChoice::downLabel)
                .containsExactly("Terminal Down", "Open File Down…", "Notes Down");
        });
    }

    /**
     * Tabs and panes spec 8 and 9: a content tab splits beside its own pane with any Split choice, the terminal's
     * included, whichever pane was focused, each on the side it names, and Move Pane to New Tab gives its pane a tab of its
     * own right after its tab; its tab's only pane and a pinned one stay. Once its plugin stops, its closed handle splits
     * and moves nothing, quietly.
     */
    @Test void aContentTabSplitsBesideItsOwnPaneAndMovesItToANewTab() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        var placements = new java.util.concurrent.CopyOnWriteArrayList<dev.jasper.sdk.ui.TabPlacement>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.sketch", "Sketch"), (window, placement) -> {
                placements.add(placement);
                context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("sketch", "Sketch"), window, kitView(context), placement);
            });
        });
        var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var ids = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var windows = alpha.get().windows();
            var owner = alpha.get().terminals().window(main).orElseThrow();
            notes.set(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get())));
            UUID note = owner.activeTab().orElseThrow().id();
            assertThat(notes.get().mayMoveToNewTab()).as("its tab's only pane").isFalse();
            notes.get().moveToNewTab();
            alpha.get().terminals().tab(shell).orElseThrow().select();
            notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT);
            var terminal = owner.activeTab().orElseThrow();
            assertThat(terminal.kind()).as("Terminal Right").isEqualTo(TabHandle.Kind.TERMINAL);
            alpha.get().terminals().tab(shell).orElseThrow().select();
            notes.get().split(windows.splitChoices().get(1), Direction.DOWN);
            UUID sketch = owner.activeTab().orElseThrow().id();
            ids.set(List.of(note, terminal.id(), sketch));
            assertThatIllegalArgumentException().as("not one of Jasper's choices").isThrownBy(() -> notes.get().split(
                new dev.jasper.sdk.ui.SplitChoice("Other", "test.alpha.split_right_other", "test.alpha.split_down_other", false),
                Direction.RIGHT));
        });
        UUID note = ids.get().get(0), terminal = ids.get().get(1), sketch = ids.get().get(2);
        assertThat(h.windowTabs(main)).as("beside its own pane, whichever pane was focused")
            .containsExactly(List.of(shell), List.of(note, sketch, terminal));
        assertThat(h.openRequests()).as("Terminal Right").containsExactly("beside|" + note + "|RIGHT");
        assertThat(placements).as("Sketch Down").containsExactly(dev.jasper.sdk.ui.TabPlacement.SPLIT_DOWN);
        h.ui(() -> {
            assertThat(notes.get().mayMoveToNewTab()).isTrue();
            notes.get().moveToNewTab();
            assertThat(alpha.get().terminals().window(main).orElseThrow().activeTab().map(TabHandle::id)).as("its new tab shows")
                .contains(note);
        });
        assertThat(h.windowTabs(main)).as("a tab of its own, right after its old tab")
            .containsExactly(List.of(shell), List.of(sketch, terminal), List.of(note));
        h.ui(() -> {
            notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.DOWN);
            var prompt = alpha.get().windows().dialog(new DialogSpec("Question", notes.get(), true));
            assertThat(notes.get().mayMoveToNewTab()).as("a pane with a modal prompt stays").isFalse();
            notes.get().moveToNewTab();
            prompt.close();
        });
        assertThat(h.windowTabs(main)).hasSize(3);
        assertThat(h.windowTabs(main).get(2)).hasSize(2).startsWith(note);
        assertThat(h.openRequests()).as("Terminal Down").containsExactly("beside|" + note + "|RIGHT", "beside|" + note + "|DOWN");
        h.stopAll();
        var before = h.windowTabs(main);
        h.ui(() -> {
            assertThatNoException().as("a closed handle's split").isThrownBy(() -> notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT));
            assertThatNoException().as("a closed handle's move").isThrownBy(() -> notes.get().moveToNewTab());
            assertThat(notes.get().mayMoveToNewTab()).isFalse();
        });
        assertThat(h.windowTabs(main)).as("nothing opens or moves once its plugin stops").isEqualTo(before);
        assertThat(h.openRequests()).hasSize(2);
    }

    /**
     * Pane flexibility spec 4 and 8 and workspace plan 9a Rulings 8: a content tab lists the other tabs of its window,
     * numbered as Select Tab numbers them and titled after their shown items, and Move Pane to Tab moves its pane into
     * one, to the right of that tab's shown pane, which shows, the item selected. Its own tab's number, a number no tab
     * has and an item with a modal prompt open move nothing, and a window of one tab offers none. Once its plugin stops,
     * its closed handle offers and moves nothing, quietly.
     */
    @Test void aContentTabMovesItsPaneIntoAnotherTabOfItsWindow() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        var note = new AtomicReference<UUID>();
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            notes.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get())));
            note.set(owner.activeTab().orElseThrow().id());
            assertThat(notes.get().tabChoices()).as("the window's other tab").containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "zsh"));
            assertThat(notes.get().tabChoices().getFirst().label()).isEqualTo("1 zsh");
            notes.get().moveToTab(2);
            notes.get().moveToTab(5);
        });
        assertThat(h.windowTabs(main)).as("its own tab's number, and one no tab has, move nothing")
            .containsExactly(List.of(shell), List.of(note.get()));
        h.ui(() -> {
            notes.get().moveToTab(1);
            assertThat(alpha.get().terminals().window(main).orElseThrow().activeTab().map(TabHandle::id)).as("selected there")
                .contains(note.get());
            assertThat(notes.get().tabChoices()).as("a window of one tab offers none").isEmpty();
        });
        assertThat(h.windowTabs(main)).as("to the right of the shell, its old tab gone").containsExactly(List.of(shell, note.get()));
        UUID top = h.addTerminalTab(main, "top");
        h.ui(() -> {
            assertThat(notes.get().tabChoices()).containsExactly(new dev.jasper.sdk.ui.TabChoice(2, "top"));
            var prompt = alpha.get().windows().dialog(new DialogSpec("Question", notes.get(), true));
            assertThat(notes.get().tabChoices()).as("a pane with a modal prompt offers none").isEmpty();
            notes.get().moveToTab(2);
            prompt.close();
        });
        assertThat(h.windowTabs(main)).containsExactly(List.of(shell, note.get()), List.of(top));
        h.ui(() -> notes.get().moveToTab(2));
        assertThat(h.windowTabs(main)).as("its old tab keeps its other pane").containsExactly(List.of(shell), List.of(top, note.get()));
        h.stopAll();
        var before = h.windowTabs(main);
        h.ui(() -> {
            assertThat(notes.get().tabChoices()).as("a closed handle offers none").isEmpty();
            assertThatNoException().as("a closed handle's move").isThrownBy(() -> notes.get().moveToTab(1));
        });
        assertThat(h.windowTabs(main)).as("nothing moves once its plugin stops").isEqualTo(before);
    }

    /**
     * Workspace plan 9a Rulings 8: a tab choice's title past 200 characters is its start and an ellipsis within that
     * limit, as Jasper's own menus show a tab's title.
     */
    @Test void aTabChoicesLongTitleIsCutAsJaspersMenusCutIt() {
        String title = "x".repeat(250);
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, title);
        h.addTerminalPane(shell, title, Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            var notes = alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get()));
            assertThat(notes.tabChoices()).containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "x".repeat(199) + "…"));
        });
    }

    /**
     * Workspace plan 9a Rulings 8: a title cut at the limit never splits an emoji's surrogate pair: with the pair's first
     * half where the ellipsis would follow, the title ends before the pair.
     */
    @Test void aTabChoicesTitleIsNotCutThroughAnEmoji() {
        String title = "x".repeat(198) + new String(Character.toChars(0x1F600)) + "y".repeat(40);
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, title);
        h.addTerminalPane(shell, title, Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            assertThat(notes.tabChoices()).containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "x".repeat(198) + "…"));
        });
    }

    /**
     * Pane flexibility spec 4: a choice carries its tab's title, the title of the tab's shown item, only to a plugin that
     * holds {@code terminal.observe}, as {@code TabHandle.title()} requires, or when that item is the plugin's own content,
     * which it names itself; any other choice has an empty title and reads {@code Tab 2}. Its number is the tab's place in
     * the strip either way, and moves the pane there.
     */
    @Test void aTabChoiceCarriesItsTabsTitleOnlyToAPluginThatMayReadIt() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        var gamma = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta"), Set.of(), Set.of(), beta::set);
        h.start(info("test.gamma", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), gamma::set);
        var ids = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var other = openContent(beta.get(), main, "other", "Other");
            UUID others = beta.get().terminals().window(main).orElseThrow().activeTab().orElseThrow().id();
            var memo = openContent(alpha.get(), main, "memo", "Memo");
            UUID memos = alpha.get().terminals().window(main).orElseThrow().activeTab().orElseThrow().id();
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            UUID note = alpha.get().terminals().window(main).orElseThrow().activeTab().orElseThrow().id();
            var seen = openContent(gamma.get(), main, "seen", "Seen");
            UUID seens = gamma.get().terminals().window(main).orElseThrow().activeTab().orElseThrow().id();
            ids.set(List.of(others, memos, note, seens));
            assertThat(notes.tabChoices()).as("without terminal.observe: the name of its own content, and no other")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, ""), new dev.jasper.sdk.ui.TabChoice(2, ""),
                    new dev.jasper.sdk.ui.TabChoice(3, "Memo"), new dev.jasper.sdk.ui.TabChoice(5, ""));
            assertThat(notes.tabChoices()).extracting(dev.jasper.sdk.ui.TabChoice::label)
                .containsExactly("Tab 1", "Tab 2", "3 Memo", "Tab 5");
            assertThat(other.tabChoices()).as("another plugin's content names nothing to a plugin that may not read it")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, ""), new dev.jasper.sdk.ui.TabChoice(3, ""),
                    new dev.jasper.sdk.ui.TabChoice(4, ""), new dev.jasper.sdk.ui.TabChoice(5, ""));
            assertThat(seen.tabChoices()).as("with terminal.observe: every tab's title")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "zsh"), new dev.jasper.sdk.ui.TabChoice(2, "Other"),
                    new dev.jasper.sdk.ui.TabChoice(3, "Memo"), new dev.jasper.sdk.ui.TabChoice(4, "Notes"));
            assertThat(memo.tabChoices()).extracting(dev.jasper.sdk.ui.TabChoice::number).containsExactly(1, 2, 4, 5);
            notes.moveToTab(1);
        });
        UUID others = ids.get().get(0), memos = ids.get().get(1), note = ids.get().get(2), seens = ids.get().get(3);
        assertThat(h.windowTabs(main)).as("a tab with no title shown moves a pane there as any other")
            .containsExactly(List.of(shell, note), List.of(others), List.of(memos), List.of(seens));
    }

    /**
     * Pane flexibility spec 4: a choice's title is the title of its tab's shown item, so a tab that holds the plugin's
     * own content behind another plugin's shown pane names nothing to a plugin without {@code terminal.observe}; once
     * its own content shows there, its name does.
     */
    @Test void aTabWhoseShownPaneIsAnotherPluginsNamesNothingThoughItHoldsThePluginsOwnContent() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta"), Set.of(), Set.of(), beta::set);
        h.ui(() -> {
            var memo = openContent(alpha.get(), main, "memo", "Memo");
            beta.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("other", "Other"),
                beta.get().terminals().window(main).orElseThrow(), kitView(beta.get()), dev.jasper.sdk.ui.TabPlacement.SPLIT_RIGHT);
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            assertThat(notes.tabChoices()).as("its own Memo behind Other, which shows")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, ""), new dev.jasper.sdk.ui.TabChoice(2, ""));
            memo.select();
            assertThat(notes.tabChoices()).as("Memo shows there now")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, ""), new dev.jasper.sdk.ui.TabChoice(2, "Memo"));
        });
    }

    /**
     * Pane flexibility spec 4: the pane moves to the right of the target tab's shown pane, not to the end of its panes.
     */
    @Test void aContentTabMovesRightOfTheShownPaneOfATabOfTwoPanes() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        var ids = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            var draft = openContent(alpha.get(), main, "draft", "Draft");
            UUID drafted = owner.activeTab().orElseThrow().id();
            draft.split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT);
            UUID terminal = owner.activeTab().orElseThrow().id();
            draft.select();
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            UUID note = owner.activeTab().orElseThrow().id();
            ids.set(List.of(drafted, terminal, note));
            assertThat(notes.tabChoices()).as("a tab is titled after its shown pane's item")
                .containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "zsh"), new dev.jasper.sdk.ui.TabChoice(2, "Draft"));
            notes.moveToTab(2);
        });
        UUID drafted = ids.get().get(0), terminal = ids.get().get(1), note = ids.get().get(2);
        assertThat(h.windowTabs(main)).as("right after the shown pane, not after the last").containsExactly(
            List.of(shell), List.of(drafted, note, terminal));
    }

    /**
     * Pane flexibility spec 4: an item waiting on a close decision is offered no tab and moves into none, and is offered
     * them again once it is kept.
     */
    @Test void aContentTabWaitingOnACloseDecisionIsOfferedNoTabAndMovesIntoNone() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var note = new AtomicReference<UUID>();
        h.ui(() -> {
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            note.set(alpha.get().terminals().window(main).orElseThrow().activeTab().orElseThrow().id());
            var answer = new CompletableFuture<Boolean>();
            alpha.get().windows().onTabsClosing((tabs, batch) -> answer);
            var closing = notes.requestClose();
            assertThat(notes.tabChoices()).as("waiting on its close decision").isEmpty();
            notes.moveToTab(1);
            answer.complete(false);
            assertThat(closing.join()).isFalse();
            assertThat(notes.tabChoices()).as("kept").containsExactly(new dev.jasper.sdk.ui.TabChoice(1, "zsh"));
        });
        assertThat(h.windowTabs(main)).as("nothing moved while it waited").containsExactly(List.of(shell), List.of(note.get()));
    }

    /**
     * Pane flexibility spec 4: a number that moves nothing selects nothing: an item that is not shown stays inactive, and
     * the move that does go selects it.
     */
    @Test void aRefusedMoveToTabLeavesAnUnselectedItemInactive() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var notes = openContent(alpha.get(), main, "notes", "Notes");
            var draft = openContent(alpha.get(), main, "draft", "Draft");
            assertThat(notes.isActive()).as("another tab was shown after it").isFalse();
            assertThat(draft.isActive()).isTrue();
            notes.moveToTab(2);
            notes.moveToTab(9);
            notes.moveToTab(0);
            assertThat(notes.isActive()).as("its own tab's number, one no tab has and none select nothing").isFalse();
            assertThat(draft.isActive()).isTrue();
            notes.moveToTab(3);
            assertThat(notes.isActive()).as("a move that goes selects the item").isTrue();
        });
    }

    /**
     * Workspace plan 8b Rulings 19: Terminal Right and Terminal Down beside a content tab open a local shell, so they need
     * {@code terminal.open}, as {@code Terminals.split} does; without it the call is refused, and nothing opens or is
     * selected. The capability comes first, as in {@code Terminals}, so a closed tab's split is refused too.
     */
    @Test void aContentTabsTerminalSplitNeedsTerminalOpen() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var note = new AtomicReference<UUID>();
        var notes = new AtomicReference<dev.jasper.sdk.ui.ContentTab>();
        h.ui(() -> {
            var owner = alpha.get().terminals().window(main).orElseThrow();
            notes.set(alpha.get().windows().openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get())));
            note.set(owner.activeTab().orElseThrow().id());
            alpha.get().terminals().tab(shell).orElseThrow().select();
            for (Direction direction : List.of(Direction.RIGHT, Direction.DOWN))
                assertThatThrownBy(() -> notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, direction))
                    .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                        assertThat(failure.pluginId()).isEqualTo("test.alpha");
                        assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OPEN);
                    });
            assertThat(owner.activeTab().map(TabHandle::id)).as("nothing is selected").contains(shell);
        });
        assertThat(h.windowTabs(main)).as("nothing opens").containsExactly(List.of(shell), List.of(note.get()));
        h.ui(() -> {
            notes.get().close();
            assertThatThrownBy(() -> notes.get().split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT)).as("a closed tab's split")
                .isInstanceOf(MissingCapabilityException.class);
        });
        assertThat(h.windowTabs(main)).containsExactly(List.of(shell));
        assertThat(h.openRequests()).isEmpty();
    }

    /**
     * Workspace plan 8b Task 4 review: a content tab waiting on a close decision splits nothing, with the terminal's choice
     * or a split item's, as it moves nowhere; nothing opens or is selected, and the split item's opener never runs. The
     * wait is that tab's alone: another content tab of the same plugin splits meanwhile.
     */
    @Test void aContentTabWaitingOnACloseDecisionSplitsNothing() {
        UUID main = h.addTerminalWindow(), shell = h.addTerminalTab(main, "zsh");
        h.addTerminalPane(shell, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        var placements = new java.util.concurrent.CopyOnWriteArrayList<dev.jasper.sdk.ui.TabPlacement>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.windows().splitItem(dev.jasper.sdk.ui.SplitItemSpec.of("test.alpha.sketch", "Sketch"),
                (window, placement) -> placements.add(placement));
        });
        var ids = new AtomicReference<List<UUID>>();
        h.ui(() -> {
            var windows = alpha.get().windows();
            var owner = alpha.get().terminals().window(main).orElseThrow();
            var notes = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("notes", "Notes"), owner, kitView(alpha.get()));
            UUID note = owner.activeTab().orElseThrow().id();
            var draft = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("draft", "Draft"), owner, kitView(alpha.get()));
            UUID drafted = owner.activeTab().orElseThrow().id();
            var answer = new CompletableFuture<Boolean>();
            windows.onTabsClosing((tabs, batch) -> answer);
            var closing = notes.requestClose();
            alpha.get().terminals().tab(shell).orElseThrow().select();
            notes.split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT);
            notes.split(windows.splitChoices().get(1), Direction.DOWN);
            assertThat(owner.activeTab().map(TabHandle::id)).as("nothing is selected").contains(shell);
            draft.split(dev.jasper.sdk.ui.SplitChoice.TERMINAL, Direction.RIGHT);
            ids.set(List.of(note, drafted, owner.activeTab().orElseThrow().id()));
            answer.complete(false);
            assertThat(closing.join()).isFalse();
        });
        UUID note = ids.get().get(0), draft = ids.get().get(1), terminal = ids.get().get(2);
        assertThat(placements).as("the split item's opener never runs").isEmpty();
        assertThat(h.windowTabs(main)).as("nothing opens beside the waiting tab; the other tab's split opens")
            .containsExactly(List.of(shell), List.of(note), List.of(draft, terminal));
        assertThat(h.openRequests()).containsExactly("beside|" + draft + "|RIGHT");
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle opened = terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localIn(Path.of("/tmp").toAbsolutePath())).orElseThrow();
            assertThat(opened.isOpen()).isTrue();
            assertThat(opened.tab().id()).isNotEqualTo(tab);
            PaneHandle split = terminals.split(terminals.pane(only).orElseThrow(), Direction.DOWN, OpenRequest.local()).orElseThrow();
            assertThat(split.tab().id()).as("a split is a terminal tab of its own").isNotEqualTo(tab).isNotEqualTo(opened.tab().id());
            assertThat(split.tab().kind()).isEqualTo(TabHandle.Kind.TERMINAL);
            assertThat(split.tab().terminal()).contains(split);
            assertThat(split.tab().window().id()).isEqualTo(window);
            assertThat(terminals.window(window).orElseThrow().activeTab()).as("and it is selected").contains(split.tab());
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|" + Path.of("/tmp").toAbsolutePath(), "split|" + only + "|DOWN|-");
    }

    /**
     * As the application opens them: a new tab or split is announced, given its pane, then selected, so the active pane
     * goes straight to it.
     */
    @Test void aTabOrSplitOpenedInTheActiveWindowIsAnnouncedGivenItsPaneThenSelected() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        h.activateTerminalWindow(window);
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN, Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            for (var topic : List.of(TerminalEvents.TAB_OPENED, TerminalEvents.PANE_OPENED, TerminalEvents.TAB_SELECTED,
                    TerminalEvents.ACTIVE_PANE_CHANGED))
                context.events().subscribe(topic, event -> heard.add(java.util.Map.entry(topic, event)));
        });
        h.flush();
        heard.clear();
        var opened = new AtomicReference<PaneHandle>();
        var openedTab = new AtomicReference<UUID>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            opened.set(terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.local()).orElseThrow());
            openedTab.set(opened.get().tab().id());
        });
        h.flush();
        assertThat(heard).containsExactly(java.util.Map.entry(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window, openedTab.get())),
            java.util.Map.entry(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(openedTab.get(), opened.get().id())),
            java.util.Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window, openedTab.get())),
            java.util.Map.entry(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.of(opened.get().id()))));
        heard.clear();
        var split = new AtomicReference<PaneHandle>();
        var splitTab = new AtomicReference<UUID>();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            split.set(terminals.split(terminals.pane(only).orElseThrow(), Direction.RIGHT, OpenRequest.local()).orElseThrow());
            splitTab.set(split.get().tab().id());
        });
        h.flush();
        assertThat(heard).containsExactly(java.util.Map.entry(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window, splitTab.get())),
            java.util.Map.entry(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(splitTab.get(), split.get().id())),
            java.util.Map.entry(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(window, splitTab.get())),
            java.util.Map.entry(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.of(split.get().id()))));
    }

    @Test void shellProfilesAreListedWatchedAndOpenedByIdOnly() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
        h.setShellProfiles(List.of(pwsh));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        var changes = new AtomicInteger();
        var subscription = new AtomicReference<Subscription>();
        h.ui(() -> {
            assertThat(alpha.get().terminals().profiles()).containsExactly(pwsh);
            subscription.set(alpha.get().terminals().onProfilesChanged(changes::incrementAndGet));
            assertThatThrownBy(() -> bare.get().terminals().profiles()).isInstanceOf(MissingCapabilityException.class);
        });
        h.setShellProfiles(List.of(pwsh, ubuntu));
        h.flush();
        assertThat(changes.get()).isEqualTo(1);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.profiles()).containsExactly(pwsh, ubuntu);
            assertThat(terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localProfile("wsl:Ubuntu"))).as("known").isPresent();
            assertThat(terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localProfile("wsl:Debian"))).as("unknown").isEmpty();
            assertThat(terminals.split(terminals.pane(only).orElseThrow(), Direction.RIGHT, OpenRequest.localProfile("cmd"))).as("unknown").isEmpty();
            assertThat(terminals.split(terminals.pane(only).orElseThrow(), Direction.DOWN, OpenRequest.local())).as("the default").isPresent();
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu", "split|" + only + "|DOWN|-");
        subscription.get().close();
        h.setShellProfiles(List.of(pwsh));
        h.flush();
        assertThat(changes.get()).as("a closed registration hears nothing").isEqualTo(1);
        h.ui(() -> alpha.get().terminals().onProfilesChanged(changes::incrementAndGet));
        h.stopAll();
        h.setShellProfiles(List.of());
        h.flush();
        assertThat(changes.get()).as("a stopped plugin hears nothing").isEqualTo(1);
    }

    @Test void actionContextsCarryHandlesOfTheInvokingPlugin() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_INJECT), Set.of(), Set.of(), context ->
            context.actions().register(ActionSpec.of("test.alpha.type", "Type"), invoked -> {
                PaneHandle pane = invoked.pane().orElseThrow();
                seen.add(invoked.window().isOpen() + " " + pane.isOpen() + " " + pane.tab().id().equals(tab));
                pane.sendText("typed");
                try { pane.info(); } catch (MissingCapabilityException expected) { seen.add(expected.capability()); }
            }));
        assertThat(h.invoke("test.alpha.type", window, only)).isTrue();
        h.flush();
        assertThat(seen).containsExactly("true true true", Capabilities.TERMINAL_OBSERVE);
        assertThat(h.sentToPane(only)).containsExactly("write:typed");
    }

    /** A connection the test can watch: what was typed, how often it was closed, and when it exits. */
    private static final class Probe {
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();
        final AtomicInteger closes = new AtomicInteger();
        final CompletableFuture<Integer> exited = new CompletableFuture<>();
        TerminalConnection connection(String output) {
            return new TerminalConnection(new ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8)), typed,
                (columns, rows) -> { }, exited, closes::incrementAndGet);
        }
    }

    private PaneHandle openSession(AtomicReference<PluginContext> context, UUID window, SessionSpec spec) {
        var pane = new AtomicReference<PaneHandle>();
        h.ui(() -> pane.set(context.get().terminals().openTab(context.get().terminals().window(window).orElseThrow(), OpenRequest.session(spec)).orElseThrow()));
        return pane.get();
    }

    @Test void aProvidedSessionAppearsFirstThenAttachesAndCarriesBytesBothWays() {
        UUID window = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        assertThat(pendings).as("the connector ran, on the event thread, before openTab returned").hasSize(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|");
        assertThat(pendings.get(0).pane()).isEqualTo(pane);
        assertThat(pendings.get(0).columns()).isPositive();
        h.ui(() -> {
            assertThat(pane.info().kind()).isEqualTo(SessionKind.PLUGIN);
            assertThat(pane.info().providerPluginId()).contains("test.alpha");
            assertThat(pane.info().state()).isEqualTo(SessionState.CONNECTING);
        });
        pendings.get(0).status("Authenticating");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
        var probe = new Probe();
        pendings.get(0).attach(probe.connection("welcome"));
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("RUNNING|");
        h.typeIntoSession(pane.id(), "uptime\r");
        assertThat(probe.typed.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("uptime\r");
        assertThat(h.sessionOutput(pane.id())).isEqualTo("welcome");
        probe.exited.complete(3);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|exit 3");
        assertThat(probe.closes).hasValue(1);
    }

    @Test void ownershipTransfersAtAttachAndEveryConnectionIsClosedExactlyOnce() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        var first = new Probe(); var second = new Probe(); var late = new Probe();
        pendings.get(0).attach(first.connection(""));
        pendings.get(0).attach(second.connection(""));
        pendings.get(0).fail("ignored after attach");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("RUNNING|");
        assertThat(second.closes).as("offered to an attempt that already attached").hasValue(1);
        assertThat(first.closes).hasValue(0);
        h.closeTerminalPane(pane.id());
        h.flush();
        assertThat(first.closes).as("the pane closed").hasValue(1);
        pendings.get(0).attach(late.connection(""));
        h.flush();
        assertThat(late.closes).hasValue(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
    }

    @Test void failureCancellationAndReconnectFollowTheAttemptRules() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, new SessionSpec("build-host", Optional.empty(), ExitPolicy.KEEP_OPEN, pendings::add));
        pendings.get(0).fail("Connection refused");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|Connection refused");
        h.reconnectSession(pane.id());
        assertThat(pendings).as("the same connector, a fresh attempt").hasSize(2);
        var stale = new Probe();
        pendings.get(0).attach(stale.connection(""));
        h.flush();
        assertThat(stale.closes).as("a finished attempt cannot attach into a later one").hasValue(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|");

        var live = new Probe();
        pendings.get(1).attach(live.connection(""));
        h.flush();
        live.exited.complete(0);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|exit 0");
        assertThat(live.closes).as("closed before a reconnect is possible").hasValue(1);
        h.reconnectSession(pane.id());
        assertThat(pendings).hasSize(3);
        pendings.get(2).onCancelled(() -> seen.add("aborted"));
        var withdrawn = pendings.get(2).onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        h.cancelSession(pane.id());
        h.flush();
        assertThat(pendings.get(2).isCancelled()).isTrue();
        assertThat(h.sessionState(pane.id())).as("a cancelled reconnect returns to the disconnected pane").isEqualTo("EXITED|Connection cancelled");
        pendings.get(2).onCancelled(() -> seen.add("late"));
        h.flush();
        assertThat(seen).containsExactly("aborted", "late");
    }

    @Test void cancellingAnAttemptThatNeverShowedAnythingClosesThePane() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        h.cancelSession(pane.id());
        h.flush();
        assertThat(pendings.get(0).isCancelled()).isTrue();
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
        h.ui(() -> assertThat(pane.isOpen()).isFalse());
    }

    /**
     * Workspace plan 6b Rulings 44: a restored session's tab came back to be reconnected, so cancelling its first attempt
     * keeps its pane, which returns to the disconnected state, and Reconnect connects again.
     */
    @Test void cancellingARestoredSessionsFirstAttemptKeepsItsPane() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        var restored = new AtomicReference<PaneHandle>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context ->
            context.windows().restorer("ssh", request -> {
                context.terminals().openTab(request.window(), OpenRequest.session(SessionSpec.of(request.title(), pendings::add)
                    .withRestore(request.state()))).ifPresent(restored::set);
                return Optional.empty();
            }));
        var back = new AtomicReference<Optional<UUID>>();
        h.ui(() -> back.set(h.restoreTab("test.alpha", window, "build-host",
            new dev.jasper.sdk.RestoreState("ssh", java.util.Map.of("host", "build-host")))));
        assertThat(back.get()).as("restored").isPresent();
        PaneHandle pane = restored.get();
        assertThat(h.sessionState(pane.id())).as("it waits to be shown").isEqualTo("NOT_STARTED|");
        h.focusTerminalPane(pane.id());
        h.flush();
        assertThat(pendings).as("shown, it connects").hasSize(1);
        h.cancelSession(pane.id());
        h.flush();
        assertThat(pendings.get(0).isCancelled()).isTrue();
        assertThat(h.sessionState(pane.id())).as("its pane stays, disconnected").isEqualTo("EXITED|Connection cancelled");
        h.ui(() -> assertThat(pane.isOpen()).isTrue());
        h.reconnectSession(pane.id());
        assertThat(pendings).as("Reconnect connects again").hasSize(2);
    }

    @Test void providingSessionsNeedsTheCapabilityAndAThrowingConnectorFailsTheAttempt() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var bare = new AtomicReference<PluginContext>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.bare", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), bare::set);
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> assertThatThrownBy(() -> bare.get().terminals().openTab(bare.get().terminals().window(window).orElseThrow(),
            OpenRequest.session(SessionSpec.of("x", pending -> { })))).isInstanceOfSatisfying(MissingCapabilityException.class,
                failure -> assertThat(failure.capability()).isEqualTo(Capabilities.SESSION_PROVIDE)));
        assertThat(h.openRequests()).isEmpty();
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("x", pending -> { throw new IllegalStateException("no such host"); }));
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|no such host");
        assertThat(h.active("test.alpha")).as("a failing connector is contained").isTrue();
    }

    @Test void aSessionThatClosesWhenItEndsTakesItsTabWithIt() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, new SessionSpec("build-host", Optional.empty(), ExitPolicy.CLOSE, pendings::add));
        var tab = new AtomicReference<UUID>();
        h.ui(() -> tab.set(pane.tab().id()));
        var probe = new Probe();
        pendings.get(0).attach(probe.connection("bye"));
        h.flush();
        probe.exited.complete(0);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
        assertThat(probe.closes).hasValue(1);
        h.ui(() -> {
            assertThat(pane.isOpen()).isFalse();
            assertThat(alpha.get().terminals().tab(tab.get())).as("its tab closed with it").isEmpty();
            assertThat(alpha.get().terminals().window(window)).as("the window keeps its other tab").isPresent();
        });
    }

    @Test void stoppingAPluginCancelsWhatItIsStillConnecting() {
        UUID window = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        openSession(alpha, window, SessionSpec.of("build-host", pending -> { pendings.add(pending); pending.onCancelled(() -> seen.add("aborted")); }));
        h.stopAll();
        h.flush();
        assertThat(pendings.get(0).isCancelled()).isTrue();
        assertThat(seen).containsExactly("aborted");
        var late = new Probe();
        pendings.get(0).attach(late.connection(""));
        h.flush();
        assertThat(late.closes).as("after the plugin stopped, too").hasValue(1);
    }

    @Test void aRemoteDirectoryIsNeverALocalOneInQueriesOrEvents() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "zsh", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(TerminalEvents.CWD_CHANGED, heard::add);
            context.events().subscribe(TerminalEvents.COMMAND_FINISHED, heard::add);
        });
        h.flush();
        heard.clear();
        var remote = Optional.of(new RemoteDirectory("build-host", "/srv/app"));
        h.reportDirectory(only, "build-host", "/srv/app");
        h.finishCommand(only, "make", 0);
        h.ui(() -> {
            var info = alpha.get().terminals().pane(only).orElseThrow().info();
            assertThat(info.workingDirectory()).as("the user ran ssh by hand; this is not a local path").isEmpty();
            assertThat(info.remoteDirectory()).isEqualTo(remote);
        });
        h.reportDirectory(only, "", "/tmp");
        h.flush();
        h.ui(() -> {
            var info = alpha.get().terminals().pane(only).orElseThrow().info();
            assertThat(info.workingDirectory()).contains(Path.of("/tmp"));
            assertThat(info.remoteDirectory()).isEmpty();
        });
        assertThat(heard).hasSize(3);
        assertThat(heard.get(0)).isEqualTo(new TerminalEvents.CwdChanged(only, Optional.empty(), remote));
        assertThat(heard.get(1)).isInstanceOfSatisfying(TerminalEvents.CommandFinished.class, finished -> {
            assertThat(finished.workingDirectory()).isEmpty();
            assertThat(finished.remoteDirectory()).isEqualTo(remote);
        });
        assertThat(heard.get(2)).isEqualTo(new TerminalEvents.CwdChanged(only, Optional.of(Path.of("/tmp")), Optional.empty()));
    }

        static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), NAME = new PaletteVerb("name", "Name it");

        /** Two rows; "paste" pastes the title into the target pane, "name" asks for a name and reopens elsewhere. */
        static final class Things implements PaletteScope {
            final String id; final List<String> log = new ArrayList<>();
            Things(String id) { this.id = id; }
            @Override public ScopeSpec spec() { return ScopeSpec.of(id, "Things", "Search things", List.of(PASTE, NAME)); }
            @Override public PaletteResults search(String query, PaletteQuery context) {
                log.add("search:" + query + ":" + context.window().id() + ":" + context.target().map(pane -> pane.id().toString()).orElse("-"));
                return PaletteResults.of(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two").withEnabled(false)));
            }
            @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
                if (!verb.equals(NAME)) return Optional.empty();
                return Optional.of(new PaletteStep("Name " + row.title(), List.of(new PaletteStep.Field("name", "Name", row.title())),
                    (values, done) -> done.accept(values.get("name").isBlank() ? PaletteStep.Result.error("Give it a name")
                        : PaletteStep.Result.reopen("test.b.other", Optional.of("row-" + values.get("name")), Optional.of(values.get("name"))))));
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
                log.add("execute:" + row.id() + ":" + verb.id() + ":" + row.token());
                context.target().ifPresent(pane -> pane.paste(row.title()));
            }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
        }

        static final class Hosts implements FileSource {
            private final String id;
            Hosts(String id) { this.id = id; }
            @Override public FileSourceSpec spec() { return new FileSourceSpec(id, "Hosts"); }
            @Override public List<FileRoot> roots() { return List.of(new FileRoot("one", "One", "", false)); }
            @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            @Override public CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner) {
                return CompletableFuture.failedFuture(new java.io.IOException("offline"));
            }
        }

        @Test void aFileSourceNeedsTheCapabilityAndTheNamespaceAndVanishesWithThePlugin() {
            var failure = new AtomicReference<Throwable>();
            h.start(info("test.bare"), Set.of(), Set.of(), context -> {
                try { context.fileSources().register(new Hosts("test.bare.hosts")); } catch (RuntimeException caught) { failure.set(caught); }
            });
            assertThat(failure.get()).isInstanceOfSatisfying(MissingCapabilityException.class,
                missing -> assertThat(missing.capability()).isEqualTo(Capabilities.FILES_SOURCE));
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.FILES_SOURCE), Set.of(), Set.of(), a::set);
            var first = new AtomicReference<Subscription>();
            h.ui(() -> {
                assertThatIllegalArgumentException().isThrownBy(() -> a.get().fileSources().register(new Hosts("test.b.hosts")));
                first.set(a.get().fileSources().register(new Hosts("test.a.hosts")));
                assertThatIllegalArgumentException().isThrownBy(() -> a.get().fileSources().register(new Hosts("test.a.hosts")));
                a.get().fileSources().register(new Hosts("test.a.more"));
            });
            assertThat(h.fileSources()).containsExactly("test.a.hosts|Hosts", "test.a.more|Hosts");
            h.ui(() -> { first.get().close(); first.get().close(); });
            assertThat(h.fileSources()).containsExactly("test.a.more|Hosts");
            h.stopAll();
            assertThat(h.fileSources()).isEmpty();
        }

        @Test void aPaletteScopeNeedsTheCapabilityAndThePluginsNamespace() {
            var failure = new AtomicReference<Throwable>();
            h.start(info("test.bare"), Set.of(), Set.of(), context -> {
                try { context.palette().register(new Things("test.bare.things")); } catch (RuntimeException caught) { failure.set(caught); }
            });
            assertThat(failure.get()).isInstanceOfSatisfying(MissingCapabilityException.class,
                missing -> assertThat(missing.capability()).isEqualTo(Capabilities.PALETTE_CONTRIBUTE));
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), a::set);
            h.ui(() -> assertThatIllegalArgumentException().isThrownBy(() -> a.get().palette().register(new Things("test.b.things"))));
            h.ui(() -> assertThatIllegalArgumentException().as("the shortcut action must be the plugin's own")
                .isThrownBy(() -> a.get().palette().register(new PaletteScope() {
                    @Override public ScopeSpec spec() { return ScopeSpec.of("test.a.s", "S", "Search", List.of(PASTE)).withShortcutActionId("test.a.open"); }
                    @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
                    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
                    @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
                })));
            h.ui(() -> a.get().palette().register(new Things("test.a.things")));
            assertThat(h.scopes()).containsExactly("test.a.things|Things|paste,name");
            assertThat(h.active("test.bare")).as("a contained registration failure does not fail the plugin").isTrue();
        }

        /** SDK 0.18: a scope's hue, item noun and verb roles reach the host as the presentation will read them. */
        @Test void aScopeCarriesItsHueItemNounAndSplitVerbToTheHost() {
            var split = new PaletteVerb("split", "Open in a split pane", PaletteVerb.Role.SPLIT);
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), context -> {
                context.palette().register(presented(ScopeSpec.of("test.a.hosts", "Hosts", "Search hosts", List.of(PASTE, split))
                    .withHue(dev.jasper.sdk.ui.Nodes.Hue.GREEN).withItemNoun("session")));
                context.palette().register(presented(ScopeSpec.of("test.a.plain", "Plain", "Search", List.of(PASTE))));
            });
            assertThat(h.scopes()).containsExactly("test.a.hosts|Hosts|paste,split", "test.a.plain|Plain|paste");
            assertThat(h.scopePresentation("test.a.hosts")).isEqualTo("GREEN|session|STANDARD,SPLIT");
            assertThat(h.scopePresentation("test.a.plain")).as("NONE takes the plugin's icon hue, NEUTRAL without one; no noun is the label")
                .isEqualTo("NEUTRAL|Plain|STANDARD");
        }

        private static PaletteScope presented(ScopeSpec spec) {
            return new PaletteScope() {
                @Override public ScopeSpec spec() { return spec; }
                @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
                @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
                @Override public Subscription onChanged(Runnable listener) { return () -> { }; }
            };
        }

        @Test void aScopeGetsItsOwnRowsAndHandlesBackAndAStepCanReopenAnotherScope() {
            UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "t");
            UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/src"));
            var things = new Things("test.a.things");
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE, Capabilities.TERMINAL_INJECT), Set.of(), Set.of(),
                context -> context.palette().register(things));
            assertThat(h.searchScope("test.a.things", "on", window, pane)).containsExactly("one|One|true", "two|Two|false");
            assertThat(things.log).containsExactly("search:on:" + window + ":" + pane);
            assertThat(h.availableInScope("test.a.things", "one", "paste", window, pane)).isTrue();
            assertThat(h.availableInScope("test.a.things", "two", "paste", window, pane)).as("the default follows enabled").isFalse();
            assertThat(h.stepInScope("test.a.things", "one", "paste", window, pane)).isEmpty();
            assertThat(h.stepInScope("test.a.things", "one", "name", window, pane)).contains("Name One");
            assertThat(h.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", " "))).isEqualTo("error:Give it a name");
            assertThat(h.completeStep("test.a.things", "one", "name", window, pane, Map.of("name", "x"))).isEqualTo("reopen:test.b.other:row-x:x");
            h.executeInScope("test.a.things", "one", "paste", window, pane);
            assertThat(things.log).contains("execute:one:paste:t1");
            assertThat(h.sentToPane(pane)).containsExactly("paste:One");
            assertThat(h.searchScope("test.a.things", "", window, null)).hasSize(2);
            assertThat(things.log.getLast()).isEqualTo("search::" + window + ":-");
        }

        @Test void duplicateScopeIdsFailEachRegistrationRemovesOnlyItselfAndAStoppedPluginsScopesVanish() {
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), a::set);
            var first = new AtomicReference<Subscription>();
            h.ui(() -> {
                first.set(a.get().palette().register(new Things("test.a.things")));
                assertThatIllegalArgumentException().isThrownBy(() -> a.get().palette().register(new Things("test.a.things")));
                a.get().palette().register(new Things("test.a.more"));
            });
            assertThat(h.scopes()).containsExactly("test.a.things|Things|paste,name", "test.a.more|Things|paste,name");
            h.ui(() -> { first.get().close(); first.get().close(); });
            assertThat(h.scopes()).containsExactly("test.a.more|Things|paste,name");
            h.stopAll();
            assertThat(h.scopes()).isEmpty();
        }

        @Test void openingThePaletteNeedsTheCapabilityAndCarriesTheQueryAndRow() {
            UUID window = h.addTerminalWindow();
            var bare = new AtomicReference<PluginContext>();
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.bare", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), bare::set);
            h.start(info("test.a", Capabilities.PALETTE_CONTRIBUTE), Set.of(), Set.of(), context -> {
                a.set(context);
                context.actions().register(ActionSpec.of("test.a.open", "Things…"),
                    invoked -> context.palette().open(invoked.window(), "test.a.things", Optional.empty(), Optional.empty()));
                context.palette().register(new Things("test.a.things"));
            });
            h.ui(() -> assertThatThrownBy(() -> bare.get().palette().open(bare.get().terminals().window(window).orElseThrow(), "jasper.commands", Optional.empty(), Optional.empty()))
                .isInstanceOf(MissingCapabilityException.class));
            assertThat(h.invoke("test.a.open", window, null)).isTrue();
            h.ui(() -> a.get().palette().open(a.get().terminals().window(window).orElseThrow(), "jasper.commands", Optional.of("new"), Optional.of("new_tab")));
            assertThat(h.paletteOpens()).containsExactly(window + " test.a.things - -", window + " jasper.commands new new_tab");
        }

        @Test void theEditorReachesTheHostAndAPaneKnowsItsShell() {
            UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "t");
            UUID pane = h.addTerminalPane(tab, "zsh", Path.of("/src"));
            var a = new AtomicReference<PluginContext>();
            h.start(info("test.a", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), a::set);
            h.ui(() -> {
                a.get().platform().openInEditor(Path.of("/tmp/x.toml"));
                assertThat(a.get().terminals().pane(pane).orElseThrow().info().shell()).isEqualTo("zsh");
            });
            h.flush();
            for (int i = 0; i < 100 && h.openedInEditor().isEmpty(); i++) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
            assertThat(h.openedInEditor()).containsExactly(Path.of("/tmp/x.toml"));
        }

    @Test void notificationsReachTheHostInTheirGroups() {
        PluginContext a = started("test.a", Set.of());
        UUID window = h.addTerminalWindow();
        h.ui(() -> {
            var plugin = a.notifications();
            var tunnels = plugin.group(new GroupSpec("test.a.tunnels", "Tunnels", DisplayMode.HISTORY));
            assertThat(plugin.group().id()).isEqualTo("test.a");
            assertThat(plugin.group(new GroupSpec("test.a.tunnels", "Again", DisplayMode.OFF))).isSameAs(tunnels);
            assertThat(plugin.group(new GroupSpec("test.a", "Mine", DisplayMode.OFF))).isSameAs(plugin.group());
            plugin.group().info("Saved", "Three hosts");
            plugin.group().warning("Slow", "");
            tunnels.post(NotificationSpec.of(Severity.ERROR, "Tunnel down").withContent("db:5432")
                .withAction(NotificationAction.of("Restart", () -> { })).withWindow(a.terminals().window(window).orElseThrow()));
        });
        h.flush();
        assertThat(h.notificationGroups()).containsExactly("test.a|test.a|CARD", "test.a.activities|test.a activities|CARD",
            "test.a.tunnels|Tunnels|HISTORY");
        assertThat(h.notifications()).containsExactly(
            "test.a|INFO|Saved|Three hosts|-|-|-|live",
            "test.a|WARNING|Slow||-|-|-|live",
            "test.a.tunnels|ERROR|Tunnel down|db:5432|Restart|-|" + window + "|live");
    }

    @Test void groupsStayInThePluginsNamespace() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> {
            assertThatIllegalArgumentException().isThrownBy(() -> a.notifications().group(new GroupSpec("test.b.x", "X", DisplayMode.CARD)));
            assertThatIllegalArgumentException().isThrownBy(() -> a.notifications().group(new GroupSpec("test.ab.x", "X", DisplayMode.CARD)));
            assertThatIllegalArgumentException().as("the host's")
                .isThrownBy(() -> a.notifications().group(new GroupSpec("test.a.activities", "Mine", DisplayMode.CARD)));
        });
    }

    @Test void actionsAndTheBodyRunAndExpireWhenTheySay() {
        PluginContext a = started("test.a", Set.of());
        var ran = new java.util.concurrent.CopyOnWriteArrayList<String>();
        h.ui(() -> a.notifications().group().post(NotificationSpec.of(Severity.INFO, "Update ready")
            .withAction(NotificationAction.of("Install", () -> ran.add("install")))
            .withAction(new NotificationAction("Later", () -> ran.add("later"), false))
            .withAction(new NotificationAction("Break", () -> { throw new IllegalStateException("broken action"); }, false))
            .withActivate(() -> ran.add("body"))));
        h.flush();
        assertThat(h.runNotificationAction("Update ready", "Later")).isTrue();
        assertThat(h.runNotificationAction("Update ready", "Break")).as("contained, not thrown").isTrue();
        assertThat(h.clickNotification("Update ready")).isTrue();
        assertThat(h.notifications()).singleElement().asString().endsWith("|live");
        assertThat(h.runNotificationAction("Update ready", "Install")).isTrue();
        assertThat(h.notifications()).singleElement().asString().endsWith("|expired");
        assertThat(h.runNotificationAction("Update ready", "Later")).as("an expired notification's links stop working").isFalse();
        assertThat(ran).containsExactly("later", "body", "install");
    }

    @Test void aHandleExpiresItsNotificationAndStoppingExpiresTheRest() {
        PluginContext a = started("test.a", Set.of());
        var handle = new AtomicReference<NotificationHandle>();
        h.ui(() -> {
            handle.set(a.notifications().group().info("First", ""));
            a.notifications().group().info("Second", "");
        });
        h.flush();
        handle.get().expire();
        h.flush();
        assertThat(handle.get().expired()).isTrue();
        assertThat(h.notifications()).containsExactly("test.a|INFO|First||-|-|-|expired", "test.a|INFO|Second||-|-|-|live");
        h.stopAll();
        h.flush();
        assertThat(h.notifications()).allSatisfy(line -> assertThat(line).endsWith("|expired"));
        assertThatIllegalStateException().as("a closed context refuses posts").isThrownBy(() -> a.notifications().group().info("Late", ""));
    }

    @Test void aPostFromAnyThreadArrivesAndItsCauseIsMarked() throws Exception {
        PluginContext a = started("test.a", Set.of());
        Thread worker = Thread.ofPlatform().start(() ->
            a.notifications().group().error("Could not copy the address", new IllegalStateException("clipboard busy")));
        worker.join();
        h.flush();
        assertThat(h.notifications())
            .containsExactly("test.a|ERROR|Could not copy the address|IllegalStateException: clipboard busy|-|cause|-|live");
    }

    @Test void activityOutcomesArePostedInTheHostsActivitiesGroup() {
        var shown = new AtomicInteger();
        var context = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), started -> {
            context.set(started);
            started.actions().register(ActionSpec.of("test.a.show", "Show"), invoked -> shown.incrementAndGet());
        });
        PluginContext a = context.get();
        UUID window = h.addTerminalWindow();
        h.activateTerminalWindow(window);
        h.ui(() -> {
            a.activities().begin(ActivitySpec.of("Sync hosts")).succeed("3 hosts");
            a.activities().begin(ActivitySpec.of("Upload")).fail("Connection lost");
            a.activities().begin(ActivitySpec.of("Index")).cancelled();
            a.activities().begin(new ActivitySpec("Deploy", "", Optional.of("test.a.show"), Optional.empty())).succeed("");
        });
        h.flush();
        assertThat(h.clickNotification("Deploy")).as("the body runs the activity's action").isTrue();
        h.flush();
        assertThat(shown).hasValue(1);
        h.start(info("test.b"), Set.of(), Set.of(), broken -> {
            broken.activities().begin(ActivitySpec.of("Left open"));
            throw new IllegalStateException("broken");
        });
        h.flush();
        assertThat(h.activityLog()).as("the host still fails the activity it left open")
            .anySatisfy(event -> assertThat(event.title()).isEqualTo("Left open"));
        assertThat(h.notifications()).as("but a plugin that is not running posts no outcome").containsExactly(
            "test.a.activities|INFO|Sync hosts|3 hosts|-|-|-|live",
            "test.a.activities|ERROR|Upload|Connection lost|-|-|-|live",
            "test.a.activities|INFO|Deploy||-|-|-|live");
        h.ui(() -> a.activities().begin(ActivitySpec.of("Still running")));
        h.stopAll();
        h.flush();
        assertThat(h.notifications()).as("nor does one that stopped").hasSize(3);
    }

    @Test void browseOpensOnlyWebAddressesAndReachesTheHost() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> {
            a.platform().browse(java.net.URI.create("http://localhost:5432/"));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("file:///etc/hosts")));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("localhost:5432")));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("/relative")));
            assertThatThrownBy(() -> a.platform().browse(null)).isInstanceOf(NullPointerException.class);
        });
        h.flush();
        for (int i = 0; i < 100 && h.browsed().isEmpty(); i++) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        assertThat(h.browsed()).containsExactly(java.net.URI.create("http://localhost:5432/"));
    }

    @Test void copyPutsTextOnTheClipboardAndReadingNeedsClipboardRead() {
        var reader = new AtomicReference<PluginContext>();
        h.start(info("test.reader", Capabilities.CLIPBOARD_READ), Set.of(), Set.of(), reader::set);
        PluginContext writer = started("test.writer", Set.of());
        h.ui(() -> {
            assertThat(writer.platform().copy("line one\nline two")).isTrue();
            assertThat(reader.get().platform().clipboardText()).contains("line one\nline two");
            assertThatThrownBy(() -> writer.platform().clipboardText())
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("clipboard.read"));
            assertThatThrownBy(() -> writer.platform().copy(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> writer.platform().copySecret(null)).isInstanceOf(NullPointerException.class);
            assertThat(writer.platform().copy("")).isTrue();
        });
        assertThat(h.clipboard()).contains("");
    }

    @Test void aSecretIsClearedLaterOnlyIfTheClipboardStillHoldsIt() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> assertThat(a.platform().copySecret("hunter2")).isTrue());
        assertThat(h.clipboard()).contains("hunter2");
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("cleared after its time").contains("");
        h.ui(() -> a.platform().copySecret("hunter3"));
        h.userCopies("something else");
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("a later copy by the user is left alone").contains("something else");
        h.ui(() -> { a.platform().copySecret("hunter4"); a.platform().copy("not secret"); });
        h.expireClipboardSecret();
        assertThat(h.clipboard()).as("a later copy ends the secret").contains("not secret");
    }

    @Test void stoppingThePluginClearsTheSecretItCopied() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> a.platform().copySecret("hunter2"));
        h.stopAll();
        assertThat(h.clipboard()).contains("");
    }

    @Test void stoppingThePluginLeavesAPlainCopy() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> a.platform().copy("kept"));
        h.stopAll();
        assertThat(h.clipboard()).contains("kept");
    }

    @Test void aBusyClipboardIsReportedInsteadOfThrown() {
        PluginContext a = started("test.a", Set.of());
        h.clipboardBusy(true);
        h.ui(() -> assertThat(a.platform().copy("lost")).isFalse());
        h.flush();
        h.clipboardBusy(false);
        assertThat(h.notifications()).hasSize(1)
            .allSatisfy(line -> assertThat(line).startsWith("test.a|ERROR|Could not copy|").contains("|cause|"));
        assertThat(h.clipboard()).isEmpty();
    }

    @Test void aBusyClipboardIsReportedForASecretToo() {
        PluginContext a = started("test.a", Set.of());
        h.clipboardBusy(true);
        h.ui(() -> assertThat(a.platform().copySecret("lost too")).isFalse());
        h.flush();
        h.clipboardBusy(false);
        assertThat(h.notifications()).hasSize(1)
            .allSatisfy(line -> assertThat(line).startsWith("test.a|ERROR|Could not copy|").contains("|cause|"));
        assertThat(h.clipboard()).isEmpty();
    }

    @Test void hostedTabsPreserveIdentityAndDeferMovesDuringPromptsAndCloseDecisions() {
        try (var harness = newHarness()) {
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("dev.test.hosted", "Hosted", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            harness.ui(() -> {
                var windows = context.get().windows();
                var component = new javax.swing.JTextArea("keep this text");
                var content=context.get().kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Custom("editor",dev.jasper.sdk.ui.ControlState.READY,"editor",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,"Editor")),e -> {});
                content.registerContent(new dev.jasper.sdk.ui.CustomContent("editor",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,component,() -> {}));
                var tab = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("file", "File"), null, content);
                assertThat(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("file", "Again"), null, kitView(context.get()))).isSameAs(tab);
                var home = tab.window();
                windows.openTab(new dev.jasper.sdk.ui.ContentSpec("other", "Other"), home, kitView(context.get()));
                var disposed = new AtomicInteger(); tab.onClosed(disposed::incrementAndGet);
                var dialog = windows.dialog(new DialogSpec("Question", tab, true));
                dialog.show(); tab.moveToNewWindow();
                assertThat(tab.window()).isEqualTo(home);
                assertThat(tab.requestClose().join()).isFalse();
                dialog.close(); tab.moveToNewWindow();
                assertThat(tab.window()).isNotEqualTo(home);
                tab.moveToWindow(home); assertThat(tab.window()).isEqualTo(home);
                var answer = new CompletableFuture<Boolean>();
                windows.onTabsClosing((tabs, batch) -> answer);
                var closing = tab.requestClose();
                assertThat(tab.requestClose()).isSameAs(closing);
                tab.moveToNewWindow(); assertThat(tab.window()).isEqualTo(home);
                answer.complete(false);
                assertThat(closing.join()).isFalse(); assertThat(tab.isClosed()).isFalse();
                windows.onTabsClosing((tabs, batch) -> CompletableFuture.failedFuture(new IllegalStateException("save failed")));
                assertThat(tab.requestClose().join()).isFalse();
                windows.onTabsClosing((tabs, batch) -> CompletableFuture.completedFuture(true));
                assertThat(tab.requestClose().join()).isTrue();
                tab.close(); tab.select(); tab.moveToNewWindow();
                assertThat(disposed.get()).isEqualTo(1);
                assertThat(component.getText()).isEqualTo("keep this text");
                assertThatIllegalArgumentException().isThrownBy(() -> windows.dialog(new DialogSpec("Closed", tab, true)));
            });
        }
    }

    @Test void declaredSettingsReadTheirFileAndABadValueFallsBackWithOneReport() {
        h.setSetting("test.settings", "font_size", 99L);
        h.setSetting("test.settings", "tab_size", 6L);
        var size = new AtomicReference<Setting<Integer>>();
        var tab = new AtomicReference<Setting<Integer>>();
        h.start(info("test.settings"), Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            tab.set(context.settings().declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size")));
        });
        assertThat(h.active("test.settings")).isTrue();
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(tab.get().get()).isEqualTo(6);
        assertThat(h.settingsReports("test.settings")).singleElement().asString().startsWith("font_size: ");
    }

    /** Plan 15a (spec 5.8): saved, then the look's, then the plugin's own default. */
    @Test void aKitDefaultReplacesTheDeclaredDefaultAndASavedValueWins() {
        h.setKitDefault("test.kitdefault", "size", 15L);
        var size = new AtomicReference<Setting<Integer>>();
        h.start(new PluginInfo("test.kitdefault", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context ->
            size.set(context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size"))));
        assertThat(size.get().get()).isEqualTo(15);
        h.setSetting("test.kitdefault", "size", 13L);
        assertThat(size.get().get()).as("a saved value equal to the plugin's own default still wins").isEqualTo(13);
        assertThat(h.lookDefaultProblems("test.kitdefault")).isEmpty();
    }

    @Test void aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported() {
        h.setKitDefault("test.kitbad", "size", 99L);
        h.setKitDefault("test.kitbad", "mode", "sideways");
        var size = new AtomicReference<Setting<Integer>>(); var mode = new AtomicReference<Setting<String>>();
        h.start(new PluginInfo("test.kitbad", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context -> {
            size.set(context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
            mode.set(context.settings().declare(SettingSpec.of("mode", SettingKind.choice(List.of(Choice.of("up", "Up", "up"), Choice.of("down", "Down", "down"))), "up").label("Mode")));
        });
        assertThat(h.active("test.kitbad")).isTrue();
        assertThat(size.get().get()).isEqualTo(13);
        assertThat(mode.get().get()).isEqualTo("up");
        assertThat(h.lookDefaultProblems("test.kitbad")).hasSize(2).allSatisfy(line -> assertThat(line).startsWith("test.kitbad: the look's default for "))
            .anySatisfy(line -> assertThat(line).startsWith("test.kitbad: the look's default for size is not used: "));
    }

    @Test void aKitDefaultForAnUndeclaredKeyIsReportedAndAnotherPluginsTableIsIgnored() {
        h.setKitDefault("test.kitextra", "missing", true);
        h.setKitDefault("test.notrunning", "size", 9L);
        h.start(new PluginInfo("test.kitextra", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context ->
            context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
        assertThat(h.lookDefaultProblems("test.kitextra"))
            .containsExactly("test.kitextra: the look gives a default for missing, which the plugin does not declare");
        assertThat(h.lookDefaultProblems("test.notrunning")).isEmpty();
    }

    @Test void settingsAreDeclaredOnlyWhileStartingWithUniqueLabelledKeys() {
        var settings = new AtomicReference<PluginSettings>();
        h.start(info("test.declare"), Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            context.settings().declare(SettingSpec.of("a", SettingKind.bool(), false).label("A"));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("a", SettingKind.bool(), true).label("Again")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("a.b", SettingKind.bool(), true).label("Inside")));
            assertThatIllegalArgumentException().isThrownBy(() -> context.settings().declare(SettingSpec.of("b", SettingKind.bool(), true)));
        });
        assertThat(h.active("test.declare")).isTrue();
        h.ui(() -> assertThatIllegalStateException()
            .isThrownBy(() -> settings.get().declare(SettingSpec.of("late", SettingKind.bool(), false).label("Late"))));
    }

    @Test void aPluginGivesOneSettingsPageAndOnlyWhileStarting() {
        var settings = new AtomicReference<PluginSettings>();
        SettingsPanel panel = panelHost -> { throw new AssertionError("Page must remain lazy"); };
        h.start(info("test.page"), Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            context.settings().page(panel);
            assertThatIllegalStateException().isThrownBy(() -> context.settings().page(panel));
        });
        assertThat(h.active("test.page")).isTrue();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> settings.get().page(panel)));
    }

    /** Vim IdeaVim keys spec 4: a plugin opens Settings at its own page, on the UI thread, while it runs. */
    @Test void aPluginOpensItsSettingsPageOnTheUiThreadWhileItRuns() {
        var settings = new AtomicReference<PluginSettings>();
        h.start(info("test.show"), Set.of(), Set.of(), context -> settings.set(context.settings()));
        assertThat(h.active("test.show")).isTrue();
        h.ui(() -> settings.get().show());
        assertThatIllegalStateException().as("off the UI thread").isThrownBy(() -> settings.get().show());
        h.stopAll();
        h.ui(() -> assertThatIllegalStateException().as("after the plugin stopped").isThrownBy(() -> settings.get().show()));
    }

    @Test void setSavesTheValueAndItsListenersHearIt() throws Exception {
        var vim = new AtomicReference<Setting<Boolean>>();
        var size = new AtomicReference<Setting<Integer>>();
        var seen = new CopyOnWriteArrayList<Boolean>();
        var any = new AtomicInteger();
        h.start(info("test.save"), Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            vim.get().onChanged(seen::add);
            context.settings().onChanged(any::incrementAndGet);
        });
        var saved = new AtomicReference<CompletableFuture<Void>>();
        h.ui(() -> saved.set(vim.get().set(true)));
        saved.get().get(5, TimeUnit.SECONDS);
        h.flush();
        assertThat(vim.get().get()).isTrue();
        assertThat(seen).containsExactly(true);
        assertThat(any.get()).isEqualTo(1);
        assertThat(h.savedSetting("test.save", "vim")).contains(true);
        var refused = new AtomicReference<CompletableFuture<Void>>();
        h.ui(() -> refused.set(size.get().set(99)));
        assertThatThrownBy(() -> refused.get().get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(h.savedSetting("test.save", "font_size")).isEmpty();
    }

    @Test void editsToTheFileReachListenersAndUnknownKeysAreReported() {
        var vim = new AtomicReference<Setting<Boolean>>();
        var seen = new CopyOnWriteArrayList<Boolean>();
        h.start(info("test.edit"), Set.of(), Set.of(), context -> {
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode")));
            vim.get().onChanged(seen::add);
        });
        h.setSetting("test.edit", "vim", true);
        h.flush();
        assertThat(seen).containsExactly(true);
        h.setSetting("test.edit", "mystery", 1L);
        h.flush();
        assertThat(seen).containsExactly(true);
        assertThat(h.settingsReports("test.edit")).anySatisfy(line -> assertThat(line).startsWith("mystery: "));
    }

    @Test void aValueOfTheWrongTypeKeepsTheLastGoodValue() {
        var vim = new AtomicReference<Setting<Boolean>>();
        h.start(info("test.wrong"), Set.of(), Set.of(), context ->
            vim.set(context.settings().declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"))));
        h.setSetting("test.wrong", "vim", true);
        h.setSetting("test.wrong", "vim", "yes");
        h.flush();
        assertThat(vim.get().get()).isTrue();
        assertThat(h.settingsReports("test.wrong")).anySatisfy(line -> assertThat(line).startsWith("vim: "));
    }

    private static final java.nio.file.Path HERE = java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();

    @Test void aSubcommandNeedsItsDeclarationAndOneHandler() {
        h.declareSubcommand("test.a", "greet", "Say hello");
        h.declareSubcommand("test.b", "other", "Someone else's");
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> {
            dev.jasper.sdk.cli.CommandHandler done = call -> java.util.concurrent.CompletableFuture.completedFuture(0);
            assertThatIllegalArgumentException().as("undeclared").isThrownBy(() -> a.get().commandLine().handle("undeclared", done));
            assertThatIllegalArgumentException().as("another plugin's").isThrownBy(() -> a.get().commandLine().handle("other", done));
            a.get().commandLine().handle("greet", done);
            assertThatIllegalArgumentException().as("a second handler").isThrownBy(() -> a.get().commandLine().handle("greet", done));
        });
    }

    @Test void aCallGetsItsArgumentsAndRelaysOutputAndTheExitCode() {
        h.declareSubcommand("test.a", "greet", "Say hello");
        var a = new AtomicReference<PluginContext>();
        var seen = new AtomicReference<dev.jasper.sdk.cli.CommandCall>();
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> registration.set(a.get().commandLine().handle("greet", call -> {
            seen.set(call);
            call.out("hello " + call.arguments().getFirst() + "\n");
            call.err("careful\n");
            return java.util.concurrent.CompletableFuture.completedFuture(call.arguments().size() == 2 ? 300 : 3);
        })));
        assertThat(h.call("greet", List.of("world", "x"), HERE, null).exitCode()).as("outside 0 to 255").isEqualTo(1);
        ContractCall call = h.call("greet", List.of("you"), HERE, null);
        assertThat(call.exitCode()).isEqualTo(3);
        assertThat(call.out()).isEqualTo("hello you\n");
        assertThat(call.err()).isEqualTo("careful\n");
        assertThat(seen.get().arguments()).containsExactly("you");
        assertThat(seen.get().workingDirectory()).isEqualTo(HERE);
        assertThat(seen.get().window()).isEmpty();
        h.ui(() -> registration.get().close());
        ContractCall closed = h.call("greet", List.of(), HERE, null);
        assertThat(closed.exitCode()).isEqualTo(1);
        assertThat(closed.err()).contains("greet is unavailable");
    }

    @Test void aCallFromAPaneGetsThatPanesWindow() {
        h.declareSubcommand("test.a", "where", "Which window");
        var a = new AtomicReference<PluginContext>();
        var window = new AtomicReference<Optional<dev.jasper.sdk.WindowOwner>>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        java.util.UUID windowId = h.addTerminalWindow();
        java.util.UUID pane = h.addTerminalPane(h.addTerminalTab(windowId, "shell"), "shell", HERE);
        h.ui(() -> a.get().commandLine().handle("where", call -> {
            window.set(call.window());
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }));
        assertThat(h.call("where", List.of(), HERE, pane).exitCode()).isZero();
        h.ui(() -> assertThat(window.get()).get().isInstanceOfSatisfying(dev.jasper.sdk.terminal.WindowHandle.class,
            handle -> assertThat(handle.id()).isEqualTo(windowId)));
        assertThat(h.call("where", List.of(), HERE, java.util.UUID.randomUUID()).exitCode()).isZero();
        assertThat(window.get()).as("a pane that is not open").isEmpty();
    }

    @Test void unknownFailingAndStoppedSubcommandsAreReported() {
        h.declareSubcommand("test.a", "boom", "Throws");
        h.declareSubcommand("test.a", "sour", "Fails");
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> {
            a.get().commandLine().handle("boom", call -> { throw new IllegalStateException("kaboom"); });
            a.get().commandLine().handle("sour", call -> java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("curdled")));
        });
        ContractCall unknown = h.call("nothing", List.of(), HERE, null);
        assertThat(unknown.exitCode()).isEqualTo(2);
        assertThat(unknown.err()).contains("unknown command nothing");
        ContractCall boom = h.call("boom", List.of(), HERE, null);
        assertThat(boom.exitCode()).isEqualTo(1);
        assertThat(boom.err()).contains("boom failed: kaboom");
        ContractCall sour = h.call("sour", List.of(), HERE, null);
        assertThat(sour.exitCode()).isEqualTo(1);
        assertThat(sour.err()).contains("sour failed: curdled");
        h.stopAll();
        ContractCall stopped = h.call("boom", List.of(), HERE, null);
        assertThat(stopped.exitCode()).isEqualTo(1);
        assertThat(stopped.err()).contains("boom is unavailable");
    }

    @Test void cancellingACallTellsTheHandlerAndDropsLaterOutput() {
        h.declareSubcommand("test.a", "wait", "Waits");
        var a = new AtomicReference<PluginContext>();
        var pending = new java.util.concurrent.CompletableFuture<Integer>();
        var cancelled = new java.util.concurrent.atomic.AtomicInteger();
        var seen = new AtomicReference<dev.jasper.sdk.cli.CommandCall>();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> a.get().commandLine().handle("wait", call -> {
            call.onCancelled(cancelled::incrementAndGet);
            seen.set(call);
            return pending;
        }));
        ContractCall call = h.call("wait", List.of(), HERE, null);
        assertThat(call.finished()).isFalse();
        call.cancel();
        assertThat(cancelled).hasValue(1);
        h.ui(() -> seen.get().out("too late\n"));
        assertThat(call.out()).isEmpty();
        h.ui(() -> pending.complete(0));
    }

    @Test void aCallStillRunningWhenItsPluginStopsEndsWithOne() {
        h.declareSubcommand("test.a", "wait", "Waits");
        var a = new AtomicReference<PluginContext>();
        var cancelled = new java.util.concurrent.atomic.AtomicInteger();
        h.start(info("test.a"), Set.of(), Set.of(), a::set);
        h.ui(() -> a.get().commandLine().handle("wait", call -> {
            call.onCancelled(cancelled::incrementAndGet);
            return new java.util.concurrent.CompletableFuture<Integer>();
        }));
        ContractCall call = h.call("wait", List.of(), HERE, null);
        assertThat(call.finished()).isFalse();
        h.stopAll();
        assertThat(call.finished()).as("ended as its plugin stopped").isTrue();
        assertThat(call.exitCode()).isEqualTo(1);
        assertThat(call.err()).isEqualTo("jasper: wait stopped\n");
        call.cancel();
        assertThat(cancelled).as("a stopped plugin's cancellation listener never runs").hasValue(0);
    }

    @Test void shellVariablesNeedTheCapabilityAndTheFirstPluginByIdWins() {
        var bare = new AtomicReference<PluginContext>();
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.start(info("test.beta", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), beta::set);
        h.start(info("test.alpha", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), alpha::set);
        var seen = new AtomicReference<Map<String, String>>();
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().terminals().environment(environment -> Map.of("A", "1")))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.environment"));
            beta.get().terminals().environment(environment -> Map.of("EDITOR", "beta", "PAGER", "less"));
            alpha.get().terminals().environment(environment -> {
                seen.set(environment);
                return Map.of("EDITOR", "alpha");
            });
        });
        Map<String, String> jasper = new java.util.HashMap<>(Map.of("HOME", "/home/me", "EDITOR", "vim"));
        assertThat(h.shellVariables(jasper)).as("by plugin id, not by start order").isEqualTo(Map.of("EDITOR", "alpha", "PAGER", "less"));
        assertThat(seen.get()).as("Jasper's own environment").isEqualTo(Map.of("HOME", "/home/me", "EDITOR", "vim"));
    }

    @Test void invalidReservedAndNulEntriesAreDroppedAndAFailingContributorAddsNothing() {
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), a::set);
        Map<String, String> offered = new java.util.HashMap<>();
        for (String name : List.of("TERM", "COLORTERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION", "JASPER_PANE", "JASPER_ANYTHING",
                "1ST", "WITH-DASH", "WITH SPACE", "", "\u00dcBER"))
            offered.put(name, "x");
        offered.put(null, "x");
        offered.put("NO_VALUE", null);
        offered.put("NUL_VALUE", "a\0b");
        offered.put("KEPT", "yes");
        offered.put("_kept_2", "yes");
        h.ui(() -> {
            var terminals = a.get().terminals();
            terminals.environment(environment -> offered);
            terminals.environment(environment -> { throw new IllegalStateException("broken contributor"); });
            terminals.environment(environment -> {
                environment.put("HOME", "/elsewhere");
                return Map.of("NEVER", "1");
            });
            terminals.environment(environment -> null);
        });
        Map<String, String> jasper = new java.util.HashMap<>(Map.of("HOME", "/home/me"));
        assertThat(h.shellVariables(jasper)).isEqualTo(Map.of("KEPT", "yes", "_kept_2", "yes"));
        assertThat(jasper).as("contributors get a read-only copy").isEqualTo(Map.of("HOME", "/home/me"));
    }

    @Test void aContributorEndsWithItsRegistrationAndWithItsPlugin() {
        var a = new AtomicReference<PluginContext>();
        var b = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), a::set);
        h.start(info("test.b", Capabilities.TERMINAL_ENVIRONMENT), Set.of(), Set.of(), b::set);
        var registration = new AtomicReference<Subscription>();
        h.ui(() -> {
            registration.set(a.get().terminals().environment(environment -> Map.of("A", "1")));
            b.get().terminals().environment(environment -> Map.of("B", "2"));
        });
        assertThat(h.shellVariables(Map.of())).isEqualTo(Map.of("A", "1", "B", "2"));
        h.ui(() -> registration.get().close());
        assertThat(h.shellVariables(Map.of())).isEqualTo(Map.of("B", "2"));
        h.stopAll();
        assertThat(h.shellVariables(Map.of())).as("the registration ends with its plugin").isEmpty();
    }

    private static final TabNamePieceSpec HOST = new TabNamePieceSpec("host", "the SSH host this tab is connected to");

    @Test void tabNamePiecesNeedTerminalObserve() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        h.addTerminalPane(tab, "make", Path.of("/src"));
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().terminals().addTabNamePiece(HOST))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.observe"));
            assertThatThrownBy(() -> bare.get().terminals().tab(tab).orElseThrow().namePieces())
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("terminal.observe"));
        });
    }

    @Test void aPluginPieceHoldsAValuePerTerminalTab() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        UUID pane = h.addTerminalPane(tab, "ssh", Path.of("/"));
        UUID notes = h.addContentTab(window, "notes");
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), beta::set);
        var piece = new AtomicReference<TabNamePiece>();
        h.ui(() -> {
            piece.set(alpha.get().terminals().addTabNamePiece(HOST));
            assertThat(piece.get().name()).isEqualTo("host");
            piece.get().set(alpha.get().terminals().tab(tab).orElseThrow(), " db1\n.example ");
            piece.get().set(alpha.get().terminals().tab(notes).orElseThrow(), "ignored");
            assertThat(beta.get().terminals().tab(tab).orElseThrow().namePieces()).as("another plugin reads it")
                .containsEntry("host", "db1 .example");
            assertThat(beta.get().terminals().tab(notes).orElseThrow().namePieces()).as("a content tab has none").isEmpty();
        });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1 .example"));
        assertThat(h.tabNamePieces(notes)).isEmpty();
        var closing = new AtomicReference<TabHandle>();
        h.ui(() -> {
            closing.set(alpha.get().terminals().tab(tab).orElseThrow());
            piece.get().set(closing.get(), "  ");
        });
        assertThat(h.tabNamePieces(tab)).as("blank clears").isEmpty();
        h.ui(() -> piece.get().set(closing.get(), "db1"));
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1"));
        h.closeTerminalPane(pane);
        assertThat(h.tabNamePieces(tab)).as("a closed tab's values go").isEmpty();
        h.ui(() -> assertThatCode(() -> piece.get().set(closing.get(), "late")).doesNotThrowAnyException());
        assertThat(h.tabNamePieces(tab)).as("a closed tab takes no value").isEmpty();
    }

    @Test void aPieceValueLosesLineSeparatorsAndBidirectionalControls() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        h.addTerminalPane(tab, "ssh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            terminals.addTabNamePiece(HOST).set(terminals.tab(tab).orElseThrow(), "\u202Edb1\u2028.example\u2029\u2066x\u2069");
            assertThat(terminals.tab(tab).orElseThrow().namePieces()).containsEntry("host", "db1 .example x");
        });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1 .example x"));
    }

    @Test void aPieceNameIsHeldByOnePluginAtATime() {
        var alpha = new AtomicReference<PluginContext>();
        var beta = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.start(info("test.beta", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), beta::set);
        h.ui(() -> {
            TabNamePiece held = alpha.get().terminals().addTabNamePiece(HOST);
            assertThatIllegalStateException().isThrownBy(() -> beta.get().terminals().addTabNamePiece(HOST));
            assertThatIllegalStateException().isThrownBy(() -> alpha.get().terminals().addTabNamePiece(HOST));
            held.close();
            held.close();
            assertThat(beta.get().terminals().addTabNamePiece(HOST).name()).isEqualTo("host");
        });
    }

    @Test void aPieceEndsWithItsHandleAndWithItsPlugin() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        h.addTerminalPane(tab, "ssh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            TabNamePiece host = terminals.addTabNamePiece(HOST);
            host.set(terminals.tab(tab).orElseThrow(), "db1");
            host.close();
            host.set(terminals.tab(tab).orElseThrow(), "db2");
            terminals.addTabNamePiece(new TabNamePieceSpec("branch", "the branch")).set(terminals.tab(tab).orElseThrow(), "main");
        });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("branch", "main"));
        h.stopAll();
        assertThat(h.tabNamePieces(tab)).as("the piece ends with its plugin").isEmpty();
    }

    @Test void setMayBeCalledFromAnyThread() throws Exception {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "ssh");
        h.addTerminalPane(tab, "ssh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        var piece = new AtomicReference<TabNamePiece>();
        var handle = new AtomicReference<TabHandle>();
        h.ui(() -> { piece.set(alpha.get().terminals().addTabNamePiece(HOST)); handle.set(alpha.get().terminals().tab(tab).orElseThrow()); });
        Thread worker = Thread.ofVirtual().start(() -> piece.get().set(handle.get(), "db1"));
        worker.join();
        h.ui(() -> { });
        assertThat(h.tabNamePieces(tab)).isEqualTo(Map.of("host", "db1"));
    }

    @Test void aTabOrASplitMayRunAProgramAndThatNeedsTerminalOpen() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        Path here = Path.of("/tmp").toAbsolutePath();
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle editor = terminals.openTab(terminals.window(window).orElseThrow(),
                OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("nvim", "+3", "notes.txt"), Optional.of(here)))).orElseThrow();
            assertThat(editor.isOpen()).isTrue();
            terminals.split(terminals.pane(only).orElseThrow(), Direction.RIGHT,
                OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("htop"), Optional.empty()))).orElseThrow();
            var others = bare.get().terminals();
            assertThatThrownBy(() -> others.openTab(others.window(window).orElseThrow(),
                    OpenRequest.local(dev.jasper.sdk.terminal.LocalSpec.running(List.of("htop"), Optional.empty()))))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OPEN));
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|" + here + "|run:nvim +3 notes.txt", "split|" + only + "|RIGHT|-|run:htop");
    }

    /** An editor that records {@code <file name>|<line or ->|<window id or ->} and answers {@code answer}. */
    private static dev.jasper.sdk.ui.FileEditor recordingEditor(List<String> calls, boolean answer) {
        return (file, line, window) -> {
            calls.add(file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|"
                + window.map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-"));
            return answer;
        };
    }

    @Test void providingTheEditorNeedsTheCapabilityAndOnePluginProvidesItAtATime() {
        var bare = new AtomicReference<PluginContext>();
        var a = new AtomicReference<PluginContext>();
        var b = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.b", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), b::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        java.nio.file.Path notes = HERE.resolve("notes.txt");
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("no plugin provides one").isFalse();
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().platform().editor(recordingEditor(calls, true)))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("editor.provide"));
            a.get().platform().editor(recordingEditor(calls, true));
            assertThatIllegalStateException().isThrownBy(() -> b.get().platform().editor(recordingEditor(calls, true)))
                .withMessage("The editor is already provided by test.a");
            assertThatIllegalStateException().as("the same plugin twice").isThrownBy(() -> a.get().platform().editor(recordingEditor(calls, true)))
                .withMessage("The editor is already provided by test.a");
        });
        assertThat(h.edit(notes, java.util.OptionalInt.of(3), Optional.empty())).isTrue();
        assertThat(calls).containsExactly("notes.txt|3|-");
    }

    @Test void aBundledPluginsEditorReplacesAnotherPluginsAndEndsWithItsPlugin() {
        h.declareBundled("test.z");
        var a = new AtomicReference<PluginContext>();
        var z = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.z", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), z::set);
        List<String> byA = new CopyOnWriteArrayList<>(), byZ = new CopyOnWriteArrayList<>();
        var replaced = new AtomicReference<Subscription>();
        java.nio.file.Path notes = HERE.resolve("notes.txt");
        h.ui(() -> {
            replaced.set(a.get().platform().editor(recordingEditor(byA, true)));
            z.get().platform().editor(recordingEditor(byZ, true));
            assertThatIllegalStateException().isThrownBy(() -> a.get().platform().editor(recordingEditor(byA, true)))
                .withMessage("The editor is already provided by test.z");
        });
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).isTrue();
        h.ui(() -> replaced.get().close());
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("closing the replaced registration leaves the bundled one").isTrue();
        assertThat(byA).isEmpty();
        assertThat(byZ).containsExactly("notes.txt|-|-", "notes.txt|-|-");
        h.stopAll();
        assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).as("the registration ends with its plugin").isFalse();
    }

    @Test void anEditorThatThrowsGivesFalseAndOneGetsItsPluginsHandleForAnOpenWindow() {
        UUID window = h.addTerminalWindow();
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        dev.jasper.sdk.ui.FileEditor recording = recordingEditor(calls, true);
        h.ui(() -> a.get().platform().editor((file, line, owner) -> {
            if (file.getFileName().toString().equals("broken.txt")) throw new IllegalStateException("the editor broke");
            return recording.edit(file, line, owner);
        }));
        assertThat(h.edit(HERE.resolve("broken.txt"), java.util.OptionalInt.empty(), Optional.empty())).isFalse();
        assertThat(h.edit(HERE.resolve("notes.txt"), java.util.OptionalInt.of(7), Optional.of(window))).isTrue();
        assertThat(h.edit(HERE.resolve("notes.txt"), java.util.OptionalInt.empty(), Optional.of(UUID.randomUUID())))
            .as("a window that is not open is no window").isTrue();
        assertThat(calls).containsExactly("notes.txt|7|" + window, "notes.txt|-|-");
    }

    @Test void openInEditorGoesToTheProvidedEditorFirst() {
        var a = new AtomicReference<PluginContext>();
        var user = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.user"), Set.of(), Set.of(), user::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        h.ui(() -> a.get().platform().editor((file, line, window) -> {
            calls.add(file.getFileName().toString());
            return !file.getFileName().toString().equals("declined.txt");
        }));
        h.ui(() -> {
            user.get().platform().openInEditor(HERE.resolve("notes.txt"));
            user.get().platform().openInEditor(HERE.resolve("declined.txt"));
        });
        h.flush();
        // Up to 10 s: the app host's worker hops to the UI thread, which a loaded CI can delay.
        for (int i = 0; i < 1000 && (calls.size() < 2 || h.openedInEditor().isEmpty()); i++) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        assertThat(calls).containsExactlyInAnyOrder("notes.txt", "declined.txt");
        assertThat(h.openedInEditor()).as("only the declined file reaches the desktop").containsExactly(HERE.resolve("declined.txt"));
    }

    @Test void theCommandLineNamesTheClientOnceTheHostKnowsIt() {
        PluginContext a = started("test.a", Set.of());
        assertThat(a.commandLine().client()).as("no client yet").isEmpty();
        java.nio.file.Path client = HERE.resolve("bin").resolve("jasper");
        h.commandClient(client);
        assertThat(a.commandLine().client()).as("any thread").contains(client);
    }
}
