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
            var kind = spec.kind() instanceof SettingKind.IntegerRange ? Nodes.FieldKind.NUMBER : value instanceof List<?> ? Nodes.FieldKind.TEXTAREA
                : spec.editor().orElse(null) instanceof EditorHint.File ? Nodes.FieldKind.PATH : Nodes.FieldKind.TEXT;
            String text = invalidText != null ? invalidText : value instanceof List<?> items
                ? String.join("\n", items.stream().map(Object::toString).toList()) : String.valueOf(value);
            var numeric = spec.kind() instanceof SettingKind.IntegerRange range
                ? Optional.of(new Nodes.Numeric(range.min(), range.max(), 1, 0)) : Optional.<Nodes.Numeric>empty();
            input = new Nodes.Field(id, state, kind, text, "", spec.label(), "", numeric);
        }
        var reset = new Nodes.Button(id + ":reset", ControlState.READY, "Reset", "", Nodes.ButtonKind.GHOST, id + ":reset", "Reset to default");
        return new Nodes.SettingRow(id + ":row", state, spec.label(), spec.description(),
            new Nodes.Stack(id + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(input, reset)), false);
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
            case Nodes.SettingRow n -> new Nodes.SettingRow(n.id(), n.state(), n.title(), n.description(), child.apply(n.control()), n.dependent(), n.highlighted());
            case Nodes.Header n -> new Nodes.Header(n.id(), n.state(), n.title(), n.count(), n.context().map(child), n.primary(), n.overflow());
            case Nodes.Panel n -> new Nodes.Panel(n.id(), n.state(), (Nodes.Header) child.apply(n.header()), n.searchOrNavigation().map(child), child.apply(n.body()), n.footer().map(child));
            case Nodes.Empty n -> new Nodes.Empty(n.id(), n.state(), child.apply(n.icon()), n.title(), n.description(), n.actions().stream().map(child).toList());
            default -> node;
        };
    }
}
