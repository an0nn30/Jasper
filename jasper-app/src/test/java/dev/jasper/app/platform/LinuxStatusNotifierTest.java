package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LinuxStatusNotifierTest {
    @Test void acceptsOnlyCapturedUnixAddressesInOrder() {
        assertThat(LinuxStatusNotifier.addresses("tcp:host=localhost;unix:path=/tmp/one;unix:abstract=two", null))
            .containsExactly("unix:path=/tmp/one", "unix:abstract=two");
        assertThat(LinuxStatusNotifier.addresses(null, "relative")).isEmpty();
        assertThat(LinuxStatusNotifier.addresses("autolaunch:", "/tmp")).isEmpty();
        assertThat(LinuxStatusNotifier.addresses("unix:guid=abc,path=/tmp/bus;unix:path=/tmp/listener,listen=true", null))
            .containsExactly("unix:guid=abc,path=/tmp/bus");
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void stalledAuthenticationIsCancelledAndSocketCloses() throws Exception {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("/tmp"), "jta-");
            var path = directory.resolve("bus");
            try (var server = java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)) {
                server.bind(java.net.UnixDomainSocketAddress.of(path));
                var hosts = new java.util.concurrent.LinkedBlockingQueue<LinuxStatusNotifier.Host>();
                try (var adapter = new LinuxStatusNotifier("unix:path=" + path, hosts::add)) {
                    adapter.start();
                    try (var socket = server.accept()) {
                        assertThat(hosts.poll(3, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
                        var buffer = java.nio.ByteBuffer.allocate(4096);
                        while (socket.read(buffer) != -1) buffer.clear();
                    }
                }
            } finally { java.nio.file.Files.deleteIfExists(path); java.nio.file.Files.delete(directory); }
        });
    }
}
