package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

class PluginRuntimeTest {
    static final String FIXTURE = """
        package fix.probe;
        import dev.jasper.sdk.activity.ActivitySpec;
        import dev.jasper.sdk.events.AppEvents;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.settings.Setting;
        import dev.jasper.sdk.settings.SettingKind;
        import dev.jasper.sdk.settings.SettingSpec;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            private PluginContext context;
            private static void note(PluginContext context, String name, String text) {
                try { Files.writeString(context.dataDirectory().resolve(name), text); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
            @Override public void start(PluginContext context) {
                this.context = context;
                Setting<String> greeting = context.settings().declare(SettingSpec.of("greeting", SettingKind.text(), "none").label("Greeting"));
                note(context, "started", greeting.get());
                greeting.onChanged(value -> note(context, "changed", value));
                context.events().subscribe(AppEvents.THEME_CHANGED, event -> note(context, "theme", event.variant().name()));
                context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> note(context, "reloaded", "yes"));
                context.activities().begin(ActivitySpec.of("Probe work")).progress(0.5, "half way");
            }
            @Override public void stop() { note(context, "stopped", "yes"); }
        }
        """;

    static final String BOOM = """
        package fix.boom;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { throw new IllegalStateException("boom at start"); }
        }
        """;

    static final String LINK = """
        package fix.link;
        import dev.jasper.sdk.activity.ConnectionEvent;
        import dev.jasper.sdk.activity.ConnectionSpec;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        import java.nio.file.Files;
        import java.util.Optional;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.actions().register(ActionSpec.of("dev.example.link.show", "Show Link"), invoked -> {
                    try { Files.writeString(context.dataDirectory().resolve("shown"), invoked.window().id().toString()); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
                context.activities().connection(new ConnectionSpec("Link via db", "reconnecting",
                    ConnectionEvent.State.DEGRADED, Optional.of("dev.example.link.show")));
                context.activities().connection(new ConnectionSpec("Stray", "gone",
                    ConnectionEvent.State.DOWN, Optional.of("dev.example.other.show")));
            }
        }
        """;

    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();

