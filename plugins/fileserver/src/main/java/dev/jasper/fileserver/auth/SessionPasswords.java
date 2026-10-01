package dev.jasper.fileserver.auth;

import java.security.SecureRandom;
import java.util.Random;

/** Passwords generated for one run of a server: 20 characters that cannot be misread. */
public final class SessionPasswords {
    static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionPasswords() { }

    public static String generate() { return generate(RANDOM); }

    static String generate(Random random) {
        var out = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return out.toString();
    }
}
