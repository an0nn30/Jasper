package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.PluginWindow;
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

import static dev.jasper.sdk.activity.ActivityEvent.State.FAILED;
import static dev.jasper.sdk.activity.ActivityEvent.State.PROGRESS;
import static dev.jasper.sdk.activity.ActivityEvent.State.STARTED;
import static dev.jasper.sdk.activity.ActivityEvent.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
        assertThat(alpha.get().appearance().variant()).isEqualTo(Variant.LIGHT);
        assertThat(seen).containsExactly(Variant.LIGHT);
        h.stopAll();
        assertThat(h.actions()).isEmpty();
        assertThat(h.menu("TAB")).isEmpty();
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
            PaneHandle opened = terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localIn(Path.of("/tmp"))).orElseThrow();
            assertThat(opened.isOpen()).isTrue();
            assertThat(opened.tab().id()).isNotEqualTo(tab);
            PaneHandle split = terminals.split(terminals.pane(only).orElseThrow(), Direction.DOWN, OpenRequest.local()).orElseThrow();
            assertThat(split.tab().id()).isEqualTo(tab);
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|/tmp", "split|" + only + "|DOWN|-");
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
}
