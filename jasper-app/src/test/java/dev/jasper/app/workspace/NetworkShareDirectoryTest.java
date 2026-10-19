package dev.jasper.app.workspace;

import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * On Windows a pane passes on no directory on another host: any program can report one (OSC 7), and a split, tab or window
 * started there would make Windows authenticate to that host over SMB. A WSL profile's pane passes on its own share; any
 * other pane has none. Windows only, like the rule ({@code UncHostsTest} tests its text everywhere). The sessions are
 * attached, so no process starts, and the host ends in {@code .invalid}, which never resolves, should a regression ask.
 */
@EnabledOnOs(OS.WINDOWS)
class NetworkShareDirectoryTest {
    private static final Path SHARE = Path.of("\\\\attacker.invalid\\share\\project");
    private static final Path WSL_SHARE = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me");
    private static final ShellProfile POWERSHELL =
        new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe"));
    private static final ShellProfile UBUNTU =
        new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.empty(), List.of("wsl.exe", "-d", "Ubuntu"));
    private final List<Path> starts = Collections.synchronizedList(new ArrayList<>());
    private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());
    private final List<PipedOutputStream> outputs = Collections.synchronizedList(new ArrayList<>());

    @AfterEach void close() throws Exception {
        closeOwners();
        for (PipedOutputStream output : outputs) output.close();
    }

    /** As a plugin can open a tab on any directory: its shell starts at home, and a split or new tab of it starts where a new shell starts. */
    @Test void aPaneOnAnotherHostPassesNothingOn() throws Exception {
        WindowContent owner = window();
        TerminalContent[] tab = new TerminalContent[1];
        edt(() -> tab[0] = owner.openTab(SHARE, null, Optional.empty(), Optional.empty()));
        until(() -> tab[0].pane().running());
        edt(() -> {
            assertThat(tab[0].pane().inheritableDirectory()).isNull();
            assertThat(owner.directory()).as("for a new tab or window").isNull();
        });
        TerminalPane split = split(owner, tab[0]);

        edt(() -> assertThat(split.directory()).isEqualTo(HOME));
        assertThat(starts).as("the window's tab, the tab on the share and its split").containsExactly(HOME, HOME, HOME);
    }

    /** The control: a WSL profile's pane on its distribution's share passes it on, and the split's {@code --cd} is its Linux path. */
    @Test void aWslPanePassesItsOwnShareOn() throws Exception {
        WindowContent owner = window();
        TerminalContent[] tab = new TerminalContent[1];
        edt(() -> tab[0] = owner.openTab(WSL_SHARE, null, Optional.of(UBUNTU.id()), Optional.empty()));
        until(() -> tab[0].pane().running());
        edt(() -> assertThat(tab[0].pane().inheritableDirectory()).isEqualTo(WSL_SHARE));
        TerminalPane split = split(owner, tab[0]);

        edt(() -> assertThat(split.directory()).isEqualTo(WSL_SHARE));
        assertThat(commands.getLast()).containsExactly("wsl.exe", "-d", "Ubuntu", "--cd", "/home/me");
    }

    /** A pane of any other profile has no WSL share to pass on: without WSL, {@code wsl.localhost} is a name the network may answer. */
    @Test void aPaneOutsideAWslProfilePassesNoWslShareOn() throws Exception {
        WindowContent owner = window();
        TerminalContent[] tab = new TerminalContent[1];
        edt(() -> tab[0] = owner.openTab(WSL_SHARE, null, Optional.empty(), Optional.empty()));
        until(() -> tab[0].pane().running());
        edt(() -> assertThat(tab[0].pane().inheritableDirectory()).isNull());
        TerminalPane split = split(owner, tab[0]);

        edt(() -> assertThat(split.directory()).isEqualTo(HOME));
    }

    /** A window whose panes start PowerShell, or Ubuntu when asked for it, over attached sessions. */
    private WindowContent window() throws Exception {
        var powershell = new LaunchSettings(POWERSHELL.command(), Map.of(), 80, 24, 100, POWERSHELL);
        var ubuntu = new LaunchSettings(UBUNTU.command(), Map.of(), 80, 24, 100, UBUNTU);
        ShellLauncher launcher = new ShellLauncher(Runnable::run,
            (Optional<String> profile) -> profile.filter(UBUNTU.id()::equals).isPresent() ? ubuntu : powershell,
            (where, settings) -> {
                starts.add(where);
                commands.add(settings.command());
                return attached();
            });
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher));
        until(() -> owner[0].currentPane().running());
        return owner[0];
    }

    private static TerminalPane split(WindowContent owner, TerminalContent tab) throws Exception {
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = owner.openBeside(tab, dev.jasper.app.layout.Side.RIGHT, null, null, tab.pane().profile(), Optional.empty()).pane());
        until(() -> pane[0].running());
        return pane[0];
    }

    private TerminalSession attached() {
        try {
            var output = new PipedOutputStream();
            outputs.add(output);
            var input = new PipedInputStream(output);
            return TerminalSession.attach(new AttachedConnection(input, OutputStream.nullOutputStream(), (columns, rows) -> { },
                new CompletableFuture<>(), () -> { try { input.close(); } catch (IOException ignored) { } }), new GridSize(80, 24), 100);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
