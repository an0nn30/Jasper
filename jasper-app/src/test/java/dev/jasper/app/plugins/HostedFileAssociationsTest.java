package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.platform.FileAssociationsBridge;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.FileAssociations;
import dev.jasper.sdk.ui.FileAssociations.Claimability;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Outcome;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.TypeResult;
import dev.jasper.sdk.ui.FileAssociations.TypeState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The plugin's file associations: the platform layer's values one to one, completions only while the plugin runs. */
class HostedFileAssociationsTest {
    private static final dev.jasper.app.platform.FileAssociations.Handler APP_XCODE =
        new dev.jasper.app.platform.FileAssociations.Handler.Other("com.apple.dt.Xcode", "Xcode");

    private final AtomicBoolean open = new AtomicBoolean(true);
    private final List<Runnable> handlers = new CopyOnWriteArrayList<>();
    private FileAssociationsBridge bridge;

    private HostedFileAssociations hosted(Map<String, List<String>> types,
                                          Map<String, dev.jasper.app.platform.FileAssociations.Handler> handlers) {
        bridge = FileAssociationsBridge.open(types, handlers, Optional.empty());
        return new HostedFileAssociations("test.a", bridge::facade, open::get, SwingUtilities::isEventDispatchThread, handler -> {
            this.handlers.add(handler);
            return () -> this.handlers.remove(handler);
        });
    }

    @AfterEach void close() {
        if (bridge != null) bridge.close();
    }

    private static void onEdt(Runnable action) {
        AppContractTest.onEdt(action);
    }

