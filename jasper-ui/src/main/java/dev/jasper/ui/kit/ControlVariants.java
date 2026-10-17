package dev.jasper.ui.kit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The value of each of the kit file's 14 {@code [controls]} points (Enterprise presentation spec 5.2), each two-valued:
 * the default look's value first, the classic look's second.
 *
 * @param toggle a Toggle: a switch or a checkbox
 * @param exclusive an EXCLUSIVE choice: a segment track or a row of radios
 * @param tabs a TABS choice: a segment track or folder tabs
 * @param group Section and Card: a section label over a card, or a titled box
 * @param formLabel FormRow labels: plain, or a colon-ended label column
 * @param empty an Empty state: with its tile, or text only
 * @param table a Table: plain, or a header band with zebra rows
 * @param tree a Tree: plain, or classic disclosure and category rows
 * @param stepper a STEPPER or SLIDER field: a stepper, or a spinner
 * @param orderedList an ORDERED list: per-row commands, or a side column
 * @param buttons buttons and dropdown faces: raised, or classic gradients
 * @param tag a Chip: a borderless tint, or a boxed uppercase label
 * @param route a ROUTE strip: icon tiles, or edged cells
 * @param prompt a PROMPT form: the icon tile and title, or the plain classic prompt
 */
public record ControlVariants(Toggle toggle, Exclusive exclusive, Tabs tabs, Group group, FormLabel formLabel, Empty empty, Table table,
                              Tree tree, Stepper stepper, OrderedList orderedList, Buttons buttons, Tag tag, Route route, Prompt prompt) {
    /** A point's value, named in a kit file by {@link #key()}. */
    public sealed interface Variant permits Toggle, Exclusive, Tabs, Group, FormLabel, Empty, Table, Tree, Stepper, OrderedList, Buttons,
        Tag, Route, Prompt {
        /** The value's name in a kit file: the constant's name in lower case. */
        default String key() { return ((Enum<?>) this).name().toLowerCase(Locale.ROOT); }
    }
    public enum Toggle implements Variant { SWITCH, CHECKBOX }
    public enum Exclusive implements Variant { SEGMENTS, RADIOS }
    public enum Tabs implements Variant { SEGMENTS, FOLDER }
    public enum Group implements Variant { CARD, TITLED_BOX }
    public enum FormLabel implements Variant { PLAIN, COLON }
    public enum Empty implements Variant { TILE, TEXT }
    public enum Table implements Variant { PLAIN, CLASSIC }
    public enum Tree implements Variant { PLAIN, CLASSIC }
    public enum Stepper implements Variant { STEPPER, SPINNER }
    public enum OrderedList implements Variant { INLINE, SIDE_COLUMN }
    public enum Buttons implements Variant { RAISED, CLASSIC }
    public enum Tag implements Variant { TINT, BOXED }
    public enum Route implements Variant { TILES, EDGED }
    public enum Prompt implements Variant { TILE, PLAIN }

    /** Every point at the default look's value. */
    public static final ControlVariants DEFAULT = of(new boolean[14]);
    /** Every point at the classic look's value. */
    public static final ControlVariants CLASSIC = of(all());

    /** Every point is required. */
    public ControlVariants {
        Objects.requireNonNull(toggle, "toggle"); Objects.requireNonNull(exclusive, "exclusive"); Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(group, "group"); Objects.requireNonNull(formLabel, "formLabel"); Objects.requireNonNull(empty, "empty");
        Objects.requireNonNull(table, "table"); Objects.requireNonNull(tree, "tree"); Objects.requireNonNull(stepper, "stepper");
        Objects.requireNonNull(orderedList, "orderedList"); Objects.requireNonNull(buttons, "buttons"); Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(route, "route"); Objects.requireNonNull(prompt, "prompt");
    }

    /** The variants with each point at its classic value where {@code classic} is true, in spec 5.2's order. */
    public static ControlVariants of(boolean... classic) {
        if (classic.length != 14) throw new IllegalArgumentException("Expected 14 control points, got " + classic.length);
        return new ControlVariants(pick(Toggle.values(), classic[0]), pick(Exclusive.values(), classic[1]), pick(Tabs.values(), classic[2]),
            pick(Group.values(), classic[3]), pick(FormLabel.values(), classic[4]), pick(Empty.values(), classic[5]),
            pick(Table.values(), classic[6]), pick(Tree.values(), classic[7]), pick(Stepper.values(), classic[8]),
            pick(OrderedList.values(), classic[9]), pick(Buttons.values(), classic[10]), pick(Tag.values(), classic[11]),
            pick(Route.values(), classic[12]), pick(Prompt.values(), classic[13]));
    }

    /** Which points hold their classic value, in spec 5.2's order. */
    public boolean[] flags() {
        var values = values();
        boolean[] flags = new boolean[values.size()];
        for (int i = 0; i < flags.length; i++) flags[i] = ((Enum<?>) values.get(i)).ordinal() == 1;
        return flags;
    }

    /** Every point at its other value. */
    public ControlVariants complement() {
        boolean[] flags = flags();
        for (int i = 0; i < flags.length; i++) flags[i] = !flags[i];
        return of(flags);
    }

    /** The points' values in spec 5.2's order. */
    public List<Variant> values() {
        return List.of(toggle, exclusive, tabs, group, formLabel, empty, table, tree, stepper, orderedList, buttons, tag, route, prompt);
    }

    /** The points' values as a kit file names them, in spec 5.2's order. */
    public List<String> keys() { return values().stream().map(Variant::key).toList(); }

    private static <E> E pick(E[] values, boolean classic) { return values[classic ? 1 : 0]; }

    private static boolean[] all() {
        boolean[] all = new boolean[14];
        java.util.Arrays.fill(all, true);
        return all;
    }
}
