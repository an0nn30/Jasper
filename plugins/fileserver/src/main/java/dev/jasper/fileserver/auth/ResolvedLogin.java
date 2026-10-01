package dev.jasper.fileserver.auth;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** The credentials one run of a server accepts. Thread-safe; closing zeroes the password. */
public final class ResolvedLogin implements AutoCloseable {
    private static final ResolvedLogin ANONYMOUS = new ResolvedLogin(null, null);
    private final String user;
    private final char[] password;
    private boolean closed;

    private ResolvedLogin(String user, char[] password) { this.user = user; this.password = password; }

    public static ResolvedLogin anonymous() { return ANONYMOUS; }

    public static ResolvedLogin password(String user, char[] password) {
        if (user == null || user.isBlank()) throw new IllegalArgumentException("A login needs a user name");
        Objects.requireNonNull(password, "password");
        return new ResolvedLogin(user, password.clone());
    }

    public boolean isAnonymous() { return user == null; }
    public Optional<String> user() { return Optional.ofNullable(user); }

    /** Compares both parts in constant time. */
    public synchronized boolean matches(String candidateUser, char[] candidatePassword) {
        if (user == null || closed || candidateUser == null || candidatePassword == null) return false;
        boolean sameUser = MessageDigest.isEqual(user.getBytes(StandardCharsets.UTF_8), candidateUser.getBytes(StandardCharsets.UTF_8));
        boolean samePassword = MessageDigest.isEqual(bytes(password), bytes(candidatePassword));
        return sameUser & samePassword;
    }

    public synchronized Optional<String> revealPassword() {
        return password == null || closed ? Optional.empty() : Optional.of(new String(password));
    }

    @Override public synchronized void close() {
        if (password == null || closed) return;
        closed = true;
        Arrays.fill(password, '\0');
    }

    private static byte[] bytes(char[] chars) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] out = new byte[encoded.remaining()];
        encoded.get(out);
        return out;
    }
}
