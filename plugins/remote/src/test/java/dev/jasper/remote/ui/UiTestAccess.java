package dev.jasper.remote.ui;

import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.List;
import java.util.Set;
import static dev.jasper.remote.testsupport.Kits.*;

/**
 * Owner-package helpers that drive the host dialogs and the Sessions panel through their kit views, as the user would:
 * typing reports {@code Edited}, a click reports the button's action. Call on the EDT.
 */
public final class UiTestAccess {
    private UiTestAccess() {}
    public static void groupName(GroupNamePanel panel, String text) { type(panel.view(), GroupNamePanel.NAME, text); }
    public static void saveGroup(GroupNamePanel panel) { click(panel.view(), GroupNamePanel.RENAME_BUTTON); }

    public static void cancel(ConnectionPanel panel) { click(panel.view(), ConnectionPanel.CANCEL); }
    public static void retry(ConnectionPanel panel) { click(panel.view(), ConnectionPanel.RETRY); }
    public static String connectionStatus(ConnectionPanel panel) { return panel.message(); }

    public static void trust(HostKeyPanel panel) { click(panel.view(), HostKeyPanel.TRUST); }

    public static List<HostRows.Row> rows(HostsPanel panel) { return panel.rows(); }
    public static ListItem row(HostsPanel panel, int index) { return node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class).items().get(index); }
    /** Runs the row's own context menu item with this label. */
    public static void menu(HostsPanel panel, int index, String label) { press(panel.view(), menuItem(row(panel, index), label).action()); }
    public static List<String> menu(HostsPanel panel, int index) { return menuLabels(row(panel, index)); }
    /** A plain single click on the row: a group toggles. */
    public static void toggle(HostsPanel panel, int index) {
        String id = row(panel, index).id();
        emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(id))); emit(panel.view(), new UiEvent.Clicked(HostsPanel.ROWS, id));
    }
    /** Enter (or a double click) on the row. */
    public static void activate(HostsPanel panel, int index) {
        emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(row(panel, index).id())));
        press(panel.view(), node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class).activateAction());
    }
    public static String cardCredential(HostsPanel panel) { return node(panel.view(), HostsPanel.CARD_CREDENTIAL, Nodes.Text.class).text(); }

    public static void name(HostEditor editor, String text) { type(editor.view(), HostEditor.NAME, text); }
    public static void hostname(HostEditor editor, String text) { type(editor.view(), HostEditor.HOSTNAME, text); }
    public static void username(HostEditor editor, String text) { type(editor.view(), HostEditor.USERNAME, text); }
    public static void agentAuth(HostEditor editor) { emit(editor.view(), new UiEvent.Selected(HostEditor.AUTH, Set.of(HostEditor.AGENT))); }
    public static void save(HostEditor editor) { click(editor.view(), HostEditor.SAVE); }

    public static void chooseCredential(ImportPanel panel, int row) { emit(panel.view(),new UiEvent.Action(ImportPanel.choose(row))); }
    public static void refreshImport(ImportPanel panel) { click(panel.view(), ImportPanel.REFRESH); }
    public static void cancelImport(ImportPanel panel) { click(panel.view(), ImportPanel.CANCEL); }
    public static boolean importChecked(ImportPanel panel, int row) { return node(panel.view(), ImportPanel.check(row), Nodes.Toggle.class).checked(); }
    public static void importCheck(ImportPanel panel, int row, boolean checked) { emit(panel.view(), new UiEvent.Toggled(ImportPanel.check(row), checked)); }
    public static String importStatus(ImportPanel panel) { return panel.status(); }
    public static void importButton(ImportPanel panel) { click(panel.view(), ImportPanel.IMPORT); }
}
