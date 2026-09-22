package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.WindowSpec;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.service.VaultService;
import java.awt.Dimension;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

/** Singleton window ownership and modal editors. The plugin forwards state/save/config changes here. */
public final class VaultManagerWindow implements AutoCloseable {
    public static final String ID = "dev.jasper.vault.manager";
    private final PluginContext context;
    private final LockManager lock;
    private final VaultManager manager;
    private final VaultService service;
    private final SecretClipboard clipboard;
    private final Supplier<String> status;
    private final Runnable generate;
    private final Map<PluginDialog, EditorForm> dialogs = new IdentityHashMap<>();
    private PluginWindow window;
    private WindowHandle owner;
    private UnlockPrompt unlockPrompt;
    ManagerPanel panel;
    PasswordPanel passwordPanel;

    public VaultManagerWindow(PluginContext context, LockManager lock, VaultManager manager,
                              VaultService service, SecretClipboard clipboard, Supplier<String> status, Runnable generate) {
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;
        this.clipboard = clipboard; this.status = status; this.generate = generate;
    }
    public boolean isOpen() { return window != null; }
    public void show(WindowHandle owner, Optional<UUID> selection) {
        this.owner = owner;
        if (window == null) {
            window = context.windows().create(new WindowSpec(ID, "Credential Vault", new Dimension(900, 620), true));
            panel = new ManagerPanel(manager::entry, this::add, this::edit, this::delete, this::copyPublic,
                grant -> report(manager.revoke(grant)), generate, this::changePassword, lock::lock,
                () -> service.requestUnlock(this.owner));
            window.setContent(panel);
            window.onClosed(() -> {
                window = null; closeDialogs(); panel.close();
                if (unlockPrompt != null) unlockPrompt.cancel();
                if (passwordPanel != null) passwordPanel.clear();
                passwordPanel = null; unlockPrompt = null;
            });
        }
        changed(); selection.ifPresent(panel::select); window.show(); window.toFront();
    }
    /** Refresh existing content; never opens a window in response to an unrelated consumer unlock. */
    public void changed() {
        if (window == null) return;
        boolean unlocked = lock.state() == LockState.UNLOCKED;
        if (!unlocked) closeDialogs();
        panel.refresh(manager.rows(), manager.grants(), status.get(), unlocked);
        if (unlockPrompt == null) panel.passiveUnlock();
    }
    /** Routes the service's existing shared prompt into this window, with no second unlock attempt. */
    public boolean showUnlock(UnlockPrompt prompt) {
        if (window == null) return false;
        unlockPrompt = prompt; passwordPanel = PasswordPanel.unlock(prompt);
        PasswordPanel shown = passwordPanel;
        panel.unlockContent(shown);
        prompt.onDismiss(() -> {
            shown.clear();
            if (unlockPrompt == prompt) { unlockPrompt = null; passwordPanel = null; changed(); }
        });
        window.toFront(); return true;
    }
    /** Every form, including the generator, gets the same modal ownership and close-time wipe. */
    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) {
        if (window == null || lock.state() != LockState.UNLOCKED) return;
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        EditorForm form;
        try { form = factory.apply(dialog::close); }
        catch (RuntimeException failure) { dialog.close(); context.notices().error(EditorForm.message(failure)); return; }
        form.setFileChooser(dialog::chooseFile);
        dialogs.put(dialog, form); dialog.setContent(form);
        dialog.onClosed(() -> { form.close(); dialogs.remove(dialog); });
        dialog.show();
    }
    private void add(VaultManager.Type type) {
        switch (type) {
            case LOGIN -> dialog("Add Login", close -> EntryEditor.login(null, manager::saveAccount, close));
            case SSH_KEY -> dialog("Add SSH Key", close -> EntryEditor.key(null, manager::importKey, close));
            case NOTE -> dialog("Add Secure Note", close -> EntryEditor.note(null, manager::saveNote, close));
        }
    }
    private void edit(UUID id) {
        manager.entry(id).ifPresent(value -> {
            switch (value) {
                case Account a -> dialog("Edit Login", close -> EntryEditor.login(a, manager::saveAccount, close));
                case SshKey k -> dialog("Edit SSH Key", close -> EntryEditor.key(k, manager::importKey, close));
                case Note n -> dialog("Edit Secure Note", close -> EntryEditor.note(n, manager::saveNote, close));
                default -> throw new IllegalArgumentException("Unknown vault entry");
            }
        });
    }
    private void delete(UUID id) {
        manager.entry(id).ifPresent(value -> dialog("Delete entry", close -> {
            var form = new EditorForm(close);
            form.field("Remove this entry?", new JLabel(manager.rows().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().name()));
            var deleteFiles = new JCheckBox("Delete private and public key files too", false);
            if (value instanceof SshKey key) {
                form.field("Files", deleteFiles);
                form.field("Private file", new JLabel(key.privatePath().toString()));
                form.field("Public file", new JLabel(key.publicPath().toString()));
                form.field("Shared files", new JLabel("Other accounts using these paths will also lose the files."));
            }
            form.save.setText("Delete"); form.submit(() -> manager.delete(id, deleteFiles.isSelected())); return form;
        }));
    }
    private void changePassword() {
        dialog("Change Master Password", close -> new ChangePasswordForm(lock, close, failure -> {
            if (failure != null) context.notices().error("Master password was not changed: " + EditorForm.message(failure));
        }));
    }
    private void copyPublic(UUID id) {
        CompletableFuture<Void> copied = manager.publicKey(id).thenAccept(clipboard::copy);
        report(copied);
    }
    private void report(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) context.notices().error(EditorForm.message(failure));
            changed();
        });
    }
    private void closeDialogs() {
        for (var entry : List.copyOf(dialogs.entrySet())) { entry.getValue().close(); entry.getKey().close(); }
        dialogs.clear();
    }
    @Override public void close() {
        closeDialogs();
        if (window != null) window.close();
        if (panel != null) panel.close();
    }
}
