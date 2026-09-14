package dev.jasper.app.vault;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.jasper.app.vault.VaultSnapshot.*;

public final class VaultService implements AutoCloseable {
    public enum LockReason { AUTO, EXPLICIT }
    public record KeyPreview(String algorithm, String fingerprint, String publicKey) {}
    public record ImportResult(UUID id, boolean duplicate) {}
    private record Hint(UUID id, Instant until, boolean revoked) {}
    private static final long REMEMBER_MAGIC = 0x4D525952454D3031L, HINT_MAGIC = 0x4D5259484E543031L;
    /** Concrete filesystem boundary; permits controlled blocked-publication verification. */
    static class Publication {
        void publish(Path path, byte[] bytes, byte[] expectedHash, boolean create) throws IOException {
            VaultFiles.publish(path, bytes, expectedHash, create);
        }
        void replaceHint(Path path, byte[] bytes) throws IOException { VaultFiles.replaceHint(path, bytes); }
    }
    private final Publication publication;
    private final Path path, hintPath;
    private final DeviceAccessStore device;
    private final boolean deviceAvailable;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Object state = new Object(), operation = new Object();
    private VaultCrypto.Header header;
    private VaultData data;
    private byte[] key, fileHash;
    private long generation, revision, lastActivity;
    private boolean exists, closed, rememberedDenied;
    private VaultSettings settings = VaultSettings.DEFAULT;
    private Hint hint;
    private String warning;

