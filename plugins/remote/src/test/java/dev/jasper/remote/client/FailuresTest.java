package dev.jasper.remote.client;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailuresTest {
    @Test
    void noRouteToHostNamesTheHostAndTheMacLocalNetworkPermission() {
        // The shape MINA's connect future reports: its own summary, the socket failure as the cause.
        var failure = new CompletionException(new IOException(
            "DefaultConnectFuture[dustin@/192.168.1.120:22]: Failed (NoRouteToHostException) to execute: No route to host",
            new NoRouteToHostException("No route to host")));
        assertThat(Failures.message(failure, Duration.ofSeconds(10), "192.168.1.120"))
            .isEqualTo("No route to host 192.168.1.120. On macOS, allow Jasper under System Settings > Privacy & Security > Local Network.");
    }
}
