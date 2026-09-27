package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    final List<EditorDocument> documents = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    volatile RuntimeException editorFailure;
    final Editor editor = new Editor() {
        @Override public void open(Path file) { throw new AssertionError("Remote opens documents, not local paths"); }
        @Override public void open(EditorDocument document) { if (editorFailure != null) throw editorFailure; documents.add(document); }
    };
    volatile Optional<WindowHandle> active = Optional.empty();
    final RemoteEditing editing = new RemoteEditing(editor, channels, () -> active, notices::add);
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");
    final TestWindow origin = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }

    @Test void openHandsTheEditorARemoteDocument() throws Exception {
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document).isInstanceOf(RemoteDocument.class);
            assertThat(document.key()).isEqualTo(RemoteDocument.key(prod, "/etc/hosts"));
            assertThat(document.location()).isEqualTo("deploy@prod:/etc/hosts");
        });
    }

    @Test void anEditorFailureBecomesANotice() throws Exception {
        editorFailure = new IllegalStateException("the Editor window is closing");
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(notices).containsExactly("Could not open /etc/hosts in the Editor: the Editor window is closing");
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
            .hasRootCauseMessage("Reconnecting to prod needs an open terminal window for the sign-in prompt.");
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
