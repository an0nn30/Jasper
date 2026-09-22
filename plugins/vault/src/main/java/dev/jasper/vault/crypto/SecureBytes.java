package dev.jasper.vault.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Helpers that keep secrets in arrays: UTF-8 both ways with the intermediate buffers zeroed, and zeroing. */
public final class SecureBytes {
    private SecureBytes() { }

    /** The UTF-8 bytes of {@code text}; the caller owns and zeroes the result. */
    public static byte[] utf8(char[] text) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(text));
        byte[] out = new byte[encoded.remaining()];
        encoded.get(out);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        return out;
    }

    /** The characters of UTF-8 {@code bytes}; the caller owns and zeroes the result. */
    public static char[] chars(byte[] bytes) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] out = new char[decoded.remaining()];
        decoded.get(out);
        if (decoded.hasArray()) Arrays.fill(decoded.array(), (char) 0);
        return out;
    }

    /** {@code first || second}; a null {@code second} contributes nothing. The caller zeroes the result. */
    public static byte[] concat(byte[] first, byte[] second) {
        if (second == null) return Arrays.copyOf(first, first.length);
        byte[] out = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    /** Overwrites with zeros; null is ignored. */
    public static void zero(byte[] secret) { if (secret != null) Arrays.fill(secret, (byte) 0); }

    /** Overwrites with zeros; null is ignored. */
    public static void zero(char[] secret) { if (secret != null) Arrays.fill(secret, (char) 0); }
}
