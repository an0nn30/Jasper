package dev.jasper.vault.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * {@code "JASPERVLT" | version u16 LE | flags u8 | salt 16 | nonce 12 | ciphertext}. The header bytes are the
 * cipher's additional authenticated data, so the flags cannot be changed without the key.
 */
public final class VaultFileFormat {
    static final byte[] MAGIC = "JASPERVLT".getBytes(StandardCharsets.US_ASCII);
    static final int VERSION = 1;
    public static final int NONCE_LENGTH = 12;
    static final int FLAG_BOUND = 1;
    public static final int HEADER_LENGTH = MAGIC.length + 2 + 1 + KeyDerivation.SALT_LENGTH + NONCE_LENGTH;

    private VaultFileFormat() { }

    /** The authenticated header; {@code bound} means the key includes the device secret. */
    public record Header(boolean bound, byte[] salt, byte[] nonce) {
        public Header {
            if (salt.length != KeyDerivation.SALT_LENGTH) throw new IllegalArgumentException("The salt must be " + KeyDerivation.SALT_LENGTH + " bytes");
            if (nonce.length != NONCE_LENGTH) throw new IllegalArgumentException("The nonce must be " + NONCE_LENGTH + " bytes");
            salt = salt.clone(); nonce = nonce.clone();
        }
        public byte[] encode() {
            ByteBuffer out = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
            out.put(MAGIC).putShort((short) VERSION).put((byte) (bound ? FLAG_BOUND : 0)).put(salt).put(nonce);
            return out.array();
        }
        @Override public boolean equals(Object other) {
            return other instanceof Header header && bound == header.bound && Arrays.equals(salt, header.salt) && Arrays.equals(nonce, header.nonce);
        }
        @Override public int hashCode() { return 31 * (31 * Boolean.hashCode(bound) + Arrays.hashCode(salt)) + Arrays.hashCode(nonce); }
        @Override public String toString() { return "Header[bound=" + bound + "]"; }
    }

    /** A parsed file: header plus ciphertext (tag included). */
    public record Parsed(Header header, byte[] ciphertext) { }

    public static byte[] assemble(Header header, byte[] ciphertext) {
        byte[] head = header.encode();
        byte[] out = Arrays.copyOf(head, head.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, out, head.length, ciphertext.length);
        return out;
    }

    public static Parsed parse(byte[] file) {
        if (file.length < HEADER_LENGTH) throw new CorruptVaultException("The vault file is truncated");
        if (!Arrays.equals(file, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) throw new CorruptVaultException("The file is not a Jasper vault");
        ByteBuffer in = ByteBuffer.wrap(file, MAGIC.length, HEADER_LENGTH - MAGIC.length).order(ByteOrder.LITTLE_ENDIAN);
        int version = Short.toUnsignedInt(in.getShort());
        if (version != VERSION) throw new CorruptVaultException("The vault file is version " + version + "; this plugin reads version " + VERSION);
        int flags = Byte.toUnsignedInt(in.get());
        byte[] salt = new byte[KeyDerivation.SALT_LENGTH], nonce = new byte[NONCE_LENGTH];
        in.get(salt).get(nonce);
        return new Parsed(new Header((flags & FLAG_BOUND) != 0, salt, nonce), Arrays.copyOfRange(file, HEADER_LENGTH, file.length));
    }
}
