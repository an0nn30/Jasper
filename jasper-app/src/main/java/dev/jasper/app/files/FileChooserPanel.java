package dev.jasper.app.files;

import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Crumb;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.SurfacePanel;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.JTextComponent;

/**
 * The chooser's view over a {@link ChooserModel}: a layout-only shell (toolbar row, a draggable split between the
 * places tree and the file table or its message, an optional preview and the bottom row) hosting kit views on the
 * chrome {@link KitContext}: navigation buttons, kit breadcrumbs, search, the places tree, a file table with the
 * overlay scroll policy, connecting/error/empty states, the preview and the name field with Cancel and the primary
 * button. It never waits for I/O. Whoever shows it starts the model, closes the dialog when {@code finish} runs (an
 * empty list means cancelled) and then calls {@link #close()}. File names are shown as plain text. UI thread.
 */
public final class FileChooserPanel extends JPanel {
    static final String FILES = "files";
    static final String PLACES = "places";
    static final String PATH = "path";
    static final String SEARCH = "search";
    static final String NAME = "name";
    static final String PREVIEW_TOGGLE = "previewToggle";
    private static final String PREVIEW_CONTENT = "chooser.preview";
    private static final String PIN = "pin:";
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MENU = SystemInfo.isMacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private final KitContext kit;
    private final ChooserModel model;
    private final ChooserPrefs prefs;
    private final Path home;
    private final Consumer<List<Location>> finish;
    private final Predicate<Location> confirmReplace;
    private final Supplier<Optional<String>> askFolderName;
    private final List<Runnable> sourceRemovals = new ArrayList<>();
    private final List<KitView> views = new ArrayList<>();
    private final KitView navigationView, pathView, toolsView, headingView, placesView, filesView, messageView, previewHeaderView,
        previewContentView, statusView, buttonsView;
    private final KitView nameView;
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final JPanel previewPane = new JPanel(new BorderLayout());
    private final JTextArea previewText = new JTextArea();
    private final JSplitPane split;
    private final List<dev.jasper.ui.swing.SurfacePanel> panes = new ArrayList<>();
    private final AutoCloseable themeSubscription;
    private final AutoCloseable typography;
    private final Timer loadingDelay;
    private final Map<String, Places.Place> placeIds = new LinkedHashMap<>();
    private final List<Object> navigationItems = new ArrayList<>();
    private final Map<String, Location> crumbTargets = new LinkedHashMap<>();
    private final List<String> crumbLabels = new ArrayList<>();
    private String placeSelection;
    private String name = "";
    private String status = "";
    private boolean editingPath;
    private String pathValue = "";
    private boolean previewVisible;
    private Optional<String> centerMessage = Optional.empty();
    /** The center state's title over its message (the message is the sentence that says what happened). */
    private String centerTitle = "Loading…";
    private Optional<String> centerActionLabel = Optional.empty();
    private Runnable centerAction = () -> { };
    private boolean updating;
    private boolean finished;
    private boolean closed;

