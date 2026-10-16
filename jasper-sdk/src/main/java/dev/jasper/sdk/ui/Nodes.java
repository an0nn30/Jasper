package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Objects;

/** Immutable, semantic view vocabulary; no pixel dimensions, delegates or secret values. */
public final class Nodes {
    private Nodes() {}
    public sealed interface Node permits Text, Icon, Button, MenuButton, StatusDot, Field, Secret, Choice, PickerMenu, Toggle, Chip, Progress, Strength, Stack, FormRow, SettingRow, Card, Section, Empty, ItemList, Header, Panel, Form, Split, Table, Tree, Breadcrumbs, Custom {
        String id();
        ControlState state();
    }
    /** The MONO roles mark data values (hosts, ports, paths, commands), drawn in the data face. */
    public enum TextRole { PROMPT_TITLE, DETAIL_TITLE, WINDOW_TITLE, PANEL_TITLE, BODY, BODY_STRONG, LABEL, DESCRIPTION, SUPPORTING, CAPTION, SECTION_LABEL, COUNT, CHIP, MONO, MONO_SMALL, MONO_CAPTION }
    public enum Hue { BLUE, GREEN, AMBER, PURPLE, NEUTRAL, NONE }
    /** PROMPT is the large icon: a prompt's header, a detail heading or a sheet's title. */
    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT }
    /** GHOST is a quiet button. */
    public enum ButtonKind { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE_GHOST, LINK }
    public enum StatusKind { SUCCESS, WARNING, DANGER, IDLE }
    public enum FieldKind { TEXT, SEARCH, PORT, PATH, TEXTAREA, NUMBER }
    /** EXCLUSIVE is a choice among two to four options, all of them visible. */
    public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }
    public enum CardKind { FORM, SETTINGS, MIXED }
    /** NONE keeps rows readable and scrollable but removes row selection, activation and the canvas focus stop.
     * Independently interactive inline controls keep their behavior. NONE requires empty selectedIds. */
    public enum SelectionMode { NONE, SINGLE, MULTIPLE }
    /**
     * RESULTS is a one-line result list driven by an external field (a command palette): it never takes focus, the host
     * moves its selection, a left press activates a row, and fixed row and heading heights keep it steady. SINGLE,
     * DOUBLE and COMPACT are density hints.
     */
    public enum ListKind { SINGLE, DOUBLE, COMPACT, ORDERED, RESULTS }
    /**
     * PROMPT is a short, focused question (unlock, create) and PROMPT_COMPACT its compact form. WINDOW has exactly one
     * full-bleed content section, no hero, and a shared pinned footer.
     */
    public enum FormKind { PROMPT, PROMPT_COMPACT, SHEET, FULL, SETTINGS, WINDOW }
    /**
     * SUPPORTING keeps leading adornments natural-sized beside a wrapping final text/body child. ROUTE is a read-only
     * route preview (a tunnel's path): a bordered strip on the input surface whose children are its steps, left to
     * right, with the kit's arrow before every step after the first. A step is a stack whose leading Icon the kit shows
     * on a category tile in its hue, its other children (Text) being the step's lines. The steps take equal columns,
     * each later column opening with the arrow, and a step's lines truncate within its column.
     */
    public enum LayoutRole { VERTICAL, INLINE, ACTIONS, PROMPT_HEADER, DETAIL, FOOTER, NAVIGATION, SUPPORTING, JOINED_ACTIONS, SETTINGS_PAGE, SETTINGS_CONTROLS, SETTINGS_TRAILING, SETTINGS_GROUP_HEADER, SETTINGS_FOOTER, SETTINGS_NAVIGATION, ROUTE }
    /**
     * THREE_COLUMN places a manager window's rail, list and detail columns side by side; SIDEBAR_DETAIL is the same manager
     * without its rail (its last child is the detail column, the others list columns). DOCUMENT stacks them top to bottom for a document
     * surface (an editor tab): every visible child takes its preferred height except the first visible Custom or Empty
     * child, which fills the height left over; hidden children take no space. The other children are the document's bars:
     * an INLINE stack among them (directly, or as a row of a VERTICAL group) is one compact bar row whose first field or
     * text fills the width the controls after it leave (the rows of one group line their fills up), with the panel search
     * field's look for its fields and the panel action size for its buttons; a group stacks its rows without gaps and
     * shows a passive (SelectionMode.NONE) list's rows as text lines; a hairline separates neighbouring children.
     */
    public enum SplitKind { SIDEBAR_DETAIL, THREE_COLUMN, DOCUMENT }
    public enum CustomKind { EDITOR, TERMINAL, CHART, PREVIEW }
    /** A hint: STANDARD reserves the scroll bar's width; OVERLAY shows a transient thumb over the rows only while scrolling. */
    public enum ScrollPolicy { STANDARD, OVERLAY }

    /**
     * A present {@code severity} draws the text in that severity's colour (INFO the accent, WARNING and DANGER the status
     * colours), as {@link Icon#severity()} tints a glyph, instead of its role's colour. A present {@code caret} (MONO and
     * MONO_SMALL and MONO_CAPTION text only, from 0 to the text's length) draws a thin insertion bar in the text's colour after that many
     * characters, as a command line shows its cursor.
     */
    public record Text(String id, ControlState state, String text, TextRole role, Optional<Severity> severity, OptionalInt caret) implements Node {
        public Text {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(text);
            Objects.requireNonNull(role);
            Objects.requireNonNull(severity);
            Objects.requireNonNull(caret);
            if (caret.isPresent()) {
                if (role != TextRole.MONO && role != TextRole.MONO_SMALL && role != TextRole.MONO_CAPTION) throw new IllegalArgumentException("A caret belongs to monospaced text only");
                if (caret.getAsInt() < 0 || caret.getAsInt() > text.length()) throw new IllegalArgumentException("Caret outside the text");
            }
        }
        /** Text in its role's colour, without a caret. */
        public Text(String id, ControlState state, String text, TextRole role) {
            this(id, state, text, role, Optional.empty(), OptionalInt.empty());
        }
    }

    /** Message severity a glyph can carry: INFO in the accent colour, WARNING and DANGER in the status colours. */
    public enum Severity { INFO, WARNING, DANGER }

    /** A present {@code severity} tints the glyph with its colour instead of the hue. */
    public record Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName, Optional<Severity> severity) implements Node {
        public Icon {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(name, "name");
            Objects.requireNonNull(hue);
            Objects.requireNonNull(size);
            ModelChecks.label(accessibleName, "accessibleName");
            Objects.requireNonNull(severity);
        }
        public Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName) {
            this(id, state, name, hue, size, accessibleName, Optional.empty());
        }
    }

    /**
     * How a button sits in its composition. STANDARD is the kit's padded, minimum-height button. EMBEDDED is content-sized
     * for a host arrangement that supplies the spacing (for example a row of inline links): no padding, focus-ring inset,
     * minimum height or resting fill, and an icon-only embedded button draws its glyph at the row-glyph size. Keyboard
     * focus still shows, as a hairline in the focus colour inside the button's bounds.
     * CONTEXTUAL_EMBEDDED reserves the same mounted, focusable and accessible control, but paints it only while its
     * containing item row is selected, hovered or keyboard-active, or while the button itself is hovered/focused.
     * Outside an item row it behaves like EMBEDDED. It never changes action dispatch or selection. HELP is a button that
     * opens help; the default look draws it round.
     */
    public enum ButtonPresentation { STANDARD, EMBEDDED, CONTEXTUAL_EMBEDDED, HELP }

    /**
     * A present {@code iconSeverity} tints the button's glyph with that severity's colour (as {@link Icon#severity()}
     * does) at rest and on hover, instead of the kind's text colour; for example a favourite's warning star. The label
     * keeps the kind's colour. A present {@code selected} makes it a toggle button (a search option such as match case):
     * the kit paints the pressed treatment while it is true and reports its pressed state to assistive technology; a
     * click reports the Action and the owner confirms the new state in its next update. Only GHOST and SECONDARY buttons
     * take it.
     */
    public record Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip, ButtonPresentation presentation,
                         Optional<Severity> iconSeverity, Optional<Boolean> selected) implements Node {
        public Button {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.button(label, icon, tooltip);
            Objects.requireNonNull(kind);
            ModelChecks.label(action, "action");
            Objects.requireNonNull(presentation);
            Objects.requireNonNull(iconSeverity);
            Objects.requireNonNull(selected);
            if (selected.isPresent() && kind != ButtonKind.GHOST && kind != ButtonKind.SECONDARY)
                throw new IllegalArgumentException("Only ghost and secondary buttons have a pressed state");
        }
        /**
         * A button whose glyph takes its kind's colour (or {@code iconSeverity}'s) and that has no pressed state.
         */
        public Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip, ButtonPresentation presentation,
                      Optional<Severity> iconSeverity) {
            this(id, state, label, icon, kind, action, tooltip, presentation, iconSeverity, Optional.empty());
        }
        /** A button whose glyph takes its kind's colour. */
        public Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip, ButtonPresentation presentation) {
            this(id, state, label, icon, kind, action, tooltip, presentation, Optional.empty());
        }
        public Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip) {
            this(id, state, label, icon, kind, action, tooltip, ButtonPresentation.STANDARD);
        }
    }

    /** {@code presentation} as for {@link Button}; an EMBEDDED menu-only button draws no chevron of its own. */
    public record MenuButton(String id, ControlState state, String label, String icon, ButtonKind kind, String action, List<MenuItem> items, String tooltip, ButtonPresentation presentation) implements Node {
        public MenuButton {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.button(label, icon, tooltip);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(action);
            items = List.copyOf(items);
            ModelChecks.unique(items, MenuItem::id);
            if (items.isEmpty()) throw new IllegalArgumentException("Menu must have items");
            Objects.requireNonNull(presentation);
        }
        public MenuButton(String id, ControlState state, String label, String icon, ButtonKind kind, String action, List<MenuItem> items, String tooltip) {
            this(id, state, label, icon, kind, action, items, tooltip, ButtonPresentation.STANDARD);
        }
    }

    public record StatusDot(String id, ControlState state, StatusKind kind) implements Node {
        public StatusDot {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
        }
    }

    /**
     * Bounds and shape of a NUMBER field: the kit admits only numbers of this shape and steps by {@code step} with the
     * arrow keys, clamped to the bounds. The domain still validates the value and reports problems through state.
     */
    public record Numeric(double min, double max, double step, int decimals) {
        public Numeric {
            if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step)) throw new IllegalArgumentException("Numeric bounds must be finite");
            if (min > max) throw new IllegalArgumentException("Numeric minimum exceeds maximum");
            if (step <= 0) throw new IllegalArgumentException("Numeric step must be positive");
            if (decimals < 0 || decimals > 6) throw new IllegalArgumentException("Numeric decimals must be in 0..6");
        }
    }

    /**
     * How a field sits in its composition. STANDARD shows the kit label (a SEARCH field never does) over a filled input.
     * EMBEDDED leaves the visible label to the host's own arrangement (the label stays the accessible name). TRANSPARENT
     * is EMBEDDED on a host surface that is the field's backdrop: no input fill, the outline and focus ring kept.
     * TECHNICAL marks a data value (a host, port, path or command): standard geometry in the data face.
     * PLAIN_PATH keeps PATH semantics and Browse with no leading folder; other kinds reject it. STEPPER and SLIDER
     * (NUMBER only) are hints for how the number is offered.
     */
    public enum FieldPresentation { STANDARD, EMBEDDED, TRANSPARENT, TECHNICAL, PLAIN_PATH, STEPPER, SLIDER }

    /**
     * A NUMBER field states its {@link Numeric} shape; other kinds have none. {@code alternateAction} is what Shift+Enter
     * in a single-line field runs (a find field's Previous); {@code cancelAction} is what Escape in the field runs. Blank
     * passes either key on, to an enclosing stack or form. A nonblank {@code listId} names an ItemList in the same view:
     * Up, Down, Page Up and Page Down in the field move that list's selection (reporting its Selected) while the focus
     * stays in the field, and Enter stays {@code trailingAction}. A TEXTAREA takes neither {@code alternateAction} nor
     * {@code listId}, and a NUMBER field (whose arrows step it) takes no {@code listId}.
     */
    public record Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                        String alternateAction, String cancelAction, String listId) implements Node {
        public Field {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
            Objects.requireNonNull(hint);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(trailingAction);
            Objects.requireNonNull(numeric);
            Objects.requireNonNull(presentation);
            if ((kind == FieldKind.NUMBER) != numeric.isPresent()) throw new IllegalArgumentException("Numeric bounds belong to NUMBER fields only, which require them");
            if ((presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER) && kind != FieldKind.NUMBER) throw new IllegalArgumentException("Numeric presentation requires NUMBER");
            if (presentation == FieldPresentation.PLAIN_PATH && kind != FieldKind.PATH) throw new IllegalArgumentException("PLAIN_PATH requires PATH");
            Objects.requireNonNull(alternateAction); Objects.requireNonNull(cancelAction); Objects.requireNonNull(listId);
            if (!listId.isBlank()) ModelChecks.id(listId);
            if (kind == FieldKind.TEXTAREA && (!alternateAction.isBlank() || !listId.isBlank()))
                throw new IllegalArgumentException("A multiline field takes no Shift+Enter action or list");
            if (kind == FieldKind.NUMBER && !listId.isBlank()) throw new IllegalArgumentException("A number field's arrow keys step its value, not a list");
        }
        /** A field whose Shift+Enter and Escape pass on and that moves no list. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, presentation, "", "", "");
        }
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, FieldPresentation.STANDARD);
        }
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction) {
            this(id, state, kind, value, hint, label, trailingAction, Optional.empty());
        }
    }

    /**
     * INPUT is a masked single-line entry with a reveal control; CONFIRMATION is the same editable input without its own
     * reveal control, following its reveal group. DETAIL a read-only stored value with reveal and optional
     * copy. MULTILINE is an editable, wrapping secure-note area over the same wipeable storage, shown in clear, with no
     * reveal or copy control, so it takes neither a reveal group nor a copy action.
     * DISCREET_INPUT reserves reveal geometry and shows the control when nonempty or focused within.
     */
    public enum SecretKind { INPUT, DISCREET_INPUT, CONFIRMATION, DETAIL, MULTILINE }

    /**
     * A present {@code submitAction} (INPUT or CONFIRMATION) is what Enter in the field runs: the field keeps the key, as a single-line
     * field with a trailing action does, so the containing form's primary is not pressed. Blank passes Enter on.
     */
    public record Secret(String id, ControlState state, String hint, String label, String revealGroup, SecretKind kind, String copyAction, String submitAction) implements Node {
        public Secret {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(hint);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(revealGroup); Objects.requireNonNull(kind); Objects.requireNonNull(copyAction); Objects.requireNonNull(submitAction);
            if (kind == SecretKind.MULTILINE && (!revealGroup.isEmpty() || !copyAction.isEmpty()))
                throw new IllegalArgumentException("A multiline secret has no reveal group or copy action");
            if (kind != SecretKind.INPUT && kind != SecretKind.DISCREET_INPUT && kind != SecretKind.CONFIRMATION && !submitAction.isEmpty())
                throw new IllegalArgumentException("Only an input secret takes a submit action");
        }
        public Secret(String id, ControlState state, String hint, String label, String revealGroup, SecretKind kind, String copyAction) {
            this(id, state, hint, label, revealGroup, kind, copyAction, "");
        }
        public Secret(String id, ControlState state, String hint, String label, String revealGroup) {
            this(id,state,hint,label,revealGroup,SecretKind.INPUT,"");
        }
    }

    /**
     * Type-to-filter configuration of a SEARCHABLE choice. The first {@code pinned} options always show; {@code noMatches}
     * is the line shown when typing matches nothing. A present {@code entry} makes the value free text (options become
     * suggestions): the kit reports typed or picked text as {@link UiEvent.Edited}, and the choice has no selected id.
     * A nonempty {@code placeholder} is shown, in the placeholder role, while a free entry is empty (for example what a
     * blank value means); it is never a value. It requires free entry.
     */
    public record ChoiceSearch(int pinned, String noMatches, Optional<String> entry, String placeholder) {
        public ChoiceSearch {
            if (pinned < 0) throw new IllegalArgumentException("Negative pinned count");
            ModelChecks.label(noMatches, "noMatches");
            Objects.requireNonNull(entry);
            Objects.requireNonNull(placeholder);
            if (!placeholder.isEmpty() && entry.isEmpty()) throw new IllegalArgumentException("A placeholder requires free entry");
        }
        public ChoiceSearch(int pinned, String noMatches, Optional<String> entry) { this(pinned, noMatches, entry, ""); }
    }

    /** A hint: the full form and inline picker share one renderer at two densities. */
    public enum PickerDensity { DEFAULT, COMPACT }
    /**
     * Grant-free display metadata. Empty primary means the placeholder face. A PICKER's face is its whole control; a
     * DROPDOWN may carry one too (a rich closed face, such as a saved host's tile, name and endpoint) while its popup
     * still lists the options and reports Selected; its owner keeps the face describing the selected option.
     */
    public record PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density) {
        public PickerFace { Objects.requireNonNull(primary);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(density); }
    }
    /** One selectable popup row; its id is also the Action emitted on explicit acceptance. */
    public enum PickerItemKind { VALUE, UTILITY }
    public record PickerItem(String id,String label,String caption,String icon,Hue hue,PickerItemKind kind) {
        public PickerItem { ModelChecks.id(id);Objects.requireNonNull(label);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(kind); }
        public PickerItem(String id,String label,String caption,String icon,Hue hue){this(id,label,caption,icon,hue,PickerItemKind.VALUE);}
    }
    /** Rich searchable menu: checked value and keyboard-active row are distinct. Edited/Selected name this node;
     * Enter/pointer acceptance emits the row id or footerAction; Escape emits cancelAction. */
    public record PickerMenu(String id,ControlState state,String query,String placeholder,List<PickerItem> items,
            String checkedId,String activeId,String noMatches,String footerLabel,String footerAction,String cancelAction) implements Node {
        public PickerMenu { ModelChecks.id(id);Objects.requireNonNull(state);Objects.requireNonNull(query);Objects.requireNonNull(placeholder);
            items=List.copyOf(items);ModelChecks.unique(items,PickerItem::id);Objects.requireNonNull(checkedId);Objects.requireNonNull(activeId);
            Objects.requireNonNull(noMatches);ModelChecks.label(footerLabel,"footerLabel");ModelChecks.id(footerAction);ModelChecks.id(cancelAction);
            if(items.stream().anyMatch(item -> item.id().equals(footerAction)))throw new IllegalArgumentException("Footer id collides with item"); }
    }

    public record Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon, Optional<ChoiceSearch> search, Optional<PickerFace> face) implements Node {
        public Choice {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            options = List.copyOf(options);
            Objects.requireNonNull(selectedId);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(action);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(search);Objects.requireNonNull(face);
            if(face.isPresent() && kind!=ChoiceKind.PICKER && kind!=ChoiceKind.DROPDOWN)throw new IllegalArgumentException("Rich face requires PICKER or DROPDOWN");
            if (kind != ChoiceKind.PICKER && !icon.isEmpty()) throw new IllegalArgumentException("Icon cue requires PICKER");
            if ((kind == ChoiceKind.SEARCHABLE) != search.isPresent()) throw new IllegalArgumentException("Search configuration belongs to SEARCHABLE choices only, which require it");
            if (search.isPresent() && search.get().pinned() > options.size()) throw new IllegalArgumentException("More pinned entries than options");
            if (search.isPresent() && search.get().entry().isPresent() && !selectedId.isEmpty()) throw new IllegalArgumentException("Free entry owns the value; no option is selected");
            ModelChecks.unique(options, Option::id);
        }
        /** Compatibility constructor, without rich picker metadata. */
        public Choice(String id,ControlState state,ChoiceKind kind,List<Option> options,String selectedId,String label,String action,String icon,Optional<ChoiceSearch> search) {
            this(id,state,kind,options,selectedId,label,action,icon,search,Optional.empty());
        }
        /** A choice without search; SEARCHABLE requires the full constructor. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon) {
            this(id, state, kind, options, selectedId, label, action, icon, Optional.empty());
        }
        /** Existing pickers retain the key cue; use the full constructor for an explicit cue or none. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action) {
            this(id, state, kind, options, selectedId, label, action, kind == ChoiceKind.PICKER ? "key" : "");
        }
    }

    /**
     * A boolean, drawn as a switch or a checkbox; {@code compact} is a size hint. {@code tooltip} is the control's literal
     * tooltip (blank shows the label); a validation error still takes precedence. {@code pending} means a change is in
     * flight while the domain confirms it (for example a tunnel starting): the control stays operable and reports the
     * opposite of {@code checked}, which is always the confirmed state, and the default look shows the switch's knob
     * midway on the warning colour.
     */
    public record Toggle(String id, ControlState state, String label, boolean checked, String action, boolean compact, String tooltip, boolean pending) implements Node {
        public Toggle {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(label, "label");
            ModelChecks.label(action, "action");
            Objects.requireNonNull(tooltip);
        }
        /** A toggle that is not pending. */
        public Toggle(String id, ControlState state, String label, boolean checked, String action, boolean compact, String tooltip) {
            this(id, state, label, checked, action, compact, tooltip, false);
        }
        public Toggle(String id, ControlState state, String label, boolean checked, String action, boolean compact) {
            this(id, state, label, checked, action, compact, "");
        }
    }

    public record Chip(String id, ControlState state, String label, Hue hue) implements Node {
        public Chip {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(hue);
        }
    }

    public record Progress(String id, ControlState state, double fraction, String label) implements Node {
        public Progress {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (!Double.isFinite(fraction) || (fraction != -1 && (fraction < 0 || fraction > 1))) throw new IllegalArgumentException("Progress must be -1 or in 0..1");
        }
    }

    public record Strength(String id, ControlState state, int filled, String label) implements Node {
        public Strength {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (filled < 0 || filled > 4) throw new IllegalArgumentException("Strength must be in 0..4");
        }
    }

    /**
     * A nonblank {@code cancelAction} is what Escape runs from any focused control inside the stack that does not take
     * Escape itself (a field's own {@code cancelAction}, an open popup), as an inline find bar closes; blank passes it on.
     */
    public record Stack(String id, ControlState state, LayoutRole role, List<Node> children, String cancelAction) implements Node {
        public Stack {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(role);
            children = List.copyOf(children);
            Objects.requireNonNull(cancelAction);
        }
        /** A stack that leaves Escape to its children and ancestors. */
        public Stack(String id, ControlState state, LayoutRole role, List<Node> children) {
            this(id, state, role, children, "");
        }
    }

    /** {@code label} carries no colon: the presentation punctuates it. {@code highlighted} marks a row matching the host's current search. */
    public record FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing, boolean highlighted) implements Node {
        public FormRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            Objects.requireNonNull(caption);
            Objects.requireNonNull(control);
            Objects.requireNonNull(trailing);
        }
        public FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing) {
            this(id, state, label, caption, control, trailing, false);
        }
    }

    /** {@code highlighted} marks a row matching the host's current search. */
    public record SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted, String titleNote) implements Node {
        public SettingRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description); Objects.requireNonNull(titleNote);
            Objects.requireNonNull(control);
        }
        /** An ordinary setting row without a trailing title annotation. */
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted) {
            this(id, state, title, description, control, dependent, highlighted, "");
        }
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent) {
            this(id, state, title, description, control, dependent, false, "");
        }
    }

    public record Card(String id, ControlState state, CardKind kind, List<Node> children) implements Node {
        public Card {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
        }
    }

    /** With a {@link Card} as its body, a titled group. */
    public record Section(String id, ControlState state, String title, Node body) implements Node {
        public Section {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(body);
        }
    }

    /** Nothing to show yet, or a disconnected state. {@code icon} is drawn only by presentations that draw an icon tile. */
    public record Empty(String id, ControlState state, Node icon, String title, String description, List<Node> actions) implements Node {
        public Empty {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(icon);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description);
            actions = List.copyOf(actions);
        }
    }

    /**
     * Virtual row collection. MULTIPLE selects rows with Shift (a range) and Cmd/Ctrl or Space (one row), without checkboxes; addAction is a footer, never a data item. A present
     * {@code visibleRows} is how many selectable rows the viewport shows before it scrolls (headings above them add their
     * own height); absent keeps the kit default. Headings are never selected and ORDERED lists have none.
     * A present {@code enterAction} is what Enter on the focused row reports (after selecting it), so a double click and
     * Enter can differ; blank keeps Enter as {@code activateAction}. A present {@code deleteAction} is what Delete and
     * Backspace on the focused row report (an unselected row is selected first); blank leaves those keys to the host.
     * A plain left single click on a row also reports {@link UiEvent.Clicked} after its selection. RESULTS lists take
     * neither action. {@code detailsAction}, {@code copyAction} and {@code cancelAction} bind D, platform Copy and
     * Escape only while the row canvas has focus; blank actions pass the key to an enclosing control.
     *
     * @param id stable list id
     * @param state visibility and interaction state
     * @param kind row presentation
     * @param items ordered rows
     * @param selectedIds selected row ids
     * @param activateAction action for ordinary activation
     * @param contextActions default row menu
     * @param selectionMode selection behavior
     * @param addAction optional footer button
     * @param visibleRows requested viewport row count
     * @param enterAction focused-row Enter action
     * @param deleteAction focused-row Delete action
     * @param detailsAction focused-row D action
     * @param copyAction focused-row platform Copy action
     * @param cancelAction focused-row Escape action
     */
    public record ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction, String detailsAction, String copyAction, String cancelAction) implements Node {
        public ItemList {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            items = List.copyOf(items);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(addAction); Objects.requireNonNull(visibleRows);
            if (selectionMode == SelectionMode.NONE && !selectedIds.isEmpty()) throw new IllegalArgumentException("Passive rows cannot be selected");
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            if (visibleRows.isPresent() && visibleRows.getAsInt() < 1) throw new IllegalArgumentException("Visible rows must be positive");
            Objects.requireNonNull(enterAction); Objects.requireNonNull(deleteAction);
            Objects.requireNonNull(detailsAction); Objects.requireNonNull(copyAction); Objects.requireNonNull(cancelAction);
            if (kind == ListKind.RESULTS && (!enterAction.isEmpty() || !deleteAction.isEmpty() || !detailsAction.isEmpty() || !copyAction.isEmpty() || !cancelAction.isEmpty()))
                throw new IllegalArgumentException("RESULTS rows never take focus, so they have no row key actions");
            ModelChecks.unique(items, ListItem::id);
            ModelChecks.unique(contextActions, MenuItem::id);
            for (var item : items) if (item.heading()) {
                if (kind == ListKind.ORDERED) throw new IllegalArgumentException("Ordered lists have no headings");
                if (selectedIds.contains(item.id())) throw new IllegalArgumentException("A heading is never selected");
            }
        }
        /** A list without D, Copy or Escape row actions. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, enterAction, deleteAction, "", "", "");
        }
        /** A list whose Enter reports its activate action and which has no Delete action. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, "", "");
        }
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, OptionalInt.empty());
        }
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, SelectionMode.SINGLE, Optional.empty());
        }
    }

    /** Optional total collection count: zero hides the panel count/navigation, absent leaves navigation declared visibility intact. */
    public record Header(String id, ControlState state, String title, OptionalInt count, Optional<Node> context, Optional<Node> primary, List<MenuItem> overflow) implements Node {
        public Header {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(count);
            Objects.requireNonNull(context);
            Objects.requireNonNull(primary);
            overflow = List.copyOf(overflow);
            ModelChecks.unique(overflow, MenuItem::id);
            if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
            if (primary.isPresent() && !(primary.get() instanceof Button || primary.get() instanceof MenuButton)) throw new IllegalArgumentException("Primary must be Button or MenuButton");
        }
    }

    public record Panel(String id, ControlState state, Header header, Optional<Node> searchOrNavigation, Node body, Optional<Node> footer) implements Node {
        public Panel {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(header);
            Objects.requireNonNull(searchOrNavigation);
            Objects.requireNonNull(body);
            Objects.requireNonNull(footer);
        }
    }

    /** Form-level Enter behavior; NONE leaves explicit field actions and button activation intact. */
    public enum FormSubmission { PRIMARY, NONE }

    /** Form content only; cancellation uses an explicit action key, blank means none. */
    public record Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission) implements Node {
        public Form {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(hero);
            sections = List.copyOf(sections);
            Objects.requireNonNull(preview);
            actions = List.copyOf(actions);
            Objects.requireNonNull(cancelAction);
            Objects.requireNonNull(submission);
            if (kind == FormKind.WINDOW && (hero.isPresent() || sections.size() != 1))
                throw new IllegalArgumentException("WINDOW requires one body section and no hero");
            if (actions.stream().filter(button -> button.kind() == ButtonKind.PRIMARY).count() > 1)
                throw new IllegalArgumentException("Form supports at most one primary action");
        }
        /** Existing forms submit their primary action on Enter. NONE keeps explicit field actions and button activation. */
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction) {
            this(id, state, kind, title, hero, sections, preview, actions, cancelAction, FormSubmission.PRIMARY);
        }
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions) {
            this(id, state, kind, title, hero, sections, preview, actions, "");
        }
    }

    public record Split(String id, ControlState state, SplitKind kind, List<Node> children) implements Node {
        public Split {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Split needs at least two children");
        }
    }

    /** Column collection with explicit single or multiple (Shift, Cmd/Ctrl or Space) selection and an explicit scroll policy. */
    public record Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll) implements Node {
        public Table {
            ModelChecks.id(id); Objects.requireNonNull(state);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(scroll);
            if (selectionMode == SelectionMode.NONE && !selectedIds.isEmpty()) throw new IllegalArgumentException("Passive rows cannot be selected");
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            ModelChecks.unique(columns, Column::id);
            ModelChecks.unique(rows, TableRow::id);
            ModelChecks.unique(contextActions, MenuItem::id);
        }
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, ScrollPolicy.STANDARD);
        }
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, SelectionMode.SINGLE);
        }
    }

    public record Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction) implements Node {
        public Tree {
            ModelChecks.id(id); Objects.requireNonNull(state);
            roots = List.copyOf(roots);
            expandedIds = Set.copyOf(expandedIds);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
        }
    }

    /**
     * A path of crumbs that can turn into a typed path. Clicking a crumb reports {@link UiEvent.Selected} with its id;
     * a crumb's context action reports only its {@link UiEvent.Action} (it does not navigate). Clicking the free area
     * reports {@code editAction}; {@code focus(id)} focuses the field while editing. While {@code editing} the kit shows a text field holding
     * {@code value}, reports typing as {@link UiEvent.Edited}, Enter as {@code submitAction} and Escape or leaving the
     * field as {@code cancelAction}. When the crumbs do not fit, the leading ones slide out so the last stays visible.
     */
    public record Breadcrumbs(String id, ControlState state, List<Crumb> crumbs, String label, boolean editing, String value, String editAction, String submitAction, String cancelAction) implements Node {
        public Breadcrumbs {
            ModelChecks.id(id); Objects.requireNonNull(state);
            crumbs = List.copyOf(crumbs); ModelChecks.unique(crumbs, Crumb::id);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(value);
            Objects.requireNonNull(editAction); Objects.requireNonNull(submitAction); Objects.requireNonNull(cancelAction);
            if (editing && (submitAction.isBlank() || cancelAction.isBlank())) throw new IllegalArgumentException("Editing needs submit and cancel actions");
        }
    }

    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
        public Custom {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.id(contentId);
            Objects.requireNonNull(kind);
            ModelChecks.label(accessibleName, "accessibleName");
        }
    }
}
