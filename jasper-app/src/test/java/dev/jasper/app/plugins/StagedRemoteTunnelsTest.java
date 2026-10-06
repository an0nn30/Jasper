package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 2 Task 8b acceptance in the real host: the staged Remote plugin's Tunnels panel, a kit Panel mounted in a real
 * window. The window borrows its kit header, which keeps the title, the tunnel count and New Tunnel from
 * {@code PanelHost.setHeader} (Task 6d). Real key and pointer events reach the kit rows: Enter and Space operate the
 * focused row's switch (start/stop, by the manager's confirmed state), a double click edits, Delete and Backspace ask to
 * delete; the tunnel dialogs keep baseline f16ae32f keys (Enter does nothing, Escape cancels). Every tunnel goes through
 * a missing host, so starting one fails at once without any network. Runs on the test thread and hops to the EDT.
 */
class StagedRemoteTunnelsTest {
    static final UUID MISSING = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    @TempDir Path root;

    static void tunnels(Path root, String... names) throws Exception {
        var text = new StringBuilder("# Jasper Remote tunnels.\n");
        int port = 5432;
        for (String name : names) {
            text.append("\n[[tunnel]]\nname = \"").append(name).append("\"\nhost = \"").append(MISSING)
                .append("\"\nbind_address = \"localhost\"\nbind_port = ").append(port).append("\ntarget_host = \"db.internal\"\ntarget_port = ").append(port).append('\n');
            port++;
        }
        Files.createDirectories(data(root));
        Files.writeString(data(root).resolve("tunnels.toml"), text);
    }

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theTunnelsHeaderKeepsItsCountAndActionAndRowsAndDialogsKeepTheirKeys() throws Exception {
        tunnels(root, "db", "web");
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openTunnels();
            // The store reads tunnels.toml in the background after the plugin starts.
            StagedVaultHost.await(() -> labels(remote.tunnelsHeader()).contains("2"), "tunnels.toml is read");
            onEdt(() -> {
                var header = remote.tunnelsHeader();
                assertThat(labels(header)).as("the title, then the count from PanelHost.setHeader").contains("Tunnels", "2");
                assertThat(names(header)).as("contributed action before the window's own").containsExactly("New Tunnel…", "Options", "Hide");
                assertThat(labels(remote.window).stream().filter("Tunnels"::equals).count()).as("one title row").isEqualTo(1);
                assertThat(names(remote.window).stream().filter("New Tunnel…"::equals).count()).as("no duplicate in-panel action row").isEqualTo(1);
                var view = remote.tunnels();
                view.setSize(260, 480);
                layout(view);
                var db = row(view, "db");
                assertThat(db.getAccessibleContext().getAccessibleName()).as("named by its title").isEqualTo("db, Host missing");
                assertThat(db.getAccessibleContext().getAccessibleDescription()).as("described by its live tooltip")
                    .isEqualTo("db, localhost:5432 → db.internal:5432, Host missing — edit to choose another");
                assertThat(db.getHeight()).as("two lines fit the old row height").isBetween(2 * db.getFontMetrics(db.getFont()).getHeight() - 8, 50);
                assertThat(problems(view)).as("both rows ask for a host").isEqualTo(2);
                click(db, 1);
                press(canvas(db), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.await(() -> problems(remote.tunnels()) == 1, "Enter started db, which failed at once: its host is missing");
            onEdt(() -> {
                var view = remote.tunnels();
                layout(view);
                var toggle = toggle(row(view, "db"));
                assertThat(toggle.isSelected()).as("the switch shows the confirmed state: off").isFalse();
                assertThat(toggle.getAccessibleContext().getAccessibleName()).isEqualTo("Start tunnel");
                assertThat(toggle.getAccessibleContext().getAccessibleDescription()).as("and the failure").isEqualTo("Host missing");
                var web = row(view, "web");
                click(web, 1);
                press(canvas(web), KeyEvent.VK_SPACE, ' ');
            });
            StagedVaultHost.await(() -> problems(remote.tunnels()) == 0, "Space operated web's switch");
            onEdt(() -> { layout(remote.tunnels()); click(row(remote.tunnels(), "web"), 2); });
            var editor = remote.awaitSurface("Edit web");
            awaitNamed(editor, "tunnel.save");
            onEdt(() -> {
                press(require(editor, "tunnel.name.input"), KeyEvent.VK_ENTER, '\n');
                press(require(editor, "tunnel.save"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(editor.closed()).as("Enter saves nothing: the old dialog had no default button").isFalse();
                // Any save attempt here would show "Choose a host to go through" (the host is missing): none was made.
                assertSaveNotAttempted(editor);
                press(require(editor, "tunnel.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            });
            StagedVaultHost.await(editor::closed, "Escape cancels the tunnel editor");
            onEdt(() -> {
                layout(remote.tunnels());
                var db = row(remote.tunnels(), "db");
                click(db, 1);
                press(canvas(db), KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED);
            });
            var confirm = remote.awaitSurface("Delete db?");
            awaitNamed(confirm, "confirm.verb");
            onEdt(() -> press(require(confirm, "confirm.verb"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
            StagedVaultHost.await(confirm::closed, "Escape cancels the delete confirmation");
            assertThat(Files.readString(data(root).resolve("tunnels.toml"))).contains("\"db\"");
            onEdt(() -> press(canvas(row(remote.tunnels(), "db")), KeyEvent.VK_BACK_SPACE, '\b'));
            var again = remote.awaitSurface("Delete db?");
            awaitNamed(again, "confirm.verb");
            onEdt(() -> ((AbstractButton) require(again, "confirm.verb")).doClick(0));
            StagedVaultHost.await(() -> again.closed() && labels(remote.tunnelsHeader()).contains("1"), "Backspace deleted db and the header counts one");
            assertThat(Files.readString(data(root).resolve("tunnels.toml"))).doesNotContain("\"db\"").contains("\"web\"");
            onEdt(() -> button(remote.tunnelsHeader(), "New Tunnel…").doClick(0));
            var created = remote.awaitSurface("New Tunnel");
            awaitNamed(created, "tunnel.save");
            onEdt(() -> {
                type(created, "tunnel.name.input", "api");
                press(require(created, "tunnel.name.input"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                assertThat(created.closed()).as("Enter adds nothing").isFalse();
                assertSaveNotAttempted(created);
                press(require(created, "tunnel.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            });
            StagedVaultHost.await(created::closed, "Escape cancels New Tunnel");
            assertThat(Files.readString(data(root).resolve("tunnels.toml"))).as("nothing was saved").doesNotContain("\"api\"");
            assertThat(remote.problems).isEmpty();
        }
    }

    /**
     * The baseline's empty-state wrap and literal-text tests in the real host: in a rail-wide panel the empty message
     * wraps over several lines and New Tunnel stays inside; the broken-file note shows above it. Tunnel text that looks
     * like HTML is shown as text. More grows Forward a Port's form and its host-packed window, Less shrinks both back,
     * and a dialog the user resized keeps its size.
     */
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theEmptyStateWrapsTextStaysLiteralAndMoreGrowsTheForwardForm() throws Exception {
        try (var remote = new StagedRemoteHost(root, false)) {
            remote.openTunnels();
            onEdt(() -> {
                var view = remote.tunnels();
                view.setSize(260, 400);
                layout(view);
                var description = accessible(view, "Forward a local port to a remote host, or use Forward Port from an SSH pane.");
                assertThat(description).as("the empty state's description").isNotNull();
                int line = description.getFontMetrics(description.getFont()).getHeight();
                assertThat(description.getWidth()).as("it takes the panel's width, not the text's").isLessThanOrEqualTo(260);
                assertThat(description.getHeight()).as("the text runs over several lines").isGreaterThanOrEqualTo(2 * line);
                var create = button(view, "New Tunnel");
                var bounds = javax.swing.SwingUtilities.convertRectangle(create.getParent(), create.getBounds(), view);
                assertThat(bounds.y + bounds.height).as("New Tunnel stays inside the panel").isLessThanOrEqualTo(view.getHeight());
                assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(260);
            });
            var forward = new AtomicReference<PluginDialog>();
            onEdt(() -> {
                try {
                    var context = remote.context();
                    var handle = context.terminals().window(remote.window.id()).orElseThrow();
                    forward.set(context.windows().dialog(new DialogSpec("Forward a Port", handle, true)));
                    Object panel = remote.loader.loadClass("dev.jasper.remote.ui.tunnels.ForwardPanel")
                        .getConstructor(UiKit.class, List.class, Optional.class, IntPredicate.class, Consumer.class, Runnable.class)
                        .newInstance(context.kit(), List.of(), Optional.empty(), (IntPredicate) port -> true, (Consumer<Object>) tunnel -> { }, (Runnable) () -> { });
                    forward.get().setContent((UiView) panel.getClass().getMethod("view").invoke(panel));
                    forward.get().show();
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            var surface = remote.awaitSurface("Forward a Port");
            awaitNamed(surface, "forward.more");
            int[] heights = new int[3];
            var windows = new java.awt.Dimension[4];
            onEdt(() -> {
                heights[0] = form(surface.holder()).getPreferredSize().height;
                windows[0] = remote.dialogSize(surface);
                ((AbstractButton) require(surface, "forward.more")).doClick(0);
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                heights[1] = form(surface.holder()).getPreferredSize().height;
                windows[1] = remote.dialogSize(surface);
                ((AbstractButton) require(surface, "forward.more")).doClick(0);
            });
            StagedVaultHost.settle();
            onEdt(() -> { heights[2] = form(surface.holder()).getPreferredSize().height; windows[2] = remote.dialogSize(surface); });
            assertThat(heights[1]).as("More reveals the target host and grows the form").isGreaterThan(heights[0]);
            assertThat(heights[2]).as("Less returns it to its size").isEqualTo(heights[0]);
            // Fix round 1 (I1): the host-packed dialog re-packs as the old panel's pack() did.
            assertThat(windows[1].height).as("More grows the dialog window").isGreaterThan(windows[0].height);
            assertThat(windows[2]).as("Less returns the window to its original size").isEqualTo(windows[0]);
            onEdt(() -> {
                remote.userResize(surface, new java.awt.Dimension(700, 520));
                ((AbstractButton) require(surface, "forward.more")).doClick(0);
            });
            StagedVaultHost.settle();
            onEdt(() -> windows[3] = remote.dialogSize(surface));
            assertThat(windows[3]).as("a dialog the user resized is never re-packed").isEqualTo(new java.awt.Dimension(700, 520));
            onEdt(() -> forward.get().close());
        }
        Path html = root.resolve("html");
        var text = "# Jasper Remote tunnels.\n\n[[tunnel]]\nname = \"<html><b>bold</b>\"\nhost = \"" + MISSING
            + "\"\nbind_address = \"localhost\"\nbind_port = 5432\ntarget_host = \"<html>db\"\ntarget_port = 5432\n";
        Files.createDirectories(data(html));
        Files.writeString(data(html).resolve("tunnels.toml"), text);
        try (var remote = new StagedRemoteHost(html, true)) {
            remote.openTunnels();
            StagedVaultHost.await(() -> labels(remote.tunnelsHeader()).contains("1"), "tunnels.toml is read");
            onEdt(() -> {
                var view = remote.tunnels();
                view.setSize(320, 400);
                layout(view);
                assertThat(labels(view)).as("the name shows as written").contains("<html><b>bold</b>");
                for (var label : all(view, JLabel.class))
                    assertThat(label.getClientProperty(BasicHTML.propertyKey)).as("no HTML view on %s", label.getText()).isNull();
            });
            assertThat(remote.problems).isEmpty();
        }
    }

    /** The editor shows no validation or save message: Save never ran (it would at least ask for a host here). */
    static void assertSaveNotAttempted(dev.jasper.app.windows.AuxiliarySurface editor) {
        var message = StagedVaultHost.named(editor.holder(), "tunnel.message");
        assertThat(message == null || !message.isVisible()).as("no message line: Save never ran").isTrue();
        assertThat(labels(editor.holder())).as("no validation text").noneMatch(text -> text.startsWith("Choose a host")
            || text.startsWith("A tunnel") || text.startsWith("The listen port") || text.startsWith("The target port"));
    }

    /** Rows asking for a host: the subtitle "Edit to choose a host" a stopped tunnel through a missing host shows. */
    static long problems(Container view) { return labels(view).stream().filter("Edit to choose a host"::equals).count(); }

    /** The kit list row whose accessible name starts with {@code name}. */
    static Component row(Container root, String name) {
        var found = findRow(root, name);
        if (found == null) throw new AssertionError("no row " + name);
        return found;
    }

    private static Component findRow(Container root, String name) {
        for (var child : root.getComponents()) {
            if (child.getClass().getName().endsWith("InteractiveRows$Row") && child.getAccessibleContext().getAccessibleName() != null
                    && child.getAccessibleContext().getAccessibleName().startsWith(name + ",")) return child;
            if (child instanceof Container nested) { var found = findRow(nested, name); if (found != null) return found; }
        }
        return null;
    }

    /** The rows' focusable canvas, which owns the list's key bindings. */
    static JComponent canvas(Component row) { return (JComponent) row.getParent(); }

    static AbstractButton toggle(Component row) {
        return buttons((Container) row).stream().filter(b -> b.getAccessibleContext().getAccessibleName().endsWith("tunnel")).findFirst().orElseThrow();
    }

    /** A plain left click (or double click) on the row, as the pointer delivers it. */
    static void click(Component row, int count) {
        for (int n = 1; n <= count; n++) {
            for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED})
                row.dispatchEvent(new MouseEvent(row, id, System.currentTimeMillis(), id == MouseEvent.MOUSE_CLICKED ? 0 : InputEvent.BUTTON1_DOWN_MASK,
                    4, 4, n, false, MouseEvent.BUTTON1));
        }
    }

    static Component accessible(Container root, String name) {
        for (var child : root.getComponents()) {
            if (child.isVisible() && name.equals(child.getAccessibleContext().getAccessibleName())) return child;
            if (child instanceof Container nested && child.isVisible()) { var found = accessible(nested, name); if (found != null) return found; }
        }
        return null;
    }

    /** The mounted Forward a Port form: the holder's kit view. */
    static Component form(Container holder) {
        for (var child : holder.getComponents()) {
            if (child instanceof dev.jasper.ui.swing.KitView) return child;
            if (child instanceof Container nested) { var found = form(nested); if (found != null) return found; }
        }
        return null;
    }

    static <T> List<T> all(Container root, Class<T> type) {
        var result = new java.util.ArrayList<T>();
        for (var child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container nested) result.addAll(all(nested, type));
        }
        return result;
    }

    static void layout(Container root) {
        root.doLayout();
        for (var child : root.getComponents()) if (child instanceof Container nested && child.isVisible()) layout(nested);
    }
}
