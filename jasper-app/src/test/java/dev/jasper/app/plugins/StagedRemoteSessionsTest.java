package dev.jasper.app.plugins;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 2 Task 8a acceptance (Task 6d): the staged Remote plugin's Sessions panel, a kit Panel, mounted in a real window.
 * The window borrows the panel's kit header, which carries the panel's own title and, through {@code PanelHost.setHeader},
 * the saved-session count and the New session and Import sessions actions before Options and Hide: one title row, no
 * in-panel action row. The Add Host dialog keeps baseline f16ae32f keys through the real kit components: Enter saves
 * nothing (no default button), Escape cancels. Runs on the test thread and hops to the EDT for each step.
 */
class StagedRemoteSessionsTest {
    @TempDir Path root;

    @Test void editingSavedVaultReferenceWithoutOptionalVaultDoesNotLinkItsApi() throws Exception {
        try(var remote=new StagedRemoteHost(root,true)) {
            Class<?> auth=remote.loader.loadClass("dev.jasper.remote.hosts.Auth");
            Object credential=remote.loader.loadClass("dev.jasper.remote.hosts.Auth$Vault").getConstructor(java.util.UUID.class).newInstance(java.util.UUID.randomUUID());
            Class<?> host=remote.loader.loadClass("dev.jasper.remote.hosts.RemoteHost");
            Object saved=host.getMethod("create",String.class,String.class,int.class,String.class,auth,String.class,java.util.Optional.class)
                .invoke(null,"saved","saved.invalid",22,"",credential,"",java.util.Optional.empty());
            var lookup=java.lang.invoke.MethodHandles.privateLookupIn(remote.plugin.getClass(),java.lang.invoke.MethodHandles.lookup());
            var edit=lookup.findVirtual(remote.plugin.getClass(),"editHost",java.lang.invoke.MethodType.methodType(void.class,dev.jasper.sdk.terminal.WindowHandle.class,java.util.Optional.class));
            onEdt(()->{try{edit.invoke(remote.plugin,remote.context().terminals().window(remote.window.id()).orElseThrow(),java.util.Optional.of(saved));}
                catch(Throwable failure){throw new AssertionError(failure);}});
            var editor=remote.awaitSurface("Edit saved");
            onEdt(()->{assertThat(require(editor,"host.name.input")).isNotNull();
                assertThat(((dev.jasper.ui.model.Nodes.Choice)StagedVaultHost.node(editor,"host.choose")).face().orElseThrow().primary()).isEqualTo("credential missing");
                assertThat(((AbstractButton)require(editor,"host.choose.input")).isEnabled()).isFalse();editor.requestClose();});
            assertThat(remote.problems).isEmpty();
        }
    }

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theSessionsHeaderKeepsItsCountAndActionsInARealWindowAndTheHostDialogKeepsItsKeys() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSessions();
            onEdt(() -> {
                var header = remote.header();
                assertThat(labels(header)).as("the title, then the count from PanelHost.setHeader").contains("Sessions").doesNotContain("0");
                assertThat(names(header)).as("contributed actions before the window's own").containsExactly("New session", "Import sessions", "Options", "Hide");
                assertThat(labels(remote.window).stream().filter("Sessions"::equals).count()).as("one title row").isEqualTo(1);
                assertThat(names(remote.window).stream().filter(name -> name.equals("New session") || name.equals("Import sessions")).toList())
                    .as("no duplicate in-panel action row").containsExactly("New session", "Import sessions");
                button(header, "New session").doClick(0);
            });
            var editor = remote.awaitSurface("Add Host");
            onEdt(() -> {
                type(editor, "host.name.input", "web");
                type(editor, "host.hostname.input", "web.example");
                type(editor, "host.username.input", "deploy"); // valid: a save would succeed here
                press(require(editor, "host.name.input"), KeyEvent.VK_ENTER, '\n');
                press(require(editor, "host.hostname.input"), KeyEvent.VK_ENTER, '\n');
                press(require(editor, "host.save"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(editor.closed()).as("Enter saves nothing: the old dialog had no default button").isFalse();
                assertThat(labels(remote.header())).doesNotContain("0");
                press(require(editor, "host.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            });
            StagedVaultHost.await(editor::closed, "Escape cancels the Add Host dialog");
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(labels(remote.header())).as("empty collection count is hidden").doesNotContain("0");
                button(remote.header(), "New session").doClick(0);
            });
            var again = remote.awaitSurface("Add Host");
            onEdt(() -> {
                type(again, "host.name.input", "web");
                type(again, "host.hostname.input", "web.example");
                type(again, "host.username.input", "deploy");
                ((AbstractButton) require(again, "host.save")).doClick(0);
            });
            StagedVaultHost.await(() -> again.closed() && labels(remote.header()).contains("1"), "Add Host saved the host and the header counts it");
            assertThat(remote.problems).isEmpty();
        }
    }

    /**
     * Review fix round 1 (I3, I4): the baseline's narrow-panel and assistive-identity tests, in the real host. At 220 px
     * the card keeps Edit and Connect inside the panel at their full width, both for a host whose Vault is locked
     * ("Vault locked" with the lock glyph, Connect) and for a connected one (the dot, "Connected", Open Shell with the
     * terminal glyph). A host row is named by its name and address and described by its identity, session count included.
     */
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void aNarrowPanelKeepsTheCardsEditAndConnectReachableAndRowsCarryTheirIdentity() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSessions();
            remote.addHost("production-database", "db.internal.example", "deploy");
            for (int sessions : new int[] {0, 1}) {
                onEdt(() -> {
                    remote.sessionsState(host -> sessions, "Vault locked");
                    var view = remote.sessions();
                    view.setSize(220, 520);
                    layout(view);
                    var connect = button(view, sessions == 0 ? "Connect" : "Open Shell");
                    var edit = button(view, "Edit session");
                    for (var control : java.util.List.of(edit, connect)) {
                        var bounds = javax.swing.SwingUtilities.convertRectangle(control.getParent(), control.getBounds(), view);
                        assertThat(bounds.x).as("%s starts inside", control.getAccessibleContext().getAccessibleName()).isGreaterThanOrEqualTo(0);
                        assertThat(bounds.x + bounds.width).as("%s ends inside the 220 px panel", control.getAccessibleContext().getAccessibleName()).isLessThanOrEqualTo(220);
                        assertThat(bounds.width).as("%s keeps its full width", control.getAccessibleContext().getAccessibleName())
                            .isGreaterThanOrEqualTo(control.getPreferredSize().width);
                        assertThat(control.isShowing() || !view.isDisplayable()).isTrue();
                    }
                    assertThat(labels(view)).contains(sessions == 0 ? "Vault locked" : "Connected");
                    var row = row(view, "production-database");
                    assertThat(row).as("a host row named by its title").isNotNull();
                    var identity = row.getAccessibleContext();
                    assertThat(identity.getAccessibleName()).isEqualTo("production-database, db.internal.example");
                    assertThat(identity.getAccessibleDescription()).contains("production-database", "deploy@db.internal.example:22", "Ubuntu 24.04",
                        sessions == 1 ? "1 session" : "0 sessions");
                });
            }
            assertThat(remote.problems).isEmpty();
        }
    }

    /** The kit row (a list row) whose accessible name starts with {@code name}. */
    private static java.awt.Component row(java.awt.Container root, String name) {
        for (var child : root.getComponents()) {
            if (child.getClass().getName().endsWith("InteractiveRows$Row") && child.getAccessibleContext().getAccessibleName() != null
                    && child.getAccessibleContext().getAccessibleName().startsWith(name)) return child;
            if (child instanceof java.awt.Container nested) { var found = row(nested, name); if (found != null) return found; }
        }
        return null;
    }

    private static void layout(java.awt.Container root) {
        root.doLayout();
        for (var child : root.getComponents()) if (child instanceof java.awt.Container nested && child.isVisible()) layout(nested);
    }
}
