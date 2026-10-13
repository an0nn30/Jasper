package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.UiKit;
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

/** Singleton window ownership and modal editors. The plugin forwards state/save/config changes here. */
public final class VaultManagerWindow implements AutoCloseable {
    public static final String ID = "dev.jasper.vault.manager";
    private final PluginContext context;
    private final LockManager lock;
    private final VaultManager manager;
    private final VaultService service;
    /** Presentation of the configured provider, auto-lock policy and actual vault path. */
    public record Status(String deviceSecret, String autoLock, String path) { }
    private final Supplier<Status> status;
    private final Runnable generate;
    private final EntryPresenter editors;
    private PluginWindow window;
    private WindowHandle owner;
    private UnlockPrompt unlockPrompt;
    ManagerPanel panel;
    PasswordPanel passwordPanel;

    public VaultManagerWindow(PluginContext context, LockManager lock, VaultManager manager,
                              VaultService service, Supplier<Status> status, Runnable generate) {
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;
        this.status = status; this.generate = generate;this.editors=new EntryPresenter(context);
    }
    public boolean isOpen() { return window != null; }
    /** The plugin's kit, for the forms this window owns. */
    public UiKit kit() { return context.kit(); }
    public void show(WindowHandle owner, Optional<UUID> selection) {
        this.owner = owner;
        if (window == null) {
            window = context.windows().create(new WindowSpec(ID, "Credential Vault", new Dimension(900, 555), true, WindowSpec.SizeBasis.CONTENT));
            var shown = new ManagerPanel(context.kit(), manager::entry, this::add, this::edit, this::delete, this::copyPublic,
                grant -> report("Could not revoke the grant", manager.revoke(grant)), generate, lock::lock,
                () -> service.requestUnlock(this.owner), this::close);
            shown.secretClipboard(text -> context.platform().copySecret(text));
            panel = shown;
            window.setContent(shown.view());
            window.setTitleActions(Optional.of(shown.titleActions()));
            window.onClosed(() -> {
                // The surface has already disposed the view and erased its storage; nothing touches it from here.
                window = null; shown.close(); closeDialogs();
                if (unlockPrompt != null) unlockPrompt.cancel();
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
        unlockPrompt = prompt; passwordPanel = panel.showUnlock(prompt);
        PasswordPanel shown = passwordPanel;
        boolean[] active = {true};
        prompt.onPresentationRetired(() -> {
            active[0] = false;
            if (unlockPrompt == prompt && passwordPanel == shown) {
                unlockPrompt = null; passwordPanel = null;
                panel.passiveUnlock();
            } else shown.close();
        });
        prompt.onDismiss(() -> {
            if (!active[0]) return;
            shown.clear();
            if (unlockPrompt == prompt && passwordPanel == shown) { unlockPrompt = null; passwordPanel = null; changed(); }
        });
        window.toFront(); return true;
    }
    /** Every form, including the generator, gets the same modal ownership and close-time wipe. */
    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) {
        if (window == null || lock.state() != LockState.UNLOCKED) return;
        editors.show(window,title,factory,()->{});
    }
    /** Opens the sheet for a new entry of the type over the open window; does nothing when it is closed or locked. */
    public void add(VaultManager.Type type) {
        UiKit kit = context.kit();
        switch (type) {
            case LOGIN -> dialog("Add Login", close -> EntryEditor.login(kit, null, manager::saveAccount, close));
            case SSH_KEY -> dialog("Add SSH Key", close -> EntryEditor.key(kit, null, manager::importKey, close));
            case NOTE -> dialog("Add Secure Note", close -> EntryEditor.note(kit, null, manager::saveNote, close));
        }
    }
    private void edit(UUID id) {
        UiKit kit = context.kit();
        manager.entry(id).ifPresent(value -> {
            switch (value) {
                case Account a -> dialog("Edit Login", close -> EntryEditor.login(kit, a, manager::saveAccount, close));
                case dev.jasper.vault.model.ManagedSshKey k -> dialog("Edit stored SSH key", close -> {
                    var form = new EditorForm(kit, close);
                    form.text(EntryEditor.NAME, "Name", k.name());
                    form.display("storage", "Storage", "Stored in Vault");
                    form.display("fingerprint", "Fingerprint", k.fingerprint());
                    UUID keyId = k.id(); form.submit(() -> manager.renameManaged(keyId, form.value(EntryEditor.NAME).strip())); return form;
                });
                case SshKey k -> dialog("Edit SSH Key", close -> EntryEditor.key(kit, k, manager::importKey, close));
                case Note n -> dialog("Edit Secure Note", close -> EntryEditor.note(kit, n, manager::saveNote, close));
                default -> throw new IllegalArgumentException("Unknown vault entry");
            }
        });
    }
    static final String DELETE_FILES = "delete-files";
    private void delete(UUID id) {
        UiKit kit = context.kit();
        manager.entry(id).ifPresent(value -> dialog("Delete entry", close -> {
            var form = new EditorForm(kit, close);
            form.description("entry", "", "Remove this entry? " + manager.rows().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().name());
            if (value instanceof SshKey key) {
                form.toggle(DELETE_FILES, "Delete private and public key files too", false);
                form.display("private-file", "Private file", key.privatePath().toString());
                form.display("public-file", "Public file", key.publicPath().toString());
                form.display("shared-files", "Shared files", "Other accounts using these paths will also lose the files.");
            }
            form.setSaveLabel("Delete"); form.submit(() -> manager.delete(id, form.checked(DELETE_FILES))); return form;
        }));
    }
    /** Shows the saved plugin grants in the open window; does nothing when it is closed or locked. */
    public void showGrants() { if (window != null) { panel.showGrants(); window.toFront(); } }
    /** Asks for a new master password over the open window; does nothing when it is closed or locked. */
    public void changePassword() {
        dialog("Change Master Password", close -> new ChangePasswordForm(context.kit(), lock, close, failure -> {
            if (failure != null) context.notifications().group().error("Master password was not changed", EditorForm.message(failure), failure);
        }));
    }
    private void copyPublic(UUID id) {
        CompletableFuture<Void> copied = manager.publicKey(id).thenAccept(text -> context.platform().copy(text));
        report("Could not copy the public key", copied);
    }
    private void report(String title, CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) context.notifications().group().error(title, EditorForm.message(failure), failure);
            changed();
        });
    }
    private void closeDialogs() {editors.close();}
    @Override public void close() {
        closeDialogs();
        if (window != null) window.close();
        if (panel != null) panel.close();
    }
}
