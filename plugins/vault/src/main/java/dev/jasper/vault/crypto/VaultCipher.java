package dev.jasper.vault.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM over the plaintext with the file header as additional authenticated data. */
public final class VaultCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAG_BITS = 128;

    private VaultCipher() { }

    public static byte[] randomSalt() { byte[] salt = new byte[KeyDerivation.SALT_LENGTH]; RANDOM.nextBytes(salt); return salt; }

    /** A complete file with a fresh nonce. Zeroes nothing: the caller owns {@code key} and {@code plaintext}. */
    public static byte[] seal(byte[] key, byte[] salt, boolean bound, byte[] plaintext) {
        byte[] nonce = new byte[VaultFileFormat.NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        var header = new VaultFileFormat.Header(bound, salt, nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(header.encode());
            return VaultFileFormat.assemble(header, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM is unavailable", failure);
        }
    }

    /** The plaintext, which the caller zeroes; {@link WrongPasswordException} when the tag fails. */
    public static byte[] open(byte[] key, VaultFileFormat.Parsed parsed) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, parsed.header().nonce()));
            cipher.updateAAD(parsed.header().encode());
            return cipher.doFinal(parsed.ciphertext());
        } catch (AEADBadTagException failure) {
            throw new WrongPasswordException();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM is unavailable", failure);
        }
    }
}
