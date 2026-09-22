package dev.jasper.vault.keygen;

import dev.jasper.vault.model.SshKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeyGeneratorTest {
    static AsymmetricKeyParameter readPrivate(Path file) throws Exception {
        String pem = Files.readString(file);
        String body = pem.lines().filter(line -> !line.startsWith("-----")).reduce("", String::concat);
        return OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(Base64.getDecoder().decode(body));
    }

    @Test void ed25519KeyIsOpenSshFormatWithMatchingPublicLineAndFingerprint(@TempDir Path dir) throws Exception {
        SshKey key = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "laptop", "me@laptop");
        assertThat(key.name()).isEqualTo("laptop");
        assertThat(key.algorithm()).isEqualTo("ed25519");
        assertThat(key.privatePath().getFileName().toString()).startsWith("id_ed25519_").hasSize("id_ed25519_".length() + 8);
        assertThat(key.publicPath()).isEqualTo(key.privatePath().resolveSibling(key.privatePath().getFileName() + ".pub"));
        assertThat(Files.readString(key.privatePath())).startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n").endsWith("-----END OPENSSH PRIVATE KEY-----\n");
        assertThat(readPrivate(key.privatePath())).isInstanceOf(Ed25519PrivateKeyParameters.class);
        String[] parts = Files.readString(key.publicPath()).strip().split(" ");
        assertThat(parts).hasSize(3).startsWith("ssh-ed25519").endsWith("me@laptop");
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        assertThat(OpenSSHPublicKeyUtil.parsePublicKey(blob)).isNotNull();
        assertThat(key.fingerprint()).isEqualTo("SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob)));
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(key.privatePath())).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            assertThat(Files.getPosixFilePermissions(dir.resolve("keys"))).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE, java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        }
        new KeyGenerator(dir.resolve("keys")).delete(key);
        assertThat(key.privatePath()).doesNotExist();
        assertThat(key.publicPath()).doesNotExist();
    }

    @Test void ecdsaAndRsaUseTheirTraditionalPemLabels(@TempDir Path dir) throws Exception {
        var generator = new KeyGenerator(dir);
        SshKey ec = generator.generate(KeyAlgorithm.ECDSA_P256, "ec", "");
        assertThat(Files.readString(ec.privatePath())).startsWith("-----BEGIN EC PRIVATE KEY-----");
        assertThat(readPrivate(ec.privatePath())).isInstanceOf(ECPrivateKeyParameters.class);
        assertThat(Files.readString(ec.publicPath())).startsWith("ecdsa-sha2-nistp256 ").doesNotEndWith(" \n");
        SshKey rsa = generator.generate(KeyAlgorithm.RSA_3072, "rsa", "c");
        assertThat(Files.readString(rsa.privatePath())).startsWith("-----BEGIN RSA PRIVATE KEY-----");
        assertThat(readPrivate(rsa.privatePath())).isInstanceOf(RSAPrivateCrtKeyParameters.class);
        assertThat(Files.readString(rsa.publicPath())).startsWith("ssh-rsa ");
        assertThat(KeyAlgorithm.ECDSA_P384.sshType()).isEqualTo("ecdsa-sha2-nistp384");
        assertThat(KeyAlgorithm.RSA_4096.label()).isEqualTo("RSA 4096");
    }

    /** With OpenSSH on the PATH, its fingerprint matches ours. */
    @Test void sshKeygenAgreesOnTheFingerprint(@TempDir Path dir) throws Exception {
        Path sshKeygen = java.util.stream.Stream.of(System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator))
            .map(entry -> Path.of(entry, "ssh-keygen")).filter(Files::isExecutable).findFirst().orElse(null);
        assumeTrue(sshKeygen != null, "ssh-keygen not on PATH");
        SshKey key = new KeyGenerator(dir).generate(KeyAlgorithm.ED25519, "k", "x");
        Process process = new ProcessBuilder(sshKeygen.toString(), "-l", "-f", key.publicPath().toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output).contains(key.fingerprint());
    }
    @org.junit.jupiter.api.Test void failedGenerationRemovesWrittenFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new KeyGenerator(dir).generate(KeyAlgorithm.ED25519, "", "test"))
            .isInstanceOf(IllegalArgumentException.class);
        try (var files = java.nio.file.Files.list(dir)) {
            org.assertj.core.api.Assertions.assertThat(files.toList()).isEmpty();
        }
    }
}
