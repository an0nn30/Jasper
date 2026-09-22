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
    @Test void everyKeyPathHasAnInlineChooserAndCancelPreservesManualInput(@TempDir Path directory) {
        Path typed = directory.resolve("typed"), chosen = directory.resolve("chosen key"), recovered = directory.resolve("recovered");
        var key = EntryEditor.key(null, value -> CompletableFuture.completedFuture(null), () -> { });
        var login = EntryEditor.login(null, value -> CompletableFuture.completedFuture(null), () -> { });
        for (var form : java.util.List.of(key, login)) {
            var paths = form == key ? java.util.List.of(form.privatePath, form.publicPath) : java.util.List.of(form.privatePath);
            for (var path : paths) {
                var browse = (javax.swing.JButton) path.getParent().getComponent(1);
                assertThat(browse.getText()).isEqualTo("Browse...");
                path.setText(typed.toString());
                form.setFileChooser((title, initial) -> {
                    assertThat(initial).contains(typed);
                    return java.util.Optional.of(chosen);
                });
                browse.doClick();
                assertThat(path.getText()).isEqualTo(chosen.toString());
                form.setFileChooser((title, initial) -> java.util.Optional.empty());
                browse.doClick();
                assertThat(path.getText()).isEqualTo(chosen.toString());
                path.setText("invalid\0path");
                form.setFileChooser((title, initial) -> {
                    assertThat(initial).isEmpty();
                    return java.util.Optional.of(recovered);
                });
                browse.doClick();
                assertThat(path.getText()).isEqualTo(recovered.toString());
            }
            form.close();
        }
    }

    @Test void aPickerResultAfterFormClosureIsIgnored() {
        var form = EntryEditor.key(null, value -> CompletableFuture.completedFuture(null), () -> { });
        form.privatePath.setText("/keys/original");
        form.setFileChooser((title, initial) -> {
            form.close();
            return java.util.Optional.of(Path.of("/keys/late"));
        });
        ((javax.swing.JButton) form.privatePath.getParent().getComponent(1)).doClick();
        assertThat(form.privatePath.getText()).isEqualTo("/keys/original");
    }

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

    private static ChangePasswordForm passwordChange(VaultUiFixture f) {
        var form = new ChangePasswordForm(f.lock, () -> { });
        form.current.replace("test-password".toCharArray());
        form.replacement.replace("new-password".toCharArray());
        form.confirm.replace("new-password".toCharArray());
        return form;
    }

    @Test void passwordChangeCanRetryTheSameOldPasswordAfterWriteFailure(@TempDir Path directory) throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            var form = passwordChange(f);
            Path obstruction = java.nio.file.Files.createDirectory(directory.resolve("vault.jv.tmp"));
            form.save.doClick(); f.drain();
            assertThat(form.save.isEnabled()).isTrue();
            java.nio.file.Files.delete(obstruction);
            form.save.doClick(); f.drain();
            assertThat(form.current.snapshot()).as("successful retry closes and wipes the form").isEmpty();
            f.lock.lock();
            var opened = f.lock.unlock("new-password".toCharArray()); f.drain();
            assertThat(opened).isCompletedWithValue(null);
        }
    }

    @Test void cancellingOrClosingBeforePasswordCommitKeepsTheOldPassword(@TempDir Path directory) {
        for (int mode = 0; mode < 3; mode++) {
            try (var f = new VaultUiFixture(directory.resolve("case-" + mode))) {
                var form = passwordChange(f); form.save.doClick();
                assertThat(f.queue).hasSize(1);
                if (mode == 0) form.cancel.doClick(); else form.close();
                if (mode == 2) f.lock.lock(); // Plugin shutdown closes forms, then locks.
                f.drain();
                assertThat(form.current.snapshot()).isEmpty();
                assertThat(form.replacement.snapshot()).isEmpty();
                f.lock.lock();
                var opened = f.lock.unlock("test-password".toCharArray()); f.drain();
                assertThat(opened).as("close mode %s", mode).isCompletedWithValue(null);
            }
        }
    }

    @Test void closingAfterPasswordCommitStartsIsClearlyDifferentFromCancel(@TempDir Path directory) {
        try (var f = new VaultUiFixture(directory)) {
            var form = passwordChange(f); form.save.doClick();
            f.queue.removeFirst().run(); // Derivation completes; the file write is now committed to run.
            assertThat(form.cancel.getText()).isEqualTo("Close");
            assertThat(form.error.getText()).contains("closing will not cancel");
            form.close(); f.lock.lock(); f.drain();
            assertThat(f.lock.state()).isEqualTo(dev.jasper.vault.api.LockState.LOCKED);
            var opened = f.lock.unlock("new-password".toCharArray()); f.drain();
            assertThat(opened).isCompletedWithValue(null);
        }
    }

    @Test void passwordWriteFailureIsReportedEvenIfTheFormWasClosed(@TempDir Path directory) throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            var reported = new AtomicReference<Throwable>();
            var form = new ChangePasswordForm(f.lock, () -> { }, reported::set);
            form.current.replace("test-password".toCharArray());
            form.replacement.replace("new-password".toCharArray());
            form.confirm.replace("new-password".toCharArray());
            java.nio.file.Files.createDirectory(directory.resolve("vault.jv.tmp"));
            form.save.doClick(); f.queue.removeFirst().run();
            form.close(); f.drain();
            assertThat(reported.get()).isNotNull();
            f.lock.lock();
            var opened = f.lock.unlock("test-password".toCharArray()); f.drain();
            assertThat(opened).isCompletedWithValue(null);
        }
    }
}
