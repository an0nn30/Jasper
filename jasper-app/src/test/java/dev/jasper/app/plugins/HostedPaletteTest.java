package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteStep;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

class HostedPaletteTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste"), BOOM = new PaletteVerb("boom", "Boom");
    final List<String> notices = new CopyOnWriteArrayList<>();
    final List<Path> edited = new CopyOnWriteArrayList<>();
    Contributions contributions = onEdtValue(Contributions::new);
    TerminalFixture fixture = onEdtValue(TerminalFixture::new);
    PluginHost host;

    PluginHost host() throws Exception {
        Path data = Files.createTempDirectory("jasper-palette");
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, id -> Map.of(), id -> data.resolve(id + ".toml"), (key, message) -> { },
            Duration.ofMillis(200), contributions, () -> true, AppContractTest.headlessWindows(), fixture.registry,
            notices::add, edited::add)));
        return host;
    }

    /** Stops every plugin the way the contract harness does and waits for their background work. */
    void stopAll() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    @AfterEach void stop() { if (host != null) stopAll(); }

    /** A scope whose "boom" verb throws everywhere it can. */
    static final class Flaky implements PaletteScope {
        final List<String> log = new ArrayList<>();
        @Override public ScopeSpec spec() { return ScopeSpec.of("test.a.flaky", "Flaky", "Search", List.of(PASTE, BOOM)).withInAll(false); }
        @Override public PaletteResults search(String query, PaletteQuery context) {
            if (query.equals("boom")) throw new IllegalStateException("search failed");
            log.add("search:" + query + ":" + context.target().map(pane -> pane.id().toString()).orElse("-"));
            return new PaletteResults(List.of(PaletteRow.of("one", "One").withToken("t1"), PaletteRow.of("two", "Two")), Optional.of("Recent"), Optional.of("two"));
        }
        @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("available failed");
            return true;
        }
        @Override public Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("step failed");
            return row.id().equals("two") ? Optional.of(new PaletteStep("Two", List.of(new PaletteStep.Field("f", "F", "")),
                (values, done) -> done.accept(PaletteStep.Result.reopen("jasper.commands", Optional.of("new_tab"), Optional.empty())))) : Optional.empty();
        }
        @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
            if (verb.equals(BOOM)) throw new IllegalStateException("execute failed");
            log.add("execute:" + row.id() + ":" + row.token());
        }
        @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
    }

    @Test void aContributedScopeIsAdaptedContainedAndHandsThePluginItsOwnRowsBack() throws Exception {
        PluginHost host = host();
        var flaky = new Flaky();
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.a", "A", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> c -> { context.set(c); c.palette().register(flaky); })));
        UUID window = onEdtValue(fixture::addWindow), tab = onEdtValue(() -> fixture.addTab(window, "t"));
        UUID pane = onEdtValue(() -> fixture.addPane(tab, "zsh", Path.of("/src")));
        onEdt(() -> {
            var scope = contributions.scopes().getFirst();
            assertThat(scope.id()).isEqualTo("test.a.flaky");
            assertThat(scope.inAll()).isFalse();
            assertThat(scope.verbs()).extracting(dev.jasper.app.palette.PaletteVerb::id).containsExactly("paste", "boom");
            var target = new PaletteTarget(text -> { }, () -> { }, Optional::empty, () -> true, Optional.of(window), Optional.of(pane));
            var ctx = new PaletteContext(true, target, 5);
            var results = scope.search("on", ctx);
            assertThat(results.sectionLabel()).isEqualTo("Recent");
            assertThat(results.initialSelectionId()).isEqualTo("two");
            assertThat(results.rows()).extracting(dev.jasper.app.palette.PaletteRow::id).containsExactly("one", "two");
            assertThat(results.rows().getFirst().token()).isInstanceOf(PaletteRow.class);
            assertThat(flaky.log).containsExactly("search:on:" + pane);
            assertThat(scope.search("boom", ctx).rows()).as("a failing search is contained").isEmpty();
            var one = results.rows().getFirst(); var two = results.rows().get(1);
            var paste = scope.verbs().getFirst(); var boom = scope.verbs().get(1);
            assertThat(scope.available(one, paste, ctx)).isTrue();
            assertThat(scope.available(one, boom, ctx)).as("contained").isFalse();
            assertThat(scope.step(one, paste, ctx)).isNull();
            assertThat(scope.step(one, boom, ctx)).as("contained").isNull();
            var step = scope.step(two, paste, ctx);
            assertThat(step.title()).isEqualTo("Two");
            var result = new AtomicReference<dev.jasper.app.palette.PaletteStep.Result>();
            step.complete().accept(Map.of("f", "v"), result::set);
            assertThat(result.get().reopenScopeId()).isEqualTo("jasper.commands");
            assertThat(result.get().reopenRowId()).isEqualTo("new_tab");
            scope.execute(one, paste, ctx);
            scope.execute(one, boom, ctx);
            assertThat(flaky.log).contains("execute:one:t1");
            assertThat(scope.search("x", new PaletteContext(true, PaletteTarget.none(), 5)).rows()).as("no window, no rows").isEmpty();
            context.get().palette().open(context.get().terminals().window(window).orElseThrow(), "test.a.flaky", Optional.of("q"), Optional.empty());
        });
        List<Contributions.PaletteRequest> requests = new ArrayList<>();
        onEdt(() -> contributions.onPaletteRequest(requests::add));
        onEdt(() -> context.get().palette().open(context.get().terminals().window(window).orElseThrow(), "test.a.flaky", Optional.of("q"), Optional.of("one")));
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.windowId()).isEqualTo(window);
            assertThat(request.query()).contains("q");
            assertThat(request.rowId()).contains("one");
        });
        stopAll();
        assertThat(onEdtValue(contributions::scopes)).as("teardown removes the scope").isEmpty();
    }

    @Test void registrationIsGatedAndNamespacedAndNoticesAndTheEditorReachTheApplication() throws Exception {
        PluginHost host = host();
        var failures = new AtomicReference<Throwable>();
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.bare", "Bare", "1.0.0", Set.of()), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> c -> {
                context.set(c);
                try { c.palette().register(new Flaky()); } catch (RuntimeException failure) { failures.set(failure); }
            })));
        assertThat(failures.get()).isInstanceOf(MissingCapabilityException.class);
        var other = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo("test.b", "B", "1.0.0", Set.of(Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(),
            Set.of(getClass().getPackageName()), getClass().getClassLoader(), () -> other::set)));
        onEdt(() -> assertThatIllegalArgumentException().as("test.a.flaky is not in test.b's namespace")
            .isThrownBy(() -> other.get().palette().register(new Flaky())));
        onEdt(() -> other.get().actions().register(ActionSpec.of("test.b.open", "Open"), invoked -> { }));
        onEdt(() -> assertThatIllegalArgumentException().as("a shortcut action must be the plugin's own").isThrownBy(() -> other.get().palette().register(new PaletteScope() {
            @Override public ScopeSpec spec() { return ScopeSpec.of("test.b.s", "S", "Search", List.of(PASTE)).withShortcutActionId("test.a.open"); }
            @Override public PaletteResults search(String query, PaletteQuery context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) { }
            @Override public dev.jasper.sdk.Subscription onChanged(Runnable listener) { return () -> { }; }
        })));
        onEdt(() -> { other.get().notices().error("Broken"); other.get().platform().openInEditor(Path.of("/tmp/s.toml")); });
        for (int i = 0; i < 50 && edited.isEmpty(); i++) Thread.sleep(20);
        onEdt(() -> { });
        assertThat(notices).containsExactly("B: Broken");
        assertThat(edited).containsExactly(Path.of("/tmp/s.toml"));
    }
}
