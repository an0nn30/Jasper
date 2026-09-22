# Credential Vault Plan 6b — UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

> **Status (2026-09-22):** Tasks 1–7 implemented on `claude/vault-6b`, based on
> `c8141b6` (6a already on local main). Task 8 documentation and repository
> verification and the independent whole-branch review are complete. Two review findings
> were fixed in `a06dcb8`, with failing-before/passing-after regressions and green full checks.
> `check :jasper-app:installDist -q` passed: 1,526 tests, 1,523 passed, three expected skips. No merge/push or GUI launch.
> **Open decision:** the spec simultaneously requires unencrypted generated keys and an
> optional generation passphrase. The user has been asked to choose. The runnable generator
> retains 6a's unencrypted format and clearly says so; full 6b acceptance awaits that answer.
>
> **Execution/deviations:** Existing worktree/branch and approved scope were resumed; the
> unfinished two-task draft was completed while settled tasks executed. Task 1 used an
> implementer/reviewer and one fix round; tasks 2 onward ran inline with one final review.
> Both username and password copies expire after 30 s (the original draft contradicted the
> spec). The locked row is enabled so Enter can unlock (SDK disabled rows cannot execute).
> Stale rows/copy timers are guarded; clipboard teardown failure cannot prevent lock.
> `737f90c` fixes pending create/unlock installation after lock; cancellation during derivation
> now invalidates the attempt. Wipeable Swing documents preserve char-array storage, with
> `Content.getString` only as a required Swing compatibility boundary, never model/codec/I/O.
> Generated-file failures clean up their files. UI rendering prompted GridBag forms, readable
> entry-kind labels and hiding the unused note area. Actual code is authoritative for these
> refinements over the initial code blocks below; system clipboard acquisition is lazy so
> bundled-plugin startup also works headlessly, and the app integration fixture expects its rail/status.
> Review fixes preserve old keys on failed password writes, serialize queued saves behind rekey,
> and cancel closed password forms before commit. After commit starts the button says Close
> and the form explains that closing will not cancel; detached write failures are reported.
> No public consumer API or file-format change. Native acceptance and the key-generation choice remain pending.

**Goal:** The user-facing side of the Credential Vault: a Vault palette scope, a padlock status item and rail action, the manager window (accounts, keys, notes, grants, change password, lock), the editors, the key generator dialog, and the documentation.

**Architecture:** Every screen is a `JPanel` with package-private fields that tests drive headless; the host supplies windows and dialogs. UI logic that does not need Swing (`VaultManager`: listing, add/update/delete with save, grant revocation, key import) is a plain class over `LockManager` and is tested on its own. The clipboard is injected so the 30-second clear is testable. `VaultPlugin` grows the status item, rail action, scope and manager wiring; the 6a lock/prompt lifecycle rejects late results and the generator cleans up failed file writes.

**Tech Stack:** Java 25 (JBR) Swing, `jasper-sdk` 0.7 (palette, status bar, rail, windows), BouncyCastle (already bundled), JUnit 6 + AssertJ, `jasper-sdk-testkit`.

**Spec:** `docs/superpowers/specs/2026-09-22-jasper-vault-design.md` (sections 8, 9, 10, 11); plan 6a is `docs/superpowers/plans/2026-09-22-jasper-vault-plan-6a-core-and-api.md`.

## Review Focus

- A stale palette row after deletion/auth change must not copy secrets or throw; Task 1.
- A second copy must get its full 30 seconds; stopping clears owned contents; Task 1.
- A pending edit or generation completed after lock must not repopulate the vault; manager tasks.
- File-write errors must stay visible and must not present a failed save as success; manager tasks.
- Closing a secret editor or locking the vault clears form contents and cancels pending delivery; editor/window tasks.

## Global Constraints

- Plugin compiles against `jasper-sdk` and bundled `bcprov` only; `capabilities = ["palette.contribute"]` from this plan on.
- The palette scope copies, never pastes: Copy password puts the secret on the clipboard and clears it after 30 s unless the clipboard changed; Copy username has the same 30-second expiry.
- No `String` holds a secret except where Swing forces it: a `JPasswordField` is read as `char[]`; the clipboard's `StringSelection` is the one accepted exception and is documented as such.
- All vault edits happen on the UI thread through `LockManager.vault()` followed by `LockManager.save()`.
- Headless tests only; never launch the GUI; commit per task with the `Co-Authored-By: Codex <noreply@openai.com>` trailer; never commit on `main` (branch `claude/vault-6b` in `.worktrees/vault-6b`).
- No raw control, private-use or surrogate characters in source. Ordinary punctuation is allowed.
- Run `./gradlew :jasper-plugin-vault:test -q` per task; `./gradlew check -q` before the final commit.

---

### Task 1: The Vault palette scope and the secret clipboard

**Files:**
- Modify: `plugins/vault/src/main/resources/plugin.toml` (`capabilities = ["palette.contribute"]`)
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/SecretClipboard.java`, `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultScope.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java` (third constructor argument, scope registration, `changed()` calls)
- Modify: `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java` (`INFO` gains the capability; a scope assertion)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/SecretClipboardTest.java`, `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultScopeTest.java`

**Interfaces:**
- Consumes: `LockManager`, `VaultService.descriptors()`, `CredentialDescriptor`, `Kind`, SDK palette types.
- Produces: `SecretClipboard(Consumer<String> put, Supplier<Optional<String>> read, Consumer<Runnable> armClear)` with `copySecret(String)`, `copy(String)`, `clearIfUnchanged()`, `static SecretClipboard system()`; `VaultScope(LockManager, VaultService, SecretClipboard, BiConsumer<WindowHandle, Optional<UUID>> openManager)` with `ID = "dev.jasper.vault.scope"`, verbs `COPY_PASSWORD`, `COPY_USERNAME`, `OPEN`, `LOCKED_ROW = "locked"`, `void changed()`; `VaultPlugin(Function<PluginContext, DeviceSecrets>, Executor ui, Supplier<SecretClipboard>)` (the two-argument test constructor stays and uses a no-op clipboard); package-private `VaultPlugin.scope()`, `open(WindowHandle, Optional<UUID>)` (Task 3 gives the id a meaning).

- [x] **Step 1: Write the failing tests**

`ui/SecretClipboardTest.java`:

```java
package dev.jasper.vault.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecretClipboardTest {
    @Test void anOlderTimerCannotClearANewerCopyAndCloseClearsTheCurrentCopy() {
        clipboard.copySecret("first");
        clipboard.copySecret("second");
        armed.getFirst().run();
        assertThat(contents).isEqualTo("second");
        clipboard.close();
        assertThat(contents).isEmpty();
    }

    String contents = "";
    final List<Runnable> armed = new ArrayList<>();
    final SecretClipboard clipboard = new SecretClipboard(text -> contents = text, () -> Optional.of(contents), armed::add);

    @Test void aSecretIsClearedLaterUnlessTheClipboardChanged() {
        clipboard.copySecret("s3cret");
        assertThat(contents).isEqualTo("s3cret");
        assertThat(armed).hasSize(1);
        armed.getFirst().run();
        assertThat(contents).as("unchanged: cleared").isEmpty();
        clipboard.copySecret("again");
        contents = "something the user copied";
        armed.get(1).run();
        assertThat(contents).as("changed meanwhile: left alone").isEqualTo("something the user copied");
    }

    @Test void aPlainCopyIsNeverCleared() {
        clipboard.copy("deploy");
        assertThat(armed).isEmpty();
        clipboard.clearIfUnchanged();
        assertThat(contents).isEqualTo("deploy");
    }
}
```

`ui/VaultScopeTest.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultScopeTest {
    static final UUID PROD = UUID.randomUUID(), BASTION = UUID.randomUUID(), KEY = UUID.randomUUID();

    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final WindowHandle window = window();
    final PaletteQuery query = new PaletteQuery(window, Optional.empty(), 5, true);
    String clipboard = "";
    final List<Runnable> armed = new ArrayList<>();
    final List<String> opened = new ArrayList<>();
    LockManager lock;
    VaultScope scope;

    VaultScope scope(Path dir) {
        var keychain = new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, dir.resolve("x"));
        lock = new LockManager(new VaultFile(dir.resolve("vault.jv")), new DeviceSecrets(keychain, new FileStore(dir.resolve("device.secret"))), Runnable::run, Runnable::run, state -> { });
        var service = new VaultService(lock, () -> Optional.of(window), prompt -> { }, prompt -> { }, prompt -> { }, notice -> { });
        scope = new VaultScope(lock, service, new SecretClipboard(text -> clipboard = text, () -> Optional.of(clipboard), armed::add),
            (owner, id) -> opened.add(id.map(UUID::toString).orElse("-")));
        return scope;
    }

    void populate() {
        lock.create("pw".toCharArray(), false).join();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
        lock.vault().accounts().add(new Account(BASTION, "bastion", "ops", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        lock.vault().keys().add(new SshKey(KEY, "laptop", "ed25519", "SHA256:abc", "", Path.of("/k"), Path.of("/k.pub"), Instant.EPOCH));
    }

    static PaletteRow row(List<PaletteRow> rows, String id) { return rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow(); }

    @Test void specNamesTheOpenActionAndThreeVerbs(@TempDir Path dir) {
        scope(dir);
        assertThat(scope.spec().id()).isEqualTo(VaultScope.ID);
        assertThat(scope.spec().aliases()).containsExactly("vault", "cred");
        assertThat(scope.spec().verbs()).containsExactly(VaultScope.COPY_PASSWORD, VaultScope.COPY_USERNAME, VaultScope.OPEN);
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.vault.open");
    }

    @Test void lockedAndMissingVaultsShowOneRowWhoseEnterOpensTheVault(@TempDir Path dir) {
        scope(dir);
        List<PaletteRow> rows = scope.search("anything", query).rows();
        assertThat(rows).singleElement().satisfies(row -> { assertThat(row.id()).isEqualTo(VaultScope.LOCKED_ROW); assertThat(row.title()).contains("No vault yet"); });
        assertThat(scope.available(rows.getFirst(), VaultScope.COPY_PASSWORD, query)).isTrue();
        scope.execute(rows.getFirst(), VaultScope.COPY_PASSWORD, query);
        assertThat(opened).containsExactly("-");
        populate();
        lock.lock();
        rows = scope.search("", query).rows();
        assertThat(rows.getFirst().title()).contains("locked");
        assertThat(clipboard).isEmpty();
    }

    @Test void rowsFilterByNameAndUsernameAndVerbsFollowTheKind(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        assertThat(all).extracting(PaletteRow::title).containsExactly("prod", "bastion", "laptop");
        assertThat(all.get(0).detail()).contains("deploy");
        assertThat(all.get(0).tag()).contains("password");
        assertThat(all.get(1).tag()).contains("key");
        assertThat(all.get(2).tag()).contains("ssh key");
        assertThat(scope.search("ops", query).rows()).extracting(PaletteRow::title).containsExactly("bastion");
        assertThat(scope.search("LAP", query).rows()).extracting(PaletteRow::title).containsExactly("laptop");
        assertThat(scope.search("nothing", query).rows()).isEmpty();
        assertThat(scope.search("", new PaletteQuery(window, Optional.empty(), 2, true)).rows()).hasSize(2);
        PaletteRow prod = row(all, "cred." + PROD), bastion = row(all, "cred." + BASTION), laptop = row(all, "cred." + KEY);
        assertThat(scope.available(prod, VaultScope.COPY_PASSWORD, query)).isTrue();
        assertThat(scope.available(bastion, VaultScope.COPY_PASSWORD, query)).as("a key account has no password").isFalse();
        assertThat(scope.available(bastion, VaultScope.COPY_USERNAME, query)).isTrue();
        assertThat(scope.available(laptop, VaultScope.COPY_USERNAME, query)).as("a bare key has no username").isFalse();
        assertThat(scope.available(laptop, VaultScope.OPEN, query)).isTrue();
    }

    @Test void staleRowsCannotCopyAfterAuthChangesDeletionOrLock(@TempDir Path dir) {
        scope(dir);
        populate();
        PaletteRow saved = scope.search("prod", query).rows().getFirst();
        lock.vault().remove(PROD);
        assertThat(scope.available(saved, VaultScope.COPY_PASSWORD, query)).isFalse();
        scope.execute(saved, VaultScope.COPY_PASSWORD, query);
        assertThat(clipboard).isEmpty();
        lock.vault().accounts().add(new Account(PROD, "prod", "deploy", new Auth.Key(Path.of("/k"), null), Instant.EPOCH, Instant.EPOCH));
        assertThat(scope.available(saved, VaultScope.COPY_PASSWORD, query)).isFalse();
        lock.lock();
        assertThat(scope.available(saved, VaultScope.COPY_USERNAME, query)).isFalse();
    }

    @Test void copyingPutsTheSecretOnTheClipboardWithAClearArmedAndOpenSelectsTheRow(@TempDir Path dir) {
        scope(dir);
        populate();
        List<PaletteRow> all = scope.search("", query).rows();
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_PASSWORD, query);
        assertThat(clipboard).isEqualTo("s3cret");
        assertThat(armed).hasSize(1);
        scope.execute(row(all, "cred." + PROD), VaultScope.COPY_USERNAME, query);
        assertThat(clipboard).isEqualTo("deploy");
        assertThat(armed).as("usernames expire too").hasSize(2);
        scope.execute(row(all, "cred." + KEY), VaultScope.OPEN, query);
        assertThat(opened).containsExactly(KEY.toString());
        int[] changes = {0};
        scope.onChanged(() -> changes[0]++);
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
    }
}
```

