# Jasper Credential Vault — Design

**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Realises the "Interop sketch"
and failure walkthroughs of `2026-09-21-jasper-plugin-sdk-design.md` for the Vault side; the
SSH/SFTP/Tunnel Manager gets its own spec and consumes the API defined here.

**Scope:** One bundled plugin, `dev.jasper.vault`, that keeps SSH credentials (accounts with a
password, a key, or both; standalone SSH keys; secure notes) in one encrypted file, bound to this
device, unlocked with a master password, locked again after inactivity; a consumer API other
plugins use to list, pick and fetch credentials under per-plugin grants; a manager window, create
and unlock dialogs, an SSH key generator, a padlock status item, actions and a command-palette
scope. Two plans: 6a (core and API), 6b (UI).

**Not in scope:** Export or import of a vault; syncing; the system SSH agent; API tokens as a
kind of their own (a password account covers them); intercepting passwords typed in terminals
("save this password?" belongs to the SSH plugin, which can ask the vault through the API); a
settings page inside Jasper's Settings (the plugin's settings file is edited like any plugin's).

---

## 1. Why

TermLab's Vault (`plugins/vault` in that repository) proved the shape: one AES-256-GCM file, an
Argon2id key from the master password plus a device secret, a lock manager with auto-lock, and a
provider contract the SSH plugin consumed. It leaned on IntelliJ for the keychain, dialogs and
palette, and it carried a known weakness: the decrypted vault passed through a JSON `String` that
could not be zeroed. Jasper's SDK now has services with per-consumer publication, plugin windows
and dialogs, status items, activities, palette scopes, per-plugin settings and data directories,
so the same features fit a plugin with no application code, and the storage format can be fixed.

## 2. Decisions

1. **Device binding, via the platform's own tools**, on by default; a "bind to this device"
   checkbox at creation, off meaning a portable file. (Chosen over password-only and over
   always-bound.)
2. **BouncyCastle is bundled** for Argon2id and OpenSSH key encoding. The KDF is not something to
   own; the size is the plugin's, not the application's.
3. **Kinds**: accounts with Password, Key (path and optional passphrase) or Key-and-password;
   standalone SSH keys; secure notes. Dropped: API tokens, `pushToSystemAgent`, the auto-save
   policy. Added: change master password. Kept: auto-lock, key generation, padlock, palette
   presence, per-consumer grants.
4. **Secrets never become `String`s** on the way to or from disk: the plaintext is a versioned
   binary encoding, secrets are `byte[]`/`char[]`, and every buffer is zeroed after use.
5. **The palette scope copies, it never pastes**: Copy password and Copy username put text on the
   clipboard and clear it after 30 seconds; nothing from the vault is typed into a pane by the
   vault itself.

## 3. Shape

```
plugins/vault/                      module :jasper-plugin-vault, bundling org.bouncycastle:bcprov-jdk18on
  src/main/resources/plugin.toml    id = "dev.jasper.vault", name = "Credential Vault",
                                    capabilities = ["palette.contribute"], exports = ["dev.jasper.vault.api"]
  src/main/resources/settings.toml  the example settings file (section 8)
  src/main/java/dev/jasper/vault/
    api/                            the consumer contract (section 4)
    crypto/                         KeyDerivation, VaultCipher, VaultFileFormat, SecureBytes
    model/                          Vault, Account, Auth, SshKey, Note, Grant, and their binary codec
    store/                          VaultFile (atomic I/O), DeviceSecretStore + KeychainStore + FileStore
    lock/                           LockManager, InactivityTimer
    keygen/                         KeyGenerator, KeyAlgorithm
    ui/                             the windows, dialogs, status item, palette scope (plan 6b)
    VaultPlugin.java                start: settings, lock manager, service, actions, status, scope
```

The plugin compiles against `jasper-sdk` only plus its bundled library; the SSH plugin compiles
`compileOnly` against this module and declares `requires = [{ id = "dev.jasper.vault", version = ">=0.1" }]`
(hard: SSH is useless without it). `gradle/plugin-architecture.gradle.kts` records
`":jasper-plugin-ssh" to listOf("dev.jasper.vault.api")` when SSH arrives.

