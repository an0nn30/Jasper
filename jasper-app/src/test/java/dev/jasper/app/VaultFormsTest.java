package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultFormsTest {
    static final UUID LOGIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID KEY = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final Instant NOW = Instant.parse("2026-09-14T09:00:00Z");

    static VaultSnapshot example(boolean locked) {
        return new VaultSnapshot(true, locked,
            locked ? List.of() : List.of(new VaultSnapshot.LoginInfo(LOGIN, "Production admin", "alice", KEY, true)),
            locked ? List.of() : List.of(new VaultSnapshot.KeyInfo(KEY, "Infrastructure", "Ed25519", "SHA256:fixture", "ssh-ed25519 AAAA fixture", 1)),
            VaultSettings.DEFAULT, null, 1, null);
    }

    @Test void loginDraftNeverLoadsTheSavedPasswordAndTracksDirtiness() throws Exception {
        edt(() -> {
            var form = new VaultLoginForm(true);
            form.load(example(false).logins().getFirst(), example(false).keys());
            assertThat(form.password.getPassword()).isEmpty();
            assertThat(form.password.getClientProperty("JTextField.placeholderText")).isEqualTo("Saved password");
            assertThat(form.key.getSelectedItem().toString()).isEqualTo("Infrastructure · Ed25519");
            assertThat(form.reuse.getText()).isEqualTo("Ed25519 · shared by 1 login");
            assertThat(form.dirty()).isFalse();
            form.name.setText("Renamed");
            assertThat(form.dirty()).isTrue();
            try (var draft = form.draft()) {
                assertThat(draft.name()).isEqualTo("Renamed");
                assertThat(draft.keyId()).isEqualTo(KEY);
                assertThat(draft.password()).isNull();
            }
            form.password.setText("typed secret");
            assertThat(form.hasTypedPassword()).isTrue();
            try (var draft = form.draft()) { assertThat(draft.password()).containsExactly("typed secret".toCharArray()); }
            form.reveal("shown".toCharArray());
            assertThat(form.revealed()).isTrue();
            form.hideSecret();
            assertThat(form.revealed()).isFalse();
            form.clear();
            assertThat(form.name.getText()).isEmpty();
            assertThat(form.password.getPassword()).isEmpty();
            assertThat(form.dirty()).isFalse();
            assertThat(form.password.getClientProperty("JTextField.placeholderText")).isEqualTo("Not set");
        });
    }

    @Test void keyFormShowsPublicDetailsOnlyAndTracksTheName() throws Exception {
        edt(() -> {
            var form = new VaultKeyForm();
            form.load(example(false).keys().getFirst());
            assertThat(form.algorithm.getText()).isEqualTo("Ed25519");
            assertThat(form.fingerprint.getText()).isEqualTo("SHA256:fixture");
            assertThat(form.publicKey.getText()).isEqualTo("ssh-ed25519 AAAA fixture");
            assertThat(form.publicKey.isEditable()).isFalse();
            assertThat(form.reuse.getText()).isEqualTo("Used by 1 login");
            assertThat(form.dirty()).isFalse();
            form.name.setText("Renamed key");
            assertThat(form.dirty()).isTrue();
            assertThat(form.draftName()).isEqualTo("Renamed key");
            form.clear();
            assertThat(form.publicKey.getText()).isEmpty();
        });
    }

    @Test void unlockFormSwitchesBetweenRememberedAndPasswordModes() throws Exception {
        edt(() -> {
            var create = new VaultUnlockForm(true, true);
            assertThat(create.primary.getText()).isEqualTo("Create vault");
            assertThat(create.confirmation.isVisible()).isTrue();
            var unlock = new VaultUnlockForm(false, true);
            assertThat(unlock.primary.getText()).isEqualTo("Unlock");
            unlock.state(new VaultSnapshot(true, true, List.of(), List.of(), VaultSettings.DEFAULT, NOW.plusSeconds(3600), 1, null));
            assertThat(unlock.remember.getText()).isEqualTo("Remember on this device for 7 days");
            unlock.passwordVisible(false);
            assertThat(unlock.password.isVisible()).isFalse();
            unlock.showPassword("Device access expired. Enter the master password.");
            assertThat(unlock.password.isVisible()).isTrue();
            assertThat(unlock.message.getText()).isEqualTo("Device access expired. Enter the master password.");
            unlock.password.setText("demo"); unlock.remember.setSelected(true); unlock.clear();
            assertThat(unlock.password.getPassword()).isEmpty();
            assertThat(unlock.remember.isSelected()).isFalse();
            var unavailable = new VaultUnlockForm(false, false);
            unavailable.state(new VaultSnapshot(true, true, List.of(), List.of(), VaultSettings.DEFAULT, null, 0, "OS credential store unavailable"));
            assertThat(unavailable.remember.isEnabled()).isFalse();
            assertThat(unavailable.message.getText()).isEqualTo("OS credential store unavailable. Use the master password.");
        });
    }

    @Test void settingsImportAndAddDialogExposeTheirControls() throws Exception {
        edt(() -> {
            var settings = new VaultSettingsForm(example(false));
            settings.auto.setValue(0); settings.days.setValue(365);
            assertThat(settings.settings()).isEqualTo(new VaultSettings(0, 365));
            var importForm = new VaultImportForm();
            assertThat(importForm.save.isEnabled()).isFalse();
            assertThat(importForm.file.getText()).isEqualTo("No file selected");
            importForm.passphrase.setText("x"); importForm.source = java.nio.file.Path.of("k"); importForm.clear();
            assertThat(importForm.passphrase.getPassword()).isEmpty();
            assertThat(importForm.source).isNull();
            var dialog = new VaultAddLoginDialog(example(false).keys());
            var layout = (BorderLayout) dialog.getLayout();
            assertThat(layout.getLayoutComponent(BorderLayout.CENTER)).isSameAs(dialog.form);
            var footer = (BorderLayout) dialog.footer.getLayout();
            assertThat(footer.getLayoutComponent(BorderLayout.WEST)).isSameAs(dialog.imported);
            assertThat(dialog.actions.getComponent(0)).isSameAs(dialog.cancel);
            assertThat(dialog.actions.getComponent(1)).isSameAs(dialog.add);
            assertThat(dialog.form.key.getItemCount()).isEqualTo(2);
            assertThat(dialog.form.key.getItemAt(0).toString()).isEqualTo("None");
        });
    }

    @Test void rememberedTextReportsMissingActiveAndExpiredAccess() {
        assertThat(VaultUi.rememberedText(null, NOW)).isEqualTo("No remembered device access");
        assertThat(VaultUi.rememberedText(NOW.plusSeconds(60), NOW)).startsWith("Device access until ");
        assertThat(VaultUi.rememberedText(NOW.minusSeconds(60), NOW)).startsWith("Device access expired ");
    }
}