In `VaultPluginTest.java`: `INFO` becomes `new PluginInfo("dev.jasper.vault", "Credential Vault", "0.1.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE))`, and `publishesTheServiceActionsAndLockStateEvents` gains, after the `host.actions()` assertion:

```java
            assertThat(host.scopes()).containsExactly("dev.jasper.vault.scope|Vault|copy_password,copy_username,open");
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement**

`plugin.toml`: `capabilities = ["palette.contribute"]`.

`ui/SecretClipboard.java`:

```java
package dev.jasper.vault.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * The clipboard with a timed clear: a secret copied here is wiped after {@value #CLEAR_MILLIS} ms unless the
 * user copied something else meanwhile. The system clipboard takes a {@code String}; that copy is the one
 * place a secret leaves array form, and it is the point of the feature.
 */
public final class SecretClipboard implements AutoCloseable {
    public static final int CLEAR_MILLIS = 30_000;
    private final Consumer<String> put;
    private final Supplier<Optional<String>> read;
    private final Consumer<Runnable> armClear;
    private String pending;
    private long generation;

    public SecretClipboard(Consumer<String> put, Supplier<Optional<String>> read, Consumer<Runnable> armClear) {
        this.put = put; this.read = read; this.armClear = armClear;
    }

    /** The AWT system clipboard with a Swing timer for the clear. */
    public static SecretClipboard system() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        var timer = new Timer(CLEAR_MILLIS, null);
        timer.setRepeats(false);
        return new SecretClipboard(text -> clipboard.setContents(new StringSelection(text), null), () -> {
            try { return Optional.ofNullable((String) clipboard.getData(DataFlavor.stringFlavor)); }
            catch (Exception unavailable) { return Optional.empty(); }
        }, clear -> {
            for (var listener : timer.getActionListeners()) timer.removeActionListener(listener);
            timer.addActionListener(event -> clear.run());
            timer.restart();
        });
    }

    /** Copies a secret and arms the clear. */
    public void copySecret(String secret) {
        put.accept(secret);
        pending = secret;
        long copied = ++generation;
        armClear.accept(() -> { if (copied == generation) clearIfUnchanged(); });
    }

    /** Copies something that may stay. */
    public void copy(String text) { put.accept(text); pending = null; generation++; }

    /** Wipes the clipboard if it still holds the last secret. */
    public void clearIfUnchanged() {
        if (pending == null) return;
        try { if (read.get().map(pending::equals).orElse(false)) put.accept(""); }
        finally { pending = null; generation++; }
    }

    @Override public void close() { clearIfUnchanged();
    }
}
```

`ui/VaultScope.java`:

```java
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

/**
 * {@code >vault}: accounts and keys by name or username. Copy password (cleared after 30 s), Copy username,
 * Open in Vault. Locked or without a vault, one row whose Enter opens the vault.
 */
public final class VaultScope implements PaletteScope {
    public static final String ID = "dev.jasper.vault.scope";
    public static final PaletteVerb COPY_PASSWORD = new PaletteVerb("copy_password", "Copy password");
    public static final PaletteVerb COPY_USERNAME = new PaletteVerb("copy_username", "Copy username");
    public static final PaletteVerb OPEN = new PaletteVerb("open", "Open in Vault");
    public static final String LOCKED_ROW = "locked";
    static final String OPEN_ACTION = "dev.jasper.vault.open";

    private final LockManager lock;
    private final VaultService service;
    private final SecretClipboard clipboard;
    private final BiConsumer<WindowHandle, Optional<UUID>> openManager;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public VaultScope(LockManager lock, VaultService service, SecretClipboard clipboard, BiConsumer<WindowHandle, Optional<UUID>> openManager) {
        this.lock = lock; this.service = service; this.clipboard = clipboard; this.openManager = openManager;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Vault", "Search accounts and keys, or > to switch scope", List.of(COPY_PASSWORD, COPY_USERNAME, OPEN))
            .withDescription("Copy a password or username from the vault").withAliases(List.of("vault", "cred")).withShortcutActionId(OPEN_ACTION);
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
        if (descriptor == null) return false;
        if (verb.equals(COPY_PASSWORD)) return descriptor.kind() == Kind.ACCOUNT_PASSWORD || descriptor.kind() == Kind.ACCOUNT_KEY_AND_PASSWORD;
        if (verb.equals(COPY_USERNAME)) return descriptor.kind() != Kind.SSH_KEY;
        return verb.equals(OPEN);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!available(row, verb, context)) return;
        if (row.id().equals(LOCKED_ROW)) { openManager.accept(context.window(), Optional.empty()); return; }
        if (!(row.token() instanceof CredentialDescriptor descriptor) || lock.state() != LockState.UNLOCKED) return;
        if (verb.equals(OPEN)) { openManager.accept(context.window(), Optional.of(descriptor.id())); return; }
        lock.vault().account(descriptor.id()).ifPresent(account -> {
            if (verb.equals(COPY_USERNAME)) { clipboard.copySecret(account.username()); return; }
            char[] password = switch (account.auth()) {
                case Auth.Password p -> p.password();
                case Auth.KeyAndPassword both -> both.password();
                case Auth.Key key -> null;
            };
            if (password != null) clipboard.copySecret(new String(password));
        });
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** The plugin calls this after every lock transition and save. */
    public void changed() { listeners.forEach(Runnable::run); }
}
```

`VaultPlugin.java` changes:

```java
    private final Supplier<SecretClipboard> clipboardFactory;
    private VaultScope scope;
    private SecretClipboard clipboard;

    public VaultPlugin() {
        this(context -> new DeviceSecrets(KeychainStore.forPlatform(context.dataDirectory()), new FileStore(context.dataDirectory().resolve("device.secret"))),
            SwingUtilities::invokeLater, SecretClipboard::system);
    }

    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui) {
        this(secretsFactory, ui, () -> new SecretClipboard(text -> { }, Optional::empty, clear -> { }));
    }

    VaultPlugin(Function<PluginContext, DeviceSecrets> secretsFactory, Executor ui, Supplier<SecretClipboard> clipboardFactory) {
        this.secretsFactory = secretsFactory; this.ui = ui; this.clipboardFactory = clipboardFactory;
    }
```

In `start`, after the actions are registered:

```java
        clipboard = clipboardFactory.get();
        scope = new VaultScope(lock, service, clipboard, this::open);
        context.palette().register(scope);
```

The Open action handler becomes `invoked -> open(invoked.window(), Optional.empty())`, and `open` takes the optional id (Task 3 uses it):

```java
    /** Open Vault…: create when there is no vault, unlock when locked; unlocked, the manager (Task 3). */
    void open(WindowHandle window, Optional<UUID> select) {
        switch (lock.state()) {
            case NO_VAULT -> showCreate(window);
            case LOCKED -> service.requestUnlock(window);
            case UNLOCKED -> { }
        }
    }
```

`lockStateChanged` ends with `if (scope != null) scope.changed();`. Add `VaultScope scope() { return scope; }`. Imports: `java.util.Optional`, `java.util.UUID`, `java.util.function.Supplier`, `dev.jasper.vault.ui.SecretClipboard`, `dev.jasper.vault.ui.VaultScope`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): the Vault palette scope with a self-clearing clipboard"
```

---

### Task 2: Padlock status item and rail action

**Files:**
- Create: `plugins/vault/src/main/resources/dev/jasper/vault/lock.svg`, `lock-open.svg`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java` (new test)

**Interfaces:**
- Produces: status item `dev.jasper.vault.status` (right side, priority 50); `VaultPlugin.STATUS = "dev.jasper.vault.status"`; `static String tooltip(LockState, Duration remaining)`.

- [x] **Step 1: Write the failing test**

Add to `VaultPluginTest.java`:

```java
    @Test void thePadlockFollowsTheLockStateAndTheRailOpensTheVault() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.rail()).containsExactly(VaultPlugin.OPEN);
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|No vault — click to create one|dev.jasper.vault.open");
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.tick();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|Vault unlocked · locks in 15 min|dev.jasper.vault.lock");
            plugin.clock = Duration.ofMinutes(14).toMillis() + 1;
            plugin.tick();
            assertThat(host.status().getFirst()).contains("locks in 1 min");
            plugin.lockManager().lock();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|Vault locked|dev.jasper.vault.open");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ZERO)).isEqualTo("Vault unlocked");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ofSeconds(30))).isEqualTo("Vault unlocked · locks in 1 min");
        }
    }
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: compilation failure (`STATUS`, `tooltip`).

- [x] **Step 3: Implement**

`lock.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#6e6e6e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="5" y="11" width="14" height="10" rx="2" />
  <circle cx="12" cy="16" r="1" />
  <path d="M8 11v-4a4 4 0 0 1 8 0v4" />
</svg>
```

`lock-open.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#6e6e6e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="5" y="11" width="14" height="10" rx="2" />
  <circle cx="12" cy="16" r="1" />
  <path d="M8 11v-5a4 4 0 0 1 8 0" />
</svg>
```

`VaultPlugin.java`: fields `public static final String STATUS = "dev.jasper.vault.status";`, `private StatusItem status; private Icon locked, unlocked;`. In `start`, after the scope registration:

```java
        locked = context.appearance().icon("dev/jasper/vault/lock.svg");
        unlocked = context.appearance().icon("dev/jasper/vault/lock-open.svg");
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 50));
        status.setText("Vault");
        context.rail().add(OPEN);
        refreshStatus();
```

The Open action gets `.withIcon(locked)` — move the two `appearance().icon` calls above the action registration so the icon exists. Add:

```java
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
```

`lockStateChanged` calls `refreshStatus()` after the scope notification; `tick()` becomes:

```java
    void tick() {
        if (lock.state() == LockState.UNLOCKED && timer.expired()) lock.lock();
        else refreshStatus();
    }
```

