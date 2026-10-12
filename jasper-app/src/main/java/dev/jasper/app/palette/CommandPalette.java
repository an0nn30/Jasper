package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 * Jasper's palette: a query row with an optional scope chip, a one-line result list with section headings (or the
 * empty line, or a step's small form), and a hint bar with the selected row's detail and its scope's other verbs.
 * The module supplies its shared surface, outline and measured rows on the chrome {@link KitContext}. Every control
 * remains a retained kit view. The
 * query field keeps focus; the host moves the selection and routes the keys. EDT only.
 */
public final class CommandPalette extends dev.jasper.ui.swing.SurfacePanel implements AutoCloseable {
    static final String QUERY = "query", CHIP = "chip", RESULTS = "results", HINT = "hint", EMPTY = "empty", STEP = "step",
        RUN = "run", VERB = "verb:";
    private static final String STEP_FIELD = "step.field.";

    private final KitContext kit;
    private final dev.jasper.app.platform.PresentationIcons presentationIcons = new dev.jasper.app.platform.PresentationIcons();
    private final boolean macOs;
    private final Consumer<String> queryChanged;
    private final ObjIntConsumer<PaletteEntry> execute;
    private final Function<String, Optional<ScopePrefix>> prefixes;
    private final Consumer<PaletteScope> chipTyped;
    private final List<KitView> views = new ArrayList<>();
    private final KitView chipView, queryView, resultsView, emptyView, hintView, stepTitleView, stepErrorView;
    private final JPanel fieldRow, hintBar, cards, stepPanel, stepTitleRow, stepErrorRow;
    /** One shell row per step field: a fixed label column and the field, each its own kit view. */
    private final List<StepRow> stepRows = new ArrayList<>();
    private final CardLayout cardLayout = new CardLayout();
    private final AutoCloseable themeSubscription;
    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;

    private String query = "", placeholder = "Search everything", chip, emptyText = "";
    private List<PaletteEntry> entries = List.of();
    private int selected = -1;
    private long resultCount, revision;
    private String listCard = EMPTY;
    private String stepTitle;
    private List<PaletteStep.Field> stepFields = List.of();
    private final List<String> stepTexts = new ArrayList<>();
    private String stepError;
    private int stepFocus = -1;
    private boolean closed;

