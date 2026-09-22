package dev.jasper.vault.store;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * The device secret: read from the keychain, else from the file; created in the keychain when the
 * tool works, else in the file. {@link #source()} says which one answered last.
 */
public final class DeviceSecrets {
    public static final int SECRET_LENGTH = 32;
    private final KeychainStore keychain;
    private final FileStore file;
    private String source = "keychain";

    public DeviceSecrets(KeychainStore keychain, FileStore file) { this.keychain = keychain; this.file = file; }

    /** The secret if one exists in either store. The caller zeroes it. */
    public Optional<byte[]> existing() throws IOException {
        Optional<byte[]> fromKeychain;
        try { fromKeychain = keychain.read(); source = keychain.description(); }
        catch (IOException unavailable) { fromKeychain = Optional.empty(); source = file.description(); }
        if (fromKeychain.isPresent()) return fromKeychain;
        Optional<byte[]> fromFile = file.read();
        if (fromFile.isPresent()) source = file.description();
        return fromFile;
    }

    /** The secret, created on first use. The caller zeroes it. */
    public byte[] getOrCreate() throws IOException {
        Optional<byte[]> present = existing();
        if (present.isPresent()) return present.get();
        byte[] secret = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(secret);
        try { keychain.write(secret); source = keychain.description(); }
        catch (IOException unavailable) { file.write(secret); source = file.description(); }
        return secret;
    }

    public String source() { return source; }
}
