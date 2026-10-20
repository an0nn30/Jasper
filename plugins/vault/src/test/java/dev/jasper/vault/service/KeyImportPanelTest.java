package dev.jasper.vault.service;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.SshKeySource;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import dev.jasper.vault.ui.KeyImportPanel;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

/** The import dialog keeps the former keyboard meaning: Enter unlocks a wanted passphrase and never commits. */
class KeyImportPanelTest {
    @TempDir Path directory;
    final ArrayDeque<Runnable> reads = new ArrayDeque<>(), writes = new ArrayDeque<>(), ui = new ArrayDeque<>();
    final List<KeyImportPrompt> prompts = new ArrayList<>();
    LockManager lock;
    VaultApi api;
    final dev.jasper.sdk.terminal.WindowHandle owner = VaultServiceTest.window();

    void setup() {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> { throw new java.io.UncheckedIOException(new java.io.IOException("fixture")); }, directory.resolve("unused")), new FileStore(directory.resolve("device.secret")));
        VaultService[] service = {null};
        lock = new LockManager(new VaultFile(directory.resolve("vault.jv")), secrets, writes::add, ui::add,
            state -> { if (service[0] != null) service[0].lockStateChanged(state); });
        var created = lock.create("pw".toCharArray(), false); drain(writes); created.join();
        service[0] = new VaultService(lock, () -> Optional.of(owner), p -> {}, p -> {}, p -> {}, new FakeNotifications().group("dev.jasper.vault"),
            reads::add, ui::add, w -> CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED), prompts::add, () -> {});
        api = service[0].forConsumer(VaultServiceTest.SSH);
    }
    void flushUi() { while (!ui.isEmpty()) ui.removeFirst().run(); }
    void drain(ArrayDeque<Runnable> workers) { do { while (!workers.isEmpty()) workers.removeFirst().run(); flushUi(); } while (!workers.isEmpty()); }

    static Nodes.Form form(dev.jasper.sdk.ui.UiView view) { return node(view, "import", Nodes.Form.class); }

    @Test void enterUnlocksOnlyFromThePassphraseFieldAndNeverCommits() throws Exception {
        edt(() -> {
            setup();
            var key = new KeyGenerator(directory.resolve("keys")).generate(KeyAlgorithm.ED25519, "fixture", "test", "synthetic-key-phrase".toCharArray());
            UUID request = UUID.randomUUID();
            var result = api.importSshKeys(owner, List.of(new SshKeySource(request, "fixture", key.privatePath())), List.of());
            flushUi(); drain(reads);
            var prompt = prompts.getFirst();
            assertThat(prompt.phase()).isEqualTo(KeyImportPrompt.Phase.PASSPHRASE);
            var kit = new FakeKit("dev.jasper.vault");
            var panel = new KeyImportPanel(kit, prompt); var view = panel.view();
            assertThat(form(view).kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form(view).actions()).as("no default: Enter outside the passphrase field does nothing, as at baseline")
                .noneMatch(b -> b.kind() == Nodes.ButtonKind.PRIMARY);
            assertThat(button(view, "unlock-key").label()).isEqualTo("Unlock key");
            assertThat(node(view, "passphrase", Nodes.Secret.class).submitAction()).as("Enter in the passphrase field unlocks").isEqualTo("unlock-key");
            assertThat(button(view, "commit").kind()).isEqualTo(Nodes.ButtonKind.SECONDARY);
            assertThat(button(view, "commit").state().enabled()).isFalse();
            assertThat(node(view, "passphrase", Nodes.Secret.class).state().visible()).isTrue();
            assertThat(form(view).cancelAction()).isEqualTo("cancel");
            assertThat(fake(view).focused()).contains("passphrase");
            type(view, "passphrase", "synthetic-key-phrase");
            assertThat(view.spec().toString()).doesNotContain("synthetic-key-phrase");
            press(view, "unlock-key");
            assertThat(secret(view, "passphrase")).as("the field is erased once the prompt owns its copy").isEmpty();
            drain(reads);
            assertThat(prompt.phase()).isEqualTo(KeyImportPrompt.Phase.REVIEW);
            assertThat(form(view).actions()).as("Enter never commits an import").noneMatch(b -> b.kind() == Nodes.ButtonKind.PRIMARY);
            assertThat(button(view, "commit").state().enabled()).isTrue();
            assertThat(node(view, "passphrase", Nodes.Secret.class).state().visible()).isFalse();
            var keys = node(view, "keys", Nodes.ItemList.class);
            assertThat(keys.items()).hasSize(1);
            assertThat(((Nodes.Text) keys.items().getFirst().title().getFirst()).text()).isEqualTo("fixture");
            assertThat(((Nodes.Chip) keys.items().getFirst().trailing().getFirst()).label()).isEqualTo("new");
            press(view, "commit"); drain(writes);
            assertThat(result.join()).isPresent();
            panel.close();
            assertThat(closed(view)).isTrue();
            assertThat(kit.failures()).isEmpty();
            lock.lock();
        });
    }

    @Test void cancelWhileAPassphraseIsWantedAnswersEmptyAndErasesTheField() throws Exception {
        edt(() -> {
            setup();
            var key = new KeyGenerator(directory.resolve("keys")).generate(KeyAlgorithm.ED25519, "fixture", "test", "synthetic-key-phrase".toCharArray());
            var result = api.importSshKeys(owner, List.of(new SshKeySource(UUID.randomUUID(), "fixture", key.privatePath())), List.of());
            flushUi(); drain(reads);
            var panel = new KeyImportPanel(new FakeKit("dev.jasper.vault"), prompts.getFirst()); var view = panel.view();
            type(view, "passphrase", "typed-then-cancelled");
            press(view, form(view).cancelAction()); flushUi();
            assertThat(result.join()).isEmpty();
            assertThat(secret(view, "passphrase")).isEmpty();
            panel.close(); lock.lock();
        });
    }
}
