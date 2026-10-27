package dev.jasper.app.workspace;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalLink;
import dev.jasper.terminal.view.TerminalView;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * A Cmd-click (Ctrl-click elsewhere) on a file or a URL in a pane, through the real view, to the window's
 * {@code onOpenClickedFile} or {@code onOpenLink}.
 */
class FileLinkClickTest {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final int LINK = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    /** The split click: Cmd+Shift (Ctrl+Shift elsewhere), with no program asking for mouse reports. */
    private static final int SPLIT = LINK | InputEvent.SHIFT_DOWN_MASK;

    @TempDir Path directory;
    private final PipedOutputStream remote = new PipedOutputStream();
    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    private final List<String> chosen = Collections.synchronizedList(new ArrayList<>());
    private final List<LinkClick> clicks = Collections.synchronizedList(new ArrayList<>());
    private final List<WindowContent> windows = new ArrayList<>();
    private int writes;

    @AfterEach void close() throws Exception {
        edt(() -> windows.forEach(WindowContent::close));
        remote.close();
    }

    /** Review Focus 1, the window's half: compiler output in a local pane names the file under the pane's directory, at its line. */
    @Test void compilerOutputInALocalPaneOpensTheFileAtItsLine() throws Exception {
        TerminalPane pane = localPane(attached());
        Path log = directory.resolve("logs").resolve("x.log");
        write(pane, "src/Foo.java:42:5: error: cannot find symbol\r\nFoo.cs(7,3): error CS1002: ; expected\r\n"
            + "see ~/notes.txt.\r\n" + log + ":12\r\n");

        click(pane, 2, 0);
        click(pane, 2, 1);
        click(pane, 6, 2);
        click(pane, 2, 3);
        click(pane, 22, 0);

        assertThat(opened).as("a pane that reported no directory resolves against the one it started in").containsExactly(
            directory.resolve("src").resolve("Foo.java") + ":42", directory.resolve("Foo.cs") + ":7",
            Path.of(System.getProperty("user.home")).resolve("notes.txt") + ":0", log + ":12");
    }