Imports: `dev.jasper.sdk.ui.Side`, `dev.jasper.sdk.ui.StatusItem`, `dev.jasper.sdk.ui.StatusItemSpec`, `javax.swing.Icon`, `java.time.Duration`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test -q`
Expected: PASS. The fake `Appearance.icon` returns a blank icon; `status()` renders `id|SIDE|text|tooltip|actionId`.

- [x] **Step 5: Commit**

```bash
git add plugins/vault
git commit -m "feat(vault): padlock status item and rail action"
```

---

### Task 3: Manager operations with save failure and lock guards

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManager.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultUiFixture.java`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerTest.java`

**Interfaces:**
- Consumes the existing `LockManager.vault()`, `save()`, `state()` and model records; mutation is on the UI executor.
- Produces `VaultManager(LockManager, Executor background, Executor ui, Runnable changed)`; `rows()`, `grants()`, `entry(UUID)`, `saveAccount(Account)`, `saveNote(Note)`, `saveKey(SshKey)`, `delete(UUID, boolean)`, `revoke(Grant)`, `publicKey(UUID)`, `invalidate()` and `busy()`.
- The manager takes ownership of secret arrays supplied to its save methods, including on rejection. Replacement saves retain the old record until persistence succeeds. A failed write restores that record if this is still the same unlock session. `invalidate()` is called at every lock and stop; it immediately zeroes retained old secrets.
- Key-file deletion happens only after the record deletion saves. A file deletion failure reports that the record was removed and names the files requiring attention; it does not resurrect a record with a potentially deleted private key.

- [x] **Step 1: Add these failing tests and the reusable fixture**

`VaultUiFixture.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

final class VaultUiFixture implements AutoCloseable {
    final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    final Executor background = queue::add;
    final LockManager lock;
    final VaultManager manager;
    int changes;

    VaultUiFixture(Path directory) {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> {
            throw new UncheckedIOException(new IOException("No keychain in this test"));
        }, directory.resolve("unused")), new FileStore(directory.resolve("device.secret")));
        lock = new LockManager(new VaultFile(directory.resolve("vault.jv")), secrets,
            background, Runnable::run, state -> { });
        manager = new VaultManager(lock, background, Runnable::run, () -> changes++);
        var create = lock.create("test-password".toCharArray(), false);
        drain();
        create.join();
    }

    void drain() { while (!queue.isEmpty()) queue.removeFirst().run(); }
    @Override public void close() { manager.invalidate(); lock.lock(); drain(); }
}
```

`VaultManagerTest.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultManagerTest {
    @TempDir Path directory;

    static Account account(UUID id, char[] password) {
        return new Account(id, "Production", "deploy", new Auth.Password(password), Instant.EPOCH, Instant.EPOCH);
    }

    @Test void editsSaveAndDeleteRemovesGrantsAndSecrets() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            f.manager.saveAccount(account(id, fresh)); f.drain();
            assertThat(old).containsOnly((char) 0);
            assertThat(f.manager.rows()).singleElement().satisfies(row -> assertThat(row.subtitle()).isEqualTo("deploy"));
            f.lock.vault().grants().add(new Grant("dev.jasper.ssh", id));
            f.manager.delete(id, false); f.drain();
            assertThat(fresh).containsOnly((char) 0);
            assertThat(f.manager.rows()).isEmpty();
            assertThat(f.manager.grants()).isEmpty();
            f.lock.lock();
            var unlock = f.lock.unlock("test-password".toCharArray()); f.drain(); unlock.join();
            assertThat(f.manager.rows()).isEmpty();
        }
    }

    @Test void failedReplacementRestoresOldRecordAndWipesRejectedSecret() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            Files.delete(directory.resolve("vault.jv"));
            Files.createDirectory(directory.resolve("vault.jv"));
            Files.writeString(directory.resolve("vault.jv/block"), "block replacement");
            var save = f.manager.saveAccount(account(id, fresh)); f.drain();
            assertThat(save).isCompletedExceptionally();
            assertThat(((Auth.Password) f.lock.vault().account(id).orElseThrow().auth()).password()).isEqualTo(old);
            assertThat(fresh).containsOnly((char) 0);
            assertThat(old).containsExactly('o', 'l', 'd');
        }
    }

    @Test void lockingWhileASaveWaitsWipesBothVersionsWithoutRestoringEither() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            char[] old = "old".toCharArray(), fresh = "new".toCharArray();
            f.manager.saveAccount(account(id, old)); f.drain();
            f.manager.saveAccount(account(id, fresh));
            f.manager.invalidate(); f.lock.lock();
            assertThat(old).containsOnly((char) 0);
            assertThat(fresh).containsOnly((char) 0);
            f.drain();
            assertThat(f.manager.rows()).isEmpty();
        }
    }

    @Test void notesAreManagerOnlyAndRevocationPersists() {
        try (var f = new VaultUiFixture(directory)) {
            UUID id = UUID.randomUUID();
            f.manager.saveNote(new Note(id, "Recovery", "a\nb".toCharArray(), Instant.EPOCH)); f.drain();
            Grant grant = new Grant("dev.jasper.ssh", id);
            f.lock.vault().grants().add(grant);
            f.manager.revoke(grant); f.drain();
            assertThat(f.manager.rows().getFirst().type()).isEqualTo(VaultManager.Type.NOTE);
            assertThat(f.manager.grants()).isEmpty();
        }
    }

    @Test void deletingAKeyKeepsFilesUnlessExplicitlySelected() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Path privatePath = directory.resolve("key"), publicPath = directory.resolve("key.pub");
            Files.writeString(privatePath, "private fixture"); Files.writeString(publicPath, "public fixture");
            SshKey key = new SshKey(UUID.randomUUID(), "Laptop", "ed25519", "SHA256:test", "",
                privatePath, publicPath, Instant.EPOCH);
            f.manager.saveKey(key); f.drain();
            f.manager.delete(key.id(), false); f.drain();
            assertThat(privatePath).exists(); assertThat(publicPath).exists();
            f.manager.saveKey(key); f.drain();
            var deletion = f.manager.delete(key.id(), true); f.drain(); deletion.join();
            assertThat(privatePath).doesNotExist(); assertThat(publicPath).doesNotExist();
        }
    }

    @Test void overlappingEditsRejectAndWipeTheRejectedInput() {
        try (var f = new VaultUiFixture(directory)) {
            f.manager.saveNote(new Note(UUID.randomUUID(), "One", new char[] {'1'}, Instant.EPOCH));
            char[] rejected = {'2'};
            assertThat(f.manager.saveNote(new Note(UUID.randomUUID(), "Two", rejected, Instant.EPOCH))).isCompletedExceptionally();
            assertThat(rejected).containsOnly((char) 0);
            f.drain();
        }
    }
}
```

- [x] **Step 2: Run the tests and confirm missing `VaultManager` fails compilation**

Run: `./gradlew :jasper-plugin-vault:test --tests '*VaultManagerTest' -q`.

- [x] **Step 3: Add `VaultManager.java`**

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.model.Vault;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import dev.jasper.vault.lock.LockManager;

/** UI-thread editing operations; asynchronous I/O completes on the supplied UI executor. */
public final class VaultManager {
    public enum Type { LOGIN, SSH_KEY, NOTE }
    public record Row(UUID id, String name, String subtitle, Type type) {
        @Override public String toString() { return name + " (" + type + ")"; }
    }
    private final LockManager lock;
    private final Executor background, ui;
    private final Runnable changed;
    private boolean busy;
    private long generation;
    private Runnable retainedCleanup = () -> { };

    public VaultManager(LockManager lock, Executor background, Executor ui, Runnable changed) {
        this.lock = lock; this.background = background; this.ui = ui; this.changed = changed;
    }
    public boolean busy() { return busy; }
    public List<Row> rows() {
        if (lock.state() != LockState.UNLOCKED) return List.of();
        var rows = new ArrayList<Row>();
        lock.vault().accounts().forEach(a -> rows.add(new Row(a.id(), a.name(), a.username(), Type.LOGIN)));
        lock.vault().keys().forEach(k -> rows.add(new Row(k.id(), k.name(), k.fingerprint(), Type.SSH_KEY)));
        lock.vault().notes().forEach(n -> rows.add(new Row(n.id(), n.name(), "Secure note", Type.NOTE)));
        return List.copyOf(rows);
    }
    public List<Grant> grants() {
        return lock.state() == LockState.UNLOCKED ? List.copyOf(lock.vault().grants()) : List.of();
    }
    /** Borrowed model value: use synchronously on the UI thread; never retain it in a window. */
    public Optional<Object> entry(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return Optional.empty();
        Vault v = lock.vault();
        return v.account(id).map(a -> (Object) a).or(() -> v.key(id).map(k -> (Object) k))
            .or(() -> v.notes().stream().filter(n -> n.id().equals(id)).map(n -> (Object) n).findFirst());
    }
    public CompletableFuture<Void> saveAccount(Account value) {
        if (!editable()) { value.auth().zero(); return rejected(); }
        Vault v = lock.vault();
        Account old = v.account(value.id()).orElse(null);
        return replace(v.accounts(), old, value, () -> { if (old != null) old.auth().zero(); }, value.auth()::zero);
    }
    public CompletableFuture<Void> saveNote(Note value) {
        if (!editable()) { value.zero(); return rejected(); }
        Vault v = lock.vault();
        Note old = v.notes().stream().filter(n -> n.id().equals(value.id())).findFirst().orElse(null);
        return replace(v.notes(), old, value, () -> { if (old != null) old.zero(); }, value::zero);
    }
    public CompletableFuture<Void> saveKey(SshKey value) {
        if (!editable()) return rejected();
        return replace(lock.vault().keys(), lock.vault().key(value.id()).orElse(null), value, () -> { }, () -> { });
    }
    private <T> CompletableFuture<Void> replace(List<T> list, T old, T value, Runnable oldCleanup, Runnable newCleanup) {
        int index = old == null ? list.size() : list.indexOf(old);
        return edit(v -> { if (old == null) list.add(value); else list.set(index, value); },
            () -> { if (old == null) list.remove(value); else list.set(index, old); }, oldCleanup, newCleanup);
    }
    public CompletableFuture<Void> revoke(Grant grant) {
        if (!editable()) return rejected();
        Vault v = lock.vault();
        boolean existed = v.grants().contains(grant);
        return edit(ignored -> v.grants().remove(grant), () -> { if (existed) v.grants().add(grant); }, () -> { }, () -> { });
    }
    public CompletableFuture<Void> delete(UUID id, boolean deleteFiles) {
        if (!editable()) return rejected();
        Vault v = lock.vault();
        Object value = entry(id).orElseThrow(() -> new IllegalArgumentException("The entry no longer exists"));
        List<Grant> grants = v.grants().stream().filter(g -> g.credentialId().equals(id)).toList();
        Runnable wipe = () -> { if (value instanceof Account a) a.auth().zero(); if (value instanceof Note n) n.zero(); };
        var saved = edit(ignored -> {
            v.accounts().remove(value); v.keys().remove(value); v.notes().remove(value); v.grants().removeAll(grants);
        }, () -> {
            if (value instanceof Account a) v.accounts().add(a);
            if (value instanceof SshKey k) v.keys().add(k);
            if (value instanceof Note n) v.notes().add(n);
            v.grants().addAll(grants);
        }, wipe, () -> { });
        if (!deleteFiles || !(value instanceof SshKey key)) return saved;
        return saved.thenCompose(ignored -> io(() -> {
            IOException failure = null;
            for (var path : List.of(key.privatePath(), key.publicPath())) {
                try { Files.deleteIfExists(path); }
                catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            if (failure != null) throw new IOException("Entry removed; could not delete all key files: "
                + key.privatePath() + ", " + key.publicPath(), failure);
            return null;
        }));
    }
    public CompletableFuture<String> publicKey(UUID id) {
        if (lock.state() != LockState.UNLOCKED) return CompletableFuture.failedFuture(new IllegalStateException("The vault is locked"));
        SshKey key = lock.vault().key(id).orElseThrow(() -> new IllegalArgumentException("Select an SSH key"));
        long expected = generation;
        return io(() -> {
            try (var in = Files.newInputStream(key.publicPath())) {
                byte[] bytes = in.readNBytes(65_537);
                if (bytes.length > 65_536) throw new IOException("Public key file is too large");
                return new String(bytes, StandardCharsets.US_ASCII);
            }
        }).thenApply(text -> {
            if (generation != expected || lock.state() != LockState.UNLOCKED) throw new IllegalStateException("The vault was locked meanwhile");
            return text;
        });
    }
    /** Called on lock/stop, even if a save is still pending. */
    public void invalidate() { generation++; retainedCleanup.run(); retainedCleanup = () -> { }; }
    private boolean editable() { return !busy && lock.state() == LockState.UNLOCKED; }
    private CompletableFuture<Void> rejected() {
        return CompletableFuture.failedFuture(new IllegalStateException(busy ? "An edit is still saving" : "The vault is locked"));
    }
    private CompletableFuture<Void> edit(Consumer<Vault> apply, Runnable undo, Runnable committed, Runnable rejected) {
        Vault original = lock.vault();
        long expected = generation;
        busy = true; retainedCleanup = committed;
        CompletableFuture<Void> save;
        try { apply.accept(original); save = lock.save(); }
        catch (RuntimeException failure) { save = CompletableFuture.failedFuture(failure); }
        return save.handle((ignored, failure) -> {
            boolean same = expected == generation && lock.state() == LockState.UNLOCKED && lock.vault() == original;
            if (failure == null) committed.run();
            else if (same) { undo.run(); rejected.run(); }
            else { committed.run(); rejected.run(); }
            retainedCleanup = () -> { }; busy = false;
            changed.run();
            if (failure != null) throw new java.util.concurrent.CompletionException(failure);
            return null;
        });
    }
    private <T> CompletableFuture<T> io(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        try {
            background.execute(() -> {
                try { T value = operation.call(); ui.execute(() -> result.complete(value)); }
                catch (Exception failure) { ui.execute(() -> result.completeExceptionally(failure)); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }
}
```