    private PluginRuntime runtime(Path bundled, Path user, Path dev, boolean safeMode, List<String> reports) {
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(bundled, user, dev, safeMode,
            root.resolve("plugins.toml"), root.resolve("plugins.lock")),
            new ActivityNotifier(deck.companion(), () -> { }), new PluginRuntime.SettingsReports(d -> reports.add(d.file().getFileName() + ": " + d.message()), file -> reports.add("clear " + file.getFileName())),
            new dev.jasper.app.contributions.Contributions(),
            AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry())));
        return created.get();
    }

    private static void settle() { for (int i = 0; i < 3; i++) onEdt(() -> { }); }

    /** Waits for the bounded shutdown grace returned by {@link PluginRuntime#stop}. */
    private static void await(List<CompletableFuture<?>> pending) throws Exception {
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
    }

    /** Stops the plugins and waits until their jars are closed: Windows cannot delete a jar a classloader holds open. */
    private static void stopAndWait(PluginRuntime runtime) throws Exception {
        var workers = PluginShutdownTestSupport.capture(runtime);
        await(AppContractTest.onEdtValue(runtime::stop));
        workers.await();
    }

    private Path probe(Path parent, String id) throws Exception {
        Path directory = parent.resolve(id);
        PluginJars.build(directory, "probe.jar", PluginJars.descriptor(id, "1.0.0", "fix.probe.Main"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        return directory;
    }

    @Test void savedTerminalTypographySeedsPluginsAndUpdatesBeforeConfigurationNotifications() throws Exception {
        Path dev = root.resolve("dev/dev.example.font");
        String source = """
            package fix.font;
            import dev.jasper.sdk.plugin.Plugin;
            import dev.jasper.sdk.plugin.PluginContext;
            import dev.jasper.sdk.events.AppEvents;
            import java.nio.file.Files;
            public final class Main implements Plugin {
                private void note(PluginContext context, String file) {
                    var font = context.appearance().terminalTypography();
                    try { Files.writeString(context.dataDirectory().resolve(file),
                        font.family() + "|" + font.size() + "|" + font.fallback() + "|" + font.ligatures() + "|" + font.lineHeight()); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }
                public void start(PluginContext context) {
                    note(context, "initial");
                    context.appearance().onTerminalTypographyChanged(value -> note(context, "changed"));
                    context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> note(context, "reloaded"));
                }
            }
            """;
        PluginJars.build(dev, "font.jar", PluginJars.descriptor("dev.example.font", "1.0.0", "fix.font.Main"),
            Map.of("fix.font.Main", source), List.of());
        var runtime = runtime(null, root.resolve("user"), dev, false, new ArrayList<>());
        var data = root.resolve("user/dev.example.font/data");
        try {
            onEdt(() -> {
                runtime.terminalTypography(new dev.jasper.app.config.FontConfig("DialogInput", 13.5f,
                    List.of("Monospaced"), true, 1.25f));
                runtime.start(Map.of(), true);
            });
            assertThat(Files.readString(data.resolve("initial"))).isEqualTo("DialogInput|13.5|[Monospaced]|true|1.25");
            onEdt(() -> {
                runtime.terminalTypography(new dev.jasper.sdk.ui.TerminalTypography("Monospaced", 15.25, List.of(), false, 1.5));
                runtime.configurationChanged(Map.of());
            });
            settle();
            assertThat(Files.readString(data.resolve("changed"))).isEqualTo("Monospaced|15.25|[]|false|1.5");
            assertThat(Files.readString(data.resolve("reloaded"))).isEqualTo("Monospaced|15.25|[]|false|1.5");
        } finally { stopAndWait(runtime); }
    }

    @Test void shutdownKeepsPluginClassesAvailableUntilWorkerCleanupFinishes() throws Exception {
        assertShutdownKeepsCleanupClasses(false);
    }

    @Test void shutdownKeepsPluginClassesAvailableForConnectionsAttachedAfterCancellation() throws Exception {
        assertShutdownKeepsCleanupClasses(true);
    }

    private void assertShutdownKeepsCleanupClasses(boolean lateConnection) throws Exception {
        Path bundled = root.resolve("bundled");
        PluginJars.build(bundled.resolve("dev.example.cleanup"), "cleanup.jar",
            PluginJars.descriptor("dev.example.cleanup", "1.0.0", "fix.cleanup.Main"), Map.of(
                "fix.cleanup.Main", """
                    package fix.cleanup;
                    import java.util.concurrent.*;
                    public final class Main implements dev.jasper.sdk.plugin.Plugin {
                        public static final CountDownLatch RUNNING = new CountDownLatch(1);
                        public static final CountDownLatch RELEASE = new CountDownLatch(1);
                        public static final CompletableFuture<String> CLOSED = new CompletableFuture<>();
                        public static final CountDownLatch CLEANING = new CountDownLatch(1);
                        public static final CountDownLatch CLOSE_RELEASE = new CountDownLatch(1);
                        public static Runnable FINISH;
                        public static final Runnable CLOSE = () -> {
                            CLEANING.countDown();
                            try { CLOSE_RELEASE.await(); CLOSED.complete(Late.close()); }
                            catch (Throwable failure) { CLOSED.completeExceptionally(failure); }
                        };
                        public void start(dev.jasper.sdk.plugin.PluginContext context) {
                            context.background().execute(() -> {
                                RUNNING.countDown();
                                try { new CountDownLatch(1).await(); }
                                catch (InterruptedException stopping) {
                                    try { RELEASE.await(); if (FINISH != null) FINISH.run(); else CLOSED.complete(Late.close()); }
                                    catch (Throwable failure) { CLOSED.completeExceptionally(failure); }
                                }
                            });
                        }
                    }
                    """,
                "fix.cleanup.Late", """
                    package fix.cleanup;
                    public final class Late { public static String close() { return "closed"; } }
                    """), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("user"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        var field = PluginRuntime.class.getDeclaredField("loaders");
        field.setAccessible(true);
        var loader = (ClassLoader) ((List<?>) field.get(runtime)).getFirst();
        Class<?> main = loader.loadClass("fix.cleanup.Main");
        var running = (java.util.concurrent.CountDownLatch) main.getField("RUNNING").get(null);
        var release = (java.util.concurrent.CountDownLatch) main.getField("RELEASE").get(null);
        var closed = (CompletableFuture<?>) main.getField("CLOSED").get(null);
        var cleaning = (java.util.concurrent.CountDownLatch) main.getField("CLEANING").get(null);
        var closeRelease = (java.util.concurrent.CountDownLatch) main.getField("CLOSE_RELEASE").get(null);
        if (lateConnection) {
            var hostField = PluginRuntime.class.getDeclaredField("host");
            hostField.setAccessible(true);
            var host = (PluginHost) hostField.get(runtime);
            var sessions = new HostedSessions("dev.example.cleanup", host.containment, host.cleanup,
                id -> null, name -> null);
            var pending = new AtomicReference<dev.jasper.sdk.terminal.PendingSession>();
            var request = sessions.request(dev.jasper.sdk.terminal.SessionSpec.of("late", pending::set));
            var attempt = new dev.jasper.app.terminals.SessionAttempt(UUID.randomUUID(), 80, 24,
                request.cleanup(), Runnable::run);
            onEdt(() -> request.connector().accept(attempt));
            attempt.cancel();
            var close = (Runnable) main.getField("CLOSE").get(null);
            main.getField("FINISH").set(null, (Runnable) () -> pending.get().attach(
                new dev.jasper.sdk.terminal.TerminalConnection(new java.io.ByteArrayInputStream(new byte[0]),
                    new java.io.ByteArrayOutputStream(), (columns, rows) -> { }, new CompletableFuture<>(), close)));
        }
        var workers = PluginShutdownTestSupport.capture(runtime);
        try {
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            await(AppContractTest.onEdtValue(runtime::stop));
            assertThat(closed).isNotDone();
            release.countDown();
            if (lateConnection) {
                assertThat(cleaning.await(5, TimeUnit.SECONDS)).isTrue();
                for (var executor : workers.executors()) assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> workers.resourcesClosed().get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
                closeRelease.countDown();
            }
            assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo("closed");
        } finally {
            release.countDown();
            closeRelease.countDown();
            workers.await();
        }
        assertThat(loader.getResource("fix/cleanup/Late.class")).isNull();
    }

    @Test void manifestArtworkIsCachedBeforeSettingsAndClearedAtStop() throws Exception {
        Path bundled=root.resolve("art-bundled");
        Path jar=PluginJars.build(bundled.resolve("dev.example.art"),"art.jar",PluginJars.descriptor("dev.example.art","1.0.0","fix.probe.Main")+"icon = \"art.svg\"\nicon_hue = \"blue\"\n",Map.of("fix.probe.Main",FIXTURE),List.of());
        String svg="<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'><rect width='16' height='16' fill='red'/></svg>";
        PluginJars.addResource(jar,"art.svg",svg);
        Path bad=PluginJars.build(bundled.resolve("dev.example.badart"),"art.jar",PluginJars.descriptor("dev.example.badart","1.0.0","fix.probe.Main")+"icon = \"art.svg\"\nicon_hue = \"blue\"\n",Map.of("fix.probe.Main",FIXTURE),List.of());
        PluginJars.addResource(bad,"art.svg","<svg><image href='data:image/png;base64,AAAA'/></svg>");
        for (String format : List.of("svg", "png")) {
            Path empty = PluginJars.build(bundled.resolve("dev.example.empty." + format), "art.jar",
                PluginJars.descriptor("dev.example.empty." + format, "1.0.0", "fix.probe.Main")
                    + "icon = \"empty." + format + "\"\nicon_hue = \"purple\"\n", Map.of("fix.probe.Main", FIXTURE), List.of());
            PluginJars.addResource(empty, "empty." + format, "");
        }
        Path badPng = PluginJars.build(bundled.resolve("dev.example.badpng"), "art.jar",
            PluginJars.descriptor("dev.example.badpng", "1.0.0", "fix.probe.Main")
                + "icon = \"art.png\"\nicon_hue = \"green\"\n", Map.of("fix.probe.Main", FIXTURE), List.of());
        PluginJars.addResource(badPng, "art.png", "not a PNG");
        var runtime=runtime(bundled,root.resolve("art-user"),null,false,new ArrayList<>());
        onEdt(()->runtime.start(Map.of(),true));
        try {
            assertThat(runtime.pluginIcons().get("dev.example.art").data()).isEqualTo(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(runtime.pluginIcons()).containsKeys("dev.example.art","dev.example.badart");
            assertThat(runtime.pluginIcons()).doesNotContainKeys("dev.example.empty.svg", "dev.example.empty.png");
            var complete = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.snapshot(complete::complete));
            var outcome = complete.get(5, TimeUnit.SECONDS);
            assertThat(outcome.ok()).isTrue();
            assertThat(outcome.snapshot().rows()).hasSize(5);
            assertThat(outcome.snapshot().rows().stream().filter(row -> row.id().startsWith("dev.example.empty.")))
                .allSatisfy(row -> assertThat(row.icon()).isEmpty());
            assertThat(runtime.pluginIcons().get("dev.example.art").identityArtwork().tileHue())
                .isEqualTo(dev.jasper.ui.model.Nodes.Hue.BLUE);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.pluginIcons().get("dev.example.badpng").identityArtwork().artwork().prepare())
                .isInstanceOf(IllegalArgumentException.class);
            var retained=runtime.pluginIcons().get("dev.example.art").artwork();
            assertThat(runtime.pluginIcons().get("dev.example.art").artwork()).isSameAs(retained);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->runtime.pluginIcons().get("dev.example.badart").artwork().prepare()).isInstanceOf(IllegalArgumentException.class);
            var failed=new CompletableFuture<PluginRuntime.Outcome>();onEdt(()->runtime.setEnabled("absent.id",false,failed::complete));assertThat(failed.get(5,TimeUnit.SECONDS).ok()).isFalse();
            assertThat(runtime.pluginIcons().get("dev.example.art").artwork()).isSameAs(retained);
            byte[] changed=runtime.pluginIcons().get("dev.example.art").data();changed[0]=0;
            assertThat(runtime.pluginIcons().get("dev.example.art").data()[0]).isEqualTo((byte)'<');
            var late=new CompletableFuture<PluginRuntime.Outcome>();
            var stopping=new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(()->{runtime.snapshot(late::complete);stopping.set(runtime.stop());});
            assertThat(late.get(5,TimeUnit.SECONDS).ok()).isTrue();
            await(stopping.get());
        } finally {stopAndWait(runtime);}
        assertThat(runtime.pluginIcons()).isEmpty();
    }

    @Test void aPluginFailureIsReportedWithThePluginAndWhatItWasDoing() throws Exception {
        Path bundled = root.resolve("bundled");
        PluginJars.build(bundled.resolve("dev.example.boom"), "boom.jar", PluginJars.descriptor("dev.example.boom", "1.2.3", "fix.boom.Main"),
            Map.of("fix.boom.Main", BOOM), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        var failures = new CopyOnWriteArrayList<PluginRuntime.Failure>();
        runtime.reportFailuresTo(failures::add);
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.pluginId()).isEqualTo("dev.example.boom");
            assertThat(failure.operation()).isEqualTo("start");
            assertThat(failure.cause()).isInstanceOf(IllegalStateException.class).hasMessage("boom at start");
        });
        assertThat(runtime.pluginVersions()).containsEntry("dev.example.boom", "1.2.3");
        stopAndWait(runtime);
    }

    @Test void runsADevelopmentPluginBridgesItsActivityAndStopsIt() throws Exception {
        Path dev = probe(root.resolve("dev"), "dev.example.probe");
        PluginRuntime runtime = runtime(null, root.resolve("absent"), dev, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of("dev.example.probe", Map.<String, Object>of("greeting", "hello")), true));
        settle();
        Path data = root.resolve("absent/dev.example.probe/data");
        assertThat(data.resolve("started")).hasContent("hello");
        assertThat(runtime.statusLines()).singleElement().asString().contains("dev.example.probe", "DEV", "ACTIVE");
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.example.probe");
            assertThat(notice.title()).isEqualTo("Probe work");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.detail().get()).isEqualTo("50% · half way");
        }));

        assertThat(root.resolve("absent/dev.example.probe/dev.example.probe.toml")).content().contains("greeting = \"hello\"");
        Files.writeString(root.resolve("absent/dev.example.probe/dev.example.probe.toml"), "greeting = \"again\"\n");
        onEdt(() -> runtime.configurationChanged(Map.of()));
        onEdt(() -> runtime.themeChanged(false));
        settle();
        assertThat(data.resolve("changed")).hasContent("again");
        assertThat(data.resolve("reloaded")).hasContent("yes");
        assertThat(data.resolve("theme")).hasContent("LIGHT");

        stopAndWait(runtime);
        settle();
        assertThat(data.resolve("stopped")).hasContent("yes");
        onEdt(() -> assertThat(deck.notices()).as("the activity it left open ends with it, and so does its card").isEmpty());
    }

    @Test void connectionCardsFollowTheirPluginAndRunOnlyItsOwnAction() throws Exception {
        Path dev = root.resolve("dev/dev.example.link");
        PluginJars.build(dev, "link.jar", PluginJars.descriptor("dev.example.link", "1.0.0", "fix.link.Main"),
            Map.of("fix.link.Main", LINK), List.of());
        TerminalFixture fixture = AppContractTest.onEdtValue(TerminalFixture::new);
        UUID window = AppContractTest.onEdtValue(fixture::addWindow);
        onEdt(() -> fixture.activateWindow(window));
        var raised = new AtomicInteger();
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(null, root.resolve("absent"), dev, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock")),
            new ActivityNotifier(deck.companion(), raised::incrementAndGet), new PluginRuntime.SettingsReports(d -> { }, file -> { }),
            new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), fixture.registry)));
        PluginRuntime runtime = created.get();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        onEdt(() -> {
            assertThat(deck.notices()).hasSize(2)
                .allSatisfy(notice -> assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.CONNECTION));
            BuddyNotice link = notice("Link via db"), stray = notice("Stray");
            assertThat(link.state()).isEqualTo(BuddyNotice.State.DEGRADED);
            assertThat(link.detail().get()).isEqualTo("reconnecting");
            assertThat(stray.state()).isEqualTo(BuddyNotice.State.DOWN);
            link.activate().run();
            assertThat(raised).hasValue(1);
            stray.activate().run();
            assertThat(raised).as("another plugin's action only raises the application").hasValue(2);
        });
        assertThat(root.resolve("absent/dev.example.link/data/shown")).hasContent(window.toString());

        stopAndWait(runtime);
        settle();
        onEdt(() -> assertThat(deck.notices()).as("stopping closes the plugin's connections").isEmpty());
    }

    private BuddyNotice notice(String title) {
        return deck.notices().stream().filter(notice -> notice.title().equals(title)).findFirst().orElseThrow();
    }

    @Test void userPluginsWaitForConsentAndSafeModeSkipsThem() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.probe");
        PluginRuntime unreviewed = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> unreviewed.start(Map.of(), true));
        assertThat(unreviewed.statusLines()).singleElement().asString().contains("NEEDS_CONSENT");
        assertThat(root.resolve("user/dev.example.probe/data/started")).doesNotExist();

        Files.writeString(root.resolve("plugins.toml"), "version = 1\n[plugins.\"dev.example.probe\"]\nenabled = true\nconsented = []\n");
        PluginRuntime consented = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> consented.start(Map.of(), true));
        assertThat(consented.statusLines()).singleElement().asString().contains("ACTIVE");
        stopAndWait(consented);

        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.start(Map.of(), true));
        assertThat(safe.statusLines()).singleElement().asString().contains("DISABLED", "safe mode");
        // Workspace plan 6b's final review M3: a saved tab's placeholder says safe mode keeps it off, not the user.
        assertThat(safe.standing("dev.example.probe")).hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.SAFE_MODE));
    }

    /**
     * Workspace plan 6b's residual round, items 3 and 4: outside safe mode, a plugin the user turned off stands as turned off,
     * never as held back by safe mode, and one marked for removal as that, which a placeholder says without advising to turn
     * it on. In safe mode the plugin the user turned off is still held back by safe mode.
     */
    @Test void aPluginTheUserTurnedOffOrMarkedForRemovalStandsAsThatAndNotAsSafeMode() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.off");
        probe(user, "dev.example.gone");
        Files.writeString(root.resolve("plugins.toml"), "version = 1\n"
            + "[plugins.\"dev.example.off\"]\nenabled = false\nconsented = []\n"
            + "[plugins.\"dev.example.gone\"]\nenabled = true\nconsented = []\nremove = true\n");
        PluginRuntime normal = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> normal.start(Map.of(), true));
        assertThat(normal.standing("dev.example.off")).as("turned off by the user").hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.TURNED_OFF));
        assertThat(normal.standing("dev.example.gone")).as("marked for removal").hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.MARKED_FOR_REMOVAL));
        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.start(Map.of(), true));
        assertThat(safe.standing("dev.example.off")).as("safe mode holds back every user plugin").hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.SAFE_MODE));
    }

    private static String declaring(String id, String command, String summary) {
        return PluginJars.descriptor(id, "1.0.0", "fix.probe.Main") + "\n[[subcommands]]\nname = \"" + command + "\"\nsummary = \"" + summary + "\"\n";
    }

    private static String run(PluginRuntime runtime, String command, int expectedExit) throws Exception {
        var err = new StringBuffer();
        var invocation = new dev.jasper.app.commandline.Invocation(List.of(command), Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath(),
            java.util.Optional.empty(), text -> { }, err::append);
        assertThat(runtime.commands().dispatch(invocation).toCompletableFuture().get(5, TimeUnit.SECONDS)).as(command).isEqualTo(expectedExit);
        return err.toString();
    }

    @Test void aSubcommandOfAPluginThatDoesNotRunIsUnavailableAndNeverUnknown() throws Exception {
        Path bundled = root.resolve("bundled");
        Path user = root.resolve("user");
        PluginJars.build(bundled.resolve("dev.example.alpha"), "alpha.jar", declaring("dev.example.alpha", "alpha", "Runs"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        PluginJars.build(user.resolve("dev.example.beta"), "beta.jar", declaring("dev.example.beta", "beta", "Waits for consent"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        Path list = root.resolve("subcommands.tsv");
        PluginRuntime runtime = runtime(bundled, user, null, false, new ArrayList<>());
        onEdt(() -> runtime.subcommandList(list));
        assertThat(runtime.declaresSubcommand("alpha")).as("before the plugins start").isFalse();
        assertThat(runtime.runsSubcommand("alpha")).as("before the plugins start").isFalse();
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.statusLines())
            .anySatisfy(line -> assertThat(line).contains("dev.example.alpha", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.beta", "NEEDS_CONSENT"));
        assertThat(run(runtime, "beta", 1)).isEqualTo("jasper: beta is unavailable (the dev.example.beta plugin is off)\n");
        assertThat(run(runtime, "alpha", 1)).as("running, but it never called handle").isEqualTo("jasper: alpha is unavailable (the dev.example.alpha plugin is off)\n");
        assertThat(run(runtime, "gamma", 2)).isEqualTo("jasper: unknown command gamma; run jasper --help for the list\n");
        assertThat(runtime.declaresSubcommand("alpha")).isTrue();
        assertThat(runtime.declaresSubcommand("beta")).as("declared, though its plugin waits for consent").isTrue();
        assertThat(runtime.declaresSubcommand("gamma")).isFalse();
        // Workspace plan 6b's residual round: only a call whose plugin registered its handler runs, and restores windows first.
        assertThat(runtime.runsSubcommand("alpha")).as("running, but it never called handle").isFalse();
        assertThat(runtime.runsSubcommand("beta")).as("its plugin waits for consent").isFalse();
        assertThat(runtime.runsSubcommand("gamma")).as("declared by none").isFalse();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(list) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(dev.jasper.app.commandline.SubcommandList.read(list)).as("--help lists only what runs").containsExactly(
            new dev.jasper.app.commandline.SubcommandList.Entry("alpha", "Runs", "dev.example.alpha"));
        stopAndWait(runtime);
    }

    @Test void aSafeModeRunKeepsEverySubcommandNameOwnedAndNoneListed() throws Exception {
        Path user = root.resolve("user");
        PluginJars.build(user.resolve("dev.example.beta"), "beta.jar", declaring("dev.example.beta", "beta", "Waits for consent"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        Path list = root.resolve("subcommands.tsv");
        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.subcommandList(list));
        onEdt(() -> safe.start(Map.of(), true));
        assertThat(safe.statusLines()).singleElement().asString().contains("DISABLED", "safe mode");
        assertThat(run(safe, "beta", 1)).contains("beta is unavailable");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(list) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(list).exists();
        assertThat(dev.jasper.app.commandline.SubcommandList.read(list)).isEmpty();
        stopAndWait(safe);
    }

    @Test void aPluginThatCannotLoadIsReportedAndTheRestStillStart() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        PluginJars.build(bundled.resolve("dev.example.broken"), "broken.jar",
            PluginJars.descriptor("dev.example.broken", "1.0.0", "fix.broken.Missing"), Map.of(), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.statusLines()).hasSize(2)
            .anySatisfy(line -> assertThat(line).contains("dev.example.broken", "FAILED", "fix.broken.Missing"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        stopAndWait(runtime);
    }

    @Test void theBundledDirectorySitsBesideTheApplicationJarUnlessOverridden() throws Exception {
        Path jar = Files.createFile(root.resolve("jasper-app.jar"));
        assertThat(PluginRuntime.bundledDirectory(jar)).isEqualTo(root.resolve("plugins"));
        assertThat(PluginRuntime.bundledDirectory(root)).as("classes directory during development").isNull();
        assertThat(PluginRuntime.bundledDirectory(null)).isNull();
    }

    @Test void managementRunsOffTheEdtAnswersOnItAndNoticesAChange() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.probe");
        new PluginStateStore(root.resolve("plugins.toml"), root.resolve("plugins.lock"), Duration.ofSeconds(2))
            .transact(PluginStateStore.consenting("dev.example.probe", Set.of()));
        PluginRuntime runtime = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();

        var first = new CompletableFuture<PluginRuntime.Outcome>();
        var answeredOnEdt = new AtomicReference<Boolean>();
        onEdt(() -> runtime.snapshot(outcome -> { answeredOnEdt.set(SwingUtilities.isEventDispatchThread()); first.complete(outcome); }));
        PluginRuntime.Outcome listed = first.get(5, TimeUnit.SECONDS);
        assertThat(answeredOnEdt.get()).isTrue();
        assertThat(listed.ok()).isTrue();
        assertThat(listed.snapshot().restartNeeded()).isFalse();
        assertThat(listed.snapshot().rows()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("ACTIVE");
            assertThat(row.errors()).isZero();
        });

        var second = new CompletableFuture<PluginRuntime.Outcome>();
        onEdt(() -> runtime.setEnabled("dev.example.probe", false, second::complete));
        assertThat(second.get(5, TimeUnit.SECONDS).snapshot().restartNeeded()).isTrue();

        var third = new CompletableFuture<PluginRuntime.Outcome>();
        onEdt(() -> runtime.setEnabled("dev.example.absent", false, third::complete));
        PluginRuntime.Outcome failed = third.get(5, TimeUnit.SECONDS);
        assertThat(failed.ok()).isFalse();
        assertThat(failed.message()).contains("dev.example.absent");
        assertThat(failed.snapshot()).isNull();

        stopAndWait(runtime);
    }

        @Test void aChangedSettingsFileReachesThePluginWithoutAConfigReload() throws Exception {
            // The probe fixture above writes its declared greeting to data/started at start and, when it changes, to data/changed.
            Path dev = root.resolve("dev-plugin");
            PluginJars.build(dev, "probe.jar", PluginJars.descriptor("dev.example.probe", "1.0.0", "fix.probe.Main"), Map.of("fix.probe.Main", FIXTURE), List.of());
            List<String> reports = new ArrayList<>();
            PluginRuntime runtime = runtime(null, root.resolve("plugins"), dev, false, reports);
            onEdt(() -> runtime.start(Map.of(), true));
            settle();
            Path file = root.resolve("plugins/dev.example.probe/dev.example.probe.toml");
            Files.writeString(file, "greeting = \"later\"\n");
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
            onEdt(runtime::pollSettingsNow);
            settle();
            assertThat(root.resolve("plugins/dev.example.probe/data/changed")).hasContent("later");
            Files.writeString(file, "greeting = [\n");
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10000));
            onEdt(runtime::pollSettingsNow);
            settle();
            assertThat(reports).filteredOn(r -> !r.startsWith("clear ")).singleElement().asString().startsWith("dev.example.probe.toml: ");
            stopAndWait(runtime);
        }

    @Test void whatKeptPluginsFromRunningIsListedAsProblems() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        PluginJars.build(bundled.resolve("dev.example.future"), "future.jar",
            "id = \"dev.example.future\"\nname = \"Future\"\nversion = \"1.0.0\"\nentry = \"fix.future.Main\"\nsdk = \">=9.0.0\"\n",
            Map.of("fix.future.Main", PluginJars.emptyPlugin("fix.future", "Main")), List.of());
        PluginJars.build(bundled.resolve("dev.example.reserved"), "reserved.jar",
            PluginJars.descriptor("dev.example.reserved", "1.0.0", "dev.jasper.app.evil.Main"),
            Map.of("dev.jasper.app.evil.Main", PluginJars.emptyPlugin("dev.jasper.app.evil", "Main")), List.of());
        PluginJars.build(bundled.resolve("dev.example.nameless"), "nameless.jar", null,
            Map.of("fix.nameless.Main", PluginJars.emptyPlugin("fix.nameless", "Main")), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.problems()).extracting(PluginRuntime.Problem::kind, problem -> problem.name().orElse("-"))
            .containsExactlyInAnyOrder(
                tuple(PluginRuntime.Problem.Kind.UNREADABLE, "-"),
                tuple(PluginRuntime.Problem.Kind.NOT_STARTED, "Future"),
                tuple(PluginRuntime.Problem.Kind.NOT_LOADED, "dev.example.reserved"));
        assertThat(runtime.problems()).filteredOn(problem -> problem.kind() == PluginRuntime.Problem.Kind.NOT_STARTED)
            .singleElement().satisfies(problem -> assertThat(problem.detail()).contains("needs SDK"));
        assertThat(runtime.problems()).filteredOn(problem -> problem.kind() == PluginRuntime.Problem.Kind.NOT_LOADED)
            .singleElement().satisfies(problem -> {
                assertThat(problem.pluginId()).contains("dev.example.reserved");
                assertThat(problem.detail()).contains("reserved package dev.jasper.app.evil");
                assertThat(problem.cause()).as("no exception lies behind it").isEmpty();
            });
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        // Workspace tabs spec 13: only a plugin that runs rebuilds its saved tabs; the others' placeholders say why.
        assertThat(runtime.standing("dev.example.probe")).hasValueSatisfying(standing ->
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.RUNNING));
        assertThat(runtime.standing("dev.example.future")).as("it needs another SDK").hasValueSatisfying(standing -> {
            assertThat(standing.name()).isEqualTo("Future");
            assertThat(standing.kind()).isEqualTo(PluginRuntime.Standing.Kind.FAILED);
        });
        assertThat(runtime.standing("dev.example.absent")).as("not installed").isEmpty();
        stopAndWait(runtime);
    }

    @Test void aPluginWhoseNameShowsNothingIsAProblemAndJasperStillStarts() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        String invisible = new String(new int[] {0x200B, 0x202E}, 0, 2);
        PluginJars.build(bundled.resolve("dev.example.ghost"), "ghost.jar",
            "id = \"dev.example.ghost\"\nname = \"" + invisible + "\"\nversion = \"1.0.0\"\nentry = \"fix.ghost.Main\"\nsdk = \">=0.1\"\n",
            Map.of("fix.ghost.Main", PluginJars.emptyPlugin("fix.ghost", "Main")), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.kind()).isEqualTo(PluginRuntime.Problem.Kind.UNREADABLE);
            assertThat(problem.detail()).contains("dev.example.ghost", "name");
        });
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        stopAndWait(runtime);
    }

    static final String ENVIRONMENT = """
        package fix.environment;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.terminals().environment(jasper -> java.util.Map.of("EDITOR", "probe --wait", "VISUAL", "probe --wait"));
            }
        }
        """;

    static final String FAILING_ENVIRONMENT = """
        package fix.failing;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.terminals().environment(jasper -> java.util.Map.of("FAILED_PLUGIN", "1"));
                throw new IllegalStateException("fails after registering");
            }
        }
        """;

    /** Review Focus 4: a plugin that failed or stopped adds nothing to new shells. */
    @Test void onlyRunningPluginsAddToNewShells() throws Exception {
        Path bundled = root.resolve("bundled");
        PluginJars.build(bundled.resolve("dev.example.environment"), "environment.jar",
            PluginJars.descriptor("dev.example.environment", "1.0.0", "fix.environment.Main") + "capabilities = [\"terminal.environment\"]\n",
            Map.of("fix.environment.Main", ENVIRONMENT), List.of());
        PluginJars.build(bundled.resolve("dev.example.failing"), "failing.jar",
            PluginJars.descriptor("dev.example.failing", "1.0.0", "fix.failing.Main") + "capabilities = [\"terminal.environment\"]\n",
            Map.of("fix.failing.Main", FAILING_ENVIRONMENT), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of()))).as("before the plugins start").isEmpty();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(runtime.statusLines()).anySatisfy(line -> assertThat(line).contains("dev.example.failing", "FAILED"));
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of("EDITOR", "vim"))))
            .as("the failed plugin's contributor went with it").isEqualTo(Map.of("EDITOR", "probe --wait", "VISUAL", "probe --wait"));
        assertThatIllegalStateException().as("off the UI thread").isThrownBy(() -> runtime.shellVariables(Map.of()));
        stopAndWait(runtime);
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of()))).as("after the plugin stopped").isEmpty();
    }

    static final String EDITOR = """
        package fix.editor;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"), file.getFileName() + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    private static Path editorPlugin(Path directory, String id) throws Exception {
        return PluginJars.build(directory, id + ".jar",
            PluginJars.descriptor(id, "1.0.0", "fix.editor.Main") + "capabilities = [\"editor.provide\"]\n",
            Map.of("fix.editor.Main", EDITOR), List.of());
    }

    /** Review Focus 4: before the plugins start, and once the providing plugin stopped, files open with the desktop. */
    @Test void openAsksTheProvidedEditorAndOtherwiseTheDesktop() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.editor"), "dev.example.editor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        List<Path> desktop = new CopyOnWriteArrayList<>();
        Path before = root.resolve("before.txt"), during = root.resolve("during.txt"), after = root.resolve("after.txt");
        runtime.open(before, desktop::add);
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        runtime.open(during, desktop::add);
        Path edited = root.resolve("absent/dev.example.editor/data/edited");
        assertThat(Files.readAllLines(edited)).containsExactly("during.txt");
        onEdt(() -> assertThatIllegalStateException().as("never on the UI thread").isThrownBy(() -> runtime.open(during, desktop::add)));
        stopAndWait(runtime);
        runtime.open(after, desktop::add);
        assertThat(desktop).containsExactly(before, after);
        assertThat(Files.readAllLines(edited)).as("a stopped plugin's editor is not asked").containsExactly("during.txt");
    }

    /** Final review M2: quitting interrupts the worker opening a file; the desktop then does not open it either. */
    @Test void anInterruptedOpenSkipsTheDesktopAndKeepsTheInterrupt() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.editor"), "dev.example.editor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        List<Path> desktop = new CopyOnWriteArrayList<>();
        Thread.currentThread().interrupt();
        try {
            runtime.open(root.resolve("quitting.txt"), desktop::add);
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(desktop).as("the desktop does not open it").isEmpty();
        stopAndWait(runtime);
    }

    /** Review Focus 3: Open in Editor creates a missing config.toml from its template, then the provided editor opens it. */
    @Test void aMissingConfigTomlIsCreatedFromTheTemplateAndThenTheProvidedEditorOpensIt() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.editor"), "dev.example.editor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        Path config = root.resolve("home").resolve("config.toml");
        List<Path> desktop = new CopyOnWriteArrayList<>();
        var service = new dev.jasper.app.config.ConfigService(config, false);
        try {
            assertThat(service.openSettings(file -> runtime.open(file, desktop::add)).get(10, TimeUnit.SECONDS)).isEqualTo(config);
        } finally {
            service.close();
        }
        assertThat(Files.readString(config)).isEqualTo(dev.jasper.app.config.ConfigTemplate.text(false));
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.editor/data/edited"))).containsExactly("config.toml");
        assertThat(desktop).isEmpty();
        stopAndWait(runtime);
    }

    @Test void aBundledPluginsEditorReplacesADevelopmentPluginsEditor() throws Exception {
        editorPlugin(root.resolve("bundled").resolve("dev.example.zeditor"), "dev.example.zeditor");
        Path dev = root.resolve("dev").resolve("dev.example.aeditor");
        editorPlugin(dev, "dev.example.aeditor");
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), dev, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(runtime.statusLines()).as("the development plugin registered first; the bundled one replaced it")
            .anySatisfy(line -> assertThat(line).contains("dev.example.aeditor", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.zeditor", "ACTIVE"));
        runtime.open(root.resolve("notes.txt"), path -> { throw new AssertionError("the bundled editor opens it"); });
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.zeditor/data/edited"))).containsExactly("notes.txt");
        assertThat(root.resolve("absent/dev.example.aeditor/data/edited")).doesNotExist();
        stopAndWait(runtime);
    }

    static final String CLIENT = """
        package fix.client;
        import java.nio.file.Files;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                try {
                    Files.writeString(context.dataDirectory().resolve("client"),
                        context.commandLine().client().map(Object::toString).orElse("none"));
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    @Test void pluginsReadTheClientGivenBeforeTheyStart() throws Exception {
        Path dev = root.resolve("dev").resolve("dev.example.client");
        PluginJars.build(dev, "client.jar", PluginJars.descriptor("dev.example.client", "1.0.0", "fix.client.Main"),
            Map.of("fix.client.Main", CLIENT), List.of());
        PluginRuntime runtime = runtime(null, root.resolve("absent"), dev, false, new ArrayList<>());
        Path client = root.resolve("build").resolve("cli").resolve("bin").resolve("jasper");
        onEdt(() -> runtime.commandClient(java.util.Optional.of(client)));
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        assertThat(root.resolve("absent/dev.example.client/data/client")).hasContent(client.toString());
        onEdt(() -> assertThatIllegalStateException().isThrownBy(() -> runtime.commandClient(java.util.Optional.empty()))
            .withMessage("Plugins have already started"));
        stopAndWait(runtime);
    }

    static final String KIT_PAGE = """
        package fix.page;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.settings().page(rows -> context.kit().create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Button("go",
                    dev.jasper.sdk.ui.ControlState.READY, "Go", "", dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY, "go", "")), event -> { }));
            }
        }
        """;

    /**
     * Enterprise presentation plan 5a Task 10: the kit given before start reaches a plugin's views through the host and
     * the plugin's context (spec 4.9), and the setter refuses once plugins have started.
     */
    @Test void pluginsViewsAreMadeWithThePresentationGivenBeforeTheyStart() throws Exception {
        Path devPlugins = root.resolve("dev").resolve("dev.example.page");
        PluginJars.build(devPlugins, "page.jar", PluginJars.descriptor("dev.example.page", "1.0.0", "fix.page.Main"),
            Map.of("fix.page.Main", KIT_PAGE), List.of());
        var base = dev.jasper.ui.kit.Kit.DEFAULT;
        var tall = new dev.jasper.ui.kit.Kit("tall-buttons", base.metrics().with(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT, 40),
            base.type(), base.faces(), base.controls(), base.colors(), base.uiKeys());
        PluginRuntime runtime = runtime(null, root.resolve("absent"), devPlugins, false, new ArrayList<>());
        var look = dev.jasper.app.presentation.Projections.standard(new dev.jasper.app.presentation.Projections.Host(dev.jasper.app.presentation.Projections.Platform.LINUX, false)).plugins();
        onEdt(() -> runtime.presentation(tall, look, dev.jasper.app.config.DefaultSource.CORE));
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        var context = new dev.jasper.app.settingsui.PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
            () -> { }, () -> null, dev.jasper.app.testsupport.KitTestSupport::context);
        try {
            onEdt(() -> {
                var page = runtime.settingsPages(context).getFirst();
                var pair = dev.jasper.app.appearance.ThemeTestSupport.pair(false);
                var reference = new HostedKit("reference", () -> true, () -> pair, null, new Containment(java.awt.EventQueue::isDispatchThread),
                    () -> false, action -> "", () -> base);
                var spec = new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Button("go", dev.jasper.sdk.ui.ControlState.READY, "Go", "",
                    dev.jasper.sdk.ui.Nodes.ButtonKind.SECONDARY, "go", ""));
                var view = reference.create(spec, event -> { });
                try {
                    var made = dev.jasper.app.testsupport.KitTestSupport.require(page.component(), "go", javax.swing.JComponent.class);
                    var standard = dev.jasper.app.testsupport.KitTestSupport.require(view.component(), "go", javax.swing.JComponent.class);
                    assertThat(made.getPreferredSize().height).as("the plugin's button measures with the presentation's kit")
                        .isGreaterThan(standard.getPreferredSize().height);
                } finally { view.close(); reference.close(); page.dispose(); }
                assertThatIllegalStateException().isThrownBy(() -> runtime.presentation(base, look, dev.jasper.app.config.DefaultSource.CORE)).withMessage("Plugins have already started");
            });
        } finally { stopAndWait(runtime); }
    }

    static final String LOOK_READER = """
        package fix.look;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                var size = context.settings().declare(dev.jasper.sdk.settings.SettingSpec.of("size",
                    dev.jasper.sdk.settings.SettingKind.integer(8, 48), 13).label("Size"));
                System.setProperty("jasper.test.look", context.appearance().presentation().classic() + " "
                    + context.appearance().presentation().toolWindows() + " " + size.get());
            }
        }
        """;

    /**
     * Plan 15a Task 2: a started plugin reads the presentation the runtime was given, and the look's defaults for its
     * settings; the setter refuses after start.
     */
    @Test void aStartedPluginReadsThePresentationTheRuntimeWasGiven() throws Exception {
        Path devPlugins = root.resolve("dev").resolve("dev.example.look");
        PluginJars.build(devPlugins, "look.jar", PluginJars.descriptor("dev.example.look", "1.0.0", "fix.look.Main"),
            Map.of("fix.look.Main", LOOK_READER), List.of());
        var enterprise = dev.jasper.app.presentation.Projections.of(dev.jasper.app.testsupport.EnterpriseLook.CHROME, true, true,
            "dev.jasper.enterprise", new dev.jasper.app.presentation.Projections.Host(dev.jasper.app.presentation.Projections.Platform.LINUX, false)).plugins();
        var defaults = new dev.jasper.app.config.DefaultSource(Map.of(), Map.of("dev.example.look", Map.of("size", 15L)));
        PluginRuntime runtime = runtime(null, root.resolve("absent"), devPlugins, false, new ArrayList<>());
        try {
            onEdt(() -> runtime.presentation(dev.jasper.ui.kit.Kit.DEFAULT, enterprise, defaults));
            onEdt(() -> runtime.start(Map.of(), true));
            settle();
            assertThat(System.getProperty("jasper.test.look")).as("the presentation and the look's default for the plugin's setting")
                .isEqualTo("true STRIPES 15");
            onEdt(() -> assertThatIllegalStateException()
                .isThrownBy(() -> runtime.presentation(dev.jasper.ui.kit.Kit.DEFAULT, enterprise, dev.jasper.app.config.DefaultSource.CORE))
                .withMessage("Plugins have already started"));
        } finally {
            System.clearProperty("jasper.test.look");
            stopAndWait(runtime);
        }
    }

    static final String LINE_EDITOR = """
        package fix.lines;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.platform().editor((file, line, window) -> {
                    String where = window.map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-");
                    try {
                        Files.writeString(context.dataDirectory().resolve("edited"),
                            file.getFileName() + "|" + (line.isPresent() ? String.valueOf(line.getAsInt()) : "-") + "|" + where + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return true;
                });
            }
        }
        """;

    /** A Cmd-click opens the file at its line in the clicked pane's window; Jasper's own opens keep the active window. */
    @Test void openWithALineAndAWindowReachesTheProvidedEditor() throws Exception {
        PluginJars.build(root.resolve("bundled").resolve("dev.example.lines"), "lines.jar",
            PluginJars.descriptor("dev.example.lines", "1.0.0", "fix.lines.Main") + "capabilities = [\"editor.provide\"]\n",
            Map.of("fix.lines.Main", LINE_EDITOR), List.of());
        TerminalFixture fixture = AppContractTest.onEdtValue(TerminalFixture::new);
        UUID active = AppContractTest.onEdtValue(fixture::addWindow);
        UUID clicked = AppContractTest.onEdtValue(fixture::addWindow);
        onEdt(() -> fixture.activateWindow(active));
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(root.resolve("bundled"), root.resolve("absent"), null, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock")),
            new ActivityNotifier(deck.companion(), () -> { }), new PluginRuntime.SettingsReports(d -> { }, file -> { }),
            new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), fixture.registry)));
        PluginRuntime runtime = created.get();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        List<Path> desktop = new CopyOnWriteArrayList<>();
        runtime.open(root.resolve("Foo.java"), OptionalInt.of(42), Optional.of(clicked), desktop::add);
        runtime.open(root.resolve("Foo.java"), OptionalInt.empty(), Optional.empty(), desktop::add);
        runtime.open(root.resolve("config.toml"), desktop::add);
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.lines/data/edited")))
            .containsExactly("Foo.java|42|" + clicked, "Foo.java|-|-", "config.toml|-|" + active);
        assertThat(desktop).isEmpty();
        onEdt(() -> assertThatIllegalStateException().as("never on the UI thread")
            .isThrownBy(() -> runtime.open(root.resolve("Foo.java"), OptionalInt.of(1), Optional.of(clicked), desktop::add)));
        stopAndWait(runtime);
    }

    /**
     * A web browser that appends {@code <address>|<origin>|<placement>|<target or ->} to {@code data/opened}, declines
     * {@code /declined} and throws for {@code /throws}.
     */
    static final String WEB_BROWSER = """
        package fix.web;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                context.platform().browser(link -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("opened"),
                            link.address() + "|" + link.origin() + "|" + link.placement() + "|" + link.target().orElse("-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    if (link.address().getPath().endsWith("/throws")) throw new IllegalStateException("the browser broke");
                    return !link.address().getPath().endsWith("/declined");
                });
            }
        }
        """;

    /**
     * Browser plan 3b (R2): a web address reaches the provided browser as a click, with its placement and target, or as
     * an external address; a local web file reaches it as a click on a {@code file:} link. The address falls to the
     * caller's desktop opener and the file to a false answer before the plugins start, with a browser that declines or
     * throws, and after it stopped. UI thread only.
     */
    @Test void webAddressesAndLocalWebFilesGoToTheProvidedBrowserAndOtherwiseBackToTheCaller() throws Exception {
        PluginJars.build(root.resolve("bundled").resolve("dev.example.web"), "web.jar",
            PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main") + "capabilities = [\"browser.provide\"]\n",
            Map.of("fix.web.Main", WEB_BROWSER), List.of());
        PluginRuntime runtime = runtime(root.resolve("bundled"), root.resolve("absent"), null, false, new ArrayList<>());
        List<String> desktop = new CopyOnWriteArrayList<>();
        Path page = root.resolve("page.html"), declined = root.resolve("declined"), thrown = root.resolve("throws");
        onEdt(() -> {
            runtime.openLink(java.net.URI.create("https://example.com/before"), PluginRuntime.Placement.SPLIT_RIGHT, Optional.of("t"),
                Optional.empty(), address -> desktop.add(address.toString()));
            runtime.openExternalLink(java.net.URI.create("https://example.com/before-external"), Optional.empty(),
                address -> desktop.add(address.toString()));
            assertThat(runtime.openLocalWebFile(page.toUri(), Optional.empty())).as("before the plugins start").isFalse();
        });
        assertThat(desktop).as("before the plugins start").containsExactly("https://example.com/before", "https://example.com/before-external");

        desktop.clear();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        onEdt(() -> {
            runtime.openLink(java.net.URI.create("https://example.com/down"), PluginRuntime.Placement.SPLIT_DOWN, Optional.of("docs"),
                Optional.empty(), address -> desktop.add(address.toString()));
            runtime.openLink(java.net.URI.create("https://example.com/tab"), PluginRuntime.Placement.TAB, Optional.empty(),
                Optional.empty(), address -> desktop.add(address.toString()));
            runtime.openExternalLink(java.net.URI.create("https://example.com/sign-in"), Optional.empty(),
                address -> desktop.add(address.toString()));
            assertThat(runtime.openLocalWebFile(page.toUri(), Optional.empty())).as("a browser took the file").isTrue();
            assertThat(desktop).as("the browser took each one").isEmpty();
            runtime.openLink(java.net.URI.create("https://example.com/declined"), PluginRuntime.Placement.TAB, Optional.empty(),
                Optional.empty(), address -> desktop.add(address.toString()));
            runtime.openExternalLink(java.net.URI.create("https://example.com/throws"), Optional.empty(),
                address -> desktop.add(address.toString()));
            runtime.openLink(java.net.URI.create("https://example.com/long"), PluginRuntime.Placement.TAB, Optional.of("t".repeat(257)),
                Optional.empty(), address -> desktop.add(address.toString()));
            assertThat(runtime.openLocalWebFile(declined.toUri(), Optional.empty())).as("a browser that declines").isFalse();
            assertThat(runtime.openLocalWebFile(thrown.toUri(), Optional.empty())).as("a browser that throws").isFalse();
        });
        assertThat(desktop).as("declined, thrown and a target no WebLink holds go to the caller")
            .containsExactly("https://example.com/declined", "https://example.com/throws", "https://example.com/long");
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.web/data/opened"))).containsExactly(
            "https://example.com/down|CLICK|SPLIT_DOWN|docs", "https://example.com/tab|CLICK|TAB|-",
            "https://example.com/sign-in|EXTERNAL|TAB|-", page.toUri() + "|CLICK|TAB|-",
            "https://example.com/declined|CLICK|TAB|-", "https://example.com/throws|EXTERNAL|TAB|-",
            declined.toUri() + "|CLICK|TAB|-", thrown.toUri() + "|CLICK|TAB|-");

        var address = java.net.URI.create("https://example.com/x");
        assertThatIllegalStateException().as("never off the UI thread")
            .isThrownBy(() -> runtime.openLink(address, PluginRuntime.Placement.TAB, Optional.empty(), Optional.empty(), next -> { }));
        assertThatIllegalStateException().isThrownBy(() -> runtime.openExternalLink(address, Optional.empty(), next -> { }));
        assertThatIllegalStateException().isThrownBy(() -> runtime.openLocalWebFile(page.toUri(), Optional.empty()));

        stopAndWait(runtime);
        desktop.clear();
        onEdt(() -> {
            runtime.openExternalLink(address, Optional.empty(), next -> desktop.add(next.toString()));
            assertThat(runtime.openLocalWebFile(page.toUri(), Optional.empty())).as("after the plugin stopped").isFalse();
        });
        assertThat(desktop).containsExactly(address.toString());
        assertThat(Files.readAllLines(root.resolve("absent/dev.example.web/data/opened"))).as("a stopped plugin's browser is not asked").hasSize(8);
    }
}
