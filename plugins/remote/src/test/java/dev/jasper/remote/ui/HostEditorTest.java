package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

class HostEditorTest {
    final List<RemoteHost> saved = new ArrayList<>();
    final RemoteHost bastion = RemoteHost.create("bastion", "b.example", 22, "ops", Auth.AGENT, "Homelab", Optional.empty());
    final CredentialDescriptor login = new CredentialDescriptor(UUID.randomUUID(), "Work login", "deploy", Kind.ACCOUNT_PASSWORD);
    final UiKit kit = new FakeKit("dev.jasper.remote");

    HostEditor editor(List<RemoteHost> others, Optional<RemoteHost> editing, boolean vault, java.util.function.Function<UUID, Optional<String>> names,
                      Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick, Runnable cancel) {
        return new HostEditor(kit, UI, others, editing, vault, names, pick, saved::add, cancel);
    }

    static String value(HostEditor editor, String id) { return node(editor.view(), id, Nodes.Field.class).value(); }
    static Nodes.Choice choice(HostEditor editor, String id) { return node(editor.view(), id, Nodes.Choice.class); }
    static boolean checked(HostEditor editor, String id) { return node(editor.view(), id, Nodes.Toggle.class).checked(); }
    static void choose(HostEditor editor, String id, String option) { emit(editor.view(), new UiEvent.Selected(id, Set.of(option))); }
    static void save(HostEditor editor) { click(editor.view(), HostEditor.SAVE); }

    /** The plugin's form: agent authentication (no Vault), persistence it controls, and a connect callback. */
    HostEditor connecting(Optional<RemoteHost> editing, java.util.function.Function<RemoteHost, CompletableFuture<Void>> persist,
                          Runnable onSaved, java.util.function.Consumer<RemoteHost> connect) {
        return new HostEditor(kit, UI, List.of(), editing, false, id -> Optional.empty(), request -> new CompletableFuture<>(),
            persist, onSaved, () -> { }, connect);
    }

    static void fill(HostEditor editor) {
        type(editor.view(), HostEditor.NAME, "prod"); type(editor.view(), HostEditor.HOSTNAME, "api.example");
        type(editor.view(), HostEditor.USERNAME, "deploy");
    }

    @Test void addAndConnectSavesThenConnectsToTheSavedHost() throws Exception {
        edt(() -> {
            var events = new ArrayList<String>(); var connected = new ArrayList<RemoteHost>(); var persisted = new CompletableFuture<Void>();
            var editor = connecting(Optional.empty(), host -> { events.add("persist " + host.name()); return persisted; },
                () -> events.add("saved"), host -> { events.add("connect " + host.name()); connected.add(host); });
            assertThat(form(editor.view()).actions()).extracting(Nodes.Button::id)
                .containsExactly(HostEditor.CANCEL, HostEditor.SAVE_CONNECT, HostEditor.SAVE);
            assertThat(button(editor.view(), HostEditor.SAVE_CONNECT)).satisfies(add -> {
                assertThat(add.label()).isEqualTo("Add and Connect");
                assertThat(add.kind()).as("Add Host stays the primary").isEqualTo(Nodes.ButtonKind.SECONDARY);
                assertThat(add.state().enabled()).as("enabled with Add Host, once the form is valid").isFalse();
            });
            fill(editor);
            assertThat(button(editor.view(), HostEditor.SAVE_CONNECT).state().enabled()).isTrue();
            click(editor.view(), HostEditor.SAVE_CONNECT);
            assertThat(events).containsExactly("persist prod");
            assertThat(button(editor.view(), HostEditor.SAVE_CONNECT).label()).isEqualTo("Saving…");
            assertThat(button(editor.view(), HostEditor.SAVE)).satisfies(add -> {
                assertThat(add.label()).isEqualTo("Add Host"); assertThat(add.state().enabled()).isFalse(); });
            persisted.complete(null);
            assertThat(events).containsExactly("persist prod", "saved", "connect prod");
            assertThat(connected).singleElement().satisfies(host -> {
                assertThat(host.hostname()).isEqualTo("api.example"); assertThat(host.username()).isEqualTo("deploy"); });
        });
    }

