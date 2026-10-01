package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.FakeVault;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.fileserver.runtime.ServerState.Phase.*;
import static org.assertj.core.api.Assertions.assertThat;

class ServerRegistryTest {
    @TempDir Path folder;
    final Deque<Runnable> background = new ArrayDeque<>();
    final List<FakeEngine> engines = new ArrayList<>();
    final List<ServerEvent> observed = new ArrayList<>();
    final List<UUID> activity = new ArrayList<>();
    final FakeVault vault = new FakeVault();
    String failure;
    ServerRegistry registry;
    int changes;

    final class FakeEngine implements ServerEngine {
        final ServerDefinition definition;
        final ServerEvents events;
        boolean started, stopped;
        int stopDelayMs;
        FakeEngine(ServerDefinition definition, ServerEvents events) { this.definition = definition; this.events = events; }
        @Override public void start() throws EngineFailure { if (failure != null) throw new EngineFailure(failure); started = true; }
        @Override public void stop() { stopped = true; if (stopDelayMs > 0) { try { Thread.sleep(stopDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } } }
        @Override public List<URI> addresses() { return List.of(URI.create("http://127.0.0.1:" + definition.port() + "/")); }
    }

    @BeforeEach void registry() {
        var logins = new LoginResolver(Optional.of(vault.api()), () -> "dustin", () -> "session-password");
        registry = new ServerRegistry((definition, share, login, events) -> {
            var engine = new FakeEngine(definition, events);
            engines.add(engine);
            return engine;
        }, logins, background::add, Runnable::run, new TestClock(), () -> 3, observed::add);
        registry.onChanged(() -> changes++);
        registry.onActivity(activity::add);
    }

    void run() { while (!background.isEmpty()) background.poll().run(); }

    ServerDefinition definition(String name, int port, Bind bind, Login login, boolean autostart) {
        var base = ServerDefinition.create(name, Protocol.HTTP, folder);
        return new ServerDefinition(base.id(), name, Protocol.HTTP, folder, bind, port, Optional.empty(), false, login, List.of(), false, false, autostart);
    }

    ServerState.Phase phase(UUID id) { return registry.server(id).orElseThrow().state().phase(); }

    @Test void startsAndStopsThroughEveryState() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.SESSION, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        assertThat(phase(docs.id())).isEqualTo(STARTING);
        run();
        var view = registry.server(docs.id()).orElseThrow();
        assertThat(view.state().phase()).isEqualTo(RUNNING);
        assertThat(view.addresses()).containsExactly(URI.create("http://127.0.0.1:8080/"));
        assertThat(view.user()).contains("dustin");
        assertThat(registry.password(docs.id())).contains("session-password");
        assertThat(registry.running()).isEqualTo(1);
        assertThat(registry.active()).isEqualTo(1);
        registry.stop(docs.id());
        assertThat(phase(docs.id())).isEqualTo(STOPPING);
        run();
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(registry.password(docs.id())).isEmpty();
        assertThat(registry.running()).isZero();
        assertThat(changes).isGreaterThanOrEqualTo(5);
    }

