package dev.jasper.app.palette;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.lifecycle.Subscription;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Test-only component input and observation; callers run on the EDT. */
public final class PaletteTestSupport {
    private PaletteTestSupport() {}

    public static JList<PaletteEntry> entryList(CommandPalette palette) { return palette.entryList(); }

    /** Every entry as text: {@code # Label} for a header, {@code scopeId/rowId} for a row, {@code more:scopeId}. */
    public static List<String> entries(CommandPalette palette) {
        var texts = new ArrayList<String>();
        var model = palette.entryList().getModel();
        for (int i = 0; i < model.getSize(); i++) texts.add(describe(model.getElementAt(i)));
        return texts;
    }

    public static String describe(PaletteEntry entry) {
        return switch (entry) {
            case PaletteEntry.Header header -> "# " + header.label();
            case PaletteEntry.Item item -> item.scope().id() + "/" + item.row().id();
            case PaletteEntry.More more -> "more:" + more.scope().id();
        };
    }

    /** The rows shown, in order, without headers or "More in" entries. */
    public static List<PaletteRow> rows(CommandPalette palette) {
        var rows = new ArrayList<PaletteRow>();
        var model = palette.entryList().getModel();
        for (int i = 0; i < model.getSize(); i++) if (model.getElementAt(i) instanceof PaletteEntry.Item item) rows.add(item.row());
        return rows;
    }

    public static int rowCount(CommandPalette palette) { return rows(palette).size(); }
    public static PaletteRow rowAt(CommandPalette palette, int index) { return rows(palette).get(index); }

    /** The selected row, or null when nothing or a "More in" entry is selected. */
    public static PaletteRow selectedRow(CommandPalette palette) {
        return palette.selectedEntry() instanceof PaletteEntry.Item item ? item.row() : null;
    }

    /** The selected row's position among the rows, or -1. */
    public static int selectedRowIndex(CommandPalette palette) {
        var model = palette.entryList().getModel();
        int selected = palette.entryList().getSelectedIndex();
        for (int i = 0, row = 0; i < model.getSize(); i++) {
            if (!(model.getElementAt(i) instanceof PaletteEntry.Item)) continue;
            if (i == selected) return row;
            row++;
        }
        return -1;
    }

    /** The list-cell bounds of the {@code index}th row. */
    public static Rectangle rowBounds(CommandPalette palette, int index) {
        var model = palette.entryList().getModel();
        for (int i = 0, row = 0; i < model.getSize(); i++)
            if (model.getElementAt(i) instanceof PaletteEntry.Item && row++ == index) return palette.entryList().getCellBounds(i, i);
        throw new IndexOutOfBoundsException(index);
    }

    public static List<String> tabIds(CommandPalette palette) { return palette.tabIds(); }
    public static String selectedTabId(CommandPalette palette) { return palette.selectedTabId(); }
    public static void clickTab(CommandPalette palette, String id) { palette.clickTab(id); }
    public static String placeholder(CommandPalette palette) { return (String) palette.queryField().getClientProperty("JTextField.placeholderText"); }
    public static JPanel hintBar(CommandPalette palette) { return palette.hintBar(); }
    public static JPanel tabStrip(CommandPalette palette) { return palette.tabStrip(); }
    public static JPanel fieldRow(CommandPalette palette) { return (JPanel) palette.queryField().getParent(); }
    public static String hint(CommandPalette palette) { return palette.hintText(); }
    public static List<String> hintActions(CommandPalette palette) { return palette.hintActionTexts(); }
    public static void clickHintAction(CommandPalette palette, int index) { palette.clickHintAction(index); }
    public static int itemHeight(CommandPalette palette) { return palette.itemHeight(); }
    public static JLabel stepTitle(CommandPalette palette) { return palette.stepTitle(); }
    public static List<JTextField> stepFields(CommandPalette palette) { return palette.stepFields(); }
    public static int stepFocusIndex(CommandPalette palette) { return palette.stepFocusIndex(); }
    public static JLabel stepError(CommandPalette palette) { return palette.stepError(); }
    public static void selectRow(CommandPalette palette, String id) { palette.selectRow(id); }
    public static void selectRelative(CommandPalette palette, int delta) { palette.selectRelative(delta); }
    public static void executeSelected(CommandPalette palette, int verb) { palette.executeSelected(verb); }
    public static void executeSelected(CommandPalette palette) { palette.executeSelected(); }

    /** Shows {@code rows} as {@code scope}'s own tab would, selecting {@code selectionId}. */
    public static void setRows(CommandPalette palette, PaletteScope scope, List<PaletteRow> rows, String selectionId) {
        palette.setEntries(rows.stream().<PaletteEntry>map(row -> new PaletteEntry.Item(scope, row)).toList(),
            selectionId == null ? null : PaletteEntry.key(scope, selectionId), "No matching " + scope.label());
    }

    public static String scopeFor(ActionId action) { return PaletteKeyRouter.scopeFor(action); }

    /** A scope with one verb and two fixed rows; {@code shortcutOrNull} names a contributed action. */
    public static PaletteScope scope(String id, String shortcutOrNull) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("one", "One")); }
            @Override public Optional<String> shortcutActionId() { return Optional.ofNullable(shortcutOrNull); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                return new PaletteResults(List.of(PaletteRow.of("alpha", "Alpha"), PaletteRow.of("beta", "Beta")), null, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    /** Rows the Commands scope builds for {@code commands}, for tests that seed the list directly. */
    public static List<PaletteRow> rows(CommandsScope scope, List<Command> commands) { return scope.rows(commands); }
}
