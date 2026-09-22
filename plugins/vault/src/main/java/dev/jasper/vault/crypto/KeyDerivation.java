package dev.jasper.vault.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id with the spec's parameters: 64 MiB, 3 iterations, 4 lanes, a 32-byte key from a 16-byte salt. */
public final class KeyDerivation {
    public static final int KEY_LENGTH = 32;
    public static final int SALT_LENGTH = 16;
    static final int MEMORY_KIB = 65536;
    static final int ITERATIONS = 3;
    static final int LANES = 4;

    private KeyDerivation() { }

    /**
     * The key for {@code password || deviceSecret} ({@code deviceSecret} may be null for an unbound
     * vault). Takes about a quarter of a second; never call it on the UI thread. The caller zeroes the result.
     */
    public static byte[] derive(byte[] password, byte[] deviceSecret, byte[] salt) {
        if (salt.length != SALT_LENGTH) throw new IllegalArgumentException("The salt must be " + SALT_LENGTH + " bytes, not " + salt.length);
        byte[] material = SecureBytes.concat(password, deviceSecret);
        try {
            var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_KIB).withIterations(ITERATIONS).withParallelism(LANES)
                .withSalt(salt).build();
            var generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] key = new byte[KEY_LENGTH];
            generator.generateBytes(material, key);
            return key;
        } finally {
            SecureBytes.zero(material);
        }
    }
}
