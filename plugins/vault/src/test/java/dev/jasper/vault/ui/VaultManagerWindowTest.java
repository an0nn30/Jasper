package dev.jasper.vault.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.service.VaultService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultManagerWindowTest {
    @TempDir Path directory;
    @Test void editorBrowseUsesTheSdkDialogChooser() {
        try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
            var context = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            host.addTerminalWindow();
            var owner = context.terminals().windows().getFirst();
            var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, text -> { });
            var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                new SecretClipboard(text -> { }, Optional::empty, clear -> { }), () -> "test", () -> { });
            window.show(owner, Optional.empty());
            var editor = new AtomicReference<EntryEditor>();
            window.dialog("Add SSH Key", dismiss -> {
                var form = EntryEditor.key(null, value -> java.util.concurrent.CompletableFuture.completedFuture(null), dismiss);
                editor.set(form); return form;
            });
            Path selected = directory.resolve("key with spaces");
            host.queueFileSelection(Optional.of(selected));
            ((javax.swing.JButton) editor.get().privatePath.getParent().getComponent(1)).doClick();
            assertThat(editor.get().privatePath.getText()).isEqualTo(selected.toString());
            window.close();
        }
    }

    @Test void singletonSelectionDetailsAndCloseAreHeadless() {
        try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
            PluginContext context = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            host.addTerminalWindow();
            WindowHandle owner = context.terminals().windows().getFirst();
            var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, text -> { });
            var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                new SecretClipboard(text -> { }, Optional::empty, clear -> { }), () -> "Device secret: test", () -> { });
            UUID id = UUID.randomUUID();
            f.manager.saveNote(new Note(id, "Recovery", "line one\nline two".toCharArray(), Instant.EPOCH)); f.drain();
            window.show(owner, Optional.of(id)); window.show(owner, Optional.of(id));
            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            assertThat(window.panel.rows.getSelectedValue().id()).isEqualTo(id);
            assertThat(window.panel.rows.getSelectedValue().name()).isEqualTo("Recovery");
            assertThat(host.requestClose("dev.jasper.vault.manager")).isTrue();
            assertThat(window.isOpen()).isFalse();
            assertThat(window.panel.rows.getModel().getSize()).isZero();
        }
    }

    @Test void lockClearsDetailsAndEditorsWithoutCreatingAnUnlockWaiter() {
        try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
            PluginContext context = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
            int[] prompts = {0};
            var controller = new AtomicReference<VaultManagerWindow>();
            var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> {
                prompts[0]++; assertThat(controller.get().showUnlock(prompt)).isTrue();
            }, p -> { }, p -> { }, text -> { });
            var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                new SecretClipboard(text -> { }, Optional::empty, clear -> { }), () -> "status", () -> { });
            controller.set(window);
            UUID id = UUID.randomUUID();
            f.manager.saveNote(new Note(id, "Recovery", "secret".toCharArray(), Instant.EPOCH)); f.drain();
            window.show(owner, Optional.of(id));
            var editor = new AtomicReference<EntryEditor>();
            window.dialog("Edit Secure Note", dismiss -> {
                EntryEditor form = EntryEditor.note((Note) f.manager.entry(id).orElseThrow(), f.manager::saveNote, dismiss);
                editor.set(form); return form;
            });
            f.manager.invalidate(); f.lock.lock(); window.changed();
            assertThat(window.panel.rows.getModel().getSize()).isZero();
            assertThat(editor.get().noteText.snapshot()).isEmpty();
            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            assertThat(prompts[0]).isZero();
            window.panel.unlock.doClick();
            assertThat(prompts[0]).isEqualTo(1);
            assertThat(window.passwordPanel).isNotNull();
            window.passwordPanel.password.setText("test-password");
            window.passwordPanel.primary.doClick(); f.drain();
            assertThat(f.lock.state()).isEqualTo(dev.jasper.vault.api.LockState.UNLOCKED);
            assertThat(window.passwordPanel).isNull();
            window.close();
        }
    }

    @Test void grantsShowCredentialNamesAndRevokeUpdatesTheList() {
        try (var f = new VaultUiFixture(directory); var host = new FakePluginHost()) {
            PluginContext context = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
            var service = new VaultService(f.lock, () -> Optional.of(owner), p -> { }, p -> { }, p -> { }, text -> { });
            var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                new SecretClipboard(text -> { }, Optional::empty, clear -> { }), () -> "status", () -> { });
            UUID id = UUID.randomUUID();
            f.manager.saveAccount(VaultManagerTest.account(id, "secret".toCharArray())); f.drain();
            Grant grant = new Grant("dev.jasper.ssh", id); f.lock.vault().grants().add(grant);
            window.show(owner, Optional.empty());
            assertThat(window.panel.grantLabel(grant)).isEqualTo("dev.jasper.ssh \u2014 Production");
            window.panel.grants.setSelectedIndex(0); window.panel.revoke.doClick(); f.drain(); window.changed();
            assertThat(window.panel.grants.getModel().getSize()).isZero();
            window.close();
        }
    }

    @Test void managerLockAndUnlockUseHostNamedIcons() throws Exception {
        for (boolean retro : new boolean[]{false, true}) try (var f = new VaultUiFixture(java.nio.file.Files.createDirectories(directory.resolve(retro ? "retro" : "modern"))); var host = new FakePluginHost()) {
            host.setRetroIcons(retro);
            PluginContext context = host.start(new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
            var service = new VaultService(f.lock, () -> Optional.of(owner), p -> {}, p -> {}, p -> {}, text -> {});
            var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                new SecretClipboard(text -> {}, Optional::empty, clear -> {}), () -> "status", () -> {});
            window.show(owner, Optional.empty());
            assertThat(window.panel.lock.getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.LOCK, retro));
            assertThat(window.panel.unlock.getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.UNLOCK, retro));
            window.close();
        }
    }
}