    @Test void aFailedSaveConnectsNothingAndAddHostStillOnlySaves() throws Exception {
        edt(() -> {
            var connected = new ArrayList<RemoteHost>(); var saves = new ArrayList<RemoteHost>();
            var failing = connecting(Optional.empty(), host -> CompletableFuture.failedFuture(new java.io.IOException("disk full")),
                () -> { }, connected::add);
            fill(failing);
            click(failing.view(), HostEditor.SAVE_CONNECT);
            assertThat(failing.message()).isEqualTo("disk full");
            assertThat(button(failing.view(), HostEditor.SAVE_CONNECT).label()).isEqualTo("Add and Connect");
            var plain = connecting(Optional.empty(), host -> { saves.add(host); return CompletableFuture.completedFuture(null); },
                () -> { }, connected::add);
            fill(plain);
            save(plain);
            assertThat(saves).hasSize(1);
            assertThat(connected).isEmpty();
        });
    }

    @Test void onlyAddingWithAConnectCallbackOffersAddAndConnect() throws Exception {
        edt(() -> {
            var existing = connecting(Optional.of(bastion), host -> CompletableFuture.completedFuture(null), () -> { }, host -> { });
            assertThat(form(existing.view()).actions()).extracting(Nodes.Button::id).containsExactly(HostEditor.CANCEL, HostEditor.SAVE);
            var legacy = editor(List.of(), Optional.empty(), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> { });
            assertThat(form(legacy.view()).actions()).extracting(Nodes.Button::id).containsExactly(HostEditor.CANCEL, HostEditor.SAVE);
        });
    }

    @Test void createsAHostWithAVaultCredentialAndAJump() throws Exception {
        edt(() -> {
            var editor = editor(List.of(bastion), Optional.empty(), true, id -> id.equals(login.id()) ? Optional.of(login.name()) : Optional.empty(),
                () -> CompletableFuture.completedFuture(Optional.of(login)), () -> { });
            assertThat(value(editor, HostEditor.PORT)).isEqualTo("22");
            assertThat(choice(editor, HostEditor.AUTH).selectedId()).isEqualTo(HostEditor.VAULT);
            assertThat(choice(editor, HostEditor.JUMP).options()).as("none + bastion").hasSize(2);
            assertThat(choice(editor, HostEditor.JUMP).label()).as("spec 13: sentence case").isEqualTo("Jump host");
            assertThat(node(editor.view(), "row." + HostEditor.JUMP, Nodes.FormRow.class).label()).isEqualTo("Jump host");
            save(editor);
            assertThat(editor.message()).contains("name");
            assertThat(form(editor.view()).preview()).as("the message sits beside the buttons, as it sat above them at baseline")
                .hasValueSatisfying(line -> assertThat(line).isInstanceOfSatisfying(Nodes.Text.class, text -> {
                    assertThat(text.id()).isEqualTo(HostEditor.MESSAGE); assertThat(text.state().visible()).isTrue(); assertThat(text.text()).contains("name"); }));
            type(editor.view(), HostEditor.NAME, "prod"); type(editor.view(), HostEditor.HOSTNAME, "api.example"); type(editor.view(), HostEditor.PORT, "2222");
            save(editor);
            assertThat(editor.message()).contains("credential");
            click(editor.view(), HostEditor.CHOOSE);
            assertThat(editor.credentialLabel()).isEqualTo("Work login");
            type(editor.view(), HostEditor.GROUP, "Production");
            choose(editor, HostEditor.JUMP, bastion.id().toString());
            emit(editor.view(), new UiEvent.Toggled(HostEditor.FAVORITE, true));
            save(editor);
            assertThat(saved).singleElement().satisfies(host -> {
                assertThat(host.name()).isEqualTo("prod");
                assertThat(host.port()).isEqualTo(2222);
                assertThat(host.auth()).isEqualTo(new Auth.Vault(login.id()));
                assertThat(host.username()).as("the login supplies it").isEmpty();
                assertThat(host.group()).isEqualTo("Production");
                assertThat(host.jump()).contains(bastion.id());
                assertThat(host.favorite()).isTrue();
            });
        });
    }

    @Test void editsKeepTheIdAndAgentNeedsAUsername() throws Exception {
        edt(() -> {
            boolean[] cancelled = {false};
            var editor = editor(List.of(), Optional.of(bastion), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> cancelled[0] = true);
            assertThat(value(editor, HostEditor.NAME)).isEqualTo("bastion");
            var auth = choice(editor, HostEditor.AUTH);
            assertThat(auth.options()).extracting(dev.jasper.sdk.ui.Option::label).as("no Vault installed")
                .containsExactly("Vault credential (needs Credential Vault)", "SSH Agent");
            assertThat(auth.selectedId()).isEqualTo(HostEditor.AGENT);
            choose(editor, HostEditor.AUTH, HostEditor.VAULT);
            assertThat(choice(editor, HostEditor.AUTH).selectedId()).as("Vault cannot be chosen without the Vault").isEqualTo(HostEditor.AGENT);
            assertThat(choice(editor, HostEditor.GROUP).search().orElseThrow().entry()).contains("Homelab");
            type(editor.view(), HostEditor.USERNAME, "");
            save(editor);
            assertThat(editor.message()).contains("username");
            type(editor.view(), HostEditor.USERNAME, "root"); type(editor.view(), HostEditor.PORT, "abc");
            save(editor);
            assertThat(editor.message()).contains("port");
            type(editor.view(), HostEditor.PORT, "22");
            save(editor);
            assertThat(saved).singleElement().satisfies(host -> { assertThat(host.id()).isEqualTo(bastion.id()); assertThat(host.username()).isEqualTo("root"); assertThat(host.created()).isEqualTo(bastion.created()); });
            click(editor.view(), HostEditor.CANCEL);
            assertThat(cancelled[0]).isTrue();
        });
    }

