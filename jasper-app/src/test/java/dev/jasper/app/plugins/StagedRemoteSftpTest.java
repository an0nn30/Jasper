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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedRemoteHost.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 2 Task 8c acceptance in the real host: the staged Remote plugin's SFTP explorer, a kit Panel mounted in a real
 * window. The window borrows its kit header, which keeps the title and, through {@code PanelHost.setHeader}, the
 * connection name with its connected dot (Task 6d). A listing is shown as the plugin's controller shows one after a
 * successful list, with no connection: rows are named by their file name and described by "name — size — modified";
 * a 220 px rail keeps the size column whole; real Enter and double clicks reach the table's activate action; transfer
 * rows keep their components across refreshes. The New folder and Items already exist dialogs keep baseline f16ae32f
 * keys (Enter creates only from the name field; Escape closes or cancels). Runs on the test thread and hops to the EDT.
 */
class StagedRemoteSftpTest {
    @TempDir Path root;

    /** Measures the real mounted owner at its normal 5 Hz cadence, including layout and paint. */
    @SuppressWarnings("try") // Staged host shutdown is awaited by the test; interruption is propagated.
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void measuresChangedTransfersWithTwoHundredListedFilesAtFiveHertz() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSftp(); remote.quietTransfers();
            onEdt(() -> {
                var files = new ArrayList<Object>();
                for (int i=0;i<200;i++) files.add(remote.fileEntry("file-"+i+".txt", "FILE", 3700, 1000));
                remote.sftpListing("prod", "/srv/app", files);
                remote.sftp().setSize(360, 720);
            });
            StagedVaultHost.settle();
            var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            var samples = new ArrayList<Double>();
            for (int tick=0;tick<25;tick++) {
                int step=tick;
                long due=System.nanoTime()+200_000_000L;
                onEdt(() -> {
                    long start=System.nanoTime();
                    var rows = new ArrayList<Object[]>();
                    for (int i=0;i<ids.size();i++) rows.add(new Object[]{ids.get(i), "archive-"+i+".zip", step+" MiB / 100 MiB", step/100.0, false});
                    remote.transferRows(rows);
                    var view=remote.sftp(); StagedRemoteTunnelsTest.layout(view);
                    var image=new java.awt.image.BufferedImage(360,720,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics=image.createGraphics(); try {view.paint(graphics);} finally {graphics.dispose();}
                    if(step>=5) samples.add((System.nanoTime()-start)/1_000_000.0);
                    assertThat(StagedRemoteHost.labels(view)).contains(step+" MiB / 100 MiB");
                });
                long remaining=due-System.nanoTime();
                if(remaining>0) TimeUnit.NANOSECONDS.sleep(remaining);
            }
            var sorted=samples.stream().sorted().toList();
            System.out.printf(java.util.Locale.ROOT,
                "SFTP_PERF runtime=%s vendor=%s files=200 transfers=3 warmup=5 measured=20 cadenceHz=5 meanMs=%.3f p95Ms=%.3f maxMs=%.3f%n",
                System.getProperty("java.runtime.version"),System.getProperty("java.vendor"),
                samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), sorted.get(18), sorted.getLast());
            assertThat(remote.problems).isEmpty();
        }
    }

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theSftpHeaderKeepsItsConnectionAndDotAndTheBrowserKeepsItsKeysAndGeometry() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSftp();
            onEdt(() -> {
                var header = remote.sftpHeader();
                assertThat(labels(header)).as("the title, no connection yet").contains("SFTP").doesNotContain("prod");
                assertThat(names(header)).as("no contributed actions; the window's own").containsExactly("Options", "Hide");
                assertThat(StagedRemoteTunnelsTest.accessible(remote.sftp(), "Not Connected")).as("the empty state").isNotNull();
                assertThat(names(remote.window)).contains("Choose Session");
            });
            var activated = new CopyOnWriteArrayList<String>();
            onEdt(() -> {
                remote.sftpEditing(entry -> activated.add(name(entry)), () -> { });
                remote.sftpListing("prod", "/srv/app", List.of(remote.fileEntry("logs", "DIRECTORY", 0, 1_000),
                    remote.fileEntry("readme.txt", "FILE", 3_700, 1_000), remote.fileEntry("big.iso", "FILE", 999_900_000_000L, 1_000),
                    remote.fileEntry("current", "LINK", 10, 1_000)));
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                var header = remote.sftpHeader();
                var detail = label(header, "prod");
                assertThat(detail).as("the connection name from PanelHost.setHeader").isNotNull();
                assertThat(detail.getIcon()).as("with the connected dot").isNotNull();
                assertThat(labels(remote.window).stream().filter("SFTP"::equals).count()).as("one title row").isEqualTo(1);
                var view = remote.sftp();
                view.setSize(220, 520);
                StagedRemoteTunnelsTest.layout(view);
                assertThat(names(view)).as("the path as crumb buttons").contains("srv", "app");
                var big = row(view, "big.iso");
                var size = label((Container) big, "999.9 GB");
                assertThat(size).as("the size cell").isNotNull();
                assertThat(size.getWidth()).as("a 220 px rail keeps the size whole").isGreaterThanOrEqualTo(size.getPreferredSize().width);
                var bounds = javax.swing.SwingUtilities.convertRectangle(size.getParent(), size.getBounds(), view);
                assertThat(bounds.x + bounds.width).as("inside the rail").isLessThanOrEqualTo(220);
                assertThat(size.getHorizontalAlignment()).as("sizes align to the trailing edge").isEqualTo(javax.swing.SwingConstants.RIGHT);
                var readme = row(view, "readme.txt");
                assertThat(readme.getAccessibleContext().getAccessibleName()).as("named by its file name").isEqualTo("readme.txt");
                assertThat(readme.getAccessibleContext().getAccessibleDescription()).as("described by name, size and modified time")
                    .startsWith("readme.txt — 3.7 KB — ");
                var name = label((Container) readme, "readme.txt");
                assertThat(readme.getHeight()).as("a row fits its text with the one-line row padding")
                    .isGreaterThanOrEqualTo(name.getFontMetrics(name.getFont()).getHeight() + dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.ROW_VERTICAL_PADDING));
                click(readme, 1);
                press(StagedRemoteTunnelsTest.canvas(readme), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            assertThat(activated).as("Enter hands the file to the Editor").containsExactly("readme.txt");
            onEdt(() -> click(row(remote.sftp(), "big.iso"), 2));
            StagedVaultHost.settle();
            assertThat(activated).as("a double click does too").containsExactly("readme.txt", "big.iso");
            onEdt(() -> assertThat(selection(remote)).as("the click selected the row").containsExactly("big.iso"));

            var id = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
            String title = "a-very-long-folder-name-for-photos (340 items) → production-server:/srv/app/uploads/2026";
            var close = new AtomicReference<Component>();
            remote.quietTransfers();
            onEdt(() -> remote.transferRows(List.<Object[]>of(new Object[] {id, title, "58%", 0.58, false})));
            StagedVaultHost.settle();
            onEdt(() -> {
                var view = remote.sftp();
                StagedRemoteTunnelsTest.layout(view);
                assertThat(labels(view)).contains("Transfers", "58%");
                close.set(StagedRemoteTunnelsTest.accessible(view, "Cancel transfer"));
                assertThat(close.get()).as("the row's close button").isNotNull();
                var row = row(view, "↑ " + title);
                assertThat(row.getAccessibleContext().getAccessibleDescription()).as("the full title and paths in the tooltip")
                    .contains(title, "/fixture/", "58%");
                var titleLabel = label((Container) row, "↑ " + title);
                assertThat(titleLabel.getWidth()).as("the long title truncates at the rail's width").isLessThan(titleLabel.getPreferredSize().width);
                remote.transferRows(List.<Object[]>of(new Object[] {id, title, "59%", 0.59, false}));
            });
            StagedVaultHost.settle();
            onEdt(() -> {
                var view = remote.sftp();
                StagedRemoteTunnelsTest.layout(view);
                assertThat(labels(view)).contains("59%");
                assertThat(StagedRemoteTunnelsTest.accessible(view, "Cancel transfer")).as("a refresh keeps the row's components, so focus stays")
                    .isSameAs(close.get());
                remote.transferRows(List.of());
            });
            StagedVaultHost.settle();
            onEdt(() -> assertThat(labels(remote.sftp())).as("no transfers, no strip").doesNotContain("Transfers"));
            assertThat(remote.problems).isEmpty();
        }
    }

    /**
     * Review fix round 1. I1: as the old table's component popup did, a right click in an empty folder (outside every row)
     * opens the folder's menu on the selection (Up, New folder…, Upload folder… enabled) and selects nothing. M4: the old
     * 24 pt table test, in the real host: with the application's font enlarged, rows grow to fit their text.
     */
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void anEmptyFolderOffersItsMenuAndRowsGrowWithAnEnlargedFont() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            remote.openSftp();
            onEdt(() -> remote.sftpListing("prod", "/srv/empty", List.of()));
            StagedVaultHost.settle();
            onEdt(() -> {
                var view = remote.sftp();
                view.setSize(260, 520);
                StagedRemoteTunnelsTest.layout(view);
                var files = (javax.swing.JScrollPane) StagedVaultHost.named(view, "sftp.files");
                var viewport = files.getViewport();
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK,
                    20, Math.max(1, viewport.getHeight() / 2), 1, true, MouseEvent.BUTTON3));
                var popup = (javax.swing.JPopupMenu) files.getClientProperty(dev.jasper.ui.swing.KitOwnership.CONTEXT_MENU);
                var enabled = new ArrayList<String>();
                for (var item : popup.getComponents()) if (item instanceof javax.swing.JMenuItem entry && entry.isEnabled()) enabled.add(entry.getText());
                assertThat(enabled).as("the empty folder's menu").containsExactly("Up", "New folder…", "Upload folder…");
                assertThat(selection(remote)).as("a context press selects nothing").isEmpty();
            });
            // The application's font setting is a defaultFont/Label.font override, which kit views follow live.
            Object previous = AppContractTest.onEdtValue(() -> javax.swing.UIManager.get("Label.font"));
            Object previousDefault = AppContractTest.onEdtValue(() -> javax.swing.UIManager.get("defaultFont"));
            try {
                onEdt(() -> {
                    remote.sftpListing("prod", "/srv/app", List.of(remote.fileEntry("readme.txt", "FILE", 3_700, 1_000)));
                    var large = new javax.swing.plaf.FontUIResource(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 24));
                    javax.swing.UIManager.put("defaultFont", large);
                    javax.swing.UIManager.put("Label.font", large);
                });
                StagedVaultHost.settle();
                onEdt(() -> {
                    var view = remote.sftp();
                    view.setSize(260, 520);
                    StagedRemoteTunnelsTest.layout(view);
                    var readme = row(view, "readme.txt");
                    var name = label((Container) readme, "readme.txt");
                    assertThat(name.getFont().getSize2D()).as("the body text follows the enlarged font").isGreaterThanOrEqualTo(20f);
                    assertThat(readme.getHeight()).as("the row grows to fit it with room")
                        .isGreaterThanOrEqualTo(name.getFontMetrics(name.getFont()).getHeight() + 8);
                });
            } finally { onEdt(() -> { javax.swing.UIManager.put("Label.font", previous); javax.swing.UIManager.put("defaultFont", previousDefault); }); }
            assertThat(remote.problems).isEmpty();
        }
    }

    /** New folder and Items already exist keep baseline f16ae32f keys through the real kit components. */
    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void theSftpDialogsKeepTheirKeys() throws Exception {
        try (var remote = new StagedRemoteHost(root, false)) {
            var created = new CopyOnWriteArrayList<String>();
            var events = new CopyOnWriteArrayList<String>();
            var folder = new AtomicReference<PluginDialog>();
            onEdt(() -> folder.set(mount(remote, "New folder", "dev.jasper.remote.ui.sftp.FolderNamePanel",
                new Class<?>[] {UiKit.class, Function.class, Runnable.class},
                (Function<String, Optional<String>>) name -> { created.add(name); return name.contains("/") ? Optional.of("Enter one folder name") : Optional.empty(); },
                (Runnable) () -> events.add("closed"))));
            var surface = remote.awaitSurface("New folder");
            awaitNamed(surface, "new-folder.name.input");
            onEdt(() -> {
                type(surface, "new-folder.name.input", "a/b");
                press(require(surface, "new-folder.create-button"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            assertThat(created).as("Create is no default button: Enter on it creates nothing").isEmpty();
            onEdt(() -> press(require(surface, "new-folder.name.input"), KeyEvent.VK_ENTER, '\n'));
            StagedVaultHost.settle();
            assertThat(created).as("Enter in the name field creates, as its action listener did").containsExactly("a/b");
            onEdt(() -> {
                assertThat(labels(surface.holder())).as("the refusal shows").contains("Enter one folder name");
                press(require(surface, "new-folder.name.input"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            });
            StagedVaultHost.settle();
            assertThat(events).as("Escape closes").containsExactly("closed");
            onEdt(() -> folder.get().close());

            var existing = new AtomicReference<PluginDialog>();
            onEdt(() -> existing.set(mount(remote, "Items already exist", "dev.jasper.remote.ui.transfers.ExistingItemsPanel",
                new Class<?>[] {UiKit.class, String.class, Consumer.class, Runnable.class},
                "\"report.pdf\" already exists in prod:/srv.", (Consumer<Object>) choice -> events.add("chose " + choice), (Runnable) () -> events.add("cancelled"))));
            var ask = remote.awaitSurface("Items already exist");
            awaitNamed(ask, "existing.replace");
            onEdt(() -> {
                assertThat(labels(ask.holder())).contains("\"report.pdf\" already exists in prod:/srv.");
                press(require(ask, "existing.replace"), KeyEvent.VK_ENTER, '\n');
            });
            StagedVaultHost.settle();
            assertThat(events).as("Enter replaces nothing: no default button").containsExactly("closed");
            onEdt(() -> press(require(ask, "existing.replace"), KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
            StagedVaultHost.settle();
            assertThat(events).as("Escape cancels").containsExactly("closed", "cancelled");
            onEdt(() -> ((AbstractButton) require(ask, "existing.skip")).doClick(0));
            StagedVaultHost.settle();
            assertThat(events).containsExactly("closed", "cancelled", "chose SKIP");
            onEdt(() -> existing.get().close());
            assertThat(remote.problems).isEmpty();
        }
    }

    /** Mounts a Remote kit form (built through its constructor, as the plugin builds it) on a plugin dialog and shows it. */
    static PluginDialog mount(StagedRemoteHost remote, String title, String type, Class<?>[] parameters, Object... arguments) {
        try {
            var context = remote.context();
            var handle = context.terminals().window(remote.window.id()).orElseThrow();
            var dialog = context.windows().dialog(new DialogSpec(title, handle, false));
            var constructor = remote.loader.loadClass(type).getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            var values = new ArrayList<Object>(); values.add(context.kit()); values.addAll(List.of(arguments));
            Object panel = constructor.newInstance(values.toArray());
            var view = panel.getClass().getDeclaredMethod("view"); view.setAccessible(true);
            dialog.setContent((UiView) view.invoke(panel));
            dialog.show();
            return dialog;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static String name(Object entry) {
        try { return (String) entry.getClass().getMethod("name").invoke(entry); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The live explorer's selection, by name; UI thread. */
    static List<String> selection(StagedRemoteHost remote) {
        try {
            Object panel = remote.sftpPanel();
            return ((List<?>) panel.getClass().getMethod("selection").invoke(panel)).stream().map(StagedRemoteSftpTest::name).toList();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The kit row (a table row is named by its first column) whose accessible name is {@code name}. */
    static Component row(Container root, String name) {
        var found = findRow(root, name);
        if (found == null) throw new AssertionError("no row " + name);
        return found;
    }

    private static Component findRow(Container root, String name) {
        for (var child : root.getComponents()) {
            if (child.getClass().getName().endsWith("InteractiveRows$Row") && name.equals(child.getAccessibleContext().getAccessibleName())) return child;
            if (child instanceof Container nested) { var found = findRow(nested, name); if (found != null) return found; }
        }
        return null;
    }

    static JLabel label(Container root, String text) {
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return label;
            if (child instanceof Container nested) { var found = label(nested, text); if (found != null) return found; }
        }
        return null;
    }

    /** A plain left click (or double click) in the middle of the row, clear of its checkbox, as the pointer delivers it. */
    static void click(Component row, int count) {
        int x = row.getWidth() / 2, y = Math.max(1, row.getHeight() / 2);
        for (int n = 1; n <= count; n++)
            for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED})
                row.dispatchEvent(new MouseEvent(row, id, System.currentTimeMillis(), id == MouseEvent.MOUSE_CLICKED ? 0 : InputEvent.BUTTON1_DOWN_MASK,
                    x, y, n, false, MouseEvent.BUTTON1));
    }
}