- [x] **Step 4: Run manager tests, then the vault module tests**

Run: `./gradlew :jasper-plugin-vault:test -q`.
Expected: PASS. The deliberately unwritable target is a nonempty directory, so the failure test also works when the test process has permission to write ordinary files.

- [x] **Step 5: Commit the operation layer and its tests**

```bash
git add plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManager.java plugins/vault/src/test/java/dev/jasper/vault/ui/VaultUiFixture.java plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerTest.java
git commit -m "feat(vault): add persisted manager operations" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

---
### Task 4: Wipeable editor documents and complete entry/password forms

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/SecretDocument.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/EditorForm.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/EntryEditor.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/ChangePasswordForm.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/KeyFiles.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManager.java` (validated public-key import)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/EntryEditorTest.java`

**Interfaces:**
- `SecretDocument.replace(char[])`, `snapshot()` and `clear()`; caller owns and clears each snapshot. This document does not keep secret undo histories. Its `Content.getString` must exist for Swing compatibility: that unavoidable UI boundary is explicitly separate from model/codec/disk handling; application code reads secrets with `snapshot()`/`JPasswordField.getPassword()` only.
- `EditorForm` owns fields and `save`, `cancel`, `error`; it disables editing during asynchronous submissions, reports failure in place and erases all secret documents on success, Cancel, window closure and lock.
- `EntryEditor.login(Account nullable, Function<Account, CompletableFuture<Void>>, Runnable close)`, `note(Note nullable, Function<Note, CompletableFuture<Void>>, Runnable close)`, `key(SshKey nullable, Function<SshKey, CompletableFuture<Void>>, Runnable close)` return an `EntryEditor` (an `EditorForm`). No form retains borrowed `Account`/`Note` instances after construction.
- `ChangePasswordForm(LockManager, Runnable close)` requires current password, a new password of eight or more characters, and confirmation. The existing `changePassword` consumes both password arrays.
- `VaultManager.importKey(SshKey)` reads the public file off the UI thread, verifies its encoding, and derives algorithm/fingerprint rather than asking the user to transcribe them. The private file is checked for existence/readability but never read.

- [x] **Step 1: Write the failing editor and import tests**

`EntryEditorTest.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class EntryEditorTest {
    @Test void noteEditingPreservesMultilineUnicodeAndClearsOnClose() {
        var received = new AtomicReference<Note>();
        char[] original = "first\nsecond \u03bb".toCharArray();
        var note = new Note(UUID.randomUUID(), "Recovery", original, Instant.EPOCH);
        var form = EntryEditor.note(note, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        char[] snapshot = form.noteText.snapshot();
        assertThat(snapshot).isEqualTo(original); Arrays.fill(snapshot, (char) 0);
        form.save.doClick();
        assertThat(received.get().id()).isEqualTo(note.id());
        assertThat(received.get().text()).isEqualTo(original);
        assertThat(form.noteText.snapshot()).isEmpty();
        assertThat(original).as("editor borrows then copies, it does not wipe the vault record").isEqualTo("first\nsecond \u03bb".toCharArray());
        received.get().zero(); note.zero();
    }

    @Test void changingAuthKindsRetainsOnlyTheChosenSecretFields() {
        var received = new AtomicReference<Account>();
        var form = EntryEditor.login(null, value -> { received.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        form.name.setText("Production"); form.username.setText("deploy");
        form.password.replace("password".toCharArray()); form.passphrase.replace("phrase".toCharArray());
        form.privatePath.setText("/keys/deploy"); form.auth.setSelectedIndex(1);
        form.save.doClick();
        assertThat(received.get().auth()).isInstanceOf(Auth.Key.class);
        assertThat(((Auth.Key) received.get().auth()).passphrase()).isEqualTo("phrase".toCharArray());
        assertThat(form.password.snapshot()).isEmpty(); assertThat(form.passphrase.snapshot()).isEmpty();
        received.get().auth().zero();
    }

    @Test void failedSaveAllowsRetryAndClosingWipesDraft() {
        var completion = new CompletableFuture<Void>();
        var form = EntryEditor.note(null, value -> { value.zero(); return completion; }, () -> { });
        form.name.setText("Recovery"); form.noteText.replace("secret".toCharArray());
        form.save.doClick();
        assertThat(form.save.isEnabled()).isFalse();
        completion.completeExceptionally(new java.io.IOException("Disk full"));
        assertThat(form.save.isEnabled()).isTrue();
        assertThat(form.error.getText()).contains("Disk full");
        form.close();
        assertThat(form.noteText.snapshot()).isEmpty();
    }

    @Test void documentReplacesWithoutKeepingDeletedBuffers() throws Exception {
        var document = new SecretDocument();
        document.replace("secret".toCharArray());
        var segment = new javax.swing.text.Segment();
        document.getText(0, document.getLength(), segment);
        char[] old = segment.array;
        document.remove(1, 3);
        assertThat(old).containsOnly((char) 0);
        assertThat(document.snapshot()).containsExactly('s', 'e', 't');
        document.clear();
        assertThat(document.snapshot()).isEmpty();
    }

    @Test void passwordMismatchDoesNotStartAChangeAndCloseClearsEveryField(@TempDir Path directory) {
        try (var f = new VaultUiFixture(directory)) {
            var form = new ChangePasswordForm(f.lock, () -> { });
            form.current.replace("test-password".toCharArray());
            form.replacement.replace("new-password".toCharArray());
            form.confirm.replace("different".toCharArray());
            form.save.doClick();
            assertThat(form.error.getText()).contains("do not match");
            assertThat(f.queue).isEmpty();
            form.close();
            assertThat(form.current.snapshot()).isEmpty();
            assertThat(form.replacement.snapshot()).isEmpty();
            assertThat(form.confirm.snapshot()).isEmpty();
        }
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
}
```

- [x] **Step 2: Confirm the new test fails compilation**

Run: `./gradlew :jasper-plugin-vault:test --tests '*EntryEditorTest' -q`.

- [x] **Step 3: Add the array-backed Swing document**

`SecretDocument.java`:

```java
package dev.jasper.vault.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

/** Wipeable Swing text storage without an undo history. All methods are UI-thread-only. */
public final class SecretDocument extends PlainDocument {
    private final ArraysContent storage;
    public SecretDocument() { this(new ArraysContent()); }
    private SecretDocument(ArraysContent storage) { super(storage); this.storage = storage; }
    public char[] snapshot() { return Arrays.copyOf(storage.data, getLength()); }
    public void clear() { replace(new char[0]); }
    public void replace(char[] text) {
        writeLock();
        try {
            remove(0, getLength());
            if (text.length == 0) return;
            storage.insertChars(0, text);
            var event = new DefaultDocumentEvent(0, text.length, DocumentEvent.EventType.INSERT);
            insertUpdate(event, null); event.end(); fireInsertUpdate(event);
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        finally { writeUnlock(); }
    }

    private static final class ArraysContent implements AbstractDocument.Content {
        private char[] data = {'\n'};
        private final List<WeakReference<Mark>> positions = new ArrayList<>();
        private static final class Mark implements Position {
            int offset;
            Mark(int offset) { this.offset = offset; }
            @Override public int getOffset() { return offset; }
        }
        @Override public int length() { return data.length; }
        @Override public Position createPosition(int offset) throws BadLocationException {
            range(offset, 0);
            var mark = new Mark(offset); positions.add(new WeakReference<>(mark)); return mark;
        }
        @Override public UndoableEdit insertString(int offset, String text) throws BadLocationException {
            char[] chars = text.toCharArray();
            try { insertChars(offset, chars); return noUndo(); }
            finally { Arrays.fill(chars, (char) 0); }
        }
        void insertChars(int offset, char[] chars) throws BadLocationException {
            range(offset, 0);
            char[] next = new char[data.length + chars.length];
            System.arraycopy(data, 0, next, 0, offset);
            System.arraycopy(chars, 0, next, offset, chars.length);
            System.arraycopy(data, offset, next, offset + chars.length, data.length - offset);
            Arrays.fill(data, (char) 0); data = next;
            positions.removeIf(ref -> ref.get() == null);
            for (var ref : positions) {
                Mark mark = ref.get();
                if (mark != null && mark.offset >= offset && mark.offset != 0) mark.offset += chars.length;
            }
        }
        @Override public UndoableEdit remove(int offset, int count) throws BadLocationException {
            range(offset, count);
            char[] next = new char[data.length - count];
            System.arraycopy(data, 0, next, 0, offset);
            System.arraycopy(data, offset + count, next, offset, data.length - offset - count);
            Arrays.fill(data, (char) 0); data = next;
            positions.removeIf(ref -> ref.get() == null);
            for (var ref : positions) {
                Mark mark = ref.get();
                if (mark != null && mark.offset > offset) mark.offset = Math.max(offset, mark.offset - count);
            }
            return noUndo();
        }
        /** Swing's required String boundary; vault code uses snapshot/Segment instead. */
        @Override public String getString(int offset, int count) throws BadLocationException {
            range(offset, count); return new String(data, offset, count);
        }
        @Override public void getChars(int offset, int count, Segment destination) throws BadLocationException {
            range(offset, count); destination.array = data; destination.offset = offset; destination.count = count;
        }
        private void range(int offset, int count) throws BadLocationException {
            if (offset < 0 || count < 0 || offset > data.length - count) throw new BadLocationException("Invalid text range", offset);
        }
        private static UndoableEdit noUndo() {
            return new AbstractUndoableEdit() {
                @Override public boolean canUndo() { return false; }
                @Override public boolean canRedo() { return false; }
            };
        }
    }
}
```

- [x] **Step 4: Add the reusable form shell**

`EditorForm.java`:

```java
package dev.jasper.vault.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Shared modal form behavior, including failure recovery and deterministic secret cleanup. */
public class EditorForm extends JPanel implements AutoCloseable {
    final JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
    final JButton save = new JButton("Save"), cancel = new JButton("Cancel");
    final JLabel error = new JLabel(" ");
    private final List<SecretDocument> secrets = new ArrayList<>();
    private boolean closed, busy;
    private final Runnable dismiss;

    protected EditorForm(Runnable dismiss) {
        super(new BorderLayout(8, 8)); this.dismiss = dismiss;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(fields, BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(8, 8));
        footer.add(error, BorderLayout.NORTH);
        var buttons = new JPanel(); buttons.add(cancel); buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        cancel.addActionListener(event -> { close(); dismiss.run(); });
    }
    protected final void field(String label, JComponent component) { fields.add(new JLabel(label)); fields.add(component); }
    protected final SecretDocument secret() { var document = new SecretDocument(); secrets.add(document); return document; }
    protected final JPasswordField passwordField(SecretDocument document) {
        var field = new JPasswordField(24); field.setDocument(document); return field;
    }
    protected final void submit(Supplier<CompletableFuture<Void>> action) {
        save.addActionListener(event -> {
            if (closed || busy) return;
            busy = true; enabled(fields, false); save.setEnabled(false); error.setText("Saving...");
            CompletableFuture<Void> pending;
            try { pending = action.get(); }
            catch (RuntimeException failure) { pending = CompletableFuture.failedFuture(failure); }
            pending.whenComplete((ignored, failure) -> {
                busy = false;
                if (closed) return;
                if (failure == null) { close(); dismiss.run(); return; }
                enabled(fields, true); save.setEnabled(true);
                error.setText(message(failure));
            });
        });
    }
    static String message(Throwable failure) {
        while (failure.getCause() != null && (failure instanceof java.util.concurrent.CompletionException
            || failure instanceof java.util.concurrent.ExecutionException)) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static void enabled(Container container, boolean value) {
        for (Component child : container.getComponents()) {
            child.setEnabled(value); if (child instanceof Container nested) enabled(nested, value);
        }
    }
    @Override public void close() { closed = true; secrets.forEach(SecretDocument::clear); }
}
```

- [x] **Step 5: Add all three entry editors**

`EntryEditor.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Account, existing-key and multiline secure-note editing; drafts own their secret copies. */
public final class EntryEditor extends EditorForm {
    final JTextField name = new JTextField(28), username = new JTextField(28);
    final JTextField privatePath = new JTextField(28), publicPath = new JTextField(28), comment = new JTextField(28);
    final JComboBox<String> auth = new JComboBox<>(new String[] {"Password", "Key", "Key and password"});
    final SecretDocument password = secret(), passphrase = secret(), noteText = secret();
    private EntryEditor(Runnable close) { super(close); field("Name", name); }

    public static EntryEditor login(Account initial, Function<Account, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        if (initial != null) {
            form.name.setText(initial.name()); form.username.setText(initial.username());
            switch (initial.auth()) {
                case Auth.Password value -> { form.auth.setSelectedIndex(0); form.password.replace(value.password()); }
                case Auth.Key value -> {
                    form.auth.setSelectedIndex(1); form.privatePath.setText(value.keyPath().toString());
                    if (value.passphrase() != null) form.passphrase.replace(value.passphrase());
                }
                case Auth.KeyAndPassword value -> {
                    form.auth.setSelectedIndex(2); form.privatePath.setText(value.keyPath().toString()); form.password.replace(value.password());
                    if (value.passphrase() != null) form.passphrase.replace(value.passphrase());
                }
            }
        }
        form.field("Username", form.username); form.field("Authentication", form.auth);
        form.field("Password", form.passwordField(form.password)); form.field("Private key path", form.privatePath);
        form.field("Key passphrase (optional)", form.passwordField(form.passphrase));
        form.submit(() -> {
            requireName(form.name.getText());
            int choice = form.auth.getSelectedIndex();
            Path path = choice == 0 ? null : requiredPath(form.privatePath.getText());
            char[] secret = choice == 1 ? null : form.password.snapshot();
            char[] phrase = choice == 0 ? null : form.passphrase.snapshot();
            if (phrase != null && phrase.length == 0) { SecureBytes.zero(phrase); phrase = null; }
            Auth value = switch (choice) {
                case 0 -> new Auth.Password(secret);
                case 1 -> new Auth.Key(path, phrase);
                default -> new Auth.KeyAndPassword(path, phrase, secret);
            };
            try { return save.apply(new Account(id, form.name.getText().strip(), form.username.getText(), value, created, Instant.now())); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor note(Note initial, Function<Note, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        if (initial != null) { form.name.setText(initial.name()); form.noteText.replace(initial.text()); }
        var text = new JTextArea(form.noteText, null, 8, 32);
        text.setLineWrap(true); text.setWrapStyleWord(true);
        form.field("Secure note", new JScrollPane(text));
        form.submit(() -> {
            requireName(form.name.getText());
            Note value = new Note(id, form.name.getText().strip(), form.noteText.snapshot(), Instant.now());
            try { return save.apply(value); }
            catch (RuntimeException failure) { value.zero(); throw failure; }
        });
        return form;
    }

    public static EntryEditor key(SshKey initial, Function<SshKey, CompletableFuture<Void>> save, Runnable close) {
        var form = new EntryEditor(close);
        UUID id = initial == null ? UUID.randomUUID() : initial.id();
        Instant created = initial == null ? Instant.now() : initial.created();
        if (initial != null) {
            form.name.setText(initial.name()); form.privatePath.setText(initial.privatePath().toString());
            form.publicPath.setText(initial.publicPath().toString()); form.comment.setText(initial.comment());
        }
        form.field("Private key path", form.privatePath); form.field("Public key path", form.publicPath); form.field("Comment", form.comment);
        form.submit(() -> {
            requireName(form.name.getText());
            return save.apply(new SshKey(id, form.name.getText().strip(), "", "", form.comment.getText(),
                requiredPath(form.privatePath.getText()), requiredPath(form.publicPath.getText()), created));
        });
        return form;
    }
    static Path requiredPath(String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Choose a key file path");
        return Path.of(text.strip()).toAbsolutePath().normalize();
    }
    static void requireName(String value) { if (value.isBlank()) throw new IllegalArgumentException("Enter a name"); }
}
```

- [x] **Step 6: Add change-password validation and ownership**

`ChangePasswordForm.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.crypto.SecureBytes;
import dev.jasper.vault.lock.LockManager;
import java.util.Arrays;

/** Changing the master password requires the existing password and a matching replacement. */
public final class ChangePasswordForm extends EditorForm {
    final SecretDocument current = secret(), replacement = secret(), confirm = secret();
    public ChangePasswordForm(LockManager lock, Runnable close) {
        super(close);
        field("Current master password", passwordField(current));
        field("New master password", passwordField(replacement));
        field("Confirm new master password", passwordField(confirm));
        save.setText("Change Password");
        submit(() -> {
            char[] old = current.snapshot(), next = replacement.snapshot(), again = confirm.snapshot();
            try {
                if (old.length == 0) throw new IllegalArgumentException("Enter the current master password");
                if (next.length < 8) throw new IllegalArgumentException("Use at least 8 characters");
                if (!Arrays.equals(next, again)) throw new IllegalArgumentException("The passwords do not match");
                return lock.changePassword(old, next);
            } finally { SecureBytes.zero(old); SecureBytes.zero(next); SecureBytes.zero(again); }
        });
    }
}
```

- [x] **Step 7: Add public-key validation and connect import**

`KeyFiles.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.model.SshKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

/** Public metadata only: imported private-key bytes never enter the vault plugin. */
final class KeyFiles {
    private KeyFiles() { }
    static SshKey inspect(SshKey selected) throws IOException {
        if (!Files.isRegularFile(selected.privatePath()) || !Files.isReadable(selected.privatePath()))
            throw new IOException("Private key file is missing or unreadable: " + selected.privatePath());
        byte[] bytes;
        try (var in = Files.newInputStream(selected.publicPath())) { bytes = in.readNBytes(65_537); }
        if (bytes.length > 65_536) throw new IOException("Public key file is too large");
        String[] fields = new String(bytes, StandardCharsets.US_ASCII).strip().split("\\s+", 3);
        if (fields.length < 2) throw new IOException("Expected an OpenSSH public key");
        try {
            byte[] blob = Base64.getDecoder().decode(fields[1]);
            var parsed = OpenSSHPublicKeyUtil.parsePublicKey(blob);
            byte[] canonical = OpenSSHPublicKeyUtil.encodePublicKey(parsed);
            if (!java.util.Arrays.equals(blob, canonical)) throw new IllegalArgumentException("Noncanonical public key");
            int typeLength = java.nio.ByteBuffer.wrap(blob).getInt();
            if (typeLength < 1 || typeLength > blob.length - 4
                || !fields[0].equals(new String(blob, 4, typeLength, StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Public key type does not match its data");
            String fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
            return new SshKey(selected.id(), selected.name(), fields[0], fingerprint, selected.comment(),
                selected.privatePath(), selected.publicPath(), selected.created());
        } catch (IllegalArgumentException | NoSuchAlgorithmException failure) { throw new IOException("Invalid OpenSSH public key", failure); }
    }
}
```

Add this method to `VaultManager`:

```java
    public CompletableFuture<Void> importKey(SshKey selected) {
        if (!editable()) return rejected();
        long expected = generation;
        return io(() -> KeyFiles.inspect(selected)).thenCompose(key -> {
            if (expected != generation || lock.state() != LockState.UNLOCKED)
                return CompletableFuture.failedFuture(new IllegalStateException("The vault was locked meanwhile"));
            return saveKey(key);
        });
    }
```

- [x] **Step 8: Run the complete vault test task**

Run: `./gradlew :jasper-plugin-vault:test -q`.
Expected: PASS. The failure test keeps the draft available to retry; closing the same form clears it.

- [x] **Step 9: Commit editors and key import**

```bash
git add plugins/vault/src/main/java/dev/jasper/vault/ui plugins/vault/src/test/java/dev/jasper/vault/ui/EntryEditorTest.java
git commit -m "feat(vault): add entry and master-password editors" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

---
### Task 5: Singleton manager window, details and Grants

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/ManagerPanel.java`
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManagerWindow.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/PasswordPanel.java` (`clear()`)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java`

**Interfaces:**
- `VaultManagerWindow(PluginContext, LockManager, VaultManager, VaultService, SecretClipboard, Supplier<String> status, Runnable generate)`.
- `show(WindowHandle, Optional<UUID>)`, `changed()`, `isOpen()`, `showUnlock(UnlockPrompt)` (true when routed into an existing manager), `dialog(String, Function<Runnable, ? extends EditorForm>)`, `close()`.
- The plugin opens the manager after a successful explicit create/unlock. An existing window remains open on lock and shows an in-place Unlock button. That button calls the shared service; `VaultPlugin.showUnlock` routes the resulting single `UnlockPrompt` into the manager. Merely locking or ticking never creates an unlock waiter.
- Details contain public account/key metadata and read-only secure note text. Selecting another row, locking or closing immediately wipes the previous note document. Editor dialogs are window-modal and owned by the `PluginWindow`.

- [x] **Step 1: Write the failing headless window tests**

`VaultManagerWindowTest.java`:

```java
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
            assertThat(window.panel.note.snapshot()).isEqualTo("line one\nline two".toCharArray());
            assertThat(host.requestClose("dev.jasper.vault.manager")).isTrue();
            assertThat(window.isOpen()).isFalse();
            assertThat(window.panel.note.snapshot()).isEmpty();
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
            assertThat(window.panel.note.snapshot()).isEmpty();
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
}
```

- [x] **Step 2: Confirm the window tests fail compilation**

Run: `./gradlew :jasper-plugin-vault:test --tests '*VaultManagerWindowTest' -q`.

- [x] **Step 3: Add the list/details/grants panel**

`ManagerPanel.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Headless manager content. Borrowed model values are rendered synchronously and never retained. */
final class ManagerPanel extends JPanel implements AutoCloseable {
    final JList<VaultManager.Row> rows = new JList<>(new DefaultListModel<>());
    final JList<Grant> grants = new JList<>(new DefaultListModel<>());
    final JButton add = new JButton("Add"), edit = new JButton("Edit"), delete = new JButton("Delete");
    final JButton copyPublic = new JButton("Copy public key"), generate = new JButton("Generate Key...");
    final JButton changePassword = new JButton("Change Password..."), lock = new JButton("Lock"), unlock = new JButton("Unlock");
    final JButton revoke = new JButton("Revoke");
    final JComboBox<VaultManager.Type> kind = new JComboBox<>(VaultManager.Type.values());
    final SecretDocument note = new SecretDocument();
    final JTextArea metadata = new JTextArea(6, 35);
    final JLabel status = new JLabel(" ");
    private final JPanel cards = new JPanel(new CardLayout()), locked = new JPanel(new BorderLayout());
    private final JPanel passiveUnlock = new JPanel();
    private final Function<UUID, Optional<Object>> entry;

    ManagerPanel(Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate, Runnable onChangePassword,
                 Runnable onLock, Runnable onUnlock) {
        super(new BorderLayout(8, 8)); this.entry = entry;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        rows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); grants.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        metadata.setEditable(false); metadata.setLineWrap(true); metadata.setWrapStyleWord(true);
        var noteArea = new JTextArea(note, null, 8, 35); noteArea.setEditable(false); noteArea.setLineWrap(true);
        var details = new JPanel(new BorderLayout(8, 8)); details.add(new JScrollPane(metadata), BorderLayout.NORTH);
        details.add(new JScrollPane(noteArea), BorderLayout.CENTER);
        var entries = new JPanel(new BorderLayout(8, 8));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(rows), details); split.setResizeWeight(.35);
        entries.add(split, BorderLayout.CENTER);
        var buttons = new JPanel();
        for (JComponent component : List.of(kind, add, edit, delete, copyPublic)) buttons.add(component);
        entries.add(buttons, BorderLayout.SOUTH);
        var grantPanel = new JPanel(new BorderLayout(8, 8)); grantPanel.add(new JScrollPane(grants), BorderLayout.CENTER);
        grantPanel.add(revoke, BorderLayout.SOUTH);
        var tabs = new JTabbedPane(); tabs.addTab("Entries", entries); tabs.addTab("Grants", grantPanel);
        var unlocked = new JPanel(new BorderLayout(8, 8)); unlocked.add(tabs, BorderLayout.CENTER);
        var tools = new JPanel(); tools.add(generate); tools.add(changePassword); tools.add(lock); unlocked.add(tools, BorderLayout.NORTH);
        passiveUnlock.add(new JLabel("Vault locked")); passiveUnlock.add(unlock); locked.add(passiveUnlock, BorderLayout.CENTER);
        cards.add(unlocked, "unlocked"); cards.add(locked, "locked"); add(cards, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        grants.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Grant grant ? grantLabel(grant) : value, index, selected, focus);
            }
        });
        rows.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) details(); });
        grants.addListSelectionListener(event -> revoke.setEnabled(grants.getSelectedValue() != null));
        add.addActionListener(event -> onAdd.accept((VaultManager.Type) kind.getSelectedItem()));
        edit.addActionListener(event -> selected().ifPresent(onEdit)); delete.addActionListener(event -> selected().ifPresent(onDelete));
        copyPublic.addActionListener(event -> selected().ifPresent(onCopy));
        revoke.addActionListener(event -> { Grant grant = grants.getSelectedValue(); if (grant != null) onRevoke.accept(grant); });
        generate.addActionListener(event -> onGenerate.run()); changePassword.addActionListener(event -> onChangePassword.run());
        lock.addActionListener(event -> onLock.run()); unlock.addActionListener(event -> onUnlock.run());
        details(); revoke.setEnabled(false);
    }
    Optional<UUID> selected() { return Optional.ofNullable(rows.getSelectedValue()).map(VaultManager.Row::id); }
    void refresh(List<VaultManager.Row> nextRows, List<Grant> nextGrants, String text, boolean unlocked) {
        Optional<UUID> selection = selected(); note.clear();
        var rowModel = (DefaultListModel<VaultManager.Row>) rows.getModel(); rowModel.clear(); rowModel.addAll(nextRows);
        var grantsModel = (DefaultListModel<Grant>) grants.getModel(); grantsModel.clear(); grantsModel.addAll(nextGrants);
        selection.ifPresent(this::select); status.setText(text);
        ((CardLayout) cards.getLayout()).show(cards, unlocked ? "unlocked" : "locked");
        details();
    }
    void select(UUID id) {
        for (int index = 0; index < rows.getModel().getSize(); index++) {
            if (rows.getModel().getElementAt(index).id().equals(id)) { rows.setSelectedIndex(index); rows.ensureIndexIsVisible(index); return; }
        }
    }
    void unlockContent(JComponent content) { locked.removeAll(); locked.add(content, BorderLayout.CENTER); locked.revalidate(); locked.repaint(); }
    void passiveUnlock() { unlockContent(passiveUnlock); }
    String grantLabel(Grant grant) {
        String name = grant.credentialId().toString();
        for (int i = 0; i < rows.getModel().getSize(); i++) {
            var row = rows.getModel().getElementAt(i); if (row.id().equals(grant.credentialId())) { name = row.name(); break; }
        }
        return grant.pluginId() + " \u2014 " + name;
    }
    private void details() {
        note.clear(); metadata.setText("");
        Optional<Object> selected = selected().flatMap(entry);
        edit.setEnabled(selected.isPresent()); delete.setEnabled(selected.isPresent());
        copyPublic.setEnabled(selected.filter(SshKey.class::isInstance).isPresent());
        selected.ifPresent(value -> {
            switch (value) {
                case Account a -> {
                    String authentication = switch (a.auth()) {
                        case Auth.Password p -> "Password stored";
                        case Auth.Key k -> "Private key: " + k.keyPath();
                        case Auth.KeyAndPassword both -> "Password stored\nPrivate key: " + both.keyPath();
                    };
                    metadata.setText(a.name() + "\nUsername: " + a.username() + "\n" + authentication + "\nUpdated: " + a.updated());
                }
                case SshKey k -> metadata.setText(k.name() + "\n" + k.algorithm() + "\n" + k.fingerprint()
                    + "\nPrivate: " + k.privatePath() + "\nPublic: " + k.publicPath() + "\n" + k.comment());
                case Note n -> { metadata.setText(n.name() + "\nUpdated: " + n.updated()); note.replace(n.text()); }
                default -> throw new IllegalArgumentException("Unknown vault entry");
            }
        });
    }
    @Override public void close() { note.clear(); metadata.setText(""); rows.clearSelection(); }
}
```

- [x] **Step 4: Add the window lifetime/controller**

`VaultManagerWindow.java`:

```java
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
    private void changePassword() { dialog("Change Master Password", close -> new ChangePasswordForm(lock, close)); }
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
```

- [x] **Step 5: Add the explicit password-form cleanup used by both dialog routes**

In the `PasswordPanel` constructor, immediately after `super(...)`, install the wipeable documents:

```java
        password.setDocument(new SecretDocument());
        confirm.setDocument(new SecretDocument());
```

Add to `PasswordPanel.java`:

```java
    /** Enable/disable submission while the owner is creating a vault. */
    public void setBusy(boolean busy) { primary.setEnabled(!busy); password.setEnabled(!busy); confirm.setEnabled(!busy); bind.setEnabled(!busy); }

    /** Erases the editable copies when the containing dialog/window closes or the vault locks. */
    public void clear() { password.setText(""); confirm.setText(""); }
```

- [x] **Step 6: Run the manager-window tests and the entire vault test task**

Run: `./gradlew :jasper-plugin-vault:test -q`.
Expected: PASS. No native windows or login shells are created by `FakePluginHost`.

- [x] **Step 7: Commit the manager UI**

```bash
git add plugins/vault/src/main/java/dev/jasper/vault/ui plugins/vault/src/test/java/dev/jasper/vault/ui/VaultManagerWindowTest.java
git commit -m "feat(vault): add singleton credential manager window" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

---
### Task 6: Key generation dialog with an optional account

**Scope ruling:** This task implements the existing core's unencrypted key generation and labels it explicitly. The spec's optional passphrase conflicts with its unencrypted-file requirement. The user has been asked whether generated files should be encrypted when a passphrase is supplied or whether the field should be omitted. This task's runnable baseline omits that field; do not mark the entire 6b spec complete until that decision is recorded and any chosen encryption extension is implemented. Never store a purported generated-key passphrase without encrypting the generated file.

**Files:**
- Create: `plugins/vault/src/main/java/dev/jasper/vault/ui/KeyGeneratorForm.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManager.java` (generation/save transaction)
- Test: `plugins/vault/src/test/java/dev/jasper/vault/ui/KeyGeneratorFormTest.java`

**Interfaces:**
- `KeyGeneratorForm(Function<Request, CompletableFuture<Void>>, Runnable close)` and `Request(KeyAlgorithm algorithm, String name, String comment, Optional<String> username)`.
- `VaultManager.generate(Path directory, KeyAlgorithm algorithm, String name, String comment, Optional<String> username)` returns a future for generation and persistence; it runs key generation on the background executor, then adds the standalone key and optional key-auth account in one vault save. A late result after lock or a failed save removes generated files.
- While generating, the dialog's Cancel control is disabled; locking/closing its owner still closes and clears the form. Generation already submitted is allowed to finish, but a changed unlock session rejects the result and removes its files. This avoids claiming Java key-pair generation can be interrupted safely midway.

- [x] **Step 1: Add the failing generator tests**

`KeyGeneratorFormTest.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.model.Auth;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KeyGeneratorFormTest {
    @TempDir Path directory;
    @Test void formDefaultsToEd25519AndRequiresAUsernameOnlyWhenAddingAnAccount() {
        var request = new AtomicReference<KeyGeneratorForm.Request>();
        var form = new KeyGeneratorForm(value -> { request.set(value); return CompletableFuture.completedFuture(null); }, () -> { });
        form.name.setText("Laptop"); form.alsoAccount.setSelected(true); form.save.doClick();
        assertThat(request.get()).isNull(); assertThat(form.error.getText()).contains("username");
        form.username.setText("deploy"); form.save.doClick();
        assertThat(request.get().algorithm()).isEqualTo(KeyAlgorithm.ED25519);
        assertThat(request.get().username()).contains("deploy");
        assertThat(form.description.getText()).contains("unencrypted");
    }

    @Test void generationAddsKeyAndOptionalAccountInOnePersistedEdit() {
        try (var f = new VaultUiFixture(directory)) {
            var generated = f.manager.generate(directory.resolve("keys"), KeyAlgorithm.ED25519, "Laptop", "test", Optional.of("deploy"));
            assertThat(f.manager.busy()).isTrue(); assertThat(f.manager.rows()).isEmpty();
            f.drain(); generated.join();
            assertThat(f.lock.vault().keys()).hasSize(1);
            assertThat(f.lock.vault().accounts()).singleElement().satisfies(account -> {
                assertThat(account.username()).isEqualTo("deploy");
                assertThat(((Auth.Key) account.auth()).passphrase()).isNull();
                assertThat(((Auth.Key) account.auth()).keyPath()).isEqualTo(f.lock.vault().keys().getFirst().privatePath());
            });
            f.lock.lock();
            var unlock = f.lock.unlock("test-password".toCharArray()); f.drain(); unlock.join();
            assertThat(f.lock.vault().keys()).hasSize(1); assertThat(f.lock.vault().accounts()).hasSize(1);
        }
    }

    @Test void lockingDuringGenerationDiscardsTheResultAndRemovesItsFiles() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Path keys = directory.resolve("keys");
            var generated = f.manager.generate(keys, KeyAlgorithm.ED25519, "Laptop", "", Optional.empty());
            f.manager.invalidate(); f.lock.lock(); f.drain();
            assertThat(generated).isCompletedExceptionally();
            assertThat(f.manager.rows()).isEmpty();
            try (var files = Files.list(keys)) { assertThat(files.toList()).isEmpty(); }
        }
    }

    @Test void failedVaultSaveRemovesBothGeneratedFilesAndBothModelEntries() throws Exception {
        try (var f = new VaultUiFixture(directory)) {
            Files.delete(directory.resolve("vault.jv")); Files.createDirectory(directory.resolve("vault.jv"));
            Files.writeString(directory.resolve("vault.jv/block"), "block replacement");
            Path keys = directory.resolve("keys");
            var generated = f.manager.generate(keys, KeyAlgorithm.ED25519, "Laptop", "", Optional.of("deploy")); f.drain();
            assertThat(generated).isCompletedExceptionally();
            assertThat(f.manager.rows()).isEmpty();
            try (var files = Files.list(keys)) { assertThat(files.toList()).isEmpty(); }
        }
    }
}
```

- [x] **Step 2: Confirm these tests fail compilation**

Run: `./gradlew :jasper-plugin-vault:test --tests '*KeyGeneratorFormTest' -q`.

- [x] **Step 3: Add the complete generator form**

`KeyGeneratorForm.java`:

```java
package dev.jasper.vault.ui;

import dev.jasper.vault.keygen.KeyAlgorithm;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Generates a standalone SSH key and, optionally, an account referring to it. */
public final class KeyGeneratorForm extends EditorForm {
    public record Request(KeyAlgorithm algorithm, String name, String comment, Optional<String> username) { }
    final JTextField name = new JTextField(28), comment = new JTextField(28), username = new JTextField(28);
    final JComboBox<KeyAlgorithm> algorithm = new JComboBox<>(KeyAlgorithm.values());
    final JCheckBox alsoAccount = new JCheckBox("Also add a login account", false);
    final JLabel description = new JLabel("Generated private key files are unencrypted; the vault stores their paths.");
    public KeyGeneratorForm(Function<Request, CompletableFuture<Void>> generate, Runnable close) {
        super(close); save.setText("Generate Key");
        field("Name", name); field("Algorithm", algorithm); field("Comment", comment);
        field("Key files", description); field("Account", alsoAccount); field("Account username", username);
        submit(() -> {
            EntryEditor.requireName(name.getText());
            String user = username.getText().strip();
            if (alsoAccount.isSelected() && user.isEmpty()) throw new IllegalArgumentException("Enter the account username");
            if (comment.getText().indexOf('\n') >= 0 || comment.getText().indexOf('\r') >= 0)
                throw new IllegalArgumentException("The key comment must fit on one line");
            var request = new Request((KeyAlgorithm) algorithm.getSelectedItem(), name.getText().strip(), comment.getText().strip(),
                alsoAccount.isSelected() ? Optional.of(user) : Optional.empty());
            cancel.setEnabled(false);
            try { return generate.apply(request).whenComplete((ignored, failure) -> cancel.setEnabled(true)); }
            catch (RuntimeException failure) { cancel.setEnabled(true); throw failure; }
        });
    }
}
```

- [x] **Step 4: Add generation and its persistence transaction to `VaultManager`**

Add imports `dev.jasper.vault.keygen.KeyAlgorithm`, `dev.jasper.vault.keygen.KeyGenerator`, `dev.jasper.vault.model.Auth`, `java.nio.file.Path` and `java.time.Instant`, then these methods:

```java
    public CompletableFuture<Void> generate(Path directory, KeyAlgorithm algorithm, String name, String comment, Optional<String> username) {
        if (!editable()) return rejected();
        if (name.isBlank() || username.filter(String::isBlank).isPresent())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Enter a key name and, when selected, an account username"));
        long expected = generation;
        busy = true;
        CompletableFuture<Void> operation = io(() -> new KeyGenerator(directory).generate(algorithm, name, comment)).thenCompose(key -> {
            CompletableFuture<Void> save;
            if (generation != expected || lock.state() != LockState.UNLOCKED) {
                save = CompletableFuture.failedFuture(new IllegalStateException("The vault was locked during key generation"));
            } else {
                Vault original = lock.vault();
                Account account = username.map(user -> new Account(UUID.randomUUID(), name + " (" + user + ")", user,
                    new Auth.Key(key.privatePath(), null), Instant.now(), Instant.now())).orElse(null);
                save = edit(v -> { v.keys().add(key); if (account != null) v.accounts().add(account); },
                    () -> { original.keys().remove(key); if (account != null) original.accounts().remove(account); },
                    () -> { }, () -> { if (account != null) account.auth().zero(); });
            }
            return save.handle((ignored, failure) -> failure).thenCompose(failure -> {
                if (failure == null) return CompletableFuture.completedFuture(null);
                return removeGeneratedFiles(key).handle((ignored, cleanupFailure) -> {
                    if (cleanupFailure != null) {
                        var combined = new IOException("Could not save generated key; file cleanup also failed. Check "
                            + key.privatePath() + " and " + key.publicPath(), failure);
                        combined.addSuppressed(cleanupFailure);
                        throw new java.util.concurrent.CompletionException(combined);
                    }
                    throw new java.util.concurrent.CompletionException(failure);
                });
            });
        });
        return operation.whenComplete((ignored, failure) -> { busy = false; changed.run(); });
    }
    private CompletableFuture<Void> removeGeneratedFiles(SshKey key) {
        return io(() -> {
            IOException failure = null;
            for (Path path : List.of(key.privatePath(), key.publicPath())) {
                try { Files.deleteIfExists(path); }
                catch (IOException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
            }
            if (failure != null) throw failure;
            return null;
        });
    }
```

The existing `KeyGenerator.generate` can fail after writing a private file but before returning its `SshKey`. Its own failure cleanup must therefore be in that method, not just the manager. Add a `try`/`catch` around its two writes, with both paths allocated before entering it. Replace the body from `AsymmetricCipherKeyPair pair = ...` through its final `return` with:

```java
        AsymmetricCipherKeyPair pair = pair(algorithm);
        Files.createDirectories(directory);
        permissions(directory, "rwx------");
        try {
            Files.writeString(privatePath, pem(algorithm, OpenSSHPrivateKeyUtil.encodePrivateKey(pair.getPrivate())), StandardCharsets.US_ASCII);
            permissions(privatePath, "rw-------");
            byte[] publicBlob = OpenSSHPublicKeyUtil.encodePublicKey(pair.getPublic());
            String line = algorithm.sshType() + " " + Base64.getEncoder().encodeToString(publicBlob)
                + (comment == null || comment.isBlank() ? "" : " " + comment.strip());
            Files.writeString(publicPath, line + "\n", StandardCharsets.US_ASCII);
            return new SshKey(id, name, algorithm.id(), fingerprint(publicBlob), comment == null ? "" : comment.strip(),
                privatePath.toAbsolutePath(), publicPath.toAbsolutePath(), Instant.now());
        } catch (IOException | RuntimeException failure) {
            for (Path path : java.util.List.of(privatePath, publicPath)) {
                try { Files.deleteIfExists(path); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
```

This is a narrow lifecycle amendment to 6a, recorded in the implementation status: a failed generator removes files it already created. It does not change key encoding, encryption or filenames.

- [x] **Step 5: Run generator/UI tests and the vault suite**

Run: `./gradlew :jasper-plugin-vault:test -q`.
Expected: PASS. Background generation is observable in the queued fixture; lock-before-completion cannot attach its result to a later unlock session.

- [x] **Step 6: Commit the generator UI and lifecycle cleanup**

```bash
git add plugins/vault/src/main/java/dev/jasper/vault/ui plugins/vault/src/main/java/dev/jasper/vault/keygen/KeyGenerator.java plugins/vault/src/test/java/dev/jasper/vault/ui/KeyGeneratorFormTest.java
git commit -m "feat(vault): add key generator dialog and optional account" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

---
### Task 7: Wire the manager, generator and shared prompt lifecycle

**Files:** Modify `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`,
`plugins/vault/src/main/java/dev/jasper/vault/service/UnlockPrompt.java` and
`plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`; create
`plugins/vault/src/test/java/dev/jasper/vault/service/UnlockPromptCancellationTest.java`.

**Interfaces:** Uses the completed task 3–6 UI classes and the existing per-consumer service.
Explicit Open creates/unlocks, then opens the singleton manager. Generate follows the same
path, then opens its modal form. Consumer-only unlock does not open a manager. An existing
manager hosts the single shared unlock prompt. Closing during derivation invalidates its result.

- [x] Add these tests to `VaultPluginTest`:

```java
    @Test void openingTheVaultAndGeneratorUsesOneManagerAndInlineUnlock() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            host.invoke(VaultPlugin.OPEN, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            host.invoke("dev.jasper.vault.generate_key", window, null);
            assertThat(host.windows()).contains("dialog|Generate SSH Key|true");
            host.requestClose("dialog");
            host.invoke(VaultPlugin.LOCK, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).as("the existing manager hosts the shared unlock form").containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            assertThat(plugin.currentUnlock()).isNotNull();
            plugin.currentUnlock().submit("test-password".toCharArray());
            host.runBackground();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThat(host.windows()).hasSize(1);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void stoppingDuringUnlockClosesThePromptAndRejectsLateOpen() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            plugin.lockManager().lock();
            host.invoke(VaultPlugin.OPEN, window, null);
            plugin.currentUnlock().submit("test-password".toCharArray());
            host.stopAll();
            host.runBackground();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            assertThat(host.windows()).isEmpty();
        }
    }

    @Test void createPromptIsSingletonAndCanBeCancelled() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.invoke(VaultPlugin.OPEN, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).containsExactly("dialog|Create Vault|true");
            host.requestClose("dialog");
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.NO_VAULT);
            assertThat(host.windows()).isEmpty();
        }
    }