    /** Reads only local encrypted header and nonsecret deadline hint; never reads the OS store. */
    public VaultService(Path path, DeviceAccessStore device, Clock clock) throws IOException {
        this(path, device, clock, System::nanoTime);
    }
    public VaultService(Path path, DeviceAccessStore device, Clock clock, LongSupplier nanoTime) throws IOException {
        this(path, device, clock, nanoTime, new Publication());
    }
    VaultService(Path path, DeviceAccessStore device, Clock clock, LongSupplier nanoTime, Publication publication) throws IOException {
        this.publication = Objects.requireNonNull(publication);
        this.path = path.toAbsolutePath().normalize(); this.hintPath = this.path.resolveSibling(this.path.getFileName() + ".device");
        this.device = Objects.requireNonNull(device); this.deviceAvailable = device.available();
        this.clock = Objects.requireNonNull(clock);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        exists = Files.exists(this.path, LinkOption.NOFOLLOW_LINKS);
        if (exists) { header = VaultCrypto.header(VaultFiles.read(this.path)); readHint(); }
    }
    public VaultSnapshot snapshot() {
        synchronized (state) {
            List<LoginInfo> logins = new ArrayList<>(); List<KeyInfo> keys = new ArrayList<>();
            if (data != null) {
                Map<UUID, Integer> uses = new HashMap<>();
                data.logins.forEach((id, l) -> {
                    logins.add(new LoginInfo(id, l.name, l.username, l.keyId, l.password.length > 0));
                    if (l.keyId != null) uses.merge(l.keyId, 1, Integer::sum);
                });
                data.keys.forEach((id, k) -> keys.add(new KeyInfo(id, k.name, k.algorithm, k.fingerprint, k.publicKey,
                        uses.getOrDefault(id, 0))));
            }
            return new VaultSnapshot(exists, data == null, logins, keys, settings,
                    rememberedDenied || hint == null || hint.revoked() ? null : hint.until(), revision,
                    warning != null ? warning : deviceAvailable ? null : "OS credential store unavailable");
        }
    }
    private long begin(boolean requireUnlocked) throws IOException {
        synchronized (state) {
            if (closed) throw new IOException("Vault service closed");
            if (requireUnlocked && data == null) throw new IOException("Vault locked");
            return generation;
        }
    }
    private void current(long token) throws IOException {
        if (closed || generation != token) throw new IOException("Vault operation cancelled by lock");
    }
    private static void duration(Duration duration) throws IOException {
        if (duration != null && (duration.compareTo(Duration.ofDays(1)) < 0 || duration.compareTo(Duration.ofDays(365)) > 0))
            throw new IOException("Remember duration must be between 1 and 365 days");
    }
    /** Successful creation unlocks. A later enrollment failure throws while leaving the created vault usable. */
    public void create(char[] password, Duration rememberFor) throws IOException {
        duration(rememberFor);
        synchronized (operation) {
            long token = begin(false);
            synchronized (state) { if (exists || data != null) throw new IOException("Vault already exists"); }
            VaultCrypto.Header fresh = VaultCrypto.fresh(); byte[] derived = VaultCrypto.derive(password, fresh.salt());
            VaultData candidate = new VaultData(); boolean accepted = false;
            try {
                byte[] encrypted = VaultCrypto.encrypt(fresh, derived, candidate);
                try (var verified = VaultCrypto.decrypt(encrypted, derived)) { verified.validate(); }
                byte[] publishedHash = VaultFiles.hash(encrypted);
                // Crossing this short boundary authorizes encrypted publication even if Lock follows.
                synchronized (state) { current(token); }
                publication.publish(path, encrypted, null, true);
                synchronized (state) {
                    // File existence is public state; a late completed create must offer Unlock, not Create.
                    header = fresh; exists = true; fileHash = publishedHash; revision++;
                    current(token);
                    data = candidate; key = derived;
                    settings = candidate.settings; lastActivity = nanoTime.getAsLong(); accepted = true;
                }
                if (rememberFor != null) enroll(token, rememberFor);
            } finally { if (!accepted) { candidate.close(); Arrays.fill(derived, (byte) 0); } }
        }
    }
    /** Password is caller-owned and never retained. Enrollment failure is reported after successful unlock. */
    public void unlock(char[] password, Duration rememberFor) throws IOException {
        duration(rememberFor);
        synchronized (operation) {
            long token = begin(false);
            synchronized (state) { if (data != null) throw new IOException("Vault already unlocked"); }
            byte[] encrypted = VaultFiles.read(path); VaultCrypto.Header parsed = VaultCrypto.header(encrypted);
            byte[] derived = VaultCrypto.derive(password, parsed.salt()); VaultData candidate = null; boolean accepted = false;
            try {
                candidate = VaultCrypto.decrypt(encrypted, derived);
                synchronized (state) {
                    current(token); header = parsed; data = candidate; key = derived; exists = true;
                    fileHash = VaultFiles.hash(encrypted); settings = candidate.settings;
                    lastActivity = nanoTime.getAsLong(); revision++; accepted = true;
                }
                if (rememberFor != null) enroll(token, rememberFor);
            } finally { if (!accepted) { if (candidate != null) candidate.close(); Arrays.fill(derived, (byte) 0); } }
        }
    }
    /** False means no usable remembered enrollment. Native I/O denial remains an IOException. */
    public boolean unlockRemembered() throws IOException {
        synchronized (operation) {
            long token = begin(false); Hint expected;
            synchronized (state) { if (data != null) return true; }
            readHint();
            synchronized (state) {
                if (data != null) return true;
                expected = hint;
                if (rememberedDenied || expected == null || expected.revoked() || !clock.instant().isBefore(expected.until())) return false;
            }
            byte[] encrypted = VaultFiles.read(path); VaultCrypto.Header parsed = VaultCrypto.header(encrypted);
            if (!expected.id().equals(parsed.id())) return false;
            byte[] payload = device.read(parsed.id().toString());
            if (payload == null) return false;
            byte[] derived = null; VaultData candidate = null; boolean accepted = false;
            try {
                if (payload.length != 64) return false;
                ByteBuffer b = ByteBuffer.wrap(payload);
                if (b.getLong() != REMEMBER_MAGIC) return false;
                UUID id = new UUID(b.getLong(), b.getLong()); Instant until = Instant.ofEpochMilli(b.getLong());
                if (!id.equals(parsed.id()) || !until.equals(expected.until()) || !clock.instant().isBefore(until)) return false;
                derived = new byte[32]; b.get(derived); candidate = VaultCrypto.decrypt(encrypted, derived);
                synchronized (state) {
                    current(token);
                    if (rememberedDenied || !clock.instant().isBefore(until)) return false;
                    header = parsed; data = candidate; key = derived; exists = true; fileHash = VaultFiles.hash(encrypted);
                    settings = candidate.settings; lastActivity = nanoTime.getAsLong(); revision++; accepted = true;
                }
                return true;
            } finally {
                Arrays.fill(payload, (byte) 0);
                if (!accepted) { if (derived != null) Arrays.fill(derived, (byte) 0); if (candidate != null) candidate.close(); }
            }
        }
    }
    private void enroll(long token, Duration duration) throws IOException {
        byte[] payload = new byte[64]; Hint next;
        synchronized (state) {
            current(token); if (data == null) throw new IOException("Vault locked");
            next = new Hint(header.id(), Instant.ofEpochMilli(clock.instant().plus(duration).toEpochMilli()), false);
            ByteBuffer.wrap(payload).putLong(REMEMBER_MAGIC).putLong(next.id().getMostSignificantBits())
                    .putLong(next.id().getLeastSignificantBits()).putLong(next.until().toEpochMilli()).put(key);
        }
        try {
            if (!device.available()) throw new IOException("OS credential store unavailable");
            device.write(next.id().toString(), payload);
            synchronized (state) { current(token); }
            writeHint(next);
            synchronized (state) { current(token); hint = next; rememberedDenied = false; warning = null; revision++; }
        } catch (IOException e) {
            try { device.delete(next.id().toString()); } catch (IOException rollback) { e.addSuppressed(rollback); }
            String message;
            synchronized (state) {
                message = data == null ? "Remembered access could not be saved"
                        : "Vault unlocked, but remembered access could not be saved";
                warning = message; revision++;
            }
            throw new IOException(message, e);
        } finally { Arrays.fill(payload, (byte) 0); }
    }
    private void readHint() {
        synchronized (state) { hint = null; if (header == null) return; }
        if (!Files.exists(hintPath, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            byte[] bytes = VaultFiles.read(hintPath);
            if (bytes.length != 33) throw new IOException("Invalid hint size");
            ByteBuffer b = ByteBuffer.wrap(bytes);
            if (b.getLong() != HINT_MAGIC) throw new IOException("Invalid hint format");
            UUID id = new UUID(b.getLong(), b.getLong()); Instant until = Instant.ofEpochMilli(b.getLong()); byte revoked = b.get();
            if (!id.equals(header.id()) || (revoked != 0 && revoked != 1)) throw new IOException("Invalid hint binding");
            synchronized (state) { hint = new Hint(id, until, revoked == 1); }
        } catch (IOException e) {
            synchronized (state) { hint = null; warning = "Remembered access metadata invalid; master password required"; }
        }
    }
    private void writeHint(Hint value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(33).putLong(HINT_MAGIC).putLong(value.id().getMostSignificantBits())
                .putLong(value.id().getLeastSignificantBits()).putLong(value.until().toEpochMilli()).put((byte) (value.revoked() ? 1 : 0)).array();
        publication.replaceHint(hintPath, bytes);
    }
    /** Seals immediately; EXPLICIT then revokes on disk/OS and may throw after the vault is already locked. */
    public void lock(LockReason reason) throws IOException {
        Objects.requireNonNull(reason);
        synchronized (state) {
            generation++; revision++;
            if (reason == LockReason.EXPLICIT) rememberedDenied = true;
            if (data != null) { data.close(); data = null; }
            if (key != null) { Arrays.fill(key, (byte) 0); key = null; }
        }
        if (reason == LockReason.EXPLICIT) forgetDeviceAccess();
    }
    public void forgetDeviceAccess() throws IOException {
        synchronized (state) { rememberedDenied = true; }
        synchronized (operation) {
            UUID id;
            synchronized (state) {
                // An earlier enrollment may have completed while this revocation waited for operation.
                rememberedDenied = true;
                if (header == null) return;
                id = header.id();
            }
            IOException diskError = null, nativeError = null;
            Hint revoked = new Hint(id, Instant.EPOCH, true);
            try { writeHint(revoked); } catch (IOException e) { diskError = e; }
            synchronized (state) { hint = revoked; revision++; }
            try { device.delete(id.toString()); } catch (IOException e) { nativeError = e; }
            synchronized (state) {
                warning = nativeError == null ? null : diskError == null
                        ? "Remembered access revoked locally; OS deletion failed"
                        : "Remembered access is blocked now, but disk and OS deletion failed; restart revocation is not guaranteed";
            }
            if (nativeError != null) {
                IOException failure = new IOException(warning, nativeError);
                if (diskError != null) failure.addSuppressed(diskError); throw failure;
            }
            // Successful OS deletion makes a failed sidecar update safe; report that persistence failure honestly.
            if (diskError != null) throw new IOException("OS remembered access deleted; local revocation marker could not be saved", diskError);
        }
    }
    public void userActivity() { synchronized (state) { if (!closed && data != null) lastActivity = nanoTime.getAsLong(); } }
    public boolean checkInactivity() throws IOException {
        synchronized (state) {
            if (data != null && settings.autoLockMinutes() > 0
                    && nanoTime.getAsLong() - lastActivity >= Duration.ofMinutes(settings.autoLockMinutes()).toNanos()) {
                lock(LockReason.AUTO);
                return true;
            }
            return false;
        }
    }
    public KeyPreview inspectKey(byte[] privateBytes, char[] passphrase) throws IOException {
        begin(true); return VaultKeys.inspect(privateBytes, passphrase);
    }
    public ImportResult importKey(String name, byte[] privateBytes, char[] passphrase) throws IOException {
        synchronized (operation) {
            long token = begin(true); VaultData.text(name, true);
            KeyPreview preview = VaultKeys.inspect(privateBytes, passphrase);
            synchronized (state) {
                current(token);
                for (var entry : data.keys.entrySet()) if (entry.getValue().fingerprint.equals(preview.fingerprint()))
                    return new ImportResult(entry.getKey(), true);
            }
            UUID id = UUID.randomUUID(); VaultData candidate = copy(token);
            try {
                candidate.keys.put(id, new VaultData.Key(name, preview.algorithm(), preview.fingerprint(), preview.publicKey(), privateBytes, passphrase));
                commit(token, candidate); return new ImportResult(id, false);
            } finally { candidate.close(); }
        }
    }
    public UUID saveLogin(UUID id, String name, String username, char[] password, UUID keyId) throws IOException {
        synchronized (operation) {
            long token = begin(true); VaultData candidate = copy(token); UUID target = id == null ? UUID.randomUUID() : id;
            try {
                VaultData.Login old = candidate.logins.get(target);
                if (id != null && old == null) throw new IOException("Login no longer exists");
                char[] value = password != null ? password : old == null ? new char[0] : old.password;
                var replacement = new VaultData.Login(name, username, keyId, value);
                candidate.logins.put(target, replacement);
                if (old != null) Arrays.fill(old.password, (char) 0);
                commit(token, candidate); return target;
            } finally { candidate.close(); }
        }
    }
    public void renameKey(UUID id, String name) throws IOException {
        synchronized (operation) {
            long token = begin(true); VaultData candidate = copy(token);
            try {
                var k = candidate.keys.get(id); if (k == null) throw new IOException("Key no longer exists");
                k.name = name; commit(token, candidate);
            } finally { candidate.close(); }
        }
    }
    private volatile java.util.function.Predicate<UUID> hostReferenceGuard=id->false;
    public void setHostReferenceGuard(java.util.function.Predicate<UUID> guard){hostReferenceGuard=Objects.requireNonNull(guard);}
    public void deleteLogin(UUID id) throws IOException {
        synchronized (operation) {
            if(hostReferenceGuard.test(id))throw new IOException("Credential is referenced by an SSH host");
            long token = begin(true); VaultData candidate = copy(token);
            try {
                var removed = candidate.logins.remove(id); if (removed == null) throw new IOException("Login no longer exists");
                Arrays.fill(removed.password, (char) 0); commit(token, candidate);
            } finally { candidate.close(); }
        }
    }
    public void deleteKey(UUID id) throws IOException {
        synchronized (operation) {
            if(hostReferenceGuard.test(id))throw new IOException("Credential is referenced by an SSH host");
            long token = begin(true); VaultData candidate = copy(token);
            try {
                if (candidate.logins.values().stream().anyMatch(l -> id.equals(l.keyId))) throw new IOException("Key is used by a login");
                var removed = candidate.keys.remove(id); if (removed == null) throw new IOException("Key no longer exists");
                Arrays.fill(removed.bytes, (byte) 0); Arrays.fill(removed.passphrase, (char) 0); commit(token, candidate);
            } finally { candidate.close(); }
        }
    }
    public void saveSettings(VaultSettings value) throws IOException {
        synchronized (operation) {
            long token = begin(true); VaultData candidate = copy(token);
            try { candidate.settings = Objects.requireNonNull(value); commit(token, candidate); }
            finally { candidate.close(); }
        }
    }
    private VaultData copy(long token) throws IOException {
        synchronized (state) { current(token); if (data == null) throw new IOException("Vault locked"); return data.copy(); }
    }
    private void commit(long token, VaultData candidate) throws IOException {
        byte[] ownKey, expectedHash; VaultCrypto.Header ownHeader;
        synchronized (state) { current(token); ownKey = key.clone(); ownHeader = header; expectedHash = fileHash; }
        try {
            byte[] encrypted = VaultCrypto.encrypt(ownHeader, ownKey, candidate);
            byte[] publishedHash = VaultFiles.hash(encrypted);
            VaultData verified = VaultCrypto.decrypt(encrypted, ownKey); boolean accepted = false;
            try {
                // Serialize disk writes with operation, never with the EDT-visible state monitor.
                // Once this boundary is crossed, Lock may seal while this encrypted write finishes.
                synchronized (state) { current(token); }
                publication.publish(path, encrypted, expectedHash, false);
                synchronized (state) {
                    fileHash = publishedHash; revision++;
                    current(token);
                    data.close(); data = verified; settings = verified.settings; accepted = true;
                }
            } finally { if (!accepted) verified.close(); }
        } finally { Arrays.fill(ownKey, (byte) 0); }
    }
    public CredentialMaterial resolveLogin(UUID id) throws IOException {
        synchronized (state) {
            begin(true); var login = data.logins.get(id); if (login == null) throw new IOException("Login no longer exists");
            var k = login.keyId == null ? null : data.keys.get(login.keyId);
            return new CredentialMaterial(login.username, login.password, k == null ? new byte[0] : k.bytes,
                    k == null ? new char[0] : k.passphrase);
        }
    }
    public CredentialMaterial resolveKey(UUID id, String username) throws IOException {
        synchronized (state) {
            begin(true); VaultData.text(username, true); var k = data.keys.get(id);
            if (k == null) throw new IOException("Key no longer exists");
            return new CredentialMaterial(username, new char[0], k.bytes, k.passphrase);
        }
    }
    @Override public void close() {
        synchronized (state) {
            closed = true;
            try { lock(LockReason.AUTO); } catch (IOException impossible) { throw new AssertionError(impossible); }
        }
    }
}
