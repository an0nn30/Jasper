package dev.jasper.app;

import dev.jasper.terminal.TerminalSession;
import java.nio.charset.StandardCharsets;
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

import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real PTY -> parser -> EDT -> tab/window and buddy, without opening a native window. */
@DisabledOnOs(OS.WINDOWS)
class TerminalTitleIntegrationTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static String start(String command) {
        String encoded = Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
        return "\\033]1341;jasper;cmd;" + encoded + "\\007\\033]133;C\\007";
    }

    private record Fixture(WindowContent content, BuddyDeck deck, List<String> nativeTitles) { }

    private Fixture open(String script) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            java.nio.file.Files.isExecutable(java.nio.file.Path.of("/bin/bash")), "Bash fixture unavailable");
        // ProcessHandle reports the real executable; /bin/sh may resolve to bash or dash.
        return open(List.of("/bin/bash", "--noprofile", "--norc", "-c", script), "bash");
    }

    private Fixture open(List<String> program, String label) throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try { return TerminalSession.start(program, System.getenv(), path, 80, 24, 100); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }, label);
        Fixture[] fixture = new Fixture[1];
        edt(() -> {
            WindowContent window = content(launcher);
            BuddyDeck deck = new BuddyDeck();
            CommandNotifier notifier = new CommandNotifier(() -> Duration.ofNanos(1), deck, () -> {},
                (title, detail) -> {}, working -> {}, (delay, run) -> () -> {});
            window.onCommandStarted = (command, pane, elapsed, focus, watched) ->
                notifier.started(pane, command, elapsed, focus, false);
            window.onPaneTitleChanged = notifier::titleChanged;
            window.onCommandFinished = (command, status, duration, origin, pane, focus) ->
                notifier.finished(pane, command, status, duration, origin, focus);
            List<String> titles = new ArrayList<>();
            window.onTitle = titles::add;
            fixture[0] = new Fixture(window, deck, titles);
        });
        pending.remove().run();
        until(() -> fixture[0].content.currentPane().view() != null);
        return fixture[0];
    }

    @Test void tabsAndNativeTitlesFollowForegroundJobsWithoutShellIntegration() throws Exception {
        Fixture f = open(List.of("/bin/bash", "--noprofile", "--norc", "-l", "-i"), "bash");
        until(() -> f.content.currentTab().title().equals("~ (-bash)"));
        edt(() -> f.content.currentPane().session().write("sleep 30\r"));
        until(() -> f.content.currentTab().title().equals("~ (sleep)"));
        edt(() -> {
            assertThat(f.nativeTitles.getLast()).isEqualTo("~ (sleep)");
            assertThat(f.content.currentPane().shellIntegrationDetected()).isFalse();
            f.content.currentPane().session().write(new byte[]{3});
        });
        until(() -> f.content.currentTab().title().equals("~ (-bash)"));
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
            until(() -> f.content.currentTab().title().equals("Reviewing files (tmux)"));
            edt(() -> {
                assertThat(f.nativeTitles.getLast()).isEqualTo("Reviewing files (tmux)");
                assertThat(f.content.currentPane().title()).isEqualTo("Reviewing files");
            });
        } finally {
            Process cleanup = new ProcessBuilder(tmux.get().toString(), "-L", socket, "kill-server").start();
            if (!cleanup.waitFor(5, TimeUnit.SECONDS)) cleanup.destroyForcibly();
        }
    }

    @Test void oscTitlesUpdateTheTabWindowAndBubbleWhileACommandRuns() throws Exception {
        Fixture f = open("read step; printf '" + start("worker") + "'; "
            + "while read title; do printf '\\033]2;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("start\n"));
        until(() -> !f.deck.isEmpty());
        edt(() -> {
            assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("~ (bash)");
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("worker");
            f.content.currentPane().session().write("Reviewing files\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("Reviewing files"));
        edt(() -> {
            assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("Reviewing files (bash)");
            assertThat(f.nativeTitles.getLast()).isEqualTo("Reviewing files (bash)");
            f.content.currentPane().session().write("Writing tests\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("Writing tests"));
        edt(() -> {
            assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("Writing tests (bash)");
            f.content.currentPane().session().write("\n");
        });
        until(() -> f.deck.notices().getFirst().title().equals("worker"));
        edt(() -> assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("~ (bash)"));
    }

    @Test void aBurstEndingWithAPromptTitleCannotOverwriteTheCompletedNotice() throws Exception {
        String burst = start("worker") + "\\033]0;Task complete\\007\\033]133;D;0\\007"
            + "\\033]2;Prompt directory\\007";
        Fixture f = open("read step; printf '" + burst + "'; read step");
        CountDownLatch parsed = new CountDownLatch(1);
        edt(() -> {
            f.content.currentPane().session().addListener(new TerminalSession.Listener() {
                @Override public void titleChanged(String title) {
                    if (title.equals("Prompt directory")) parsed.countDown();
                }
            });
            f.content.currentPane().session().write("go\n");
            // Hold Swing until the reader has parsed the entire burst. A later session.title() read
            // would now see only "Prompt directory", losing the title belonging to the command.
            try { assertThat(parsed.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        until(() -> f.content.tabStrip().getTitleAt(0).equals("Prompt directory (bash)"));
        edt(() -> {
            assertThat(f.deck.notices().getFirst().title()).isEqualTo("Task complete");
            assertThat(f.deck.notices().getFirst().state()).isEqualTo(BuddyNotice.State.DONE);
            assertThat(f.nativeTitles.getLast()).isEqualTo("Prompt directory (bash)");
        });
    }

    @Test void programTitlesWorkWithoutShellIntegrationAndManualTabNamesStillWin() throws Exception {
        Fixture f = open("while read title; do printf '\\033]1;%s\\007' \"$title\"; done");
        edt(() -> f.content.currentPane().session().write("Editor document\n"));
        until(() -> f.content.tabStrip().getTitleAt(0).equals("Editor document (bash)"));
        edt(() -> {
            f.content.currentTab().rename("Pinned name");
            f.content.currentPane().session().write("Another document\n");
        });
        until(() -> f.content.currentPane().title().equals("Another document"));
        edt(() -> {
            assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("Pinned name");
            assertThat(f.deck.notices()).isEmpty();
            f.content.currentTab().rename("");
            assertThat(f.content.tabStrip().getTitleAt(0)).isEqualTo("Another document (bash)");
        });
    }
}
