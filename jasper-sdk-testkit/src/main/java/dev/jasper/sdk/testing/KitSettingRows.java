package dev.jasper.sdk.testing;

import dev.jasper.sdk.settings.*;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Descriptor projection only. The fake draft, edits, validation and reload are independent of rendering. */
final class KitSettingRows {
    private KitSettingRows() {}

    static Nodes.Node row(SettingSpec<?> spec, Object value, String invalidText, String error) {
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
        } else {
            var numeric = spec.kind() instanceof SettingKind.IntegerRange range ? Optional.of(new Nodes.Numeric(range.min(), range.max(), 1, 0))
                : spec.kind() instanceof SettingKind.NumberRange range ? Optional.of(new Nodes.Numeric(range.min(), range.max(), 0.1, 2)) : Optional.<Nodes.Numeric>empty();
            var kind = numeric.isPresent() ? Nodes.FieldKind.NUMBER : value instanceof List<?> ? Nodes.FieldKind.TEXTAREA
                : spec.editor().orElse(null) instanceof EditorHint.File ? Nodes.FieldKind.PATH : Nodes.FieldKind.TEXT;
            String text = invalidText != null ? invalidText : value instanceof List<?> items
                ? String.join("\n", items.stream().map(Object::toString).toList()) : String.valueOf(value);
            // As the host: a bounded integer is the Settings stepper, a bounded decimal a number field, both with the unit.
            input = new Nodes.Field(id, state, kind, text, numeric.isPresent() ? spec.unit() : "", spec.label(), "", numeric,
                spec.kind() instanceof SettingKind.IntegerRange ? Nodes.FieldPresentation.STEPPER : Nodes.FieldPresentation.STANDARD);
        }
        // As the host: the settings control slot, then the icon-only Reset, shown while the value differs from the default.
        // As the host: Reset also shows while an unparsed edit or its error is pending; decimals compare at float precision.
        boolean differs = value instanceof Number a && spec.defaultValue() instanceof Number b && !(a instanceof Integer && b instanceof Integer)
            ? a.floatValue() != b.floatValue() : !Objects.equals(value, spec.defaultValue());
        var reset = new Nodes.Button(id + ":reset", new ControlState(differs || invalidText != null || !error.isEmpty(), true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + spec.label() + " to default");
        return new Nodes.SettingRow(id + ":row", state, spec.label(), spec.description(),
            new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(input, reset)), false);
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
            case Nodes.Form n -> new Nodes.Form(n.id(), n.state(), n.kind(), n.title(), n.hero().map(child), n.sections().stream().map(child).toList(), n.preview().map(child), n.actions(), n.cancelAction());
            case Nodes.FormRow n -> new Nodes.FormRow(n.id(), n.state(), n.label(), n.caption(), child.apply(n.control()), n.trailing().map(child), n.highlighted());
            case Nodes.SettingRow n -> new Nodes.SettingRow(n.id(), n.state(), n.title(), n.description(), child.apply(n.control()), n.dependent(), n.highlighted(), n.titleNote());
            case Nodes.Header n -> new Nodes.Header(n.id(), n.state(), n.title(), n.count(), n.context().map(child), n.primary(), n.overflow());
            case Nodes.Panel n -> new Nodes.Panel(n.id(), n.state(), (Nodes.Header) child.apply(n.header()), n.searchOrNavigation().map(child), child.apply(n.body()), n.footer().map(child));
            case Nodes.Empty n -> new Nodes.Empty(n.id(), n.state(), child.apply(n.icon()), n.title(), n.description(), n.actions().stream().map(child).toList());
            default -> node;
        };
    }
}
