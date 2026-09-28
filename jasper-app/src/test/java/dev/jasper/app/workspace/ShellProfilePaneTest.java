package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.terminals.OpenSpec;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ShellProfilePaneTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final List<Optional<String>> requested = new ArrayList<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static TerminalSession idle(Path directory) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder()
                .command(WINDOWS ? List.of("cmd.exe", "/k") : List.of("/bin/sh", "-c", "read answer"))
                .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        } catch (Exception failure) { throw new CompletionException(failure); }
    }

    private ShellLauncher launcher() {
        return new ShellLauncher(pending::add, (Optional<String> profile) -> {
            requested.add(profile);
            String id = profile.orElse("login");
            return new LaunchSettings(List.of("idle"), Map.of(), 80, 24, 100,
                new ShellProfile(id, "Profile " + id, ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("idle")));
        }, (directory, settings) -> idle(directory));
    }

    private void launchAll() throws Exception {
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
    }

    @Test void splitsInheritTheLaunchedProfileWhileNewTabsAndExplicitRequestsChooseTheirOwn() throws Exception {
        WindowContent[] owner = new WindowContent[1];
        TerminalPane[] wsl = new TerminalPane[1];
        edt(() -> owner[0] = content(launcher()));
        launchAll();
        edt(() -> {
            TerminalPane first = owner[0].currentPane();
            assertThat(first.profile()).contains("login");
            assertThat(first.shellLabel()).isEqualTo("Profile login");
            owner[0].currentTab().split(SplitTree.Axis.RIGHT);
        });
        launchAll();
        edt(() -> {
            TerminalTab tab = owner[0].currentTab();
            wsl[0] = tab.split(tab.focusedPane(), SplitTree.Axis.DOWN, null, null, Optional.of("wsl:Ubuntu"));
            assertThat(wsl[0]).isNotNull();
            assertThat(wsl[0].shellLabel()).isEqualTo("Profile wsl:Ubuntu");
        });
        launchAll();
        edt(() -> assertThat(owner[0].currentTab().split(wsl[0], SplitTree.Axis.RIGHT, null, null)).isNotNull());
        launchAll();
        edt(() -> owner[0].newTab(HOME));
        launchAll();
        edt(() -> owner[0].openTab(HOME, null, Optional.of("cmd")));
        launchAll();
        assertThat(requested).containsExactly(Optional.empty(), Optional.of("login"), Optional.of("wsl:Ubuntu"),
            Optional.of("wsl:Ubuntu"), Optional.empty(), Optional.of("cmd"));
        edt(() -> assertThat(owner[0].currentPane().profile()).contains("cmd"));
    }

    @Test void anOpenSpecCarriesAProfileAndDefaultsToNone() {
        assertThat(new OpenSpec.Local(Optional.empty()).profile()).isEmpty();
        assertThat(new OpenSpec.Local(Optional.empty(), Optional.of("wsl:Ubuntu")).profile()).contains("wsl:Ubuntu");
        assertThatNullPointerException().isThrownBy(() -> new OpenSpec.Local(Optional.empty(), null));
    }
}
