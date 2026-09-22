package dev.jasper.vault.ui;

import dev.jasper.vault.model.SshKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;

/** Public metadata only: imported private-key bytes never enter the vault plugin. */
final class KeyFiles {
    private KeyFiles() { }
    static SshKey inspect(SshKey selected) throws IOException {
        if (!Files.isRegularFile(selected.privatePath()) || !Files.isReadable(selected.privatePath()))
            throw new IOException("Private key file is missing or unreadable: " + selected.privatePath());
        byte[] bytes;
        try (var in = Files.newInputStream(selected.publicPath())) { bytes = in.readNBytes(65_537); }
        if (bytes.length > 65_536) throw new IOException("Public key file is too large");
        String[] fields = new String(bytes, StandardCharsets.US_ASCII).strip().split("\\s+", 3);
        if (fields.length < 2) throw new IOException("Expected an OpenSSH public key");
        try {
            byte[] blob = Base64.getDecoder().decode(fields[1]);
            var parsed = OpenSSHPublicKeyUtil.parsePublicKey(blob);
            byte[] canonical = OpenSSHPublicKeyUtil.encodePublicKey(parsed);
            if (!java.util.Arrays.equals(blob, canonical)) throw new IllegalArgumentException("Noncanonical public key");
            int typeLength = java.nio.ByteBuffer.wrap(blob).getInt();
            if (typeLength < 1 || typeLength > blob.length - 4
                || !fields[0].equals(new String(blob, 4, typeLength, StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Public key type does not match its data");
            String fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob));
            return new SshKey(selected.id(), selected.name(), fields[0], fingerprint, selected.comment(),
                selected.privatePath(), selected.publicPath(), selected.created());
        } catch (IllegalArgumentException | NoSuchAlgorithmException failure) { throw new IOException("Invalid OpenSSH public key", failure); }
    }
}
