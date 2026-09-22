package dev.jasper.vault.keygen;

import dev.jasper.vault.crypto.SecureBytes;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

/** OpenSSH PROTOCOL.key framing; bcrypt_pbkdf and AES-256-CTR are supplied by BouncyCastle. */
final class EncryptedOpenSsh {
    private static final int ROUNDS = 24; // OpenSSH's default bcrypt_pbkdf work factor.
    private EncryptedOpenSsh() { }

    /** Borrows the passphrase. The caller owns the returned encrypted blob. */
    static byte[] encode(AsymmetricCipherKeyPair pair, KeyAlgorithm algorithm, String comment,
                         char[] passphrase, SecureRandom random) throws java.io.IOException {
        byte[] password = SecureBytes.utf8(passphrase), salt = new byte[16];
        byte[] derived = null, plain = null;
        KeyParameter key = null;
        try (var payload = new Buffer(); var options = new Buffer(); var output = new Buffer()) {
            random.nextBytes(salt);
            int check = random.nextInt(); payload.u32(check); payload.u32(check);
            payload.text(algorithm.sshType());
            switch (pair.getPrivate()) {
                case Ed25519PrivateKeyParameters ed -> {
                    byte[] publicKey = ((Ed25519PublicKeyParameters) pair.getPublic()).getEncoded();
                    byte[] seed = ed.getEncoded(), privateKey = SecureBytes.concat(seed, publicKey);
                    try { payload.string(publicKey); payload.string(privateKey); }
                    finally { SecureBytes.zero(seed); SecureBytes.zero(privateKey); }
                }
                case ECPrivateKeyParameters ec -> {
                    payload.text(algorithm == KeyAlgorithm.ECDSA_P256 ? "nistp256" : "nistp384");
                    payload.string(((ECPublicKeyParameters) pair.getPublic()).getQ().getEncoded(false));
                    payload.mpint(ec.getD());
                }
                case RSAPrivateCrtKeyParameters rsa -> {
                    payload.mpint(rsa.getModulus()); payload.mpint(rsa.getPublicExponent());
                    payload.mpint(rsa.getExponent()); payload.mpint(rsa.getQInv());
                    payload.mpint(rsa.getP()); payload.mpint(rsa.getQ());
                }
                default -> throw new IllegalArgumentException("Unsupported SSH key algorithm");
            }
            payload.text(comment == null ? "" : comment.strip());
            for (int pad = 1; payload.size % 16 != 0; pad++) payload.raw(new byte[] {(byte) pad});
            plain = payload.bytes();
            derived = BCrypt.pbkdfGenerate(password, salt, ROUNDS, 48);
            key = new KeyParameter(derived, 0, 32);
            var cipher = SICBlockCipher.newInstance(AESEngine.newInstance());
            cipher.init(true, new ParametersWithIV(key, derived, 32, 16));
            byte[] encrypted = new byte[plain.length];
            cipher.processBytes(plain, 0, plain.length, encrypted, 0);
            options.string(salt); options.u32(ROUNDS);
            output.raw("openssh-key-v1\0".getBytes(StandardCharsets.US_ASCII));
            output.text("aes256-ctr"); output.text("bcrypt"); output.string(options.bytes());
            output.u32(1); output.string(OpenSSHPublicKeyUtil.encodePublicKey(pair.getPublic()));
            output.string(encrypted);
            return output.bytes();
        } finally {
            SecureBytes.zero(password); SecureBytes.zero(derived); SecureBytes.zero(plain);
            if (key != null) SecureBytes.zero(key.getKey());
        }
    }

    /** Length-prefixed SSH fields, wiping replaced buffers as well as the final backing storage. */
    private static final class Buffer implements AutoCloseable {
        private byte[] data = new byte[8192];
        private int size;
        void raw(byte[] value) {
            if (size + value.length > data.length) {
                byte[] old = data;
                data = Arrays.copyOf(old, Math.max(size + value.length, old.length * 2));
                SecureBytes.zero(old);
            }
            System.arraycopy(value, 0, data, size, value.length); size += value.length;
        }
        void u32(int value) { raw(new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value}); }
        void string(byte[] value) { u32(value.length); raw(value); }
        void text(String value) { string(value.getBytes(StandardCharsets.UTF_8)); }
        void mpint(BigInteger value) {
            byte[] bytes = value.toByteArray();
            try { string(bytes); } finally { SecureBytes.zero(bytes); }
        }
        byte[] bytes() { return Arrays.copyOf(data, size); }
        @Override public void close() { SecureBytes.zero(data); size = 0; }
    }
}
