package dev.jasper.vault.ui;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.ManagedSshKey;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The manager, one content tab (workspace tabs spec 1 decision 2), and its modal editors, which the tab owns. The plugin
 * forwards state/save/config changes here.
 */
public final class VaultManagerTab implements AutoCloseable {
    /** The manager tab's key: opening it again selects the open one. */
    public static final String KEY = "manager";
    private final PluginContext context;
    private final LockManager lock;
    private final VaultManager manager;
    private final VaultService service;
    /** Presentation of the configured provider, auto-lock policy and actual vault path. */
    public record Status(String deviceSecret, String autoLock, String path) { }
    private final Supplier<Status> status;
    private final Runnable generate;
    private final EntryPresenter editors;
    private final Layout layout;
    private final CopyCards copies;
    private ContentTab tab;
    private UnlockPrompt unlockPrompt;
    ManagerPanel panel;
    PasswordPanel passwordPanel;

    public VaultManagerTab(PluginContext context, LockManager lock, VaultManager manager,
                           VaultService service, Supplier<Status> status, Runnable generate) {
        this(context, lock, manager, service, status, generate, Layout.ROWS);
    }

    /** The manager in {@code layout}'s composition (Enterprise presentation spec 14.8). */
    public VaultManagerTab(PluginContext context, LockManager lock, VaultManager manager,
                           VaultService service, Supplier<Status> status, Runnable generate, Layout layout) {
        this.layout = layout;
        this.context = context; this.lock = lock; this.manager = manager; this.service = service;
        this.status = status; this.generate = generate;this.editors=new EntryPresenter(context, layout);
        this.copies = CopyCards.declare(context);
    }
    public boolean isOpen() { return tab != null; }
    /** The plugin's kit, for the forms this tab owns. */
    public UiKit kit() { return context.kit(); }
    /** Opens the manager as a tab in {@code owner}'s window, or selects the open one, with {@code selection} selected. */
    public void show(WindowHandle owner, Optional<UUID> selection) {
        if (tab == null) {
            // The passive Unlock asks over the window that holds the tab, wherever it has moved.
            var shown = new ManagerPanel(context.kit(), layout, manager::entry, this::add, this::edit, this::delete, this::copyPublic,
                grant -> report("Could not revoke the grant", manager.revoke(grant)), generate, lock::lock,
                () -> { if (tab != null) service.requestUnlock(tab.window()); });
            shown.copies(copies);
            panel = shown;
            tab = context.windows().openTab(new ContentSpec(KEY, "Credential Vault"), owner, shown.view());
            tab.onClosed(() -> {
                // The tab has already disposed the view and erased its storage; nothing touches it from here.
                tab = null; shown.close(); closeDialogs();
                if (unlockPrompt != null) unlockPrompt.cancel();
                passwordPanel = null; unlockPrompt = null;
            });
        }
        changed(); selection.ifPresent(panel::select); tab.select();
    }
    /** Refresh existing content; never opens the manager in response to an unrelated consumer unlock. */
    public void changed() {
        if (tab == null) return;
        boolean unlocked = lock.state() == LockState.UNLOCKED;
        if (!unlocked) closeDialogs();
        panel.refresh(manager.rows(), manager.grants(), status.get(), unlocked);
        if (unlockPrompt == null) panel.passiveUnlock();
    }
    /** Routes the service's existing shared prompt into the manager, with no second unlock attempt. */
    public boolean showUnlock(UnlockPrompt prompt) {
        if (tab == null) return false;
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
        tab.select(); return true;
    }
    /** Every form, including the generator, gets the same modal ownership and close-time wipe. */
    public void dialog(String title, Function<Runnable, ? extends EditorForm> factory) { dialog(title, Nodes.Hue.NONE, factory); }
    /** As {@link #dialog(String, Function)}, the sheet's swatch in {@code hue} under the classic layout (plan 10b Rulings 5). */
    public void dialog(String title, Nodes.Hue hue, Function<Runnable, ? extends EditorForm> factory) {
        if (tab == null || lock.state() != LockState.UNLOCKED) return;
        editors.show(tab,title,hue,factory,()->{});
    }
    /** Opens the sheet for a new entry of the type over the open manager; does nothing when it is closed or locked. */
    public void add(VaultManager.Type type) {
        UiKit kit = context.kit();
        switch (type) {
            case LOGIN -> dialog("Add Login", Nodes.Hue.BLUE, close -> EntryEditor.login(kit, null, manager::saveAccount, close));
            case SSH_KEY -> dialog("Add SSH Key", Nodes.Hue.GREEN, close -> EntryEditor.key(kit, null, manager::importKey, close));
            case NOTE -> dialog("Add Secure Note", Nodes.Hue.AMBER, close -> EntryEditor.note(kit, null, manager::saveNote, close));
        }
    }
    private void edit(UUID id) {
        UiKit kit = context.kit();
        manager.entry(id).ifPresent(value -> {
            switch (value) {
                case Account a -> dialog("Edit Login", Nodes.Hue.BLUE, close -> EntryEditor.login(kit, a, manager::saveAccount, close));
                case dev.jasper.vault.model.ManagedSshKey k -> dialog("Edit stored SSH key", Nodes.Hue.GREEN, close -> {
                    var form = new EditorForm(kit, close);
                    form.text(EntryEditor.NAME, "Name", k.name());
                    form.display("storage", "Storage", "Stored in Vault");
                    form.display("fingerprint", "Fingerprint", k.fingerprint());
                    UUID keyId = k.id(); form.submit(() -> manager.renameManaged(keyId, form.value(EntryEditor.NAME).strip())); return form;
                });
                case SshKey k -> dialog("Edit SSH Key", Nodes.Hue.GREEN, close -> EntryEditor.key(kit, k, manager::importKey, close));
                case Note n -> dialog("Edit Secure Note", Nodes.Hue.AMBER, close -> EntryEditor.note(kit, n, manager::saveNote, close));
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
    /** Shows the saved plugin grants in the open manager; does nothing when it is closed or locked. */
    public void showGrants() { if (tab != null) { panel.showGrants(); tab.select(); } }
    /** Asks for a new master password over the open manager; does nothing when it is closed or locked. */
    public void changePassword() {
        dialog("Change Master Password", close -> new ChangePasswordForm(context.kit(), lock, close, failure -> {
            if (failure != null) context.notifications().group().error("Master password was not changed", EditorForm.message(failure), failure);
        }));
    }
    private void copyPublic(UUID id) {
        String name = manager.entry(id).map(entry -> switch (entry) {
            case SshKey key -> key.name();
            case ManagedSshKey key -> key.name();
            default -> "";
        }).orElse("");
        CompletableFuture<Void> copied = manager.publicKey(id).thenAccept(text -> copies.plain("public key", name, text));
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
        if (tab != null) tab.close();
        if (panel != null) panel.close();
    }
}