## 4. Consumer API — `dev.jasper.vault.api`

```java
public interface VaultApi {
    Topic<LockState> LOCK_STATE_CHANGED = Topic.of("dev.jasper.vault.lock-state", LockState.class);
    LockState lockState();                                         // NO_VAULT, LOCKED, UNLOCKED
    CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner); // prompts; true once unlocked
    List<CredentialDescriptor> credentials();                      // empty unless UNLOCKED; no secrets
    CompletableFuture<Optional<Credential>> credential(UUID id);   // unlocks and asks for a grant as needed
    CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner); // the picker; empty on cancel
}
public enum LockState { NO_VAULT, LOCKED, UNLOCKED }
public enum Kind { ACCOUNT_PASSWORD, ACCOUNT_KEY, ACCOUNT_KEY_AND_PASSWORD, SSH_KEY }
public record CredentialDescriptor(UUID id, String name, String subtitle, Kind kind) { }  // subtitle: username, or fingerprint
public final class Credential implements AutoCloseable {
    UUID id(); String name(); Kind kind(); Optional<String> username();   // empty for a bare SSH key
    char[] password();       // null unless the kind has one; the caller's copy, zeroed by close()
    Optional<Path> keyPath(); char[] passphrase();                          // null when absent
    @Override void close();  // zeroes; every later accessor throws IllegalStateException
}
```

Rules:

- **Threading.** Every method is called on the UI thread and returns at once; futures complete
  on the UI thread. Key derivation and file I/O run on the plugin's `background()` executor.
- **Per consumer.** The service is published with `publishPerConsumer`, so each call knows the
  calling plugin. `credentials()` lists everything for any consumer: descriptors hold no secrets.
- **Grants.** `credential(id)` returns the secret only under a grant for the calling plugin. Without
  one it prompts, over the window the consumer last named in `ensureUnlocked` or `pick` (else the
  active window): "Allow *SSH* to use *deploy@prod*?" with **Allow once**, **Always allow for SSH**
  and **Deny**. "Always" grants are `(pluginId, credentialId)` pairs stored inside the encrypted
  vault, listed under Grants in the manager and revocable there; removing a credential removes its
  grants. A descriptor chosen through `pick` carries a one-time grant for that plugin. Deny answers
  `Optional.empty()`.
- **Unlocking.** `credential` and `pick` unlock first when needed, through the same dialog as
  `ensureUnlocked`. One unlock prompt serves every waiting request; one grant prompt serves every
  waiting request for the same pair. `NO_VAULT` answers `false`/`empty` and shows a notice
  ("Create a vault in Credential Vault first") rather than the create dialog.
- **Cancellation.** Cancelling a returned future withdraws that request; when no request waits on
  a prompt any more, the prompt closes. A cancelled request never delivers a secret.
- **Lifetime.** A `Credential` is a copy for the caller; the vault keeps nothing about it. Callers
  fetch just before use and `close()` right after; the SSH plugin holds no secret once connected,
  which is why an auto-lock leaves its sessions running.
- **Events.** `LOCK_STATE_CHANGED` publishes every transition, including lock at stop.

## 5. Storage and crypto

**File.** `<plugin data>/vault.jv`, that is `plugins/dev.jasper.vault/data/vault.jv`. Layout:

```
MAGIC "JASPERVLT" (9) | VERSION u16 LE = 1 | FLAGS u8 (bit 0: device-bound) | SALT (16) | NONCE (12) | CIPHERTEXT
```

AES-256-GCM with a 128-bit tag; a fresh random nonce on every write; a fresh salt whenever the
password is set (create, change password), because a new salt needs the password again and the
vault keeps only the derived key while unlocked (zeroed at lock); the header is the GCM additional
authenticated data, so flags cannot be flipped. Saves write a temp file beside the
target and rename it into place. A wrong password and a foreign device secret both fail the tag;
the plugin distinguishes them by the flags: a bound file whose device secret is missing or freshly
created reports "created on another machine".

**Key.** Argon2id, 64 MiB, 3 iterations, 4 lanes, 32-byte output, over `password || deviceSecret`
(password alone for an unbound file), via BouncyCastle; about 250 ms on an M-series Mac, never on
the EDT. The password is the UTF-8 bytes of what the user typed, taken from a `char[]`.

