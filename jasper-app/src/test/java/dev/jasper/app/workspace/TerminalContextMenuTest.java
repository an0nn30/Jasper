package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    /**
     * Browser plan 3b Task 6: the menu reads the right-clicked pane's selection as it is built, so a web-address item shows
     * only while the selection is one web address, and choosing it carries the address; a plain item of the same section
     * carries none.
     */
    @Test void aWebAddressItemShowsOnlyWhileThePanesSelectionIsOneWebAddress() throws Exception {
        var output = new PipedInputStream(remote, 1 << 16);
        TerminalSession session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(),
            (columns, rows) -> { }, new CompletableFuture<>(), () -> { try { output.close(); } catch (IOException ignored) { } }),
            new GridSize(80, 24), 100);
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        List<Contributions.Invocation> seen = Collections.synchronizedList(new ArrayList<>());
        edt(() -> {
            owner[0] = content(new ShellLauncher(pending::add, directory -> session, "sh"));
            var model = new Contributions();
            owner[0].connectContributions(model);
            model.addAction("dev.x.split", "Open URL in Split Right", null, List.of(), Optional.empty(), seen::add);
            model.addAction("dev.x.plain", "Plain Tool", null, List.of(), Optional.empty(), seen::add);
            model.addMenuSection(MenuTarget.terminalContext())
                .set(List.of(new MenuEntry.Item("dev.x.plain"), new MenuEntry.WebAddressItem("dev.x.split")));
        });
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        edt(() -> assertThat(labels(owner[0].chrome().contextMenu())).as("nothing selected").endsWith("---", "Plain Tool"));

        write("see https://example.com/x now\033]0;shown\007");
        until(() -> owner[0].currentPane().session().title().equals("shown"));
        doubleClick(owner[0].currentPane(), 6, 0);
        edt(() -> {
            JPopupMenu menu = owner[0].chrome().contextMenu();
            assertThat(labels(menu)).endsWith("---", "Plain Tool", "Open URL in Split Right");
            item(menu, "Open URL in Split Right").doClick(0);
            item(owner[0].chrome().contextMenu(), "Plain Tool").doClick(0);
        });
        assertThat(seen).extracting(Contributions.Invocation::webAddress)
            .containsExactly(Optional.of(URI.create("https://example.com/x")), Optional.empty());
        assertThat(seen).extracting(Contributions.Invocation::paneId).containsOnly(Optional.of(owner[0].currentPane().id()));

        doubleClick(owner[0].currentPane(), 1, 0);
        edt(() -> assertThat(labels(owner[0].chrome().contextMenu())).as("\"see\" is no web address").endsWith("---", "Plain Tool"));
    }

    /** A double-click on a cell, which selects the word there (a URL is one word), through the view's own mouse listener. */
    private static void doubleClick(TerminalPane pane, int column, int row) throws Exception {
        edt(() -> {
            var view = pane.view();
            int x = column * (view.getPreferredSize().width / pane.session().columns()) + 1;
            int y = row * (view.getPreferredSize().height / pane.session().rows()) + 1;
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y, 2, false, MouseEvent.BUTTON1));
            view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 2, false, MouseEvent.BUTTON1));
        });
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