    /**
     * @param kit the chrome kit context the views use
     * @param home the local home folder, for the Places list
     * @param finish receives the chosen locations, or an empty list on cancel; runs at most once
     * @param confirmReplace asks whether to replace an existing file in Save
     * @param askFolderName asks for a new folder's name; empty when the user cancelled
     */
    public FileChooserPanel(KitContext kit, ChooserModel model, ChooserPrefs prefs, Path home, Consumer<List<Location>> finish,
                            Predicate<Location> confirmReplace, Supplier<Optional<String>> askFolderName) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit);
        this.model = Objects.requireNonNull(model);
        this.prefs = Objects.requireNonNull(prefs);
        this.home = Objects.requireNonNull(home);
        this.finish = Objects.requireNonNull(finish);
        this.confirmReplace = Objects.requireNonNull(confirmReplace);
        this.askFolderName = Objects.requireNonNull(askFolderName);
        ChooserRequest request = model.request();
        loadingDelay = new Timer(kit.metrics().value(DesignMetrics.Role.CHOOSER_LOADING_DELAY_MILLIS), event -> showCenter());
        loadingDelay.setRepeats(false);
        if (request.mode() == ChooserRequest.Mode.SAVE) name = request.suggestedName().orElse("");
        previewVisible = prefs.preview();
        rebuildNavigationItems();
        navigationView = view(navigationSpec());
        pathView = view(pathSpec());
        toolsView = view(toolsSpec());
        headingView = view(new ViewSpec(new Nodes.Text("places.heading", ControlState.READY, "Places", Nodes.TextRole.CAPTION)));
        placesView = view(placesSpec());
        filesView = view(filesSpec());
        messageView = view(messageSpec());
        // The places and the listing are the manager window's rail and list columns inside the chooser's own panes; the
        // message is a list column's empty state on the pane surface.
        placesView.managerColumn(KitView.ManagerColumn.RAIL);
        filesView.managerColumn(KitView.ManagerColumn.LIST);
        messageView.panelSurface(false);
        previewHeaderView = view(previewHeaderSpec());
        previewContentView = view(new ViewSpec(new Nodes.Custom("preview.content", ControlState.READY, PREVIEW_CONTENT, Nodes.CustomKind.EDITOR, "File preview")));
        previewText.setEditable(false);
        previewText.setOpaque(false);
        previewText.setBorder(BorderFactory.createEmptyBorder(metric(DesignMetrics.Role.CARD_PADDING), metric(DesignMetrics.Role.CARD_PADDING),
            metric(DesignMetrics.Role.CARD_PADDING), metric(DesignMetrics.Role.CARD_PADDING)));
        // The preview is specialized read-only text content: selectable and scrollable, painted from module roles.
        var previewScroll = new JScrollPane(previewText);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.setOpaque(false);
        previewScroll.getViewport().setOpaque(false);
        previewContentView.registerContent(PREVIEW_CONTENT, previewScroll, () -> { });
        statusView = view(statusSpec());
        buttonsView = view(buttonsSpec());
        nameView = request.mode() == ChooserRequest.Mode.OPEN ? null : view(nameSpec());

        var content = new JPanel(new BorderLayout()); content.setOpaque(false);
        var toolbar = toolbar(); var bottom = bottom();
        content.add(toolbar, BorderLayout.NORTH);
        split = body();
        content.add(split, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        add(SurfacePanel.scrolling(kit,content,width -> {
            int bodyWidth=((SurfacePanel.NativeSplit)split).receivingWidth(width);
            return SurfacePanel.measuredHeight(toolbar,width)+SurfacePanel.measuredHeight(bottom,width)
                +SurfacePanel.measuredHeight(split.getRightComponent(),bodyWidth);
        }));
        bindKeys();
        if (request.mode() == ChooserRequest.Mode.SAVE)
            request.suggestedName().ifPresent(suggested -> {
                int dot = suggested.lastIndexOf('.');
                // The kit field holds the host's value; select the name part so typing replaces it, as before.
                nameInput().ifPresent(input -> input.select(0, dot > 0 ? dot : suggested.length()));
            });
        previewPane.setVisible(previewVisible);
        for (ChooserSource source : model.sources()) {
            try { sourceRemovals.add(source.onChanged(this::rebuildNavigation)); }
            catch (RuntimeException failed) { /* a failing source just does not update the pane */ }
        }
        themeSubscription = kit.binding().subscribe(pair -> applySurfaces());
        applySurfaces();
        model.addListener(this::sync);
        sync();
        typography=dev.jasper.ui.swing.NativeControlStyles.observeTypography(this::applySurfaces);
    }

    private KitView view(ViewSpec spec) {
        var view = new KitView(kit, spec, this::event);
        views.add(view);
        return view;
    }

    private int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    private Color color(SemanticColor role) { return kit.theme().semantic().color(role); }

    /**
     * Lets Enter anywhere in the dialog press the primary button, as a default button did; controls owning Enter keep it.
     * Like a disabled default button, the binding is disabled while the primary is, so Enter is then not consumed.
     */
    public void installDefaultAction(JRootPane root) {
        var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "chooser.approve");
        root.getActionMap().put("chooser.approve", new AbstractAction() {
            @Override public boolean isEnabled() { return primaryEnabled(); }
            @Override public void actionPerformed(ActionEvent event) { if (primaryEnabled()) approve(); }
        });
    }

    /**
     * Whether the primary button (and so Enter) acts. Invalid input is reported in the status line when the primary is
     * pressed, as before Plan 2, so the primary is disabled only once the chooser has closed.
     */
    private boolean primaryEnabled() { return !closed; }

    /** Stops listening to the sources and the theme; call once the dialog has closed. */
    public void close() {
        if (closed) return;
        closed = true;
        loadingDelay.stop();
        sourceRemovals.forEach(Runnable::run);
        sourceRemovals.clear();
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { themeSubscription.close(); } catch (Exception ignored) { /* closing a subscription has nothing to report */ }
        views.forEach(KitView::close);
    }

    // Package-private accessors for headless tests.
    /** The name the primary button acts on: the field's text in Save and Directory, the selection's names in Open. */
    String nameText() { return name; }
    /** The mounted name field; Open has none. */
    Optional<JTextComponent> nameInput() {
        return nameView == null ? Optional.empty() : Optional.ofNullable(find(nameView, NAME + ".input", JTextComponent.class));
    }
    List<Object> navigationItems() { return List.copyOf(navigationItems); }
    Optional<Places.Place> selectedPlace() { return Optional.ofNullable(placeIds.get(placeSelection)); }
    String placeId(Places.Place place) {
        return placeIds.entrySet().stream().filter(entry -> entry.getValue().equals(place)).map(Map.Entry::getKey).findFirst().orElseThrow();
    }
    String statusText() { return status; }
    Optional<String> centerMessage() { return centerMessage; }
    Optional<String> centerAction() { return centerActionLabel; }
    void pressCenterAction() { centerAction.run(); }
    String previewText() { return previewText.getText(); }
    String pathText() { return String.join(" › ", crumbLabels); }
    List<dev.jasper.ui.swing.SurfacePanel> roundedPanes() { return List.copyOf(panes); }
    JSplitPane split() { return split; }

    private JComponent toolbar() {
        var bar = dev.jasper.ui.swing.SurfacePanel.toolbar(kit, navigationView, pathView, toolsView);
        bar.setOpaque(false);
        int vertical = metric(DesignMetrics.Role.CHOOSER_TOOLBAR_VERTICAL_PADDING), side = metric(DesignMetrics.Role.CHOOSER_TOOLBAR_SIDE_PADDING);
        bar.setBorder(BorderFactory.createEmptyBorder(vertical, side, vertical, side));
        return bar;
    }

    private JSplitPane body() {
        var navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);
        int pad = metric(DesignMetrics.Role.ICON_GAP), inset = metric(DesignMetrics.Role.COLUMN_LIST_SIDE);
        // Places heads its rail as the listing's column titles head theirs: on the rows' text inset.
        headingView.setBorder(BorderFactory.createEmptyBorder(pad, 2 * inset, pad, pad));
        placesView.setBorder(BorderFactory.createEmptyBorder(metric(DesignMetrics.Role.COLUMN_RAIL_TOP), inset, 0, inset));
        filesView.setBorder(BorderFactory.createEmptyBorder(0, inset, 0, inset));
        navigation.add(headingView, BorderLayout.NORTH);
        navigation.add(placesView, BorderLayout.CENTER);
        var navigationPane = rounded(navigation, () -> headingView.getHeight());
        navigationPane.fill(dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR);
        navigationPane.setMinimumSize(new java.awt.Dimension(metric(DesignMetrics.Role.CHOOSER_NAVIGATION_MIN_WIDTH), 0));
        center.setOpaque(false);
        center.add(rounded(filesView, this::filesHeaderHeight), "table");
        center.add(rounded(messageView, () -> 0), "message");

        int vertical = metric(DesignMetrics.Role.CHOOSER_PREVIEW_VERTICAL_PADDING), card = metric(DesignMetrics.Role.CARD_PADDING);
        previewPane.setOpaque(false);
        ((BorderLayout) previewPane.getLayout()).setVgap(metric(DesignMetrics.Role.CHOOSER_PREVIEW_GAP));
        // The heading's text lines up with the preview text inside its pane.
        previewHeaderView.setBorder(BorderFactory.createEmptyBorder(0, card, 0, card));
        previewPane.add(previewHeaderView, BorderLayout.NORTH);
        // The preview text is mono text inside the chooser's rounded pane, as the listing is; the pane keeps the
        // panes' gutter on its leading side and ends with them.
        previewPane.add(rounded(previewContentView, () -> 0), BorderLayout.CENTER);
        previewPane.setBorder(BorderFactory.createEmptyBorder(vertical, metric(DesignMetrics.Role.WORKSPACE_GUTTER), 0, 0));
        previewPane.setPreferredSize(new java.awt.Dimension(metric(DesignMetrics.Role.CHOOSER_PREVIEW_WIDTH), 0));

        var main = SurfacePanel.sidecar(kit,center,previewPane,width -> {
            var listing=find(filesView,FILES,JScrollPane.class);
            int row=((javax.swing.Scrollable)listing.getViewport().getView()).getScrollableUnitIncrement(new Rectangle(),javax.swing.SwingConstants.VERTICAL,1);
            var rowsInset=listing.getViewportBorder()==null?new java.awt.Insets(0,0,0,0):listing.getViewportBorder().getBorderInsets(listing);
            return listing.getColumnHeader().getPreferredSize().height+rowsInset.top+rowsInset.bottom+DesignMetrics.NATIVE_BODY_VISIBLE_UNITS*row;
        },width -> previewPane.getInsets().top+previewPane.getInsets().bottom
            +SurfacePanel.measuredHeight(previewHeaderView,Math.max(0,width-previewPane.getInsets().left-previewPane.getInsets().right))
            +((BorderLayout)previewPane.getLayout()).getVgap()
            +DesignMetrics.NATIVE_BODY_VISIBLE_UNITS*previewText.getFontMetrics(previewText.getFont()).getHeight()+previewText.getInsets().top+previewText.getInsets().bottom
            +((JScrollPane)previewText.getParent().getParent()).getHorizontalScrollBar().getPreferredSize().height);
        var pane = new SurfacePanel.NativeSplit(kit,navigationPane, main,() -> metric(DesignMetrics.Role.CHOOSER_NAVIGATION_WIDTH),
            () -> Math.max(SurfacePanel.bodyLaneWidth(kit),filesView.getMinimumSize().width)
                +find(filesView,FILES,JScrollPane.class).getVerticalScrollBar().getPreferredSize().width) {
            @Override public void updateUI() {
                // The divider is an unpainted resize gutter. Keep Swing's keyboard and drag handling.
                setUI(new BasicSplitPaneUI() {
                    @Override public BasicSplitPaneDivider createDefaultDivider() {
                        return new BasicSplitPaneDivider(this) {
                            @Override public void paint(Graphics graphics) {
                                graphics.setColor(splitPane.getBackground());
                                graphics.fillRect(0, 0, getWidth(), getHeight());
                            }
                        };
                    }
                });
                setDividerSize(UIScale.scale(kit.metrics().value(DesignMetrics.Role.WORKSPACE_GUTTER)));
                int gutter = UIScale.scale(kit.metrics().value(DesignMetrics.Role.CHOOSER_SIDE_PADDING));
                setBorder(BorderFactory.createEmptyBorder(0, gutter, 0, gutter));
                setBackground(kit.theme().semantic().color(SemanticColor.SURFACE_DIALOG));
            }
        };
        pane.setContinuousLayout(true);
        return pane;
    }

    /** The file table's column header height: the listing's rounded surface starts below it, on the chrome. */
    private int filesHeaderHeight() {
        var scroll = find(filesView, FILES, JScrollPane.class);
        if (scroll == null || scroll.getColumnHeader() == null) return 0;
        var header = scroll.getColumnHeader();
        return SwingUtilities.convertPoint(header, 0, header.getHeight(), filesView).y;
    }

    private dev.jasper.ui.swing.SurfacePanel rounded(JComponent content, IntSupplier top) {
        var pane = new dev.jasper.ui.swing.SurfacePanel(kit, dev.jasper.ui.swing.SurfacePanel.Kind.CONTENT, top);
        pane.add(content, BorderLayout.CENTER);
        panes.add(pane);
        return pane;
    }

    /** The name row (Save and Directory) and the buttons. Open has no name row: the selection is what opens. */
    private JComponent bottom() {
        var actions = new JPanel(new BorderLayout(metric(DesignMetrics.Role.FOOTER_STATUS_GAP), 0));
        actions.setOpaque(false);
        actions.add(statusView, BorderLayout.CENTER);
        actions.add(buttonsView, BorderLayout.EAST);
        // The status and the actions sit in the window footer band, as a manager window's do; the name row stays above it.
        var bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        if (nameView != null) {
            int vertical = metric(DesignMetrics.Role.CHOOSER_BOTTOM_VERTICAL_PADDING), side = metric(DesignMetrics.Role.CHOOSER_BOTTOM_SIDE_PADDING);
            nameView.setBorder(BorderFactory.createEmptyBorder(vertical, side, vertical, side));
            bottom.add(nameView, BorderLayout.NORTH);
        }
        bottom.add(SurfacePanel.footerBand(kit, actions), BorderLayout.SOUTH);
        return bottom;
    }

    /** Surfaces come from the theme: the dialog behind, the gutter, the panes' corners and the preview text. */
    private void applySurfaces() {
        if (closed) return;
        setBackground(color(SemanticColor.SURFACE_DIALOG));
        if (split != null) split.setBackground(getBackground());
        for (var pane : panes) pane.setBackground(getBackground());
        previewText.setFont(dev.jasper.ui.swing.NativeControlStyles.font(kit,Typography.Role.MONO_SMALL));
        previewText.setForeground(color(SemanticColor.TEXT_PRIMARY));
        previewText.setCaretColor(previewText.getForeground());
        previewText.setSelectionColor(color(SemanticColor.SELECTION_BG));
        previewText.setSelectedTextColor(color(SemanticColor.TEXT_PRIMARY));
        repaint();
    }

    private void bindKeys() {
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "back", model::goBack);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "forward", model::goForward);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "up", model::goUp);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_L, MENU), "path", this::editPath);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, MENU | InputEvent.SHIFT_DOWN_MASK), "hidden", this::toggleHidden);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        // Backspace in the file listing goes up, as before; text fields keep their own Backspace.
        bind(filesView, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "up", model::goUp);
    }

    private static void bind(JComponent component, int condition, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(condition).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    // ---- Kit events -------------------------------------------------------------------------------------------------

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Action action -> action(action.id());
            case UiEvent.Edited edited -> {
                switch (edited.id()) {
                    case SEARCH -> { if (!updating) model.setFilter(edited.text()); }
                    case NAME -> name = edited.text();
                    case PATH -> pathValue = edited.text();
                    default -> { }
                }
            }
            case UiEvent.Selected selected -> {
                switch (selected.id()) {
                    case FILES -> { if (!updating) selectionChanged(selected.ids()); }
                    // Moving the highlight (a left click or the arrow keys) opens the place, as before. A right click
                    // never changes the selection, so it never reaches here.
                    case PLACES -> {
                        if (!updating) openPlace(selected.ids().stream().findFirst().orElse(null));
                    }
                    case PATH -> selected.ids().stream().findFirst().map(crumbTargets::get).ifPresent(model::navigate);
                    default -> { }
                }
            }
            case UiEvent.Sorted sorted -> {
                switch (sorted.columnId()) {
                    case "name" -> model.sortBy(ChooserModel.Column.NAME);
                    case "modified" -> model.sortBy(ChooserModel.Column.MODIFIED);
                    case "size" -> model.sortBy(ChooserModel.Column.SIZE);
                    default -> { }
                }
            }
            case UiEvent.Toggled toggled when toggled.id().equals(PREVIEW_TOGGLE) -> setPreviewVisible(toggled.checked());
            // Section headings stay open; their disclosure arrow changes nothing.
            case UiEvent.Expanded expanded -> render(placesView, placesSpec());
            default -> { }
        }
    }

    private void action(String id) {
        if (id.startsWith(PIN)) { pinTarget(id.substring(PIN.length())).ifPresent(this::togglePin); return; }
        switch (id) {
            case "back" -> model.goBack();
            case "forward" -> model.goForward();
            case "up" -> model.goUp();
            case "refresh" -> model.refresh();
            case "newFolder" -> newFolder();
            case "open" -> activateSelection();
            case "approve" -> approve();
            case "cancel" -> cancel();
            case "center" -> centerAction.run();
            case "path.edit" -> editPath();
            case "path.go" -> submitPath();
            case "path.done" -> finishEditingPath();
            default -> { }
        }
    }

    /** A pin menu names its folder: {@code row:<path>}, {@code place:<id>} or {@code crumb:<id>}. */
    private Optional<Location> pinTarget(String key) {
        if (key.startsWith("row:")) return Optional.of(model.location().at(key.substring(4)));
        if (key.startsWith("place:")) return Optional.ofNullable(placeIds.get(key.substring(6))).map(Places.Place::location);
        if (key.startsWith("crumb:")) return Optional.ofNullable(crumbTargets.get(key.substring(6)));
        return Optional.empty();
    }

    /** Shows a place; the place already shown is not listed again. */
    private void openPlace(String id) {
        Places.Place place = placeIds.get(id);
        if (place == null || place.location().equals(model.location())) {
            syncNavigationSelection();
            return;
        }
        model.navigate(place.location());
    }

    // ---- Model to views ---------------------------------------------------------------------------------------------

    /** Brings every view up to date with the model. */
    private void sync() {
        if (closed) return;
        updating = true;
        try {
            status = statusLine();
            if (model.loading() && model.error().isEmpty()) {
                if (!loadingDelay.isRunning()) loadingDelay.restart();
                if (model.entries().isEmpty()) {
                    centerMessage = Optional.empty();
                    centerActionLabel = Optional.empty();
                    centerCards.show(center, "table");
                } else {
                    showCenter();
                }
            } else {
                loadingDelay.stop();
                showCenter();
            }
            selectNavigationPlace();
            renderAll();
        } finally {
            updating = false;
        }
    }

    private void renderAll() {
        render(navigationView, navigationSpec());
        render(pathView, pathSpec());
        render(toolsView, toolsSpec());
        render(placesView, placesSpec());
        render(filesView, filesSpec());
        render(messageView, messageSpec());
        render(previewHeaderView, previewHeaderSpec());
        syncPreviewText();
        render(statusView, statusSpec());
        render(buttonsView, buttonsSpec());
        if (nameView != null) render(nameView, nameSpec());
    }

    private void render(KitView view, ViewSpec spec) {
        if (view == null || closed) return;
        boolean was = updating;
        updating = true;
        try { view.update(spec); } finally { updating = was; }
    }

    private void syncNavigationSelection() {
        selectNavigationPlace();
        render(placesView, placesSpec());
    }

    /** The place shown is highlighted: the exact place, or else its source root. */
    private void selectNavigationPlace() {
        Location shown = model.location();
        String exact = null, sameRoot = null;
        for (var entry : placeIds.entrySet()) {
            Places.Place place = entry.getValue();
            if (place.location().equals(shown)) { exact = entry.getKey(); break; }
            if (sameRoot == null && place.section() == Places.Section.SOURCE && place.location().source().equals(shown.source())
                    && place.location().root().equals(shown.root())) sameRoot = entry.getKey();
        }
        placeSelection = exact != null ? exact : sameRoot;
    }

    /** The message area: connecting, loading, an error with Retry, or an empty folder; otherwise the table. */
    private void showCenter() {
        Optional<String> text;
        Optional<String> action = Optional.empty();
        String title = "Loading…";
        if (model.connectingTo().isPresent()) {
            title = "Connecting…";
            text = Optional.of("Connecting to " + safe(model.connectingTo().get()) + "…");
            action = Optional.of("Cancel connection");
            centerAction = model::cancelConnection;
        } else if (model.error().isPresent()) {
            title = "Couldn't Open Folder";
            text = Optional.of(safe(model.error().get()));
            if (model.retryable()) {
                action = Optional.of("Retry");
                centerAction = model::retry;
            }
        } else if (model.loading()) {
            text = model.entries().isEmpty() ? Optional.of("Loading…") : Optional.empty();
        } else if (model.entries().isEmpty()) {
            title = model.filter().isBlank() ? "Empty Folder" : "No Matches";
            text = Optional.of(model.filter().isBlank() ? "This folder is empty." : "No items match your search.");
        } else {
            text = Optional.empty();
        }
        centerMessage = text;
        centerTitle = title;
        centerActionLabel = action;
        render(messageView, messageSpec());
        centerCards.show(center, text.isPresent() ? "message" : "table");
    }

    private ViewSpec navigationSpec() {
        return new ViewSpec(new Nodes.Stack("navigation", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            tool("back", "back", "Back", model.canGoBack()),
            tool("forward", "forward", "Forward", model.canGoForward()),
            tool("up", "chevron.up", "Up", model.canGoUp()),
            tool("refresh", "refresh", "Refresh", true))));
    }

    private static Nodes.Button tool(String id, String icon, String tip, boolean enabled) {
        return new Nodes.Button(id, new ControlState(true, enabled, false, ""), "", icon, Nodes.ButtonKind.GHOST, id, tip);
    }

    /** The crumbs for the place shown: a source's root label first, then its folders; while connecting, the root alone. */
    private ViewSpec pathSpec() {
        crumbTargets.clear();
        crumbLabels.clear();
        var crumbs = new ArrayList<Crumb>();
        Location shown = model.location();
        if (!shown.isLocal()) crumb(crumbs, model.rootLabel(shown), Location.home(shown.source(), shown.root()));
        if (!shown.path().equals(Location.HOME))
            for (Location step : model.chain()) crumb(crumbs, model.name(step), step);
        return new ViewSpec(new Nodes.Breadcrumbs(PATH, ControlState.READY, crumbs, "Location", editingPath, pathValue,
            "path.edit", "path.go", "path.done"));
    }

    private void crumb(List<Crumb> crumbs, String text, Location target) {
        String id = "c" + crumbs.size();
        String label = safe(text);
        crumbTargets.put(id, target);
        crumbLabels.add(label);
        crumbs.add(new Crumb(id, label, target.path().equals(Location.HOME) ? List.of() : List.of(pinItem(target, "crumb:" + id))));
    }

    private MenuItem pinItem(Location folder, String key) {
        return new MenuItem("pin", prefs.isPinned(folder) ? "Unpin" : "Pin", "bookmark", PIN + key, true, false);
    }

    private ViewSpec toolsSpec() {
        var children = new ArrayList<Nodes.Node>();
        children.add(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, model.filter(),
            "Search " + safe(model.name(model.location())), "Search", ""));
        if (model.request().mode() == ChooserRequest.Mode.SAVE)
            children.add(new Nodes.Button("newFolder", ControlState.READY, "New folder", "folder.add", Nodes.ButtonKind.SECONDARY, "newFolder", ""));
        children.add(new Nodes.Icon("preview.icon", ControlState.READY, "preview", Nodes.Hue.NONE, Nodes.IconSize.CONTROL, "Preview pane"));
        children.add(new Nodes.Toggle(PREVIEW_TOGGLE, ControlState.READY, "Preview pane", previewVisible, PREVIEW_TOGGLE, true));
        return new ViewSpec(new Nodes.Stack("tools", ControlState.READY, Nodes.LayoutRole.NAVIGATION, children));
    }

    private ViewSpec placesSpec() {
        var roots = new ArrayList<TreeItem>();
        var open = new HashSet<String>();
        String section = null, headingId = null, heading = null;
        List<TreeItem> children = null;
        for (var entry : placeIds.entrySet()) {
            Places.Place place = entry.getValue();
            var item = new TreeItem(entry.getKey(), safe(place.label()), Optional.of(place.icon()), true, List.of(), OptionalInt.empty(), false,
                place.tooltip().isEmpty() ? "" : safe(place.tooltip()), place.connected() ? Optional.of(Nodes.StatusKind.SUCCESS) : Optional.empty(),
                List.of(), Optional.of(place.location().path().equals(Location.HOME) ? List.of() : List.of(pinItem(place.location(), "place:" + entry.getKey()))));
            if (place.section() == Places.Section.PLACES) { roots.add(item); continue; }
            String key = place.section() + "|" + place.heading();
            if (!key.equals(section)) {
                if (children != null) roots.add(branch(headingId, heading, children));
                section = key; heading = place.heading(); headingId = "section/" + roots.size(); children = new ArrayList<>();
                open.add(headingId);
            }
            children.add(item);
        }
        if (children != null) roots.add(branch(headingId, heading, children));
        return new ViewSpec(new Nodes.Tree(PLACES, ControlState.READY, roots, open,
            // No activate action: as the old JList, Enter on the places is the dialog's (its default action).
            placeSelection == null ? Set.of() : Set.of(placeSelection), ""));
    }

    private static TreeItem branch(String id, String heading, List<TreeItem> children) {
        return new TreeItem(id, safe(heading), Optional.empty(), false, children);
    }

    private ViewSpec filesSpec() {
        boolean save = model.request().mode() == ChooserRequest.Mode.SAVE;
        var rows = new ArrayList<TableRow>();
        for (ChooserEntry entry : model.entries()) {
            var cells = Map.of(
                "name", safe(entry.name()),
                "modified", entry.modified() < 0 ? "" : modifiedText(entry.modified()),
                "size", entry.directory() ? "" : sizeText(entry.size()));
            rows.add(new TableRow(entry.path(), cells, Optional.of(entry.directory() ? "folder" : "file"), save && !entry.directory(),
                Optional.of(entry.directory() ? List.of(pinItem(model.location().at(entry.path()), "row:" + entry.path())) : List.of())));
        }
        var selected = new HashSet<String>();
        for (ChooserEntry entry : model.selection()) if (model.entries().contains(entry)) selected.add(entry.path());
        return new ViewSpec(new Nodes.Table(FILES, ControlState.READY, List.of(
                column("name", "Name", Nodes.TextRole.BODY, false, DesignMetrics.Role.CHOOSER_NAME_COLUMN, ChooserModel.Column.NAME),
                column("modified", "Modified", Nodes.TextRole.DESCRIPTION, false, DesignMetrics.Role.CHOOSER_MODIFIED_COLUMN, ChooserModel.Column.MODIFIED),
                column("size", "Size", Nodes.TextRole.DESCRIPTION, true, DesignMetrics.Role.CHOOSER_SIZE_COLUMN, ChooserModel.Column.SIZE)),
            rows, selected, "open", List.of(),
            model.request().multiple() ? Nodes.SelectionMode.MULTIPLE : Nodes.SelectionMode.SINGLE, Nodes.ScrollPolicy.OVERLAY));
    }

    private Column column(String id, String title, Nodes.TextRole role, boolean numeric, DesignMetrics.Role width, ChooserModel.Column column) {
        return new Column(id, title, role, numeric, kit.metrics().value(width),
            model.sortColumn() == column ? Optional.of(model.ascending()) : Optional.empty());
    }

    /**
     * Connecting, loading, an error with Retry, an empty folder or no matches: a list column's standard empty state (a
     * hidden-header panel's body, so it takes the panel empty geometry) with a title over the sentence that says why.
     */
    private ViewSpec messageSpec() {
        String text = centerMessage.orElse("");
        String icon = model.connectingTo().isPresent() ? "server" : model.error().isPresent() ? "warning"
            : !model.loading() && model.entries().isEmpty() && !model.filter().isBlank() ? "search" : "folder";
        var actions = centerActionLabel.<List<Nodes.Node>>map(label -> List.of(new Nodes.Button("center", ControlState.READY, label, "",
            Nodes.ButtonKind.SECONDARY, "center", ""))).orElse(List.of());
        var empty = new Nodes.Empty("message", ControlState.READY,
            new Nodes.Icon("message.icon", ControlState.READY, icon, Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, centerTitle),
            centerTitle, text.equals(centerTitle) ? "" : text, actions);
        return new ViewSpec(new Nodes.Panel("message.panel", ControlState.READY, new Nodes.Header("message.header",
            new ControlState(false, true, false, ""), "Folder", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), empty, Optional.empty()));
    }

    private ViewSpec previewHeaderSpec() {
        String title, details;
        List<ChooserEntry> selected = model.selection();
        if (selected.size() != 1) {
            title = selected.isEmpty() ? "No selection" : selected.size() + " items selected";
            details = "";
        } else {
            ChooserEntry entry = selected.getFirst();
            Optional<PreviewInfo> loaded = model.preview();
            title = safe(entry.name());
            details = entry.directory()
                ? loaded.map(preview -> preview.items() == 1 ? "1 item" : preview.items() + " items").orElse("Folder")
                : sizeText(entry.size()) + (entry.modified() >= 0 ? " · " + modifiedText(entry.modified()) : "");
        }
        return new ViewSpec(new Nodes.Stack("preview.header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("preview.title", ControlState.READY, title, Nodes.TextRole.BODY_STRONG),
            new Nodes.Text("preview.details", ControlState.READY, details, Nodes.TextRole.DESCRIPTION))));
    }

    private void syncPreviewText() {
        if (!previewVisible) return;
        List<ChooserEntry> selected = model.selection();
        String text = "";
        if (selected.size() == 1) {
            Optional<PreviewInfo> loaded = model.preview();
            text = loaded.flatMap(PreviewInfo::text).orElse(loaded.filter(PreviewInfo::binary).isPresent() ? "Binary file" : "");
        }
        if (!previewText.getText().equals(text)) {
            previewText.setText(text);
            previewText.setCaretPosition(0);
        }
    }

    private ViewSpec statusSpec() {
        return new ViewSpec(new Nodes.Text("status", ControlState.READY, status, Nodes.TextRole.CAPTION));
    }

    private ViewSpec buttonsSpec() {
        ChooserRequest request = model.request();
        var children = new ArrayList<Nodes.Node>();
        if (request.mode() == ChooserRequest.Mode.OPEN && request.suffix().isPresent()) children.add(types());
        children.add(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""));
        children.add(new Nodes.Button("primary", new ControlState(true, primaryEnabled(), false, ""), switch (request.mode()) {
            case OPEN -> "Open";
            case SAVE -> "Save";
            case DIRECTORY -> "Choose";
        }, "", Nodes.ButtonKind.PRIMARY, "approve", ""));
        return new ViewSpec(new Nodes.Stack("buttons", ControlState.READY, Nodes.LayoutRole.ACTIONS, children));
    }

    private Nodes.Choice types() {
        String label = model.request().suffix().map(suffix -> "*" + suffix).orElse("All files");
        return new Nodes.Choice("types", ControlState.READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("type", label, Optional.empty())),
            "type", "File type", "types");
    }

    private ViewSpec nameSpec() {
        boolean directory = model.request().mode() == ChooserRequest.Mode.DIRECTORY;
        var field = new Nodes.Field(NAME, ControlState.READY, Nodes.FieldKind.TEXT, name, "", directory ? "Folder" : "File name", "approve");
        return new ViewSpec(new Nodes.FormRow("name.row", ControlState.READY, directory ? "Folder" : "File name", "", field, Optional.of(types())));
    }

    private String statusLine() {
        int count = model.entries().size();
        int selected = model.selection().size();
        String line = (count == 1 ? "1 item" : count + " items") + (selected > 0 ? " · " + selected + " selected" : "");
        if (model.request().mode() != ChooserRequest.Mode.DIRECTORY) return line;
        List<ChooserEntry> chosen = model.selection();
        String target = chosen.size() == 1 && chosen.getFirst().directory() ? chosen.getFirst().name() : model.name(model.location());
        return line + " · Choose: " + safe(target);
    }

    // ---- Behaviour --------------------------------------------------------------------------------------------------

    private void selectionChanged(Set<String> ids) {
        List<ChooserEntry> chosen = model.entries().stream().filter(entry -> ids.contains(entry.path())).toList();
        model.select(chosen);
        model.nameForSelection().ifPresent(this::showName);
        if (previewVisible) model.requestPreview();
    }

    private void showName(String text) {
        name = text;
        if (nameView != null) render(nameView, nameSpec());
    }

    private void activateSelection() {
        List<ChooserEntry> selected = model.selection();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) handle(model.activate(selected.getFirst()));
        else approve();
    }

    void approve() { model.accept(name, this::handle); }

    void cancel() { done(List.of()); }

    private void handle(ChooserModel.Outcome outcome) {
        switch (outcome) {
            case ChooserModel.Outcome.Chosen chosen -> done(chosen.locations());
            case ChooserModel.Outcome.ConfirmReplace replace -> {
                if (confirmReplace.test(replace.file())) done(List.of(replace.file()));
            }
            case ChooserModel.Outcome.Invalid invalid -> setStatus(safe(invalid.message()));
            case ChooserModel.Outcome.Navigated navigated -> showName(model.request().mode() == ChooserRequest.Mode.SAVE
                ? model.request().suggestedName().orElse("") : "");
        }
    }

    private void setStatus(String text) {
        status = text;
        render(statusView, statusSpec());
    }

    private void done(List<Location> chosen) {
        if (finished) return;
        finished = true;
        loadingDelay.stop();
        if (!chosen.isEmpty()) {
            Location first = chosen.getFirst();
            prefs.recordRecent(model.request().mode() == ChooserRequest.Mode.DIRECTORY ? first : model.parent(first).orElse(first));
        }
        finish.accept(List.copyOf(chosen));
    }

    void newFolder() {
        askFolderName.get().ifPresent(folder -> model.createFolder(folder, problem -> problem.ifPresent(text -> setStatus(safe(text)))));
    }

    void toggleHidden() {
        boolean next = !model.showHidden();
        prefs.setShowHidden(next);
        model.setShowHidden(next);
    }

    void togglePin(Location folder) {
        if (prefs.isPinned(folder)) prefs.unpin(folder);
        else prefs.pin(folder);
        rebuildNavigation();
    }

    /** Cmd/Ctrl+L or a click beside the crumbs: the path becomes a text field holding the folder shown. */
    void editPath() {
        editingPath = true;
        pathValue = model.location().path().equals(Location.HOME) ? "" : model.location().path();
        render(pathView, pathSpec());
    }

    private void submitPath() {
        model.goTo(pathValue, problem -> {
            if (problem.isPresent()) setStatus(safe(problem.get()));
            else finishEditingPath();
        });
    }

    private void finishEditingPath() {
        if (!editingPath) return;
        editingPath = false;
        pathValue = "";
        render(pathView, pathSpec());
    }

    private void setPreviewVisible(boolean visible) {
        prefs.setPreview(visible);
        previewVisible = visible;
        previewPane.setVisible(visible);
        if (visible) model.requestPreview();
        render(toolsView, toolsSpec());
        render(previewHeaderView, previewHeaderSpec());
        syncPreviewText();
        revalidate();
        repaint();
    }

    private void rebuildNavigation() {
        rebuildNavigationItems();
        if (placesView == null) return;
        syncNavigationSelection();
        render(filesView, filesSpec());
        render(pathView, pathSpec());
    }

    private void rebuildNavigationItems() {
        placeIds.clear();
        navigationItems.clear();
        String heading = null;
        Places.Section section = null;
        for (Places.Place place : Places.build(model.request().terminalFolder(), home, prefs.pinned(), prefs.recent(), model.sources(), Files::isDirectory)) {
            if (place.section() != section || !place.heading().equals(heading)) {
                section = place.section();
                heading = place.heading();
                if (section != Places.Section.PLACES) navigationItems.add(heading);
            }
            navigationItems.add(place);
            String id = place.section() + "|" + place.location().stored();
            while (placeIds.containsKey(id)) id = id + "'";
            placeIds.put(id, place);
        }
    }

    private static <T extends java.awt.Component> T find(java.awt.Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) return type.cast(root);
        if (root instanceof java.awt.Container container) for (var child : container.getComponents()) {
            T found = find(child, name, type);
            if (found != null) return found;
        }
        return null;
    }

    /** Untrusted text for display: control characters become U+FFFD. */
    static String safe(String text) {
        var out = new StringBuilder(text.length());
        text.codePoints().forEach(c -> out.appendCodePoint(c < 0x20 || c == 0x7f ? 0xFFFD : c));
        return out.toString();
    }

    static String sizeText(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        String[] units = { "KB", "MB", "GB", "TB" };
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
    }

    static String modifiedText(long millis) { return MODIFIED.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())); }

}
