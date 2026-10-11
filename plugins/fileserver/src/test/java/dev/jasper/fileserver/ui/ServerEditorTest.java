package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.*;
import java.nio.file.Path;
import java.net.InetAddress;
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
    private static final String ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAEgwWITbeAjMHfs63n4fe6AxR8buxH3Wx+IdWdotPF0 alice@laptop";
    private static final String ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBCKLV4ChGhVzArLw7brgwvIOQAjpwGqDaTifUtUTLxNQCWVdgGVtTjhhZSbIMKVdDUtRyBDkfjMCE3d4mznEAa8=";

    private static void onEdt(Runnable task) { UiTestAccess.edt(task); }
    private ServerEditor editor(Set<Protocol> protocols, Optional<ServerDefinition> initial, boolean vault, ServerEditor.KeySources keys) {
        return editor(protocols, initial, vault, keys, List.of(), false, List.of());
    }
    private ServerEditor editor(Set<Protocol> protocols, Optional<ServerDefinition> initial, boolean vault, ServerEditor.KeySources keys,
                                List<ServerDefinition> others, boolean linux, List<ServerAddresses.Nic> interfaces) {
        var result = new AtomicReference<ServerEditor>();
        onEdt(() -> result.set(new ServerEditor(kit, others, initial, initial.isPresent(), protocols, interfaces, vault,
            id -> Optional.of("Share login"), () -> { var future = new CompletableFuture<Optional<ServerEditor.Account>>(); pending.set(future); return future; },
            () -> { folderRequests++; return Optional.of(folder); }, keys, linux, saved::add, () -> { })));
        return result.get();
    }
    private ServerEditor editor(Set<Protocol> protocols) { return editor(protocols, Optional.empty(), false, ServerEditor.KeySources.NONE); }
    private static FakeKitView view(ServerEditor editor) { return (FakeKitView) editor.view(); }
    private static void emit(ServerEditor editor, UiEvent event) { onEdt(() -> view(editor).emit(event)); }
    private static void edit(ServerEditor editor, String id, String value) { emit(editor, new UiEvent.Edited(id, value)); }
    private static void action(ServerEditor editor, String id) {
        var node = node(editor, id);
        emit(editor, new UiEvent.Action(node instanceof Nodes.Button button ? button.action() : id));
    }
    private static void choose(ServerEditor editor, String id, String value) { emit(editor, new UiEvent.Selected(id, Set.of(value))); }
    private static void toggle(ServerEditor editor, String id, boolean value) { emit(editor, new UiEvent.Toggled(id, value)); }
    private static Nodes.Node node(ServerEditor editor, String id) { return UiTestAccess.node(view(editor), id); }
    private static String field(ServerEditor editor, String id) { return ((Nodes.Field) node(editor, id)).value(); }
    private static String message(ServerEditor editor) { return ((Nodes.Text) node(editor, ServerEditor.MESSAGE)).text(); }
    private static String text(ServerEditor editor, String id) { return ((Nodes.Text) node(editor, id)).text(); }
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

    @Test void editingLocksProtocolKeepsUnavailableInterfaceAndRejectsDuplicateNames() throws Exception {
        var other = ServerDefinition.create("taken", Protocol.HTTP, folder);
        var original = new ServerDefinition(UUID.randomUUID(), "docs", Protocol.HTTP, folder, new Bind.Interface("en9"), 8443,
            Optional.empty(), true, new Login.Vault(UUID.randomUUID()), List.of(), true, false, true);
        var editor = editor(Set.of(Protocol.HTTP, Protocol.FTP), Optional.of(original), true, ServerEditor.KeySources.NONE,
            List.of(other, original), false, List.of(new ServerAddresses.Nic("en0", true, false, false,
                List.of(InetAddress.getByName("192.168.1.20")))));
        assertThat(((Nodes.Choice) node(editor, ServerEditor.PROTOCOL)).state().enabled()).isFalse();
        assertThat(((Nodes.Choice) node(editor, ServerEditor.REACHABLE)).options())
            .anyMatch(option -> option.label().equals("en9 (not available)"));
        assertThat(((Nodes.Choice) node(editor, ServerEditor.REACHABLE)).options())
            .anyMatch(option -> option.label().equals("en0 (192.168.1.20)"));
        assertThat(((Nodes.Toggle) node(editor, ServerEditor.AUTOSTART)).checked()).isTrue();
        choose(editor, ServerEditor.PROTOCOL, "FTP");
        assertThat(((Nodes.Choice) node(editor, ServerEditor.PROTOCOL)).selectedId()).isEqualTo("HTTP");
        edit(editor, ServerEditor.NAME, "TAKEN"); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).isEqualTo("A server named taken exists");
        edit(editor, ServerEditor.NAME, "docs"); action(editor, ServerEditor.SAVE);
        assertThat(saved).containsExactly(original);
    }

    @Test void ftpRangeValidationTlsDependencyAndEditRoundTrip() {
        var editor = editor(Set.of(Protocol.HTTP, Protocol.FTP));
        choose(editor, ServerEditor.PROTOCOL, "FTP");
        assertThat(((Nodes.Field) node(editor, ServerEditor.PASSIVE)).state().enabled()).isTrue();
        assertThat(((Nodes.Toggle) node(editor, ServerEditor.REQUIRE_TLS)).state().enabled()).isFalse();
        assertThat(text(editor, "server.warning")).contains("Plain FTP sends the password unencrypted");
        toggle(editor, ServerEditor.TLS, true);
        assertThat(((Nodes.Toggle) node(editor, ServerEditor.REQUIRE_TLS)).state().enabled()).isTrue();
        toggle(editor, ServerEditor.REQUIRE_TLS, true);
        basic(editor); edit(editor, ServerEditor.PASSIVE, "60000-50000"); action(editor, ServerEditor.SAVE);
        assertThat(message(editor)).contains("lower to a higher port");
        assertThat(saved).isEmpty();
        edit(editor, ServerEditor.PASSIVE, " 51000-51010 "); action(editor, ServerEditor.SAVE);
        var definition = saved.getLast();
        assertThat(definition.passiveRange()).contains(new PortRange(51000, 51010));
        assertThat(definition.tls()).isTrue();
        assertThat(definition.requireTls()).isTrue();
        var edited = editor(Set.of(Protocol.HTTP, Protocol.FTP), Optional.of(definition), false, ServerEditor.KeySources.NONE);
        assertThat(field(edited, ServerEditor.PASSIVE)).isEqualTo("51000-51010");
        assertThat(((Nodes.Toggle) node(edited, ServerEditor.TLS)).checked()).isTrue();
        assertThat(((Nodes.Toggle) node(edited, ServerEditor.REQUIRE_TLS)).checked()).isTrue();
        action(edited, ServerEditor.SAVE);
        assertThat(saved.getLast()).isEqualTo(definition);
        toggle(editor, ServerEditor.TLS, false);
        assertThat(((Nodes.Toggle) node(editor, ServerEditor.REQUIRE_TLS)).checked()).isTrue();
        assertThat(((Nodes.Toggle) node(editor, ServerEditor.REQUIRE_TLS)).state().enabled()).isFalse();
    }

    @Test void sftpKeyDedupRemovalMalformedFeedbackAndKeysOnlyValidation() {
        var editor = editor(Set.of(Protocol.SFTP), Optional.empty(), false,
            new ServerEditor.KeySources(() -> Optional.of("# keys\n" + ED25519 + "\n" + ED25519), () -> Optional.of(ECDSA)));
        action(editor, ServerEditor.ADD_KEY); action(editor, ServerEditor.PASTE_KEY);
        assertThat(((Nodes.ItemList) node(editor, ServerEditor.KEYS)).items()).hasSize(2);
        emit(editor, new UiEvent.Selected(ServerEditor.KEYS, Set.of("key.0")));
        action(editor, ServerEditor.REMOVE_KEY);
        assertThat(((Nodes.ItemList) node(editor, ServerEditor.KEYS)).items()).hasSize(1);
        basic(editor); choose(editor, ServerEditor.LOGIN, "anon"); action(editor, ServerEditor.SAVE);
        assertThat(saved.getLast().authorizedKeys()).containsExactly(ECDSA);
        var edited = editor(Set.of(Protocol.SFTP), Optional.of(saved.getLast()), false, ServerEditor.KeySources.NONE);
        action(edited, ServerEditor.SAVE);
        assertThat(saved.getLast()).isEqualTo(saved.get(saved.size() - 2));
        var bad = editor(Set.of(Protocol.SFTP), Optional.empty(), false,
            new ServerEditor.KeySources(() -> Optional.of("not a key"), Optional::empty));
        action(bad, ServerEditor.ADD_KEY);
        assertThat(message(bad)).contains("Not an OpenSSH public key");
        action(bad, ServerEditor.PASTE_KEY);
        basic(bad); choose(bad, ServerEditor.LOGIN, "anon"); action(bad, ServerEditor.SAVE);
        assertThat(message(bad)).isEqualTo("SFTP needs a password or authorized keys");
    }

    @Test void linuxPortHintAndRiskySharingWarningsTrackChoices() {
        var editor = editor(Set.of(Protocol.HTTP), Optional.empty(), true, ServerEditor.KeySources.NONE,
            List.of(), true, List.of());
        edit(editor, ServerEditor.PORT, "80");
        assertThat(text(editor, "server.port.hint")).contains("root or CAP_NET_BIND_SERVICE");
        edit(editor, ServerEditor.PORT, "8080");
        assertThat(node(editor, "server.port.hint").state().visible()).isFalse();
        choose(editor, ServerEditor.LOGIN, "vault");
        assertThat(text(editor, "server.warning")).contains("Plain HTTP sends the password unencrypted");
        toggle(editor, ServerEditor.WRITABLE, true);
        choose(editor, ServerEditor.REACHABLE, "bind.1");
        assertThat(text(editor, "server.warning")).contains("Writable and reachable from other computers");
    }
}
