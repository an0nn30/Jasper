package dev.jasper.vault;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import java.time.Duration;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.lock.InactivityTimer;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.service.GrantPrompt;
import dev.jasper.vault.service.PickPrompt;
import dev.jasper.vault.service.UnlockPrompt;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import dev.jasper.vault.ui.CopyCards;
import dev.jasper.vault.ui.GrantPanel;
import dev.jasper.vault.ui.PasswordPanel;
import dev.jasper.vault.ui.PickerPanel;
import dev.jasper.vault.ui.VaultScope;
import dev.jasper.vault.ui.VaultManager;
import dev.jasper.vault.ui.VaultManagerDialog;
import dev.jasper.vault.ui.KeyGeneratorForm;
import java.util.concurrent.CompletableFuture;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the vault to Jasper: the lock manager on the plugin's executors, the service published per
 * consumer, the Open and Lock actions, the lock-state topic, the settings file and the inactivity lock.
 * Owns the manager dialog, status item, rail action and palette scope.
 */
public class VaultPlugin implements Plugin {
    public static final String OPEN = "dev.jasper.vault.open";
    public static final String LOCK = "dev.jasper.vault.lock";
    /** Opens the palette on the Vault scope. */
    public static final String SEARCH = "dev.jasper.vault.search";
    public static final String STATUS = "dev.jasper.vault.status";
    private StatusItem status;
    private String locked, unlocked;
    private static final int TICK_MILLIS = 5_000;

    private final Function<PluginContext, DeviceSecrets> secretsFactory;
    private final Executor ui;
    private PluginContext context;
    /** Which composition this run takes (Enterprise presentation spec 14.3), read once in {@link #start}. */
    private dev.jasper.vault.workstation.Layout layout = dev.jasper.vault.workstation.Layout.ROWS;
    /** The composition this run takes. */
    dev.jasper.vault.workstation.Layout layout() { return layout; }
    private LockManager lock;
    private VaultService service;
    private InactivityTimer timer;
    private PluginAction lockAction;
    private UnlockPrompt currentUnlock;
    private GrantPrompt currentGrant;
    private Timer ticker;
    private AWTEventListener activity;
    private VaultScope scope;
    private VaultSettings settings;
    private VaultSettings.Declared declared;
    public static final String GENERATE = "dev.jasper.vault.generate_key";
    /** Opens the manager and asks for a new master password. */
    public static final String CHANGE_PASSWORD = "dev.jasper.vault.change_password";
    /** Opens the manager on the saved plugin grants. */
    public static final String GRANTS = "dev.jasper.vault.grants";
    /** Open the manager and its sheet for a new entry of one type. */
    public static final String ADD_LOGIN = "dev.jasper.vault.add_login", ADD_KEY = "dev.jasper.vault.add_key",
        ADD_NOTE = "dev.jasper.vault.add_note";
    private VaultManager manager;
    private VaultManagerDialog managerDialog;
    private PluginDialog createDialog;
    private CompletableFuture<Boolean> creating;
    private final java.util.Set<CompletableFuture<Boolean>> createWaiters = new java.util.HashSet<>();
    private boolean stopped;
    /** Milliseconds for the inactivity clock; tests set it, production reads the wall clock. */
    long clock = -1;

    /** Created by the runtime: the platform keychain with the file fallback, completing on the EDT. */
    public VaultPlugin() {
        this(context -> new DeviceSecrets(KeychainStore.forPlatform(context.dataDirectory()), new FileStore(context.dataDirectory().resolve("device.secret"))), SwingUtilities::invokeLater);
    }