```

- [x] Add and run the following cancellation regression before changing the prompt:

## Integration prerequisite: cancel an in-flight shared unlock

Create `plugins/vault/src/test/java/dev/jasper/vault/service/UnlockPromptCancellationTest.java` before changing production:

```java
package dev.jasper.vault.service;

import dev.jasper.vault.api.LockState;
import dev.jasper.vault.lock.LockManager;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import dev.jasper.vault.store.VaultFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UnlockPromptCancellationTest {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void cancellingDuringDerivationClosesThePromptAndKeepsTheVaultLocked(boolean cancelFuture, @TempDir Path dir) {
        var secrets = new DeviceSecrets(new KeychainStore("Linux", command -> {
            throw new UncheckedIOException(new IOException("No test keychain"));
        }, dir.resolve("unused")), new FileStore(dir.resolve("secret")));
        var file = new VaultFile(dir.resolve("vault.jv"));
        var created = new LockManager(file, secrets, Runnable::run, Runnable::run, state -> { });
        created.create("test-password".toCharArray(), false).join();
        created.lock();
        var queue = new ArrayDeque<Runnable>();
        var lock = new LockManager(file, secrets, queue::add, Runnable::run, state -> { });
        var prompt = new UnlockPrompt(null, lock, () -> { });
        var pending = prompt.attach();
        int[] dismissed = {0};
        prompt.onDismiss(() -> dismissed[0]++);
        prompt.submit("test-password".toCharArray());
        if (cancelFuture) pending.cancel(false); else prompt.cancel();
        assertThat(dismissed[0]).isEqualTo(1);
        while (!queue.isEmpty()) queue.removeFirst().run();
        assertThat(lock.state()).isEqualTo(LockState.LOCKED);
        if (cancelFuture) assertThat(pending).isCancelled();
        else assertThat(pending).isCompletedWithValue(false);
    }
}
```

Run `./gradlew :jasper-plugin-vault:test --tests '*UnlockPromptCancellationTest' -q`, observe failure.
Replace two methods in `UnlockPrompt.java`:

```java
    private void detach(CompletableFuture<Boolean> waiter) {
        waiters.remove(waiter);
        if (waiters.isEmpty() && !settled) cancel();
    }

    public void cancel() {
        if (settled) return;
        if (busy) lock.lock();
        finish(false);
    }
