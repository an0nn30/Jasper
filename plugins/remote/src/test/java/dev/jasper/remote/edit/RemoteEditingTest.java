package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteEditingTest {
    @TempDir Path temp;
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<Optional<WindowHandle>> owners = new CopyOnWriteArrayList<>();
    volatile boolean connected = true;
    final EditChannels channels = new EditChannels((identity, owner) -> {
        owners.add(owner);
        return CompletableFuture.completedFuture(new LocalEndpoint());
    }, identity -> connected, background, ui);
    final List<dev.jasper.sdk.WindowOwner> editorOwners = new ArrayList<>();
    final List<EditorDocument> documents = new ArrayList<>();
    final FakeNotifications notifications = new FakeNotifications();
    volatile RuntimeException editorFailure;
    final Map<String, Function<String, Optional<Function<dev.jasper.sdk.WindowOwner, EditorDocument>>>> reopeners = new ConcurrentHashMap<>();
    final Map<UUID, RemoteHost> saved = new ConcurrentHashMap<>();
    final Editor editor = new Editor() {
        @Override public void open(Path file) { throw new AssertionError("Remote opens documents, not local paths"); }
        @Override public void open(EditorDocument document, dev.jasper.sdk.WindowOwner owner) { editorOwners.add(owner); open(document); }
        @Override public void open(EditorDocument document) { if (editorFailure != null) throw editorFailure; documents.add(document); }
        @Override public Subscription reopener(String prefix, Function<String, Optional<Function<dev.jasper.sdk.WindowOwner, EditorDocument>>> resolve) {
            reopeners.put(prefix, resolve);
            return () -> reopeners.remove(prefix, resolve);
        }
    };
    volatile Optional<WindowHandle> active = Optional.empty();
    final RemoteEditing editing = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"));
    final RemoteEditing reopening = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"), id -> Optional.ofNullable(saved.get(id)));
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");
    final TestWindow origin = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }

    @Test void openHandsTheEditorARemoteDocument() throws Exception {
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(editorOwners).containsExactly(origin);
        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document).isInstanceOf(RemoteDocument.class);
            assertThat(document.key()).isEqualTo(RemoteDocument.key(prod, "/etc/hosts"));
            assertThat(document.location()).isEqualTo("deploy@prod:/etc/hosts");
        });
    }

    @Test void aRecordedKeyReopensOnTheSavedHostsAsTheyAreNow() {
        var hop = prod.hops().getFirst();
        var renamed = hop.host().withEdited("prod-renamed", hop.host().hostname(), hop.host().port(), hop.host().username(),
            hop.host().auth(), "", Optional.empty());
        saved.put(renamed.id(), renamed);
        String key = RemoteDocument.key(prod, "/etc/hosts");
        EditorDocument document = reopening.reopen(key).orElseThrow().apply(origin);
        assertThat(document).isInstanceOf(RemoteDocument.class);
        assertThat(document.key()).isEqualTo(key);
        assertThat(document.host()).contains("prod-renamed");
        assertThat(document.location()).isEqualTo("deploy@prod-renamed:/etc/hosts");
    }

    @Test void aKeyWhoseHostIsGoneOrThatIsMalformedCannotBeReopened() {
        var target = prod.hops().getFirst().host();
        var jump = RemoteHost.create("bastion", "10.0.0.1", 22, "ops", dev.jasper.remote.hosts.Auth.AGENT, "", Optional.empty());
        var routed = new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(target, "deploy"), new ConnectionIdentity.Hop(jump, "ops")));
        assertThat(reopening.reopen(RemoteDocument.key(prod, "/etc/hosts"))).isEmpty();
        saved.put(target.id(), target);
        assertThat(reopening.reopen(RemoteDocument.key(routed, "/etc/hosts"))).isEmpty();
        assertThat(reopening.reopen("remote:garbage")).isEmpty();
        saved.put(jump.id(), jump);
        assertThat(reopening.reopen(RemoteDocument.key(routed, "/etc/hosts"))).isPresent();
    }

    /**
     * Workspace tabs spec 13: Jasper restores its windows before Remote has read its saved hosts; a recorded key still
     * reopens, and the document finds its hosts when it is first read. A host gone once they are read fails that read.
     */
    @Test void aKeyReopensBeforeTheSavedHostsHaveLoadedAndFindsThemWhenRead() throws Exception {
        var loaded = new CompletableFuture<Void>();
        var early = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> loaded, ui, route -> true);
        String key = RemoteDocument.key(prod, "/etc/hosts");
        EditorDocument document = early.reopen(key).orElseThrow().apply(origin);
        assertThat(document.key()).isEqualTo(key);
        assertThat(document.name()).isEqualTo("hosts");
        var read = onUi(() -> document.read(1 << 20));
        assertThat(read).as("waits for the saved hosts").isNotDone();
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        loaded.complete(null);
        assertThat(read.get(10, TimeUnit.SECONDS).bytes()).isEqualTo(Files.readAllBytes(Path.of("/etc/hosts")));
        assertThat(onUi(document::host)).contains("prod");
        var gone = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(null), ui, route -> true);
        assertThat(gone.reopen(key)).as("the saved hosts are read: a host that is gone cannot reopen").isEmpty();
        var pending = new CompletableFuture<Void>();
        var later = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.empty(), () -> pending, ui, route -> true);
        EditorDocument orphan = later.reopen(key).orElseThrow().apply(origin);
        var failed = onUi(() -> orphan.read(1 << 20));
        pending.complete(null);
        assertThatThrownBy(() -> failed.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Its host is no longer saved in Remote");
    }

    /** Workspace tabs spec 13: a quiet read asks nothing: it reads when signing in would not, and otherwise fails saying so. */
    @Test void aQuietReadSignsInOnlyWhenNothingWouldBeAsked() throws Exception {
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        connected = false;
        var asking = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> CompletableFuture.completedFuture(null), ui, route -> false);
        EditorDocument document = asking.reopen(RemoteDocument.key(prod, "/etc/hosts")).orElseThrow().apply(origin);
        var quiet = onUi(() -> document.readQuietly(1 << 20));
        assertThatThrownBy(() -> quiet.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Not signed in to prod; Retry signs in");
        assertThat(owners).as("no channel opened, nothing asked").isEmpty();
        var silent = new RemoteEditing(editor, channels, () -> active, notifications.group("dev.jasper.remote"),
            id -> Optional.ofNullable(saved.get(id)), () -> CompletableFuture.completedFuture(null), ui, route -> true);
        EditorDocument signsIn = silent.reopen(RemoteDocument.key(prod, "/etc/hosts")).orElseThrow().apply(origin);
        assertThat(onUi(() -> signsIn.readQuietly(1 << 20)).get(10, TimeUnit.SECONDS).bytes()).isNotEmpty();
    }

    @Test void registeringHandsTheEditorTheRemotePrefixUntilClosed() {
        var target = prod.hops().getFirst().host();
        saved.put(target.id(), target);
        Subscription registration = reopening.registerReopener();
        assertThat(reopeners).containsOnlyKeys("remote:");
        assertThat(reopeners.get("remote:").apply(RemoteDocument.key(prod, "/etc/hosts"))).isPresent();
        registration.close();
        assertThat(reopeners).isEmpty();
    }

    @Test void anEditorFailureBecomesANotice() throws Exception {
        editorFailure = new IllegalStateException("the Editor window is closing");
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(notifications.lines()).containsExactly(
            "dev.jasper.remote|ERROR|Could not open a file in the Editor|/etc/hosts: the Editor window is closing|IllegalStateException");
        assertThat(documents).isEmpty();
    }

    @Test void signInPromptsBelongToTheOriginWindowWhileItIsOpenThenToTheActiveWindow() throws Exception {
        connected = false;
        Path file = Files.writeString(temp.toRealPath().resolve("hosts"), "127.0.0.1 localhost\n");
        onUi(() -> { editing.open(prod, file.toString(), origin); return null; });
        var document = documents.getFirst();
        onUi(document::revision).get(10, TimeUnit.SECONDS);
        origin.open().set(false);
        var other = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));
        active = Optional.of(other);
        onUi(document::revision).get(10, TimeUnit.SECONDS);
        assertThat(owners).containsExactly(Optional.of(origin), Optional.of(other));
        active = Optional.empty();
        assertThatThrownBy(() -> onUi(document::revision).get(10, TimeUnit.SECONDS))
            .hasRootCauseMessage("Reconnecting to prod needs an open Jasper window for the sign-in prompt.");
    }

    @Test void kindsFollowLinksInOrder() throws Exception {
        Path folder = temp.toRealPath();
        Path file = Files.writeString(folder.resolve("app.conf"), "x");
        Files.createSymbolicLink(folder.resolve("to-file"), Path.of("app.conf"));
        Files.createDirectory(folder.resolve("conf.d"));
        Files.createSymbolicLink(folder.resolve("to-folder"), Path.of("conf.d"));
        Files.createSymbolicLink(folder.resolve("dangling"), Path.of("gone"));
        var paths = List.of(file.toString(), folder.resolve("to-file").toString(), folder.resolve("to-folder").toString(), folder.resolve("dangling").toString());
        var kinds = onUi(() -> editing.kinds(prod, paths, origin)).get(10, TimeUnit.SECONDS);
        assertThat(kinds).containsExactly(Optional.of(FileEntry.Kind.FILE), Optional.of(FileEntry.Kind.FILE),
            Optional.of(FileEntry.Kind.DIRECTORY), Optional.empty());
    }
}
