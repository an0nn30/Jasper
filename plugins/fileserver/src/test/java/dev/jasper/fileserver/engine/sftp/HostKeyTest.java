package dev.jasper.fileserver.engine.sftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

class HostKeyTest {
    @TempDir Path data;

    @Test void createsAnEd25519KeyOnceAndReadsItBack() throws Exception {
        Path file = data.resolve("hostkey");
        var key = new HostKey(file);
        assertThat(key.fingerprint()).as("nothing yet, and asking does not create one").isEmpty();
        assertThat(file).doesNotExist();
        KeyPair pair = key.provider().loadKeys(null).iterator().next();
        assertThat(pair.getPublic().getAlgorithm()).isIn("Ed25519", "EdDSA");
        String fingerprint = key.cachedFingerprint().orElseThrow();
        assertThat(fingerprint).startsWith("SHA256:").doesNotEndWith("=");
        assertThat(file).isRegularFile();
        assertThat(Files.readString(file)).startsWith("-----BEGIN OPENSSH PRIVATE KEY-----");
        assertThat(key.provider()).as("the provider is created once").isSameAs(key.provider());
        var again = new HostKey(file);
        assertThat(again.fingerprint()).as("the same key after a restart").contains(fingerprint);
    }

    @Test void theKeyFileIsOwnerOnly() throws Exception {
        if (!Files.getFileStore(data).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        Path file = data.resolve("hostkey");
        new HostKey(file).provider();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test void aMissingFolderIsCreated() throws Exception {
        Path file = data.resolve("nested/hostkey");
        var key = new HostKey(file);
        key.provider();
        assertThat(file).isRegularFile();
        assertThat(key.cachedFingerprint()).isPresent();
    }
}
