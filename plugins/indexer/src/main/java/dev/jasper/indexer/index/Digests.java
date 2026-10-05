package dev.jasper.indexer.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 for file names and row ids, which have length limits that paths do not. */
public final class Digests {
    private Digests() { }

    /**
     * @param text any text
     * @return its UTF-8 SHA-256 in lower-case hex
     */
    public static String sha256Hex(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("The JDK has no SHA-256", missing); }
    }
}
