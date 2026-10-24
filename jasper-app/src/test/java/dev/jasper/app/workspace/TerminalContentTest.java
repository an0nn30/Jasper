package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.Side;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.jasper.terminal.session.TerminalSession;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Terminal tabs: one pane each, in groups (workspace tabs spec 9.2). */
@DisabledOnOs(OS.WINDOWS)
class TerminalContentTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void completionAfterOwnerCloseTerminatesReturnedProcess() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        List<TerminalSession> sessions = new ArrayList<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            TerminalSession session = shell(path); sessions.add(session); return session;
        }, "sh");
        edt(() -> { WindowContent owner = content(launcher); owner.close(); owner.close(); });
        pending.remove().run();
        edt(() -> {});
        assertThat(sessions.getFirst().exitFuture().get(5, TimeUnit.SECONDS)).isNotNull();
    }
    @Test void closingOneOwnerPreservesOtherAndReorderPreservesActiveIdentity() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> { owners[0] = content(launcher(pending)); owners[1] = content(launcher(pending)); });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {
            owners[0].close();
            assertThat(owners[1].currentPane().running()).isTrue();
            TerminalContent original = owners[1].currentTerminal();
            owners[1].newTab(HOME);
            owners[1].selectTab(original);
            owners[1].reorderParentTab(owners[1].parentTabOf(owners[1].tabs().get(0)).orElseThrow(), 1);
            assertThat(owners[1].currentTerminal()).isSameAs(original);
            owners[1].close();
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {});
    }
    @Test void splitsAndMaximizeReuseSessionsAndRenameSurvivesShellTitle() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> {
            first[0] = owner[0].currentPane();
            owner[0].currentTerminal().rename("work");
            owner[0].invoke(ActionId.SPLIT_RIGHT);
        });
        pending.remove().run();
        edt(() -> {
            TerminalContent work = owner[0].terminalOf(first[0]);
            owner[0].toggleMaximize(); owner[0].focusGroup(Side.LEFT); owner[0].toggleMaximize();
            assertThat(owner[0].currentPane()).isSameAs(first[0]);
            assertThat(owner[0].workspace().model().maximized()).isEmpty();
            assertThat(first[0].running()).isTrue();
            assertThat(work.title()).isEqualTo("work");
            owner[0].closeTab(work);
            assertThat(owner[0].tabCount()).isEqualTo(1);
            assertThat(owner[0].workspace().model().groups()).hasSize(1);
            assertThat(owner[0].currentPane().running()).isTrue();
            owner[0].close();
        });
    }
    @Test void failedSplitShowsErrorAndKeepsExistingSession() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        int[] launches = {0};
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            if (++launches[0] > 1) throw new IllegalStateException("shell executable unavailable");
            return shell(path);
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        List<String> errors = new ArrayList<>();
        edt(() -> { owner[0] = content(launcher); owner[0].onError = (title, message) -> errors.add(title + ": " + message); });
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> { first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.SPLIT_DOWN); });
        pending.remove().run();
        edt(() -> {
            assertThat(errors).singleElement().asString().contains("shell executable unavailable");
            assertThat(errors.getFirst()).startsWith("Could not start the terminal: ");
            assertThat(owner[0].currentPane()).isSameAs(first[0]);
            assertThat(first[0].running()).isTrue();
            assertThat(owner[0].tabCount()).as("the failed split's tab closed").isEqualTo(1);
            assertThat(owner[0].workspace().model().groups()).as("and its group with it").hasSize(1);
            owner[0].close();
        });
    }

    @Test void aFailedLaunchReportsItsDetailsAndWhatWasThrown() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var thrown = new IllegalStateException("shell executable unavailable");
        int[] launches = {0};
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            if (++launches[0] > 1) throw thrown;
            return shell(path);
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        List<String> details = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].onLaunchFailure = (text, cause) -> { details.add(text); causes.add(cause); };
        });
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN));
        pending.remove().run();
        edt(() -> {
            assertThat(details).singleElement().asString().contains("shell executable unavailable");
            assertThat(causes).containsExactly(thrown);
            owner[0].close();
        });
    }

    @Test void titleOverrideSurvivesRealOscTitleChangeAndBlankRestoresIt() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of("/bin/bash")), "Bash fixture unavailable");
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/bash", "--noprofile", "--norc", "-c",
                    "read answer; printf '\\033]2;new-shell-title\\007'; read answer")).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception e) { throw new RuntimeException(e); }
        }, "bash");
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher)); pending.remove().run();
        edt(() -> { owner[0].currentTerminal().rename("manual"); owner[0].currentPane().session().write("go\n"); });
        until(() -> owner[0].currentPane().title().equals("new-shell-title"));
        edt(() -> {
            assertThat(owner[0].currentTerminal().title()).isEqualTo("manual");
            assertThat(owner[0].currentTerminal().renamed()).contains("manual");
            owner[0].currentTerminal().rename("");
            assertThat(owner[0].currentTerminal().title()).isEqualTo("new-shell-title");
            assertThat(owner[0].selectedTab().title()).as("the tab's presentation follows").isEqualTo("new-shell-title");
            owner[0].close();
        });
    }

    @Test void weightsSurviveMaximizeAndAStaleSplitChangesNothing() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> {
            owner[0].setSize(1000, 600); LayoutTestSupport.layoutTree(owner[0]);
            var old = owner[0].workspace().split(List.of());
            owner[0].workspace().resizeSplit(List.of(), List.of(0.7, 0.3));
            owner[0].toggleMaximize(); owner[0].toggleMaximize();
            LayoutTestSupport.layoutTree(owner[0]);
            var restored = owner[0].workspace().split(List.of());
            assertThat(restored).as("the maximize rebuilt the split").isNotSameAs(old);
            assertThat(restored.weights().get(0)).isCloseTo(0.7, within(1e-9));
            int available = restored.getWidth() - SplitContainer.dividerSize();
            assertThat(restored.children().get(0).getWidth()).isCloseTo((int) Math.round(available * 0.7), within(1));
            owner[0].closeTab(owner[0].currentTerminal());
            old.setWeights(List.of(0.2, 0.8));
            assertThat(owner[0].workspace().model().groups()).hasSize(1);
            assertThat(owner[0].tabCount()).isEqualTo(1);
            owner[0].close();
        });
    }
    @Test void layoutMinimumIncludesLargeFontsNestedGroupsAndVisibleChrome() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN)); pending.remove().run();
        edt(() -> {
            var content = owner[0];
            var panes = content.terminalTabs().stream().map(TerminalContent::pane).toList();
            for (TerminalPane pane : panes) pane.view().setFontSize(72);
            int twoColumns = panes.get(0).view().getMinimumSize().width + panes.get(1).view().getMinimumSize().width;
            int twoRows = panes.get(1).view().getMinimumSize().height + panes.get(2).view().getMinimumSize().height;
            var workspace = content.workspace();
            assertThat(workspace.getMinimumSize().width).isGreaterThan(twoColumns);
            assertThat(workspace.getMinimumSize().height).isGreaterThan(twoRows);
            javax.swing.JRootPane root = new javax.swing.JRootPane();
            root.setContentPane(content); root.setJMenuBar(content.menuBar());
            var outer = WindowFrame.minimumSize(root, new java.awt.Insets(30, 2, 2, 2));
            assertThat(outer.height).isGreaterThan(workspace.getMinimumSize().height + 32);
            assertThat(outer.width).isGreaterThan(twoColumns + 4);
            java.awt.Dimension previous = outer;
            content.toggleMaximize();
            assertThat(WindowFrame.minimumSize(root, new java.awt.Insets(30, 2, 2, 2)).height).isLessThan(previous.height);
            content.close();
        });
    }
    @Test void longOscDirectoryAndTitlesDoNotInflateNativeWindowMinimum() throws Exception {
        String directory = "/" + "long-directory-segment/".repeat(100);
        String title = "long shell title ".repeat(100);
        Queue<Runnable> pending = new ArrayDeque<>();
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            try {
                return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
                    "read answer; printf '\\033]7;file://localhost" + directory + "\\007\\033]2;" + title + "\\007'; read answer")).environment(System.getenv()).workingDirectory(path).grid(new GridSize(80, 24)).scrollback(100).build());
            } catch (Exception e) { throw new RuntimeException(e); }
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        javax.swing.JRootPane[] root = new javax.swing.JRootPane[1];
        java.awt.Dimension[] minimum = new java.awt.Dimension[1];
        edt(() -> owner[0] = content(launcher)); pending.remove().run();
        edt(() -> {
            root[0] = new javax.swing.JRootPane(); root[0].setContentPane(owner[0]); root[0].setJMenuBar(owner[0].menuBar());
            minimum[0] = WindowFrame.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2));
            owner[0].currentPane().session().write("go\n");
        });
        until(() -> owner[0].currentPane().title().equals(title));
        edt(() -> {
            owner[0].update();
            assertThat(owner[0].status().getText()).contains("long-directory-segment/");
            assertThat(WindowFrame.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].currentTerminal().rename("manual rename ".repeat(200));
            assertThat(WindowFrame.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].close();
        });
    }
}
