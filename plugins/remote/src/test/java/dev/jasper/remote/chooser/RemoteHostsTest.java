package dev.jasper.remote.chooser;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.files.FileLocation;
import dev.jasper.sdk.files.FileRoot;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteHostsTest {
    final RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", 22, "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
    final ConnectionIdentity identity = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(prod, "deploy")));
    final Set<UUID> connected = new HashSet<>();
    final List<Runnable> hostListeners = new ArrayList<>();
    final List<Runnable> connectionListeners = new ArrayList<>();
    final Map<UUID, RemoteHosts.Located> panes = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    CompletableFuture<ConnectionIdentity> resolving = new CompletableFuture<>();
    CompletableFuture<FileEndpoint> opening = new CompletableFuture<>();
    final WindowHandle window = proxy(WindowHandle.class, Map.of("isOpen", true));

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Map<String, Object> answers) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (self, method, args) -> answers.get(method.getName()));
    }

    RemoteHosts hosts() {
        return new RemoteHosts(() -> List.of(prod), connected::contains,
            listener -> { hostListeners.add(listener); return () -> hostListeners.remove(listener); },
            listener -> { connectionListeners.add(listener); return () -> connectionListeners.remove(listener); },
            id -> Optional.ofNullable(panes.get(id)),
            (id, owner) -> { calls.add("resolve " + id); return resolving; },
            (found, owner) -> { calls.add("open " + found.username()); return opening; },
            () -> Optional.of(window));
    }

    @Test void theSavedHostsAreRootsWithTheirConnectedState() {
        var hosts = hosts();
        assertThat(hosts.spec().id()).isEqualTo(RemoteHosts.ID);
        assertThat(hosts.roots()).containsExactly(new FileRoot(prod.id().toString(), "prod", prod.label(), false));
        connected.add(prod.id());
        assertThat(hosts.roots().getFirst().connected()).isTrue();
        var subscription = hosts.onChanged(() -> { });
        assertThat(hostListeners).hasSize(1);
        assertThat(connectionListeners).hasSize(1);
        subscription.close();
        assertThat(hostListeners).isEmpty();
        assertThat(connectionListeners).isEmpty();
    }

    @Test void anSshPaneIsLocatedOnItsHostAtTheShellsFolderOrItsHome() {
        var hosts = hosts();
        UUID pane = UUID.randomUUID();
        PaneHandle handle = proxy(PaneHandle.class, Map.of("id", pane));
        assertThat(hosts.locate(handle)).isEmpty();
        panes.put(pane, new RemoteHosts.Located(identity, "/srv"));
        assertThat(hosts.locate(handle)).contains(new FileLocation.Sourced(RemoteHosts.ID, prod.id().toString(), "/srv"));
        panes.put(pane, new RemoteHosts.Located(identity, ""));
        assertThat(hosts.locate(handle)).contains(new FileLocation.Sourced(RemoteHosts.ID, prod.id().toString(), FileLocation.Sourced.HOME));
        assertThat(hosts.identity(prod.id().toString())).as("the pane's logins are remembered").contains(identity);
    }

    @Test void connectingResolvesTheLoginsOnceAndOpensAnEndpoint() throws Exception {
        var hosts = hosts();
        var first = hosts.connect(prod.id().toString(), window).toCompletableFuture();
        assertThat(calls).containsExactly("resolve " + prod.id());
        resolving.complete(identity);
        assertThat(calls).containsExactly("resolve " + prod.id(), "open deploy");
        opening.complete(new LocalEndpoint());
        assertThat(first).isCompleted();
        first.join().close();
        assertThat(hosts.identity(prod.id().toString())).contains(identity);
        opening = new CompletableFuture<>();
        hosts.connect(prod.id().toString(), window);
        assertThat(calls).as("the remembered logins are reused").containsExactly("resolve " + prod.id(), "open deploy", "open deploy");
    }

    @Test void cancellingAbandonsTheStepInProgressAndFailuresFailTheConnection() {
        var hosts = hosts();
        hosts.connect(prod.id().toString(), window).toCompletableFuture().cancel(true);
        assertThat(resolving).isCancelled();
        resolving = new CompletableFuture<>();
        var failed = hosts.connect(prod.id().toString(), window).toCompletableFuture();
        resolving.completeExceptionally(new IOException("denied"));
        assertThat(failed).isCompletedExceptionally();
        assertThat(hosts.connect("not-a-host", window).toCompletableFuture()).isCompletedExceptionally();
    }
}