```

Run the full vault test suite and commit with the Codex coauthor trailer. `LockManager.lock()` now invalidates the derivation generation even while locked (737f90c). This test covers both closing a manager's inline prompt and cancellation of its last consumer future.

- [x] Run `./gradlew :jasper-plugin-vault:test -q` and observe missing manager/generator windows,
duplicate create dialogs and the pending cancellation failure.

- [x] Replace `VaultPlugin.java` with this complete integration:

```java
package dev.jasper.vault;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Side;
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
        locked = context.appearance().icon("dev/jasper/vault/lock.svg");
        unlocked = context.appearance().icon("dev/jasper/vault/lock-open.svg");
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
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 50));
        status.setText("Vault");
        context.rail().add(OPEN);
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
        return "Device secret: " + lock.deviceSecretSource() + " | " + context.dataDirectory().resolve("vault.jv")
            + " | Auto-lock: " + (settings.autoLock().isZero() ? "off" : settings.autoLock().toMinutes() + " min");
    }

    private void showGenerator() {
        managerWindow.dialog("Generate SSH Key", close -> new KeyGeneratorForm(request ->
            manager.generate(settings.keysDirectory(), request.algorithm(), request.name(), request.comment(), request.username()), close));
    }

    private static String message(Throwable failure) {
        while (failure.getCause() != null && failure instanceof java.util.concurrent.CompletionException) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
```

- [x] Run `./gradlew :jasper-plugin-vault:test -q`; all integration and existing consumer tests pass.

- [x] Commit with `git add plugins/vault` followed by
`git commit -m "feat(vault): connect manager and generator lifecycle" -m "Co-Authored-By: Codex <noreply@openai.com>"`.

---

### Task 8: User guide, integration verification and native handoff

**Files:**
- Create: `docs/credential-vault.md`
- Modify: `docs/README.md` (user-guide link)
- Modify: `docs/STATUS.md` (executed scope, deviations, evidence and pending user decision/native checks)
- Modify: this plan (execution checkboxes and the status banner).

**Interfaces:** The guide describes the implemented actions, manager, key files, grants, clipboard, paths and live settings. It makes no unsupported claim that vault encryption also encrypts SSH private-key files. Native acceptance belongs to the user and is never replaced with an unattended GUI launch.

- [x] **Step 1: Write the complete user guide**

`docs/credential-vault.md`:

````markdown
# Credential Vault

Credential Vault is bundled with Jasper. Press **F8**, click the vault button in the rail, or
choose **Open Vault...** in the command palette. First use creates a vault with a master password
and an optional device binding. Later use unlocks the vault and opens its manager window.

The manager lists logins, SSH keys, secure notes and saved plugin grants. Select an entry to
inspect its details, edit it or delete it. Add a login with a password, a key path and optional
passphrase, or both. Add an existing SSH key by choosing its private and public paths; its public
key supplies the algorithm and SHA-256 fingerprint. Secure notes are available only in the manager.

**Generate Key...** supports Ed25519, ECDSA P-256/P-384 and RSA 3072/4096. Generated files live in
`plugins/dev.jasper.vault/data/keys/` unless `keys_directory` selects another directory.
The generator can also create a login account for a supplied username. Generated private keys
are currently unencrypted. The encrypted vault stores SSH key paths; key files remain separate. Locking the vault does not
delete or encrypt those files. **Copy public key** copies the selected public key. Deleting a key
entry offers a separate choice to delete its two files; the default keeps them.

**Change Password...** requires the old password and matching new passwords. **Lock** removes
secrets from the open vault and clears its editor forms. The window stays open with an Unlock button that shows the shared unlock form in place.
The padlock in the status bar unlocks/opens a locked vault and locks an unlocked vault; its tooltip
shows the remaining inactivity time. Already connected SSH sessions are unaffected by locking.

In the command palette, select **Vault**, or enter `>vault` / `>cred`. Search by entry name,
username or key fingerprint. Enter copies an account's password, Cmd/Ctrl+Enter copies its
username, and Shift+Enter opens the selected entry in the manager. Both password and username
copies are cleared after 30 seconds if the clipboard still contains that copy. A later user copy
is left alone. The vault never types clipboard contents into a terminal.

Other plugins request credentials through `dev.jasper.vault.api.VaultApi`. The first use asks
whether to allow the named plugin once, always for that credential, or deny access. Choosing a
credential through the picker grants one use. Saved grants appear in the manager's Grants tab;
**Revoke** removes one. Deleting an entry removes all its saved grants. Notes are not exposed
through the consumer API.

The encrypted file is `plugins/dev.jasper.vault/data/vault.jv`. A device-bound vault uses the
platform keychain tool, with a file fallback shown in the manager's status line. An unbound vault
uses the master password alone. The master password cannot be recovered. Change the live plugin
settings through the Plugins manager's **Open Settings** action:

```toml
auto_lock_minutes = 15            # 0 disables inactivity locking
keys_directory = ""              # empty uses this plugin's data/keys directory
bind_new_vaults_to_device = true  # initial state of the create checkbox
```

The three actions are `dev.jasper.vault.open` (F8), `dev.jasper.vault.lock` and
`dev.jasper.vault.generate_key`. Configure their shortcuts like other contributed actions.

## Native acceptance

Headless tests cover the forms, persistence failures, lock transitions, service requests and
clipboard timing. The following checks need a user-run Jasper window:

1. Open F8 and create a throwaway vault. Verify native keychain access, then lock and unlock it.
2. Add/edit a login and a multiline note; generate/import an SSH key and copy its public key.
3. Open F8 repeatedly; verify only one manager window opens. Lock with an editor open; verify
   its secret fields disappear and the manager offers inline unlock.
4. Copy a password and username through the Vault scope. Check 30-second clearing, then copy
   ordinary text before expiry and verify it survives.
5. Set `auto_lock_minutes = 1`, leave Jasper inactive and verify the padlock locks. Set it to 0
   and confirm the countdown disappears. Restore the desired timeout.
6. With a consumer plugin, exercise Allow once, Always and Deny; revoke its saved grant in the
   manager and verify the next fetch asks again.

````

- [x] **Step 2: Add the guide to the documentation index**

In `docs/README.md`, insert this exact bullet after the Command palette bullet:

```markdown
- [Credential Vault](credential-vault.md): accounts, SSH keys, secure notes, grants, locking and clipboard handling.
```

- [x] **Step 3: Run the complete headless verification and distribution build**

Run:

```bash
./gradlew check :jasper-app:installDist -q
```

Expected: all tasks succeed. Do not run `:jasper-app:run` or `:jasper-app:bench`.

Count the actual results rather than copying historical numbers:

```bash
python3 - <<'PYTESTS'
from pathlib import Path
import xml.etree.ElementTree as ET
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for path in Path('.').glob('**/build/test-results/test/TEST-*.xml'):
    if '.worktrees' in path.parts:
        continue
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, '0'))
print(counts)
if counts['failures'] or counts['errors']:
    raise SystemExit('Test failures remain')
