# Self-contained Vault-backed SSH Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. The user selected native inline execution, with one independent final review. Steps use checkbox syntax for tracking.

**Status:** native implementation approved and executed on `codex/retro-metal`; final verification/review in progress. Tasks 1–6 complete. Task 7 covers real restart acceptance and staged-loader/skin tests.

Implementation rulings: the v1 all-kinds fixture was authored independently; manager inputs are consumed by copy-on-write transactions; unsupported legacy plain EC PEM remains rejected while supported encrypted OpenSSH ECDSA is tested; one `KeyImportPrompt` owns request/review/passphrase state. The restart fixture accumulates output because the testkit drains it. Staged tests exposed BC provider cross-loader conflicts and Swing delegate lookup; the plugins now use local provider instances and the app supplies Swing's UI-delegate classloader.

**Goal:** Import SSH hosts and their private keys into Jasper so connections survive removal of the source key files and absence of the system SSH agent.

**Architecture:** Vault owns encrypted managed-key storage, parsing, consent and persistence. Remote owns configuration interpretation, preview/re-import and saved host references; its existing MINA client authenticates using owned in-memory Vault credentials. A durable Vault commit precedes the single host-file update.

**Tech Stack:** Java 25 / JetBrains Runtime, Gradle wrapper, Swing, Apache MINA sshd-common/core 2.19.0, BouncyCastle 1.85.2, existing Vault encryption and TOML host storage.

**Spec:** `docs/superpowers/specs/2026-09-23-jasper-vault-backed-ssh-import-design.md` (approved).

## Global Constraints

- macOS, Linux and Windows, modern and retro appearances, use the same flow.
- No agent socket server, agent forwarding, external ssh process, or automatic connection during import is introduced.
- Extend only `dev.jasper.vault.api`; the general Jasper SDK does not need new types.
- MINA types remain internal to the plugins. Remote never accesses Vault internals or its storage file.
- Maximum 1 MiB per private-key source, with an explicit error above the limit.
- Original files are never modified or removed. Managed private material never enters temporary files, hosts.toml, logs, labels or diagnostics.
- Read existing VaultCodec version 1; write version 2 when managed entries are introduced. Preserve an encrypted version-1 backup before the first version-2 commit.
- Bump Vault and Remote to 0.2.0; Remote's optional Vault dependency requires >=0.2. Jasper SDK remains 0.7.4.
- Existing manually configured Agent, password, and path-backed Vault authentication remain supported.
- Preserve native inline execution, one commit per task, and one independent final review. Each commit ends `Co-Authored-By: Codex <noreply@openai.com>`.
- Work in `/Users/dustin/.codex/worktrees/retro-metal-plan/moray`, branch `codex/retro-metal`; do not change the original checkout or its development data.
- Use `./gradlew`; no native GUI, user login shell, real remote host, merge to main or push. Tests generate their own keys and Vault files.

## Review Focus

1. **Cancellation after a background read finishes but before its EDT callback:** discarded private bytes must be cleared; no host or grant may be published (Task 4).
2. **Two aliases reference the same key while another import is committing:** fingerprint deduplication must use current durable state, not stale preview metadata (Tasks 2/4).
3. **Vault commit succeeds but an external host-file edit arrives:** preserve the external edit, retain usable imported keys, and allow a clean retry (Task 6).
4. **Upgrade, rekey and import writes overlap:** the backup must be the actual encrypted version-1 predecessor and a failed rekey must not corrupt the import (Task 2).
5. **A public companion is absent or belongs to a different private key:** derive the identity from private material; accept absence, reject a mismatch (Task 3).

## Entry points and file structure

Read `docs/STATUS.md`, the approved spec, `docs/remote.md`, `docs/superpowers/specs/2026-09-22-jasper-vault-design.md`, `docs/sdk-architecture.md`, and both plugin package-info files before execution. Verify this existing isolated worktree with using-git-worktrees; do not create a second checkout.

Paths below are repository-relative. Each task lists the actual Java files it owns. Public API additions stay in `plugins/vault/src/main/java/dev/jasper/vault/api`. New implementation helpers are concrete classes, not single-implementation interfaces.

| Task | Deliverable | Dependencies |
| --- | --- | --- |
| 1 | Owned managed-key values and versioned payload codec | none |
| 2 | Durable serialized Vault mutations and upgrade backup | 1 |
| 3 | In-process key inspection with bounded secret ownership | 1 |
| 4 | Vault batch-import API, prompts and manager integration | 1–3 |
| 5 | Ordered host identities and in-memory SSH authentication | 1, 4 |
| 6 | Config preview, re-import and durable host binding | 4–5 |
| 7 | Installed-plugin acceptance, documentation and final review | 1–6 |

This is one runnable deliverable: all seven tasks are required before calling the feature complete. Task commits are integration checkpoints, not separate feature releases.

---

## Task 1: Managed key ownership, API values and payload compatibility

**Files**

Create:
- `plugins/vault/src/main/java/dev/jasper/vault/model/ManagedSshKey.java`
- `plugins/vault/src/main/java/dev/jasper/vault/crypto/WipingOutputStream.java`
- `plugins/vault/src/test/java/dev/jasper/vault/model/ManagedSshKeyTest.java`

Modify:
- `plugins/vault/src/main/java/dev/jasper/vault/model/{Vault,VaultCodec}.java`
- `plugins/vault/src/main/java/dev/jasper/vault/api/{Credential,CredentialDescriptor}.java`
- `plugins/vault/src/test/java/dev/jasper/vault/model/VaultCodecTest.java`
- `plugins/vault/src/test/java/dev/jasper/vault/api/CredentialTest.java`

**Interfaces**

```java
// ManagedSshKey owns these two arrays; constructor consumes them.
public ManagedSshKey(UUID id, String name, String algorithm, String fingerprint,
                     String publicKey, byte[] privateKey, char[] passphrase, Instant created);
public UUID id();
public String name();
public String algorithm();
public String fingerprint();
public String publicKey();
public Instant created();
public byte[] privateKey(); // borrowed, for immediate internal use only
public char[] passphrase(); // borrowed, nullable
public ManagedSshKey copy(); // clones secrets
public ManagedSshKey renamed(String name); // clones secrets, same id/created
public void close(); // idempotent clear, secret access subsequently throws

// Additive Credential overload; existing seven-argument constructor delegates here.
public Credential(UUID id, String name, Kind kind, String username,
                  char[] password, Path keyPath, byte[] keyBytes, char[] passphrase);
public Optional<byte[]> keyBytes(); // borrowed owned bytes, cleared by close

// The existing four-argument descriptor constructor delegates with managedKey=false.
public record CredentialDescriptor(UUID id, String name, String subtitle,
                                   Kind kind, boolean managedKey) {}

// Vault additions:
public List<ManagedSshKey> managedKeys();
public Optional<ManagedSshKey> managedKey(UUID id);
public int payloadVersion();
public void requireManagedFormat(); // monotonic 1 -> 2; decode v2 also calls it
```

A password-only Credential can have neither key source. A credential must never have both path and bytes. Preserve public constructors/methods currently used by other plugins.

- [x] **1.1 Write failing ownership and codec tests.** Add the following real regression, plus a Credential counterpart retaining the provided bytes before closing it:

```java
@Test void managedRoundTripOwnsIndependentSecretsAndRemovalClearsThem() {
    UUID id = UUID.randomUUID();
    byte[] original = {1, 2, 3};
    char[] phrase = "fixture".toCharArray();
    Vault vault = new Vault();
    vault.requireManagedFormat();
    vault.managedKeys().add(new ManagedSshKey(id, "shared", "ssh-ed25519",
        "SHA256:fixture", "ssh-ed25519 fixture", original, phrase, Instant.EPOCH));
    byte[] encoded = VaultCodec.encode(vault);
    Vault loaded = VaultCodec.decode(encoded);
    byte[] loadedKey = loaded.managedKey(id).orElseThrow().privateKey();
    assertThat(loadedKey).isEqualTo(original).isNotSameAs(original);
    vault.remove(id);
    assertThat(original).containsOnly((byte) 0);
    assertThat(phrase).containsOnly((char) 0);
    assertThat(loadedKey).containsExactly(1, 2, 3);
    loaded.zero();
    assertThat(loadedKey).containsOnly((byte) 0);
    Arrays.fill(encoded, (byte) 0);
}
```

Use an independently authored literal v1 empty fixture (`00 01` followed by four big-endian zero counts), and a fixed v1 all-kinds fixture generated by the **unchanged baseline codec** before editing it. Store the latter as test resource `plugins/vault/src/test/resources/vault-v1-all-kinds.bin` with fixed UUIDs, public paths and dummy secrets; no user data. Verify all original values after decoding, a read/encode of v1 remains v1, and once v2 is introduced it stays v2 even after removing the last managed key. Truncate each managed field in turn; reject negative/oversized lengths, unknown versions, duplicate UUIDs across entry types and trailing bytes. Keep a reference to previously constructed managed arrays to verify cleanup on decode failure.

- [x] **1.2 Run RED.**

```sh
./gradlew :jasper-plugin-vault:test --tests '*ManagedSshKeyTest' --tests '*VaultCodecTest' --tests '*CredentialTest'
```

First compilation fails for missing APIs; after introducing declarations, observe the ownership/version assertions fail before implementing their behavior.

- [x] **1.3 Implement ownership and wiping serialization.** Use final fields, explicit metadata accessors and a closed flag; `toString()` includes only name/type/closed state. Constructor validation consumes and clears secrets even if validation fails. `Vault.remove` and `Vault.zero` close managed entries; `Vault.managedKey` searches only that collection. Validate size before retaining bytes. Implement the stream used by the codec as follows:

```java
public final class WipingOutputStream extends ByteArrayOutputStream {
    @Override public synchronized void write(int b) {
        reserve(1); buf[count++] = (byte) b;
    }
    @Override public synchronized void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        reserve(len); System.arraycopy(b, off, buf, count, len); count += len;
    }
    private void reserve(int extra) {
        int required = Math.addExact(count, extra);
        if (required <= buf.length) return;
        byte[] old = buf;
        buf = Arrays.copyOf(old, Math.max(required, Math.multiplyExact(old.length, 2)));
        Arrays.fill(old, (byte) 0);
    }
    @Override public synchronized void close() {
        Arrays.fill(buf, (byte) 0); reset();
    }
}
```

`VaultCodec.encode` writes the unchanged v1 collections, then for v2 an i32 managed count and for each entry UUID/name/algorithm/fingerprint/public line/private bytes/passphrase/created epoch seconds. Use a try/finally that closes the wiping stream after obtaining the independent return array. For private fields, use the following read/write helpers rather than strings:

```java
private static void keyBytes(DataOutputStream out, byte[] value) throws IOException {
    if (value.length == 0 || value.length > 1_048_576)
        throw new IllegalArgumentException("Private key exceeds the supported size");
    out.writeInt(value.length); out.write(value);
}
private static byte[] keyBytes(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length < 1 || length > 1_048_576) throw new CorruptVaultException("Bad private-key length");
    byte[] value = new byte[length];
    try { in.readFully(value); return value; }
    catch (IOException failure) { Arrays.fill(value, (byte) 0); throw failure; }
}
```

Use `readFully` for existing strings too so truncation cannot masquerade as valid text. Bound managed counts to 4096 and aggregate private bytes to 64 MiB per decoded Vault; enforce the same limits before adding an import batch. Use a finally block per record for private/passphrase arrays until ownership is successfully transferred. Outer decode failure zeroes the whole partially built Vault, including failures thrown as CorruptVaultException. Never include field contents in malformed-input errors.

- [x] **1.4 Run GREEN and commit.** Repeat 1.2, then all Vault tests. Commit only the Task 1 files as `Add encrypted managed SSH key values and compatible Vault payloads` with the required trailer.

## Task 2: Serialize Vault mutations and preserve the encrypted predecessor

**Files**

Modify:
- `plugins/vault/src/main/java/dev/jasper/vault/lock/LockManager.java`
- `plugins/vault/src/main/java/dev/jasper/vault/store/VaultFile.java`
- `plugins/vault/src/main/java/dev/jasper/vault/ui/VaultManager.java`
- `plugins/vault/src/main/java/dev/jasper/vault/service/VaultService.java`
- `plugins/vault/src/test/java/dev/jasper/vault/{lock/LockManagerTest,store/VaultFileTest,ui/VaultManagerTest,service/VaultServiceTest}.java`

**Interfaces**

```java
// UI-thread API. Mutation receives a private clone, never the published Vault.
public <T> CompletableFuture<T> transact(Function<Vault, T> mutation,
                                       BooleanSupplier cancelled);
public boolean busy();
public long generation();
// Keep save() for compatibility; production editing uses transact.

// Called in the ordered write, immediately before first v2 replacement.
public void backupVersionOne(byte[] encryptedPredecessor) throws IOException;
// Existing read()/write() remain; add restricted-create helper inside VaultFile.
```

- [x] **2.1 Write failing persistence regressions using the existing queued executors.** The production change these tests catch is publishing a mutation before durable save or cloning before the previous mutation settles.

```java
@Test void transactionsCloneAtExecutionAndPublishOnlyAfterSave(@TempDir Path dir) {
    LockManager lock = manager(dir);
    lock.create("pw".toCharArray(), false); runBackground();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var first = lock.transact(v -> {
        v.notes().add(new Note(a, "a", new char[]{'a'}, Instant.EPOCH)); return a;
    }, () -> false);
    var second = lock.transact(v -> {
        assertThat(v.notes()).extracting(Note::id).contains(a);
        v.notes().add(new Note(b, "b", new char[]{'b'}, Instant.EPOCH)); return b;
    }, () -> false);
    assertThat(lock.vault().notes()).isEmpty();
    runBackground();
    assertThat(first.join()).isEqualTo(a);
    assertThat(second.join()).isEqualTo(b);
    assertThat(lock.vault().notes()).extracting(Note::id).containsExactly(a, b);
}
```

Add tests using `vault.jv.tmp` directory obstruction for failed saves, `vault.jv.v1-backup` directory obstruction for failed backups, and queued rekey failure. Assert original file bytes/state remain, no failed transaction result escapes, and a later transaction succeeds. Decode the encrypted backup with the old password and independently compare it to the v1 predecessor. Tests must cover lock before commit, lock after write starts, mutation throwing after allocating secrets, and cancel before queued execution.

- [x] **2.2 Run RED.**

```sh
./gradlew :jasper-plugin-vault:test --tests '*LockManagerTest' --tests '*VaultFileTest' --tests '*VaultManagerTest'
```

- [x] **2.3 Implement one ordered write/mutation queue in LockManager.** Refactor save, transact and changePassword to share the existing `lastWrite` sequence. At transaction execution: validate generation/cancellation, clone current Vault via encode/decode with a zeroed intermediate buffer, apply mutation to that clone, encrypt, queue the file write, then publish on the UI executor only after success. Keep clone ownership in a local holder until publication; on any failure or stale generation close it. Return only immutable IDs/descriptors from production mutations, never a borrowed staged model.

Use this publication rule in the write completion handler:

```java
boolean sameSession = generation == expectedGeneration && vault == original;
if (failure == null && sameSession) {
    vault = draft;
    original.zero();
    result.complete(value);
} else {
    draft.zero();
    if (failure != null) result.completeExceptionally(failure);
    else result.completeExceptionally(new IllegalStateException(
        "Vault saved, but the vault was locked before the operation completed"));
}
```

Before serialization/queueing the irreversible write, check cancellation once more. Once queued, do not cancel the encrypted write. A cancelled public future must not prevent its internal operation from cleaning up. Password changes must start their snapshot after prior transactions settle, and transactions after a failed password change must seal with the surviving key. Maintain a persisted payload-version field distinct from the editable draft's version; set it only after successful writes/unlock/create.

For the first v2 write, read the current encrypted file inside the ordered worker and call `backupVersionOne` before replacing it. Backup path is `vault.jv.v1-backup`; do not overwrite an existing valid regular backup. If it is a directory, symlink, or unreadable/invalid envelope, fail instead of assuming it is a usable backup. Use CREATE_NEW with owner-only POSIX attributes at creation when supported. On Windows use the JDK ACL view to grant only the file owner full access; preserve inherited directory protections on stores with neither view. If restricting permissions fails, delete the newly created backup and abort. A backup from a failed write is retained and reused safely on retry.

- [x] **2.4 Migrate all production edits to the transaction API.** Remove VaultManager's optimistic list edits/undo mechanism. Each save/delete/rename/revoke operation mutates only the supplied draft. Callers supplying secret-bearing values must copy into the draft and close their input on completion. Generated-file cleanup continues to run if its transaction fails; it never runs for managed keys. VaultService's ALWAYS grant decision waits for its transaction to persist before completing secret requests. Preserve the existing setup/unlock behavior and headless tests.

```java
// Example: revoke must not mutate lock.vault() directly.
public CompletableFuture<Void> revoke(Grant grant) {
    return lock.transact(v -> { v.grants().remove(grant); return (Void) null; }, () -> false)
        .whenComplete((ignored, failure) -> changed.run());
}
```

Search every production `lock.vault()` mutation and `lock.save()` call. Read-only access can remain. This is necessary to prevent an ordinary manager edit from bypassing import serialization.

- [x] **2.5 Run GREEN and commit.** Run all Vault tests. Commit `Serialize Vault edits and back up version-one upgrades`, including all converted mutation callers and tests.

## Task 3: Inspect private keys without source-file dependencies

**Files**

Create:
- `plugins/vault/src/main/java/dev/jasper/vault/service/KeyInspector.java`
- `plugins/vault/src/main/java/dev/jasper/vault/service/PreparedKey.java`
- `plugins/vault/src/test/java/dev/jasper/vault/service/KeyInspectorTest.java`

Modify `plugins/vault/build.gradle.kts` to add `implementation("org.apache.sshd:sshd-common:2.19.0")` and `implementation("org.slf4j:slf4j-jdk14:2.0.13")`; update dependency comments.

**Interfaces**

```java
public final class PreparedKey implements AutoCloseable {
    public PreparedKey(String name, String algorithm, String fingerprint,
                       String publicKey, byte[] privateKey, char[] passphrase);
    public String name(); public String algorithm(); public String fingerprint();
    public String publicKey();
    public ManagedSshKey toManaged(UUID id, Instant created); // clones owned secrets
    public void close();
}
public final class KeyInspector {
    public static PreparedKey read(String name, Path source, char[] passphrase)
        throws IOException, GeneralSecurityException;
    public static final class PassphraseRequired extends IOException {}
    public static final class InvalidPassphrase extends IOException {}
}
```

`read` consumes/clears its supplied passphrase on every path. PreparedKey owns its own copies. The parser never returns a raw private key through the API.

- [x] **3.1 Write generated-fixture tests.** Use existing `KeyGenerator` with ED25519, RSA_3072, ECDSA_P256 and ECDSA_P384, and an OpenSSH encrypted fixture generated with the existing passphrase overload. Generate a PKCS#8 PEM RSA fixture from a JDK KeyPair in the test; all inputs are local disposable keys.

```java
@Test void validatesPrivateKeyWithoutPublicCompanion(@TempDir Path dir) throws Exception {
    SshKey generated = new KeyGenerator(dir).generate(KeyAlgorithm.ED25519, "fixture", "test");
    String publicLine = Files.readString(generated.publicPath()).strip();
    Files.delete(generated.publicPath());
    try (PreparedKey prepared = KeyInspector.read("fixture", generated.privatePath(), null)) {
        assertThat(prepared.publicKey().split(" ")[0]).isEqualTo("ssh-ed25519");
        assertThat(prepared.publicKey().split(" ")[1]).isEqualTo(publicLine.split(" ")[1]);
        assertThat(prepared.fingerprint()).isEqualTo(generated.fingerprint());
    }
}
```

Add a mismatched companion from another generated key, wrong passphrase, passphrase-required, empty file, 1 MiB+1 file, unsupported marker, missing file, and malformed file. Assert safe exception messages do not contain a unique sentinel from the input. Tests retain passphrase references and assert zeroing after both success and failure.

- [x] **3.2 Run RED.**

```sh
./gradlew :jasper-plugin-vault:test --tests '*KeyInspectorTest'
```

- [x] **3.3 Implement bounded reads and MINA validation.** Reject non-regular files. Resolve the selected source to its real path for reads; deduplicate fingerprints later, so symlinks do not create a second credential. Read at most 1_048_577 bytes and reject over-limit input. Use `SecurityUtils.loadKeyPairIdentities` with `NamedResource.ofName("SSH import")`, a ByteArrayInputStream and FilePasswordProvider. Require exactly one supported key pair. Derive the public line via `PublicKeyEntry.toString(pair.getPublic())` and fingerprint via `KeyUtils.getFingerPrint(pair.getPublic())`. Use a FilePasswordProvider decode-result callback to distinguish an encrypted-key password failure from unrelated malformed input; do not infer this from arbitrary exception text. Resolve `.pub` next to the original source path, bound it to 64 KiB, parse it with MINA and compare public keys using KeyUtils. Never require a `.pub` file.

Implement the inspection method with explicit transfer, and factor only the public-file comparison and bounded read into helpers below (ordinary imports omitted):

