package dev.jasper.terminal.session;

import dev.jasper.terminal.internal.process.PtyChild;
import dev.jasper.terminal.testsupport.Await;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.core.util.TermSize;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PtySessionFactoryTest {
@Test void constructionFailureClosesTheAlreadyCreatedConnector() {
    AtomicInteger closes = new AtomicInteger();
    TtyConnector connector = new FakeConnectorForClose(closes);
    RuntimeException failure = new IllegalStateException("engine setup failed");
    assertThatThrownBy(() -> PtySessionFactory.finish(connector::close, () -> { throw failure; }))
        .isSameAs(failure);
    assertThat(closes).hasValue(1);
}

@Test void cleanupFailureIsSuppressedOnTheOriginalFailure() {
    var connector = new FakeConnectorForClose(new AtomicInteger());
    connector.failure = new IllegalArgumentException("cleanup");
    var failure = new AssertionError("startup");
    assertThatThrownBy(() -> PtySessionFactory.finish(connector::close, () -> { throw failure; }))
        .isSameAs(failure).hasSuppressedException(connector.failure);
}
@Test void environmentIsCopiedAndTerminalCapabilitiesAreEnforced() {
    var source = new java.util.HashMap<>(java.util.Map.of("TERM", "dumb", "COLORTERM", "none", "LANG", "C"));
    assertThat(PtyChild.environment(source)).containsEntry("TERM", "xterm-256color")
        .containsEntry("COLORTERM", "truecolor").containsEntry("LANG", "C");
    assertThat(source).containsEntry("TERM", "dumb").containsEntry("COLORTERM", "none");
}
private static final class FakeConnectorForClose implements TtyConnector {
    private final AtomicInteger closes;
    RuntimeException failure;
    FakeConnectorForClose(AtomicInteger closes) { this.closes = closes; }
    public int read(char[] buffer,int offset,int length) { return -1; }
    public void write(byte[] bytes) { }
    public void write(String text) { }
    public boolean isConnected() { return false; }
    public boolean ready() { return false; }
    public void resize(TermSize size) { }
    public int waitFor() { return 0; }
    public String getName() { return "closed-fixture"; }
    public void close() { closes.incrementAndGet(); if (failure != null) throw failure; }
}

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void startupFailureClosesTheNativeChild() throws Exception {
        var options = SessionLaunchOptions.builder().command(java.util.List.of("/bin/sh", "-c", "read line"))
            .environment(System.getenv()).workingDirectory(java.nio.file.Path.of(System.getProperty("user.home"))).build();
        PtyChild child = PtyChild.start(options.command(), options.environment(), options.workingDirectory(), options.grid().columns(), options.grid().rows());
        var failure = new IllegalStateException("engine setup");
        try {
            assertThatThrownBy(() -> PtySessionFactory.finish(child, () -> { throw failure; })).isSameAs(failure);
            Await.until(() -> !child.isConnected(), "startup failure closed native child");
        } finally { child.close(); }
    }
}
