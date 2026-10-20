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
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class KeyGeneratorFormTest {
    @TempDir Path directory;
    @Test void cheapReadinessRetainsOptionalAccountDraftAndMatchingPassphrase() throws Exception {
        edt(()->{
            var form=new KeyGeneratorForm(new FakeKit("dev.jasper.vault"),value->{throw new AssertionError("No generation on edits");},()->{});var view=form.view();
            assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
            emit(view,new UiEvent.Edited(KeyGeneratorForm.NAME,"Laptop"));assertThat(button(view,EditorForm.SAVE).state().enabled()).isTrue();
            emit(view,new UiEvent.Toggled(KeyGeneratorForm.ACCOUNT,true));assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
            emit(view,new UiEvent.Edited(KeyGeneratorForm.USERNAME,"deploy"));emit(view,new UiEvent.Toggled(KeyGeneratorForm.ACCOUNT,false));
            assertThat(node(view,KeyGeneratorForm.USERNAME,Nodes.Field.class).value()).isEqualTo("deploy");
            type(view,KeyGeneratorForm.PASSPHRASE,"phrase");assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
            type(view,KeyGeneratorForm.CONFIRM,"phrase");assertThat(button(view,EditorForm.SAVE).state().enabled()).isTrue();
            emit(view,new UiEvent.Edited(KeyGeneratorForm.COMMENT,"line one\nline two"));assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();form.close();
        });
    }
    @Test void formDefaultsToEd25519AndRequiresAUsernameOnlyWhenAddingAnAccount() throws Exception {
        edt(() -> {
            var request = new AtomicReference<KeyGeneratorForm.Request>();
            var form = new KeyGeneratorForm(new FakeKit("dev.jasper.vault"), value -> { request.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
            var view = form.view();
            assertThat(button(view, EditorForm.SAVE).label()).isEqualTo("Generate Key");
            emit(view, new UiEvent.Edited(KeyGeneratorForm.NAME, "Laptop")); emit(view, new UiEvent.Toggled(KeyGeneratorForm.ACCOUNT, true));
            press(view, EditorForm.SAVE);
            assertThat(request.get()).isNull(); assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
            emit(view, new UiEvent.Edited(KeyGeneratorForm.USERNAME, "deploy")); press(view, EditorForm.SAVE);
            assertThat(request.get().algorithm()).isEqualTo(KeyAlgorithm.ED25519);
            assertThat(request.get().username()).contains("deploy");
            assertThat(closed(view)).as("a successful generation closes the form").isTrue();
        });
    }

    @Test void theKeyFilesNoteAndPairedPassphraseFieldsAreDescribed() throws Exception {
        edt(() -> {
            var form = new KeyGeneratorForm(new FakeKit("dev.jasper.vault"), value -> CompletableFuture.completedFuture(null), () -> { });
            var view = form.view();
            var description = node(view, KeyGeneratorForm.KEY_FILES, Nodes.Text.class);
            assertThat(description.text()).contains("unencrypted"); assertThat(description.role()).isEqualTo(Nodes.TextRole.DESCRIPTION);
            assertThat(node(view, KeyGeneratorForm.PASSPHRASE, Nodes.Secret.class).revealGroup())
                .isEqualTo(node(view, KeyGeneratorForm.CONFIRM, Nodes.Secret.class).revealGroup()).isNotBlank();
            assertThat(node(view, KeyGeneratorForm.ACCOUNT, Nodes.Toggle.class).checked()).isFalse();
            form.close();
        });
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
    @Test void passphraseConfirmationAndRequestBuffersAreWiped() throws Exception {
        edt(() -> {
            var held = new AtomicReference<char[]>();
            var completion = new CompletableFuture<Void>();
            var form = new KeyGeneratorForm(new FakeKit("dev.jasper.vault"), request -> {
                assertThat(request.passphrase()).isEqualTo(" phrase ".toCharArray());
                held.set(request.passphrase()); return completion;
            }, () -> { });
            var view = form.view();
            emit(view, new UiEvent.Edited(KeyGeneratorForm.NAME, "Encrypted"));
            type(view, KeyGeneratorForm.PASSPHRASE, " phrase "); type(view, KeyGeneratorForm.CONFIRM, "different");
            press(view, EditorForm.SAVE);
            assertThat(held.get()).isNull(); assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
            type(view, KeyGeneratorForm.CONFIRM, " phrase "); press(view, EditorForm.SAVE);
            assertThat(held.get()).containsOnly((char) 0);
            assertThat(button(view, EditorForm.CANCEL).state().enabled()).as("generation cannot be cancelled midway").isFalse();
            assertThat(node(view, EditorForm.FORM, Nodes.Form.class).cancelAction()).as("nor escaped").isEmpty();
            form.close();
            assertThat(closed(view)).isTrue();
            completion.complete(null);
        });
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