    /** For tests: the device secret store to use and the executor that stands in for the UI thread. */
    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) {
        this.secretsFactory = secretsFactory; this.ui = ui;
    }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        layout = dev.jasper.vault.workstation.Layout.of(context.services());
        stopped = false;
        Files.createDirectories(context.dataDirectory());
        declared = new VaultSettings.Declared(context.settings());
        settings = declared.read(context.dataDirectory());
        timer = new InactivityTimer(() -> clock >= 0 ? clock : System.currentTimeMillis());
        timer.setTimeout(settings.autoLock());
        lock = new LockManager(new VaultFile(context.dataDirectory().resolve("vault.jv")), secretsFactory.apply(context), context.background(), ui, this::lockStateChanged);
        service = new VaultService(lock, () -> context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()),
            this::showUnlock, this::showGrant, this::showPick, context.notifications().group(),
            context.background(), ui, owner -> switch (lock.state()) {
                case NO_VAULT -> createVault(owner);
                case LOCKED -> service.requestUnlock(owner);
                case UNLOCKED -> CompletableFuture.completedFuture(true);
            }, this::showImport, this::vaultChanged);
        service.anchoredPicker(prompt -> new dev.jasper.vault.ui.AnchoredPicker(context,manager,prompt).show(), anchor -> context.windows().popover(anchor).close());
        context.services().publishPerConsumer(VaultApi.class, service::forConsumer);
        locked = "lock";
        unlocked = "unlock";
        context.actions().register(ActionSpec.of(OPEN, "Open Vault...").withIcon(locked).withKeywords(List.of("vault", "credentials", "password", "unlock")).withDefaultBinding("cmd+shift+\\"),
            invoked -> open(invoked.window(), Optional.empty()));
        context.actions().register(ActionSpec.of(SEARCH, "Search Vault Logins").withKeywords(List.of("vault", "login", "password", "username", "copy"))
            .withDefaultBinding("cmd+\\"),
            invoked -> context.palette().open(invoked.window(), VaultScope.ID, Optional.empty(), Optional.empty()));
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock"))
            .withDefaultBinding("cmd+shift+l"), invoked -> lock.lock());
        lockAction.setEnabled(false);
        scope = new VaultScope(lock, service, CopyCards.declare(context), this::open, this::unlockThenSearch);
        context.palette().register(scope);
        manager = new VaultManager(lock, context.background(), ui, this::vaultChanged);
        managerDialog = new VaultManagerDialog(context, lock, manager, service, this::managerStatus, this::showGenerator, layout);
        context.actions().register(ActionSpec.of(GENERATE, "Generate SSH Key...")
            .withKeywords(List.of("vault", "ssh", "key", "generate")),
            invoked -> openThen(invoked.window(), Optional.empty(), this::showGenerator));
        // The manager's own surface stays as depicted (no extra rail menu); these reach its less frequent tasks.
        context.actions().register(ActionSpec.of(CHANGE_PASSWORD, "Change Vault Master Password...")
            .withKeywords(List.of("vault", "master", "password", "change")),
            invoked -> openThen(invoked.window(), Optional.empty(), managerDialog::changePassword));
        context.actions().register(ActionSpec.of(GRANTS, "Vault Plugin Grants...")
            .withKeywords(List.of("vault", "grants", "plugins", "revoke", "permissions")),
            invoked -> openThen(invoked.window(), Optional.empty(), managerDialog::showGrants));
        context.actions().register(ActionSpec.of(ADD_LOGIN, "Add Login...")
            .withKeywords(List.of("vault", "login", "password", "credential", "add", "new")),
            invoked -> openThen(invoked.window(), Optional.empty(), () -> managerDialog.add(VaultManager.Type.LOGIN)));
        context.actions().register(ActionSpec.of(ADD_KEY, "Add SSH Key...")
            .withKeywords(List.of("vault", "ssh", "key", "add", "new")),
            invoked -> openThen(invoked.window(), Optional.empty(), () -> managerDialog.add(VaultManager.Type.SSH_KEY)));
        context.actions().register(ActionSpec.of(ADD_NOTE, "Add Secure Note...")
            .withKeywords(List.of("vault", "secure", "note", "add", "new")),
            invoked -> openThen(invoked.window(), Optional.empty(), () -> managerDialog.add(VaultManager.Type.NOTE)));
        var fileMenu = context.menus().standard(StandardMenu.FILE);
        fileMenu.add(OPEN);
        fileMenu.add(CHANGE_PASSWORD);
        fileMenu.add(GRANTS);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 50));
