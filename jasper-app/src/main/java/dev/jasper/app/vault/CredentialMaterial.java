package dev.jasper.app.vault;
import java.util.Arrays;
public final class CredentialMaterial implements AutoCloseable {
    private final String username;
    private final char[] password;
    private final byte[] privateKey;
    private final char[] passphrase;
    private boolean closed;
    CredentialMaterial(String username, char[] password, byte[] privateKey, char[] passphrase) {
        this.username = username; this.password = password.clone();
        this.privateKey = privateKey.clone(); this.passphrase = passphrase.clone();
    }
    private void check() { if (closed) throw new IllegalStateException("Credential material closed"); }
    public String username() { check(); return username; }
    public char[] password() { check(); return password; }
    public byte[] privateKey() { check(); return privateKey; }
    public char[] passphrase() { check(); return passphrase; }
    @Override public void close() {
        Arrays.fill(password, (char) 0); Arrays.fill(privateKey, (byte) 0);
        Arrays.fill(passphrase, (char) 0); closed = true;
    }
    @Override public String toString() { return "CredentialMaterial[redacted]"; }
}
