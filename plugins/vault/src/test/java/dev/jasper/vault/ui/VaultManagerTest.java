package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultManagerTest {
    @TempDir Path directory;

    static Account account(UUID id, char[] password) {
        return new Account(id, "Production", "deploy", new Auth.Password(password), Instant.EPOCH, Instant.EPOCH);
    }

    @Test void editsSaveAndDeleteRemovesGrantsAndSecrets() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            f.manager.saveAccount(account(id, fresh)); f.drain();
            assertThat(old).containsOnly((char) 0);
            assertThat(f.manager.rows()).singleElement().satisfies(row -> assertThat(row.subtitle()).isEqualTo("deploy"));
            f.lock.vault().grants().add(new Grant("dev.jasper.ssh", id));
            f.manager.delete(id, false); f.drain();
            assertThat(fresh).containsOnly((char) 0);
            assertThat(f.manager.rows()).isEmpty();
            assertThat(f.manager.grants()).isEmpty();
            f.lock.lock();
            var unlock = f.lock.unlock("test-password".toCharArray()); f.drain(); unlock.join();
            assertThat(f.manager.rows()).isEmpty();
        }
    }

    @Test void failedReplacementRestoresOldRecordAndWipesRejectedSecret() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            Files.delete(directory.resolve("vault.jv"));
            Files.createDirectory(directory.resolve("vault.jv"));
            Files.writeString(directory.resolve("vault.jv/block"), "block replacement");
            var save = f.manager.saveAccount(account(id, fresh)); f.drain();
            assertThat(save).isCompletedExceptionally();
            assertThat(((Auth.Password) f.lock.vault().account(id).orElseThrow().auth()).password()).isEqualTo("old".toCharArray());
            assertThat(fresh).containsOnly((char) 0);
            assertThat(old).as("input consumed into a private transaction copy").containsOnly((char) 0);
        }
    }

    @Test void lockingWhileASaveWaitsWipesBothVersionsWithoutRestoringEither() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            f.manager.saveAccount(account(id, fresh));
            f.manager.invalidate(); f.lock.lock();
            assertThat(old).containsOnly((char) 0);
            assertThat(fresh).containsOnly((char) 0);
            f.drain();
            assertThat(f.manager.rows()).isEmpty();
        }
    }

    @Test void notesAreManagerOnlyAndRevocationPersists() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            f.manager.saveNote(new Note(id, "Recovery", "a\nb".toCharArray(), Instant.EPOCH)); f.drain();
            Grant grant = new Grant("dev.jasper.ssh", id);
            f.lock.vault().grants().add(grant);
            f.manager.revoke(grant); f.drain();
            assertThat(f.manager.rows().getFirst().type()).isEqualTo(VaultManager.Type.NOTE);
            assertThat(f.manager.grants()).isEmpty();
        }
    }

    @Test void deletingAKeyKeepsFilesUnlessExplicitlySelected() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Path privatePath = directory.resolve("key"), publicPath = directory.resolve("key.pub");
            Files.writeString(privatePath, "private fixture"); Files.writeString(publicPath, "public fixture");
            SshKey key = new SshKey(UUID.randomUUID(), "Laptop", "ed25519", "SHA256:test", "",
                privatePath, publicPath, Instant.EPOCH);
            f.manager.saveKey(key); f.drain();
            f.manager.delete(key.id(), false); f.drain();
            assertThat(privatePath).exists(); assertThat(publicPath).exists();
            f.manager.saveKey(key); f.drain();
            var deletion = f.manager.delete(key.id(), true); f.drain(); deletion.join();
            assertThat(privatePath).doesNotExist(); assertThat(publicPath).doesNotExist();
        }
    }

    @Test void overlappingEditsRejectAndWipeTheRejectedInput() {
        try (var f = new VaultUiFixture(directory)) {
            f.manager.saveNote(new Note(UUID.randomUUID(), "One", new char[] {'1'}, Instant.EPOCH));
            char[] rejected = {'2'};
            assertThat(f.manager.saveNote(new Note(UUID.randomUUID(), "Two", rejected, Instant.EPOCH))).isCompletedExceptionally();
            assertThat(rejected).containsOnly((char) 0);
            f.drain();
        }
    }
}
