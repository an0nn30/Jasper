package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.awt.Component;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalContextMenuTest {
    private final PipedOutputStream remote = new PipedOutputStream();

    @AfterEach void close() throws Exception { closeOwners(); remote.close(); }

    @Test void clearTerminalFollowsFindAndIsOffWhileAProgramHasTheAlternateScreen() throws Exception {
        var output = new PipedInputStream(remote, 1 << 16);
        TerminalSession session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (columns, rows) -> { }, new CompletableFuture<>(), () -> { try { output.close(); } catch (IOException ignored) { } }),
            new GridSize(80, 24), 100);
        var cleared = new AtomicBoolean();
        session.addListener(new TerminalSessionListener() {
            @Override public void scrollbackReset() { cleared.set(true); }
        });
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(new ShellLauncher(pending::add, directory -> session, "sh")));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        edt(() -> {
            JPopupMenu menu = owner[0].chrome().contextMenu();
            assertThat(labels(menu)).containsExactly("Copy", "Paste", "Find", "Clear Terminal", "Split",
                "Move Pane to New Tab", "Move Pane to Tab", "Maximize Pane", "Close Pane", "---", "Move Tab to New Window");
            assertThat(item(menu, "Clear Terminal").isEnabled()).isTrue();
        });

        write("\033[?1049h");
        until(session::usingAlternateBuffer);
        edt(() -> assertThat(item(owner[0].chrome().contextMenu(), "Clear Terminal").isEnabled())
            .as("a full-screen program owns the screen").isFalse());
        write("\033[?1049l");
        until(() -> !session.usingAlternateBuffer());
        edt(() -> item(owner[0].chrome().contextMenu(), "Clear Terminal").doClick(0));

        assertThat(cleared).as("the item cleared the terminal").isTrue();
    }

    private void write(String text) throws IOException {
        remote.write(text.getBytes(StandardCharsets.UTF_8));
        remote.flush();
    }

    private static List<String> labels(JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (Component child : menu.getComponents()) labels.add(child instanceof JMenuItem item ? item.getText() : "---");
        return labels;
    }

    private static JMenuItem item(JPopupMenu menu, String label) {
        for (Component child : menu.getComponents())
            if (child instanceof JMenuItem item && item.getText().equals(label)) return item;
        throw new AssertionError("no " + label + " in " + labels(menu));
    }
}
