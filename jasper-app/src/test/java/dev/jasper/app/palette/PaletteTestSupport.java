package dev.jasper.app.palette;

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
}
