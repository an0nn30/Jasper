package dev.jasper.app;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.jasper.terminal.session.TerminalSession;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.app.DesktopTestSupport.*;

@DisabledOnOs(OS.WINDOWS)
class TerminalTabTest {
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
            TerminalTab original = owners[1].currentTab();
            owners[1].newTab(HOME);
            owners[1].selectTab(original);
            owners[1].reorderTab(0, 1);
            assertThat(owners[1].currentTab()).isSameAs(original);
            owners[1].close();
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> {});
    }
    @Test void splitsAndZoomReuseSessionsAndRenameSurvivesShellTitle() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending)));
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> {
            first[0] = owner[0].currentPane();
            owner[0].currentTab().rename("work");
            owner[0].currentTab().split(SplitTree.Axis.RIGHT);
        });
        pending.remove().run();
        edt(() -> {
            TerminalTab tab = owner[0].currentTab();
            tab.toggleZoom(); tab.navigate(SplitTree.Direction.LEFT); tab.toggleZoom();
            assertThat(tab.focusedPane()).isSameAs(first[0]);
            assertThat(first[0].running()).isTrue();
            assertThat(tab.title()).isEqualTo("work");
            tab.closePane(first[0]);
            assertThat(tab.panes()).hasSize(1);
            assertThat(tab.focusedPane().running()).isTrue();
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
        edt(() -> { owner[0] = content(launcher); owner[0].onError = errors::add; });
        pending.remove().run();
        TerminalPane[] first = new TerminalPane[1];
        edt(() -> { first[0] = owner[0].currentPane(); owner[0].invoke(ActionId.SPLIT_DOWN); });
        pending.remove().run();
        edt(() -> {
            assertThat(errors).singleElement().asString().contains("shell executable unavailable");
            assertThat(owner[0].currentPane()).isSameAs(first[0]);
            assertThat(first[0].running()).isTrue();
            assertThat(owner[0].currentTab().panes()).hasSize(1);
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
        edt(() -> { owner[0].currentTab().rename("manual"); owner[0].currentPane().session().write("go\n"); });
        until(() -> owner[0].currentPane().title().equals("new-shell-title"));
        edt(() -> {
            assertThat(owner[0].currentTab().title()).isEqualTo("manual");
            owner[0].currentTab().rename("");
            assertThat(owner[0].currentTab().title()).isEqualTo("new-shell-title (bash)");
            owner[0].close();
        });
    }

    @Test void dividerRatioSurvivesZoomAndStaleRendererCannotChangeClosedBranches() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        javax.swing.JSplitPane[] old = new javax.swing.JSplitPane[1];
        edt(() -> {
            old[0] = (javax.swing.JSplitPane) owner[0].currentTab().getComponent(0);
            old[0].setSize(1000, 600); old[0].doLayout();
        });
        edt(() -> {});
        edt(() -> {
            old[0].setDividerLocation(0.7);
            assertThat(((SplitTree.Branch) owner[0].currentTab().tree().root().orElseThrow()).ratio()).isCloseTo(0.7, within(0.002));
            owner[0].currentTab().toggleZoom(); owner[0].currentTab().toggleZoom();
            javax.swing.JSplitPane restored = (javax.swing.JSplitPane) owner[0].currentTab().getComponent(0);
            restored.setSize(1000, 600); restored.doLayout();
        });
        edt(() -> {});
        edt(() -> {
            javax.swing.JSplitPane restored = (javax.swing.JSplitPane) owner[0].currentTab().getComponent(0);
            assertThat(restored.getDividerLocation()).isBetween(685, 705);
            owner[0].currentTab().closePane(owner[0].currentPane());
            old[0].setDividerLocation(0.3);
            assertThat(owner[0].currentTab().panes()).hasSize(1);
            owner[0].close();
        });
    }
    @Test void layoutMinimumIncludesLargeFontsNestedSplitsAndVisibleChrome() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        WindowContent[] owner = new WindowContent[1];
        edt(() -> owner[0] = content(launcher(pending))); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_RIGHT)); pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN)); pending.remove().run();
        edt(() -> {
            var content = owner[0];
            for (TerminalPane pane : content.currentTab().panes()) pane.view().setFontSize(72);
            var panes = content.currentTab().panes();
            int twoColumns = panes.get(0).view().getMinimumSize().width + panes.get(1).view().getMinimumSize().width;
            int twoRows = panes.get(1).view().getMinimumSize().height + panes.get(2).view().getMinimumSize().height;
            assertThat(content.currentTab().getMinimumSize().width).isGreaterThan(twoColumns);
            assertThat(content.currentTab().getMinimumSize().height).isGreaterThan(twoRows);
            javax.swing.JRootPane root = new javax.swing.JRootPane();
            root.setContentPane(content); root.setJMenuBar(content.menuBar());
            var outer = TerminalWindow.minimumSize(root, new java.awt.Insets(30, 2, 2, 2));
            assertThat(outer.height).isGreaterThan(content.currentTab().getMinimumSize().height + 32);
            assertThat(outer.width).isGreaterThan(twoColumns + 4);
            java.awt.Dimension previous = outer;
            content.currentTab().toggleZoom();
            assertThat(TerminalWindow.minimumSize(root, new java.awt.Insets(30, 2, 2, 2)).height).isLessThan(previous.height);
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
            minimum[0] = TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2));
            owner[0].currentPane().session().write("go\n");
        });
        until(() -> owner[0].currentPane().title().equals(title));
        edt(() -> {
            owner[0].update();
            assertThat(owner[0].status().getText()).contains("long-directory-segment/");
            assertThat(TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].currentTab().rename("manual rename ".repeat(200));
            assertThat(TerminalWindow.minimumSize(root[0], new java.awt.Insets(30, 2, 2, 2))).isEqualTo(minimum[0]);
            owner[0].close();
        });
    }
}
