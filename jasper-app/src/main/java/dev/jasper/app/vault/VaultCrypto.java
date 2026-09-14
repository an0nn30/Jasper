package dev.jasper.app.vault;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

final class VaultCrypto {
    static final int HEADER = 72;
    private static final long MAGIC = 0x4A41535052564C54L;
    private static final SecureRandom RANDOM = new SecureRandom();
    record Header(UUID id, byte[] salt) {}
    static Header fresh() { byte[] salt = new byte[16]; RANDOM.nextBytes(salt); return new Header(UUID.randomUUID(), salt); }
    static Header header(byte[] file) throws IOException {
        if (file.length < HEADER + 16 || file.length > VaultFiles.MAX_FILE) throw new IOException("Invalid vault size");
        ByteBuffer b = ByteBuffer.wrap(file);
        if (b.getLong() != MAGIC || b.getInt() != 1) throw new IOException("Unsupported vault format");
        UUID id = new UUID(b.getLong(), b.getLong());
        if (b.getInt() != 65536 || b.getInt() != 3 || b.getInt() != 4) throw new IOException("Unsupported vault KDF");
        byte[] salt = new byte[16]; b.get(salt); b.position(b.position() + 12);
        if (b.getInt() != file.length - HEADER) throw new IOException("Invalid ciphertext length");
        return new Header(id, salt);
    }
    static byte[] derive(char[] password, byte[] salt) throws IOException {
        if (password.length < 1 || password.length > VaultData.MAX_SECRET || salt.length != 16)
            throw new IOException("Invalid master password length");
        ByteBuffer encoded;
        try { encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password)); }
        catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IOException("Invalid master password characters");
        }
        byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(65536)
                .withIterations(3).withParallelism(4).withSalt(salt).build();
        try {
            var generator = new Argon2BytesGenerator(); generator.init(parameters);
            byte[] key = new byte[32]; generator.generateBytes(bytes, key); return key;
        } finally { Arrays.fill(bytes, (byte) 0); parameters.clear(); }
    }
    static byte[] encrypt(Header h, byte[] key, VaultData data) throws IOException {
        if (key == null || key.length != 32) throw new IOException("Invalid vault encryption key");
        byte[] plain = data.encode();
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            ByteBuffer out = ByteBuffer.allocate(HEADER + plain.length + 16);
            out.putLong(MAGIC).putInt(1).putLong(h.id().getMostSignificantBits()).putLong(h.id().getLeastSignificantBits());
            out.putInt(65536).putInt(3).putInt(4).put(h.salt()).put(nonce).putInt(plain.length + 16);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            c.updateAAD(out.array(), 0, HEADER); out.put(c.doFinal(plain)); return out.array();
        } catch (GeneralSecurityException e) { throw new IOException("Vault encryption failed", e); }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    static VaultData decrypt(byte[] file, byte[] key) throws IOException {
        if (key == null || key.length != 32) throw new IOException("Invalid vault encryption key");
        header(file);
        byte[] plain;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, file, 56, 12));
            c.updateAAD(file, 0, HEADER); plain = c.doFinal(file, HEADER, file.length - HEADER);
        } catch (GeneralSecurityException e) { throw new IOException("Password incorrect or vault authentication failed"); }
        try { return VaultData.decode(plain); } finally { Arrays.fill(plain, (byte) 0); }
    }
}
