package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Owner-package access to the SFTP explorer for plugin tests in other packages: each call is the event a user's gesture
 * reports on the kit view (a click on a row reports {@code Selected}, Enter or a double click the table's activate
 * action, a menu item its action). Every call hops to the EDT itself ({@link Kits#edt}), so tests may call these from the
 * test thread while loopback servers answer.
 */
public final class SftpTestAccess {
    private SftpTestAccess() { }

    /** The explorer once the SFTP panel was opened in the main window. */
    public static SftpPanel panel(SftpUi sftp) throws Exception { return Kits.value(sftp::panel); }

    public static List<String> rows(SftpPanel panel) throws Exception { return Kits.value(panel::rowNames); }
    public static String directory(SftpPanel panel) throws Exception { return Kits.value(panel::directory); }
    public static List<FileEntry> selection(SftpPanel panel) throws Exception { return Kits.value(panel::selection); }
    /** Every visible text the panel shows (status, result, transfers), in document order. */
    public static List<String> texts(SftpPanel panel) throws Exception { return Kits.value(() -> Kits.texts(panel.view())); }

    /** A click on the named rows (with Shift or the checkboxes for several). */
    public static void select(SftpPanel panel, String... names) throws Exception {
        Kits.edt(() -> {
            var ids = new LinkedHashSet<String>();
            for (String name : names) {
                if (!panel.rowNames().contains(name)) throw new AssertionError("no row " + name);
                ids.add(SftpPanel.ROW + name);
            }
            Kits.emit(panel.view(), new UiEvent.Selected(SftpPanel.FILES, ids));
        });
    }

    /** Every row selected (the table's select all). */
    public static void selectAll(SftpPanel panel) throws Exception { select(panel, Kits.value(panel::rowNames).toArray(String[]::new)); }

    /** Enter (or a double click) on the named row. */
    public static void enter(SftpPanel panel, String name) throws Exception {
        select(panel, name);
        Kits.edt(() -> Kits.press(panel.view(), table(panel.view()).activateAction()));
    }

    /** Upload files: the Upload button's own click. */
    public static void upload(SftpPanel panel) throws Exception {
        Kits.edt(() -> Kits.press(panel.view(), Kits.node(panel.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).action()));
    }

    /** The disconnected state's Choose Session menu: its items' labels, in order, as the menu lists them. */
    public static List<String> sessions(SftpPanel panel) throws Exception {
        return Kits.value(() -> Kits.node(panel.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class).items().stream().map(MenuItem::label).toList());
    }

    /** Chooses the named saved host from the disconnected state's Choose Session menu, as a click on its item reports. */
    public static void chooseSession(SftpPanel panel, String label) throws Exception {
        Kits.edt(() -> {
            var item = item(Kits.node(panel.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class).items(), label);
            if (!item.enabled()) throw new AssertionError(label + " is disabled");
            Kits.press(panel.view(), item.action());
        });
    }

    /** The host chooser's Browse button, which browses the host it has selected (the first saved host at first). */
    public static void browse(UiView chooser) throws Exception { Kits.edt(() -> Kits.click(chooser, HostChooser.BROWSE)); }

    /** The Upload button's menu item with this label, which acts on the selection. */
    public static void menu(SftpPanel panel, String label) throws Exception {
        Kits.edt(() -> {
            var item = item(Kits.node(panel.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).items(), label);
            if (!item.enabled()) throw new AssertionError(label + " is disabled");
            Kits.press(panel.view(), item.action());
        });
    }

    /** The Upload button's menu labels. */
    public static List<String> menuLabels(SftpPanel panel) throws Exception {
        return Kits.value(() -> Kits.node(panel.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).items().stream()
            .filter(item -> !item.separator()).map(MenuItem::label).toList());
    }

    /** The named row's own context menu item with this label (a right click never selects the row). */
    public static void rowMenu(SftpPanel panel, String name, String label) throws Exception {
        Kits.edt(() -> {
            var item = item(row(panel.view(), name).contextActions().orElseThrow(), label);
            if (!item.enabled()) throw new AssertionError(label + " is disabled on " + name);
            Kits.press(panel.view(), item.action());
        });
    }

    static Nodes.Table table(UiView view) { return Kits.node(view, SftpPanel.FILES, Nodes.Table.class); }

    static TableRow row(UiView view, String name) {
        return table(view).rows().stream().filter(row -> row.id().equals(SftpPanel.ROW + name)).findFirst()
            .orElseThrow(() -> new AssertionError("no row " + name));
    }

    static MenuItem item(List<MenuItem> items, String label) {
        return items.stream().filter(item -> !item.separator() && item.label().equals(label)).findFirst()
            .orElseThrow(() -> new AssertionError("no menu item " + label));
    }
}
