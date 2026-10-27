package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.HOME;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.app.workspace.JasperWindow;
import dev.jasper.app.workspace.WindowShell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Full windows spec 5.2 and 5.3 through the application, with stand-in frames: a request with no window of its own goes
 * to the most recently active window, restored first when none is open, and closing the last window leaves residency to
 * decide.
 */
class JasperApplicationWindowsTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final List<String> frames = new ArrayList<>();

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> {
            held[0] = new JasperApplication(null, DesktopTestSupport.idleLauncher(pending), new CommandHistory(), null, terminate);
            held[0].frames = window -> {
                int index = held[0].windows().windows().indexOf(window);
                String name = "w" + (index < 0 ? "?" : index);
                return new WindowShell(() -> frames.add("show " + name), () -> frames.add("front " + name),
                    () -> frames.add("dispose " + name), () -> frames.add("discard " + name), () -> null,
                    component -> SwingUtilities.isDescendingFrom(component, window.content()));
            };
        });
        return held[0];
    }

    @Test void everyRequestWithNoWindowOfItsOwnRestoresTheWindowsFirst() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                JasperWindow first = application.windows().windows().getFirst();
                assertThat(application.windows().windows()).as("a handoff with no window restores one").containsExactly(first);
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                assertThat(first.content().terminalTabs()).as("a folder opens as a terminal tab in the most recently active window").hasSize(2);
                assertThat(frames).contains("front w0");
                application.windows().closeAll();
                assertThat(application.windows().isEmpty()).isTrue();
                application.windows().openWindow(null);
                assertThat(application.windows().windows()).as("New Window opens one window").hasSize(1);
                application.windows().closeAll();
                var tab = application.contributions().tabs().open("dev.example.editor", "notes", "notes.txt", null, new JPanel());
                assertThat(tab.window()).as("a plugin's tab with no window open goes to a restored window")
                    .isEqualTo(application.windows().windows().stream().findFirst().orElseThrow().id());
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * Floating tool windows plan 1 Task 8: every window's detached tool windows get native windows only where windows can
     * be made; headless they get none and wait.
     */
    @Test void everyWindowsDetachedToolWindowsGetNativeWindowsAndNoneHeadless() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                assertThat(DesktopTestSupport.detachedShells(application.windows().windows().stream().findFirst().orElseThrow().content()))
                    .as("headless, nothing native is made").isFalse();
                application.windows().closeAll();
                application.nativeToolWindows = true;
                application.open(List.of(), JasperApplication.HANDOFF);
                assertThat(DesktopTestSupport.detachedShells(application.windows().windows().stream().findFirst().orElseThrow().content()))
                    .as("where windows can be made, a window's detached tool windows get them").isTrue();
                application.windows().openWindow(null);
                JasperWindow second = application.windows().windows().getLast();
                assertThat(DesktopTestSupport.detachedShells(second.content())).as("a second window's detached tool windows get them too").isTrue();
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * Full windows spec 5.1, one kind of window: every window, its manager and the native frames are made only where the
     * application says, each with an explicit {@code new} (a constructor reference would hide the site).
     * {@code verifyApplicationArchitecture} checks the same with javap.
     */
    @Test void theWindowsTheirManagerAndTheFramesAreMadeOnlyWhereTheApplicationSays() throws Exception {
        var pinned = java.util.Set.of("dev/jasper/app/workspace/JasperWindow", "dev/jasper/app/workspace/WindowManager",
            "dev/jasper/app/workspace/WindowFrame");
        var sites = new java.util.TreeMap<String, List<String>>();
        var classes = java.nio.file.Path.of(JasperApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var files = java.nio.file.Files.walk(classes)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                var model = java.lang.classfile.ClassFile.of().parse(file);
                String owner = model.thisClass().asInternalName();
                for (var method : model.methods())
                    method.code().ifPresent(code -> {
                        for (var element : code)
                            if (element instanceof java.lang.classfile.instruction.InvokeInstruction call
                                    && call.opcode() == java.lang.classfile.Opcode.INVOKESPECIAL && call.name().equalsString("<init>")
                                    && pinned.contains(call.owner().asInternalName()) && !owner.equals(call.owner().asInternalName()))
                                sites.computeIfAbsent(call.owner().asInternalName(), made -> new ArrayList<>())
                                    .add(owner + "." + method.methodName().stringValue());
                    });
                for (var entry : model.constantPool())
                    if (entry instanceof java.lang.classfile.constantpool.MethodHandleEntry handle
                            && handle.reference().name().equalsString("<init>") && pinned.contains(handle.reference().owner().asInternalName()))
                        sites.computeIfAbsent(handle.reference().owner().asInternalName(), made -> new ArrayList<>()).add(owner + "::new");
            }
        }
        assertThat(sites).containsOnlyKeys(pinned);
        assertThat(sites.get("dev/jasper/app/workspace/JasperWindow")).hasSize(1)
            .allMatch(site -> site.matches("dev/jasper/app/workspace/WindowManager\\.(open|lambda\\$open\\$\\d+)"));
        assertThat(sites.get("dev/jasper/app/workspace/WindowManager")).containsExactly("dev/jasper/app/application/JasperApplication.<init>");
        assertThat(sites.get("dev/jasper/app/workspace/WindowFrame")).hasSize(2)
            .anyMatch(site -> site.matches("dev/jasper/app/application/JasperApplication\\.(frameOnScreen|lambda\\$frameOnScreen\\$\\d+)"))
            .anyMatch(site -> site.matches("dev/jasper/app/application/JasperApplication\\.(rehearsalWindow|lambda\\$rehearsalWindow\\$\\d+)"));
    }

    /** Tabs and panes spec 13: a request's folder opens as a new tab of the window, at the end of its strip, shown. */
    @Test void aRequestOpensANewTabInTheWindow() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                var main = application.windows().windows().stream().findFirst().orElseThrow().content();
                main.newTab(HOME);
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                ParentTabs parents = main.windowLayout();
                assertThat(parents.parentTabs()).hasSize(3);
                assertThat(parents.active()).as("the last, shown").isEqualTo(parents.parentTabs().getLast().id());
                assertThat(parents.parentTabs()).as("each holding one terminal").allSatisfy(parent -> assertThat(parent.layout().tabs()).hasSize(1));
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /** Full windows spec 5.2: an outside request raises the most recently active window and opens there. */
    @Test void aRequestTargetsTheMostRecentlyActiveWindow() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                var first = application.windows().windows().getFirst();
                var second = application.windows().openWindow(null).orElseThrow();
                second.events().activated().accept(true);
                first.events().activated().accept(true);
                assertThat(application.windows().lastActive()).as("the first window was used last").contains(first);
                frames.clear();
                application.open(List.of(HOME), JasperApplication.HANDOFF);
                assertThat(frames).as("the window used last comes forward").containsExactly("front w0");
                assertThat(first.content().terminalTabs()).as("the folder opens there").hasSize(2);
                assertThat(second.content().terminalTabs()).as("and not in the window opened last").hasSize(1);
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * Rulings 25: a request made while the last window closes (here a panel's close listener, as the window lets its
     * panels go) adds nothing to the closing window and opens no other.
     */
    @Test void aRequestWhileTheLastWindowClosesAddsNothingToItAndOpensNoOther() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                List<Integer> tabsDuringClose = new ArrayList<>();
                application.contributions().addPanel("dev.example.notes", "Notes", new javax.swing.ImageIcon(),
                    dev.jasper.app.dock.Slot.RIGHT_TOP, site -> {
                        site.onClosed(() -> {
                            application.open(List.of(HOME), JasperApplication.HANDOFF);
                            application.windows().windows().stream().findFirst().ifPresent(main -> tabsDuringClose.add(main.content().terminalTabs().size()));
                        });
                        return new javax.swing.JLabel("notes");
                    });
                application.open(List.of(), JasperApplication.HANDOFF);
                // A panel's instance is built the first time it shows in a window.
                application.contributions().requestPanel(new dev.jasper.app.contributions.Contributions.PanelRequest(
                    application.windows().windows().stream().findFirst().orElseThrow().id(), "dev.example.notes",
                    dev.jasper.app.contributions.Contributions.PanelRequest.Op.SHOW));
                application.windows().closeAll();
                assertThat(tabsDuringClose).as("the panel went while the window closed, and the request added no tab to it")
                    .containsExactly(1);
                assertThat(application.windows().isEmpty()).as("and opened no other window").isTrue();
            });
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void closingOneWindowLeavesTheOtherAndClosingTheLastEndsTheProcess() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> {
            var first = application.windows().openWindow(null).orElseThrow();
            var second = application.windows().openWindow(null).orElseThrow();
            frames.clear();
            first.requestClose();
            assertThat(application.windows().windows()).as("the other window stays").containsExactly(second);
            assertThat(terminated.getCount()).isEqualTo(1);
            second.requestClose();
            assertThat(application.windows().isEmpty()).isTrue();
            assertThat(frames).containsExactly("dispose w0", "dispose w1");
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("not resident: the last window ends the process").isTrue();
    }

    @Test void aResidentProcessOutlivesItsWindowsAndRestoresThemOnRequest() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.open(List.of(), JasperApplication.HANDOFF);
                application.windows().openWindow(null);
                frames.clear();
                application.toBackground();
                assertThat(frames).as("every window, the most recently active last").containsExactly("dispose w1", "dispose w0");
                assertThat(application.windows().isEmpty()).isTrue();
                assertThat(application.acceptsRequests()).as("still running in the background").isTrue();
                application.open(List.of(), "reopen");
                assertThat(application.windows().windows().stream().findFirst()).isPresent();
            });
        } finally {
            edt(application::quitNow);
        }
    }

    /**
     * Quit ends a resident process even when a window fails to close: every window still closes, and the failure reaches
     * the caller (on the EDT, the handler that logs it and records an incident).
     */
    @Test void quitEndsAResidentProcessWhenAWindowFailsToCloseAndPassesTheFailureOn() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> {
            application.residency(true);
            var standIn = application.frames;
            application.frames = window -> {
                WindowShell shell = standIn.apply(window);
                if (application.windows().windows().indexOf(window) == 0) return shell;
                return new WindowShell(shell.show(), shell.toFront(), () -> {
                    throw new IllegalStateException("the second window's frame failed to close");
                }, shell.discard(), shell.window(), shell.owns());
            };
            application.windows().openWindow(null);
            application.windows().openWindow(null);
            assertThatThrownBy(application::quitNow).hasMessage("the second window's frame failed to close");
            assertThat(application.windows().isEmpty()).as("every window closed whatever one threw").isTrue();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("resident, and Quit still ends the process").isTrue();
    }
}
