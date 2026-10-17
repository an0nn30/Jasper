package dev.jasper.app.palette;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.SurfacePanel;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

/**
 * The palette as the Run window (Enterprise presentation spec 11.2, the 06 Run Dialog mocks): a framed window with a
 * title row, the help row, "Open:" and its field, a bordered list of fixed height, the selected row's detail line, the
 * split-pane checkbox and a footer of the primary verb, Cancel and Browse…. Every region is a kit view presented with
 * {@link KitView#runWindow(boolean)} on a kit-painted {@link SurfacePanel}; this class only lays them out. It stays
 * open while the person works elsewhere ({@link #modeless()}). EDT only.
 */
public final class RunWindowView extends SurfacePanel implements PaletteView {
    static final String TITLE = "run.title", SHORTCUT = "run.shortcut", CLOSE = "run.close", HELP = "run.help", HELP_ICON = "run.help.icon",
        HELP_TEXT = "run.help.text", OPEN = "run.open", CHIP = "run.chip", QUERY = "run.query", SCOPES = "run.scopes", RESULTS = "run.results",
        EMPTY = "run.empty", DETAIL_LABEL = "run.detail.label", DETAIL_VALUE = "run.detail.value", SPLIT = "run.split",
        SPLIT_LABEL = "run.split.label", PRIMARY = "run.primary", CANCEL = "run.cancel", BROWSE = "run.browse", RUN = "run",
        VERB = "verb:", SCOPE = "scope:";
    /** The checkbox's label (spec 11.2). */
    static final String SPLIT_TEXT = "Open in a split pane";
    private static final String LIST = "list", NONE = "none", STEP = StepForm.STEP;

    private final KitContext kit;
    private final PaletteView.Host host;
    private final QueryEdits edits;
    private final Map<String, KitView> views = new LinkedHashMap<>();
    private final SurfacePanel fieldBox, listBox;
    private final JPanel cards;
    private final CardLayout cardLayout = new CardLayout();
    private final StepForm step;
    private final AutoCloseable themeSubscription;
    private int visibleRows = dev.jasper.app.config.PaletteSettings.DEFAULT_VISIBLE_ROWS;
    private String query = "", emptyText = "", listCard = NONE;
    private PaletteScope chip;
    private List<PaletteEntry> entries = List.of();
    private int selected = -1;
    private boolean split, closed;

