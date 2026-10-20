package dev.jasper.sdk.testing;

import dev.jasper.sdk.settings.*;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Descriptor projection only. The fake draft, edits, validation and reload are independent of rendering. */
final class KitSettingRows {
    private KitSettingRows() {}

    /** {@code defaultValue}: the setting's effective default (the look's or the declared one), which Reset returns to. */
    static Nodes.Node row(SettingSpec<?> spec, Object defaultValue, Object value, String invalidText, String error, String entry) {
        String id = "settings:" + spec.key();
        var state = new ControlState(true, true, false, error);
        Nodes.Node input;
        if (spec.kind() instanceof SettingKind.Bool) {
            input = new Nodes.Toggle(id, state, spec.label(), (Boolean) value, id, false);
        } else if (spec.kind() instanceof SettingKind.OneOf<?> choice) {
            var options = new ArrayList<Option>();
            String selected = "";
            for (var option : choice.choices()) {
                options.add(new Option(option.token(), option.displayName(), Optional.empty()));
                if (option.value().equals(value)) selected = option.token();
            }
            input = new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options, selected, spec.label(), id);
        } else if (spec.kind() instanceof SettingKind.StringList) {
            // As the host: the generated pages' ordered list, then an entry and Add below it.
            input = list(id, state, spec.label(), (List<?>) value, entry);
        } else if (spec.kind() instanceof SettingKind.Text && spec.editor().orElse(null) instanceof EditorHint.Font font) {
            // As the host: free text over the installed families, which filter as you type.
            List<String> families = font.monospaced() ? InstalledFonts.MONOSPACED : InstalledFonts.ALL;
            var options = new ArrayList<Option>();
            for (int i = 0; i < families.size(); i++) options.add(new Option("family-" + i, families.get(i), Optional.empty()));
            input = new Nodes.Choice(id, state, Nodes.ChoiceKind.SEARCHABLE, options, "", spec.label(), "", "",
                Optional.of(new Nodes.ChoiceSearch(0, "No installed font matches", Optional.of(invalidText != null ? invalidText : String.valueOf(value)))));
        } else {
            var numeric = spec.kind() instanceof SettingKind.IntegerRange range ? Optional.of(new Nodes.Numeric(range.min(), range.max(), 1, 0))
                : spec.kind() instanceof SettingKind.NumberRange range ? Optional.of(new Nodes.Numeric(range.min(), range.max(), 0.1, 2)) : Optional.<Nodes.Numeric>empty();
            var kind = numeric.isPresent() ? Nodes.FieldKind.NUMBER
                : spec.editor().orElse(null) instanceof EditorHint.File ? Nodes.FieldKind.PATH : Nodes.FieldKind.TEXT;
            String text = invalidText != null ? invalidText : String.valueOf(value);
            // As the host: a bounded integer is the Settings stepper, a bounded decimal a number field, both with the unit.
            // As the host: a path ends with Browse.
            input = new Nodes.Field(id, state, kind, text, numeric.isPresent() ? spec.unit() : "", spec.label(),
                kind == Nodes.FieldKind.PATH ? id + ":browse" : "", numeric,
                spec.kind() instanceof SettingKind.IntegerRange ? Nodes.FieldPresentation.STEPPER
                    : kind == Nodes.FieldKind.TEXT && spec.editor().orElse(null) instanceof EditorHint.Technical ? Nodes.FieldPresentation.TECHNICAL : Nodes.FieldPresentation.STANDARD);
        }
        // As the host: the settings control slot, then the icon-only Reset, shown while the value differs from the default.
        // As the host: Reset also shows while an unparsed edit or its error is pending; decimals compare at float precision.
        boolean differs = value instanceof Number a && defaultValue instanceof Number b && !(a instanceof Integer && b instanceof Integer)
            ? a.floatValue() != b.floatValue() : !Objects.equals(value, defaultValue);
        var reset = new Nodes.Button(id + ":reset", new ControlState(differs || invalidText != null || !error.isEmpty(), true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + spec.label() + " to default");
        return new Nodes.SettingRow(id + ":row", state, spec.label(), spec.description(),
            new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(input, reset)), false);
    }

    /** As the host: the generated pages' ordered-list editor, its rows {@code item-N}, then the entry and Add. */
    private static Nodes.Stack list(String id, ControlState state, String label, List<?> items, String pending) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        var rows = new ArrayList<ListItem>();
        for (int i = 0; i < items.size(); i++)
            rows.add(new ListItem("item-" + i, Optional.empty(),
                List.of(new Nodes.Text(id + ":item-" + i, ControlState.READY, items.get(i).toString(), Nodes.TextRole.BODY)), List.of(), List.of()));
        var entry = new Nodes.Field(id + ":new", plain, Nodes.FieldKind.TEXT, pending, "Add…", "Add to " + label, pending.isBlank() ? "" : id + ":add");
        var add = new Nodes.Button(id + ":add", new ControlState(state.visible(), state.enabled() && !pending.isBlank(), state.busy(), ""), "Add", "add",
            Nodes.ButtonKind.SECONDARY, id + ":add", "");
        return new Nodes.Stack(id + ":editor", plain, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, Set.of(), "", List.of()),
            new Nodes.Stack(id + ":entry", plain, Nodes.LayoutRole.INLINE, List.of(entry, add))));
    }

    /** The installed families a font setting's row offers, read once: all, and those that draw 'i' and 'W' alike. */
    private static final class InstalledFonts {
        static final List<String> ALL = List.of(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        static final List<String> MONOSPACED = monospaced();

        private static List<String> monospaced() {
            var graphics = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                var fixed = new ArrayList<String>();
                for (String family : ALL) {
                    var metrics = graphics.getFontMetrics(new java.awt.Font(family, java.awt.Font.PLAIN, 12));
                    if (metrics.charWidth('i') == metrics.charWidth('W')) fixed.add(family);
                }
                return List.copyOf(fixed);
            } finally {
                graphics.dispose();
            }
        }
    }

    static boolean matches(SettingSpec<?> spec, String query) {
        var words = Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) return false;
        String text = String.join(" ", spec.label(), spec.description(), String.join(" ", spec.keywords())).toLowerCase(Locale.ROOT);
        return words.stream().allMatch(text::contains);
    }

    static ViewSpec project(ViewSpec spec, UnaryOperator<Nodes.Node> replace) {
        return new ViewSpec(project(spec.root(), replace));
    }

    /** Explicit SDK container reconstruction is projection, not settings behavior. No reflective API. */
    private static Nodes.Node project(Nodes.Node node, UnaryOperator<Nodes.Node> replace) {
        var replacement = replace.apply(node);
        if (replacement != node) return replacement;
        UnaryOperator<Nodes.Node> child = value -> project(value, replace);
        return switch (node) {
            case Nodes.Stack n -> new Nodes.Stack(n.id(), n.state(), n.role(), n.children().stream().map(child).toList(), n.cancelAction());
            case Nodes.Card n -> new Nodes.Card(n.id(), n.state(), n.kind(), n.children().stream().map(child).toList());
            case Nodes.Split n -> new Nodes.Split(n.id(), n.state(), n.kind(), n.children().stream().map(child).toList());
            case Nodes.Section n -> new Nodes.Section(n.id(), n.state(), n.title(), child.apply(n.body()));
            case Nodes.Form n -> new Nodes.Form(n.id(), n.state(), n.kind(), n.title(), n.hero().map(child), n.sections().stream().map(child).toList(), n.preview().map(child), n.actions(), n.cancelAction(), n.submission(), n.footnote());
            case Nodes.FormRow n -> new Nodes.FormRow(n.id(), n.state(), n.label(), n.caption(), child.apply(n.control()), n.trailing().map(child), n.highlighted());
            case Nodes.SettingRow n -> new Nodes.SettingRow(n.id(), n.state(), n.title(), n.description(), child.apply(n.control()), n.dependent(), n.highlighted(), n.titleNote());
            case Nodes.Header n -> new Nodes.Header(n.id(), n.state(), n.title(), n.count(), n.context().map(child), n.primary(), n.overflow());
            case Nodes.Panel n -> new Nodes.Panel(n.id(), n.state(), (Nodes.Header) child.apply(n.header()), n.searchOrNavigation().map(child), child.apply(n.body()), n.footer().map(child));
            case Nodes.Empty n -> new Nodes.Empty(n.id(), n.state(), child.apply(n.icon()), n.title(), n.description(), n.actions().stream().map(child).toList());
            default -> node;
        };
    }
}
