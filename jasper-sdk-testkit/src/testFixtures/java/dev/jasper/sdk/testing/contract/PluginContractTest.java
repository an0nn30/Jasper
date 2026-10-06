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
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.ShortcutContext;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
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
            var window=ctx.windows().create(new WindowSpec("test.owned.window","Window",new Dimension(400,300),true));
            assertThatThrownBy(() -> window.setContent(alien)).isInstanceOf(IllegalArgumentException.class);
            window.setContent(view);window.setContent(view);
            assertThat(h.viewRoute(view)).as("window content keeps the chrome slot").isEqualTo("CHROME");
            assertThat(ctx.windows().create(new WindowSpec("test.owned.window","Window",new Dimension(400,300),true))).isSameAs(window);
            var veto=window.onClosing(() -> false);
            assertThat(h.requestClose("test.owned.window")).isFalse();assertThat(disposed.get()).isZero();
            veto.close();window.close();window.close();assertThat(disposed.get()).isEqualTo(1);
            var document=ctx.kit().create(descriptor,e -> {});
            document.registerContent(new dev.jasper.sdk.ui.CustomContent("document",dev.jasper.sdk.ui.Nodes.CustomKind.EDITOR,new javax.swing.JTextArea("retained"),disposed::incrementAndGet));
            assertThatThrownBy(() -> ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("foreign","Foreign"),null,alien)).isInstanceOf(IllegalArgumentException.class);
            assertThat(h.viewRoute(document)).as("a view before it is mounted").isEqualTo("CHROME");
            var tab=ctx.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("doc","Document"),null,document);
            assertThat(h.viewRoute(document)).as("workspace content takes the workspace slot").isEqualTo("WORKSPACE");
            tab.move(true);assertThat(tab.isSeparate()).isTrue();assertThat(h.viewRoute(document)).as("a move keeps it").isEqualTo("WORKSPACE");
            tab.move(false);assertThat(tab.isSeparate()).isFalse();assertThat(h.viewRoute(document)).isEqualTo("WORKSPACE");
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
        UUID window = h.addTerminalWindow();
        UUID tab = h.addTerminalTab(window, "local");
        UUID pane = h.addTerminalPane(tab, "shell", Path.of("/"));
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
        h.closeTerminalPane(pane); h.flush();
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

    @Test void activitiesLeftOpenFailWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        alpha.activities().begin(ActivitySpec.of("Sync"));
        h.flush();
        h.stopAll();
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

    @Test void shortcutContextsNameOnlyThePluginsOwnPanelsAndWindows() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.new", "New").withDefaultBindings("cmd+n", "ctrl+n")
                .withContext(ShortcutContext.panel("test.alpha.hosts")), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.save", "Save").withContext(ShortcutContext.content()), invoked -> { });
            context.actions().register(ActionSpec.of("test.alpha.copy", "Copy")
                .withContext(ShortcutContext.window("test.alpha.manager")), invoked -> { });
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.foreign", "Foreign")
                .withContext(ShortcutContext.panel("test.beta.hosts")), invoked -> { }));
            assertThatIllegalArgumentException().isThrownBy(() -> context.actions().register(ActionSpec.of("test.alpha.near", "Near")
                .withContext(ShortcutContext.window("test.alphabet.manager")), invoked -> { }));
        });
        assertThat(h.active("test.alpha")).isTrue();
        assertThat(h.actions()).containsExactly("test.alpha.new|New|true", "test.alpha.save|Save|true", "test.alpha.copy|Copy|true");
    }

    @Test void placementsNameOnlyThePluginsOwnActionsAndVanishWithThem() {
        var run = new AtomicReference<PluginAction>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            run.set(context.actions().register(ActionSpec.of("test.alpha.run", "Run"), invoked -> { }));
            context.actions().register(ActionSpec.of("test.alpha.stop", "Stop"), invoked -> { });
            context.toolbar().add(ToolbarItem.action("test.alpha.run"));
            context.toolbar().add(ToolbarItem.menu(new javax.swing.ImageIcon(), "Alpha", List.of("test.alpha.run", "test.alpha.stop")));
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
            assertThatIllegalArgumentException().isThrownBy(() -> context.appearance().icon("no/such/icon.svg"));
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

    @Test void aPanelMayNameOneOfThePluginsOwnActionsAsItsToggle() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.show", "Toggle Hosts"), invoked -> { });
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", new ImageIcon(), Anchor.LEFT)
                .withToggleActionId("test.alpha.show"), host -> new JLabel());
            context.panels().register(new PanelSpec("test.alpha.files", "Files", new ImageIcon(), Anchor.RIGHT), host -> new JLabel());
            for (String toggle : List.of("test.alpha.absent", "test.beta.show", "new_tab"))
                assertThatIllegalArgumentException().as(toggle).isThrownBy(() -> context.panels().register(
                    new PanelSpec("test.alpha.other", "Other", new ImageIcon(), Anchor.LEFT).withToggleActionId(toggle), host -> new JLabel()));
        });
        assertThat(h.panels()).as("a failed assertion above stops the plugin and removes its panels")
            .containsExactly("test.alpha.hosts|Hosts|LEFT|test.alpha.show", "test.alpha.files|Files|RIGHT");
    }

    @Test void aSplitPanelKeepsItsSplitBesideItsToggleAndStartsInTheSecondaryHalf() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.show", "Toggle Files"), invoked -> { });
            context.panels().register(new PanelSpec("test.alpha.files", "Files", new ImageIcon(), Anchor.LEFT).withSplit()
                .withToggleActionId("test.alpha.show"), host -> new JLabel());
            context.panels().register(new PanelSpec("test.alpha.log", "Log", new ImageIcon(), Anchor.BOTTOM).withSplit(),
                host -> new JLabel());
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT), host -> new JLabel());
        });
        assertThat(h.panels()).containsExactly("test.alpha.files|Files|LEFT+split|test.alpha.show",
            "test.alpha.log|Log|BOTTOM+split", "test.alpha.hosts|Hosts|RIGHT");
    }

    @Test void panelsAreNamespacedLazyPerWindowAndContained() {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        var registration = new AtomicReference<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            registration.set(context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT),
                host -> { hosts.add(host); return new JLabel("hosts"); }));
            context.panels().register(new PanelSpec("test.alpha.broken", "Broken", new ImageIcon(), Anchor.BOTTOM),
                host -> { throw new IllegalStateException("factory failure"); });
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.alpha.hosts", "Twice", new ImageIcon(), Anchor.LEFT), host -> new JLabel()));
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().register(
                new PanelSpec("test.beta.panel", "Foreign", new ImageIcon(), Anchor.LEFT), host -> new JLabel()));
        });
        assertThat(h.panels()).containsExactly("test.alpha.hosts|Hosts|RIGHT", "test.alpha.broken|Broken|BOTTOM");
        assertThat(hosts).as("no instance until a window shows the panel").isEmpty();
        UUID window = UUID.randomUUID();
        h.ui(() -> {
            assertThat(h.openPanel("test.alpha.hosts", window)).isInstanceOf(JLabel.class);
            assertThat(h.openPanel("test.alpha.broken", window)).as("a throwing factory is contained").isNull();
            assertThat(h.openPanel("test.alpha.absent", window)).isNull();
        });
        assertThat(hosts).singleElement().satisfies(host -> assertThat(host.window().id()).isEqualTo(window));
        h.ui(() -> registration.get().close());
        assertThat(h.panels()).containsExactly("test.alpha.broken|Broken|BOTTOM");
        h.stopAll();
        assertThat(h.panels()).isEmpty();
    }

    /**
     * PanelHost.setHeader (0.14.2) on a kit (ViewSpec) panel: per-window metadata the host shows in the panel's one
     * header row, replaced wholesale, cleared by EMPTY, contained when an action fails and ignored after the instance closes.
     */
    @Test void aKitPanelsHeaderIsPerWindowReplacedContainedAndIgnoredAfterClose() throws Exception {
        List<PanelHost> hosts = Collections.synchronizedList(new ArrayList<>());
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        var context = started("test.alpha", Set.of());
        var icon = new ImageIcon();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        javax.swing.SwingUtilities.invokeAndWait(() -> h.ui(() -> {
            context.panels().registerView(new PanelSpec("test.alpha.hosts", "Hosts", "folder", Anchor.LEFT), host -> {
                hosts.add(host);
                host.setHeader(new dev.jasper.sdk.ui.PanelHeader(String.valueOf(hosts.size() + 2), true, List.of(
                    new dev.jasper.sdk.ui.PanelHeader.Action("New session", icon, () -> calls.add("new")),
                    new dev.jasper.sdk.ui.PanelHeader.Action("Broken", icon, () -> { throw new IllegalStateException("header failed"); }))));
                var C = dev.jasper.sdk.ui.ControlState.READY;
                return context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Panel("hosts", C,
                    new dev.jasper.sdk.ui.Nodes.Header("hosts-header", C, "Hosts", java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
                    Optional.empty(), new dev.jasper.sdk.ui.Nodes.Text("body", C, "Body", dev.jasper.sdk.ui.Nodes.TextRole.BODY), Optional.empty())), e -> { });
            });
            assertThat(h.openPanel("test.alpha.hosts", first)).isNotNull();
            assertThat(h.openPanel("test.alpha.hosts", second)).isNotNull();
            assertThat(h.panelHeader("test.alpha.hosts", first)).isEqualTo("3|true|New session;Broken");
            assertThat(h.panelHeader("test.alpha.hosts", second)).isEqualTo("4|true|New session;Broken");

            assertThat(h.runPanelHeaderAction("test.alpha.hosts", first, "New session")).isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", first, "Broken")).as("a failing action is contained").isTrue();
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", first, "Absent")).isFalse();
            assertThat(calls).containsExactly("new");

            hosts.get(1).setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("import")))));
            assertThat(h.panelHeader("test.alpha.hosts", second)).isEqualTo("amp|false|Import");
            assertThat(h.panelHeader("test.alpha.hosts", first)).as("windows are independent").isEqualTo("3|true|New session;Broken");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", second, "Import")).isTrue();
            assertThat(calls).containsExactly("new", "import");

            hosts.get(0).setHeader(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(h.panelHeader("test.alpha.hosts", first)).isEqualTo("|false|-");
            assertThatNullPointerException().isThrownBy(() -> hosts.get(0).setHeader(null));
            assertThat(h.panelHeader("test.alpha.hosts", first)).isEqualTo("|false|-");

            h.closePanel("test.alpha.hosts", second);
            assertThat(h.panelHeader("test.alpha.hosts", second)).isEqualTo("|false|-");
            hosts.get(1).setHeader(new dev.jasper.sdk.ui.PanelHeader("late", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Import", icon, () -> calls.add("late")))));
            assertThat(h.panelHeader("test.alpha.hosts", second)).as("a closed instance publishes nothing").isEqualTo("|false|-");
            assertThat(h.runPanelHeaderAction("test.alpha.hosts", second, "Import")).isFalse();
            assertThat(calls).containsExactly("new", "import");
        }));
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

    @Test void windowsAreSingletonsGuardedOwnedAndClosedWithTheirPlugin() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        var manager = new AtomicReference<PluginWindow>();
        var veto = new AtomicReference<Subscription>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            PluginWindow window = context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true));
            manager.set(window);
            assertThat(context.windows().create(new WindowSpec("test.alpha.manager", "Manager", new Dimension(640, 480), true))).isSameAs(window);
            assertThatIllegalArgumentException().isThrownBy(() ->
                context.windows().create(new WindowSpec("test.beta.window", "Foreign", new Dimension(10, 10), false)));
            veto.set(window.onClosing(() -> false));
            window.onClosing(() -> { throw new IllegalStateException("guard failure"); });
            window.onClosed(() -> events.add("closed"));
            window.show();
            context.windows().dialog(new DialogSpec("Unlock", window, true));
        });
        assertThat(h.windows()).containsExactly("test.alpha.manager|Manager|true", "dialog|Unlock|false");
        h.ui(() -> {
            assertThat(h.requestClose("test.alpha.manager")).isFalse();
            veto.get().close();
            assertThat(h.requestClose("test.alpha.manager")).as("a throwing guard cannot trap the window open").isTrue();
            assertThat(h.requestClose("test.alpha.absent")).isFalse();
        });
        assertThat(events).containsExactly("closed");
        assertThat(h.windows()).as("the dialog went with its owner").isEmpty();
        h.ui(() -> {
            assertThatIllegalArgumentException().isThrownBy(() -> alpha.get().windows().dialog(new DialogSpec("Late", manager.get(), true)));
            alpha.get().windows().create(new WindowSpec("test.alpha.other", "Other", new Dimension(10, 10), false)).show();
        });
        assertThat(h.windows()).containsExactly("test.alpha.other|Other|true");
        h.stopAll();
        assertThat(h.windows()).isEmpty();
    }

    @Test void theCloseCommandAsksTheContentNewestFirstBeforeTheGuards() {
        var handled = new java.util.concurrent.atomic.AtomicBoolean(true);
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            PluginWindow window = context.windows().create(new WindowSpec("test.alpha.editor", "Editor", new Dimension(640, 480), true));
            window.onCloseCommand(() -> { asked.add("older"); return false; });
            window.onCloseCommand(() -> { asked.add("newer"); return handled.get(); });
            window.onCloseCommand(() -> { throw new IllegalStateException("handler failure"); });
            window.onClosing(() -> { asked.add("guard"); return allow.get(); });
            window.show();
        });
        h.ui(() -> assertThat(h.closeCommand("test.alpha.editor")).as("the newest handler that answers true keeps it open").isFalse());
        assertThat(asked).containsExactly("newer");
        asked.clear();
        handled.set(false);
        h.ui(() -> assertThat(h.closeCommand("test.alpha.editor")).as("unhandled, the guard still refuses").isFalse());
        assertThat(asked).containsExactly("newer", "older", "guard");
        asked.clear();
        allow.set(true);
        h.ui(() -> {
            assertThat(h.closeCommand("test.alpha.editor")).isTrue();
            assertThat(h.closeCommand("test.alpha.absent")).isFalse();
        });
        assertThat(asked).containsExactly("newer", "older", "guard");
        assertThat(h.windows()).isEmpty();
    }

    @Test void quitAsksEveryPluginWindowsGuardsAndClosesNothing() {
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            PluginWindow first = context.windows().create(new WindowSpec("test.alpha.first", "First", new Dimension(100, 100), false));
            first.onClosing(() -> { asked.add("first"); return true; });
            first.show();
            PluginWindow second = context.windows().create(new WindowSpec("test.alpha.second", "Second", new Dimension(100, 100), false));
            second.onClosing(() -> { asked.add("second"); return allow.get(); });
            context.windows().dialog(new DialogSpec("Details", second, false)).onClosing(() -> { asked.add("dialog"); return false; });
        });
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("first", "second");
        allow.set(true);
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("dialogs are not asked").isTrue());
        assertThat(asked).containsExactly("first", "second");
        assertThat(h.windows()).as("asking closes nothing").hasSize(3);
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
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src")), right = h.addTerminalPane(tab, "top", Path.of("/"));
        h.activateTerminalWindow(window);
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.windows()).extracting(handle -> handle.id()).containsExactly(window);
            assertThat(terminals.activeWindow()).contains(terminals.windows().get(0));
            var tabHandle = terminals.windows().get(0).activeTab().orElseThrow();
            assertThat(tabHandle.title()).isEqualTo("build");
            assertThat(tabHandle.panes()).extracting(PaneHandle::id).containsExactly(left, right);
            PaneHandle pane = terminals.activePane().orElseThrow();
            assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow());
            assertThat(pane.tab()).isEqualTo(tabHandle);
            assertThat(pane.tab().window().isActive()).isTrue();
            assertThat(pane.info().title()).isEqualTo("make");
            assertThat(pane.info().workingDirectory()).contains(Path.of("/src"));
            assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
            terminals.pane(right).orElseThrow().focus();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        });
        h.closeTerminalPane(right);
        h.ui(() -> assertThat(alpha.get().terminals().activePane().map(PaneHandle::id)).contains(left));
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

    @Test void injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt() throws Exception {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID only = h.addTerminalPane(tab, "make", Path.of("/src")), other = h.addTerminalPane(tab, "top", Path.of("/"));
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
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src")), right = h.addTerminalPane(tab, "top", Path.of("/"));
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
        assertThat(heard.subList(3, 5)).containsExactlyInAnyOrder(new TerminalEvents.PaneEvent(tab, right),
            new TerminalEvents.ActivePaneChanged(Optional.of(left)));
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
            assertThat(split.tab().id()).isEqualTo(tab);
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|" + Path.of("/tmp").toAbsolutePath(), "split|" + only + "|DOWN|-");
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

    @Test void hostedTabsPreserveIdentityAndDeferMovesDuringPromptsAndCloseDecisions() {
        try (var harness = newHarness()) {
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("dev.test.hosted", "Hosted", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            harness.ui(() -> {
                var windows = context.get().windows();
                var component = new javax.swing.JTextArea("keep this text");
                var tab = windows.openTab(new dev.jasper.sdk.ui.ContentSpec("file", "File"), null, component);
                assertThat(windows.openTab(new dev.jasper.sdk.ui.ContentSpec("file", "Again"), null, new javax.swing.JPanel())).isSameAs(tab);
                var disposed = new AtomicInteger(); tab.onClosed(disposed::incrementAndGet);
                var dialog = windows.dialog(new DialogSpec("Question", tab, true));
                dialog.show(); tab.move(true);
                assertThat(tab.isSeparate()).isFalse();
                assertThat(tab.requestClose().join()).isFalse();
                dialog.close(); tab.move(true);
                assertThat(tab.isSeparate()).isTrue();
                tab.move(false); assertThat(tab.isSeparate()).isFalse();
                var answer = new CompletableFuture<Boolean>();
                windows.onTabsClosing((tabs, batch) -> answer);
                var closing = tab.requestClose();
                assertThat(tab.requestClose()).isSameAs(closing);
                tab.move(true); assertThat(tab.isSeparate()).isFalse();
                answer.complete(false);
                assertThat(closing.join()).isFalse(); assertThat(tab.isClosed()).isFalse();
                windows.onTabsClosing((tabs, batch) -> CompletableFuture.failedFuture(new IllegalStateException("save failed")));
                assertThat(tab.requestClose().join()).isFalse();
                windows.onTabsClosing((tabs, batch) -> CompletableFuture.completedFuture(true));
                assertThat(tab.requestClose().join()).isTrue();
                tab.close(); tab.select(); tab.move(true);
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
        SettingsPanel panel = panelHost -> new JLabel("Mine");
        h.start(info("test.page"), Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            context.settings().page(panel);
            assertThatIllegalStateException().isThrownBy(() -> context.settings().page(panel));
        });
        assertThat(h.active("test.page")).isTrue();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> settings.get().page(panel)));
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
}