```java
public static PreparedKey read(String name, Path source, char[] passphrase)
        throws IOException, GeneralSecurityException {
    byte[] encoded = null;
    char[] ownedPhrase = passphrase == null ? null : passphrase.clone();
    List<KeyPair> pairs = new ArrayList<>();
    boolean[] requested = {false}, rejected = {false};
    try {
        Path real = source.toRealPath();
        if (!Files.isRegularFile(real)) throw new IOException("Select a regular private-key file");
        encoded = readBounded(real, 1_048_576);
        char[] supplied = ownedPhrase;
        FilePasswordProvider provider = new FilePasswordProvider() {
            public String getPassword(SessionContext session, NamedResource resource, int retry)
                    throws IOException {
                requested[0] = true;
                if (supplied == null) throw new PassphraseRequired();
                return new String(supplied);
            }
            public ResourceDecodeResult handleDecodeAttemptResult(SessionContext session,
                    NamedResource resource, int retry, String password, Exception error) {
                if (error != null) rejected[0] = true;
                return ResourceDecodeResult.TERMINATE;
            }
        };
        try (InputStream in = new ByteArrayInputStream(encoded)) {
            Iterable<KeyPair> loaded = SecurityUtils.loadKeyPairIdentities(null,
                NamedResource.ofName("SSH import"), in, provider);
            if (loaded != null) for (KeyPair pair : loaded) pairs.add(pair);
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            if (requested[0] && supplied == null) throw new PassphraseRequired();
            if (rejected[0]) throw new InvalidPassphrase();
            throw new IOException("Unsupported or malformed private key");
        }
        if (pairs.size() != 1) throw new IOException("Expected one supported private key");
        PublicKey pub = pairs.getFirst().getPublic();
        checkCompanion(source.resolveSibling(source.getFileName() + ".pub"), pub);
        PreparedKey prepared = new PreparedKey(name, KeyUtils.getKeyType(pub),
            KeyUtils.getFingerPrint(pub), PublicKeyEntry.toString(pub), encoded, ownedPhrase);
        encoded = null; ownedPhrase = null;
        return prepared;
    } finally {
        SecureBytes.zero(encoded); SecureBytes.zero(ownedPhrase); SecureBytes.zero(passphrase);
        for (KeyPair pair : pairs) {
            try { pair.getPrivate().destroy(); }
            catch (javax.security.auth.DestroyFailedException ignored) { }
        }
    }
}
private static byte[] readBounded(Path path, int limit) throws IOException {
    byte[] buffer = new byte[limit + 1];
    try (InputStream in = Files.newInputStream(path)) {
        int count = in.readNBytes(buffer, 0, buffer.length);
        if (count == 0) throw new IOException("Key file is empty");
        if (count > limit) throw new IOException("Key file exceeds the supported size");
        return Arrays.copyOf(buffer, count);
    } finally { SecureBytes.zero(buffer); }
}
private static void checkCompanion(Path companion, PublicKey key)
        throws IOException, GeneralSecurityException {
    if (Files.notExists(companion)) return;
    byte[] bytes = readBounded(companion, 65_536);
    try {
        PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(
            new String(bytes, StandardCharsets.UTF_8).strip());
        if (entry == null || !KeyUtils.compareKeys(key, entry.resolvePublicKey(null, null, null)))
            throw new IOException("Public key file does not match the private key");
    } finally { SecureBytes.zero(bytes); }
}
```

Give PassphraseRequired and InvalidPassphrase fixed messages (`Enter the key passphrase`
and `Could not unlock the key; check its passphrase`). PreparedKey validates before
retaining ownership and clears inputs on constructor failure. KeyImportRequest in Task 4
prefixes safe errors with the affected source filename. Catch malformed companion runtime
errors at that boundary as `Invalid public key file`, without logging parser causes.
JCA/provider internals do not promise zeroing of all parser objects; release those references
promptly and clear every array the application owns. Existing key generation stays unchanged.

- [x] **3.4 Run GREEN and commit.** Run KeyInspector and KeyGenerator tests plus dependency staging compilation. Commit `Validate imported private keys in process with bounded ownership`.

## Task 4: Vault batch-import API and user flow

**Files**

