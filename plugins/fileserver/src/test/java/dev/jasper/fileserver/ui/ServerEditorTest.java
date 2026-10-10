package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ServerEditorTest {
    @TempDir Path folder;
    private final FakeKit kit = new FakeKit("dev.jasper.fileserver");
    private final List<ServerDefinition> saved = new ArrayList<>();
    private final AtomicReference<CompletableFuture<Optional<ServerEditor.Account>>> pending = new AtomicReference<>();
    private int folderRequests, keyRequests;

    private static void onEdt(Runnable task) { UiTestAccess.edt(task); }
    private ServerEditor editor(Set<Protocol> protocols, Optional<ServerDefinition> initial, boolean vault, ServerEditor.KeySources keys) {
        var result = new AtomicReference<ServerEditor>();
        onEdt(() -> result.set(new ServerEditor(kit, List.of(), initial, initial.isPresent(), protocols, List.of(), vault,
            id -> Optional.of("Share login"), () -> { var future = new CompletableFuture<Optional<ServerEditor.Account>>(); pending.set(future); return future; },
            () -> { folderRequests++; return Optional.of(folder); }, keys, false, saved::add, () -> { })));
        return result.get();
    }
    private ServerEditor editor(Set<Protocol> protocols) { return editor(protocols, Optional.empty(), false, ServerEditor.KeySources.NONE); }
    private static FakeKitView view(ServerEditor editor) { return (FakeKitView) editor.view(); }
    private static void emit(ServerEditor editor, UiEvent event) { onEdt(() -> view(editor).emit(event)); }
    private static void edit(ServerEditor editor, String id, String value) { emit(editor, new UiEvent.Edited(id, value)); }
    private static void action(ServerEditor editor, String id) { emit(editor, new UiEvent.Action(id)); }
    private static void choose(ServerEditor editor, String id, String value) { emit(editor, new UiEvent.Selected(id, Set.of(value))); }
    private static void toggle(ServerEditor editor, String id, boolean value) { emit(editor, new UiEvent.Toggled(id, value)); }
    private static Nodes.Node node(ServerEditor editor, String id) { return UiTestAccess.node(view(editor), id); }
    private static String field(ServerEditor editor, String id) { return ((Nodes.Field) node(editor, id)).value(); }
    private static String message(ServerEditor editor) { return ((Nodes.Text) node(editor, ServerEditor.MESSAGE)).text(); }
    private void basic(ServerEditor editor) { edit(editor, ServerEditor.NAME, "docs"); edit(editor, ServerEditor.FOLDER, folder.toString()); }

    @Test void protocolChangesPreserveExplicitPortAndHiddenValues() {
        var editor = editor(Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP));
        assertThat(field(editor, ServerEditor.PORT)).isEqualTo("8080");
        choose(editor, ServerEditor.PROTOCOL, "FTP");
        assertThat(field(editor, ServerEditor.PORT)).isEqualTo("2121");
        edit(editor, ServerEditor.PASSIVE, "50000-50020");
        edit(editor, ServerEditor.PORT, "2317");
        toggle(editor, ServerEditor.TLS, true);
        toggle(editor, ServerEditor.REQUIRE_TLS, true);
        choose(editor, ServerEditor.PROTOCOL, "HTTP");
        assertThat(field(editor, ServerEditor.PORT)).isEqualTo("2317");
        basic(editor); action(editor, ServerEditor.SAVE);
        assertThat(saved).singleElement().satisfies(value -> {
            assertThat(value.protocol()).isEqualTo(Protocol.HTTP);
            assertThat(value.port()).isEqualTo(2317);
            assertThat(value.passiveRange()).isEmpty();
            assertThat(value.requireTls()).isFalse();
        });
    }

    @Test void validatesNameFolderAndPortOnlyOnSaveWithoutReadingUserFolders() {
        var editor = editor(Set.of(Protocol.HTTP));
        assertThat(folderRequests).isZero();
        action(editor, ServerEditor.SAVE); assertThat(message(editor)).contains("needs a name");
        edit(editor, ServerEditor.NAME, "docs"); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("Choose a folder");
        edit(editor, ServerEditor.FOLDER, folder.resolve("missing").toString()); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("does not exist");
        action(editor, ServerEditor.CHOOSE_FOLDER);
        assertThat(folderRequests).isEqualTo(1);
        edit(editor, ServerEditor.PORT, "word"); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("port");
        edit(editor, ServerEditor.PORT, "8080"); action(editor, ServerEditor.SAVE);
        assertThat(saved).singleElement().satisfies(value -> {
            assertThat(value.folder()).isEqualTo(folder);
            assertThat(value.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(value.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(value.writable()).isFalse();
        });
    }

    @Test void vaultChooserResolvesOnEdtAndStaleCompletionAfterCloseIsIgnored() throws Exception {
        var editor = editor(Set.of(Protocol.FTP), Optional.empty(), true, ServerEditor.KeySources.NONE);
        basic(editor); choose(editor, ServerEditor.LOGIN, "vault"); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("Choose a Vault account");
        action(editor, ServerEditor.CHOOSE_ACCOUNT);
        var chooser = pending.get();
        assertThat(chooser).isNotNull();
        var account = UUID.randomUUID();
        chooser.complete(Optional.of(new ServerEditor.Account(account, "Share login")));
        onEdt(() -> { });
        action(editor, ServerEditor.SAVE);
        assertThat(saved).singleElement().extracting(ServerDefinition::login).isEqualTo(new Login.Vault(account));
        var stale = editor(Set.of(Protocol.FTP), Optional.empty(), true, ServerEditor.KeySources.NONE);
        action(stale, ServerEditor.CHOOSE_ACCOUNT);
        onEdt(stale::close);
        pending.get().completeExceptionally(new IllegalStateException("unavailable"));
        SwingUtilities.invokeAndWait(() -> { });
        onEdt(() -> assertThat(kit.failures()).isEmpty());
    }

    @Test void unavailableVaultCannotBeChosenAndNoPickerRuns() {
        var editor = editor(Set.of(Protocol.FTP));
        choose(editor, ServerEditor.PROTOCOL, "FTP");
        var login = (Nodes.Choice) node(editor, ServerEditor.LOGIN);
        assertThat(login.options()).noneMatch(option -> option.id().equals("vault"));
        action(editor, ServerEditor.CHOOSE_ACCOUNT);
        assertThat(pending.get()).isNull();
        basic(editor); action(editor, ServerEditor.SAVE);
        assertThat(saved).singleElement().extracting(ServerDefinition::login).isEqualTo(Login.SESSION);
    }

    @Test void tlsAndKeySourcesAreProtocolBoundAndExplicit() {
        var editor = editor(Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP), Optional.empty(), false,
            new ServerEditor.KeySources(() -> { keyRequests++; return Optional.of("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA alice"); }, Optional::empty));
        assertThat(keyRequests).isZero();
        choose(editor, ServerEditor.PROTOCOL, "SFTP");
        action(editor, ServerEditor.ADD_KEY);
        assertThat(keyRequests).isEqualTo(1);
        assertThat(((Nodes.ItemList) node(editor, ServerEditor.KEYS)).items()).hasSize(1);
        basic(editor); action(editor, ServerEditor.SAVE);
        assertThat(saved.getLast().authorizedKeys()).hasSize(1);
        choose(editor, ServerEditor.PROTOCOL, "TFTP");
        toggle(editor, ServerEditor.WRITABLE, true);
        action(editor, ServerEditor.SAVE);
        assertThat(saved.getLast()).satisfies(value -> {
            assertThat(value.protocol()).isEqualTo(Protocol.TFTP);
            assertThat(value.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(value.authorizedKeys()).isEmpty();
            assertThat(value.tls()).isFalse();
            assertThat(value.writable()).isTrue();
        });
    }
}
