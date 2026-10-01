package dev.jasper.fileserver.engine.tls;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.engine.EngineFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class TlsMaterialTest {
    @TempDir Path data;
    final TestClock clock = new TestClock();

    TlsMaterial material() { return new TlsMaterial(data.resolve("tls"), clock, () -> "studio.example"); }

    @Test void createsTheCertificateOnFirstUseAndReloadsIt() throws Exception {
        var tls = material();
        assertThat(tls.fingerprint()).as("nothing yet, and asking does not create one").isEmpty();
        assertThat(tls.cachedFingerprint()).isEmpty();
        assertThat(data.resolve("tls")).doesNotExist();
        assertThat(tls.context().getProtocol()).isEqualTo("TLS");
        X509Certificate certificate = tls.certificate();
        assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=Jasper File Server");
        assertThat(certificate.getPublicKey().getAlgorithm()).isEqualTo("EC");
        assertThat(certificate.getNotAfter().toInstant()).isAfter(clock.instant().plus(Duration.ofDays(3649)));
        assertThat(certificate.getSubjectAlternativeNames()).extracting(name -> (String) name.get(1))
            .containsExactlyInAnyOrder("localhost", "127.0.0.1", "studio.example");
        String fingerprint = tls.cachedFingerprint().orElseThrow();
        assertThat(fingerprint).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}").isEqualTo(TlsMaterial.fingerprint(certificate));
        assertThat(data.resolve("tls/server.p12")).exists();
        assertThat(data.resolve("tls/password")).exists();
        var again = material();
        assertThat(again.fingerprint()).as("the same certificate is read back").contains(fingerprint);
        assertThat(again.certificate()).isEqualTo(certificate);
    }

    @Test void regeneratingMakesANewCertificate() throws Exception {
        var tls = material();
        String first = TlsMaterial.fingerprint(tls.certificate());
        tls.regenerate();
        assertThat(tls.cachedFingerprint()).isPresent().get().isNotEqualTo(first);
        assertThat(material().fingerprint()).isEqualTo(tls.cachedFingerprint());
    }

    @Test void anUnreadableCertificateSaysSoAndRegeneratingFixesIt() throws Exception {
        Files.createDirectories(data.resolve("tls"));
        Files.writeString(data.resolve("tls/server.p12"), "not a keystore");
        Files.writeString(data.resolve("tls/password"), "secret");
        var tls = material();
        assertThatThrownBy(tls::context).isInstanceOf(EngineFailure.class)
            .hasMessage("The TLS certificate in " + data.resolve("tls") + " can't be read");
        assertThatThrownBy(tls::fingerprint).isInstanceOf(EngineFailure.class);
        tls.regenerate();
        assertThat(tls.context()).isNotNull();
    }

    @Test void theKeyFilesAreOwnerOnly() throws Exception {
        if (!Files.getFileStore(data).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        material().context();
        for (String file : List.of("server.p12", "password"))
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data.resolve("tls").resolve(file)))).as(file).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data.resolve("tls")))).isEqualTo("rwx------");
    }
}