Create under `plugins/vault/src/main/java/dev/jasper/vault/`:
- `api/SshKeySource.java`, `api/SshKeyImportResult.java`
- `service/KeyImportRequest.java` (one operation's state machine)
- `service/KeyImportPrompt.java` (UI-facing metadata/actions, no borrowed secrets)
- `ui/KeyImportPanel.java`, `ui/KeyPassphrasePanel.java`

Modify:
- `api/{VaultApi,CredentialDescriptor}.java`
- `service/VaultService.java`, `VaultPlugin.java`
- `ui/{VaultManager,VaultManagerWindow,ManagerPanel,VaultScope,EntryEditor}.java`
- `plugins/vault/src/main/resources/plugin.toml` (0.2.0)
- `plugins/vault/src/test/java/dev/jasper/vault/{VaultPluginTest,service/VaultServiceTest,ui/VaultManagerTest}.java`

Create tests `service/KeyImportRequestTest.java`, `ui/KeyImportPanelTest.java`.

**Interfaces**

```java
public record SshKeySource(UUID requestId, String name, Path path) {
    public SshKeySource {
        Objects.requireNonNull(requestId); Objects.requireNonNull(path);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
    }
}
public record SshKeyImportResult(Map<UUID, CredentialDescriptor> keys,
                                 List<CredentialDescriptor> selectedCredentials) {
    public SshKeyImportResult {
        keys = Map.copyOf(keys); selectedCredentials = List.copyOf(selectedCredentials);
    }
}
// VaultApi, UI-thread method; Optional.empty means user cancelled before commit.
default CompletableFuture<Optional<SshKeyImportResult>> importSshKeys(
    WindowHandle owner, List<SshKeySource> sources, List<UUID> selectedCredentials) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException(
        "This Credential Vault does not support key import"));
}
```

Source request IDs identify preview bindings; the same source or fingerprint can map to one credential. `selectedCredentials` covers no-IdentityFile hosts explicitly assigned an existing managed key or password-only account. Reject legacy path-backed/account-key credentials in this list with an explanation to import the source key first.

Add this VaultService overload; the existing constructor delegates with import disabled
so existing callers still compile. The production plugin always uses the full overload.

```java
public VaultService(LockManager lock, Supplier<Optional<WindowHandle>> fallbackOwner,
    Consumer<UnlockPrompt> showUnlock, Consumer<GrantPrompt> showGrant,
    Consumer<PickPrompt> showPick, Consumer<String> notice, Executor background, Executor ui,
    Function<WindowHandle, CompletableFuture<Boolean>> openForImport,
    Consumer<KeyImportPrompt> showImport, Runnable changed);
```

For request state, use:

```java
enum Phase { OPENING_VAULT, READING, PASSPHRASE, REVIEW, COMMITTING, COMPLETE, CANCELLED }
record KeyRow(UUID requestId, String name, String fingerprint, boolean reused) {}
// KeyImportPrompt exposes immutable rows, phase, safe status; methods:
void submitPassphrase(char[] passphrase);
void commit();
void cancel();
Subscription onChanged(Runnable listener);
```

Use the existing SDK Subscription for listeners. Passphrase submission transfers ownership into the request. Only metadata reaches Swing labels.

- [x] **4.1 Write failing service tests using real LockManager, KeyInspector and queued executors.** Extend the existing service test fixture with a queued import prompt collector and setup callback. Tests drive phases/actions, rather than fake the importer. Test a source requested twice maps to one managed UUID, and two separate concurrent requests for the same fingerprint still produce one key. Test promotion of a legacy SshKey keeps UUID/grants and does not affect a path-backed Account.

Add the late-callback regression: queue a read, run that worker, cancel before its UI completion, drain UI, and assert no managed entries/grants/host bindings published. A test-only owner-package accessor may capture PreparedKey to check it is closed; add no production test accessors. Also test lock during passphrase prompt, wrong phrase followed by correct phrase, Vault absent/setup cancellation, window closure, plugin stop, backup failure, transaction failure, and grant scoping to the requester only.

Include this executable acceptance assertion in VaultServiceTest. Add a field
`List<KeyImportPrompt> imports = new ArrayList<>();` and update its `service(Path)`
fixture to construct the full overload with inline executors, `owner ->
CompletableFuture.completedFuture(lock.state() == LockState.UNLOCKED)`, `imports::add`,
and an empty changed callback. No real keychain, user Vault, or native window is involved.

```java
@Test void importingAnExistingPasswordRequiresConsentAndGrantsOnlyItsRequester(@TempDir Path dir) {
    VaultApi api = service(dir).forConsumer(SSH);
    populate();
    var imported = api.importSshKeys(window, List.of(), List.of(PROD));
    assertThat(imported).isNotDone();
    assertThat(lock.vault().grants()).isEmpty();
    imports.getFirst().commit();
    assertThat(imported.join().orElseThrow().selectedCredentials())
        .extracting(CredentialDescriptor::id).containsExactly(PROD);
    assertThat(lock.vault().grants()).containsExactly(new Grant(SSH.id(), PROD));
    try (Credential credential = api.credential(PROD).join().orElseThrow()) {
        assertThat(credential.password()).isEqualTo("s3cret".toCharArray());
    }
    assertThat(service.forConsumer(OTHER).credential(PROD)).isNotDone();
    assertThat(grants).hasSize(1);
}
```

- [x] **4.2 Run RED.**

```sh
./gradlew :jasper-plugin-vault:test --tests '*KeyImportRequestTest' --tests '*VaultServiceTest' --tests '*KeyImportPanelTest'
```

- [x] **4.3 Implement the operation state machine and durable batch mutation.** Inject existing `LockManager`, background/UI executors, setup/unlock callback, prompt presenter and metadata-change callback into the service. Preserve the old constructor with a compatibility delegate for existing test consumers. VaultPlugin wires setup with its existing creation dialog; cancellation withdraws only this operation's waiter from shared setup/unlock prompts.

In READING, process unique normalized source paths sequentially; retain prepared results until REVIEW. On PassphraseRequired/InvalidPassphrase, enter PASSPHRASE and retry that source. Other failures complete exceptionally with source-identifying safe text, leaving Remote preview editable. Do not commit a partial batch. At REVIEW, render the actual caller name, fingerprints, new/reused status and the `Import and use` action; this action persists scoped grants along with keys.

Use the following commit algorithm inside `lock.transact`, with the complete prepared list retained until its future settles:

```java
var bindings = new LinkedHashMap<UUID, CredentialDescriptor>();
for (SshKeySource source : sources) {
    PreparedKey prepared = preparedByRequest.get(source.requestId());
    ManagedSshKey key = draft.managedKeys().stream()
        .filter(k -> k.fingerprint().equals(prepared.fingerprint())).findFirst().orElse(null);
    if (key == null) {
        SshKey legacy = draft.keys().stream()
            .filter(k -> k.fingerprint().equals(prepared.fingerprint())).findFirst().orElse(null);
        UUID id = legacy == null ? UUID.randomUUID() : legacy.id();
        Instant created = legacy == null ? Instant.now() : legacy.created();
        key = prepared.toManaged(id, created);
        if (legacy != null) draft.keys().remove(legacy);
        draft.managedKeys().add(key);
        draft.requireManagedFormat();
    }
    draft.grants().add(new Grant(consumer.id(), key.id()));
    bindings.put(source.requestId(), new CredentialDescriptor(key.id(), key.name(),
        key.fingerprint(), Kind.SSH_KEY, true));
}
```

After this loop validate every selected existing credential again against the draft, add only its requester grant, enforce aggregate limits, and return immutable SshKeyImportResult. Every completion path closes all PreparedKey values. After COMMITTING, cancellation cannot claim to undo the durable write; complete internal cleanup and publish a notice that keys were saved if the requesting UI is gone. Do not proceed to host writes through a cancelled Remote operation.

Connect cancellation to actual owner lifetime using the existing terminal-event subscription/window `isOpen()` checks, plus dialog `onClosed`, future cancellation and plugin stop. Unsubscribe operation listeners when settled. Never poll the disk or start a second agent to manage lifetime.

- [x] **4.4 Integrate managed credentials and manager controls.** VaultService enumerates both key types. `copyOf` constructs a managed Credential with cloned bytes/passphrase and no path. `VaultManager.rows` includes managed metadata; `entry` resolves it; `publicKey` uses its stored public line; delete removes it and its grants transactionally. Managed rename uses `renamed`, closes replaced drafts and has no filesystem controls. Update exhaustive type switches in ManagerPanel, VaultManagerWindow and VaultScope; show `Stored in Vault`. The existing legacy path editor/generator remains available.

Add a minimal Swing passphrase form using SecretDocument and its existing JPasswordField binding pattern. It has Retry/Cancel, a generic wrong-passphrase message, and clears its document on dismissal. KeyImportPanel uses a scrollable list/table, wrapped status and Import and use/Cancel controls; no hardcoded colors or FlatLaf imports. Drive both Metal and FlatLaf headlessly and verify focus/default-button behavior with existing UI fixtures.

- [x] **4.5 Run GREEN and commit.** Run all Vault tests and `verifyPluginArchitecture`. Commit `Import managed SSH keys through the Vault API and UI`.

## Task 5: Ordered Vault identities and memory-only SSH authentication

**Files**

Modify under `plugins/remote/src/main/java/dev/jasper/remote/`:
- `hosts/{Auth,HostFile,RemoteHost}.java`
- `client/Connections.java`
- `ui/{HostEditor,HostRows}.java`, `RemotePlugin.java` (credential labels)

Modify tests:
- `plugins/remote/src/test/java/dev/jasper/remote/{client/ConnectionsTest,hosts/HostFileTest,ui/HostEditorTest,FakeVault}.java`

**Interfaces**

```java
// Add to sealed Auth; retain Vault(UUID) and Agent unchanged.
record VaultKeys(List<UUID> credentialIds) implements Auth {
    public VaultKeys {
        credentialIds = List.copyOf(new LinkedHashSet<>(credentialIds));
        if (credentialIds.isEmpty()) throw new IllegalArgumentException("Choose at least one Vault key");
    }
}
```

Use TOML `auth = "vault-keys"` and `credentials = ["uuid", "uuid"]`; single existing `auth="vault"` / `credential` remains readable/writable. Reject a mixed scalar/array declaration, invalid/empty array, null IDs and non-key credentials in VaultKeys. Imported single disk keys also use VaultKeys to distinguish the managed-only contract from legacy path-based Vault auth. Explicitly selected password accounts use the existing Auth.Vault.

- [x] **5.1 Write failing end-to-end connection tests.** In ConnectionsTest create generated keys, put new byte-backed Credentials into the fixture credential map, remove the source files, and connect through LoopbackServer. The second-key test proves actual key ordering/use, not merely array storage:

```java
@Test void managedKeysAuthenticateWithoutFilesOrAgent(@TempDir Path dir) throws Exception {
    try (var server = new LoopbackServer()) {
        var generator = new KeyGenerator(dir.resolve("keys"));
        SshKey first = generator.generate(KeyAlgorithm.ED25519, "first", "test");
        SshKey second = generator.generate(KeyAlgorithm.ED25519, "second", "test");
        server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(second.publicPath()).strip())
            .resolvePublicKey(null, null, null));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        credentials.put(a, new Credential(a, "first", Kind.SSH_KEY, null, null, null,
            Files.readAllBytes(first.privatePath()), null));
        credentials.put(b, new Credential(b, "second", Kind.SSH_KEY, null, null, null,
            Files.readAllBytes(second.privatePath()), null));
        for (Path path : List.of(first.privatePath(), first.publicPath(), second.privatePath(), second.publicPath()))
            Files.delete(path);
        connections(dir, Optional.empty());
        RemoteHost target = host("managed", server.port(), "deploy", new Auth.VaultKeys(List.of(a, b)), Optional.empty());
        assertThat(readUntil(shell(target).connection(), "\r\n")).startsWith("READY");
        assertThat(credentials.values()).allSatisfy(c ->
            assertThatThrownBy(c::keyBytes).isInstanceOf(IllegalStateException.class));
    }
}
```

Add encrypted-byte authentication, legacy path/password compatibility, cancellation during the second credential request closing the first copy, denial halfway through acquisition, duplicate IDs, wrong credential kind, managed host receiving a path credential, and no agent contact even when one is available. Preserve existing agent tests, including the recently added empty-agent diagnostic.

- [x] **5.2 Run RED.**

```sh
./gradlew :jasper-plugin-remote:test --tests '*ConnectionsTest' --tests '*HostFileTest' --tests '*HostEditorTest'
```

- [x] **5.3 Implement credential acquisition with one owned list per attempt.** Replace `Optional<Credential>` in private Connections helpers with `List<Credential>`: empty only for Auth.Agent, one for Auth.Vault, ordered nonempty for Auth.VaultKeys. Acquire sequentially on the UI executor. Register cancellation of the active request with Shared.resources and close prior copies immediately on failure/cancellation. Do not double-own the same Credential in multiple callbacks. A late granted copy for an evicted connection is closed on arrival.

Inside `connect`, select each source without a temporary file:

```java
try (InputStream stream = secret.keyBytes().isPresent()
        ? new ByteArrayInputStream(secret.keyBytes().orElseThrow())
        : Files.newInputStream(secret.keyPath().orElseThrow())) {
    String phrase = secret.passphrase() == null ? null : new String(secret.passphrase());
    Iterable<KeyPair> pairs = SecurityUtils.loadKeyPairIdentities(session,
        NamedResource.ofName("Vault SSH credential"), stream,
        phrase == null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(phrase));
    for (KeyPair pair : pairs) { session.addPublicKeyIdentity(pair); keys.add(pair); }
}
```

Keep source selection guarded by kind: password-only credentials bypass this block. VaultKeys requires `Kind.SSH_KEY`, present keyBytes and absent keyPath. An Auth.Vault containing a managed SSH key is allowed for manually selected managed credentials. Always remove identities and close every acquired credential in finally. Retain host-key checks and target attribution. Default disk identity provider remains empty and an agent factory is configured only for Auth.Agent.

- [x] **5.4 Preserve editing and display behavior.** HostEditor must represent an ordered key list without silently converting it to a single credential when an unrelated field is saved. Add list controls for Add/Remove/Move up/Move down using the existing Vault picker; require managed SSH_KEY descriptors for the multiple-key mode. Existing single credential/password and Agent radio choices remain. Labels show key names or `N Vault keys`, never key contents. Update all exhaustive Auth switches and equality-based tests.

- [x] **5.5 Run GREEN and commit.** Run all Remote tests and the app's bundled-plugin tests. Commit `Authenticate Remote hosts with ordered in-memory Vault keys`.

## Task 6: Config import, host repair and failure-safe orchestration

**Files**

Create:
- `plugins/remote/src/main/java/dev/jasper/remote/hosts/IdentityPaths.java`
- `plugins/remote/src/main/java/dev/jasper/remote/ConfigImportController.java`
- `plugins/remote/src/test/java/dev/jasper/remote/ConfigImportControllerTest.java`

Modify under `plugins/remote/src/main/java/dev/jasper/remote/`:
- `hosts/{SshConfig,ConfigImport,HostStore}.java`
- `ui/ImportPanel.java`, `RemotePlugin.java`

Modify tests `hosts/{SshConfigTest,ConfigImportTest,HostStoreTest}.java`, `ui/{ImportPanelTest,UiTestAccess}.java`, `RemotePluginTest.java`, `FakeVault.java`.

**Interfaces**

```java
// SshConfig.Entry replaces identityFile Optional with ordered identities.
record Entry(String alias, Optional<String> hostname, OptionalInt port,
             Optional<String> user, Optional<String> proxyJump, List<String> identityFiles) {}

// IdentityPaths is pure: never reads key contents.
public static Path resolve(String expression, Path home, String localUser,
                           String host, String remoteUser, int port);

// ConfigImport is a pure preview/binding model, no SDK/Vault dependency.
public record Candidate(UUID id, String name, String hostname, int port, String username,
    List<Path> identities, Optional<UUID> jump, Optional<RemoteHost> previous, List<String> errors) {}
public static List<Candidate> plan(SshConfig.Parsed parsed, List<RemoteHost> existing,
                                  Path home, String localUser);
public static RemoteHost bind(Candidate candidate, Auth auth, Instant now);

// HostStore: merge selected rows after comparing their preview snapshot to current disk.
public CompletableFuture<Void> importSelected(List<RemoteHost> expected,
                                              List<RemoteHost> replacements);
```

`expected` contains the original selected existing hosts; new rows are identified by absence from expected. Constructor/list values are defensive immutable copies. Candidates are not RemoteHosts with dummy auth; only successful binding creates an authenticated saved host.

- [x] **6.1 Write failing parser/repair regressions with literal expectations.** Test IdentityFile accumulation across globals/specific Host/Host *, ordered deduplication, quoted paths, Include, scalar first-value behavior, `none`, literal `%`, expansion of `%d/%u/%r/%h/%p`, unsupported tokens/`${...}`, and relative paths on the current platform. `none` contributes no path and never discards other explicit paths; it does not trigger default-key discovery.

```java
@Test void reimportPreservesIdentityAndUserOrganization() {
    UUID key = UUID.randomUUID();
    RemoteHost original = RemoteHost.create("PROD", "old.example", 22, "old", Auth.AGENT,
        "Servers", Optional.empty()).withFavorite(true);
    var parsed = SshConfig.parse("Host prod\n HostName new.example\n User deploy\n IdentityFile ~/.ssh/id_ed25519\n",
        include -> List.of());
    var row = ConfigImport.plan(parsed, List.of(original), Path.of("/home/me"), "me").getFirst();
    RemoteHost updated = ConfigImport.bind(row, new Auth.VaultKeys(List.of(key)), Instant.ofEpochSecond(500));
    assertThat(updated.id()).isEqualTo(original.id());
    assertThat(updated.created()).isEqualTo(original.created());
    assertThat(updated.group()).isEqualTo("Servers");
    assertThat(updated.favorite()).isTrue();
    assertThat(updated.hostname()).isEqualTo("new.example");
    assertThat(updated.username()).isEqualTo("deploy");
}
```

HostStoreTest: select an update, write a different version externally before the queued import runs, and assert failure plus byte-for-byte preservation of that external file. Also test unrelated added hosts survive; duplicate aliases/new alias races reject; unchanged selected rows succeed; invalid external TOML rejects; jump dependency missing or still Agent rejects; cycles reject.

Add the conflict regression to the existing HostStoreTest fixture:

```java
@Test void importedUpdateCannotOverwriteAnExternalEdit(@TempDir Path dir) throws Exception {
    HostStore store = store(dir);
    RemoteHost original = RemoteHost.create("prod", "old.example", 22, "u", Auth.AGENT, "G", Optional.empty());
    store.put(original); run();
    RemoteHost imported = original.withEdited("prod", "imported.example", 22, "u",
        new Auth.VaultKeys(List.of(UUID.randomUUID())), "G", Optional.empty());
    var save = store.importSelected(List.of(original), List.of(imported));
    RemoteHost external = original.withFavorite(true);
    String outside = HostFile.format(List.of(external));
    Files.writeString(store.file(), outside);
    run();
    assertThat(save).isCompletedExceptionally();
    assertThat(Files.readString(store.file())).isEqualTo(outside);
}
```

- [x] **6.2 Run RED.**

```sh
./gradlew :jasper-plugin-remote:test --tests '*SshConfigTest' --tests '*ConfigImportTest' --tests '*HostStoreTest'
```

- [x] **6.3 Implement pure parsing and bind logic.** Replace the parser's per-block scalar map with a block containing the scalar map and identity list. Keep scalar `putIfAbsent`; append IdentityFile values. When resolving a concrete alias, walk applicable blocks in existing order and accumulate identities into LinkedHashSet. Do not feed IdentityFile through scalar-first-value logic. Retain unsupported directive reporting.

`IdentityPaths.resolve` performs a single character scan; a `%` consumes exactly the following token and substitutes once, with no recursive expansion. Reject a trailing `%`, unknown token, `${`, unsupported tilde username, empty result, and invalid Path. Expand `~/` and relative paths against home; normalize without filesystem reads. On Windows recognize platform-native absolute paths using Path.isAbsolute. No shell/environment execution.

Build alias IDs before resolving jumps, using existing IDs for updates. Do not strip `user@`, ports, or extra comma-separated jumps and silently discard them: support a single alias reference (optionally `none` for no jump), otherwise report an unsupported ProxyJump expression. Expose missing/unselected/Agent jump dependencies to the preview; user includes/updates them or leaves the dependent host unselected.

Binding preserves user-owned fields:

```java
public static RemoteHost bind(Candidate c, Auth auth, Instant now) {
    if (!c.errors().isEmpty()) throw new IllegalArgumentException(String.join("; ", c.errors()));
    RemoteHost old = c.previous().orElse(null);
    return new RemoteHost(c.id(), c.name(), c.hostname(), c.port(), c.username(), auth,
        old == null ? "" : old.group(), old != null && old.favorite(), c.jump(),
        old == null ? now : old.created(), now);
}
```

Implement `HostStore.importSelected` using its existing private mutate queue. At the worker's forced current-file read, compare each selected previous record (full equality) with current; compare new aliases case-insensitively for conflicts. Merge replacements by UUID into current without dropping unrelated rows. Validate resulting jumps and names before write. A mismatch throws a safe “Hosts changed; refresh the import preview” error; do not write. Existing filesystem race guarantees remain as documented; this does not claim a cross-process atomic compare-and-swap.

- [x] **6.4 Write controller and UI RED tests before wiring the orchestration.** Extend FakeVault with a real controllable pending import future, recorded requests and explicit returned descriptors. Assert Remote behavior, not fake implementation: hosts stay unchanged until durable import success, cancellation does not save hosts, returned request bindings populate VaultKeys, failures leave the preview open, and no-key rows cannot import without an eligible credential. Verify existing rows start unchecked but can be checked for Update. Retain a generated-file integration test through real Vault in Task 7; fake tests only isolate orchestration timing.

- [x] **6.5 Implement ConfigImportController and replace RemotePlugin's inline import.** The controller owns one preview operation, selected candidates, source request IDs, chosen no-key credential IDs, pending future and window lifetime subscription. RemotePlugin constructs it with `PluginContext`, optional VaultApi, HostStore, sshDir, UI executor and a completion callback. It has `show(WindowHandle owner)` and `close()`; no new interface is needed. Move import parsing/background callbacks out of the already-large RemotePlugin.

On Import, freeze selected rows, validate all dependencies, generate stable request IDs for normalized source paths, and call VaultApi.importSshKeys. After success verify the controller/window is still active, require a returned managed descriptor for every source, bind identities in config order, then call importSelected with the preview originals. Existing no-key choices must be returned in selectedCredentials and still match allowed kinds. Re-check jump dependencies against current host state at the store boundary.

The relevant continuation is:

```java
pending.whenComplete((answer, failure) -> ui.execute(() -> {
    if (closed || !owner.isOpen()) return;
    if (failure != null) { panel.failed(safeImportMessage(failure)); return; }
    if (answer.isEmpty()) { panel.editable(); return; }
    SshKeyImportResult imported = answer.orElseThrow();
    List<RemoteHost> replacements = bindSelected(imported);
    store.importSelected(previousSelected(), replacements).whenComplete((ignored, saveFailure) -> ui.execute(() -> {
        if (saveFailure != null) {
            context.notices().error("Keys were saved in Vault; hosts were not updated. Refresh the import and retry.");
            if (!closed) panel.failed("Hosts were not saved. Refresh the preview and retry.");
        } else {
            if (!closed) panel.completed(replacements.size() + " SSH hosts saved using Vault credentials");
        }
    }));
}));
```

Define `bindSelected(SshKeyImportResult)` and `previousSelected()` as private controller methods using the frozen candidates and source-id map. Define `safeImportMessage(Throwable)` to unwrap completion exceptions and accept only the Vault API's safe user-facing messages; do not log private parser exceptions. Convert binding errors inside the completion callback into panel.failed so futures do not silently swallow failures. `panel.editable()` re-enables selection; `panel.failed(String)` restores editing and displays wrapped error text. `panel.completed(String)` shows the result, disables import controls and changes Cancel to Close; it never calls a nonexistent success-notice SDK API. Track new/updated/skipped counts from the frozen selection in that result.

ImportPanel displays name/address, New/Update, key paths or selected credential, and blocked reasons, with Select/Refresh/Choose credential/Import/Cancel controls. Refresh invalidates a pending preview and rereads hosts/config. Choose credential uses VaultApi.pick and accepts managed SSH_KEY or ACCOUNT_PASSWORD only; legacy key choices explain that their source must be imported first. Final selection still passes through Vault's scoped batch consent. Avoid HTML interpretation of user-controlled host/key names and hardcoded colors.

Cancel before host write leaves hosts unchanged. Once host write has started, let it complete and report its result; do not imply cancellation rolled back a durable Vault save. Key persistence without host persistence is recoverable and must not delete keys.

- [x] **6.6 Run GREEN and commit.** Run all Remote/Vault tests plus app bundled-plugin integration. Commit `Import SSH config into self-contained Vault-backed hosts`.

## Task 7: Installed-plugin acceptance, docs and final verification

**Files**

Modify:
- `plugins/remote/src/main/resources/plugin.toml` (version 0.2.0, Vault floor >=0.2)
- `plugins/vault/src/main/resources/plugin.toml` (confirm version 0.2.0)
- `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`
- `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`
- `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`
- `docs/{remote,STATUS,plugin-authoring}.md`, `docs/superpowers/specs/2026-09-22-jasper-vault-design.md` (link the approved amendment), both plugin package-info/API documentation
- This plan's status/checklist and the approved spec's implementation status.

Create `plugins/remote/src/test/java/dev/jasper/remote/ManagedImportIntegrationTest.java` and any owner-package test bridges under test sources only.

The fake currently discards dialog content in FakeUi.FakeWindow.setContent. To drive
real plugin forms without production-only test accessors, also modify:

- `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeUi,FakePluginHost}.java`
- `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java`

Retain a JComponent `content` in FakeWindow, assign it in setContent when open, and
clear it after close handlers have run. Add a Javadoc-documented testkit inspector
(the SDK interface and version remain unchanged):

```java
public Optional<javax.swing.JComponent> windowContent(String pluginId, String title) {
    FakePluginContext context = contexts.get(pluginId);
    if (context == null) return Optional.empty();
    return context.ui.windows.stream().filter(w -> !w.closed && w.title.equals(title))
        .map(w -> w.content).filter(Objects::nonNull).findFirst();
}
```

Before implementing it, add a FakeUiTest that creates a real fake dialog, sets a JPanel,
asserts this inspector returns that same panel, closes the dialog, and asserts empty.
The feature test uses this surface to find JPasswordField/JButton/JCheckBox controls
recursively and drive their real listeners. Keep that recursive helper test-only.
Use a test-source factory bridge at
`plugins/remote/src/test/java/dev/jasper/vault/VaultTestAccess.java` to construct the
VaultPlugin with the existing package-private DeviceSecrets/executor constructor and
an isolated FileStore, just as VaultPluginTest.plugin() already does. The bridge adds
no production accessors and never opens the real OS keychain.

- [ ] **7.1 Write the final acceptance test before completing integration wiring.** Use real VaultPlugin, RemotePlugin and FakePluginHost with generated SSH keys and LoopbackServer. Drive setup/unlock/import consent via owner-package test bridges and actual Swing buttons. Import two aliases sharing one encrypted key; assert one managed credential, two host UUIDs and grants for Remote only. Stop plugins, remove all original fixture key files, create a fresh host/runtime using the same temporary plugin data, unlock, and connect. Assert READY from loopback and no agent factory use. Also repair an existing Agent host and verify its UUID/group/favorite survive.

Parameterize the fake host icon selection for modern/retro; actual LAF rendering belongs in the app tests, where FlatLaf already exists. Use existing app-level isolated PluginRuntime staging for an additional real-loader test verifying Vault 0.2 API visibility, MINA parsing dependencies, both plugins ACTIVE, and correct optional-Vault failure messages. Keep MINA and Vault implementation classes out of SDK/API signatures. Test the legacy Agent-only Remote path with Vault absent.

The restart assertion must drive the saved state, not return a fabricated credential.
Use the following final body after the test's first host has completed the real import
and closed; `persistedRoot` is the Path passed to its FakePluginHost constructor,
`sshDir` contains its config, and `savedId` is the imported host UUID. The fields
`vaultInfo` and `remoteInfo` are PluginInfo values from the 0.2 manifests; `sourceKeys`
contains the generated fixture paths only. Declare the same factory/settle helpers as
RemotePluginTest (do not inherit that test class and rerun all its tests).

```java
for (Path path : sourceKeys) Files.deleteIfExists(path);
try (var restarted = new FakePluginHost(persistedRoot)) {
    VaultPlugin vault = VaultTestAccess.plugin();
    restarted.start(vaultInfo, Set.of(), Set.of(), vault);
    RemotePlugin remote = new RemotePlugin(Runnable::run, ignored -> Optional.empty(),
        (delay, action) -> () -> {}, sshDir);
    var context = restarted.start(remoteInfo, Set.of(), Set.of("dev.jasper.vault"), remote);
    settle(restarted);
    UUID windowId = restarted.addTerminalWindow();
    restarted.activateTerminalWindow(windowId);
    assertThat(restarted.invoke("dev.jasper.vault.open", windowId, null)).isTrue();
    JComponent unlock = restarted.windowContent("dev.jasper.vault", "Unlock Vault").orElseThrow();
    fields(unlock, JPasswordField.class).getFirst().setText("test-password");
    button(unlock, "Unlock").doClick();
    settle(restarted);
    RemoteHost saved = remote.store().host(savedId).orElseThrow();
    assertThat(saved.auth()).isInstanceOf(Auth.VaultKeys.class);
    remote.openHost(context.terminals().window(windowId).orElseThrow(), saved);
    settle(restarted);
    // Pre-trust only the generated loopback server key in this temporary home.
    // Drain until READY using the bounded awaitReady helper defined below.
    assertThat(restarted.terminalPanes()).hasSize(1);
    UUID pane = restarted.terminalPanes().getFirst();
    awaitReady(restarted, pane);
    assertThat(restarted.sessionOutput(pane)).startsWith("READY");
    assertThat(restarted.failures()).isEmpty();
}
```

Implement `fields` and `button` directly in ManagedImportIntegrationTest:

```java
static <T extends Component> List<T> fields(Container root, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (Component child : root.getComponents()) {
        if (type.isInstance(child)) result.add(type.cast(child));
        if (child instanceof Container nested) result.addAll(fields(nested, type));
    }
    return result;
}
static void awaitReady(FakePluginHost host, UUID pane) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!host.sessionOutput(pane).startsWith("READY")) {
        if (System.nanoTime() >= deadline) throw new AssertionError("No loopback READY banner");
        settle(host);
        Thread.sleep(10);
    }
}
static JButton button(Container root, String text) {
    return fields(root, JButton.class).stream().filter(b -> text.equals(b.getText()))
        .findFirst().orElseThrow();
}
```

The initial phase creates the temporary Vault with its real Create Vault form, fills
both master-password fields, disables device binding for this fixture, chooses the
selected host rows, submits the generated key's passphrase and presses Import and use.
Before the connection phase, trust `LoopbackServer.hostPublicKey()` using the existing
KnownHosts helper under Remote's temporary context.dataDirectory. Wait for READY with
a deadline and UI/background draining; do not rely on a single sleep or a preset key
in the system agent. Put these setup statements in the test body so failure identifies
the exact UI stage. Run this new test red before completing its missing wiring.

- [ ] **7.2 Update metadata and documentation.** Change Remote description to mention managed Vault imports while retaining optional Agent use for legacy/manual hosts. Update fixture PluginInfo/dependency declarations from 0.1 to 0.2 where exercising the new import API; leave compatibility tests intentionally on old versions. Document the single user flow, encrypted passphrase handling, no-key rows, re-import Update behavior, partial persistence/retry, version-1 backup/downgrade, and the fact that generated/path-only entries stay as they were. Remove guidance claiming imports fall back to the system agent.

- [ ] **7.3 Run the full gate and inspect exact results.**

```sh
./gradlew check :jasper-app:installDist
```

Aggregate XML counts from `jasper-*/build/test-results/test/TEST-*.xml` and `plugins/*/build/test-results/test/TEST-*.xml`. Report tests/passes/skips/failures/errors rather than guessing from Gradle task counts. Verify both staged plugin manifests are 0.2.0, Remote has the new Vault floor, and Vault's runtime directory contains sshd-common, BouncyCastle and the selected logging adapter. Run `git diff --check` and the AGENTS.md source hygiene scan. Inspect actual 1x/2x headless import/passphrase/managed-key manager renders in both appearances; do not open a native window or start a shell.

- [ ] **7.4 Perform the required independent final review.** Use requesting-code-review with the actual branch diff from the pre-implementation commit. Ask specifically for secret ownership, rollback/upgrade backup, ordered authentication, partial persistence, UI cancellation and API/classloader compatibility. Give the reviewer the spec, plan, verification log and exact SHAs; no full-history fork. Fix actionable findings with failing-then-passing regressions and rerun checks affected by each correction. Record any plan deviations in this status banner and docs/STATUS.md.

- [ ] **7.5 Commit and hand off.** Commit final integration/docs as `Verify managed SSH import across plugin reloads and both skins`, with the required trailer. Leave the branch/worktree and rebuilt distribution available. Final response should say what changed, give exact verification, and tell the user to re-import/check Update on existing hosts. State native real-host acceptance remains user-run; never claim their actual host connected from loopback evidence.

## Spec coverage and self-review

| Spec requirement | Implementation/test owner |
| --- | --- |
| Managed bytes, passphrases, close/lock/delete zeroing | Tasks 1, 3, 4 |
| v1 reads, v2 writes, bounded decode, encrypted backup | Tasks 1–2 |
| Serialized concurrent imports/edits/rekeys | Tasks 2, 4 |
| Setup/unlock, passphrase retry, scoped Import and use | Task 4 |
| No-key choice, absent Vault, no Agent fallback | Tasks 4, 6 |
| Multi-IdentityFile order/path expansion, jump dependencies | Tasks 5–6 |
| Deduplication and legacy standalone upgrade | Task 4 |
| Host update identity/organization/conflict preservation | Task 6 |
| No source file/agent needed after restart | Tasks 5, 7 |
| Partial save, stop/window/lock cancellation | Tasks 2, 4, 6–7 |
| Manager list/rename/delete/public key, legacy compatibility | Tasks 1, 4–5 |
| API versions, isolated loaders, both skins, all checks | Task 7 |

Before requesting plan approval, verify every path exists or is explicitly marked Create, every new method used by another task is defined in its Interfaces or implementation step, and every Review Focus item has its owning regression above. No production changes or user credential migration are part of this planning commit.
