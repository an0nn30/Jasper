package dev.jasper.remote.client;

import dev.jasper.remote.sftp.SftpFixture;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ConnectionsHostLeaseTest {
    @Test void aHostIdBorrowsAnAuthenticatedSessionWithoutConnecting(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            UUID id = fixture.host.id();
            assertThat(fixture.onUi(() -> fixture.connections.existingLease(id))).as("nothing open yet").isEmpty();
            assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).isFalse();
            try (SessionLease first = fixture.lease()) {
                assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).isTrue();
                var borrowed = fixture.onUi(() -> fixture.connections.existingLease(id)).orElseThrow();
                assertThat(borrowed.session()).isSameAs(first.session());
                assertThat(borrowed.host().id()).isEqualTo(id);
                borrowed.close();
                assertThat(fixture.onUi(() -> fixture.connections.existingLease(UUID.randomUUID()))).as("another host").isEmpty();
            }
        }
    }

    @Test void aDedicatedShellSessionCanBeBorrowed(@TempDir Path dir) throws Exception {
        try (var fixture = new SftpFixture(dir)) {
            UUID id = fixture.host.id();
            var shell = fixture.onUi(() -> fixture.connections.shell(id, null, 80, 24, status -> { }, true))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
            var borrowed = fixture.onUi(() -> fixture.connections.existingLease(id));
            assertThat(borrowed).isPresent();
            borrowed.orElseThrow().close();
            assertThat(fixture.onUi(() -> fixture.connections.authenticated(id))).as("the shell still holds it").isTrue();
            shell.connection().close().run();
        }
    }
}
