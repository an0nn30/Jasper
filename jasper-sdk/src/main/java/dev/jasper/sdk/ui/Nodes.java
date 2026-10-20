package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Objects;

/**
 * The node types a {@link ViewSpec} is built from. A node says what a view shows and which {@link UiEvent}s it
 * reports, never how it is drawn: the host draws every node in the current look and theme, and the plugin answers
 * events with {@link UiView#update(ViewSpec)}. Nodes are immutable records with no pixel dimensions, delegates or
 * secret values. Each record checks its own components when it is constructed, and {@link ViewSpec} checks the whole
 * tree: node ids unique across the view, item references, and where a node may stand.
 */
public final class Nodes {
    private Nodes() {}
    /** One node of a {@link ViewSpec} tree: a control, a text or a container of other nodes. */
    public sealed interface Node permits Text, Icon, Button, MenuButton, StatusDot, Field, Secret, Choice, PickerMenu, Toggle, Chip, Progress, Strength, Stack, FormRow, SettingRow, Card, Section, Empty, ItemList, Header, Panel, Form, Split, Table, Tree, Breadcrumbs, Custom, Separator, Notice, Properties {
        /**
         * The node's id, never blank and unique across the whole view. The events a node reports name it by this id,
         * except a {@link UiEvent.Action}, which carries the action key the node configures. An update keeps the mounted
         * control, with its focus and caret, of a node whose id and node type stay the same, and also its kind for a
         * Button, Field, Secret or Choice, whether its presentation is STEPPER, SLIDER or another for a Field, its kind
         * and content id for a Custom, and its scroll policy for a Table. Any other node is mounted afresh.
         *
         * @return the node id
         */
        String id();
        /**
         * The node's visibility, enablement, busy state and validation error.
         *
         * @return the node's state
         */
        ControlState state();
    }
    /**
     * A text's type role, which sets its font and colour. PROMPT_TITLE, DETAIL_TITLE, WINDOW_TITLE and PANEL_TITLE head a
     * prompt, a detail column, a window and a panel; BODY is ordinary text and BODY_STRONG its emphasis; CHIP is the
     * small type of a chip's label. LABEL and DESCRIPTION take the secondary text colour, CAPTION, COUNT, SECTION_LABEL,
     * SUPPORTING and MONO_CAPTION the tertiary, and the other roles the primary. The MONO roles mark data values (hosts,
     * ports, paths, commands), drawn in the data face.
     */
    public enum TextRole { PROMPT_TITLE, DETAIL_TITLE, WINDOW_TITLE, PANEL_TITLE, BODY, BODY_STRONG, LABEL, DESCRIPTION, SUPPORTING, CAPTION, SECTION_LABEL, COUNT, CHIP, MONO, MONO_SMALL, MONO_CAPTION }
    /**
     * A category colour from the theme's hues: it tints an icon's glyph, a chip, a swatch and the category tile some
     * compositions set an icon on. NONE is no category: an Icon's glyph takes the secondary text colour, and a list
     * row's leading RAIL or PROMPT icon, and a picker row's or face's icon, stands without the tile it otherwise sits on.
     */
    public enum Hue { BLUE, GREEN, AMBER, PURPLE, NEUTRAL, NONE }
    /**
     * An icon's size role. CAPTION stands beside caption text, CONTROL beside a control's or a row's text, RAIL in a rail
     * or a list row, and EMPTY in an {@link Empty} state; a list row's leading RAIL or PROMPT icon sits on a tile in its
     * hue unless the hue is NONE. PROMPT is the large icon: a prompt's header, a detail heading or a sheet's title.
     * SWATCH (since 0.18.0) is a solid square of the icon's hue at caption size, such as a protocol's colour beside its
     * name; its hue is not NONE and it takes no severity, and its name stays a valid icon name.
     */
    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT, SWATCH }
    /**
     * A button's treatment. PRIMARY is the surface's main action: a {@link Form}'s actions hold at most one, which the
     * form's Enter presses (see {@link FormSubmission}). SECONDARY is a standard button, GHOST a quiet one,
     * DESTRUCTIVE_GHOST a quiet one for a removal, and LINK a link. In the default look PRIMARY is filled in the accent
     * colour and SECONDARY in the secondary button colour; GHOST, DESTRUCTIVE_GHOST and LINK have no resting fill, a
     * DESTRUCTIVE_GHOST's text turns the danger colour, on a danger tint, on hover, and LINK is text in the link colour.
     */
    public enum ButtonKind { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE_GHOST, LINK }
    /**
     * The state a {@link StatusDot} or a tree row's status shows: SUCCESS, WARNING and DANGER in the theme's status
     * colours, IDLE in its idle status colour.
     */
    public enum StatusKind { SUCCESS, WARNING, DANGER, IDLE }
    /**
     * What a {@link Field} holds. TEXT is one line of text. SEARCH is a search or filter field: its label is only its
     * accessible name, and the default look draws a search glyph before the text. PORT admits digits only and is as wide
     * as a port number, in the data face. PATH is a file path in the data face; a nonblank {@code trailingAction} adds a
     * Browse… button that reports it, and Enter in the field passes on. TEXTAREA wraps over several lines, and Enter in
     * it starts a new line. NUMBER requires a {@link Numeric} shape.
     */
    public enum FieldKind { TEXT, SEARCH, PORT, PATH, TEXTAREA, NUMBER }
    /**
     * How a {@link Choice} is drawn. DROPDOWN is a closed control whose popup lists the options. PICKER is a face whose
     * click, Enter or Down reports its action, and the owner shows the options. SEARCHABLE is a dropdown whose open list
     * narrows as the user types (see {@link ChoiceSearch}). PREVIEWS shows its options as tiles, an option with a preview
     * as the miniature window it describes. EXCLUSIVE is a choice among two to four options, all of them visible. TABS
     * (since 0.18.0) switches among two or more views of one thing (Overview and Details): it reports Selected, and the
     * owner composes the chosen content.
     */
    public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS, TABS }
    /**
     * How a {@link Card} lays out its children. FORM pads the card and stands its children (form rows, text) a row gap
     * apart. SETTINGS holds {@link SettingRow}s edge to edge, without gaps; the default look adds no padding and draws a
     * hairline between them. MIXED holds both: its setting rows edge to edge, its other children padded.
     */
    public enum CardKind { FORM, SETTINGS, MIXED }
    /** NONE keeps rows readable and scrollable but removes row selection, activation and the canvas focus stop.
     * Independently interactive inline controls keep their behaviour. NONE requires empty selectedIds. */
    public enum SelectionMode { NONE, SINGLE, MULTIPLE }
    /**
     * RESULTS is a one-line result list driven by an external field (a command palette): it never takes focus, the host
     * moves its selection, a left press activates a row, and fixed row and heading heights keep it steady. SINGLE,
     * DOUBLE and COMPACT are density hints.
     */
    public enum ListKind { SINGLE, DOUBLE, COMPACT, ORDERED, RESULTS }
    /**
     * PROMPT is a short, focused question (unlock, create) and PROMPT_COMPACT its compact form. SHEET is a sheet's or a
     * short dialog's content; without a hero it shows its title as its heading unless the dialog's own title bar carries
     * it. FULL is a dialog's content, such as groups of fields, each a {@link Section} holding a {@link Card}. WINDOW has
     * exactly one full-bleed content section, no hero, and a shared pinned footer. SETTINGS takes the generic form
     * spacing; a plugin's own Settings page is a {@link Stack} with {@link LayoutRole#SETTINGS_PAGE}.
     */
    public enum FormKind { PROMPT, PROMPT_COMPACT, SHEET, FULL, SETTINGS, WINDOW }
    /**
     * VERTICAL stacks children top to bottom, and DETAIL a detail column's sections. INLINE, ACTIONS, FOOTER, NAVIGATION
     * and JOINED_ACTIONS lay them side by side: ACTIONS is a row of buttons that wraps onto further rows when too wide;
     * FOOTER is a status footer whose last child shortens to the width the others leave, its children stacking when it
     * is too narrow; JOINED_ACTIONS draws its buttons as one joined group. PROMPT_HEADER is a prompt's header of icon,
     * heading and message. SETTINGS_PAGE is a Settings page whose children are {@link Section}s holding SETTINGS
     * {@link Card}s, and the other SETTINGS_ roles compose its parts. SUPPORTING keeps leading adornments natural-sized
     * beside a wrapping final text/body child. ROUTE is a read-only route preview (a tunnel's path): a bordered strip on
     * the input surface whose children are its steps, left to right, with the kit's arrow before every step after the
     * first. A step is a stack whose leading Icon the kit shows on a category tile in its hue, its other children (Text)
     * being the step's lines. The steps take equal columns, each later column opening with the arrow, and a step's lines
     * truncate within its column.
     */
    public enum LayoutRole { VERTICAL, INLINE, ACTIONS, PROMPT_HEADER, DETAIL, FOOTER, NAVIGATION, SUPPORTING, JOINED_ACTIONS, SETTINGS_PAGE, SETTINGS_CONTROLS, SETTINGS_TRAILING, SETTINGS_GROUP_HEADER, SETTINGS_FOOTER, SETTINGS_NAVIGATION, ROUTE }
    /**
     * THREE_COLUMN places a manager window's rail, list and detail columns side by side; SIDEBAR_DETAIL is the same manager
     * without its rail (its last child is the detail column, the others list columns). DOCUMENT stacks them top to bottom for a document
     * surface (an editor tab): every visible child takes its preferred height except the first visible Custom, Empty or
     * Split child, which fills the height left over; hidden children take no space. The other children are the document's bars:
     * an INLINE stack among them (directly, or as a row of a VERTICAL group) is one compact bar row whose first field or
     * text fills the width the controls after it leave (the rows of one group line their fills up), with the panel search
     * field's look for its fields and the panel action size for its buttons; a group stacks its rows without gaps and
     * shows a passive (SelectionMode.NONE) list's rows as text lines; a hairline separates neighbouring children.
     */
    public enum SplitKind { SIDEBAR_DETAIL, THREE_COLUMN, DOCUMENT }
    /**
     * What a {@link Custom} node hosts; the {@link CustomContent} registered for it has the same kind. EDITOR admits a
     * text component (a JTextArea, JTextPane or JEditorPane) or a canvas; TERMINAL, CHART and PREVIEW admit only a
     * canvas: a custom-painted JComponent that is not a JPanel, a text component, a button, a label, a list, a table, a
     * tree, a combo box, a spinner or a slider. A component in a JScrollPane is judged by the scroll pane's view. The
     * host refuses anything else: ordinary controls are described with nodes.
     */
    public enum CustomKind { EDITOR, TERMINAL, CHART, PREVIEW }
    /** A hint: STANDARD reserves the scroll bar's width; OVERLAY shows a transient thumb over the rows only while scrolling. */
    public enum ScrollPolicy { STANDARD, OVERLAY }

    /**
     * A run of passive text in a type role. Its whole text is its tooltip (in a list row, the row's tooltip takes its
     * place). A MONO, MONO_SMALL or MONO_CAPTION text that does not fit is shortened at its start so its end stays in
     * view (a form's preview at its end); other text that does not fit is shortened at its end. A present
     * {@code severity} draws the text in that severity's colour (INFO the accent, WARNING and DANGER the status colours),
     * as {@link Icon#severity()} tints a glyph, instead of its role's colour. A present {@code caret} (MONO and
     * MONO_SMALL and MONO_CAPTION text only, from 0 to the text's length) draws a thin insertion bar in the text's colour
     * after that many characters, as a command line shows its cursor.
     *
     * @param id the node id
     * @param state the node's state
     * @param text the text, shown literally (never as HTML)
     * @param role the type role, which sets the text's font and colour
     * @param severity a severity whose colour the text takes; empty keeps the role's colour
     * @param caret where a monospaced text draws its insertion bar; empty for none
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

    /**
     * A message severity: INFO in the accent colour, WARNING and DANGER in the status colours. It colours a {@link Text},
     * an {@link Icon}'s or a {@link Button}'s glyph and a {@link PropertyRow}'s value, and sets a {@link Notice}'s
     * colours, glyph and word.
     */
    public enum Severity { INFO, WARNING, DANGER }

    /**
     * A glyph, or at the SWATCH size a square of its hue. A present {@code severity} tints the glyph with its colour
     * instead of the hue.
     *
     * @param id the node id
     * @param state the node's state
     * @param name a built-in icon name or one the plugin registered with {@link UiKit#registerIcon}; the host refuses an
     *     unknown name when the view is created or updated
     * @param hue the glyph's colour
     * @param size the icon's size role
     * @param accessibleName the icon's accessible name, which is also its tooltip; not blank
     * @param severity a severity whose colour tints the glyph instead of the hue; empty for none
     */
    public record Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName, Optional<Severity> severity) implements Node {
        public Icon {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(name, "name");
            Objects.requireNonNull(hue);
            Objects.requireNonNull(size);
            ModelChecks.label(accessibleName, "accessibleName");
            Objects.requireNonNull(severity);
            if (size == IconSize.SWATCH && (hue == Hue.NONE || severity.isPresent()))
                throw new IllegalArgumentException("A SWATCH is a square of its hue: the hue is not NONE and it takes no severity");
        }
        /** An icon in its hue, without a severity. */
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
     * A push button: pressing it reports {@code action} as a {@link UiEvent.Action}. A present {@code iconSeverity} tints
     * the button's glyph with that severity's colour (as {@link Icon#severity()} does) at rest and on hover, instead of
     * the kind's text colour; for example a favourite's warning star. The label keeps the kind's colour. A present
     * {@code selected} makes it a toggle button (a search option such as match case): the kit paints the pressed
     * treatment while it is true and reports its pressed state to assistive technology; a click reports the Action and
     * the owner confirms the new state in its next update. Only GHOST and SECONDARY buttons take it.
     *
     * @param id the node id
     * @param state the node's state; a disabled or busy button cannot be pressed
     * @param label the button's text; blank makes an icon-only button, which requires an icon and a tooltip
     * @param icon the glyph beside the label: a built-in icon name or one the plugin registered; empty for none
     * @param kind the button's treatment
     * @param action the action id the button reports; not blank
     * @param tooltip the text shown on hover, and an icon-only button's accessible name; empty for none
     * @param presentation how the button sits in its composition
     * @param iconSeverity a severity whose colour tints the glyph; empty for the kind's colour
     * @param selected the pressed state of a toggle button; empty for a plain button
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
        /** A STANDARD button whose glyph takes its kind's colour and that has no pressed state. */
        public Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip) {
            this(id, state, label, icon, kind, action, tooltip, ButtonPresentation.STANDARD);
        }
    }

    /**
     * A button with a menu of {@link MenuItem}s. With a blank {@code action} it is a menu-only button: pressing it opens
     * the menu, and a labelled one shows a chevron. With a nonblank {@code action} it is a split button: its main part
     * reports the action and a separate arrow opens the menu. Choosing an enabled item closes the menu and reports the
     * item's action as a {@link UiEvent.Action}. {@code presentation} as for {@link Button}; an EMBEDDED menu-only button
     * draws no chevron of its own.
     *
     * @param id the node id
     * @param state the node's state
     * @param label the button's text; blank makes an icon-only button, which requires an icon and a tooltip
     * @param icon the glyph beside the label: a built-in icon name or one the plugin registered; empty for none
     * @param kind the button's treatment
     * @param action the action the main part reports; blank for a menu-only button
     * @param items the menu's rows: at least one, with unique ids
     * @param tooltip the text shown on hover, and an icon-only button's accessible name; empty for none
     * @param presentation how the button sits in its composition
     */
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
        /** A STANDARD menu button. */
        public MenuButton(String id, ControlState state, String label, String icon, ButtonKind kind, String action, List<MenuItem> items, String tooltip) {
            this(id, state, label, icon, kind, action, items, tooltip, ButtonPresentation.STANDARD);
        }
    }

    /**
     * A small round dot in its kind's status colour, such as whether a saved host is connected. It takes no input.
     *
     * @param id the node id
     * @param state the node's state
     * @param kind the status the dot shows
     */
    public record StatusDot(String id, ControlState state, StatusKind kind) implements Node {
        public StatusDot {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
        }
    }

    /**
     * Bounds and shape of a NUMBER field: the kit admits only numbers of this shape and steps by {@code step} with the
     * arrow keys, clamped to the bounds. The domain still validates the value and reports problems through state.
     *
     * @param min the smallest value, finite; the field admits a minus sign only when it is below zero
     * @param max the largest value, finite and not below {@code min}
     * @param step what one arrow-key step adds or takes away; finite and positive
     * @param decimals the most digits after the decimal point, from 0 to 6; a stepped value is rounded to them
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
     * A text entry: every edit reports {@link UiEvent.Edited} with the field's whole text. A NUMBER field states its
     * {@link Numeric} shape; other kinds have none. {@code alternateAction} is what Shift+Enter in a single-line field
     * runs (a find field's Previous); {@code cancelAction} is what Escape in the field runs. Blank passes either key on,
     * to an enclosing stack or form. A nonblank {@code listId} names an ItemList in the same view: Up, Down, Page Up and
     * Page Down in the field move that list's selection (reporting its Selected) while the focus stays in the field, and
     * Enter keeps its meaning in the field. A TEXTAREA takes neither {@code alternateAction} nor {@code listId}, and a
     * NUMBER field (whose arrows step it) takes no {@code listId}. A {@code readOnly} field (since 0.18.0) is focusable,
     * selectable and copyable but never edited, so it reports no {@link UiEvent.Edited}; a STEPPER or SLIDER is never
     * read-only.
     *
     * @param id the node id
     * @param state the node's state; a nonblank error shows under the field
     * @param kind what the field holds
     * @param value the field's text; the field shows a new value when an update changes it
     * @param hint the placeholder shown while the field is empty, and its accessible description while it has no error;
     *     a STEPPER shows it instead as a unit after the value, and a SLIDER right after its value. Blank for none
     * @param label the field's label and accessible name; the presentation decides whether it shows. Not blank
     * @param trailingAction what Enter in a single-line field runs, the field keeping the key; a PATH field's Browse…
     *     button reports it instead. Blank passes Enter on, to an enclosing form
     * @param numeric a NUMBER field's shape: present for NUMBER, empty for the other kinds
     * @param presentation how the field sits in its composition
     * @param alternateAction what Shift+Enter runs; blank for none
     * @param cancelAction what Escape runs; blank for none
     * @param listId the id of the ItemList the field's Up, Down, Page Up and Page Down keys move; blank for none
     * @param readOnly whether the field shows its value without ever being edited
     */
    public record Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                        String alternateAction, String cancelAction, String listId, boolean readOnly) implements Node {
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
            if (readOnly && (presentation == FieldPresentation.STEPPER || presentation == FieldPresentation.SLIDER))
                throw new IllegalArgumentException("A stepper or slider is never read-only");
        }
        /** An editable field. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation,
                     String alternateAction, String cancelAction, String listId) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, presentation, alternateAction, cancelAction, listId, false);
        }
        /** A field whose Shift+Enter and Escape pass on and that moves no list. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric, FieldPresentation presentation) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, presentation, "", "", "");
        }
        /** A STANDARD field whose Shift+Enter and Escape pass on and that moves no list. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric) {
            this(id, state, kind, value, hint, label, trailingAction, numeric, FieldPresentation.STANDARD);
        }
        /** A STANDARD field of any kind but NUMBER, whose Shift+Enter and Escape pass on and that moves no list. */
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction) {
            this(id, state, kind, value, hint, label, trailingAction, Optional.empty());
        }
    }

    /**
     * INPUT is a masked single-line entry with a reveal control; CONFIRMATION is the same editable input without its own
     * reveal control, following its reveal group. DETAIL is a read-only stored value, masked as a fixed row of dots that
     * does not disclose its length, with a reveal control and an optional copy button. MULTILINE is an editable, wrapping
     * secure-note area over the same wipeable storage, shown in clear, with no reveal or copy control, so it takes
     * neither a reveal group nor a copy action. DISCREET_INPUT reserves reveal geometry and shows the control when
     * nonempty or focused within.
     */
    public enum SecretKind { INPUT, DISCREET_INPUT, CONFIRMATION, DETAIL, MULTILINE }

    /**
     * A secret entry or stored secret whose characters never appear in the descriptor: read them with
     * {@link UiView#withSecret}, replace them with {@link UiView#replaceSecret} (which also sets a DETAIL secret's stored
     * value) and erase them with {@link UiView#clearSecret}. An edit reports {@link UiEvent.SecretChanged}, which carries
     * no text. A nonblank {@code submitAction} (INPUT, DISCREET_INPUT or CONFIRMATION) is what Enter in the field runs:
     * the field keeps the key, as a single-line field with a trailing action does, so the containing form's primary is
     * not pressed. Blank passes Enter on.
     *
     * @param id the node id
     * @param state the node's state; a nonblank error shows under the secret
     * @param hint the placeholder shown while the entry is empty, and its accessible description while it has no error;
     *     blank for none
     * @param label the secret's label and accessible name; not blank
     * @param revealGroup a nonblank group whose secrets in the view reveal and hide together; empty for none, and always
     *     empty for MULTILINE
     * @param kind what the secret is
     * @param copyAction for a DETAIL secret, a nonblank action adds a copy button that reports it (the kit copies
     *     nothing itself); empty for none, and always empty for MULTILINE
     * @param submitAction what Enter in an INPUT, DISCREET_INPUT or CONFIRMATION secret runs; empty for none
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
        /** A secret without a submit action, so Enter in it passes on. */
        public Secret(String id, ControlState state, String hint, String label, String revealGroup, SecretKind kind, String copyAction) {
            this(id, state, hint, label, revealGroup, kind, copyAction, "");
        }
        /** An INPUT secret without a copy or submit action. */
        public Secret(String id, ControlState state, String hint, String label, String revealGroup) {
            this(id,state,hint,label,revealGroup,SecretKind.INPUT,"");
        }
    }

    /**
     * Type-to-filter configuration of a SEARCHABLE choice. The first {@code pinned} options always show; {@code noMatches}
     * is the line shown when typing matches none of the other options. A present {@code entry} makes the value free text
     * (options become suggestions): the kit reports typed or picked text as {@link UiEvent.Edited}, and the choice has no
     * selected id.
     * A nonempty {@code placeholder} is shown, in the placeholder role, while a free entry is empty (for example what a
     * blank value means); it is never a value. It requires free entry. Typing narrows the open list to the options whose
     * label contains every typed word, ignoring case.
     *
     * @param pinned how many leading options always show, whatever is typed; from 0 to the number of options
     * @param noMatches the line shown when typing matches none of the options after the pinned ones; not blank
     * @param entry the free entry's current text, which makes the value free text; empty when the value is an option
     * @param placeholder the text shown while a free entry is empty; empty for none
     */
    public record ChoiceSearch(int pinned, String noMatches, Optional<String> entry, String placeholder) {
        public ChoiceSearch {
            if (pinned < 0) throw new IllegalArgumentException("Negative pinned count");
            ModelChecks.label(noMatches, "noMatches");
            Objects.requireNonNull(entry);
            Objects.requireNonNull(placeholder);
            if (!placeholder.isEmpty() && entry.isEmpty()) throw new IllegalArgumentException("A placeholder requires free entry");
        }
        /** Search configuration without a placeholder. */
        public ChoiceSearch(int pinned, String noMatches, Optional<String> entry) { this(pinned, noMatches, entry, ""); }
    }

    /**
     * A hint for a {@link PickerFace}'s size: DEFAULT is a form's face, COMPACT a shorter face with smaller type and
     * tile for a picker inline in a row.
     */
    public enum PickerDensity { DEFAULT, COMPACT }
    /**
     * The closed face of a PICKER or DROPDOWN {@link Choice}, as display data only. Empty primary means the placeholder
     * face. A PICKER's face is its whole control; a DROPDOWN may carry one too (a rich closed face, such as a saved
     * host's tile, name and endpoint) while its popup still lists the options and reports Selected; its owner keeps the
     * face describing the selected option. A {@code technicalCaption} (since 0.18.0) is a data value, such as a saved
     * host's endpoint, drawn in the data face.
     *
     * @param primary the face's main text; empty shows the placeholder face, "Choose…" in the placeholder colour, which
     *     the default look draws with a lock glyph, without a tile, in place of {@code icon} and {@code hue}
     * @param caption secondary text after the main text, in the caption colour; empty for none
     * @param icon the glyph the default look sets on the face's tile: a built-in icon name or one the plugin
     *     registered; empty for none
     * @param hue the tile's hue
     * @param density the face's size
     * @param technicalCaption whether the caption is a data value, drawn in the data face
     */
    public record PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density, boolean technicalCaption) {
        public PickerFace { Objects.requireNonNull(primary);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(density); }
        /** A face whose caption is plain text. */
        public PickerFace(String primary, String caption, String icon, Hue hue, PickerDensity density) { this(primary, caption, icon, hue, density, false); }
    }
    /**
     * What a {@link PickerItem} row is. VALUE is one of the values to choose; UTILITY is a command row, such as a None row
     * that clears the value (the menu's footer row is one). The default look sets a VALUE row's icon on a tile in its hue
     * and draws a UTILITY row's icon without a tile.
     */
    public enum PickerItemKind { VALUE, UTILITY }
    /**
     * One selectable row of a {@link PickerMenu}; its id is also the Action emitted on explicit acceptance.
     *
     * @param id the row's id: unique in the menu and not the menu's {@code footerAction}
     * @param label the row's text
     * @param caption secondary text after the label, in the caption colour; empty for none
     * @param icon the row's glyph: a built-in icon name or one the plugin registered; empty for none on a VALUE row
     *     only, since a UTILITY row needs one
     * @param hue the hue of a VALUE row's tile
     * @param kind what the row is
     */
    public record PickerItem(String id,String label,String caption,String icon,Hue hue,PickerItemKind kind) {
        public PickerItem { ModelChecks.id(id);Objects.requireNonNull(label);Objects.requireNonNull(caption);Objects.requireNonNull(icon);Objects.requireNonNull(hue);Objects.requireNonNull(kind); }
        /** A VALUE row. */
        public PickerItem(String id,String label,String caption,String icon,Hue hue){this(id,label,caption,icon,hue,PickerItemKind.VALUE);}
    }
    /**
     * A searchable menu of rich rows, such as a {@link PluginPopover}'s content for a PICKER choice: a search field over
     * {@code items}, then a footer row after a hairline. The kit does not filter: typing reports {@link UiEvent.Edited}
     * naming this node, and the owner answers with the matching items. Up and Down move the active row, wrapping round
     * and including the footer, and report {@link UiEvent.Selected} naming this node and the row; Enter or a left press
     * accepts a row as {@link UiEvent.Action} with the row's id ({@code footerAction} for the footer); Escape reports
     * {@code cancelAction}. While an input method holds uncommitted text these keys do nothing. The checked row (the
     * current value) and the active row (the keyboard highlight) are distinct. At most nine rows show before the list
     * scrolls.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param query the search field's text; the field takes it whenever it changes
     * @param placeholder the search field's hint while it is empty, and its accessible name
     * @param items the rows in order; ids are unique, and accepting a row reports its id as the action
     * @param checkedId the id of the row drawn with a check mark (the current value); empty for none
     * @param activeId the id of the keyboard-active row, applied when it or the rows change
     * @param noMatches the line shown, muted, while {@code items} is empty
     * @param footerLabel the footer row's label, drawn in the link colour; the default look draws the add glyph before
     *     it, so the footer suits a command that creates a value. Not blank
     * @param footerAction the action the footer row reports; not blank and not the id of an item
     * @param cancelAction the action Escape reports; not blank
     */
    public record PickerMenu(String id,ControlState state,String query,String placeholder,List<PickerItem> items,
            String checkedId,String activeId,String noMatches,String footerLabel,String footerAction,String cancelAction) implements Node {
        public PickerMenu { ModelChecks.id(id);Objects.requireNonNull(state);Objects.requireNonNull(query);Objects.requireNonNull(placeholder);
            items=List.copyOf(items);ModelChecks.unique(items,PickerItem::id);Objects.requireNonNull(checkedId);Objects.requireNonNull(activeId);
            Objects.requireNonNull(noMatches);ModelChecks.label(footerLabel,"footerLabel");ModelChecks.id(footerAction);ModelChecks.id(cancelAction);
            if(items.stream().anyMatch(item -> item.id().equals(footerAction)))throw new IllegalArgumentException("Footer id collides with item"); }
    }

    /**
     * One value from a set of options, drawn as its {@code kind} says. A DROPDOWN, EXCLUSIVE, TABS or PREVIEWS choice, and
     * a SEARCHABLE one without free entry, reports the option the user picks as {@link UiEvent.Selected} naming this node
     * and the option; a SEARCHABLE choice with free entry reports its text as {@link UiEvent.Edited}. A PICKER lists
     * nothing itself: a click, Enter or Down reports {@code action}, and its owner shows the options, for example in a
     * {@link PluginPopover} anchored on it with {@link UiView#anchor(String)}.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param kind how the choice is drawn and what it reports
     * @param options the options in order, with unique ids; an EXCLUSIVE choice has two to four and a TABS choice at least
     *     two (checked by {@link ViewSpec})
     * @param selectedId the selected option's id, or empty for none, which a DROPDOWN or PICKER without a face and a
     *     SEARCHABLE choice without free entry show as "Choose…"; always empty with free entry
     * @param label the control's label, not blank: drawn above it, except on TABS and PREVIEWS choices, on an EXCLUSIVE
     *     choice directly in a {@link Card}, on a DROPDOWN in an ACTIONS stack, on a choice that is a {@link Panel}'s
     *     search or navigation row, and where an enclosing row or toolbar view names the control; always its accessible
     *     name
     * @param action what a PICKER reports when activated, and what Enter reports in a SEARCHABLE choice with free entry;
     *     blank for nothing, and unused by the other kinds
     * @param icon the icon name a PICKER without a face draws at its leading edge; empty for none, and only for PICKER
     * @param search the type-to-filter configuration, present exactly for SEARCHABLE
     * @param face the rich closed face of a PICKER or DROPDOWN; empty for the plain face
     */
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
        /** A choice without a rich face. */
        public Choice(String id,ControlState state,ChoiceKind kind,List<Option> options,String selectedId,String label,String action,String icon,Optional<ChoiceSearch> search) {
            this(id,state,kind,options,selectedId,label,action,icon,search,Optional.empty());
        }
        /** A choice without search or a rich face; a SEARCHABLE choice needs a constructor that takes its search. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon) {
            this(id, state, kind, options, selectedId, label, action, icon, Optional.empty());
        }
        /** A choice without search or a rich face; a PICKER gets the {@code key} icon cue, and other kinds no cue. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action) {
            this(id, state, kind, options, selectedId, label, action, kind == ChoiceKind.PICKER ? "key" : "");
        }
    }

    /**
     * A boolean, drawn as a switch or a checkbox as the look decides. Operating it reports {@link UiEvent.Toggled} naming
     * this node with the opposite of {@code checked}; it shows a new state only when the owner updates {@code checked}. It
     * draws no text of its own: in a {@link SettingRow} or {@link FormRow} the row's title or label names it. In an
     * {@link ItemList} row whose trailing nodes hold exactly one toggle, Space on the focused row operates it. A
     * validation error in {@code state} replaces the tooltip. {@code pending} means a change is in flight while the
     * domain confirms it (for example a tunnel starting): the control stays operable and reports the opposite of
     * {@code checked}, which is always the confirmed state, and the default look shows the switch's knob midway on the
     * warning colour.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param label the control's accessible name, and its tooltip while {@code tooltip} is blank; not blank
     * @param checked the confirmed state
     * @param action the toggle's action key, not blank; {@link UiEvent.Toggled} names the node instead, and the owner
     *     resolves this key from its current spec
     * @param compact a size hint: the default look draws a smaller switch
     * @param tooltip the literal tooltip; blank shows {@code label}
     * @param pending whether a change is in flight
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
        /** A toggle that is not pending and whose tooltip is its label. */
        public Toggle(String id, ControlState state, String label, boolean checked, String action, boolean compact) {
            this(id, state, label, checked, action, compact, "");
        }
    }

    /**
     * A passive tag, such as a protocol or a badge: its label in the hue's colour on a tint of that hue (the Enterprise
     * look boxes it and draws the label in capitals). On an inline line the chip keeps its whole label while the texts
     * beside it shorten.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param label the chip's text and accessible name; not blank
     * @param hue the chip's colour; NEUTRAL and NONE both draw the neutral hue
     */
    public record Chip(String id, ControlState state, String label, Hue hue) implements Node {
        public Chip {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(hue);
        }
    }

    /**
     * A passive progress bar in the accent colour, with {@code label} as a caption under its trailing end. An
     * indeterminate bar's segment sweeps along the track while the bar shows, is enabled and animations are on
     * ({@code ui.animations.enabled}, in a look that animates; the Enterprise look does not), and rests at the track's
     * start otherwise.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param fraction how much is done, from 0 to 1, or -1 for indeterminate
     * @param label the caption and accessible name; empty for none
     */
    public record Progress(String id, ControlState state, double fraction, String label) implements Node {
        public Progress {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (!Double.isFinite(fraction) || (fraction != -1 && (fraction < 0 || fraction > 1))) throw new IllegalArgumentException("Progress must be -1 or in 0..1");
        }
    }

    /**
     * A passive four-segment meter, such as a password's strength, with {@code label} as a caption under its trailing
     * end. The filled segments and the caption take one colour: one segment the danger colour, two the warning colour,
     * three the accent and four the success colour.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param filled how many segments are filled, from 0 to 4
     * @param label the caption and accessible name; empty for none
     */
    public record Strength(String id, ControlState state, int filled, String label) implements Node {
        public Strength {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (filled < 0 || filled > 4) throw new IllegalArgumentException("Strength must be in 0..4");
        }
    }

    /**
     * Children laid out as {@code role} says. A {@link Separator} stands only in an INLINE or ACTIONS stack, between two
     * items, and a {@link Notice} outside a form's sections only in a VERTICAL stack ({@link ViewSpec} checks both). A
     * nonblank {@code cancelAction} is what Escape runs from any focused control inside the stack that does not take
     * Escape itself (a field's own {@code cancelAction}, an open popup), as an inline find bar closes; blank, or a hidden,
     * disabled or busy stack, passes it on.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param role the layout
     * @param children the children in order
     * @param cancelAction the action Escape reports; blank for none
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

    /**
     * A labelled form row: the label (with its caption under it) in the form's label column, the control beside it and an
     * optional trailing cell after the control, which takes at most half the room beside the label; a row too narrow for
     * that stacks them. The label names the control, so the controls' own labels are not drawn. {@code label} carries no
     * colon: the presentation punctuates it.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param label the row's label
     * @param caption a line under the label; the default look keeps it to one line, shortened at its end, with the whole
     *     text as its tooltip; empty for none
     * @param control the row's control
     * @param trailing an optional second control after it, such as a port field beside a host field
     * @param highlighted draws the row as a search match, as Settings' search marks rows
     */
    public record FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing, boolean highlighted) implements Node {
        public FormRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            Objects.requireNonNull(caption);
            Objects.requireNonNull(control);
            Objects.requireNonNull(trailing);
        }
        /** A row that is not highlighted. */
        public FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing) {
            this(id, state, label, caption, control, trailing, false);
        }
    }

    /**
     * A setting: its title and description, with the control at the row's trailing edge; the description wraps beside
     * the control. In a look that draws toggles as checkboxes (the Enterprise look) a {@link Toggle} control leads the
     * title instead. The title names the control, so the control's own label is not drawn.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param title the setting's name; not blank
     * @param description text under the title; empty for none
     * @param control the setting's control
     * @param dependent indents the row under the one above it, for a setting that depends on that one; the kit only
     *     indents it, and the owner decides whether its control is enabled
     * @param highlighted draws the row as a search match, as Settings' search marks rows
     * @param titleNote a note drawn after the title, after a middle dot, in a muted colour; empty for none
     */
    public record SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted, String titleNote) implements Node {
        public SettingRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description); Objects.requireNonNull(titleNote);
            Objects.requireNonNull(control);
        }
        /** A row without a title note. */
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted) {
            this(id, state, title, description, control, dependent, highlighted, "");
        }
        /** A row that is not highlighted and has no title note. */
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent) {
            this(id, state, title, description, control, dependent, false, "");
        }
    }

    /**
     * A grouped surface whose children stack top to bottom; the default look draws it as a rounded card. In a
     * {@link Section} it is a titled group.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param kind how the card spaces its children: FORM pads the card and spaces its rows apart, SETTINGS lays its rows
     *     edge to edge (the default look draws a hairline between setting rows), and MIXED pads its other children and
     *     lays its {@link SettingRow}s edge to edge
     * @param children the children in order
     */
    public record Card(String id, ControlState state, CardKind kind, List<Node> children) implements Node {
        public Card {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
        }
    }

    /**
     * A titled part of a view: the default look draws the title as a muted section label above the body, and the
     * Enterprise look draws a section and its {@link Card} body as one box whose top edge the title breaks. With a Card
     * as its body, a titled group.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param title the section's title; not blank
     * @param body the section's content
     */
    public record Section(String id, ControlState state, String title, Node body) implements Node {
        public Section {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(body);
        }
    }

    /**
     * Nothing to show yet, or a disconnected state: centred in its area, the icon on a tile, the title, the description
     * and a row of actions. {@code icon} is drawn only by presentations that draw an icon tile; the Enterprise look draws
     * no tile and only one action, the visible PRIMARY button or else the first visible action.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param icon the node the tile holds, such as an {@link Icon}
     * @param title the state's title; not blank
     * @param description wrapped text under the title; empty for none
     * @param actions the controls under the description, such as buttons
     */
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
     * Virtual row collection. MULTIPLE selects rows with Shift (a range) and Cmd (Ctrl on Linux and Windows) or Space (one row), without checkboxes; addAction is a footer, never a data item. A present
     * {@code visibleRows} is how many selectable rows the viewport shows before it scrolls (headings above them add their
     * own height); absent keeps the kit default. Headings are never selected and ORDERED lists have none.
     * A present {@code enterAction} is what Enter on the focused row reports (after selecting it), so a double click and
     * Enter can differ; blank keeps Enter as {@code activateAction}. A present {@code deleteAction} is what Delete and
     * Backspace on the focused row report (an unselected row is selected first); blank passes those keys on.
     * A plain left single click on a row also reports {@link UiEvent.Clicked} after its selection, except in a RESULTS
     * list, which takes none of the row key actions either. An ORDERED list's move up, move down and remove commands
     * report {@link UiEvent.Reordered} and {@link UiEvent.Removed}; the owner confirms them by updating the list.
     * {@code detailsAction}, {@code copyAction} and {@code cancelAction} bind D, platform Copy and
     * Escape only while the row canvas has focus; blank actions pass the key to an enclosing control. With no items the
     * body shows {@code emptyText} as one muted line, above {@code addAction}; blank shows nothing.
     *
     * @param id stable list id
     * @param state visibility and interaction state
     * @param kind row presentation
     * @param items ordered rows
     * @param selectedIds selected row ids
     * @param activateAction what activating a row reports as a {@link UiEvent.Action}: a double click, Enter on the
     *     focused row while {@code enterAction} is blank, or a left press in a RESULTS list. The row is selected first
     *     (reporting {@link UiEvent.Selected}), so the owner finds it in its selection; blank reports nothing
     * @param contextActions default row menu
     * @param selectionMode selection behaviour
     * @param addAction optional footer button
     * @param visibleRows requested viewport row count
     * @param enterAction focused-row Enter action
     * @param deleteAction focused-row Delete action
     * @param detailsAction focused-row D action
     * @param copyAction focused-row platform Copy action
     * @param cancelAction focused-row Escape action
     * @param emptyText the line the body shows while there are no items; blank for none
     * @since 0.18.0 for {@code emptyText}
     */
    public record ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction, String detailsAction, String copyAction, String cancelAction, String emptyText) implements Node {
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
            Objects.requireNonNull(emptyText);
            if (kind == ListKind.RESULTS && (!enterAction.isEmpty() || !deleteAction.isEmpty() || !detailsAction.isEmpty() || !copyAction.isEmpty() || !cancelAction.isEmpty()))
                throw new IllegalArgumentException("RESULTS rows never take focus, so they have no row key actions");
            ModelChecks.unique(items, ListItem::id);
            ModelChecks.unique(contextActions, MenuItem::id);
            for (var item : items) if (item.heading()) {
                if (kind == ListKind.ORDERED) throw new IllegalArgumentException("Ordered lists have no headings");
                if (selectedIds.contains(item.id())) throw new IllegalArgumentException("A heading is never selected");
            }
        }
        /** A list that shows nothing while it has no items. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction, String detailsAction, String copyAction, String cancelAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, enterAction, deleteAction, detailsAction, copyAction, cancelAction, "");
        }
        /** A list without D, Copy or Escape row actions. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows, String enterAction, String deleteAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, enterAction, deleteAction, "", "", "");
        }
        /** A list whose Enter reports its activate action and which has no other row key actions. */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, visibleRows, "", "");
        }
        /**
         * A list with the kit's default viewport height, whose Enter reports its activate action and which has no other
         * row key actions.
         */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, OptionalInt.empty());
        }
        /**
         * A SINGLE-selection list without a footer button, with the kit's default viewport height, whose Enter reports its
         * activate action and which has no other row key actions.
         */
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, SelectionMode.SINGLE, Optional.empty());
        }
    }

    /**
     * A {@link Panel}'s one-line header: the title, the count, then {@code context} and {@code primary}, then a "More
     * actions" menu button listing {@code overflow} when it has items. When a Panel is the root of a tool window's view,
     * the tool window uses this header as its own: the {@link PanelHeader} detail follows the count, and its actions,
     * then Jasper's Options and Hide buttons, follow the overflow button.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param title the panel's title; not blank
     * @param count the size of the panel's collection, shown muted after the title while it is above 0; 0 also hides the
     *     panel's {@code searchOrNavigation}, and an absent count leaves that row to its own state
     * @param context an optional node after the count
     * @param primary an optional {@link Button} or {@link MenuButton} after {@code context}
     * @param overflow the "More actions" menu's items, with unique ids; each reports its action; empty for no menu
     */
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

    /**
     * A panel, such as a tool window's content: the header at the top, the footer at the bottom, and between them the
     * search or navigation row over the body. An {@link ItemList}, {@link Table} or {@link Tree} body runs its selection
     * edge to edge. As the root of a tool window's view, its header becomes the tool window's header (see
     * {@link Header}).
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param header the panel's header
     * @param searchOrNavigation an optional row above the body, such as a SEARCH {@link Field} or an INLINE
     *     {@link Stack} of navigation controls, whose own label is not drawn; hidden while the header's count is 0
     * @param body the panel's content
     * @param footer an optional node at the panel's bottom, under the body
     */
    public record Panel(String id, ControlState state, Header header, Optional<Node> searchOrNavigation, Node body, Optional<Node> footer) implements Node {
        public Panel {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(header);
            Objects.requireNonNull(searchOrNavigation);
            Objects.requireNonNull(body);
            Objects.requireNonNull(footer);
        }
    }

    /**
     * What Enter does at form level when the focused control does not take the key itself (see {@link Form}). PRIMARY
     * presses the form's PRIMARY action, even while another of its buttons has the focus; NONE presses nothing. Either
     * way, Enter on a focused {@link Button} goes to the form instead of pressing it, and Space presses it.
     */
    public enum FormSubmission { PRIMARY, NONE }

    /**
     * The content of a dialog, sheet or window: an optional hero, the sections, and a footer with a preview or a
     * footnote at its leading edge and the action buttons at its trailing edge. The window around it is the host's.
     * The focus starts in the form's first visible, enabled Field, Secret or Choice that is not busy; call
     * {@link UiView#focus(String)} to start elsewhere. With {@link FormSubmission#PRIMARY}, Enter presses the PRIMARY
     * action unless the focused control takes Enter itself (such as a Field whose Enter runs its trailingAction, a
     * Secret with a submitAction, a PICKER Choice, editing Breadcrumbs, or a list, table or tree row with an activate
     * or Enter action; a Table with an activateAction keeps Enter even with no row focused), a popup is open, the focus
     * is in a multi-line text area, or the form or a visible, enabled control in it is busy or shows an error, which
     * also disables the PRIMARY button. Escape closes an open popup, or else reports {@code cancelAction} while the form is
     * enabled and not busy; a focused control's own cancel action, or an enclosing {@link Stack}'s, is reported instead.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param kind the form's layout, from a short prompt to a full window
     * @param title the form's title, not blank. A SHEET without a hero draws it as its heading unless the host's title
     *              bar shows the dialog's title; other kinds do not draw it. A title bar shows the {@link DialogSpec}
     *              or {@link WindowSpec} title.
     * @param hero an optional block above the sections, such as a prompt's PROMPT_HEADER stack of icon, heading and
     *             message; a SHEET or FULL form's hero Text that only repeats the title is hidden while the dialog's
     *             title bar carries the title. A WINDOW form has none.
     * @param sections the body, top to bottom; a WINDOW form has exactly one, which fills the window. A {@link Notice}
     *                 may stand here.
     * @param preview an optional node at the footer's leading edge. A monospaced Text, such as an equivalent command
     *                (shortened at its end), and any preview of a WINDOW form stay beside the actions until the room
     *                there is narrower than the standard form label column, then move to their own line above them;
     *                any other preview moves there when it does not fit beside the actions. A form has a preview or a
     *                footnote, not both.
     * @param actions the footer's buttons; at most one is PRIMARY
     * @param cancelAction what Escape reports; blank for none
     * @param submission what Enter does at form level
     * @param footnote one plain line drawn muted at the footer's leading edge, such as an auto-lock note; blank for none
     * @since 0.18.0 for {@code footnote}
     */
    public record Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission, String footnote) implements Node {
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
            Objects.requireNonNull(footnote);
            if (footnote.chars().anyMatch(c -> c == 10 || c == 13)) throw new IllegalArgumentException("A footnote is one line");
            if (!footnote.isBlank() && preview.isPresent())
                throw new IllegalArgumentException("A footnote and a preview share the footer's leading edge; a form has one or the other");
        }
        /** A form without a footnote. */
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction, FormSubmission submission) {
            this(id, state, kind, title, hero, sections, preview, actions, cancelAction, submission, "");
        }
        /** A form without a footnote whose Enter presses its PRIMARY action. */
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction) {
            this(id, state, kind, title, hero, sections, preview, actions, cancelAction, FormSubmission.PRIMARY);
        }
        /** A form without a footnote or cancel action whose Enter presses its PRIMARY action. */
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions) {
            this(id, state, kind, title, hero, sections, preview, actions, "");
        }
    }

    /**
     * Two or more children arranged by {@code kind}: side by side as full-height columns, or for DOCUMENT stacked top
     * to bottom, the first visible Custom, Empty or Split child, if any, filling the height the others leave. Hidden
     * children take no space. A THREE_COLUMN split of three columns keeps a manager window's sidebar and list widths,
     * and a SIDEBAR_DETAIL split of two keeps the list width in its first column; each narrows in proportion when the
     * split is narrow, and the last column takes the rest. Any other number of columns shares the width equally.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param kind the arrangement
     * @param children the columns, leading to trailing, or for DOCUMENT the rows, top to bottom; at least two
     */
    public record Split(String id, ControlState state, SplitKind kind, List<Node> children) implements Node {
        public Split {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Split needs at least two children");
        }
    }

    /**
     * Rows of cells under column headers, with an explicit selection mode and scroll policy. Selecting rows reports
     * {@link UiEvent.Selected}; with MULTIPLE selection Shift-click selects a range, and Cmd-click (Ctrl-click on Linux
     * and Windows) or Space adds or removes one row. A plain left single click also reports {@link UiEvent.Clicked}, and
     * a left click on a column header reports {@link UiEvent.Sorted}. A heading row ({@link TableRow#heading()}) groups
     * the rows below it and is never selected, activated or offered a menu. With no rows the body shows {@code empty} or
     * the muted line {@code emptyText} below the column headers, which stay.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param columns the columns, leading to trailing, with unique ids; they share the width by {@link Column#weight()}
     * @param rows the rows, top to bottom, with unique ids; a row's cell keys are column ids
     * @param selectedIds the selected rows' ids: rows of this table and never a heading row; empty under NONE and at most
     *                    one under SINGLE. A selection that differs from the user's own is scrolled into view.
     * @param activateAction what a double click or Enter on a row reports after selecting it; blank for none
     * @param contextActions the shared menu a right click on a selected row, or outside every row, offers; it acts on the
     *                       selection and never changes it. A row with its own {@link TableRow#contextActions()} offers
     *                       that instead.
     * @param selectionMode whether rows are selected one at a time, several at a time or not at all
     * @param scroll the scroll bar policy
     * @param empty the empty state shown while there are no rows, with at most one action; absent for none
     * @param emptyText the muted line shown while there are no rows; blank for none, and blank when {@code empty} is
     *                  present
     * @since 0.18.0 for {@code empty} and {@code emptyText}
     */
    public record Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll,
                        Optional<Empty> empty, String emptyText) implements Node {
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
            Objects.requireNonNull(empty); Objects.requireNonNull(emptyText);
            if (empty.isPresent() && !emptyText.isBlank()) throw new IllegalArgumentException("A table shows an empty state or an empty line, not both");
            if (empty.isPresent() && empty.get().actions().size() > 1) throw new IllegalArgumentException("A table's empty state has at most one action");
            for (var row : rows) if (row.heading().isPresent() && selectedIds.contains(row.id())) throw new IllegalArgumentException("A heading row is never selected");
        }
        /** A table whose body shows nothing while it has no rows. */
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, scroll, Optional.empty(), "");
        }
        /** A table with the STANDARD scroll policy whose body shows nothing while it has no rows. */
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, ScrollPolicy.STANDARD);
        }
        /** A table with SINGLE selection and the STANDARD scroll policy whose body shows nothing while it has no rows. */
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, SelectionMode.SINGLE);
        }
    }

    /**
     * A tree of items; the user selects one item at a time. Selecting an item reports {@link UiEvent.Selected}, and a
     * plain left single click also reports {@link UiEvent.Clicked}. A branch's disclosure control, and Right or Left on
     * the focused branch, report {@link UiEvent.Expanded}; the owner answers with an updated {@code expandedIds}. A
     * right click offers the item's own {@link TreeItem#contextActions()}; a tree has no shared menu. A non-empty
     * {@code columns} shows a header over the rows: the first column titles the items, and each later column titles one
     * of the items' {@link TreeItem#trailing()} values, in order, so an item has at most one value per column after the
     * first. Clicking a column header reports {@link UiEvent.Sorted}, as a {@link Table}'s does; the owner orders the
     * items. Empty {@code columns} shows no header.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param roots the top-level items, in order; item ids are unique across the whole tree, nested at most 128 levels
     *              below the roots
     * @param expandedIds the expanded branches' ids, each a branch of this tree; a collapsed branch's children are not
     *                    shown
     * @param selectedIds the selected items' ids, each an item of this tree
     * @param activateAction what a double click or Enter on an item reports after selecting it; blank for none
     * @param columns the header's columns, with unique ids; empty for no header
     * @since 0.18.0 for {@code columns}
     */
    public record Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction, List<Column> columns) implements Node {
        public Tree {
            ModelChecks.id(id); Objects.requireNonNull(state);
            roots = List.copyOf(roots);
            expandedIds = Set.copyOf(expandedIds);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            columns = List.copyOf(columns);
            ModelChecks.unique(columns, Column::id);
        }
        /** A tree without column headers. */
        public Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction) {
            this(id, state, roots, expandedIds, selectedIds, activateAction, List.of());
        }
    }

    /**
     * A path of crumbs that can turn into a typed path. Clicking a crumb reports {@link UiEvent.Selected} with its id;
     * a crumb's context action reports only its {@link UiEvent.Action} (it does not navigate). Clicking the free area
     * reports {@code editAction}. While {@code editing} the kit shows a text field holding {@code value}; when editing
     * starts the kit focuses the field and selects its text, and {@link UiView#focus(String)} returns the focus to it.
     * The field reports typing as {@link UiEvent.Edited}, Enter as {@code submitAction} and Escape or leaving the field
     * as {@code cancelAction}. When the crumbs do not fit, the leading ones slide out so the last stays visible.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param crumbs the path, root first, with unique ids; the last crumb is the current location
     * @param label the accessible name of the crumbs and of the field; not blank
     * @param editing whether the path shows as a text field instead of crumbs
     * @param value the field's text; an update with a different value replaces what the field holds
     * @param editAction what a left click on the free area beside the crumbs reports, typically to start editing; blank
     *                   for none
     * @param submitAction what Enter in the field reports while editing; not blank while editing
     * @param cancelAction what Escape in the field, or the focus moving to another control, reports while editing; not
     *                     blank while editing
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

    /**
     * A thin rule between two items of an INLINE or ACTIONS stack, dividing a toolbar row into groups; never a stack's
     * first or last child and never beside another separator. It shows only while a shown item stands between it and
     * the previous shown separator (or the stack's start) and a shown item follows it, so a row never starts, ends or
     * doubles with a rule.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @since 0.18.0
     */
    public record Separator(String id, ControlState state) implements Node {
        public Separator { ModelChecks.id(id); Objects.requireNonNull(state); }
    }

    /**
     * A message for the person reading a form or a pane: a severity, one sentence and at most one SECONDARY or LINK
     * action. The presentation draws a box in the severity's colours with the severity's glyph, then its word
     * (Information, Warning or Error) before the wrapping text, and the action at the trailing edge. A Notice is a
     * section of a form or a child of a VERTICAL stack, nowhere else.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param severity how serious the message is: INFO, WARNING or DANGER
     * @param text the message, one sentence; not blank
     * @param actions at most one SECONDARY or LINK button
     * @since 0.18.0
     */
    public record Notice(String id, ControlState state, Severity severity, String text, List<Button> actions) implements Node {
        public Notice {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(severity);
            ModelChecks.label(text, "text");
            actions = List.copyOf(actions);
            if (actions.size() > 1) throw new IllegalArgumentException("A Notice has at most one action");
            if (actions.stream().anyMatch(button -> button.kind() != ButtonKind.SECONDARY && button.kind() != ButtonKind.LINK))
                throw new IllegalArgumentException("A Notice's action is SECONDARY or LINK");
        }
        /** A notice without an action. */
        public Notice(String id, ControlState state, Severity severity, String text) { this(id, state, severity, text, List.of()); }
    }

    /**
     * One labelled value of {@link Properties}.
     *
     * @param label the value's label; not blank and without a trailing colon, which the presentation adds where its look
     *              uses one
     * @param value the value's text, which may be empty; a non-empty value is also its tooltip, so a shortened value can
     *              be read in full
     * @param technical whether the value is a data value (a host, port, path or command), drawn in the data face and
     *                  shortened at its start so its end stays in view
     * @param severity a severity whose colour the value takes (INFO the accent, WARNING and DANGER the status colours),
     *                 held readable on its surface; absent for the ordinary value colour
     * @since 0.18.0
     */
    public record PropertyRow(String label, String value, boolean technical, Optional<Severity> severity) {
        public PropertyRow {
            ModelChecks.label(label, "label");
            if (label.strip().endsWith(":")) throw new IllegalArgumentException("A property label carries no colon; the presentation punctuates it");
            Objects.requireNonNull(value); Objects.requireNonNull(severity);
        }
        /** A value without a severity. */
        public PropertyRow(String label, String value, boolean technical) { this(label, value, technical, Optional.empty()); }
    }

    /**
     * A read-only list of labelled values, such as a selected host's properties. A blank {@code title} shows no heading.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param title the heading over the rows; blank for none
     * @param rows the labelled values, top to bottom; at least one
     * @since 0.18.0
     */
    public record Properties(String id, ControlState state, String title, List<PropertyRow> rows) implements Node {
        public Properties {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(title);
            rows = List.copyOf(rows);
            if (rows.isEmpty()) throw new IllegalArgumentException("Properties need at least one row");
        }
    }

    /**
     * A slot for one specialised component that the plugin registers with
     * {@link UiView#registerContent(CustomContent)}: the content's id and kind must match this node's {@code contentId}
     * and {@code kind}, and the component must have no parent and must not be registered in another slot. A scroll pane
     * is judged by its view. An EDITOR slot admits a text area, text pane, editor pane or purpose-built canvas; the other
     * kinds admit only a canvas: a component that is not a panel, text component, button, label, list, table, tree,
     * combo box, spinner or slider. The host sets the component's foreground to the theme's text colour and its
     * background to the theme's input colour at the workspace opacity, and makes it opaque only at full opacity, on
     * every theme or opacity change and every move. The content's lifetime closes when the node leaves the view, when
     * an update changes its {@code contentId} or {@code kind}, or when the view closes.
     *
     * @param id the node id, unique in its view
     * @param state visibility and interaction state
     * @param contentId the id the registered content carries; not blank and unique among the view's Custom nodes
     * @param kind the kind of component the slot admits
     * @param accessibleName the slot's accessible name; not blank
     */
    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
        public Custom {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.id(contentId);
            Objects.requireNonNull(kind);
            ModelChecks.label(accessibleName, "accessibleName");
        }
    }
}
