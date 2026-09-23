package dev.jasper.remote.trust;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KnownHostsTest {
    static PublicKey key(String algorithm, int size) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(size);
        KeyPair pair = generator.generateKeyPair();
        return pair.getPublic();
    }

    static String entry(PublicKey key) throws Exception { return PublicKeyEntry.appendPublicKeyEntry(new StringBuilder(), key).toString(); }

    /** An OpenSSH hashed host: {@code |1|base64(salt)|base64(HMAC-SHA1(salt, pattern))}. */
    static String hashed(String pattern) throws Exception {
        byte[] salt = new byte[20];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) (i * 7);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(salt, "HmacSHA1"));
        return "|1|" + Base64.getEncoder().encodeToString(salt) + "|" + Base64.getEncoder().encodeToString(mac.doFinal(pattern.getBytes()));
    }

    @Test void matchesPlainAndHashedEntriesInEitherFile(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048), ec = key("EC", 256);
        Path own = dir.resolve("known_hosts"), user = dir.resolve("user_known_hosts");
        Files.writeString(own, "# mine\nplain.example " + entry(rsa) + "\n[odd.example]:2222 " + entry(ec) + "\n");
        Files.writeString(user, hashed("hashed.example") + " " + entry(ec) + "\nbroken line here\n");
        var trust = new KnownHosts(own, Optional.of(user));
        assertThat(trust.verify("plain.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("odd.example", 2222, ec)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("odd.example", 22, ec)).as("port is part of the pattern").isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(trust.verify("hashed.example", 22, ec)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("hashed.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Mismatch(KnownHosts.fingerprint(ec)));
        assertThat(trust.verify("plain.example", 22, ec)).isEqualTo(new KnownHosts.Verdict.Mismatch(KnownHosts.fingerprint(rsa)));
        assertThat(trust.verify("new.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(new KnownHosts(own, Optional.empty()).verify("hashed.example", 22, ec)).as("user file off").isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(new KnownHosts(dir.resolve("absent"), Optional.of(dir.resolve("also-absent"))).verify("x", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(KnownHosts.fingerprint(rsa)).startsWith("SHA256:");
    }

    @Test void revokedIsAMismatchAndACorruptOwnFileRefuses(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048);
        Path own = dir.resolve("known_hosts");
        Files.writeString(own, "@revoked gone.example " + entry(rsa) + "\n");
        var trust = new KnownHosts(own, Optional.empty());
        assertThat(trust.verify("gone.example", 22, rsa)).isInstanceOf(KnownHosts.Verdict.Mismatch.class);
        Files.writeString(own, "this is not a known_hosts line\n");
        assertThatThrownBy(() -> trust.verify("gone.example", 22, rsa)).isInstanceOf(CorruptTrustFileException.class).hasMessageContaining("known_hosts");
    }

    @Test void trustAppendsAndRefusesToOverwriteAConflict(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048), ec = key("EC", 256);
        Path own = dir.resolve("deep/known_hosts");
        var trust = new KnownHosts(own, Optional.empty());
        trust.trust("new.example", 22, rsa);
        trust.trust("new.example", 2222, ec);
        assertThat(Files.readString(own)).contains("new.example " + entry(rsa), "[new.example]:2222 " + entry(ec));
        assertThat(trust.verify("new.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Match());
        trust.trust("new.example", 22, rsa);
        assertThat(Files.readString(own).lines().filter(line -> line.startsWith("new.example ")).count()).as("idempotent").isEqualTo(1);
        assertThatThrownBy(() -> trust.trust("new.example", 22, ec)).isInstanceOf(java.io.IOException.class).hasMessageContaining("different key");
        assertThat(KnownHosts.hostPattern("h", 22)).isEqualTo("h");
        assertThat(KnownHosts.hostPattern("h", 23)).isEqualTo("[h]:23");
    }
}