//        context.rail().add(OPEN);
        refreshStatus();
        context.settings().onChanged(() -> { settings = declared.read(context.dataDirectory()); timer.setTimeout(settings.autoLock()); refreshStatus(); vaultChanged(); });
        activity = event -> timer.touch();
        Toolkit.getDefaultToolkit().addAWTEventListener(activity, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        ticker = new Timer(TICK_MILLIS, event -> tick());
        ticker.start();
    }

    @Override public void stop() {
        stopped = true;
        if (service != null) service.closeImports();
        if (ticker != null) ticker.stop();
        if (activity != null) Toolkit.getDefaultToolkit().removeAWTEventListener(activity);
        try {
            if (managerDialog != null) managerDialog.close();
            if (createDialog != null) createDialog.close();
            if (currentUnlock != null) currentUnlock.cancel();
        } finally {
            if (manager != null) manager.invalidate();
            // Jasper clears a secret this plugin copied when the plugin stops.
            if (lock != null) lock.lock();
        }
    }

    /** Explicit Open creates/unlocks when needed, then selects the requested manager row. */
    void open(WindowHandle window, Optional<UUID> select) { openThen(window, select, () -> { }); }

    private void openThen(WindowHandle window, Optional<UUID> select, Runnable after) {
        if (stopped) return;
        WindowHandle named = owner(window);
        CompletableFuture<Boolean> ready = switch (lock.state()) {
            case NO_VAULT -> createVault(named);
            case LOCKED -> service.requestUnlock(named);
            case UNLOCKED -> CompletableFuture.completedFuture(true);
        };
        ready.thenAccept(ok -> {
            if (!ok || stopped || lock.state() != LockState.UNLOCKED) return;
            managerDialog.show(named, select);
            after.run();
        }).exceptionally(failure -> {
            if (!stopped) context.notifications().group().error("Could not open Credential Vault", message(failure), failure);
            return null;
        });
    }

    /** The palette's locked row: unlock, then show the Vault scope again so the search goes on. */
    private void unlockThenSearch(WindowHandle window) {
        if (stopped) return;
        WindowHandle named = owner(window);
        service.requestUnlock(named).thenAccept(ok -> {
            if (ok && !stopped && lock.state() == LockState.UNLOCKED && named.isOpen())
                context.palette().open(named, VaultScope.ID, Optional.empty(), Optional.empty());
        }).exceptionally(failure -> {
            if (!stopped) context.notifications().group().error("Could not unlock Credential Vault", message(failure), failure);
            return null;
        });
    }

    /** Locks when the inactivity timeout has passed; the Swing ticker calls this every few seconds. */
    void tick() {
        if (lock.state() == LockState.UNLOCKED && timer.expired()) lock.lock();
        else refreshStatus();
    }

    private void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) timer.touch();
        else if (manager != null) manager.invalidate();
        service.lockStateChanged(state);
        if (lockAction != null) lockAction.setEnabled(state == LockState.UNLOCKED);
        context.events().publish(VaultApi.LOCK_STATE_CHANGED, state);
        vaultChanged();
        refreshStatus();
    }

    private CompletableFuture<Boolean> createVault(WindowHandle owner) {
        if (creating != null) { createDialog.toFront(); return attachCreate(); }
        var result = new CompletableFuture<Boolean>();
        // Setup and unlocking are the kit's modal overlay: the window dims and the prompt fades up over it.
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Create Vault", owner, true, DialogSpec.Presentation.OVERLAY));
        creating = result; createDialog = dialog;
        boolean[] success = {false}, submitted = {false};
        PasswordPanel[] form = new PasswordPanel[1];
        form[0] = PasswordPanel.create(context.kit(), settings.bindByDefault(), (password, bind) -> {
            submitted[0] = true; form[0].setBusy(true);
            lock.create(password, bind).whenComplete((ignored, failure) -> {
                if (createDialog != dialog || stopped) return;
                if (failure == null) {
                    success[0] = true; dialog.close(); result.complete(true);
                } else {
                    form[0].setBusy(false);
                    context.notifications().group().error("Could not create the vault", message(failure), failure);
                }
            });
        }, dialog::close);
        dialog.setContent(form[0].view());
        dialog.onClosed(() -> {
            form[0].close();
            if (createDialog == dialog) { createDialog = null; creating = null; }
            if (!success[0]) {
                if (submitted[0]) lock.lock();
                result.complete(false);
            }
        });
        var waiter = attachCreate();
        dialog.show();
        return waiter;
    }

    private CompletableFuture<Boolean> attachCreate() {
        var shared = creating;
        var waiter = new CompletableFuture<Boolean>(); createWaiters.add(waiter);
        shared.whenComplete((ok, failure) -> {
            if (failure == null) waiter.complete(ok); else waiter.completeExceptionally(failure);
        });
        waiter.whenComplete((ok, failure) -> ui.execute(() -> {
            createWaiters.remove(waiter);
            if (waiter.isCancelled() && createWaiters.isEmpty() && creating == shared && !shared.isDone()) createDialog.close();
        }));
        return waiter;
    }

    private void showUnlock(UnlockPrompt prompt) {
        currentUnlock = prompt;
        prompt.onDismiss(() -> { if (currentUnlock == prompt) currentUnlock = null; });
        if (!prompt.anchored() && managerDialog != null && managerDialog.showUnlock(prompt)) return;
        var presentation=prompt.presentationOwner();
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Unlock Vault", prompt.anchored()?presentation:owner(prompt.owner()), true,
            DialogSpec.Presentation.OVERLAY));
        // The footnote is the auto-lock (plan 10b Rulings 11); the rows layout ignores it.
        PasswordPanel panel = PasswordPanel.unlock(context.kit(), prompt, layout,
            settings.autoLock().isZero() ? "" : "Auto-locks after " + settings.autoLock().toMinutes() + " min idle");
        dialog.setContent(panel.view());
        boolean[] active={true};
        prompt.onPresentationRetired(()->{active[0]=false;panel.close();dialog.close();});
        prompt.onDismiss(() -> {if(active[0]){panel.clear();dialog.close();}});
        // The surface disposed the view first; close the form before the prompt can report to it.
        dialog.onClosed(() -> { panel.close();if(active[0]&&prompt.presentationOwner()==presentation)prompt.cancel(); });
        dialog.show();
    }

    private void showGrant(GrantPrompt prompt) {
        currentGrant = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Allow " + prompt.consumerName() + " to use " + prompt.descriptor().name() + "?", owner(prompt.owner()), true));
        var panel = new GrantPanel(context.kit(), prompt);
        dialog.setContent(panel.view());
        prompt.onDismiss(() -> { currentGrant = null; dialog.close(); vaultChanged(); });
        dialog.onClosed(() -> { panel.close(); prompt.cancel(); });
        dialog.show();
    }

    private void showImport(dev.jasper.vault.service.KeyImportPrompt prompt) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Import SSH keys", owner(prompt.owner()), true));
        var panel = new dev.jasper.vault.ui.KeyImportPanel(context.kit(), prompt);
        dialog.setContent(panel.view());
        prompt.result().whenComplete((value, failure) -> ui.execute(dialog::close));
        boolean[] shown = {false};
        // Setup/unlock owns its modal loop first. Never cover it with a waiting import dialog.
        var ready = prompt.onChanged(() -> {
            if (prompt.phase() != dev.jasper.vault.service.KeyImportPrompt.Phase.OPENING && !prompt.result().isDone() && !shown[0]) {
                shown[0] = true;
                javax.swing.SwingUtilities.invokeLater(() -> { if (!prompt.result().isDone()) dialog.show(); });
            }
        });
        dialog.onClosed(() -> { ready.close(); panel.close(); prompt.cancel(); });
    }

    private void showPick(PickPrompt prompt) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Choose a credential", owner(prompt.owner()), true));
        var panel = new PickerPanel(context.kit(), prompt);
        dialog.setContent(panel.view());
        prompt.onDismiss(dialog::close);
        dialog.onClosed(() -> { panel.close(); prompt.cancel(); });
        dialog.show();
    }

    /** A dialog needs an owner; a prompt raised without one (a consumer with no window yet) uses any Jasper window. */
    private WindowHandle owner(WindowHandle named) {
        if (named != null && named.isOpen()) return named;
        return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst())
            .orElseThrow(() -> new IllegalStateException("No window to show the vault dialog in"));
    }

    LockManager lockManager() { return lock; }
    VaultService service() { return service; }
    InactivityTimer timer() { return timer; }
    VaultSettings settings() { return settings; }
    UnlockPrompt currentUnlock() { return currentUnlock; }
    GrantPrompt currentGrant() { return currentGrant; }
    VaultScope scope() { return scope; }
    private void refreshStatus() {
        if (status == null) return;
        LockState state = lock.state();
        status.setText(statusText(state));
        status.setIcon(state == LockState.UNLOCKED ? unlocked : locked);
        status.setTooltip(tooltip(state, timer.remaining()));
        status.setAction(state == LockState.UNLOCKED ? LOCK : OPEN);
    }

    /** The status item's text (spec 13): "Vault: Locked", "Vault: Unlocked", or "Vault" while there is no vault yet. */
    static String statusText(LockState state) {
        return switch (state) {
            case NO_VAULT -> "Vault";
            case LOCKED -> "Vault: Locked";
            case UNLOCKED -> "Vault: Unlocked";
        };
    }

    /** "Vault locked", "No vault — click to create one", or "Vault unlocked · locks in N min" (omitted when auto-lock is off). */
    static String tooltip(LockState state, Duration remaining) {
        return switch (state) {
            case NO_VAULT -> "No vault — click to create one";
            case LOCKED -> "Vault locked";
            case UNLOCKED -> remaining.isZero() ? "Vault unlocked" : "Vault unlocked · locks in " + Math.max(1, (remaining.toSeconds() + 59) / 60) + " min";
        };
    }

    private void vaultChanged() {
        if (scope != null) scope.changed();
        if (!stopped && managerDialog != null) managerDialog.changed();
    }

    private VaultManagerDialog.Status managerStatus() {
        return new VaultManagerDialog.Status(lock.deviceSecretSource(),
            settings.autoLock().isZero() ? "off" : settings.autoLock().toMinutes() + " min",
            context.dataDirectory().resolve("vault.jv").toString());
    }

    private void showGenerator() {
        managerDialog.dialog("Generate SSH Key", close -> new KeyGeneratorForm(context.kit(), request ->
            manager.generate(settings.keysDirectory(), request.algorithm(), request.name(), request.comment(), request.username(), request.passphrase()), close));
    }

    private static String message(Throwable failure) {
        while (failure.getCause() != null && failure instanceof java.util.concurrent.CompletionException) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
