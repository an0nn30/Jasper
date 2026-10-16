package dev.jasper.vault.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.service.VaultService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Palette rows for vault logins (accounts); SSH keys stay in the manager. */
public final class VaultScope implements PaletteScope {
    public static final String ID = "dev.jasper.vault.scope";
    public static final PaletteVerb COPY_PASSWORD = new PaletteVerb("copy_password", "Copy password");
    public static final PaletteVerb COPY_USERNAME = new PaletteVerb("copy_username", "Copy username");
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open in Vault");
    public static final String LOCKED_ROW = "locked";
    private static final String SEARCH_ACTION = "dev.jasper.vault.search";

    private final LockManager lock;
    private final VaultService service;
    private final CopyCards copies;
    private final BiConsumer<WindowHandle, Optional<UUID>> openManager;
    private final Consumer<WindowHandle> unlockThenSearch;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * Copies are secret copies through {@code copies}, so Jasper clears them after 30 seconds and a card confirms each.
     * The locked row's Enter runs {@code unlockThenSearch}; with no vault yet it opens the manager to create one.
     */
    public VaultScope(LockManager lock, VaultService service, CopyCards copies,
                      BiConsumer<WindowHandle, Optional<UUID>> openManager, Consumer<WindowHandle> unlockThenSearch) {
        this.lock = lock; this.service = service; this.copies = copies;
        this.openManager = openManager; this.unlockThenSearch = unlockThenSearch;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Vault", "Search logins", List.of(COPY_PASSWORD, COPY_USERNAME, OPEN))
            .withDescription("Copy a password or username from the vault").withShortcutActionId(SEARCH_ACTION).withInAll(false)
            .withItemNoun("login");
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        LockState state = lock.state();
        if (state != LockState.UNLOCKED) {
            String title = state == LockState.NO_VAULT ? "No vault yet — press Enter to create one" : "Vault is locked — press Enter to unlock";
            return PaletteResults.of(List.of(PaletteRow.of(LOCKED_ROW, title)));
        }
        String[] tokens = query.strip().toLowerCase(Locale.ROOT).split("\\s+");
        var rows = new ArrayList<PaletteRow>();
        for (CredentialDescriptor descriptor : service.descriptors()) {
            if (rows.size() == context.maxResults()) break;
            if (descriptor.kind() == Kind.SSH_KEY) continue;
            String haystack = (descriptor.name() + " " + descriptor.subtitle()).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String token : tokens) if (!token.isEmpty() && !haystack.contains(token)) { matches = false; break; }
            if (matches) rows.add(PaletteRow.of("cred." + descriptor.id(), descriptor.name()).withDetail(descriptor.subtitle()).withTag(tag(descriptor.kind())).withToken(descriptor));
        }
        return PaletteResults.of(rows);
    }

    static String tag(Kind kind) {
        return switch (kind) { case ACCOUNT_PASSWORD -> "password"; case ACCOUNT_KEY -> "key"; case ACCOUNT_KEY_AND_PASSWORD -> "key+password"; case SSH_KEY -> "ssh key"; };
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (row.id().equals(LOCKED_ROW)) return lock.state() != LockState.UNLOCKED;
        if (lock.state() != LockState.UNLOCKED || !(row.token() instanceof CredentialDescriptor stale)) return false;
        CredentialDescriptor descriptor = service.descriptors().stream().filter(d -> d.id().equals(stale.id())).findFirst().orElse(null);
        if (descriptor == null || descriptor.kind() == Kind.SSH_KEY) return false;
        if (verb.equals(COPY_PASSWORD)) return descriptor.kind() == Kind.ACCOUNT_PASSWORD || descriptor.kind() == Kind.ACCOUNT_KEY_AND_PASSWORD;
        return verb.equals(COPY_USERNAME) || verb.equals(OPEN);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!available(row, verb, context)) return;
        if (row.id().equals(LOCKED_ROW)) {
            if (lock.state() == LockState.LOCKED) unlockThenSearch.accept(context.window());
            else openManager.accept(context.window(), Optional.empty());
            return;
        }
        if (!(row.token() instanceof CredentialDescriptor descriptor) || lock.state() != LockState.UNLOCKED) return;
        if (verb.equals(OPEN)) { openManager.accept(context.window(), Optional.of(descriptor.id())); return; }
        lock.vault().account(descriptor.id()).ifPresent(account -> {
            if (verb.equals(COPY_USERNAME)) { copies.secret("username", account.name(), account.username()); return; }
            char[] password = switch (account.auth()) {
                case Auth.Password p -> p.password();
                case Auth.KeyAndPassword both -> both.password();
                case Auth.Key key -> null;
            };
            if (password != null) copies.secret("password", account.name(), new String(password));
        });
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void changed() { listeners.forEach(Runnable::run); }
}