    private static <T> T awaited(CompletableFuture<T> future) throws Exception {
        onEdt(() -> { });
        return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private <T> T onUi(java.util.function.Supplier<CompletableFuture<T>> call) throws Exception {
        var future = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<T>>();
        onEdt(() -> future.set(call.get()));
        return awaited(future.get());
    }

    @Test void statusMapsEveryHandlerAndLeavesTheBundleIdBehind() throws Exception {
        var associations = hosted(Map.of(".java", List.of("t.java", "t.none", "t.jasper", "t.other")), Map.of(
            "t.java", APP_XCODE, "t.jasper", new dev.jasper.app.platform.FileAssociations.Handler.Jasper()));
        var status = onUi(() -> associations.status(List.of(".java", ".nothing")));
        assertThat(status).hasSize(2);
        assertThat(status.get(0).extension()).isEqualTo(".java");
        assertThat(status.get(0).types()).containsExactly(
            new TypeState("t.java", new Handler.Other("Xcode"), Optional.empty()),
            new TypeState("t.none", new Handler.None(), Optional.empty()),
            new TypeState("t.jasper", new Handler.Jasper(), Optional.empty()),
            new TypeState("t.other", new Handler.None(), Optional.empty()));
        assertThat(status.get(1).types()).as("no system type").isEmpty();
    }

    @Test void aClaimAndARestoreMapTheirResultsAndThePreviousHandler() throws Exception {
        var associations = hosted(Map.of(".java", List.of("t.java"), ".kt", List.of("t.kt")), Map.of("t.java", APP_XCODE));
        var claimed = onUi(() -> associations.claim(List.of(".java", ".kt", ".html")));
        assertThat(claimed).containsExactlyInAnyOrder(
            new TypeResult(".java", "t.java", new Outcome.Done()),
            new TypeResult(".kt", "t.kt", new Outcome.Done()),
            new TypeResult(".html", "", new Outcome.Refused(Refusal.BROWSER_OWNED)));
        var status = onUi(() -> associations.status(List.of(".java", ".kt")));
        assertThat(status.get(0).types()).containsExactly(
            new TypeState("t.java", new Handler.Jasper(), Optional.of(new Handler.Other("Xcode"))));
        assertThat(status.get(1).types()).containsExactly(
            new TypeState("t.kt", new Handler.Jasper(), Optional.of(new Handler.None())));
        var restored = onUi(() -> associations.restore(List.of(".java", ".kt")));
        assertThat(restored).extracting(TypeResult::outcome).containsOnly(new Outcome.Done());
        assertThat(onUi(() -> associations.forget(".java"))).isNull();
    }

    @Test void claimabilityMapsEveryAnswer() throws Exception {
        var associations = hosted(Map.of(".java", List.of("t.java", "t.j2")), Map.of());
        assertThat(onUi(() -> associations.claimability(".java")))
            .isEqualTo(new Claimability.Claimable(List.of("t.java", "t.j2"), false));
        assertThat(onUi(() -> associations.claimability(".nothing")))
            .isEqualTo(new Claimability.NotClaimable(Refusal.NO_SYSTEM_TYPE));
        assertThat(onUi(() -> associations.claimability(".html")))
            .isEqualTo(new Claimability.NotClaimable(Refusal.BROWSER_OWNED));
        assertThat(onUi(() -> associations.claimability("../x")))
            .isEqualTo(new Claimability.NotClaimable(Refusal.INVALID));
    }

    @Test void unavailableIsTheFacadesReasonByName() {
        var associations = hosted(Map.of(), Map.of());
        onEdt(() -> assertThat(associations.unavailable()).isEmpty());
        for (var reason : dev.jasper.app.platform.FileAssociations.Unavailable.values()) {
            bridge.unavailable(Optional.of(reason));
            onEdt(() -> assertThat(associations.unavailable().map(Enum::name)).contains(reason.name()));
        }
    }

    @Test void everyOutcomeMapsByName() {
        assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.Done()))
            .isEqualTo(new Outcome.Done());
        assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.InSettings()))
            .isEqualTo(new Outcome.InSettings());
        assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.NoPrevious()))
            .isEqualTo(new Outcome.NoPrevious());
        assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.PreviousGone()))
            .isEqualTo(new Outcome.PreviousGone());
        assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.Failed("boom")))
            .isEqualTo(new Outcome.Failed("boom"));
        for (var refusal : dev.jasper.app.platform.FileAssociations.Refusal.values()) {
            assertThat(HostedFileAssociations.outcome(new dev.jasper.app.platform.FileAssociations.Outcome.Refused(refusal)))
                .isEqualTo(new Outcome.Refused(Refusal.valueOf(refusal.name())));
        }
        assertThat(HostedFileAssociations.handler(new dev.jasper.app.platform.FileAssociations.Handler.Unknown()))
            .isEqualTo(new Handler.Unknown());
        assertThat(HostedFileAssociations.handler(new dev.jasper.app.platform.FileAssociations.Handler.Jasper()))
            .isEqualTo(new Handler.Jasper());
    }

    @Test void aFacadeFutureThatFailsBecomesFailedNotAnException() throws Exception {
        var failing = new CompletableFuture<List<TypeResult>>();
        var mapped = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<List<TypeResult>>>();
        Function<String, List<TypeResult>> failure = detail -> List.of(new TypeResult(".java", "", new Outcome.Failed(detail)));
        onEdt(() -> mapped.set(HostedFileAssociations.adapt(failing, open::get, Function.identity(), failure)));
        failing.completeExceptionally(new IllegalStateException("kaput"));
        assertThat(awaited(mapped.get())).containsExactly(new TypeResult(".java", "", new Outcome.Failed("kaput")));
    }

    @Test void aCompletionAfterThePluginClosedRunsNothing() throws Exception {
        var associations = hosted(Map.of(".java", List.of("t.java")), Map.of());
        var future = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<List<TypeResult>>>();
        var ran = new AtomicBoolean();
        onEdt(() -> {
            var claim = associations.claim(List.of(".java"));
            future.set(claim);
            claim.thenRun(() -> ran.set(true));
            open.set(false);
        });
        onEdt(() -> { });
        onEdt(() -> { });
        assertThat(future.get()).isNotDone();
        assertThat(ran).isFalse();
    }

    @Test void changeHandlersRunOnlyWhileThePluginRuns() {
        var associations = hosted(Map.of(), Map.of());
        var heard = new CopyOnWriteArrayList<String>();
        var registration = new java.util.concurrent.atomic.AtomicReference<Subscription>();
        onEdt(() -> registration.set(associations.onChanged(() -> heard.add("changed"))));
        assertThat(handlers).hasSize(1);
        handlers.getFirst().run();
        assertThat(heard).containsExactly("changed");
        open.set(false);
        handlers.getFirst().run();
        assertThat(heard).as("after the plugin closed").containsExactly("changed");
    }

    @Test void everyCallOffTheUiThreadThrows() {
        var associations = hosted(Map.of(), Map.of());
        List<Runnable> calls = List.of(associations::unavailable, () -> associations.status(List.of()),
            () -> associations.claimability(".java"), () -> associations.claim(List.of()), () -> associations.restore(List.of()),
            () -> associations.forget(".java"), () -> associations.onChanged(() -> { }));
        for (Runnable call : calls) assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class);
    }

    @Test void aCallAfterThePluginClosedThrows() {
        var associations = hosted(Map.of(), Map.of());
        open.set(false);
        onEdt(() -> assertThatThrownBy(() -> associations.status(List.of())).isInstanceOf(IllegalStateException.class));
    }

    @Test void nullArgumentsAreRefused() {
        var associations = hosted(Map.of(), Map.of());
        onEdt(() -> {
            assertThatThrownBy(() -> associations.status(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> associations.claimability(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> associations.claim(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> associations.restore(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> associations.forget(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> associations.onChanged(null)).isInstanceOf(NullPointerException.class);
        });
    }
}
