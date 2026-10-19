package dev.jasper.sdk.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionValuesTest {
    /** Workspace tabs plan 3b: a session's tab stays, or closes as {@code terminal.on_exit = "close"} closes a shell's. */
    @Test void aSessionKeepsItsTabOpenOrClosesIt() {
        assertThat(ExitPolicy.values()).containsExactly(ExitPolicy.KEEP_OPEN, ExitPolicy.CLOSE);
        assertThat(new SessionSpec("build-host", Optional.empty(), ExitPolicy.CLOSE, pending -> { }).onExit()).isEqualTo(ExitPolicy.CLOSE);
    }

    @Test void aSessionSpecDefaultsToKeepingThePaneOpen() {
        SessionSpec spec = SessionSpec.of("build-host", pending -> { });
        assertThat(spec.onExit()).isEqualTo(ExitPolicy.KEEP_OPEN);
        assertThat(spec.icon()).isEmpty();
        assertThat(OpenRequest.session(spec)).isEqualTo(new OpenRequest.Session(spec));
        assertThatIllegalArgumentException().isThrownBy(() -> SessionSpec.of(" ", pending -> { }));
        assertThatNullPointerException().isThrownBy(() -> new SessionSpec("t", Optional.empty(), null, pending -> { }));
    }

    @Test void aConnectionNeedsEveryPart() {
        assertThat(TerminalConnection.TERM).isEqualTo("xterm-256color");
        assertThatNullPointerException().isThrownBy(() -> new TerminalConnection(InputStream.nullInputStream(), OutputStream.nullOutputStream(),
            (columns, rows) -> { }, new CompletableFuture<>(), null));
    }
}
