package dev.jasper.app.palette;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.model.Nodes;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/** Test-only component input and observation over the palette's kit views; callers run on the EDT. */
public final class PaletteTestSupport {
    private PaletteTestSupport() {}

    /** {@code palette} as the card it is in the default look. */
    static CardView card(JComponent palette) { return (CardView) palette; }

    /** The mounted kit query input (kit components are named after their node). */
    public static JTextField query(JComponent palette) {
        return KitTestSupport.require(card(palette).queryView(), CardView.QUERY + ".input", JTextField.class);
    }

    /** The query the palette holds, which the field shows once a host normalization (a typed scope prefix) applies. */
    public static String queryText(JComponent palette) { return card(palette).queryText(); }

    /**
     * Runs the events already queued behind the current one, on the EDT, as the next turn of the event loop would: a
     * kit field applies a host's normalization of the text being typed (a scope prefix leaving the query) only then,
     * because Swing forbids changing a document while it notifies.
     */
    public static void settle() {
        var loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        java.awt.EventQueue.invokeLater(loop::exit);
        if (!loop.enter()) throw new AssertionError("The event loop did not run");
    }

    /** The results list's mounted scroll pane and its canvas of row components. */
    public static JScrollPane resultsScroll(JComponent palette) {
        return KitTestSupport.require(card(palette).resultsView(), CardView.RESULTS, JScrollPane.class);
    }

    public static JComponent resultsCanvas(JComponent palette) { return (JComponent) resultsScroll(palette).getViewport().getView(); }

    /** The results view, which carries the list's accessible name ("All", or the chip's scope) and description. */
    public static JComponent resultsView(JComponent palette) { return card(palette).resultsView(); }

    /** The results list descriptor the palette last rendered. */
    public static Nodes.ItemList resultsSpec(JComponent palette) { return (Nodes.ItemList) card(palette).resultsView().spec().root(); }

    /** How many times the host replaced the list. */
    public static long revision(JComponent palette) { return card(palette).revision(); }

    /** Every entry as text: {@code # Label} for a header, {@code scopeId/rowId} for a row, {@code more:scopeId}. */
    public static List<String> entries(JComponent palette) {
        var texts = new ArrayList<String>();
        for (PaletteEntry entry : card(palette).entries()) texts.add(describe(entry));
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
    public static List<PaletteRow> rows(JComponent palette) {
        var rows = new ArrayList<PaletteRow>();
        for (PaletteEntry entry : card(palette).entries()) if (entry instanceof PaletteEntry.Item item) rows.add(item.row());
        return rows;
    }

    public static int rowCount(JComponent palette) { return rows(palette).size(); }
    public static PaletteRow rowAt(JComponent palette, int index) { return rows(palette).get(index); }

    /** The selected row, or null when nothing or a "More in" entry is selected. */
    public static PaletteRow selectedRow(JComponent palette) {
        return card(palette).selectedEntry() instanceof PaletteEntry.Item item ? item.row() : null;
    }

    /** The selected row's position among the rows, or -1. */
    public static int selectedRowIndex(JComponent palette) {
        int selected = card(palette).selectedIndex();
        var entries = card(palette).entries();
        for (int i = 0, row = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof PaletteEntry.Item)) continue;
            if (i == selected) return row;
            row++;
        }
        return -1;
    }

    /** The entry index of the {@code index}th row. */
    public static int entryIndex(JComponent palette, int index) {
        var entries = card(palette).entries();
        for (int i = 0, row = 0; i < entries.size(); i++)
            if (entries.get(i) instanceof PaletteEntry.Item && row++ == index) return i;
        throw new IndexOutOfBoundsException(index);
    }

    /** The mounted kit row component of entry {@code entry}, found through its title label (named after the entry). */
    public static Component entryComponent(JComponent palette, int entry) {
        var title = KitTestSupport.find(resultsCanvas(palette), "entry." + entry + ".title", JLabel.class);
        if (title == null) return null;
        Component row = title;
        while (row != null && row.getParent() != resultsCanvas(palette)) row = row.getParent();
        return row;
    }

    /** The canvas bounds of the {@code index}th row's mounted kit row. */
    public static Rectangle rowBounds(JComponent palette, int index) {
        var row = entryComponent(palette, entryIndex(palette, index));
        if (row == null) throw new AssertionError("Row " + index + " is not mounted");
        return row.getBounds();
    }

    /** The chip's label, or null while no chip is set. */
    public static String chipText(JComponent palette) { return card(palette).chipText(); }
    public static JComponent chip(JComponent palette) { return KitTestSupport.require(card(palette).chipView(), CardView.CHIP, JComponent.class); }
    public static boolean backspace(PaletteController controller) { return controller.backspacePressed(); }
    public static String placeholder(JComponent palette) { return card(palette).placeholder(); }
    public static JPanel hintBar(JComponent palette) { return card(palette).hintBar(); }
    public static JPanel fieldRow(JComponent palette) { return card(palette).fieldRow(); }
    public static String hint(JComponent palette) { return card(palette).hintText(); }

    /** The text of every visible label and button the hint bar shows, in component order (empty texts skipped). */
    public static List<String> hintBarTexts(JComponent palette) {
        var texts = new ArrayList<String>();
        visibleTexts(card(palette).hintBar(), texts);
        return texts;
    }

    private static void visibleTexts(Container container, List<String> texts) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JLabel label && label.getText() != null && !label.getText().isEmpty()) texts.add(label.getText());
            else if (child instanceof javax.swing.AbstractButton button && !button.getText().isEmpty()) texts.add(button.getText());
            else if (child instanceof Container nested) visibleTexts(nested, texts);
        }
    }

    public static List<String> hintActions(JComponent palette) { return card(palette).hintActionTexts(); }

    /** Clicks the {@code index}th hint verb's mounted kit link. */
    public static void clickHintAction(JComponent palette, int index) {
        KitTestSupport.require(card(palette).hintView(), CardView.VERB + index, javax.swing.AbstractButton.class).doClick(0);
    }

    public static int itemHeight(JComponent palette) { return card(palette).itemHeight(); }
    public static String stepTitle(JComponent palette) { return card(palette).stepTitleText(); }

    /** The step's mounted kit inputs, in order. */
    public static List<javax.swing.text.JTextComponent> stepFields(JComponent palette) {
        var fields = new ArrayList<javax.swing.text.JTextComponent>();
        for (int i = 0; i < card(palette).stepFieldCount(); i++) fields.add(card(palette).stepInput(i));
        return fields;
    }

    public static int stepFocusIndex(JComponent palette) { return card(palette).stepFocusIndex(); }
    /** The step's error text, or null while none shows. */
    public static String stepError(JComponent palette) { return card(palette).stepErrorText(); }
    public static void selectRow(JComponent palette, String id) { card(palette).selectRow(id); }
    public static void selectRelative(JComponent palette, int delta) { card(palette).selectRelative(delta); }
    public static void executeSelected(JComponent palette, int verb) { card(palette).executeSelected(verb); }
    public static void executeSelected(JComponent palette) { card(palette).executeSelected(); }

    /** Shows {@code rows} as {@code scope}'s own tab would, selecting {@code selectionId}. */
    public static void setRows(JComponent palette, PaletteScope scope, List<PaletteRow> rows, String selectionId) {
        card(palette).setEntries(rows.stream().<PaletteEntry>map(row -> new PaletteEntry.Item(scope, row)).toList(),
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