    @Test void aFailedStartSaysWhyAndCanBeRetried() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        failure = "Port 8080 is already in use on 127.0.0.1";
        registry.start(docs.id(), null);
        run();
        assertThat(registry.server(docs.id()).orElseThrow().state()).isEqualTo(ServerState.failed("Port 8080 is already in use on 127.0.0.1"));
        assertThat(registry.activity(docs.id())).extracting(ActivityLog.Entry::text).containsExactly("error: Port 8080 is already in use on 127.0.0.1");
        failure = null;
        registry.start(docs.id(), null);
        run();
        assertThat(phase(docs.id())).isEqualTo(RUNNING);
    }

    @Test void refusesAMissingFolderAndAPortClash() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var everywhere = definition("everywhere", 8080, Bind.ALL, Login.ANONYMOUS, false);
        var elsewhere = definition("elsewhere", 8080, new Bind.Interface("en0"), Login.ANONYMOUS, false);
        var gone = new ServerDefinition(UUID.randomUUID(), "gone", Protocol.HTTP, folder.resolve("missing"), Bind.LOOPBACK, 9090,
            Optional.empty(), false, Login.ANONYMOUS, List.of(), false, false, false);
        registry.setDefinitions(List.of(docs, everywhere, elsewhere, gone));
        registry.start(docs.id(), null);
        run();
        registry.start(everywhere.id(), null);
        assertThat(registry.server(everywhere.id()).orElseThrow().state().reason()).contains("Port 8080 is already used by ‘docs’");
        registry.start(elsewhere.id(), null);
        run();
        assertThat(phase(elsewhere.id())).as("loopback and an interface do not compete").isEqualTo(RUNNING);
        registry.start(gone.id(), null);
        assertThat(registry.server(gone.id()).orElseThrow().state().reason()).get().asString().startsWith("The folder ").endsWith(" does not exist");
    }

    @Test void cancellingAStartStopsTheEngineOnceBound() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        registry.cancelStart(docs.id());
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
        run();
        assertThat(engines.getFirst().started).isTrue();
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
    }

    @Test void temporaryServersVanishWhenStoppedAndSavingAdoptsThem() {
        var quick = definition("quick", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.startTemporary(quick, null);
        run();
        assertThat(registry.server(quick.id())).get().satisfies(view -> { assertThat(view.temporary()).isTrue(); assertThat(view.state().phase()).isEqualTo(RUNNING); });
        registry.setDefinitions(List.of(quick));
        assertThat(registry.server(quick.id())).get().satisfies(view -> {
            assertThat(view.temporary()).as("saved under the same id").isFalse();
            assertThat(view.state().phase()).isEqualTo(RUNNING);
            assertThat(view.restartNeeded()).isFalse();
        });
        var other = definition("other", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.startTemporary(other, null);
        run();
        registry.stop(other.id());
        run();
        assertThat(registry.server(other.id())).isEmpty();
    }

    @Test void editingARunningServerAsksForARestartAndDeletingItStopsIt() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        run();
        var moved = definition("docs", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        moved = new ServerDefinition(docs.id(), moved.name(), moved.protocol(), moved.folder(), moved.bind(), 8081, moved.passiveRange(),
            moved.writable(), moved.login(), moved.authorizedKeys(), false, false, false);
        registry.setDefinitions(List.of(moved));
        assertThat(registry.server(docs.id()).orElseThrow().restartNeeded()).isTrue();
        registry.setDefinitions(List.of());
        assertThat(registry.servers()).as("a deleted server leaves the list at once").isEmpty();
        run();
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(registry.server(docs.id())).isEmpty();
    }

    @Test void autostartWaitsForALockedVaultAndStartsOnUnlock() {
        UUID account = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        var vaulted = definition("vaulted", 8080, Bind.LOOPBACK, new Login.Vault(account), true);
        var plain = definition("plain", 8081, Bind.LOOPBACK, Login.ANONYMOUS, true);
        var manual = definition("manual", 8082, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(vaulted, plain, manual));
        registry.autostart();
        run();
        assertThat(phase(vaulted.id())).isEqualTo(WAITING_FOR_VAULT);
        assertThat(phase(plain.id())).isEqualTo(RUNNING);
        assertThat(phase(manual.id())).isEqualTo(STOPPED);
        vault.state = LockState.UNLOCKED;
        registry.vaultUnlocked();
        run();
        assertThat(phase(vaulted.id())).isEqualTo(RUNNING);
        assertThat(registry.server(vaulted.id()).orElseThrow().user()).contains("guest");
    }

    @Test void aDeletedVaultAccountFailsTheStart() {
        var vaulted = definition("vaulted", 8080, Bind.LOOPBACK, new Login.Vault(UUID.randomUUID()), false);
        registry.setDefinitions(List.of(vaulted));
        registry.start(vaulted.id(), null);
        run();
        assertThat(registry.server(vaulted.id()).orElseThrow().state().reason()).contains("Vault account no longer exists — edit the server");
    }

    @Test void recordsActivityAndPassesEventsOn() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        run();
        var events = engines.getFirst().events;
        events.connected("10.0.0.2");
        events.transfer("10.0.0.2", ServerEvent.Direction.DOWNLOAD, "/a", 3, () -> { }).finish(true);
        assertThat(registry.activity(docs.id())).extracting(ActivityLog.Entry::text).containsExactly("10.0.0.2 connected", "10.0.0.2 ↓ /a  0 B  0.0 s");
        assertThat(activity).containsExactly(docs.id(), docs.id());
        assertThat(observed).hasSize(3);
        registry.clearActivity(docs.id());
        assertThat(registry.activity(docs.id())).isEmpty();
    }

    @Test void stopAllAndShutdownStopEverything() {
        var a = definition("a", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var b = definition("b", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(a, b));
        registry.start(a.id(), null);
        registry.start(b.id(), null);
        run();
        var stopped = registry.stopAll();
        run();
        assertThat(stopped).isCompleted();
        assertThat(registry.running()).isZero();
        registry.start(a.id(), null);
        run();
        registry.start(b.id(), null);
        run();
        registry.shutdown();
        assertThat(engines.getLast().stopped).as("synchronously").isTrue();
        assertThat(engines.subList(2, engines.size())).allMatch(e -> e.stopped);
        assertThat(registry.servers()).isEmpty();
    }

    @Test void shutdownStopsEnginesInParallel() {
        var a = definition("a", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var b = definition("b", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(a, b));
        registry.start(a.id(), null);
        registry.start(b.id(), null);
        run();
        engines.forEach(engine -> engine.stopDelayMs = 400);
        long began = System.nanoTime();
        registry.shutdown();
        long tookMs = (System.nanoTime() - began) / 1_000_000;
        assertThat(engines).allMatch(engine -> engine.stopped);
        assertThat(tookMs).as("two 400 ms stops overlap").isLessThan(700);
    }

    @Test void aRunningServerKeepsHoldingThePortItActuallyUses() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        run();
        var moved = new ServerDefinition(docs.id(), "docs", Protocol.HTTP, folder, Bind.LOOPBACK, 8081, Optional.empty(), false,
            Login.ANONYMOUS, List.of(), false, false, false);
        var old = definition("old", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var fresh = definition("fresh", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(moved, old, fresh));
        registry.start(old.id(), null);
        assertThat(registry.server(old.id()).orElseThrow().state().reason()).contains("Port 8080 is already used by ‘docs’");
        registry.start(fresh.id(), null);
        run();
        assertThat(phase(fresh.id())).isEqualTo(RUNNING);
    }

    @Test void aStoppingServerStillHoldsItsPort() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var other = definition("other", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs, other));
        registry.start(docs.id(), null);
        run();
        registry.stop(docs.id());
        registry.start(other.id(), null);
        assertThat(phase(other.id())).isEqualTo(FAILED);
    }

    @Test void anErrorWhileBindingFailsTheStartInsteadOfHangingIt() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var broken = new ServerRegistry((definition, share, login, events) -> { throw new AssertionError("boom"); },
            new LoginResolver(Optional.of(vault.api()), () -> "dustin", () -> "pw"), background::add, Runnable::run, new TestClock(), () -> 3, observed::add);
        broken.setDefinitions(List.of(docs));
        var done = broken.start(docs.id(), null);
        run();
        assertThat(done).isCompleted();
        assertThat(broken.server(docs.id()).orElseThrow().state()).isEqualTo(ServerState.failed("boom"));
    }

    @Test void anEngineWhoseAddressesThrowFailsTheStart() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var stopped = new boolean[1];
        var odd = new ServerRegistry((definition, share, login, events) -> new ServerEngine() {
            @Override public void start() { }
            @Override public void stop() { stopped[0] = true; }
            @Override public List<URI> addresses() { throw new IllegalStateException("no network"); }
        }, new LoginResolver(Optional.of(vault.api()), () -> "dustin", () -> "pw"), background::add, Runnable::run, new TestClock(), () -> 3, observed::add);
        odd.setDefinitions(List.of(docs));
        var done = odd.start(docs.id(), null);
        run();
        assertThat(done).isCompleted();
        assertThat(odd.server(docs.id()).orElseThrow().state()).isEqualTo(ServerState.failed("no network"));
        assertThat(stopped[0]).isTrue();
    }

    @Test void startingATemporaryServerWithATakenIdChangesNothing() {
        var quick = definition("quick", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.startTemporary(quick, null);
        run();
        var again = registry.startTemporary(quick, null);
        run();
        assertThat(again).isCompleted();
        assertThat(engines).hasSize(1);
        assertThat(phase(quick.id())).isEqualTo(RUNNING);
    }
}