PYTESTS
```

Run the source-hygiene check from `AGENTS.md`, then `git diff --check`. Fix any reported source control/private-use characters with Java escapes before continuing.

- [x] **Step 4: Record execution evidence and the explicit scope ruling**

Insert this new section immediately before `### Credential Vault plan 6a` in `docs/STATUS.md`; retain the verified output from the preceding command in the plan's execution record. Update the passphrase sentence only after the user answers it and the chosen behavior is implemented and verified.

```markdown
### Credential Vault plan 6b — 2026-09-22

The Vault UI implements the singleton manager, login/key/note editors, Grants revocation,
change-master-password form, rail action, padlock status and countdown, Vault palette scope,
30-second clipboard clearing, key generator and optional generated-key account. The guide is
[Credential Vault](credential-vault.md), with the native acceptance checklist.

The manager uses the shared unlock prompt in place after it has been locked. Secret editor
storage uses wipeable char arrays and is cleared on selection changes, lock and window closure;
Swing's required document String boundary and the clipboard remain documented UI exceptions.
Failed writes restore prior manager records within the same unlock session. Lock/stop invalidates
late operations. Generated files are removed if generation cannot be committed; explicitly deleting
a key's files happens after the record deletion saves, and a partial file-delete failure is reported.

Execution rulings: the existing key generator writes unencrypted private files. The generator form
labels that behavior and can add an account without an ineffective stored passphrase. The user
choice between passphrase-encrypted generation and omitting that field is still pending; this
baseline is runnable, but the whole 6b spec is not yet claimed complete. Native acceptance is also
pending and belongs to the user. No GUI or benchmark was launched by the agent.
```