**Plaintext.** A versioned binary encoding (`DataOutput`: a `u16` model version, then counts and
length-prefixed fields; secrets as byte arrays). Reading yields a `Vault` whose secrets are
`byte[]`/`char[]` fields; `SecureBytes` wraps every transient buffer and zeroes it on close; the
plaintext and the derived key are zeroed in `finally`. No `String` holds a secret except in a
`JPasswordField`, which is read as `char[]`.

**Model.**

```
Vault(version, List<Account>, List<SshKey>, List<Note>, List<Grant>)
Account(UUID id, String name, String username, Auth auth, Instant created, Instant updated)
Auth = Password(char[]) | Key(Path keyPath, char[] passphraseOrNull) | KeyAndPassword(Path keyPath, char[] passphraseOrNull, char[] password)
SshKey(UUID id, String name, String algorithm, String fingerprint, String comment, Path privatePath, Path publicPath, Instant created)
Note(UUID id, String name, char[] text, Instant updated)
Grant(String pluginId, UUID credentialId)
```

**Keys on disk.** Generated keys go to `keys_directory` (default `<plugin data>/keys/`, created
0700) as `id_<algo>_<8 hex>` in OpenSSH private-key format (unencrypted; the vault is the
protection, and a passphrase may still be set and stored in the account) with `.pub` beside it.
Algorithms: Ed25519 (recommended), ECDSA P-256, ECDSA P-384, RSA 3072, RSA 4096. The fingerprint is
the SHA-256 form OpenSSH prints. A key referenced by an account may live anywhere; the vault stores
the path, never the private key bytes of an imported key.

**Change master password** re-derives and re-encrypts in place with a new salt and nonce; the old
password is required.

## 6. Device secret

`DeviceSecretStore` (`Optional<byte[]> read()`, `void write(byte[] secret32)`, `void delete()`) with
two real implementations:

