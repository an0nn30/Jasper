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

    private Path probe(Path parent, String id) throws Exception {
        Path directory = parent.resolve(id);
        PluginJars.build(directory, "probe.jar", PluginJars.descriptor(id, "1.0.0", "fix.probe.Main"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        return directory;
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
            onEdt(()->{runtime.snapshot(late::complete);runtime.stop();});
            assertThat(late.get(5,TimeUnit.SECONDS).ok()).isTrue();
        } finally {onEdt(runtime::stop);}
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
        onEdt(runtime::stop);
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

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
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

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
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
        onEdt(consented::stop);

        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.start(Map.of(), true));
        assertThat(safe.statusLines()).singleElement().asString().contains("DISABLED", "safe mode");
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
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.statusLines())
            .anySatisfy(line -> assertThat(line).contains("dev.example.alpha", "ACTIVE"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.beta", "NEEDS_CONSENT"));
        assertThat(run(runtime, "beta", 1)).isEqualTo("jasper: beta is unavailable (the dev.example.beta plugin is off)\n");
        assertThat(run(runtime, "alpha", 1)).as("running, but it never called handle").isEqualTo("jasper: alpha is unavailable (the dev.example.alpha plugin is off)\n");
        assertThat(run(runtime, "gamma", 2)).isEqualTo("jasper: unknown command gamma; run jasper --help for the list\n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(list) && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(dev.jasper.app.commandline.SubcommandList.read(list)).as("--help lists only what runs").containsExactly(
            new dev.jasper.app.commandline.SubcommandList.Entry("alpha", "Runs", "dev.example.alpha"));
        onEdt(runtime::stop);
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
        onEdt(safe::stop);
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
        onEdt(runtime::stop);
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

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
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
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
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
        onEdt(runtime::stop);
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
        onEdt(runtime::stop);
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
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        assertThat(AppContractTest.onEdtValue(() -> runtime.shellVariables(Map.of()))).as("after the plugin stopped").isEmpty();
    }
}
