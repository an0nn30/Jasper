package dev.jasper.vault.model;

import dev.jasper.vault.crypto.CorruptVaultException;
import dev.jasper.vault.crypto.SecureBytes;
import java.io.ByteArrayInputStream;
import dev.jasper.vault.crypto.WipingOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Version 1: {@code u16 version}, then accounts, keys, notes and grants, each as {@code i32 count} and
 * length-prefixed fields. Strings are {@code i32 length + UTF-8}; secrets the same with {@code -1} for
 * null, and the intermediate byte arrays are zeroed. Instants are epoch seconds.
 */
public final class VaultCodec {
    static final int VERSION = 2;
    private static final int MAX_LENGTH = 1 << 24;

    private VaultCodec() { }

    public static byte[] encode(Vault vault) {
        var bytes = new WipingOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeShort(vault.managedKeys().isEmpty() ? vault.payloadVersion() : VERSION);
            out.writeInt(vault.accounts().size());
            for (Account account : vault.accounts()) {
                uuid(out, account.id()); string(out, account.name()); string(out, account.username());
                switch (account.auth()) {
                    case Auth.Password password -> { out.writeByte(0); secret(out, password.password()); }
                    case Auth.Key key -> { out.writeByte(1); string(out, key.keyPath().toString()); secret(out, key.passphrase()); }
                    case Auth.KeyAndPassword both -> { out.writeByte(2); string(out, both.keyPath().toString()); secret(out, both.passphrase()); secret(out, both.password()); }
                }
                out.writeLong(account.created().getEpochSecond()); out.writeLong(account.updated().getEpochSecond());
            }
            out.writeInt(vault.keys().size());
            for (SshKey key : vault.keys()) {
                uuid(out, key.id()); string(out, key.name()); string(out, key.algorithm()); string(out, key.fingerprint()); string(out, key.comment());
                string(out, key.privatePath().toString()); string(out, key.publicPath().toString()); out.writeLong(key.created().getEpochSecond());
            }
            out.writeInt(vault.notes().size());
            for (Note note : vault.notes()) { uuid(out, note.id()); string(out, note.name()); secret(out, note.text()); out.writeLong(note.updated().getEpochSecond()); }
            out.writeInt(vault.grants().size());
            for (Grant grant : vault.grants()) { string(out, grant.pluginId()); uuid(out, grant.credentialId()); }
            if (vault.payloadVersion() == 2 || !vault.managedKeys().isEmpty()) {
                validateManaged(vault);
                out.writeInt(vault.managedKeys().size());
                for (ManagedSshKey key : vault.managedKeys()) {
                    uuid(out, key.id()); string(out, key.name()); string(out, key.algorithm());
                    string(out, key.fingerprint()); string(out, key.publicKey());
                    out.writeInt(key.privateKey().length); out.write(key.privateKey());
                    secret(out, key.passphrase()); out.writeLong(key.created().getEpochSecond());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Vault decode(byte[] bytes) {
        var vault = new Vault();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readUnsignedShort();
            if (version != 1 && version != VERSION) throw new CorruptVaultException("The vault contents are version " + version + "; this plugin reads version " + VERSION);
            int accounts = count(in);
            for (int i = 0; i < accounts; i++) {
                UUID id = uuid(in); String name = string(in), username = string(in);
                Auth auth = switch (in.readByte()) {
                    case 0 -> new Auth.Password(secret(in));
                    case 1 -> new Auth.Key(Path.of(string(in)), secretOrNull(in));
                    case 2 -> new Auth.KeyAndPassword(Path.of(string(in)), secretOrNull(in), secret(in));
                    default -> throw new CorruptVaultException("Unknown authentication kind");
                };
                vault.accounts().add(new Account(id, name, username, auth, Instant.ofEpochSecond(in.readLong()), Instant.ofEpochSecond(in.readLong())));
            }
            int keys = count(in);
            for (int i = 0; i < keys; i++)
                vault.keys().add(new SshKey(uuid(in), string(in), string(in), string(in), string(in), Path.of(string(in)), Path.of(string(in)), Instant.ofEpochSecond(in.readLong())));
            int notes = count(in);
            for (int i = 0; i < notes; i++) vault.notes().add(new Note(uuid(in), string(in), secret(in), Instant.ofEpochSecond(in.readLong())));
            int grants = count(in);
            for (int i = 0; i < grants; i++) vault.grants().add(new Grant(string(in), uuid(in)));
            if (version == 2) {
                vault.requireManagedFormat();
                int managed = count(in);
                if (managed > 4096) throw new CorruptVaultException("Too many managed keys");
                long total = 0;
                for (int i = 0; i < managed; i++) {
                    UUID id = uuid(in); String name = string(in), algorithm = string(in), fingerprint = string(in), pub = string(in);
                    byte[] key = null; char[] phrase = null;
                    try {
                        int length = count(in);
                        total += length;
                        if (length < 1 || length > ManagedSshKey.MAX_BYTES || total > 64L * 1024 * 1024)
                            throw new CorruptVaultException("Bad managed-key size");
                        key = new byte[length]; in.readFully(key); phrase = secretOrNull(in);
                        vault.managedKeys().add(new ManagedSshKey(id, name, algorithm, fingerprint, pub, key, phrase, Instant.ofEpochSecond(in.readLong())));
                        key = null; phrase = null;
                    } finally { SecureBytes.zero(key); SecureBytes.zero(phrase); }
                }
            }
            var ids = new java.util.HashSet<UUID>();
            for (Account a : vault.accounts()) if (!ids.add(a.id())) throw new CorruptVaultException("Duplicate entry ID");
            for (SshKey k : vault.keys()) if (!ids.add(k.id())) throw new CorruptVaultException("Duplicate entry ID");
            for (ManagedSshKey k : vault.managedKeys()) if (!ids.add(k.id())) throw new CorruptVaultException("Duplicate entry ID");
            for (Note n : vault.notes()) if (!ids.add(n.id())) throw new CorruptVaultException("Duplicate entry ID");
            if (in.read() != -1) throw new CorruptVaultException("Trailing vault data");
            return vault;
        } catch (EOFException truncated) {
            vault.zero();
            throw new CorruptVaultException("The vault contents are truncated");
        } catch (CorruptVaultException failure) {
            vault.zero(); throw failure;
        } catch (IOException | RuntimeException failure) {
            vault.zero();
            throw new CorruptVaultException("The vault contents are unreadable");
        }
    }

    public static void validateManaged(Vault vault) {
        if (vault.managedKeys().size() > 4096 || vault.managedKeys().stream().mapToLong(k -> k.privateKey().length).sum() > 64L * 1024 * 1024)
            throw new IllegalArgumentException("Too many managed key bytes");
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }

    private static void string(DataOutputStream out, String text) throws IOException {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length); out.write(utf8);
    }

    private static String string(DataInputStream in) throws IOException { byte[] bytes = new byte[count(in)]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8); }

    private static void secret(DataOutputStream out, char[] text) throws IOException {
        if (text == null) { out.writeInt(-1); return; }
        byte[] utf8 = SecureBytes.utf8(text);
        try { out.writeInt(utf8.length); out.write(utf8); } finally { SecureBytes.zero(utf8); }
    }

    private static char[] secretOrNull(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) return null;
        if (length < 0 || length > MAX_LENGTH) throw new CorruptVaultException("Bad length");
        byte[] utf8 = new byte[length];
        try { in.readFully(utf8); return SecureBytes.chars(utf8); } finally { SecureBytes.zero(utf8); }
    }

    private static char[] secret(DataInputStream in) throws IOException {
        char[] value = secretOrNull(in);
        if (value == null) throw new CorruptVaultException("A required secret is missing");
        return value;
    }

    private static int count(DataInputStream in) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > MAX_LENGTH) throw new CorruptVaultException("Bad length");
        return value;
    }
}
