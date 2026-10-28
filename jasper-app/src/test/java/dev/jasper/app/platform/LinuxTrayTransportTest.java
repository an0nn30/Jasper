package dev.jasper.app.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@EnabledOnOs(OS.LINUX)
class LinuxTrayTransportTest {
    @ParameterizedTest(name = "private session bus, abstract={0}")
    @ValueSource(booleans = {false, true})
    void connectsToPrivateBus(boolean abstractAddress) {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            // Unix socket paths are limited; JUnit's default temporary directory can exceed that limit.
            Path directory = Files.createTempDirectory(Path.of("/tmp"), "jtb-");
            try {
                try (var bus = LinuxTestBus.start(directory, abstractAddress);
                     var connection = DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build()) {
                    assertThat(connection.getUniqueName()).startsWith(":");
                    assertThat(connection.isConnected()).isTrue();
                }
            } finally {
                Files.deleteIfExists(directory.resolve("bus"));
                Files.delete(directory);
            }
        });
    }
}
