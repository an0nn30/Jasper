package dev.jasper.vault.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

class EntryEditorTest {
    static FakeKit kit() { return new FakeKit("dev.jasper.vault"); }

    @Test void companionReadinessTracksSecretEditsAndFullWidthInstructions(@TempDir Path directory) throws Exception {
        edt(()->{
            try(var f=new VaultUiFixture(directory)) {
                var form=new ChangePasswordForm(kit(),f.lock,()->{});var view=form.view();
                assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
                type(view,ChangePasswordForm.CURRENT,"current");type(view,ChangePasswordForm.REPLACEMENT,"replacement");type(view,ChangePasswordForm.CONFIRM,"replacement");
                assertThat(button(view,EditorForm.SAVE).state().enabled()).isTrue();
                type(view,ChangePasswordForm.CONFIRM,"different");assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
                assertThat(f.queue).isEmpty();form.close();
                var prompt=new EditorForm(kit(),()->{});prompt.description("instruction","","Remove this entry? A long literal item name");
                assertThat(node(prompt.view(),"fields",Nodes.Card.class).children().getFirst()).isInstanceOf(Nodes.Text.class);prompt.close();
            }
        });
    }

    @Test void twoAuthenticationSegmentsKeepHiddenDraftsAndSubmitCombinedOnlyWhenChosen() throws Exception {
        edt(() -> {
            var received = new AtomicReference<Account>();
            var form = EntryEditor.login(kit(), null, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> {});
            var view = form.view();
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).kind()).isEqualTo(Nodes.ChoiceKind.EXCLUSIVE);
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).options()).extracting(o -> o.label()).containsExactly("Password", "Private Key");
            assertThat(node(view, EntryEditor.NAME, Nodes.Field.class).hint()).isNotBlank();
            assertThat(node(view, EntryEditor.NAME, Nodes.Field.class).value()).isEmpty();
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isFalse();
            assertThat(node(view, EntryEditor.PRIVATE_PATH, Nodes.Field.class).state().visible()).isFalse();
            emit(view, new UiEvent.Edited(EntryEditor.NAME, "Draft")); type(view, EntryEditor.PASSWORD, "retained-password");
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isTrue();
            emit(view, new UiEvent.Selected(EntryEditor.AUTH, Set.of(EntryEditor.AUTH_KEY)));
            assertThat(node(view, EntryEditor.PASSWORD, Nodes.Secret.class).state().visible()).isFalse();
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isFalse();
            emit(view, new UiEvent.Edited(EntryEditor.PRIVATE_PATH, "/synthetic/nonexistent-key")); type(view, EntryEditor.PASSPHRASE, "retained-phrase");
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isTrue();
            emit(view, new UiEvent.Toggled("also-password", true));
            assertThat(node(view, EntryEditor.PASSWORD, Nodes.Secret.class).state().visible()).isTrue();
            emit(view, new UiEvent.Selected(EntryEditor.AUTH, Set.of(EntryEditor.AUTH_PASSWORD)));
            emit(view, new UiEvent.Selected(EntryEditor.AUTH, Set.of(EntryEditor.AUTH_KEY)));
            assertThat(secret(view, EntryEditor.PASSWORD)).isEqualTo("retained-password");
            assertThat(secret(view, EntryEditor.PASSPHRASE)).isEqualTo("retained-phrase");
            assertThat(node(view, "also-password", Nodes.Toggle.class).checked()).isTrue();
            press(view, EditorForm.SAVE);
            assertThat(received.get().auth()).isInstanceOfSatisfying(Auth.KeyAndPassword.class, auth -> {
                assertThat(auth.password()).isEqualTo("retained-password".toCharArray());
                assertThat(auth.passphrase()).isEqualTo("retained-phrase".toCharArray());
            });
            assertThat(closed(view)).isTrue(); received.get().auth().zero();
        });
    }

    @Test void pathReadinessIsCheapAndTheKeyUsesCanonicalLabelledCardRows() throws Exception {
        edt(() -> {
            var form = EntryEditor.key(kit(), null, value -> CompletableFuture.completedFuture(null), () -> {});
            var view = form.view();
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isFalse();
            assertThat(node(view, "row.name", Nodes.FormRow.class).label()).isEqualTo("Name");
            emit(view, new UiEvent.Edited(EntryEditor.NAME, "Key"));
            emit(view, new UiEvent.Edited(EntryEditor.PRIVATE_PATH, "/synthetic/absent"));
            emit(view, new UiEvent.Edited(EntryEditor.PUBLIC_PATH, "/synthetic/absent.pub"));
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isTrue();
            emit(view, new UiEvent.Edited(EntryEditor.PUBLIC_PATH, "bad\0path"));
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isFalse();
            form.close();
        });
    }

    @Test void workerSaveCompletionReturnsToUiAndClosedDraftIgnoresLateCompletion() throws Exception {
        for(boolean cancel: new boolean[]{false,true}){
            var pending=new CompletableFuture<Void>();var form=new AtomicReference<EntryEditor>();var dismissed=new java.util.concurrent.atomic.AtomicInteger();var offUi=new java.util.concurrent.atomic.AtomicBoolean();
            edt(()->{
                var entry=EntryEditor.login(kit(),null,value->{value.auth().zero();return pending;},()->{if(!javax.swing.SwingUtilities.isEventDispatchThread())offUi.set(true);dismissed.incrementAndGet();});form.set(entry);
                var view=entry.view();emit(view,new UiEvent.Edited(EntryEditor.NAME,"synthetic"));emit(view,new UiEvent.Edited(EntryEditor.USERNAME,"fixture"));type(view,EntryEditor.PASSWORD,"synthetic-secret");press(view,EditorForm.SAVE);
                if(cancel)press(view,EditorForm.CANCEL);
            });
            CompletableFuture.runAsync(()->pending.complete(null)).join();edt(()->{});
            assertThat(offUi).isFalse();assertThat(dismissed).hasValue(1);edt(()->assertThat(form.get().closed()).isTrue());
        }
    }
    @Test void saveIsTheFormsOnlyPrimaryAndEscapeCancels() throws Exception {
        edt(() -> {
            var form = EntryEditor.key(kit(), null, value -> CompletableFuture.completedFuture(null), () -> { });
            var node = node(form.view(), EditorForm.FORM, Nodes.Form.class);
            assertThat(node.actions()).filteredOn(b -> b.kind() == Nodes.ButtonKind.PRIMARY).singleElement()
                .satisfies(b -> assertThat(b.id()).isEqualTo(EditorForm.SAVE));
            assertThat(node.cancelAction()).isEqualTo(EditorForm.CANCEL);
            form.close();
        });
    }

    @Test void everyKeyPathHasAnInlineChooserAndCancelPreservesManualInput(@TempDir Path directory) throws Exception {
        edt(() -> {
            Path typed = directory.resolve("typed"), chosen = directory.resolve("chosen key"), recovered = directory.resolve("recovered");
            var key = EntryEditor.key(kit(), null, value -> CompletableFuture.completedFuture(null), () -> { });
            var login = EntryEditor.login(kit(), null, value -> CompletableFuture.completedFuture(null), () -> { });
            for (var form : java.util.List.of(key, login)) {
                var view = form.view();
                var paths = form == key ? java.util.List.of(EntryEditor.PRIVATE_PATH, EntryEditor.PUBLIC_PATH) : java.util.List.of(EntryEditor.PRIVATE_PATH);
                for (var path : paths) {
                    var field = node(view, path, Nodes.Field.class);
                    assertThat(field.kind()).isEqualTo(Nodes.FieldKind.PATH);
                    assertThat(field.trailingAction()).isEqualTo(EditorForm.BROWSE + path);
                    emit(view, new UiEvent.Edited(path, typed.toString()));
                    form.setFileChooser((title, initial) -> {
                        assertThat(initial).contains(typed);
                        return java.util.Optional.of(chosen);
                    });
                    press(view, field.trailingAction());
                    assertThat(node(view, path, Nodes.Field.class).value()).isEqualTo(chosen.toString());
                    form.setFileChooser((title, initial) -> java.util.Optional.empty());
                    press(view, field.trailingAction());
                    assertThat(form.value(path)).isEqualTo(chosen.toString());
                    emit(view, new UiEvent.Edited(path, "invalid\0path"));
                    form.setFileChooser((title, initial) -> {
                        assertThat(initial).isEmpty();
                        return java.util.Optional.of(recovered);
                    });
                    press(view, field.trailingAction());
                    assertThat(node(view, path, Nodes.Field.class).value()).isEqualTo(recovered.toString());
                }
                form.close();
            }
        });
    }

    @Test void aPickerResultAfterFormClosureIsIgnored() throws Exception {
        edt(() -> {
            var kit = new RecordingKit("dev.jasper.vault");
            var form = EntryEditor.key(kit, null, value -> CompletableFuture.completedFuture(null), () -> { });
            emit(form.view(), new UiEvent.Edited(EntryEditor.PRIVATE_PATH, "/keys/original"));
            form.setFileChooser((title, initial) -> {
                form.close();
                return java.util.Optional.of(Path.of("/keys/late"));
            });
            press(form.view(), EditorForm.BROWSE + EntryEditor.PRIVATE_PATH);
            assertThat(form.value(EntryEditor.PRIVATE_PATH)).isEqualTo("/keys/original");
            assertThat(kit.lateCalls).isEmpty();
        });
    }

    @Test void noteEditingPreservesMultilineUnicodeAndClearsOnClose() throws Exception {
        edt(() -> {
            var received = new AtomicReference<Note>();
            char[] original = "first\nsecond λ".toCharArray();
            var note = new Note(UUID.randomUUID(), "Recovery", original, Instant.EPOCH);
            boolean[] dismissed = {false};
            var form = EntryEditor.note(kit(), note, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> dismissed[0] = true);
            var view = form.view();
            assertThat(node(view, EntryEditor.NOTE, Nodes.Secret.class).kind()).isEqualTo(Nodes.SecretKind.MULTILINE);
            assertThat(secret(view, EntryEditor.NOTE)).isEqualTo("first\nsecond λ");
            assertThat(view.spec().toString()).doesNotContain("second");
            press(view, EditorForm.SAVE);
            assertThat(received.get().id()).isEqualTo(note.id());
            assertThat(received.get().text()).isEqualTo(original);
            assertThat(dismissed[0]).isTrue();
            assertThat(closed(view)).as("a successful save closes the form and its secret storage").isTrue();
            assertThat(original).as("editor borrows then copies, it does not wipe the vault record").isEqualTo("first\nsecond λ".toCharArray());
            received.get().zero(); note.zero();
        });
    }

    @Test void changingAuthKindsRetainsOnlyTheChosenSecretFields() throws Exception {
        edt(() -> {
            var received = new AtomicReference<Account>();
            var form = EntryEditor.login(kit(), null, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
            var view = form.view();
            emit(view, new UiEvent.Edited(EntryEditor.NAME, "Production")); emit(view, new UiEvent.Edited(EntryEditor.USERNAME, "deploy"));
            type(view, EntryEditor.PASSWORD, "password"); type(view, EntryEditor.PASSPHRASE, "phrase");
            emit(view, new UiEvent.Edited(EntryEditor.PRIVATE_PATH, "/keys/deploy"));
            emit(view, new UiEvent.Selected(EntryEditor.AUTH, Set.of(EntryEditor.AUTH_KEY)));
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).selectedId()).isEqualTo(EntryEditor.AUTH_KEY);
            press(view, EditorForm.SAVE);
            assertThat(received.get().auth()).isInstanceOf(Auth.Key.class);
            assertThat(((Auth.Key) received.get().auth()).passphrase()).isEqualTo("phrase".toCharArray());
            assertThat(closed(view)).isTrue();
            received.get().auth().zero();
        });
    }

    @Test void editingAnAccountCopiesItsSecretsIntoTheFormWithoutWipingTheRecord() throws Exception {
        edt(() -> {
            char[] password = "stored-password".toCharArray(), phrase = "stored-phrase".toCharArray();
            var account = new Account(UUID.randomUUID(), "Production", "deploy", new Auth.KeyAndPassword(Path.of("/keys/deploy"), phrase, password), Instant.EPOCH, Instant.EPOCH);
            var form = EntryEditor.login(kit(), account, value -> CompletableFuture.completedFuture(null), () -> { });
            var view = form.view();
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).selectedId()).isEqualTo(EntryEditor.AUTH_KEY);
            assertThat(node(view, EntryEditor.ALSO_PASSWORD, Nodes.Toggle.class).checked()).isTrue();
            assertThat(secret(view, EntryEditor.PASSWORD)).isEqualTo("stored-password");
            assertThat(secret(view, EntryEditor.PASSPHRASE)).isEqualTo("stored-phrase");
            assertThat(view.spec().toString()).doesNotContain("stored-password").doesNotContain("stored-phrase");
            form.close();
            assertThat(password).isEqualTo("stored-password".toCharArray());
            assertThat(phrase).isEqualTo("stored-phrase".toCharArray());
        });
    }

    @Test void failedSaveAllowsRetryAndClosingWipesDraft() throws Exception {
        edt(() -> {
            var completion = new CompletableFuture<Void>();
            var form = EntryEditor.note(kit(), null, value -> { value.zero(); return completion; }, () -> { });
            var view = form.view();
            emit(view, new UiEvent.Edited(EntryEditor.NAME, "Recovery")); type(view, EntryEditor.NOTE, "secret\nnote");
            press(view, EditorForm.SAVE);
            assertThat(button(view, EditorForm.SAVE).state().busy()).isTrue();
            assertThat(node(view, EntryEditor.NAME, Nodes.Field.class).state().enabled()).isFalse();
            completion.completeExceptionally(new java.io.IOException("Disk full"));
            assertThat(button(view, EditorForm.SAVE).state().busy()).isFalse();
            assertThat(node(view, EditorForm.MESSAGE, Nodes.Text.class).text()).contains("Disk full");
            assertThat(secret(view, EntryEditor.NOTE)).as("a failed save keeps the draft for retry").isEqualTo("secret\nnote");
            form.close();
            assertThat(closed(view)).isTrue();
        });
    }

    @Test void readinessKeepsEveryValueUntilTheRequiredNameIsSupplied() throws Exception {
        edt(() -> {
            var saved = new AtomicReference<Account>();
            var form = EntryEditor.login(kit(), null, value -> { saved.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
            var view = form.view();
            emit(view, new UiEvent.Edited(EntryEditor.USERNAME, "deploy")); type(view, EntryEditor.PASSWORD, "kept-password");
            press(view, EditorForm.SAVE);
            assertThat(button(view, EditorForm.SAVE).state().enabled()).isFalse();
            assertThat(saved.get()).isNull();
            assertThat(form.busy()).isFalse();
            assertThat(secret(view, EntryEditor.PASSWORD)).as("a rejected save keeps unrelated fields").isEqualTo("kept-password");
            assertThat(node(view, EntryEditor.USERNAME, Nodes.Field.class).value()).isEqualTo("deploy");
            emit(view, new UiEvent.Edited(EntryEditor.NAME, "Production"));
            assertThat(error(view, EntryEditor.NAME)).isEmpty();
            press(view, EditorForm.SAVE);
            assertThat(saved.get().name()).isEqualTo("Production");
            saved.get().auth().zero();
        });
    }

    @Test void aSaveCompletingAfterTheOwnerClosedNeverTouchesTheView() throws Exception {
        edt(() -> {
            var kit = new RecordingKit("dev.jasper.vault");
            var completion = new CompletableFuture<Void>();
            boolean[] dismissed = {false};
            var form = EntryEditor.note(kit, null, value -> { value.zero(); return completion; }, () -> dismissed[0] = true);
            emit(form.view(), new UiEvent.Edited(EntryEditor.NAME, "Recovery")); type(form.view(), EntryEditor.NOTE, "pending");
            press(form.view(), EditorForm.SAVE);
            form.close();
            completion.complete(null);
            assertThat(kit.lateCalls).isEmpty();
            assertThat(dismissed[0]).as("only an open form dismisses its dialog").isFalse();
        });
    }

    @Test void aSaveTheManagerRejectsKeepsTheFormsOwnFieldsForRetry(@TempDir Path directory) throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory)) {
                var first = EntryEditor.note(kit(), null, f.manager::saveNote, () -> { });
                emit(first.view(), new UiEvent.Edited(EntryEditor.NAME, "Pending")); type(first.view(), EntryEditor.NOTE, "first draft");
                press(first.view(), EditorForm.SAVE);
                assertThat(f.manager.busy()).isTrue();
                var second = EntryEditor.login(kit(), null, f.manager::saveAccount, () -> { });
                var view = second.view();
                emit(view, new UiEvent.Edited(EntryEditor.NAME, "Production")); type(view, EntryEditor.PASSWORD, "second-password");
                press(view, EditorForm.SAVE);
                assertThat(node(view, EditorForm.MESSAGE, Nodes.Text.class).text()).isEqualTo("An edit is still saving");
                assertThat(button(view, EditorForm.SAVE).state().busy()).isFalse();
                assertThat(secret(view, EntryEditor.PASSWORD)).as("the rejected copy is wiped by the manager, the draft stays").isEqualTo("second-password");
                f.drain();
                press(view, EditorForm.SAVE); f.drain();
                assertThat(closed(view)).isTrue();
                assertThat(f.manager.rows()).extracting(VaultManager.Row::name).contains("Pending", "Production");
            }
        });
    }

    @Test void passwordMismatchDoesNotStartAChangeAndCloseClearsEveryField(@TempDir Path directory) throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory)) {
                var form = new ChangePasswordForm(kit(), f.lock, () -> { });
                var view = form.view();
                assertThat(node(view, ChangePasswordForm.REPLACEMENT, Nodes.Secret.class).revealGroup())
                    .isEqualTo(node(view, ChangePasswordForm.CONFIRM, Nodes.Secret.class).revealGroup())
                    .isNotEqualTo(node(view, ChangePasswordForm.CURRENT, Nodes.Secret.class).revealGroup());
                type(view, ChangePasswordForm.CURRENT, "test-password");
                type(view, ChangePasswordForm.REPLACEMENT, "new-password");
                type(view, ChangePasswordForm.CONFIRM, "different");
                press(view, EditorForm.SAVE);
                assertThat(button(view,EditorForm.SAVE).state().enabled()).isFalse();
                assertThat(f.queue).isEmpty();
                assertThat(secret(view, ChangePasswordForm.CURRENT)).isEqualTo("test-password");
                form.close();
                assertThat(closed(view)).isTrue();
            }
        });
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
        var form = new ChangePasswordForm(kit(), f.lock, () -> { });
        type(form.view(), ChangePasswordForm.CURRENT, "test-password");
        type(form.view(), ChangePasswordForm.REPLACEMENT, "new-password");
        type(form.view(), ChangePasswordForm.CONFIRM, "new-password");
        return form;
    }

    @Test void passwordChangeCanRetryTheSameOldPasswordAfterWriteFailure(@TempDir Path directory) throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory)) {
                var form = passwordChange(f); var view = form.view();
                Path obstruction = java.nio.file.Files.createDirectory(directory.resolve("vault.jv.tmp"));
                press(view, EditorForm.SAVE); f.drain();
                assertThat(button(view, EditorForm.SAVE).state().busy()).isFalse();
                assertThat(secret(view, ChangePasswordForm.CURRENT)).as("a failed write keeps the fields for retry").isEqualTo("test-password");
                java.nio.file.Files.delete(obstruction);
                press(view, EditorForm.SAVE); f.drain();
                assertThat(closed(view)).as("successful retry closes and wipes the form").isTrue();
                f.lock.lock();
                var opened = f.lock.unlock("new-password".toCharArray()); f.drain();
                assertThat(opened).isCompletedWithValue(null);
            }
        });
    }

    @Test void cancellingOrClosingBeforePasswordCommitKeepsTheOldPassword(@TempDir Path directory) throws Exception {
        edt(() -> {
            for (int mode = 0; mode < 3; mode++) {
                try (var f = new VaultUiFixture(directory.resolve("case-" + mode))) {
                    var form = passwordChange(f); var view = form.view(); press(view, EditorForm.SAVE);
                    assertThat(f.queue).hasSize(1);
                    if (mode == 0) press(view, EditorForm.CANCEL); else form.close();
                    if (mode == 2) f.lock.lock(); // Plugin shutdown closes forms, then locks.
                    f.drain();
                    assertThat(closed(view)).isTrue();
                    f.lock.lock();
                    var opened = f.lock.unlock("test-password".toCharArray()); f.drain();
                    assertThat(opened).as("close mode %s", mode).isCompletedWithValue(null);
                }
            }
        });
    }

    @Test void closingAfterPasswordCommitStartsIsClearlyDifferentFromCancel(@TempDir Path directory) throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory)) {
                var form = passwordChange(f); var view = form.view(); press(view, EditorForm.SAVE);
                f.queue.removeFirst().run(); // Derivation completes; the file write is now committed to run.
                assertThat(button(view, EditorForm.CANCEL).label()).isEqualTo("Close");
                assertThat(node(view, EditorForm.MESSAGE, Nodes.Text.class).text()).contains("closing will not cancel");
                form.close(); f.lock.lock(); f.drain();
                assertThat(f.lock.state()).isEqualTo(dev.jasper.vault.api.LockState.LOCKED);
                var opened = f.lock.unlock("new-password".toCharArray()); f.drain();
                assertThat(opened).isCompletedWithValue(null);
            }
        });
    }

    @Test void passwordWriteFailureIsReportedEvenIfTheFormWasClosed(@TempDir Path directory) throws Exception {
        edt(() -> {
            try (var f = new VaultUiFixture(directory)) {
                var reported = new AtomicReference<Throwable>();
                var form = new ChangePasswordForm(kit(), f.lock, () -> { }, reported::set);
                type(form.view(), ChangePasswordForm.CURRENT, "test-password");
                type(form.view(), ChangePasswordForm.REPLACEMENT, "new-password");
                type(form.view(), ChangePasswordForm.CONFIRM, "new-password");
                java.nio.file.Files.createDirectory(directory.resolve("vault.jv.tmp"));
                press(form.view(), EditorForm.SAVE); f.queue.removeFirst().run();
                form.close(); f.drain();
                assertThat(reported.get()).isNotNull();
                f.lock.lock();
                var opened = f.lock.unlock("test-password".toCharArray()); f.drain();
                assertThat(opened).isCompletedWithValue(null);
            }
        });
    }
}
