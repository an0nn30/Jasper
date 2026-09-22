package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class EntryEditorTest {
    @Test void noteEditingPreservesMultilineUnicodeAndClearsOnClose() {
        var received = new AtomicReference<Note>();
        char[] original = "first\nsecond \u03bb".toCharArray();
        var note = new Note(UUID.randomUUID(), "Recovery", original, Instant.EPOCH);
        var form = EntryEditor.note(note, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        char[] snapshot = form.noteText.snapshot();
        assertThat(snapshot).isEqualTo(original); Arrays.fill(snapshot, (char) 0);
        form.save.doClick();
        assertThat(received.get().id()).isEqualTo(note.id());
        assertThat(received.get().text()).isEqualTo(original);
        assertThat(form.noteText.snapshot()).isEmpty();
        assertThat(original).as("editor borrows then copies, it does not wipe the vault record").isEqualTo("first\nsecond \u03bb".toCharArray());
        received.get().zero(); note.zero();
    }

    @Test void changingAuthKindsRetainsOnlyTheChosenSecretFields() {
        var received = new AtomicReference<Account>();
        var form = EntryEditor.login(null, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        form.name.setText("Production"); form.username.setText("deploy");
        form.password.replace("password".toCharArray()); form.passphrase.replace("phrase".toCharArray());
        form.privatePath.setText("/keys/deploy"); form.auth.setSelectedIndex(1);
        form.save.doClick();
        assertThat(received.get().auth()).isInstanceOf(Auth.Key.class);
        assertThat(((Auth.Key) received.get().auth()).passphrase()).isEqualTo("phrase".toCharArray());
        assertThat(form.password.snapshot()).isEmpty(); assertThat(form.passphrase.snapshot()).isEmpty();
        received.get().auth().zero();
    }

    @Test void failedSaveAllowsRetryAndClosingWipesDraft() {
        var completion = new CompletableFuture<Void>();
        var form = EntryEditor.note(null, value -> { value.zero(); return completion; }, () -> { });
        form.name.setText("Recovery"); form.noteText.replace("secret".toCharArray());
        form.save.doClick();
        assertThat(form.save.isEnabled()).isFalse();
        completion.completeExceptionally(new java.io.IOException("Disk full"));
        assertThat(form.save.isEnabled()).isTrue();
        assertThat(form.error.getText()).contains("Disk full");
        form.close();
        assertThat(form.noteText.snapshot()).isEmpty();
    }

    @Test void documentReplacesWithoutKeepingDeletedBuffers() throws Exception {
        var document = new SecretDocument();
        document.replace("secret".toCharArray());
        var segment = new javax.swing.text.Segment();
        document.getText(0, document.getLength(), segment);
        char[] old = segment.array;
        document.remove(1, 3);
        assertThat(old).containsOnly((char) 0);
        assertThat(document.snapshot()).containsExactly('s', 'e', 't');
        document.clear();
        assertThat(document.snapshot()).isEmpty();
    }

    @Test void passwordMismatchDoesNotStartAChangeAndCloseClearsEveryField(@TempDir Path directory) {
        try (var f = new VaultUiFixture(directory)) {
            var form = new ChangePasswordForm(f.lock, () -> { });
            form.current.replace("test-password".toCharArray());
            form.replacement.replace("new-password".toCharArray());
            form.confirm.replace("different".toCharArray());
            form.save.doClick();
            assertThat(form.error.getText()).contains("do not match");
            assertThat(f.queue).isEmpty();
            form.close();
            assertThat(form.current.snapshot()).isEmpty();
            assertThat(form.replacement.snapshot()).isEmpty();
            assertThat(form.confirm.snapshot()).isEmpty();
        }
    }

    @Test void importedPublicKeyMetadataIsDerivedAndThePrivateFileIsUnchanged(@TempDir Path directory) throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            SshKey generated = new KeyGenerator(directory.resolve("keys")).generate(KeyAlgorithm.ED25519, "Laptop", "test");
            byte[] before = java.nio.file.Files.readAllBytes(generated.privatePath());
            SshKey selected = new SshKey(UUID.randomUUID(), "Imported", "", "", "", generated.privatePath(), generated.publicPath(), Instant.EPOCH);
            var imported = f.manager.importKey(selected); f.drain(); imported.join();
            SshKey saved = (SshKey) f.manager.entry(selected.id()).orElseThrow();
            assertThat(saved.algorithm()).isEqualTo("ssh-ed25519");
            assertThat(saved.fingerprint()).isEqualTo(generated.fingerprint());
            assertThat(java.nio.file.Files.readAllBytes(generated.privatePath())).isEqualTo(before);
            Arrays.fill(before, (byte) 0);
        }
    }
}
