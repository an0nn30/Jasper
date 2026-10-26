package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.notifications.CommandNotice;
import dev.jasper.app.notifications.CommandNotifier;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.buddy.view.BuddyTestSupport;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real PTY -> parser -> EDT -> tab/window and buddy, without opening a native window. */
@DisabledOnOs(OS.WINDOWS)
class TerminalTitleIntegrationTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static String start(String command) {
        String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
        return "\\033]1341;jasper;cmd;" + encoded + "\\007\\033]133;C\\007";
    }

    private record Fixture(WindowContent content, BuddyTestSupport deck, List<String> nativeTitles,
                           NotificationCenter center) { }

    private Fixture open(String script) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            java.nio.file.Files.isExecutable(java.nio.file.Path.of("/bin/bash")), "Bash fixture unavailable");
        // ProcessHandle reports the real executable; /bin/sh may resolve to bash or dash.
        return open(List.of("/bin/bash", "--noprofile", "--norc", "-c", script), "bash");
    }

    private Fixture open(List<String> program, String label) throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try { return TerminalSession.start(SessionLaunchOptions.builder().command(program).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build()); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }, label);
        Fixture[] fixture = new Fixture[1];
        edt(() -> {
            WindowContent window = content(launcher);
            window.setActive(false);
            BuddyTestSupport deck = new BuddyTestSupport();
            // In the background, as when a command finishes out of sight.
            var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.BACKGROUND);
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck.companion(), center,
                working -> {}, (delay, run) -> () -> {});
            window.activity(event -> {
            switch (event) {
                case WorkspaceActivity.Presentation value -> notifier.presentation(value.id(), value.groupId(),
                    value.groupTitle(), value.title(), value.watched(), value.activate());
                case WorkspaceActivity.Started value -> notifier.started(value.id(), value.command(),
                    value.elapsedNanos(), value.activate(), false);
                case WorkspaceActivity.Finished value -> notifier.finished(value.id(), value.command(),
                    value.exitStatus(), value.duration(), new CommandNotice.Origin(value.origin().anyWindowActive(),
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), null, value.activate());
                case WorkspaceActivity.TitleChanged value -> notifier.titleChanged(value.id(), value.title());
                case WorkspaceActivity.PaneState value -> {
                    switch (value.state()) {
                        case OPENED -> notifier.opened(value.id());
                        case CLOSED -> notifier.closed(value.id());
                        case FOCUSED -> { }
                        case BLURRED -> { }
                    }
                }
            }
            });
            List<String> titles = new ArrayList<>();
            window.onTitle = titles::add;
            fixture[0] = new Fixture(window, deck, titles, center);
        });
        pending.remove().run();
        until(() -> fixture[0].content.currentPane().view() != null);
        return fixture[0];
    }

    @Test void tabsAndNativeTitlesFollowForegroundJobsWithoutShellIntegration() throws Exception {
        Fixture f = open(List.of("/bin/bash", "--noprofile", "--norc", "-l", "-i"), "bash");
        until(() -> f.content.currentTerminal().title().equals("~ bash"));
        edt(() -> f.content.currentPane().session().write("sleep 30\r"));
        until(() -> f.content.currentTerminal().title().equals("~ sleep"));
        edt(() -> {
            assertThat(f.nativeTitles.getLast()).isEqualTo("~ sleep");
            assertThat(f.content.currentPane().shellIntegrationDetected()).isFalse();
            f.content.currentPane().session().write(new byte[]{3});
        });
        until(() -> f.content.currentTerminal().title().equals("~ bash"));
    }

    @Test void tmuxForwardsPaneTitlesToTheOuterTab(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var tmux = java.util.stream.Stream.of("/opt/homebrew/bin/tmux", "/usr/local/bin/tmux", "/usr/bin/tmux")
            .map(java.nio.file.Path::of).filter(java.nio.file.Files::isExecutable).findFirst();
        org.junit.jupiter.api.Assumptions.assumeTrue(tmux.isPresent(), "tmux is not installed");
        String socket = "jasper-title-" + java.util.UUID.randomUUID();
        var config = directory.resolve("tmux.conf");
        java.nio.file.Files.writeString(config, "set -g status off\nset -g set-titles on\n"
            + "set -g set-titles-string '#{pane_title}'\n");
        var script = directory.resolve("title.sh");
        java.nio.file.Files.writeString(script, "while read title; do printf '\\033]2;%s\\007' \"$title\"; done\n");
        try {
            Fixture f = open(List.of(tmux.get().toString(), "-L", socket, "-f", config.toString(),
                "new-session", "/bin/sh", script.toString()), "tmux");
            edt(() -> f.content.currentPane().session().write("Reviewing files\r"));
            until(() -> f.content.currentTerminal().title().equals("~ tmux Reviewing files"));
            edt(() -> {
                assertThat(f.nativeTitles.getLast()).isEqualTo("~ tmux Reviewing files");
                assertThat(f.content.currentPane().title()).isEqualTo("Reviewing files");
            });
        } finally {
            Process cleanup = new ProcessBuilder(tmux.get().toString(), "-L", socket, "kill-server").start();
            if (!cleanup.waitFor(5, TimeUnit.SECONDS)) cleanup.destroyForcibly();
        }
    }

    @Test void oscTitlesUpdateTheTabWindowAndBubbleWhileACommandRuns() throws Exception {
        Fixture f = open("printf '\\033]2;Prompt directory\\007'; read step; printf '" + start("worker") + "'; "
            + "while read title; do printf '\\033]2;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("start\n"));
        until(() -> !f.deck.isEmpty());
        edt(() -> {
            assertThat(f.content.tabs().get(0).title()).isEqualTo("~ bash");
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("~ bash");
            f.content.currentPane().session().write("Reviewing files\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("~ bash Reviewing files"));
        edt(() -> {
            assertThat(f.content.tabs().get(0).title()).isEqualTo("~ bash Reviewing files");
            assertThat(f.nativeTitles.getLast()).isEqualTo("~ bash Reviewing files");
            f.content.currentPane().session().write("Writing tests\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("~ bash Writing tests"));
        edt(() -> {
            assertThat(f.content.tabs().get(0).title()).isEqualTo("~ bash Writing tests");
            f.content.currentPane().session().write("\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("~ bash"));
        edt(() -> {
            assertThat(f.content.tabs().get(0).title()).isEqualTo("~ bash");
            int generation = f.deck.generation();
            f.content.currentTerminal().rename("Pinned worker");
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("Pinned worker");
            assertThat(f.deck.notices().getFirst().group().title()).isEqualTo("Pinned worker");
            assertThat(f.deck.generation()).isEqualTo(generation);
            f.content.currentTerminal().rename("");
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("~ bash");
        });
    }

    @Test void aBurstEndingWithAPromptTitleCannotOverwriteTheCompletedNotice() throws Exception {
        String burst = "\\033]0;~\\007" + start("sleep 12")
            + "\\033]0;~\\007\\033]133;D;0\\007"
            + "\\033]2;Prompt directory\\007";
        Fixture f = open("read step; printf '" + burst + "'; read step");
        CountDownLatch parsed = new CountDownLatch(1);
        edt(() -> {
            f.content.currentPane().session().addListener(new TerminalSessionListener() {
                @Override public void titleChanged(String title) {
                    if (title.equals("Prompt directory")) parsed.countDown();
                }
            });
            f.content.currentPane().session().write("go\n");
            // Hold Swing until the reader has parsed the entire burst. Completion must name the
            // captured command even with directory titles on both sides of its end marker.
            try { assertThat(parsed.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        until(() -> f.content.tabs().get(0).title().equals("~ bash Prompt directory"));
        edt(() -> {
            assertThat(f.center.history()).singleElement()
                .satisfies(entry -> assertThat(entry.request().title()).isEqualTo("sleep 12"));
            assertThat(f.deck.notices()).as("Buddy keeps live work only").isEmpty();
            assertThat(f.nativeTitles.getLast()).isEqualTo("~ bash Prompt directory");
        });
    }

    @Test void programTitlesWorkWithoutShellIntegrationAndManualTabNamesStillWin() throws Exception {
        Fixture f = open("while read title; do printf '\\033]1;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("Editor document\n"));
        until(() -> f.content.tabs().get(0).title().equals("~ bash Editor document"));
        edt(() -> {
            f.content.currentTerminal().rename("Pinned name");
            f.content.currentPane().session().write("Another document\n");
        });
        until(() -> f.content.currentPane().title().equals("Another document"));
        edt(() -> {
            assertThat(f.content.tabs().get(0).title()).isEqualTo("Pinned name");
            assertThat(f.deck.notices()).isEmpty();
            f.content.currentTerminal().rename("");
            assertThat(f.content.tabs().get(0).title()).isEqualTo("~ bash Another document");
        });
    }

    @Test void aChangedPatternRenamesOpenTabsAtOnceAndARenameStillWins() throws Exception {
        Fixture f = open("while read title; do printf '\\033]2;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("Fix tab names\n"));
        until(() -> f.content.tabs().get(0).title().equals("~ bash Fix tab names"));
        var defaults = dev.jasper.app.config.ConfigSnapshot.defaults();
        edt(() -> {
            f.content.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{title} | {full}")).build(), false);
            assertThat(f.content.tabs().get(0).title()).isEqualTo("Fix tab names | ~");
            f.content.currentTerminal().rename("Pinned");
            f.content.applyConfiguration(defaults.toBuilder().terminal(defaults.terminal().withTabName("{process}")).build(), false);
            assertThat(f.content.tabs().get(0).title()).isEqualTo("Pinned");
            f.content.currentTerminal().rename("");
            assertThat(f.content.tabs().get(0).title()).isEqualTo("bash");
        });
    }
}