    CommandPalette(KitContext kit, boolean macOs, Consumer<String> queryChanged, ObjIntConsumer<PaletteEntry> execute,
                   Function<String, Optional<ScopePrefix>> prefixes, Consumer<PaletteScope> chipTyped) {
        super(kit, dev.jasper.ui.swing.SurfacePanel.Kind.POPUP);
        this.kit = presentationIcons.context(Objects.requireNonNull(kit));
        kit = this.kit;
        this.macOs = macOs;
        this.queryChanged = Objects.requireNonNull(queryChanged);
        this.execute = Objects.requireNonNull(execute);
        this.prefixes = Objects.requireNonNull(prefixes);
        this.chipTyped = Objects.requireNonNull(chipTyped);
        setOpaque(false);
        // AWT retargets a press to the nearest ancestor that has mouse listeners. Without one here, a
        // press on the chip, the hint bar's detail text or the field row's padding would bubble past
        // the palette to the overlay behind it, which dismisses on any press it receives.
        var swallowClicks = new MouseAdapter() { };
        addMouseListener(swallowClicks);
        addMouseMotionListener(swallowClicks);
        // Focus cycles within the palette and always lands on the query; the host routes Tab between a step's fields.
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return queryInput(); }
            @Override public Component getComponentBefore(Container root, Component current) { return queryInput(); }
            @Override public Component getFirstComponent(Container root) { return queryInput(); }
            @Override public Component getLastComponent(Container root) { return queryInput(); }
            @Override public Component getDefaultComponent(Container root) { return queryInput(); }
        });

        chipView = view(chipSpec());
        chipView.setVisible(false);
        queryView = view(querySpec());
        fieldRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_FIELD_HEIGHT, false);
        fieldRow.setLayout(new BorderLayout());
        fieldRow.setOpaque(false);
        fieldRow.add(chipView, BorderLayout.WEST);
        fieldRow.add(queryView, BorderLayout.CENTER);
        add(fieldRow, BorderLayout.NORTH);

        resultsView = view(resultsSpec());
        emptyView = view(emptySpec());
        var empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.add(emptyView);
        stepTitleView = view(stepTitleSpec());
        stepErrorView = view(stepErrorSpec());
        stepTitleRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        stepTitleRow.setLayout(new BorderLayout());
        stepTitleRow.setOpaque(false);
        stepTitleRow.add(stepTitleView, BorderLayout.CENTER);
        stepErrorRow = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_STEP_TITLE_HEIGHT, false);
        stepErrorRow.setLayout(new BorderLayout());
        stepErrorRow.setOpaque(false);
        stepErrorRow.add(stepErrorView, BorderLayout.CENTER);
        stepErrorRow.setVisible(false);
        stepPanel = new JPanel();
        stepPanel.setLayout(new javax.swing.BoxLayout(stepPanel, javax.swing.BoxLayout.Y_AXIS));
        stepPanel.setOpaque(false);
        cards = new JPanel(cardLayout);
        cards.setOpaque(false);
        cards.add(resultsView, RESULTS);
        cards.add(empty, EMPTY);
        cards.add(stepPanel, STEP);
        cardLayout.show(cards, listCard);
        add(cards, BorderLayout.CENTER);

        hintView = view(hintSpec());
        hintBar = dev.jasper.ui.swing.SurfacePanel.row(kit, DesignMetrics.Role.PALETTE_HINT_HEIGHT, true, dev.jasper.ui.theme.SemanticColor.BORDER_MENU);
        hintView.setHintPresentation(HINT, true);
        hintBar.add(hintView, BorderLayout.CENTER);
        add(hintBar, BorderLayout.SOUTH);

        themeSubscription = kit.binding().subscribe(pair -> refreshTheme());
        refreshTheme();
        setChip(null, null);
        updateHint();
    }

    private KitView view(ViewSpec spec) {
        var view = new KitView(kit, spec, this::event);
        views.add(view);
        return view;
    }

    private int metric(DesignMetrics.Role role) { return kit.metrics().value(role); }
    private int scaled(DesignMetrics.Role role) { return UIScale.scale(metric(role)); }
    private Color color(SemanticColor role) { return kit.theme().semantic().color(role); }

    // ---- Descriptors ------------------------------------------------------------------------------------------------

    private ViewSpec chipSpec() {
        return new ViewSpec(new Nodes.Chip(CHIP, ControlState.READY, chip == null ? "All" : chip, Nodes.Hue.BLUE));
    }

    private ViewSpec querySpec() {
        String name = chip == null ? "All" : chip;
        // One surface throughout: the query shows the card inside its input outline, as before.
        return new ViewSpec(new Nodes.Field(QUERY, ControlState.READY, Nodes.FieldKind.SEARCH, query, placeholder,
            "Search " + name.toLowerCase(Locale.ROOT), "", Optional.empty(), Nodes.FieldPresentation.TRANSPARENT));
    }

    private void renderResults() {
        presentationIcons.reconcile(() -> render(resultsView, resultsSpec()));
    }

    private ViewSpec resultsSpec() {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < entries.size(); i++) items.add(item(i, entries.get(i)));
        Set<String> selection = selected < 0 ? Set.of() : Set.of(id(selected));
        return new ViewSpec(new Nodes.ItemList(RESULTS, ControlState.READY, Nodes.ListKind.RESULTS, items, selection, RUN, List.of(),
            Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(visibleRows)));
    }

    private static String id(int index) { return "entry." + index; }

    private ListItem item(int index, PaletteEntry entry) {
        String id = id(index);
        return switch (entry) {
            case PaletteEntry.Header header -> new ListItem(id, Optional.empty(),
                List.of(text(id + ".title", header.label(), Nodes.TextRole.CAPTION)), List.of(), List.of(), true);
            case PaletteEntry.Item item -> {
                PaletteRow row = item.row();
                // Preserve the semantic name and scoped monochrome artwork in this receiving kit context.
                Optional<Nodes.Node> icon = Optional.ofNullable(row.icon()).flatMap(value -> presentationIcons.name(value))
                    .map(name -> new Nodes.Icon(id + ".icon", ControlState.READY, name, Nodes.Hue.NONE, Nodes.IconSize.RAIL, row.title()));
                Nodes.TextRole role = !row.enabled() ? Nodes.TextRole.DESCRIPTION
                    : item.scope().monospaceRows() ? Nodes.TextRole.MONO : Nodes.TextRole.BODY;
                yield new ListItem(id, icon, List.of(text(id + ".title", row.title(), role)),
                    row.detail() == null ? List.of() : List.of(text(id + ".detail", row.detail(), Nodes.TextRole.BODY)),
                    row.tag() == null ? List.of() : List.of(text(id + ".tag", row.tag(), Nodes.TextRole.CAPTION)));
            }
            case PaletteEntry.More more -> new ListItem(id, Optional.empty(),
                List.of(text(id + ".title", "More in " + more.scope().label() + "…", Nodes.TextRole.DESCRIPTION)), List.of(), List.of());
        };
    }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, value, role);
    }

    private ViewSpec emptySpec() {
        return new ViewSpec(text(EMPTY, emptyText, Nodes.TextRole.DESCRIPTION));
    }

    /** The selected row's detail (or tag) and its scope's second and third verbs, as links. */
    private ViewSpec hintSpec() {
        var children = new ArrayList<Nodes.Node>();
        children.add(text(HINT + ".text", hintText(), Nodes.TextRole.CAPTION));
        if (selectedEntry() instanceof PaletteEntry.Item item) {
            List<PaletteVerb> verbs = item.scope().verbs();
            for (int verb = 1; verb < Math.min(3, verbs.size()); verb++)
                children.add(new Nodes.Button(VERB + verb, ControlState.READY, verbs.get(verb).label() + " " + keys(verb), "",
                    Nodes.ButtonKind.LINK, VERB + verb, "", Nodes.ButtonPresentation.EMBEDDED));
        }
        return new ViewSpec(new Nodes.Stack(HINT, ControlState.READY, Nodes.LayoutRole.NAVIGATION, children));
    }

    private ViewSpec stepTitleSpec() {
        return new ViewSpec(text(STEP + ".title", stepTitle == null ? "" : stepTitle, Nodes.TextRole.CAPTION));
    }

    private ViewSpec stepErrorSpec() {
        var shown = new ControlState(stepError != null, true, false, "");
        return new ViewSpec(new Nodes.Stack(STEP + ".error", shown, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot(STEP + ".error.status", ControlState.READY, Nodes.StatusKind.DANGER),
            text(STEP + ".error.text", stepError == null ? "" : stepError, Nodes.TextRole.BODY))));
    }

    private static String stepLabel(PaletteStep.Field field) { return field.label().isBlank() ? field.name() : field.label(); }

    /** The row's own label column names the field, so the field is EMBEDDED: its label stays its accessible name. */
    private ViewSpec stepFieldSpec(int index) {
        return new ViewSpec(new Nodes.Field(STEP_FIELD + index, ControlState.READY, Nodes.FieldKind.TEXT, stepTexts.get(index), "",
            stepLabel(stepFields.get(index)), "", Optional.empty(), Nodes.FieldPresentation.EMBEDDED));
    }

    /** Rebuilds the step's rows: the title row, one row per field and the (hidden) error row. */
    private void buildStepRows() {
        clearStepRows();
        stepPanel.add(stepTitleRow);
        for (int i = 0; i < stepFields.size(); i++) {
            var row = new StepRow(view(new ViewSpec(text(STEP + ".label." + i, stepLabel(stepFields.get(i)), Nodes.TextRole.CAPTION))), view(stepFieldSpec(i)));
            stepRows.add(row);
            stepPanel.add(row.panel);
        }
        stepPanel.add(stepErrorRow);
        stepPanel.add(javax.swing.Box.createVerticalGlue());
        applyStepGeometry();
    }

    private void clearStepRows() {
        for (StepRow row : stepRows) {
            row.label.close(); row.field.close();
            views.remove(row.label); views.remove(row.field);
        }
        stepRows.clear();
        stepPanel.removeAll();
    }

    /** The former step geometry: 4/12 row padding, a fixed label column and a gap before the field. */
    private void applyStepGeometry() {
        int side = scaled(DesignMetrics.Role.PALETTE_STEP_SIDE_PADDING);
        stepTitleRow.setBorder(new EmptyBorder(0, side, 0, side));
        stepErrorRow.setBorder(new EmptyBorder(0, side, 0, side));
        stepPanel.revalidate();
    }

    private void render(KitView view, ViewSpec spec) { if (!view.isClosed()) view.update(spec); }

    // ---- Events -----------------------------------------------------------------------------------------------------

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(QUERY) -> edited(edited.text(), true);
            case UiEvent.Edited edited when edited.id().startsWith(STEP_FIELD) -> {
                int index = Integer.parseInt(edited.id().substring(STEP_FIELD.length()));
                if (index < stepTexts.size()) stepTexts.set(index, edited.text());
            }
            case UiEvent.Selected chosen when chosen.id().equals(RESULTS) ->
                chosen.ids().stream().findFirst().ifPresent(id -> select(Integer.parseInt(id.substring("entry.".length()))));
            case UiEvent.Action action when action.id().equals(RUN) -> executeSelected(0);
            case UiEvent.Action action when action.id().startsWith(VERB) -> executeSelected(Integer.parseInt(action.id().substring(VERB.length())));
            default -> { }
        }
    }

    /**
     * The scope prefix text a chip consumed while the field may still show it. The kit applies the shortened query one
     * event-loop turn after the edit (Swing forbids document writes while it notifies), so a key queued before then
     * reports text that still begins with the prefix; that text is stripped, as the old synchronous filter never let
     * it into the field. Cleared once the field shows the query, or by any edit without the prefix.
     */
    private String consumedPrefix;

    /**
     * The query changed, typed ({@code typed}) or set by the host. A scope prefix typed with no chip ("history > ls")
     * leaves the query and becomes the chip, as the field's prefix filter did; deleting text never makes a chip.
     */
    private void edited(String text, boolean typed) {
        if (consumedPrefix != null) {
            if (typed && text.startsWith(consumedPrefix)) text = text.substring(consumedPrefix.length());
            else consumedPrefix = null;
        }
        String previous = query;
        Optional<ScopePrefix> prefix = text.equals(previous) || removal(previous, text) ? Optional.empty() : prefixes.apply(text);
        if (prefix.isPresent()) consumedPrefix = text.substring(0, text.length() - prefix.get().rest().length());
        query = prefix.map(ScopePrefix::rest).orElse(text);
        render(queryView, querySpec());
        if (consumedPrefix != null) settleConsumedPrefix();
        queryChanged.accept(query);
        prefix.ifPresent(found -> chipTyped.accept(found.scope()));
    }

    /** After the kit's own deferred update (queued first), forgets the consumed prefix once the field shows the query. */
    private void settleConsumedPrefix() {
        java.awt.EventQueue.invokeLater(() -> {
            if (closed || consumedPrefix == null) return;
            if (queryInput() instanceof JTextComponent input && input.getText().equals(query)) consumedPrefix = null;
        });
    }

    /** Whether {@code next} is {@code previous} with one stretch of it deleted. */
    static boolean removal(String previous, String next) {
        if (next.length() >= previous.length()) return false;
        int common = 0;
        while (common < next.length() && previous.charAt(common) == next.charAt(common)) common++;
        return previous.endsWith(next.substring(common));
    }

    // ---- Query and chip ---------------------------------------------------------------------------------------------

    /** Moves keyboard focus to the query field. */
    public void focusQuery() { if (!queryView.isClosed()) queryView.focus(QUERY); }

    String queryText() { return query; }

    /** The query view's mounted text input, the palette's one focus target. */
    private Component queryInput() { return first(queryView, JTextComponent.class); }

    private static <T extends Component> T first(Component component, Class<T> type) {
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = first(child, type);
            if (found != null) return found;
        }
        return null;
    }

    /** Replaces the query as if typed (a typed scope prefix still becomes the chip); the same text changes nothing. */
    void setQueryText(String text) { if (!Objects.requireNonNull(text).equals(query)) edited(text, false); }

    /** Shows {@code label}'s chip, or none for null; the field and list are named after it. The icon stays off the chip. */
    void setChip(String label, Icon ignoredIcon) {
        chip = label;
        render(chipView, chipSpec());
        chipView.setVisible(label != null);
        chipView.getAccessibleContext().setAccessibleName(label == null ? null : "Scope: " + label);
        render(queryView, querySpec());
        resultsView.getAccessibleContext().setAccessibleName(label == null ? "All" : label);
        fieldRow.revalidate();
        fieldRow.repaint();
    }

    /** The chip's label; null while no chip is set. */
    String chipText() { return chip; }
    void setPlaceholder(String text) { placeholder = text == null ? "" : text; render(queryView, querySpec()); }
    String placeholder() { return placeholder; }
    boolean composing() { return !queryView.isClosed() && queryView.composing(QUERY); }

    // ---- Results ----------------------------------------------------------------------------------------------------

    /** Replaces the list, selecting {@code selectionKey} when present and otherwise the first selectable entry. */
    void setEntries(List<PaletteEntry> next, String selectionKey, String empty) {
        long rows = next.stream().filter(entry -> entry instanceof PaletteEntry.Item).count();
        if (rows > PaletteResults.MAX_ROWS) throw new IllegalArgumentException("Too many palette results");
        int chosen = -1, first = -1;
        for (int i = 0; i < next.size(); i++) {
            PaletteEntry entry = next.get(i);
            if (entry.selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(entry.key())) chosen = i;
        }
        entries = List.copyOf(next);
        selected = chosen >= 0 ? chosen : first;
        emptyText = empty == null ? "" : empty;
        resultCount = rows;
        revision++;
        listCard = first < 0 ? EMPTY : RESULTS;
        if (!stepShowing()) cardLayout.show(cards, listCard);
        render(emptyView, emptySpec());
        renderResults();
        updateHint();
        revalidate();
        repaint();
    }

    PaletteEntry selectedEntry() { return selected < 0 || selected >= entries.size() ? null : entries.get(selected); }

    String selectedKey() {
        PaletteEntry entry = selectedEntry();
        return entry == null ? null : entry.key();
    }

    /** Selects the first row with {@code rowId}, whichever scope it came from. */
    void selectRow(String rowId) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) { select(i); return; }
    }

    /** Moves {@code delta} selectable entries, skipping headings and stopping at either end. */
    void selectRelative(int delta) {
        if (selected < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = selected;
        for (int i = selected + direction; remaining > 0 && i >= 0 && i < entries.size(); i += direction)
            if (entries.get(i).selectable()) { target = i; remaining--; }
        select(target);
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size() || !entries.get(index).selectable()) return;
        selected = index;
        renderResults();
        updateHint();
    }

    void executeSelected() { executeSelected(0); }

    void executeSelected(int verb) {
        PaletteEntry entry = selectedEntry();
        if (entry != null) execute.accept(entry, verb);
    }

    int itemHeight() { return dev.jasper.ui.swing.ResultMetrics.height(kit, false); }

    /** How many result rows the list shows before it scrolls; section headings among them add their own height. */
    void setVisibleRows(int rows) { visibleRows = rows; renderResults(); revalidate(); }
    int visibleRows() { return visibleRows; }

    String hintText() {
        return switch (selectedEntry()) {
            case PaletteEntry.Item item -> item.row().detail() != null ? item.row().detail() : item.row().tag() != null ? item.row().tag() : "";
            case PaletteEntry.More more -> "Show every match in " + more.scope().label();
            case null, default -> "";
        };
    }

    List<String> hintActionTexts() {
        var texts = new ArrayList<String>();
        if (hintView.spec().root() instanceof Nodes.Stack stack)
            for (Nodes.Node child : stack.children()) if (child instanceof Nodes.Button button) texts.add(button.label());
        return texts;
    }

    /** As a click on the {@code index}th hint action. */
    void clickHintAction(int index) { executeSelected(index + 1); }

    private void updateHint() {
        String verbsDescription = "";
        if (selectedEntry() instanceof PaletteEntry.Item item) verbsDescription = verbsDescription(item.scope().verbs());
        else if (selectedEntry() instanceof PaletteEntry.More more) verbsDescription = keys(0) + " Show every match in " + more.scope().label();
        resultsView.getAccessibleContext().setAccessibleDescription(
            resultCount + " results" + (verbsDescription.isEmpty() ? "" : "; " + verbsDescription));
        render(hintView, hintSpec());
        hintBar.revalidate();
        hintBar.repaint();
    }

    /** Every verb, primary through third, in the base card's format: {@code key label  key label}. */
    private String verbsDescription(List<PaletteVerb> verbs) {
        if (verbs.size() < 2) return "";
        var text = new StringBuilder();
        for (int verb = 0; verb < Math.min(3, verbs.size()); verb++) {
            if (verb > 0) text.append("  ");
            text.append(keys(verb)).append(' ').append(verbs.get(verb).label());
        }
        return text.toString();
    }

    private String keys(int verb) {
        return (macOs ? new String[]{"⏎", "⌘⏎", "⇧⏎"} : new String[]{"Enter", "Ctrl+Enter", "Shift+Enter"})[verb];
    }

    // ---- Step -------------------------------------------------------------------------------------------------------

    /** Shows a form instead of the list; the chip and query stay and the hint bar hides. The first field takes focus. */
    void showStep(String title, List<PaletteStep.Field> fields) {
        stepTitle = title;
        stepFields = List.copyOf(fields);
        stepTexts.clear();
        for (PaletteStep.Field field : stepFields) stepTexts.add(field.prefill());
        stepError = null;
        render(stepTitleView, stepTitleSpec());
        render(stepErrorView, stepErrorSpec());
        stepErrorRow.setVisible(false);
        buildStepRows();
        cardLayout.show(cards, STEP);
        hintBar.setVisible(false);
        stepFocus = -1;
        focusStepField(1);
        revalidate(); repaint();
    }

    void hideStep() {
        stepTitle = null;
        stepFields = List.of();
        stepTexts.clear();
        stepError = null;
        stepFocus = -1;
        clearStepRows();
        render(stepTitleView, stepTitleSpec());
        render(stepErrorView, stepErrorSpec());
        stepErrorRow.setVisible(false);
        hintBar.setVisible(true);
        cardLayout.show(cards, listCard);
        revalidate(); repaint();
    }

    boolean stepShowing() { return stepFocus >= 0; }
    int stepFocusIndex() { return stepFocus; }
    String stepTitleText() { return stepTitle; }
    /** The step's error; null while none shows. */
    String stepErrorText() { return stepError; }
    int stepFieldCount() { return stepFields.size(); }

    /** Moves focus to the next (or previous) field, wrapping, and selects its text for replacement. */
    void focusStepField(int delta) {
        if (stepFields.isEmpty()) return;
        stepFocus = Math.floorMod(stepFocus + delta, stepFields.size());
        var view = stepRows.get(stepFocus).field;
        if (view.isClosed()) return;
        view.focus(STEP_FIELD + stepFocus);
        var input = stepInput(stepFocus);
        if (input != null) input.selectAll();
    }

    /** The mounted kit input of the {@code index}th step field (kit components are named after their node). */
    JTextComponent stepInput(int index) { return find(stepRows.get(index).field, STEP_FIELD + index + ".input", JTextComponent.class); }

    Map<String, String> stepValues() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < stepFields.size(); i++) values.put(stepFields.get(i).name(), stepTexts.get(i));
        return values;
    }

    void setStepError(String message) {
        stepError = message;
        render(stepErrorView, stepErrorSpec());
        stepErrorRow.setVisible(message != null);
        revalidate(); repaint();
    }

    // ---- Shell ------------------------------------------------------------------------------------------------------

    /** The kit views follow the theme themselves; the shell repaints its surface, outline and rule and re-measures. */
    void refreshTheme() {
        hintBar.setBorder(new EmptyBorder(scaled(DesignMetrics.Role.HAIRLINE), scaled(DesignMetrics.Role.PALETTE_HINT_SIDE_PADDING),
            0, scaled(DesignMetrics.Role.PALETTE_HINT_SIDE_PADDING)));
        int vertical = scaled(DesignMetrics.Role.PALETTE_FIELD_VERTICAL_PADDING), side = scaled(DesignMetrics.Role.PALETTE_FIELD_SIDE_PADDING);
        fieldRow.setBorder(new EmptyBorder(vertical, side, vertical, side));
        ((BorderLayout) fieldRow.getLayout()).setHgap(scaled(DesignMetrics.Role.PALETTE_CHIP_GAP));
        applyStepGeometry();
        revalidate();
        repaint();
    }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        int height = fieldRow.getPreferredSize().height + (stepShowing()
            ? stepTitleRow.getPreferredSize().height + stepRows.stream().mapToInt(row -> row.panel.getPreferredSize().height).sum()
                + (stepError != null ? stepErrorRow.getPreferredSize().height : 0)
            : listHeight() + hintBar.getPreferredSize().height);
        return new Dimension(scaled(DesignMetrics.Role.PALETTE_WIDTH), height + insets.top + insets.bottom);
    }

    /** Tall enough for the first {@link #visibleRows} rows and the headings above them; the rest scroll. */
    private int listHeight() {
        int row = itemHeight(), heading = dev.jasper.ui.swing.ResultMetrics.height(kit, true);
        if (EMPTY.equals(listCard)) return row;
        int height = 0;
        for (int i = 0, rows = 0; i < entries.size() && rows < visibleRows; i++) {
            boolean header = entries.get(i) instanceof PaletteEntry.Header;
            height += header ? heading : row;
            if (!header) rows++;
        }
        return height;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { themeSubscription.close(); } catch (Exception ignored) { /* closing a subscription has nothing to report */ }
        views.forEach(KitView::close);
        presentationIcons.close();
    }

    // ---- Package-private access for headless tests ------------------------------------------------------------------

    KitView queryView() { return queryView; }
    KitView chipView() { return chipView; }
    KitView resultsView() { return resultsView; }
    KitView hintView() { return hintView; }
    JPanel stepPanel() { return stepPanel; }
    JPanel stepTitleRow() { return stepTitleRow; }
    JPanel stepErrorRow() { return stepErrorRow; }
    JPanel stepRow(int index) { return stepRows.get(index).panel; }
    JComponent stepLabelView(int index) { return stepRows.get(index).label; }
    KitView stepFieldView(int index) { return stepRows.get(index).field; }
    KitView emptyView() { return emptyView; }
    JPanel fieldRow() { return fieldRow; }
    JPanel hintBar() { return hintBar; }
    List<PaletteEntry> entries() { return entries; }
    int selectedIndex() { return selected; }
    /** How many times the host replaced the list. */
    long revision() { return revision; }
    KitContext kit() { return kit; }

    static <T extends Component> T find(Component component, String name, Class<T> type) {
        if (type.isInstance(component) && name.equals(component.getName())) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }

    /** Retained views, laid out by the module's responsive native label/control composition. */
    private final class StepRow {
        final KitView label, field;
        final JPanel panel;
        StepRow(KitView label, KitView field) {
            this.label=label; this.field=field;
            panel=dev.jasper.ui.swing.SurfacePanel.labelledRow(kit,label,field);
        }
    }
}