    /**
     * Browser plan 3b (R8): a clicked path goes to the window's {@code onOpenClickedFile}, where the application offers a
     * web file to the browser first; {@code onOpenFile}, which File › Open File… uses, never hears a click.
     */
    @Test void aClickedPathGoesToTheTerminalClickOpenerAndNeverToOpenFilesOwn() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "docs/index.html:7\r\nsrc/Foo.java:3\r\n");

        click(pane, 2, 0);
        click(pane, 2, 1);

        assertThat(opened).containsExactly(directory.resolve("docs").resolve("index.html") + ":7",
            directory.resolve("src").resolve("Foo.java") + ":3");
        assertThat(chosen).as("the opener of File › Open File…").isEmpty();
    }

    /** Review Focus 3: once the shell reports a remote directory, nothing opens, not even a same-named local file. */
    @Test void aPaneWhoseShellReportsARemoteDirectoryOpensNothing() throws Exception {
        Path file = Files.createDirectories(directory.resolve("src")).resolve("Foo.java");
        Files.writeString(file, "class Foo { }\n");
        TerminalPane pane = localPane(attached());
        write(pane, "src/Foo.java:3\r\n" + file + "\r\n");
        click(pane, 2, 0);
        assertThat(opened).as("before any report").containsExactly(file + ":3");

        write(pane, "\033]7;file://build-host/srv/app\007");
        until(() -> pane.snapshot().remoteDirectory().isPresent());
        click(pane, 2, 0);
        click(pane, 2, 1);

        assertThat(opened).as("neither the relative path nor the absolute one").containsExactly(file + ":3");
    }

    /**
     * Review Focus 3: a provided session, such as an ssh plugin's, opens nothing. The control: the same text, clicked at the
     * same cells in a local pane, opens, so the clicks above did land on the paths.
     */
    @Test @DisabledOnOs(OS.WINDOWS) void aProvidedSessionOpensNothing() throws Exception {
        List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = window(launcher(new ArrayDeque<>()))
            .openTab(directory, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).pane());
        until(() -> !attempts.isEmpty());
        attempts.getFirst().attach(connection());
        until(() -> pane[0].view() != null);
        write(pane[0], directory.resolve("notes.txt") + "\r\n./notes.txt\r\n");

        click(pane[0], 2, 0);
        click(pane[0], 2, 1);

        assertThat(opened).isEmpty();

        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher printing = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
                    "printf '%s\\r\\n./notes.txt\\r\\n\\033]0;ready\\007' \"$1\"; read answer", "sh",
                    directory.resolve("notes.txt").toString()))
                    .environment(System.getenv()).workingDirectory(path).grid(new GridSize(200, 24)).scrollback(100).build());
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(printing));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null && owner[0].currentPane().session().title().equals("ready"));
        TerminalPane[] local = new TerminalPane[1];
        edt(() -> local[0] = owner[0].currentPane());

        click(local[0], 2, 0);
        click(local[0], 2, 1);

        assertThat(opened).as("the same cells in a local pane").containsExactly(
            directory.resolve("notes.txt") + ":0", directory.resolve("notes.txt") + ":0");
    }

    /** The directory a local shell reported comes before the one the pane started in, and its file: links open too. */
    @Test @DisabledOnOs(OS.WINDOWS) void aLocalShellsReportedDirectoryResolvesItsPaths() throws Exception {
        Path reported = Files.createDirectories(directory.resolve("reported"));
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
                    "printf '\\033]7;file://localhost%s\\007src/Foo.java:9\\r\\n\\033]8;;file://localhost%s/b.txt\\007link\\033]8;;\\007"
                        + "\\r\\n\\033]0;ready\\007' \"$1\" \"$1\"; read answer", "sh", reported.toString()))
                    .environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(launcher));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null && owner[0].currentPane().session().title().equals("ready")
            && owner[0].currentPane().session().workingDirectory().isPresent());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner[0].currentPane());

        click(pane[0], 2, 0);
        click(pane[0], 1, 1);

        assertThat(opened).containsExactly(reported.resolve("src").resolve("Foo.java") + ":9", reported.resolve("b.txt") + ":0");
    }

    /** A WSL profile's pane has this machine's WSL shares, so a path on one opens. */
    @Test @EnabledOnOs(OS.WINDOWS) void aWslSharePathOpensInAWslPane() throws Exception {
        var ubuntu = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.empty(), List.of("wsl.exe", "-d", "Ubuntu"));
        TerminalPane pane = localPane(ubuntu, attached());
        write(pane, "\\\\wsl.localhost\\Ubuntu\\notes.txt\r\n");

        click(pane, 2, 0);

        assertThat(opened).singleElement(STRING).endsWith("wsl.localhost\\Ubuntu\\notes.txt:0");
    }

    /**
     * Any other pane has no WSL share: without WSL, {@code wsl.localhost} is a name the network may answer, so a path on it
     * opens nothing, as any other host's. The control on the next row opens.
     */
    @Test @EnabledOnOs(OS.WINDOWS) void aWslSharePathOpensNothingInAnotherProfilesPane() throws Exception {
        var powershell = new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe"));
        TerminalPane pane = localPane(powershell, attached());
        write(pane, "\\\\wsl.localhost\\Ubuntu\\notes.txt\r\n" + directory.resolve("notes.txt") + "\r\n");

        click(pane, 2, 0);
        click(pane, 2, 1);

        assertThat(opened).containsExactly(directory.resolve("notes.txt") + ":0");
    }

    /**
     * Browser plan 3, Review Focus 1, the window's half: a URL under the pointer, plain text or OSC 8, goes to the window's
     * {@code onOpenLink} as the terminal read it, and a path still goes to {@code onOpenClickedFile}.
     */
    @Test void aClickedUrlGoesToTheWindowsLinkOpenerAsWritten() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "see https://example.com/a?b=1 now\r\nftp://files.example.com/x\r\n"
            + "\033]8;;mailto:someone@example.com\007mail me\033]8;;\007\r\nsrc/Foo.java:3\r\n");

        click(pane, 6, 0);
        click(pane, 3, 1);
        click(pane, 2, 2);
        click(pane, 2, 3);

        assertThat(clicks).extracting(click -> click.link().url())
            .containsExactly("https://example.com/a?b=1", "ftp://files.example.com/x", "mailto:someone@example.com");
        assertThat(clicks).extracting(click -> click.link().gesture()).as("Cmd-clicks (Ctrl-clicks elsewhere)").containsOnly(TerminalLink.Gesture.OPEN);
        assertThat(opened).containsExactly(directory.resolve("src").resolve("Foo.java") + ":3");
    }

    /** Browser plan 3: unlike a file path, a URL in a provided session's pane goes to the window too. */
    @Test @DisabledOnOs(OS.WINDOWS) void aUrlInAProvidedSessionGoesToTheWindowToo() throws Exception {
        List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = window(launcher(new ArrayDeque<>()))
            .openTab(directory, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).pane());
        until(() -> !attempts.isEmpty());
        attempts.getFirst().attach(connection());
        until(() -> pane[0].view() != null);
        write(pane[0], "see https://example.com/remote\r\n");

        click(pane[0], 6, 0);

        assertThat(clicks).extracting(click -> click.link().url()).containsExactly("https://example.com/remote");
    }

    /**
     * Browser plan 3b: a split click (Cmd+Shift-click, Ctrl+Shift-click elsewhere) in a pane wider than it is tall reaches
     * the window as a click in a wide pane, which the application opens in a split to the right
     * ({@code JasperApplicationPluginsTest.aTerminalClickReachesTheBrowserAsATabOrASplitByItsPanesShape}).
     */
    @Test void aSplitClickInAWidePaneIsSplitRight() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "see https://example.com/wide now\r\n");
        edt(() -> pane.setSize(800, 300));

        click(pane, 6, 0, SPLIT);

        assertThat(clicks).containsExactly(
            new LinkClick(new TerminalLink("https://example.com/wide", TerminalLink.Gesture.SPLIT, Optional.empty()), true));
    }

    /** As above, in a pane taller than it is wide, or as wide as it is tall: the application opens the link below it. */
    @Test void aSplitClickInATallPaneIsSplitDown() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "see https://example.com/tall now\r\n");
        edt(() -> pane.setSize(300, 800));
        click(pane, 6, 0, SPLIT);
        edt(() -> pane.setSize(500, 500));
        click(pane, 6, 0, SPLIT);

        var link = new TerminalLink("https://example.com/tall", TerminalLink.Gesture.SPLIT, Optional.empty());
        assertThat(clicks).as("a square pane is not wide").containsExactly(new LinkClick(link, false), new LinkClick(link, false));
    }

    /**
     * Browser plan 3b, Review Focus 1: a split click in a pane that is not the active one splits the pane clicked. Focus
     * reaches that pane through focus events, after the click, so the view's link opener makes it the active pane before
     * the window's {@code onOpenLink} hears the click; the active group is read there, as the application reads it. In a
     * shown window the window's press listener makes a pressed pane the active one before the view hears the press; the
     * first click leaves that listener out, so the opener's own activation is what it checks, and the second has both.
     */
    @Test void aSplitClickInAnUnfocusedPaneSplitsThatPane() throws Exception {
        Queue<TerminalSession> sessions = new ArrayDeque<>(List.of(IdleTerminalSession.start(), attached()));
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(new ShellLauncher(pending::add, path -> sessions.remove(), "sh")));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        TerminalPane[] panes = new TerminalPane[2];
        edt(() -> { panes[0] = owner[0].currentPane(); owner[0].split(Side.RIGHT); });
        pending.remove().run();
        until(() -> owner[0].currentPane() != panes[0] && owner[0].currentPane().view() != null);
        edt(() -> panes[1] = owner[0].currentPane());
        write(panes[1], "see https://example.com/second now\r\n");
        GroupId[] second = new GroupId[1];
        List<GroupId> activeAtClick = new ArrayList<>();
        edt(() -> {
            second[0] = owner[0].workspace().model().groupOf(owner[0].tabHolding(panes[1]).id()).orElseThrow();
            owner[0].focusGroup(Side.LEFT);
            assertThat(owner[0].currentPane()).as("focus on the first pane").isSameAs(panes[0]);
            owner[0].onOpenLink = click -> { clicks.add(click); activeAtClick.add(owner[0].workspace().model().activeGroup()); };
        });

        clickTheViewAlone(panes[1], 6, 0, SPLIT);
        edt(() -> owner[0].focusGroup(Side.LEFT));
        click(panes[1], 6, 0, SPLIT);

        var link = new TerminalLink("https://example.com/second", TerminalLink.Gesture.SPLIT, Optional.empty());
        assertThat(clicks).extracting(LinkClick::link).containsExactly(link, link);
        assertThat(activeAtClick).as("the clicked pane's group, as the opener ran").containsExactly(second[0], second[0]);
    }

    /**
     * Browser plan 3b: an OSC 8 link's {@code target=} name reaches the window's {@code onOpenLink} with the click, whichever
     * the gesture; a URL written in the output has none.
     */
    @Test void theTargetReachesTheWindowsOpener() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "\033]8;target=docs;https://example.com/docs\007docs\033]8;;\007 https://example.com/plain\r\n");

        click(pane, 1, 0);
        click(pane, 1, 0, SPLIT);
        click(pane, 8, 0);

        assertThat(clicks).extracting(LinkClick::link).containsExactly(
            new TerminalLink("https://example.com/docs", TerminalLink.Gesture.OPEN, Optional.of("docs")),
            new TerminalLink("https://example.com/docs", TerminalLink.Gesture.SPLIT, Optional.of("docs")),
            new TerminalLink("https://example.com/plain", TerminalLink.Gesture.OPEN, Optional.empty()));
    }

    private WindowContent window(ShellLauncher launcher) {
        WindowContent owner = new WindowContent(launcher, directory, path -> { }, () -> { }, () -> { });
        owner.onOpenClickedFile = (file, line) -> opened.add(file + ":" + line);
        owner.onOpenFile = (file, line) -> chosen.add(file + ":" + line);
        owner.onOpenLink = clicks::add;
        windows.add(owner);
        return owner;
    }

    /** A local pane (no provider) over an attached session this test writes to: it never reports a local directory. */
    private TerminalPane localPane(TerminalSession session) throws Exception {
        return localPane(pending -> new ShellLauncher(pending::add, path -> session, "sh"));
    }

    /** As above, started with {@code profile}'s launch settings, so the pane knows which profile it runs. */
    private TerminalPane localPane(ShellProfile profile, TerminalSession session) throws Exception {
        var settings = new LaunchSettings(profile.command(), Map.of(), 200, 24, 100, profile);
        return localPane(pending -> new ShellLauncher(pending::add, () -> settings, (where, started) -> session));
    }

    private TerminalPane localPane(Function<Queue<Runnable>, ShellLauncher> launcher) throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = window(launcher.apply(pending)));
        pending.remove().run();
        until(() -> owner[0].currentPane().view() != null);
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner[0].currentPane());
        return pane[0];
    }

    private TerminalSession attached() throws IOException {
        return TerminalSession.attach(connection(), new GridSize(200, 24), 100);
    }

    private AttachedConnection connection() throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        return new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, new CompletableFuture<>(),
            () -> { try { output.close(); } catch (IOException ignored) { } });
    }

    /** Writes {@code text} to the attached session and waits until the pane shows all of it. */
    private void write(TerminalPane pane, String text) throws Exception {
        String title = "shown " + ++writes;
        remote.write((text + "\033]0;" + title + "\007").getBytes(StandardCharsets.UTF_8));
        remote.flush();
        until(() -> pane.session().title().equals(title));
    }

    /** A Cmd-click (Ctrl-click elsewhere) on a cell, through the view's own mouse listener. */
    private static void click(TerminalPane pane, int column, int row) throws Exception { click(pane, column, row, LINK); }

    /** A click on a cell with {@code modifiers} held, through the view's own mouse listener. */
    private static void click(TerminalPane pane, int column, int row, int modifiers) throws Exception {
        edt(() -> { for (MouseEvent event : clickOn(pane, column, row, modifiers)) pane.view().dispatchEvent(event); });
    }

    /**
     * As {@link #click(TerminalPane, int, int, int)}, the press and release handed to the view's mouse listeners alone, so
     * no other listener, such as the window's press listener, hears them.
     */
    private static void clickTheViewAlone(TerminalPane pane, int column, int row, int modifiers) throws Exception {
        edt(() -> {
            MouseEvent[] click = clickOn(pane, column, row, modifiers);
            for (var listener : pane.view().getMouseListeners()) listener.mousePressed(click[0]);
            for (var listener : pane.view().getMouseListeners()) listener.mouseReleased(click[1]);
        });
    }

    /** The press and release of a click on a cell of {@code pane}'s view with {@code modifiers} held. EDT. */
    private static MouseEvent[] clickOn(TerminalPane pane, int column, int row, int modifiers) {
        TerminalView view = pane.view();
        int x = column * (view.getPreferredSize().width / pane.session().columns()) + 1;
        int y = row * (view.getPreferredSize().height / pane.session().rows()) + 1;
        return new MouseEvent[] {
            new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1),
            new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, modifiers, x, y, 1, false, MouseEvent.BUTTON1)};
    }
}
