package dev.jasper.vault.ui;

import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.model.Auth;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KeyGeneratorFormTest {
    @TempDir Path directory;
    @Test void formDefaultsToEd25519AndRequiresAUsernameOnlyWhenAddingAnAccount() {
        var request = new AtomicReference<KeyGeneratorForm.Request>();
        var form = new KeyGeneratorForm(value -> { request.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        form.name.setText("Laptop"); form.alsoAccount.setSelected(true); form.save.doClick();
        assertThat(request.get()).isNull(); assertThat(form.error.getText()).contains("username");
        form.username.setText("deploy"); form.save.doClick();
        assertThat(request.get().algorithm()).isEqualTo(KeyAlgorithm.ED25519);
        assertThat(request.get().username()).contains("deploy");
        assertThat(form.description.getText()).contains("unencrypted");
    }

    @Test void generationAddsKeyAndOptionalAccountInOnePersistedEdit() {
        try (var f = new VaultUiFixture(directory)) {
            var generated = f.manager.generate(directory.resolve("keys"), KeyAlgorithm.ED25519, "Laptop", "test", Optional.of("deploy"));
            assertThat(f.manager.busy()).isTrue(); assertThat(f.manager.rows()).isEmpty();
            f.drain(); generated.join();
            assertThat(f.lock.vault().keys()).hasSize(1);
            assertThat(f.lock.vault().accounts()).singleElement().satisfies(account -> {
                assertThat(account.username()).isEqualTo("deploy");
                assertThat(((Auth.Key) account.auth()).passphrase()).isNull();
                assertThat(((Auth.Key) account.auth()).keyPath()).isEqualTo(f.lock.vault().keys().getFirst().privatePath());
            });
            f.lock.lock();
            var unlock = f.lock.unlock("test-password".toCharArray()); f.drain(); unlock.join();
            assertThat(f.lock.vault().keys()).hasSize(1); assertThat(f.lock.vault().accounts()).hasSize(1);
        }
    }

    @Test void lockingDuringGenerationDiscardsTheResultAndRemovesItsFiles() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Path keys = directory.resolve("keys");
            char[] phrase = "cancelled-phrase".toCharArray();
            var generated = f.manager.generate(keys, KeyAlgorithm.ED25519, "Laptop", "", Optional.empty(), phrase);
            assertThat(phrase).containsOnly((char) 0);
            f.manager.invalidate(); f.lock.lock(); f.drain();
            assertThat(generated).isCompletedExceptionally();
            assertThat(f.manager.rows()).isEmpty();
            try (var files = Files.list(keys)) { assertThat(files.toList()).isEmpty(); }
        }
    }

    @Test void failedVaultSaveRemovesBothGeneratedFilesAndBothModelEntries() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Files.delete(directory.resolve("vault.jv")); Files.createDirectory(directory.resolve("vault.jv"));
            Files.writeString(directory.resolve("vault.jv/block"), "block replacement");
            Path keys = directory.resolve("keys");
            char[] phrase = "failed-save-phrase".toCharArray();
            var generated = f.manager.generate(keys, KeyAlgorithm.ED25519, "Laptop", "", Optional.of("deploy"), phrase); f.drain();
            assertThat(phrase).containsOnly((char) 0);
            assertThat(generated).isCompletedExceptionally();
            assertThat(f.manager.rows()).isEmpty();
            try (var files = Files.list(keys)) { assertThat(files.toList()).isEmpty(); }
        }
    }
    @Test void passphraseConfirmationAndRequestBuffersAreWiped() {
        var held = new AtomicReference<char[]>();
        var completion = new CompletableFuture<Void>();
        var form = new KeyGeneratorForm(request -> {
            assertThat(request.passphrase()).isEqualTo(" phrase ".toCharArray());
            held.set(request.passphrase()); return completion;
        }, () -> { });
        form.name.setText("Encrypted");
        form.passphrase.replace(" phrase ".toCharArray()); form.confirm.replace("different".toCharArray());
        form.save.doClick();
        assertThat(held.get()).isNull(); assertThat(form.error.getText()).contains("do not match");
        form.confirm.replace(" phrase ".toCharArray()); form.save.doClick();
        assertThat(held.get()).containsOnly((char) 0);
        form.close();
        assertThat(form.passphrase.snapshot()).isEmpty(); assertThat(form.confirm.snapshot()).isEmpty();
        completion.complete(null);
    }

    @Test void encryptedGenerationStoresThePassphraseOnlyInTheOptionalVaultAccount() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            char[] phrase = "stored-phrase".toCharArray();
            var generated = f.manager.generate(directory.resolve("keys"), KeyAlgorithm.ED25519, "Laptop", "", Optional.of("deploy"), phrase);
            assertThat(phrase).containsOnly((char) 0);
            f.drain(); generated.join();
            char[] saved = ((Auth.Key) f.lock.vault().accounts().getFirst().auth()).passphrase();
            assertThat(saved).isEqualTo("stored-phrase".toCharArray());
            f.lock.lock(); assertThat(saved).containsOnly((char) 0);
            var unlock = f.lock.unlock("test-password".toCharArray()); f.drain(); unlock.join();
            assertThat(((Auth.Key) f.lock.vault().accounts().getFirst().auth()).passphrase()).isEqualTo("stored-phrase".toCharArray());
        }
    }

    @Test void rejectedGenerationStillClearsItsPassphrase() {
        try (var f = new VaultUiFixture(directory)) {
            char[] phrase = "discard-me".toCharArray(); f.lock.lock();
            assertThat(f.manager.generate(directory.resolve("keys"), KeyAlgorithm.ED25519, "Laptop", "", Optional.empty(), phrase))
                .isCompletedExceptionally();
            assertThat(phrase).containsOnly((char) 0);
        }
    }

}
