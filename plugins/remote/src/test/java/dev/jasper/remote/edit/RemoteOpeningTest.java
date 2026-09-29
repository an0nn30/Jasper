package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.PaneHandle;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteOpeningTest {
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final EditChannels channels = new EditChannels((identity, owner) -> CompletableFuture.completedFuture(new LocalEndpoint()), identity -> true, background, ui);
    final List<Predicate<OpenRequest>> openers = new CopyOnWriteArrayList<>();
    final Editor editor = new Editor() {
        @Override public void open(Path file) { throw new AssertionError("not used"); }
        @Override public void open(EditorDocument document) { throw new AssertionError("not used"); }
        @Override public Subscription opener(Predicate<OpenRequest> handler) { openers.add(handler); return () -> openers.remove(handler); }
    };
    final Map<UUID, RemoteHost> saved = new ConcurrentHashMap<>();
    final RemoteEditing editing = new RemoteEditing(editor, channels, Optional::empty, notice -> { }, id -> Optional.ofNullable(saved.get(id)));
    final Map<UUID, RemoteOpening.Target> sshPanes = new ConcurrentHashMap<>();
    final List<String> shown = new CopyOnWriteArrayList<>();
    final RemoteOpening opening = new RemoteOpening(editor, editing, pane -> Optional.ofNullable(sshPanes.get(pane.id())),
        (window, target) -> shown.add(target.identity().host().name() + ":" + target.directory()));
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");
    final TestWindow window = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    static PaneHandle pane(UUID id) {
        return (PaneHandle) Proxy.newProxyInstance(PaneHandle.class.getClassLoader(), new Class<?>[] { PaneHandle.class }, (proxy, method, args) -> switch (method.getName()) {
            case "id" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "pane " + id;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test void anSshPaneOpensThePickerAtItsFolder() {
        UUID id = UUID.randomUUID();
        sshPanes.put(id, new RemoteOpening.Target(prod, "/srv/app"));
        assertThat(opening.handle(new OpenRequest(window, Optional.of(pane(id)), Optional.empty()))).isTrue();
        assertThat(shown).containsExactly("prod:/srv/app");
    }

    @Test void aRemoteFileOpensThePickerAtItsFolder() {
        var host = prod.hops().getFirst().host();
        saved.put(host.id(), host);
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of(RemoteDocument.key(prod, "/etc/nginx/nginx.conf"))))).isTrue();
        assertThat(shown).containsExactly("prod:/etc/nginx");
    }

    @Test void aLocalPaneALocalFileADeletedHostOrNothingIsLeftToTheEditor() {
        assertThat(opening.handle(new OpenRequest(window, Optional.of(pane(UUID.randomUUID())), Optional.empty()))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of("local:/tmp/a.txt")))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of(RemoteDocument.key(prod, "/etc/hosts"))))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.empty()))).isFalse();
        assertThat(shown).isEmpty();
    }

    @Test void registeringHandsTheEditorTheHandlerUntilClosed() {
        Subscription registration = opening.register();
        assertThat(openers).hasSize(1);
        registration.close();
        assertThat(openers).isEmpty();
    }
}
