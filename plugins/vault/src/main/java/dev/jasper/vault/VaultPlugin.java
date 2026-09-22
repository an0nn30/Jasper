package dev.jasper.vault;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
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
 * Plan 6b adds the manager window, status item, rail action and palette scope.
 */
public class VaultPlugin implements Plugin {
    public static final String OPEN = "dev.jasper.vault.open";
    public static final String LOCK = "dev.jasper.vault.lock";
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
        Files.createDirectories(context.dataDirectory());
        settings = VaultSettings.read(context.config(), context.dataDirectory());
        timer = new InactivityTimer(() -> clock >= 0 ? clock : System.currentTimeMillis());
        timer.setTimeout(settings.autoLock());
        lock = new LockManager(new VaultFile(context.dataDirectory().resolve("vault.jv")), secretsFactory.apply(context), context.background(), ui, this::lockStateChanged);
        service = new VaultService(lock, () -> context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()),
            this::showUnlock, this::showGrant, this::showPick, context.notices()::error);
        context.services().publishPerConsumer(VaultApi.class, service::forConsumer);
        context.actions().register(ActionSpec.of(OPEN, "Open Vault...").withKeywords(List.of("vault", "credentials", "password", "unlock")).withDefaultBinding("F8"),
            invoked -> open(invoked.window(), Optional.empty()));
        lockAction = context.actions().register(ActionSpec.of(LOCK, "Lock Vault").withKeywords(List.of("vault", "lock")), invoked -> lock.lock());
        lockAction.setEnabled(false);
        clipboard = clipboardFactory.get();
        scope = new VaultScope(lock, service, clipboard, this::open, context.notices()::error);
        context.palette().register(scope);
        context.config().onChanged(() -> { settings = VaultSettings.read(context.config(), context.dataDirectory()); timer.setTimeout(settings.autoLock()); });
        activity = event -> timer.touch();
        Toolkit.getDefaultToolkit().addAWTEventListener(activity, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        ticker = new Timer(TICK_MILLIS, event -> tick());
        ticker.start();
    }

    @Override public void stop() {
        if (ticker != null) ticker.stop();
        if (activity != null) Toolkit.getDefaultToolkit().removeAWTEventListener(activity);
        try {
            if (clipboard != null) clipboard.close();
        } catch (RuntimeException ignored) {
            // The host may already be shutting down; locking must still zero vault state.
        } finally {
            if (lock != null) lock.lock();
        }
    }

    /** Open Vault…: create when there is no vault, unlock when locked; unlocked does nothing until 6b opens the manager. */
    void open(WindowHandle window, Optional<UUID> select) {
        switch (lock.state()) {
            case NO_VAULT -> showCreate(window);
            case LOCKED -> service.requestUnlock(window);
            case UNLOCKED -> { }
        }
    }

    /** Locks when the inactivity timeout has passed; the Swing ticker calls this every few seconds. */
    void tick() { if (lock.state() == LockState.UNLOCKED && timer.expired()) lock.lock(); }

    private void lockStateChanged(LockState state) {
        if (state == LockState.UNLOCKED) timer.touch();
        service.lockStateChanged(state);
        if (lockAction != null) lockAction.setEnabled(state == LockState.UNLOCKED);
        context.events().publish(VaultApi.LOCK_STATE_CHANGED, state);
        if (scope != null) scope.changed();
    }

    void showCreate(WindowHandle owner) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Create Vault", owner, true));
        PasswordPanel panel = PasswordPanel.create(settings.bindByDefault(), (password, bind) ->
            lock.create(password, bind).whenComplete((ignored, failure) -> {
                if (failure == null) { dialog.close(); return; }
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                context.notices().error("Could not create the vault: " + cause.getMessage());
            }), dialog::close);
        dialog.setContent(panel);
        dialog.show();
    }

    private void showUnlock(UnlockPrompt prompt) {
        currentUnlock = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Unlock Vault", owner(prompt.owner()), true));
        dialog.setContent(PasswordPanel.unlock(prompt));
        prompt.onDismiss(() -> { currentUnlock = null; dialog.close(); });
        dialog.onClosed(prompt::cancel);
        dialog.show();
    }

    private void showGrant(GrantPrompt prompt) {
        currentGrant = prompt;
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Allow " + prompt.consumerName() + " to use " + prompt.descriptor().name() + "?", owner(prompt.owner()), true));
        dialog.setContent(new GrantPanel(prompt));
        prompt.onDismiss(() -> { currentGrant = null; dialog.close(); });
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
}