- `KeychainStore` runs the platform's tool as a process with a bounded wait: macOS `security
  find-generic-password -s "Jasper Credential Vault" -a device-secret -w` and `add-generic-password -U`;
  Linux `secret-tool lookup service jasper-vault account device-secret` and `secret-tool store`;
  Windows PowerShell with `System.Security.Cryptography.ProtectedData` (DPAPI, current user) over a
  file in the plugin's data directory. The 32 bytes travel base64-encoded.
- `FileStore` keeps `<plugin data>/device.secret` (0600). It is the fallback when the tool is
  absent or fails, and what tests use; the manager's status line says "Device secret: keychain"
  or "file (no keychain found)".

`DeviceSecret.getOrCreate()` reads, else generates 32 random bytes and writes. Creating a bound
vault, unlocking one and changing its password all pass the secret to the KDF and zero it after.

## 7. Lifecycle

`LockManager` is the single owner of the decrypted `Vault`: `state()`, `unlock(char[] password)`,
`create(char[] password, boolean bound)`, `lock()`, `withVault(Function)` for edits that save
afterwards, `changePassword(old, new)`, listeners. Transitions publish `LOCK_STATE_CHANGED` through
the plugin context on the UI thread. `InactivityTimer` re-locks after `auto_lock_minutes` (default
15; 0 disables) without user activity, where activity is any keyboard or mouse event in Jasper,
observed with a `Toolkit.addAWTEventListener` the plugin removes at stop. `stop()` locks.

## 8. Settings

`plugins/dev.jasper.vault/dev.jasper.vault.toml`, seeded from the plugin's `settings.toml`:

```toml
# Settings for Credential Vault. Jasper reads this file live.
# auto_lock_minutes = 15            # 0 never locks automatically
# keys_directory = ""               # generated keys; empty means plugins/dev.jasper.vault/data/keys
# bind_new_vaults_to_device = true  # the default state of the checkbox when creating a vault
```

Bindings for the three actions are ordinary contributed-action bindings; the defaults are `F8` for
Open Vault and none for Lock and Generate Key.

## 9. UI (plan 6b)

- **Actions**: `dev.jasper.vault.open` (Open Vault…, F8; opens or unlocks then opens the manager),
  `dev.jasper.vault.lock` (enabled while unlocked), `dev.jasper.vault.generate_key`.
- **Status item**: a padlock, right side; tooltip "Vault locked" / "Vault unlocked · locks in 12 min";
  its action is `open` when locked and `lock` when unlocked.
- **Rail**: an action opening the manager.
- **Manager**: a singleton `PluginWindow` ("Credential Vault"): a list of accounts, keys and notes
  with a details pane; Add (Login, SSH Key, Secure Note), Edit, Delete, Copy public key, Generate
  Key…, Change Password…, Lock; a Grants section listing `(plugin, credential)` pairs with Revoke;
  a status line (device secret source, file path, auto-lock). Locked, the window shows the unlock
  form in place. Delete of a key offers to delete the files too.
- **Dialogs** (window-modal `windows().dialog`): Create Vault (password twice, bind checkbox),
  Unlock (password; wrong-password and other-machine messages; Cancel withdraws every waiting
  request), Grant, Picker (for `pick`; search, kinds filtered to accounts and keys), Key Generator
  (name, algorithm, comment, optional passphrase, "also add an account for user …").
- **Palette scope** `dev.jasper.vault.scope`, label "Vault", aliases `vault`, `cred`; rows are
  accounts and keys (locked: one disabled row "Vault is locked — press Enter to unlock", whose
  Enter runs `open`); verbs **Copy password** (accounts with a password; clipboard cleared after
  30 s unless it changed), **Copy username**, **Open in Vault**; `shortcutActionId = dev.jasper.vault.open`.
- **Notices** for failures (file unwritable, keychain tool failed, key generation failed).

## 10. Testing

- Pure unit tests: `KeyDerivation` (known-answer vector, parameter sizes), `VaultFileFormat`
  (round trip, tamper detection through the AAD, version and magic checks), `VaultCipher`
  (wrong password, wrong device secret, zeroing), the binary codec (round trip of every kind,
  version gate), `FileStore`, `LockManager` (state machine, auto-save, listeners), `InactivityTimer`
  (with an injected clock and executor), `KeyGenerator` (each algorithm writes a key `ssh-keygen -l`
  would fingerprint identically, checked against the stored fingerprint; the format is validated by
  re-reading with BC).
- Plugin tests through `FakePluginHost`: a fake consumer plugin requires the vault, lists,
  picks, fetches under Allow once / Always / Deny, sees `LOCK_STATE_CHANGED`, cancels a pending
  `credential` and observes the prompt withdrawn; the palette scope's rows and verbs; the status
  item's text per state. `KeychainStore` is exercised by a test that runs only when the platform
  tool is present (`assumeTrue`), against a throwaway service name it deletes afterwards.
- Native acceptance covers the real keychain prompt on macOS, F8, auto-lock, and the clipboard clear.

## 11. Plans

- **6a — core and API**: module and bundling, crypto, format, codec, device secret, lock manager,
  timer, key generator, `VaultApi` with grants and cancellation, `VaultPlugin.start` publishing the
  service, actions and `LOCK_STATE_CHANGED`, settings file, tests. Ships a plugin that works end to
  end from another plugin's point of view: the create, unlock, grant and picker dialogs are its only
  UI, and Open Vault… while unlocked does nothing until 6b adds the manager.
- **6b — UI**: manager window (which Open Vault… then opens), key generator dialog, status item,
  rail action, palette scope, documentation and native acceptance.

## 12. Self-review record

- Placeholders: none; every type in sections 4 to 7 has its shape stated.
- Consistency: `Kind` in section 4 covers exactly the `Auth` variants and `SshKey` of section 5;
  notes have no `Kind` and are never handed to consumers; `Grant` pairs are what section 4's
  "always" produces and section 9's Grants tab lists; the flags bit in section 5 is what section 6's
  "another machine" message reads.
- Scope: two plans; 6b cannot start before 6a.
- Ambiguity resolved: futures complete on the UI thread; one prompt serves many requesters;
  descriptors need no grant; `NO_VAULT` never opens the create dialog from a consumer call.