    RunWindowView(KitContext kit, boolean macOs, PaletteView.Host host) {
        super(kit, SurfacePanel.Kind.RUN_WINDOW, () -> UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT)),
            () -> UIScale.scale(kit.metrics().value(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT)));
        this.kit = Objects.requireNonNull(kit);
        this.host = Objects.requireNonNull(host);
        this.edits = new QueryEdits(host.prefixes());
        setOpaque(false);
        // Presses that no control takes stop here, so the overlay behind never sees them; the host drags by them.
        var swallow = new MouseAdapter() { };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container root, Component current) { return queryInput(); }
            @Override public Component getComponentBefore(Container root, Component current) { return queryInput(); }
            @Override public Component getFirstComponent(Container root) { return queryInput(); }
            @Override public Component getLastComponent(Container root) { return queryInput(); }
            @Override public Component getDefaultComponent(Container root) { return queryInput(); }
        });
        // The step form first: the checkbox's and the primary button's descriptors ask whether a step is showing.
        step = new StepForm(kit);
        for (String id : List.of(TITLE, SHORTCUT, CLOSE, HELP, OPEN, CHIP, QUERY, SCOPES, RESULTS, EMPTY, DETAIL_LABEL, DETAIL_VALUE,
                SPLIT, SPLIT_LABEL, PRIMARY, CANCEL, BROWSE)) {
            var view = new KitView(kit, spec(id), this::handle);
            view.runWindow(true);
            views.put(id, view);
        }
        fieldBox = new SurfacePanel(kit, SurfacePanel.Kind.FIELD);
        fieldBox.setLayout(new FieldLayout());
        fieldBox.add(view(CHIP)); fieldBox.add(view(QUERY)); fieldBox.add(view(SCOPES));
        view(CHIP).setVisible(false);
        var empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        empty.add(view(EMPTY));
        cards = new JPanel(cardLayout);
        cards.setOpaque(false);
        cards.add(view(RESULTS), LIST);
        cards.add(empty, NONE);
        cards.add(step.panel(), STEP);
        cardLayout.show(cards, listCard);
        listBox = new SurfacePanel(kit, SurfacePanel.Kind.BOX);
        listBox.add(cards);
        setLayout(new WindowLayout());
        for (String id : List.of(TITLE, SHORTCUT, CLOSE, HELP, OPEN, DETAIL_LABEL, DETAIL_VALUE, SPLIT, SPLIT_LABEL, PRIMARY, CANCEL, BROWSE)) add(view(id));
        add(fieldBox);
        add(listBox);
        // The checkbox's label is its click target too, as a desktop checkbox's is.
        var toggleByLabel = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (splitAvailable()) setSplit(!split); }
        };
        view(SPLIT_LABEL).addMouseListener(toggleByLabel);
        var label = CardView.find(view(SPLIT_LABEL), SPLIT_LABEL, javax.swing.JLabel.class);
        if (label != null) label.addMouseListener(toggleByLabel);
        // The title row drags the window (spec 11.2), its text included: the title and shortcut labels carry tooltips, so a
        // press stops at them; they hand their mouse events to the view, where the host's drag listens (Rulings 14).
        var toView = new MouseAdapter() {
            private void pass(MouseEvent event) { dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(event.getComponent(), event, RunWindowView.this)); }
            @Override public void mousePressed(MouseEvent event) { pass(event); }
            @Override public void mouseReleased(MouseEvent event) { pass(event); }
            @Override public void mouseClicked(MouseEvent event) { pass(event); }
            @Override public void mouseDragged(MouseEvent event) { pass(event); }
        };
        for (String id : List.of(TITLE, SHORTCUT)) {
            view(id).addMouseListener(toView); view(id).addMouseMotionListener(toView);
            var text = CardView.find(view(id), id, javax.swing.JLabel.class);
            if (text != null) { text.addMouseListener(toView); text.addMouseMotionListener(toView); }
        }
        themeSubscription = kit.binding().subscribe(pair -> refreshTheme());
    }

    private KitView view(String id) { return views.get(id); }

    /** The kit view of region {@code id}, for headless tests. */
    KitView viewFor(String id) { return view(id); }

    private int s(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    private static Nodes.Text text(String id, String value, Nodes.TextRole role) { return new Nodes.Text(id, ControlState.READY, value, role); }

    private static ControlState enabled(boolean value) { return new ControlState(true, value, false, ""); }

    // ---- Descriptors ------------------------------------------------------------------------------------------------

    private ViewSpec spec(String id) {
        PaletteEntry selection = selectedEntry();
        return new ViewSpec(switch (id) {
            case TITLE -> text(TITLE, "Run", Nodes.TextRole.WINDOW_TITLE);
            case SHORTCUT -> text(SHORTCUT, host.openerShortcut().get(), Nodes.TextRole.CAPTION);
            case CLOSE -> new Nodes.Button(CLOSE, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, CLOSE, "Close", Nodes.ButtonPresentation.EMBEDDED);
            case HELP -> new Nodes.Stack(HELP, ControlState.READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
                new Nodes.Icon(HELP_ICON, ControlState.READY, "play.filled", Nodes.Hue.BLUE, Nodes.IconSize.PROMPT, "Run"),
                text(HELP_TEXT, helpText(), Nodes.TextRole.DESCRIPTION)), "");
            case OPEN -> text(OPEN, "Open:", Nodes.TextRole.LABEL);
            case CHIP -> new Nodes.Chip(CHIP, ControlState.READY, chip == null ? "All" : chip.label(), Nodes.Hue.BLUE);
            case QUERY -> new Nodes.Field(QUERY, ControlState.READY, Nodes.FieldKind.TEXT, query, "", "Open", "", Optional.empty(),
                Nodes.FieldPresentation.TRANSPARENT);
            case SCOPES -> new Nodes.MenuButton(SCOPES, ControlState.READY, "", "triangle.down", Nodes.ButtonKind.GHOST, "", scopeItems(),
                "Choose what to open", Nodes.ButtonPresentation.EMBEDDED);
            case RESULTS -> new Nodes.ItemList(RESULTS, ControlState.READY, Nodes.ListKind.RESULTS, items(),
                selected < 0 ? Set.of() : Set.of(entryId(selected)), RUN, List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(visibleRows));
            case EMPTY -> text(EMPTY, emptyText, Nodes.TextRole.DESCRIPTION);
            case DETAIL_LABEL -> text(DETAIL_LABEL, RunCopy.detailLabel(selection), Nodes.TextRole.BODY_STRONG);
            case DETAIL_VALUE -> text(DETAIL_VALUE, RunCopy.detailValue(selection), Nodes.TextRole.MONO);
            case SPLIT -> new Nodes.Toggle(SPLIT, enabled(splitAvailable()), SPLIT_TEXT, split, SPLIT, false, "");
            case SPLIT_LABEL -> new Nodes.Text(SPLIT_LABEL, enabled(splitAvailable()), SPLIT_TEXT, Nodes.TextRole.LABEL);
            case PRIMARY -> new Nodes.Button(PRIMARY, enabled(RunCopy.primaryEnabled(selection) && !step.showing()), RunCopy.primaryLabel(selection), "",
                Nodes.ButtonKind.PRIMARY, PRIMARY, "", Nodes.ButtonPresentation.STANDARD);
            case CANCEL -> new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, "", Nodes.ButtonPresentation.STANDARD);
            case BROWSE -> new Nodes.Button(BROWSE, ControlState.READY, "Browse…", "", Nodes.ButtonKind.SECONDARY, BROWSE, "", Nodes.ButtonPresentation.STANDARD);
            default -> throw new IllegalArgumentException(id);
        });
    }

    /** The help row's text: the chosen scope's description, else the fixed host string (Rulings 10). */
    String helpText() {
        return chip == null || chip.description().isBlank() ? RunCopy.HELP : chip.description();
    }

    /** "All", then each scope with its shortcut, as today's mode list orders them. */
    private List<MenuItem> scopeItems() {
        var items = new ArrayList<MenuItem>();
        items.add(new MenuItem(SCOPE + "all", "All", "", SCOPE + PaletteScope.ALL_ID, true, false, Nodes.Hue.NEUTRAL, "command_palette"));
        for (PaletteScope scope : host.scopes().get())
            items.add(new MenuItem(SCOPE + "item." + items.size(), scope.label(), "", SCOPE + scope.id(), true, false, Nodes.Hue.NEUTRAL,
                scope.shortcutActionId().orElse("")));
        return items;
    }

    private static String entryId(int index) { return "entry." + index; }

    private List<ListItem> items() {
        var items = new ArrayList<ListItem>();
        boolean matches = false;
        for (int i = 0; i < entries.size(); i++) {
            String id = entryId(i);
            switch (entries.get(i)) {
                case PaletteEntry.Header header -> {
                    if (header.kind() == PaletteEntry.Header.Kind.MATCHES) {
                        // One "Matches" heading for every scope's matches, its count on the right (spec 11.3). A heading has a
                        // title only, so the count is its second title node, which the kit lays at the band's end (Rulings 13).
                        if (matches) continue;
                        matches = true;
                        items.add(new ListItem(id, Optional.empty(), List.of(text(id + ".title", RunCopy.heading(header), Nodes.TextRole.CAPTION),
                            text(id + ".count", RunCopy.position(entries, selected), Nodes.TextRole.CAPTION)), List.of(), List.of(), true));
                    } else items.add(new ListItem(id, Optional.empty(), List.of(text(id + ".title", RunCopy.heading(header), Nodes.TextRole.CAPTION)),
                        List.of(), List.of(), true));
                }
                case PaletteEntry.Item item -> items.add(row(i, item));
                case PaletteEntry.More more -> items.add(new ListItem(id, Optional.empty(),
                    List.of(text(id + ".title", "More in " + more.scope().label() + "…", Nodes.TextRole.DESCRIPTION)), List.of(), List.of()));
            }
        }
        return items;
    }

    /** A row: its scope's hue square, its title, its detail in the data face, its tag, and its second and third verbs in its menu. */
    private ListItem row(int index, PaletteEntry.Item item) {
        String id = entryId(index);
        PaletteRow row = item.row();
        PaletteScope scope = item.scope();
        var square = new Nodes.Icon(id + ".swatch", ControlState.READY, "file", scope.hue(), Nodes.IconSize.SWATCH, scope.label());
        var verbs = scope.verbs();
        var menu = new ArrayList<MenuItem>();
        for (int verb = 1; verb < Math.min(3, verbs.size()); verb++) {
            String action = VERB + index + ":" + verb;
            menu.add(new MenuItem(action, verbs.get(verb).label(), "", action, true, false));
        }
        return new ListItem(id, Optional.of(square), List.of(text(id + ".title", row.title(), row.enabled() ? Nodes.TextRole.BODY : Nodes.TextRole.DESCRIPTION)),
            row.detail() == null ? List.of() : List.of(text(id + ".detail", row.detail(), Nodes.TextRole.MONO)),
            row.tag() == null ? List.of() : List.of(text(id + ".tag", row.tag(), Nodes.TextRole.CAPTION)), false,
            menu.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(menu)), "");
    }

    private void render(String... ids) {
        for (String id : ids) { var view = view(id); if (!view.isClosed()) view.update(spec(id)); }
        revalidate(); repaint();
    }

    /** The regions that follow the selection: the list (its selection and count), the detail line, the checkbox and the primary button. */
    private void renderSelection() {
        if (split && !splitAvailable()) split = false;
        render(RESULTS, DETAIL_LABEL, DETAIL_VALUE, SPLIT, SPLIT_LABEL, PRIMARY);
    }

    // ---- Events -----------------------------------------------------------------------------------------------------

    /** Every kit view's events, and the tests' way in. */
    void handle(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(QUERY) -> edited(edited.text(), true);
            case UiEvent.Selected chosen when chosen.id().equals(RESULTS) ->
                chosen.ids().stream().findFirst().ifPresent(id -> select(Integer.parseInt(id.substring("entry.".length()))));
            case UiEvent.Action action when action.id().equals(RUN) || action.id().equals(PRIMARY) -> executeSelected(0);
            case UiEvent.Action action when action.id().startsWith(VERB) -> {
                String[] parts = action.id().substring(VERB.length()).split(":");
                select(Integer.parseInt(parts[0]));
                executeSelected(Integer.parseInt(parts[1]));
            }
            case UiEvent.Action action when action.id().equals(CLOSE) || action.id().equals(CANCEL) -> host.cancel().run();
            case UiEvent.Action action when action.id().equals(BROWSE) -> host.browse().run();
            case UiEvent.Action action when action.id().startsWith(SCOPE) -> {
                String id = action.id().substring(SCOPE.length());
                host.chooseScope().accept(id.equals(PaletteScope.ALL_ID) ? null
                    : host.scopes().get().stream().filter(scope -> scope.id().equals(id)).findFirst().orElse(null));
            }
            case UiEvent.Toggled toggled when toggled.id().equals(SPLIT) -> setSplit(toggled.checked());
            default -> { }
        }
    }

    private void setSplit(boolean value) { split = value && splitAvailable(); render(SPLIT); }

    /** Whether the selected row's scope has a SPLIT verb and no step is showing (spec 11.2). */
    private boolean splitAvailable() {
        return !step.showing() && selectedEntry() instanceof PaletteEntry.Item item && RunCopy.splitVerb(item.scope()) >= 0;
    }

    private void edited(String text, boolean typed) {
        var outcome = edits.edited(query, text, typed);
        query = outcome.query();
        render(QUERY);
        if (edits.pending()) edits.settle(() -> closed, () -> queryInput() instanceof JTextComponent input ? input.getText() : null, () -> query);
        host.queryChanged().accept(query);
        outcome.prefix().ifPresent(found -> host.chooseScope().accept(found.scope()));
    }

    // ---- PaletteView ------------------------------------------------------------------------------------------------

    @Override public JComponent component() { return this; }
    @Override public boolean modeless() { return true; }
    /**
     * Each opening clears the checkbox (spec 11.2) and reads the opener's live COMMAND_PALETTE shortcut and the scopes
     * registered now (plugin scopes usually arrive after the window is built).
     */
    @Override public void opened() { split = false; render(SHORTCUT, SCOPES, SPLIT); }
    @Override public void focusQuery() { if (!view(QUERY).isClosed()) view(QUERY).focus(QUERY); }
    @Override public boolean composing() { return !view(QUERY).isClosed() && view(QUERY).composing(QUERY); }
    @Override public String queryText() { return query; }
    @Override public void setQueryText(String text) { if (!Objects.requireNonNull(text).equals(query)) edited(text, false); }

    @Override public void setChip(PaletteScope scope) {
        chip = scope;
        view(CHIP).setVisible(scope != null);
        render(CHIP, QUERY, HELP);
        fieldBox.revalidate();
    }

    /** The Run window's field shows no placeholder: the help row says what to type (Rulings 9). */
    @Override public void setPlaceholder(String text) { }

    @Override public void setEntries(List<PaletteEntry> next, String selectionKey, String empty) {
        if (next.stream().filter(entry -> entry instanceof PaletteEntry.Item).count() > PaletteResults.MAX_ROWS)
            throw new IllegalArgumentException("Too many palette results");
        int chosen = -1, first = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).selectable() && first < 0) first = i;
            if (selectionKey != null && selectionKey.equals(next.get(i).key())) chosen = i;
        }
        entries = List.copyOf(next);
        selected = chosen >= 0 ? chosen : first;
        emptyText = empty == null ? "" : empty;
        listCard = first < 0 ? NONE : LIST;
        if (!step.showing()) cardLayout.show(cards, listCard);
        render(EMPTY);
        renderSelection();
    }

    @Override public PaletteEntry selectedEntry() { return selected < 0 || selected >= entries.size() ? null : entries.get(selected); }
    @Override public String selectedKey() { var entry = selectedEntry(); return entry == null ? null : entry.key(); }

    @Override public void selectRow(String rowId) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i) instanceof PaletteEntry.Item item && item.row().id().equals(rowId)) { select(i); return; }
    }

    @Override public void selectRelative(int delta) {
        if (selected < 0 || delta == 0) return;
        int direction = Integer.signum(delta), remaining = Math.abs(delta), target = selected;
        for (int i = selected + direction; remaining > 0 && i >= 0 && i < entries.size(); i += direction)
            if (entries.get(i).selectable()) { target = i; remaining--; }
        select(target);
    }

    private void select(int index) {
        if (index < 0 || index >= entries.size() || !entries.get(index).selectable()) return;
        selected = index;
        renderSelection();
    }

    /** Enter's verb is verb 0, or the SPLIT verb while the box is ticked; the others keep their keys (spec 11.4). */
    @Override public void executeSelected(int verb) {
        PaletteEntry entry = selectedEntry();
        if (entry == null) return;
        int index = verb;
        if (verb == 0 && split && entry instanceof PaletteEntry.Item item && RunCopy.splitVerb(item.scope()) >= 0) index = RunCopy.splitVerb(item.scope());
        host.execute().accept(entry, index);
    }

    @Override public void setVisibleRows(int rows) { visibleRows = rows; render(RESULTS); }
    @Override public int visibleRows() { return visibleRows; }

    @Override public void showStep(String title, List<PaletteStep.Field> fields) {
        step.show(title, fields);
        cardLayout.show(cards, STEP);
        render(SPLIT, SPLIT_LABEL, PRIMARY);
    }

    @Override public void hideStep() {
        step.hide();
        cardLayout.show(cards, listCard);
        render(SPLIT, SPLIT_LABEL, PRIMARY);
    }

    @Override public boolean stepShowing() { return step.showing(); }
    @Override public void focusStepField(int delta) { step.focusField(delta); }
    @Override public Map<String, String> stepValues() { return step.values(); }
    @Override public void setStepError(String message) { step.setError(message); revalidate(); repaint(); }
    /** Follows a theme change; the title row's shortcut and the scope menu are read again, as a rebinding may come with it. */
    @Override public void refreshTheme() { step.applyGeometry(); render(SHORTCUT, SCOPES); }
    /** The Run window has no reveal (spec 11.6). */
    @Override public void setReveal(double fraction) { }
    @Override public int revealedHeight() { return getHeight(); }

    @Override public Dimension getPreferredSize() { return getLayout().preferredLayoutSize(this); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { themeSubscription.close(); } catch (Exception ignored) { /* closing a subscription has nothing to report */ }
        views.values().forEach(KitView::close);
        step.close();
    }

    private Component queryInput() { return CardView.find(view(QUERY), QUERY + ".input", JTextComponent.class); }

    /** The list box's height: {@code visible_rows} rows and two heading bands inside its edge (spec 11.2, Rulings 5). */
    int listHeight() {
        int hair = s(DesignMetrics.Role.HAIRLINE);
        return 2 * hair + visibleRows * s(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT) + 2 * (s(DesignMetrics.Role.RUN_WINDOW_HEADING_HEIGHT) + hair);
    }

    // ---- Package-private access for headless tests ------------------------------------------------------------------

    List<PaletteEntry> entries() { return entries; }
    int selectedIndex() { return selected; }
    boolean splitTicked() { return split; }
    SurfacePanel fieldBox() { return fieldBox; }
    SurfacePanel listBox() { return listBox; }
    StepForm step() { return step; }

    // ---- Layout -----------------------------------------------------------------------------------------------------

    /** The mocks' geometry from the Run window roles: the title row, the body's rows and the footer's buttons, right-packed. */
    private final class WindowLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

        private int content() { return s(DesignMetrics.Role.RUN_WINDOW_WIDTH) - 2 * s(DesignMetrics.Role.RUN_WINDOW_SIDE); }
        private int help() { return SurfacePanel.measuredHeight(view(HELP), content()); }
        private int line() { return Math.max(view(DETAIL_LABEL).getPreferredSize().height, view(DETAIL_VALUE).getPreferredSize().height); }
        /** The checkbox row: the classic box and its focus reserve, whichever face draws the toggle (plan 8a R7). */
        private int check() { return s(DesignMetrics.Role.CHECKBOX_BOX) + 2 * s(DesignMetrics.Role.FOCUS_RING); }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int hair = s(DesignMetrics.Role.HAIRLINE);
            int height = hair + s(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT) + hair
                + s(DesignMetrics.Role.RUN_WINDOW_HELP_TOP) + help() + s(DesignMetrics.Role.RUN_WINDOW_FIELD_TOP) + s(DesignMetrics.Role.FIELD_HEIGHT)
                + s(DesignMetrics.Role.RUN_WINDOW_LIST_TOP) + listHeight() + s(DesignMetrics.Role.RUN_WINDOW_DETAIL_TOP) + line()
                + s(DesignMetrics.Role.RUN_WINDOW_CHECK_TOP) + check() + s(DesignMetrics.Role.RUN_WINDOW_BOTTOM)
                + hair + s(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT) + hair;
            return new Dimension(s(DesignMetrics.Role.RUN_WINDOW_WIDTH) + 2 * hair, height);
        }

        @Override public void layoutContainer(Container parent) {
            int hair = s(DesignMetrics.Role.HAIRLINE), side = s(DesignMetrics.Role.RUN_WINDOW_SIDE), ring = s(DesignMetrics.Role.FOCUS_RING);
            int right = getWidth() - hair, x = hair + side, content = Math.max(0, getWidth() - 2 * hair - 2 * side);
            int title = s(DesignMetrics.Role.RUN_WINDOW_TITLE_HEIGHT), close = s(DesignMetrics.Role.RUN_WINDOW_CLOSE);
            view(CLOSE).setBounds(right - s(DesignMetrics.Role.RUN_WINDOW_CLOSE_SIDE) - close, hair + (title - close) / 2, close, close);
            var shortcut = view(SHORTCUT).getPreferredSize();
            int shortcutX = view(CLOSE).getX() - s(DesignMetrics.Role.RUN_WINDOW_SHORTCUT_GAP) - shortcut.width;
            view(SHORTCUT).setBounds(shortcutX, hair + (title - shortcut.height) / 2, shortcut.width, shortcut.height);
            var name = view(TITLE).getPreferredSize();
            int titleX = hair + s(DesignMetrics.Role.RUN_WINDOW_TITLE_SIDE);
            // The title takes its own width, so the rest of the row stays an empty area that drags the window.
            view(TITLE).setBounds(titleX, hair + (title - name.height) / 2, Math.max(0, Math.min(name.width, shortcutX - titleX)), name.height);
            int y = hair + title + hair + s(DesignMetrics.Role.RUN_WINDOW_HELP_TOP);
            int help = help();
            view(HELP).setBounds(x, y, content, help);
            y += help + s(DesignMetrics.Role.RUN_WINDOW_FIELD_TOP);
            int field = s(DesignMetrics.Role.FIELD_HEIGHT), column = s(DesignMetrics.Role.RUN_WINDOW_LABEL_COLUMN);
            var open = view(OPEN).getPreferredSize();
            view(OPEN).setBounds(x, y + (field - open.height) / 2, Math.min(open.width, column), open.height);
            fieldBox.setBounds(x + column - ring, y - ring, Math.max(0, content - column) + 2 * ring, field + 2 * ring);
            y += field + s(DesignMetrics.Role.RUN_WINDOW_LIST_TOP);
            int list = listHeight();
            listBox.setBounds(x, y, content, list);
            y += list + s(DesignMetrics.Role.RUN_WINDOW_DETAIL_TOP);
            int line = line();
            var label = view(DETAIL_LABEL).getPreferredSize();
            view(DETAIL_LABEL).setBounds(x, y + (line - label.height) / 2, Math.min(label.width, content), label.height);
            int valueX = x + label.width + (label.width > 0 ? s(DesignMetrics.Role.RUN_WINDOW_DETAIL_LABEL_GAP) : 0);
            var value = view(DETAIL_VALUE).getPreferredSize();
            view(DETAIL_VALUE).setBounds(valueX, y + (line - value.height) / 2, Math.max(0, x + content - valueX), value.height);
            y += line + s(DesignMetrics.Role.RUN_WINDOW_CHECK_TOP);
            int check = check();
            var box = view(SPLIT).getPreferredSize();
            view(SPLIT).setBounds(x - ring, y + (check - box.height) / 2, box.width, box.height);
            var splitLabel = view(SPLIT_LABEL).getPreferredSize();
            int labelX = x + s(DesignMetrics.Role.CHECKBOX_BOX) + s(DesignMetrics.Role.CHECKBOX_LABEL_GAP);
            view(SPLIT_LABEL).setBounds(labelX, y + (check - splitLabel.height) / 2, Math.max(0, Math.min(splitLabel.width, x + content - labelX)), splitLabel.height);
            int foot = s(DesignMetrics.Role.RUN_WINDOW_FOOTER_HEIGHT), height = s(DesignMetrics.Role.BUTTON_HEIGHT);
            int buttonY = getHeight() - hair - foot + (foot - height) / 2, buttonX = right - side;
            for (String id : List.of(BROWSE, CANCEL, PRIMARY)) {
                int width = Math.max(s(DesignMetrics.Role.RUN_WINDOW_BUTTON_WIDTH), view(id).getPreferredSize().width);
                buttonX -= width;
                view(id).setBounds(buttonX, buttonY, width, height);
                buttonX -= s(DesignMetrics.Role.RUN_WINDOW_BUTTON_GAP);
            }
        }
    }

    /** Inside the field box: the chip, the query filling the row, and the ▼ at the box's end. */
    private final class FieldLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return parent.getSize(); }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
        @Override public void layoutContainer(Container parent) {
            var insets = parent.getInsets();
            int x = insets.left + s(DesignMetrics.Role.RUN_WINDOW_FIELD_SIDE), top = insets.top;
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom), end = parent.getWidth() - insets.right;
            int menu = s(DesignMetrics.Role.RUN_WINDOW_MENU_WIDTH);
            view(SCOPES).setBounds(end - menu, top, menu, height);
            if (view(CHIP).isVisible()) {
                var chipSize = view(CHIP).getPreferredSize();
                view(CHIP).setBounds(x, top + (height - chipSize.height) / 2, chipSize.width, chipSize.height);
                x += chipSize.width + s(DesignMetrics.Role.RUN_WINDOW_FIELD_SIDE);
            }
            view(QUERY).setBounds(x, top, Math.max(0, end - menu - x), height);
        }
    }
}
