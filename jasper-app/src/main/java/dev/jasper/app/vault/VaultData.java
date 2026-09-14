package dev.jasper.app.vault;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

final class VaultData implements AutoCloseable {
    static final int MAX_ENTRIES = 10_000, MAX_KEY = 1_048_576, MAX_SECRET = 4096, MAX_TEXT = 4096;
    VaultSettings settings = VaultSettings.DEFAULT;
    final LinkedHashMap<UUID, Login> logins = new LinkedHashMap<>();
    final LinkedHashMap<UUID, Key> keys = new LinkedHashMap<>();

    static final class Login {
        String name, username; UUID keyId; final char[] password;
        Login(String name, String username, UUID keyId, char[] password) {
            this.name = name; this.username = username; this.keyId = keyId; this.password = password.clone();
        }
    }
    static final class Key {
        String name; final String algorithm, fingerprint, publicKey;
        final byte[] bytes; final char[] passphrase;
        Key(String name, String algorithm, String fingerprint, String publicKey, byte[] bytes, char[] passphrase) {
            this.name = name; this.algorithm = algorithm; this.fingerprint = fingerprint;
            this.publicKey = publicKey; this.bytes = bytes.clone(); this.passphrase = passphrase.clone();
        }
    }
    static void text(String s, boolean required) throws IOException {
        if (s == null || (required && s.isBlank()) || s.length() > MAX_TEXT
                || !StandardCharsets.UTF_8.newEncoder().canEncode(s)
                || s.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT
                || s.chars().anyMatch(Character::isISOControl)) throw new IOException("Invalid credential metadata");
    }
    void validate() throws IOException {
        if (logins.size() + keys.size() > MAX_ENTRIES) throw new IOException("Too many credentials");
        var fingerprints = new HashSet<String>();
        for (var entry : keys.entrySet()) {
            Key k = entry.getValue();
            if (entry.getKey() == null || logins.containsKey(entry.getKey())) throw new IOException("Duplicate credential ID");
            text(k.name, true); text(k.algorithm, true); text(k.fingerprint, true); text(k.publicKey, true);
            if (!fingerprints.add(k.fingerprint) || k.bytes.length < 1 || k.bytes.length > MAX_KEY
                    || k.passphrase.length > MAX_SECRET) throw new IOException("Invalid private key record");
        }
        for (var entry : logins.entrySet()) {
            Login l = entry.getValue(); text(l.name, true); text(l.username, true);
            if (entry.getKey() == null || l.password.length > MAX_SECRET
                    || (l.keyId != null && !keys.containsKey(l.keyId))
                    || (l.password.length == 0 && l.keyId == null)) throw new IOException("Invalid login record");
        }
    }
    byte[] encode() throws IOException {
        validate();
        try (var buffer = new WipingBuffer(); var out = new DataOutputStream(buffer)) {
            out.writeInt(1); out.writeInt(settings.autoLockMinutes()); out.writeInt(settings.rememberDays());
            out.writeInt(keys.size());
            for (var entry : keys.entrySet()) {
                id(out, entry.getKey()); Key k = entry.getValue();
                string(out, k.name); string(out, k.algorithm); string(out, k.fingerprint); string(out, k.publicKey);
                out.writeInt(k.bytes.length); out.write(k.bytes); chars(out, k.passphrase);
            }
            out.writeInt(logins.size());
            for (var entry : logins.entrySet()) {
                id(out, entry.getKey()); Login l = entry.getValue();
                string(out, l.name); string(out, l.username); out.writeBoolean(l.keyId != null);
                if (l.keyId != null) id(out, l.keyId);
                chars(out, l.password);
            }
            out.flush();
            if (buffer.size() > VaultFiles.MAX_FILE - VaultCrypto.HEADER - 16) throw new IOException("Vault too large");
            return buffer.toByteArray();
        } catch (IllegalArgumentException e) { throw new IOException("Vault too large", e); }
    }
    static VaultData decode(byte[] plain) throws IOException {
        VaultData d = new VaultData();
        try (var in = new DataInputStream(new ByteArrayInputStream(plain))) {
            if (in.readInt() != 1) throw new IOException("Unsupported vault payload version");
            d.settings = new VaultSettings(in.readInt(), in.readInt());
            int keyCount = length(in, MAX_ENTRIES);
            for (int i = 0; i < keyCount; i++) {
                UUID id = id(in); String name = string(in), algorithm = string(in), fingerprint = string(in), pub = string(in);
                byte[] bytes = exact(in, length(in, MAX_KEY)); char[] phrase = null;
                try {
                    phrase = chars(in);
                    if (d.keys.containsKey(id)) throw new IOException("Duplicate key ID");
                    d.keys.put(id, new Key(name, algorithm, fingerprint, pub, bytes, phrase));
                } finally { Arrays.fill(bytes, (byte) 0); if (phrase != null) Arrays.fill(phrase, (char) 0); }
            }
            int loginCount = length(in, MAX_ENTRIES - keyCount);
            for (int i = 0; i < loginCount; i++) {
                UUID id = id(in); String name = string(in), user = string(in);
                UUID keyId = in.readBoolean() ? id(in) : null; char[] password = chars(in);
                try {
                    if (d.logins.containsKey(id)) throw new IOException("Duplicate login ID");
                    d.logins.put(id, new Login(name, user, keyId, password));
                } finally { Arrays.fill(password, (char) 0); }
            }
            if (in.read() != -1) throw new IOException("Trailing vault payload");
            d.validate(); return d;
        } catch (IOException | IllegalArgumentException e) {
            d.close(); throw new IOException("Invalid vault payload", e);
        }
    }
    VaultData copy() throws IOException {
        byte[] encoded = encode();
        try { return decode(encoded); } finally { Arrays.fill(encoded, (byte) 0); }
    }
    private static void string(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in) throws IOException {
        byte[] bytes = exact(in, length(in, MAX_TEXT));
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IOException("Invalid UTF-8 metadata", e); }
    }
    private static void chars(DataOutputStream out, char[] chars) throws IOException {
        out.writeInt(chars.length); for (char c : chars) out.writeChar(c);
    }
    private static char[] chars(DataInputStream in) throws IOException {
        char[] result = new char[length(in, MAX_SECRET)];
        try { for (int i = 0; i < result.length; i++) result[i] = in.readChar(); return result; }
        catch (IOException e) { Arrays.fill(result, (char) 0); throw e; }
    }
    private static int length(DataInputStream in, int max) throws IOException {
        int n = in.readInt(); if (n < 0 || n > max) throw new IOException("Invalid vault length"); return n;
    }
    private static byte[] exact(DataInputStream in, int n) throws IOException {
        byte[] bytes = in.readNBytes(n); if (bytes.length != n) { Arrays.fill(bytes, (byte) 0); throw new EOFException(); }
        return bytes;
    }
    private static void id(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID id(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    @Override public void close() {
        logins.values().forEach(l -> Arrays.fill(l.password, (char) 0));
        keys.values().forEach(k -> { Arrays.fill(k.bytes, (byte) 0); Arrays.fill(k.passphrase, (char) 0); });
        logins.clear(); keys.clear();
    }
    private static final class WipingBuffer extends ByteArrayOutputStream {
        private void capacity(int added) {
            if (added < 0 || added > VaultFiles.MAX_FILE - count) throw new IllegalArgumentException("Vault too large");
            int required = count + added;
            if (required > buf.length) {
                byte[] previous = buf;
                buf = Arrays.copyOf(previous, Math.min(VaultFiles.MAX_FILE, Math.max(required, buf.length * 2)));
                Arrays.fill(previous, (byte) 0);
            }
        }
        @Override public synchronized void write(int b) { capacity(1); buf[count++] = (byte) b; }
        @Override public synchronized void write(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length); capacity(len);
            System.arraycopy(b, off, buf, count, len); count += len;
        }
        @Override public void close() { Arrays.fill(buf, (byte) 0); reset(); }
    }
}