    @Test void preservesAndReordersManagedKeys() throws Exception {
        edt(() -> {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            RemoteHost host = bastion.withEdited("managed", "server", 22, "ops", new Auth.VaultKeys(List.of(a, b)), "", Optional.empty());
            var editor = editor(List.of(), Optional.of(host), true, id -> Optional.of(id.equals(a) ? "first" : "second"),
                () -> CompletableFuture.completedFuture(Optional.of(login)), () -> {});
            var keys = node(editor.view(), HostEditor.KEYS, Nodes.ItemList.class);
            assertThat(keys.kind()).isEqualTo(Nodes.ListKind.ORDERED);
            assertThat(texts(editor.view())).contains("first", "second");
            assertThat(editor.credentialLabel()).isEqualTo("2 Vault key(s)");
            type(editor.view(), HostEditor.HOSTNAME, "changed"); save(editor);
            assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(a, b)));
            emit(editor.view(), new UiEvent.Reordered(HostEditor.KEYS, b.toString(), 0)); save(editor);
            assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(b, a)));
            click(editor.view(), HostEditor.ADD_KEY);
            assertThat(editor.message()).contains("managed SSH key");
            emit(editor.view(), new UiEvent.Removed(HostEditor.KEYS, b.toString())); save(editor);
            assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(a)));
            emit(editor.view(), new UiEvent.Removed(HostEditor.KEYS, a.toString()));
            assertThat(editor.credentialLabel()).isEqualTo("none chosen");
        });
    }

    /** Switching to the agent and back keeps the chosen keys (and credential) until Save, as the radio buttons did. */
    @Test void authModeChangesRetainTheKeysAndCredentialUntilSave() throws Exception {
        edt(() -> {
            UUID a = UUID.randomUUID();
            RemoteHost host = bastion.withEdited("managed", "server", 22, "ops", new Auth.VaultKeys(List.of(a)), "", Optional.empty());
            var editor = editor(List.of(), Optional.of(host), true, id -> Optional.of("first"), () -> CompletableFuture.completedFuture(Optional.of(login)), () -> {});
            choose(editor, HostEditor.AUTH, HostEditor.AGENT);
            assertThat(node(editor.view(), HostEditor.KEYS, Nodes.ItemList.class).state().enabled()).as("keys apply to Vault only").isFalse();
            assertThat(choice(editor, HostEditor.CHOOSE).state().enabled()).isFalse();
            assertThat(node(editor.view(), HostEditor.KEYS, Nodes.ItemList.class).items()).extracting(dev.jasper.sdk.ui.ListItem::id).containsExactly(a.toString());
            choose(editor, HostEditor.AUTH, HostEditor.VAULT);
            save(editor);
            assertThat(saved.getLast().auth()).isEqualTo(new Auth.VaultKeys(List.of(a)));
            choose(editor, HostEditor.AUTH, HostEditor.AGENT);
            save(editor);
            assertThat(saved.getLast().auth()).isEqualTo(Auth.AGENT);
            choose(editor, HostEditor.AUTH, HostEditor.VAULT);
            save(editor);
            assertThat(saved.getLast().auth()).as("the keys were still there").isEqualTo(new Auth.VaultKeys(List.of(a)));
        });
    }

    @Test void followDirectoryIsOnForNewHostsAndKeptWhenEditing() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> { });
            assertThat(checked(editor, HostEditor.FOLLOW)).isTrue();
            assertThat(node(editor.view(), HostEditor.FOLLOW, Nodes.Toggle.class).label()).isEqualTo("Track shell folder for SFTP follow");
            type(editor.view(), HostEditor.NAME, "n"); type(editor.view(), HostEditor.HOSTNAME, "h"); type(editor.view(), HostEditor.USERNAME, "u");
            emit(editor.view(), new UiEvent.Toggled(HostEditor.FOLLOW, false));
            save(editor);
            assertThat(saved).singleElement().satisfies(host -> assertThat(host.followDirectory()).isFalse());
            var edit = editor(List.of(), Optional.of(saved.getFirst()), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> { });
            assertThat(checked(edit, HostEditor.FOLLOW)).isFalse();
            emit(edit.view(), new UiEvent.Toggled(HostEditor.FOLLOW, true));
            save(edit);
            assertThat(saved.getLast().followDirectory()).isTrue();
            assertThat(saved.getLast().id()).isEqualTo(saved.getFirst().id());
        });
    }

    /** Baseline f16ae32f: no default button and no field listeners, so Enter saves nothing; Escape cancels. */
    @Test void enterNeverSavesEscapeCancelsAndTheDefaultGroupIsNamed() throws Exception {
        edt(() -> {
            int[] cancelled = {0};
            var editor = editor(List.of(bastion), Optional.empty(), true, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> cancelled[0]++);
            assertThat(form(editor.view()).submission()).isEqualTo(Nodes.FormSubmission.NONE);
            assertThat(primaries(editor.view())).singleElement().satisfies(action -> assertThat(action.id()).isEqualTo(HostEditor.SAVE));
            assertThat(form(editor.view()).actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Add Host");
            assertThat(all(editor.view())).filteredOn(n -> n instanceof Nodes.Field).allSatisfy(n -> assertThat(((Nodes.Field) n).trailingAction()).isEmpty());
            editor.setDefaultGroupName("Servers");
            assertThat(node(editor.view(),"row."+HostEditor.GROUP,Nodes.SettingRow.class).description()).isEmpty();
            assertThat(choice(editor,HostEditor.GROUP).label()).contains("Servers (default)");
            assertThat(choice(editor, HostEditor.GROUP).options()).extracting(dev.jasper.sdk.ui.Option::label).containsExactly("Homelab");
            press(editor.view(), form(editor.view()).cancelAction());
            assertThat(cancelled[0]).isEqualTo(1);
            assertThat(form(editor(List.of(), Optional.of(bastion), true, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> { }).view())
                .actions()).extracting(Nodes.Button::label).containsExactly("Cancel", "Save");
        });
    }

    /** A Vault choice that completes after the dialog closed never touches the disposed view. */
    @Test void aLatePickAfterCloseIsIgnored() throws Exception {
        edt(() -> {
            var recording = new RecordingKit("dev.jasper.remote");
            var pending = new CompletableFuture<Optional<CredentialDescriptor>>();
            var editor = new HostEditor(recording, UI, List.of(), Optional.empty(), true, id -> Optional.empty(), () -> pending, saved::add, () -> { });
            click(editor.view(), HostEditor.CHOOSE);
            editor.close();
            pending.complete(Optional.of(login));
            editor.showError("late");
            assertThat(recording.lateCalls).isEmpty();
            assertThat(saved).isEmpty();
        });
    }

    /**
     * Review fix round 1 (M1): Vault may complete a pick on a worker thread (after an unlock). The choice still reaches the
     * EDT-confined view, through the plugin's UI executor, instead of failing silently inside the future.
     */
    @Test void aPickCompletedOffTheEdtStillShowsTheChoice() throws Exception {
        var pending = new CompletableFuture<Optional<CredentialDescriptor>>();
        var editor = dev.jasper.remote.testsupport.Kits.value(() -> new HostEditor(kit, UI, List.of(), Optional.empty(), true, id -> id.equals(login.id()) ? Optional.of(login.name()) : Optional.empty(),
            () -> pending, saved::add, () -> { }));
        edt(() -> click(editor.view(), HostEditor.CHOOSE));
        var worker = Thread.ofPlatform().start(() -> pending.complete(Optional.of(login)));
        worker.join(5_000);
        assertThat(pending).isCompleted();
        edt(() -> { });
        assertThat(dev.jasper.remote.testsupport.Kits.value(editor::credentialLabel)).isEqualTo("Work login");
        assertThat(dev.jasper.remote.testsupport.Kits.value(() -> choice(editor, HostEditor.CHOOSE).face().orElseThrow().primary()))
            .as("the view shows the choice: the render ran on the EDT, not on the completing thread").isEqualTo("Work login");
        edt(() -> {
            type(editor.view(), HostEditor.NAME, "prod"); type(editor.view(), HostEditor.HOSTNAME, "api.example");
            save(editor);
        });
        assertThat(saved).singleElement().satisfies(host -> assertThat(host.auth()).isEqualTo(new Auth.Vault(login.id())));
    }
}
