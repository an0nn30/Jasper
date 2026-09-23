package dev.jasper.vault;

import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import java.time.Duration;
import javax.swing.Icon;
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
import dev.jasper.vault.ui.GrantPanel;
import dev.jasper.vault.ui.PasswordPanel;
import dev.jasper.vault.ui.PickerPanel;
import dev.jasper.vault.ui.SecretClipboard;
import dev.jasper.vault.ui.VaultScope;
import dev.jasper.vault.ui.VaultManager;
import dev.jasper.vault.ui.VaultManagerWindow;
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
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the vault to Jasper: the lock manager on the plugin's executors, the service published per
 * consumer, the Open and Lock actions, the lock-state topic, the settings file and the inactivity lock.
 * Owns the manager window, clipboard, status item, rail action and palette scope.
 */
public class VaultPlugin implements Plugin {
    public static final String OPEN = "dev.jasper.vault.open";
    public static final String LOCK = "dev.jasper.vault.lock";
    public static final String STATUS = "dev.jasper.vault.status";
    private StatusItem status;
    private Icon locked, unlocked;
    private static final int TICK_MILLIS = 5_000;

    private final Function<PluginContext, DeviceSecrets> secretsFactory;
    private final Executor ui;
    private final Supplier<SecretClipboard> clipboardFactory;
    private PluginContext context;
    private LockManager lock;
    private VaultService service;
    private InactivityTimer timer;
    private PluginAction lockAction;
    private UnlockPrompt currentUnlock;
    private GrantPrompt currentGrant;
    private Timer ticker;
    private AWTEventListener activity;
    private VaultScope scope;
    private SecretClipboard clipboard;
    private VaultSettings settings;
    public static final String GENERATE = "dev.jasper.vault.generate_key";
    private VaultManager manager;
    private VaultManagerWindow managerWindow;
    private PluginDialog createDialog;
    private CompletableFuture<Boolean> creating;
    private boolean stopped;
    /** Milliseconds for the inactivity clock; tests set it, production reads the wall clock. */
    long clock = -1;

    /** Created by the runtime: the platform keychain with the file fallback, completing on the EDT. */
    public VaultPlugin() {
        this(context -> new DeviceSecrets(KeychainStore.forPlatform(context.dataDirectory()), new FileStore(context.dataDirectory().resolve("device.secret"))), SwingUtilities::invokeLater, SecretClipboard::system);
    }

    /** For tests: the device secret store to use and the executor that stands in for the UI thread. */
    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) {
        this(secretsFactory, ui, () -> new SecretClipboard(text -> { }, Optional::empty, clear -> { }));
    }

    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui, Supplier<SecretClipboard> clipboardFactory) {
        this.secretsFactory = secretsFactory; this.ui = ui; this.clipboardFactory = clipboardFactory;
    }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        stopped = false;
        Files.createDirectories(context.dataDirectory());
        settings = VaultSettings.read(context.config(), context.dataDirectory());
        timer = new InactivityTimer(() -> clock >= 0 ? clock : System.currentTimeMillis());
        timer.setTimeout(settings.autoLock());
        lock = new LockManager(new VaultFile(context.dataDirectory().resolve("vault.jv")), secretsFactory.apply(context), context.background(), ui, this::lockStateChanged);
        service = new VaultService(lock, () -> context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()),
            this::showUnlock, this::showGrant, this::showPick, context.notices()::error);
        context.services().publishPerConsumer(VaultApi.class, service::forConsumer);
        locked = context.appearance().icon(IconName.LOCK);
        unlocked = context.appearance().icon(IconName.UNLOCK);
        context.actions().register(ActionSpec.of(OPEN, "Open Vault...").withIcon(locked).withKeywords(List.of("vault", "credentials", "password", "unlock")).withDefaultBinding("F8"),
            invoked -> open(invoked.window(), Optional.empty()));
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock")), invoked -> lock.lock());
        lockAction.setEnabled(false);
        clipboard = clipboardFactory.get();
        scope = new VaultScope(lock, service, clipboard, this::open, context.notices()::error);
        context.palette().register(scope);
        manager = new VaultManager(lock, context.background(), ui, this::vaultChanged);
        managerWindow = new VaultManagerWindow(context, lock, manager, service, clipboard, this::managerStatus, this::showGenerator);
        context.actions().register(ActionSpec.of(GENERATE, "Generate SSH Key...")
            .withKeywords(List.of("vault", "ssh", "key", "generate")),
            invoked -> openThen(invoked.window(), Optional.empty(), this::showGenerator));
        context.menus().standard(StandardMenu.FILE).add(OPEN);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 50));
        status.setText("Vault");
