package dev.jasper.app.palette;

import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import java.util.Optional;
/** Test-only component input and observation; callers run on the EDT. */
public final class PaletteTestSupport {
    public static javax.swing.JList<PaletteRow> resultList(CommandPalette palette) { return palette.resultList(); }
    public static javax.swing.JLabel chip(CommandPalette palette) { return palette.chip(); }
    public static javax.swing.JLabel footer(CommandPalette palette) { return palette.footer(); }
    public static javax.swing.JLabel sectionLabel(CommandPalette palette) { return palette.sectionLabel(); }
    public static java.util.List<javax.swing.JTextField> stepFields(CommandPalette palette) { return palette.stepFields(); }
    public static int stepFocusIndex(CommandPalette palette) { return palette.stepFocusIndex(); }
    public static javax.swing.JLabel stepError(CommandPalette palette) { return palette.stepError(); }
    public static void selectRow(CommandPalette palette, String id) { palette.selectRow(id); }
    public static void selectRelative(CommandPalette palette, int delta) { palette.selectRelative(delta); }
    public static void executeNumber(CommandPalette palette, int number) { palette.executeNumber(number); }
    public static void executeSelected(CommandPalette palette, int verb) { palette.executeSelected(verb); }
    public static void setResults(CommandPalette palette, java.util.List<PaletteRow> rows, String label, String selectionId) { palette.setResults(rows, label, selectionId); }
    public static void executeSelected(CommandPalette palette) { palette.executeSelected(); }
    public static String scopeFor(dev.jasper.app.commands.ActionId action) { return PaletteKeyRouter.scopeFor(action); }

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
    public static List<PaletteRow> rows(CommandsScope scope, List<dev.jasper.app.commands.Command> commands) { return scope.rows(commands); }
}