- [x] **Step 5: Commit documentation and verified integration**

```bash
git add docs/credential-vault.md docs/README.md docs/STATUS.md docs/superpowers/plans/2026-09-22-jasper-vault-plan-6b-ui.md
git commit -m "docs(vault): document manager workflows and acceptance" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

- [x] **Step 6: Hand the native checklist to the user and perform the independent whole-branch review**

The review examines late unlock/generation completion after lock/stop, failed saves, grant revocation,
notes omitted from API/palette, stale palette actions, clipboard replacement and timer identity,
secret document cleanup, modal owner closure, custom key directories and generated-file deletion.
Follow the session's approved execution method for the review. Record any findings/fixes and rerun
only affected tests plus the required final checks when code changes. Do not merge or push until
already authorized or the user agrees.


## Final review and handoff record

Independent whole-branch review of `c8141b6..f451c36` found P1 failed rekey retaining the
replacement key and P2 form cancellation still committing a password change. Both grades were
accepted and fixed inline in one pass (`a06dcb8`), per the execution skill; no second review was
needed. Four targeted tests failed on the old code and passed after the changes. A fifth regression
covers reporting a write failure after the form closes. `./gradlew check :jasper-app:installDist -q`
then passed: 1,526 tests, 1,523 passed, three expected skips, zero failures/errors. Source hygiene
and `git diff --check` passed. The installed vault includes its manager, generator, scope, icons and
BouncyCastle dependency.

Reviewer exclusions were adjudicated explicitly: the contradictory key-generation requirement
remains a user decision; native focus/modality, clipboard, auto-lock and real keychain acceptance
remain user-run; a fresh audit of unchanged 6a crypto/storage, adversarial external file edits and
future SSH consumers is outside this UI deliverable. UI-reachable rekey defects were nevertheless
fixed. The Swing String boundary remains necessary for normal editing, with wipeable backing
storage and no new persistence strings. Packaging was independently verified by the coordinator.
No deferred minor findings. The branch/worktree and execution ledger are retained while the key
choice and native acceptance remain pending. Nothing was merged, pushed or launched natively.