//        context.rail().add(OPEN);
        refreshStatus();
        context.config().onChanged(() -> { settings = VaultSettings.read(context.config(), context.dataDirectory()); timer.setTimeout(settings.autoLock()); refreshStatus(); vaultChanged(); });
        activity = event -> timer.touch();
        Toolkit.getDefaultToolkit().addAWTEventListener(activity, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        ticker = new Timer(TICK_MILLIS, event -> tick());
        ticker.start();
    }

    @Override public void stop() {
        stopped = true;
        if (ticker != null) ticker.stop();
        if (activity != null) Toolkit.getDefaultToolkit().removeAWTEventListener(activity);
        try {
            if (managerWindow != null) managerWindow.close();
            if (createDialog != null) createDialog.close();
            if (currentUnlock != null) currentUnlock.cancel();
        } finally {
            if (manager != null) manager.invalidate();
            try {
                if (clipboard != null) clipboard.close();
            } catch (RuntimeException ignored) {
                // Clipboard ownership may already have gone away during shutdown.
            } finally {
                if (lock != null) lock.lock();
            }
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
            managerWindow.show(named, select);
            after.run();
        }).exceptionally(failure -> {
            if (!stopped) context.notices().error("Could not open Credential Vault: " + message(failure));
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
        if (creating != null) { createDialog.toFront(); return creating; }
        var result = new CompletableFuture<Boolean>();
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Create Vault", owner, true));
        creating = result; createDialog = dialog;
        boolean[] success = {false}, submitted = {false};
        PasswordPanel[] form = new PasswordPanel[1];
        form[0] = PasswordPanel.create(settings.bindByDefault(), (password, bind) -> {
            submitted[0] = true; form[0].setBusy(true);
            lock.create(password, bind).whenComplete((ignored, failure) -> {
                if (createDialog != dialog || stopped) return;
                if (failure == null) {
                    success[0] = true; dialog.close(); result.complete(true);
                } else {
                    form[0].setBusy(false);
                    context.notices().error("Could not create the vault: " + message(failure));
                }
            });
        }, dialog::close);
        dialog.setContent(form[0]);
        dialog.onClosed(() -> {
            form[0].clear();
            if (createDialog == dialog) { createDialog = null; creating = null; }
            if (!success[0]) {
                if (submitted[0]) lock.lock();
                result.complete(false);
            }
        });
        dialog.show();
        return result;
    }

    private void showUnlock(UnlockPrompt prompt) {
        currentUnlock = prompt;
        prompt.onDismiss(() -> { if (currentUnlock == prompt) currentUnlock = null; });
        if (managerWindow != null && managerWindow.showUnlock(prompt)) return;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Unlock Vault", owner(prompt.owner()), true));
        PasswordPanel panel = PasswordPanel.unlock(prompt);
        dialog.setContent(panel);
        prompt.onDismiss(() -> { panel.clear(); dialog.close(); });
        dialog.onClosed(() -> { panel.clear(); prompt.cancel(); });
        dialog.show();
    }

    private void showGrant(GrantPrompt prompt) {
        currentGrant = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Allow " + prompt.consumerName() + " to use " + prompt.descriptor().name() + "?", owner(prompt.owner()), true));
        dialog.setContent(new GrantPanel(prompt));
        prompt.onDismiss(() -> { currentGrant = null; dialog.close(); vaultChanged(); });
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    private void showPick(PickPrompt prompt) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Choose a credential", owner(prompt.owner()), true));
        dialog.setContent(new PickerPanel(prompt));
        prompt.onDismiss(dialog::close);
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    /** A dialog needs an owner; a prompt raised without one (a consumer with no window yet) uses any terminal window. */
    private WindowHandle owner(WindowHandle named) {
        if (named != null && named.isOpen()) return named;
        return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst())
            .orElseThrow(() -> new IllegalStateException("No window to show the vault dialog in"));
    }

    LockManager lockManager() { return lock; }
    VaultService service() { return service; }
    InactivityTimer timer() { return timer; }
    UnlockPrompt currentUnlock() { return currentUnlock; }
    GrantPrompt currentGrant() { return currentGrant; }
    VaultScope scope() { return scope; }
    private void refreshStatus() {
        if (status == null) return;
        LockState state = lock.state();
        status.setIcon(state == LockState.UNLOCKED ? unlocked : locked);
        status.setTooltip(tooltip(state, timer.remaining()));
        status.setAction(state == LockState.UNLOCKED ? LOCK : OPEN);
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
        if (!stopped && managerWindow != null) managerWindow.changed();
    }

    private String managerStatus() {
        return "Device secret: " + lock.deviceSecretSource()
            + " | Auto-lock: " + (settings.autoLock().isZero() ? "off" : settings.autoLock().toMinutes() + " min")
            + " | " + context.dataDirectory().resolve("vault.jv");
    }

    private void showGenerator() {
        managerWindow.dialog("Generate SSH Key", close -> new KeyGeneratorForm(request ->
            manager.generate(settings.keysDirectory(), request.algorithm(), request.name(), request.comment(), request.username(), request.passphrase()), close));
    }

    private static String message(Throwable failure) {
        while (failure.getCause() != null && failure instanceof java.util.concurrent.CompletionException) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
