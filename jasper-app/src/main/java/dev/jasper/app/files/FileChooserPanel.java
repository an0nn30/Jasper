package dev.jasper.app.files;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * The chooser's Swing view over a {@link ChooserModel}: toolbar and path bar, navigation pane (places, pinned, recent
 * and each source's roots), file table with its connecting and error states, optional preview pane, and the name field
 * with Cancel and the primary button. It never waits for I/O. Whoever shows it starts the model, closes the dialog when
 * {@code finish} runs (an empty list means cancelled) and then calls {@link #close()}. File names are shown as plain
 * text. UI thread.
 */
public final class FileChooserPanel extends JPanel {
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MENU = SystemInfo.isMacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    private static final Color CONNECTED = new Color(0x5B, 0xD1, 0x8B);

    private final ChooserModel model;
    private final ChooserPrefs prefs;
    private final Path home;
    private final Consumer<List<Location>> finish;
    private final Predicate<Location> confirmReplace;
    private final Supplier<Optional<String>> askFolderName;
    private final List<Runnable> sourceRemovals = new ArrayList<>();
    private final JButton backButton = tool("general/chevronLeft", "Back");
    private final JButton forwardButton = tool("general/chevronRight", "Forward");
    private final JButton upButton = tool("nodes/upFolder", "Up");
    private final JButton refreshButton = tool("general/refresh", "Refresh");
    private final JButton newFolderButton = new JButton("New folder", AppIcons.bundled("actions/newFolder"));
    private final JToggleButton previewToggle = new JToggleButton(AppIcons.bundled("actions/preview"));
    private final PathBar pathBar = new PathBar();
    private final JTextField search = new JTextField(14);
    private final DefaultListModel<Object> navigationItems = new DefaultListModel<>();
    private final JList<Object> navigation = new JList<>(navigationItems);
    private final EntryTable tableModel = new EntryTable();
    private final JTable table = new JTable(tableModel);
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final JLabel message = plain(new JLabel("", SwingConstants.CENTER));
    private final JButton centerButton = new JButton();
    private final JPanel previewPane = new JPanel(new BorderLayout(0, 6));
    private final JLabel previewTitle = plain(new JLabel());
    private final JLabel previewDetails = plain(new JLabel());
    private final JTextArea previewText = new JTextArea();
    private final JTextField nameField = new JTextField();
    private final JLabel status = plain(new JLabel());
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton primaryButton;
    private final Timer loadingDelay = new Timer(150, event -> showCenter());
    private Optional<String> centerMessage = Optional.empty();
    private Runnable centerAction = () -> { };
    private boolean updating;
    private boolean finished;
    private String typed = "";
    private long typedAt;

    /**
     * @param home the local home folder, for the Places list
     * @param finish receives the chosen locations, or an empty list on cancel; runs at most once
     * @param confirmReplace asks whether to replace an existing file in Save
     * @param askFolderName asks for a new folder's name; empty when the user cancelled
     */
    public FileChooserPanel(ChooserModel model, ChooserPrefs prefs, Path home, Consumer<List<Location>> finish,
                            Predicate<Location> confirmReplace, Supplier<Optional<String>> askFolderName) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model);
        this.prefs = Objects.requireNonNull(prefs);
        this.home = Objects.requireNonNull(home);
        this.finish = Objects.requireNonNull(finish);
        this.confirmReplace = Objects.requireNonNull(confirmReplace);
        this.askFolderName = Objects.requireNonNull(askFolderName);
        ChooserRequest request = model.request();
        primaryButton = new JButton(switch (request.mode()) {
            case OPEN -> "Open";
            case SAVE -> "Save";
            case DIRECTORY -> "Choose";
        });
        loadingDelay.setRepeats(false);
        add(toolbar(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
        bindKeys();
        if (request.mode() == ChooserRequest.Mode.SAVE)
            request.suggestedName().ifPresent(name -> {
                nameField.setText(name);
                int dot = name.lastIndexOf('.');
                nameField.select(0, dot > 0 ? dot : name.length());
            });
        previewToggle.setSelected(prefs.preview());
        previewPane.setVisible(prefs.preview());
        for (ChooserSource source : model.sources()) {
            try { sourceRemovals.add(source.onChanged(this::rebuildNavigation)); }
            catch (RuntimeException failed) { /* a failing source just does not update the pane */ }
        }
        rebuildNavigation();
        model.addListener(this::sync);
        sync();
    }

    /** The dialog's default button. */
    public JButton primaryButton() { return primaryButton; }

    /** Stops listening to the sources; call once the dialog has closed. */
    public void close() {
        loadingDelay.stop();
        sourceRemovals.forEach(Runnable::run);
        sourceRemovals.clear();
    }

    // Package-private accessors for headless tests.
    JTable table() { return table; }
    JTextField nameField() { return nameField; }
    JList<Object> navigation() { return navigation; }
    JToggleButton previewToggle() { return previewToggle; }
    String statusText() { return status.getText(); }
    Optional<String> centerMessage() { return centerMessage; }
    Optional<String> centerAction() { return centerButton.isVisible() ? Optional.of(centerButton.getText()) : Optional.empty(); }
    void pressCenterAction() { centerAction.run(); }
    String previewText() { return previewText.getText(); }
    String pathText() { return pathBar.text(); }
    void editPath(String text) { pathBar.edit(); pathBar.field.setText(text); pathBar.submit(); }

    private JComponent toolbar() {
        var bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.add(backButton);
        buttons.add(forwardButton);
        buttons.add(upButton);
        buttons.add(refreshButton);
        backButton.addActionListener(event -> model.goBack());
        forwardButton.addActionListener(event -> model.goForward());
        upButton.addActionListener(event -> model.goUp());
        refreshButton.addActionListener(event -> model.refresh());
        var trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        search.putClientProperty("JTextField.leadingIcon", AppIcons.bundled("general/search"));
        search.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { searched(); }
            @Override public void removeUpdate(DocumentEvent event) { searched(); }
            @Override public void changedUpdate(DocumentEvent event) { searched(); }
        });
        trailing.add(search);
        if (model.request().mode() == ChooserRequest.Mode.SAVE) {
            newFolderButton.addActionListener(event -> newFolder());
            trailing.add(newFolderButton);
        }
        previewToggle.setToolTipText("Preview pane");
        previewToggle.putClientProperty("JButton.buttonType", "toolBarButton");
        previewToggle.setFocusable(false);
        previewToggle.addActionListener(event -> setPreviewVisible(previewToggle.isSelected()));
        trailing.add(previewToggle);
        bar.add(buttons, BorderLayout.WEST);
        bar.add(pathBar, BorderLayout.CENTER);
        bar.add(trailing, BorderLayout.EAST);
        return bar;
    }

    private void searched() { if (!updating) model.setFilter(search.getText()); }

    private JComponent body() {
        navigation.setCellRenderer(new NavigationRenderer());
        navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigation.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || updating) return;
            if (navigation.getSelectedValue() instanceof Places.Place place) model.navigate(place.location());
            else syncNavigationSelection();
        });
        navigation.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { navigationPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { navigationPopup(event); }
        });
        var navigationScroll = new JScrollPane(navigation);
        navigationScroll.setBorder(BorderFactory.createEmptyBorder());
        navigationScroll.setMinimumSize(new Dimension(140, 0));

        table.setAutoCreateRowSorter(false);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(model.request().multiple()
            ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new EntryRenderer());
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int column = table.columnAtPoint(event.getPoint());
                if (column >= 0 && SwingUtilities.isLeftMouseButton(event)) model.sortBy(ChooserModel.Column.values()[column]);
            }
        });
        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && !updating) selectionChanged(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) activateSelection();
            }
            @Override public void mousePressed(MouseEvent event) { tablePopup(event); }
            @Override public void mouseReleased(MouseEvent event) { tablePopup(event); }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent event) {
                if ((event.getModifiersEx() & (MENU | InputEvent.ALT_DOWN_MASK)) == 0) typeToSelect(event.getKeyChar());
            }
        });
        var tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        message.setForeground(UIManager.getColor("Label.disabledForeground"));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerButton.addActionListener(event -> centerAction.run());
        var messageBox = new JPanel();
        messageBox.setLayout(new BoxLayout(messageBox, BoxLayout.Y_AXIS));
        messageBox.setOpaque(false);
        messageBox.add(message);
        messageBox.add(Box.createVerticalStrut(10));
        messageBox.add(centerButton);
        var messageArea = new JPanel(new GridBagLayout());
        messageArea.add(messageBox);
        center.add(tableScroll, "table");
        center.add(messageArea, "message");

        previewText.setEditable(false);
        previewText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD));
        var previewHeader = new JPanel(new BorderLayout(0, 2));
        previewHeader.add(previewTitle, BorderLayout.NORTH);
        previewHeader.add(previewDetails, BorderLayout.SOUTH);
        previewPane.add(previewHeader, BorderLayout.NORTH);
        var previewScroll = new JScrollPane(previewText);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewPane.add(previewScroll, BorderLayout.CENTER);
        previewPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        previewPane.setPreferredSize(new Dimension(230, 0));

        var main = new JPanel(new BorderLayout());
        main.add(center, BorderLayout.CENTER);
        main.add(previewPane, BorderLayout.EAST);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationScroll, main);
        split.setDividerLocation(190);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JComponent bottom() {
        ChooserRequest request = model.request();
        var name = new JPanel(new BorderLayout(8, 0));
        name.add(plain(new JLabel(request.mode() == ChooserRequest.Mode.DIRECTORY ? "Folder" : "File name")), BorderLayout.WEST);
        name.add(nameField, BorderLayout.CENTER);
        var types = new JComboBox<>(new String[] { request.suffix().map(suffix -> "*" + suffix).orElse("All files") });
        types.setPrototypeDisplayValue("All files      ");
        name.add(types, BorderLayout.EAST);
        nameField.addActionListener(event -> approve());
        cancelButton.addActionListener(event -> cancel());
        primaryButton.addActionListener(event -> approve());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(primaryButton);
        var actions = new JPanel(new BorderLayout());
        actions.add(status, BorderLayout.CENTER);
        actions.add(buttons, BorderLayout.EAST);
        var bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        bottom.add(name, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        return bottom;
    }

    private void bindKeys() {
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "back", model::goBack);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "forward", model::goForward);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "up", model::goUp);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_L, MENU), "path", pathBar::edit);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, MENU | InputEvent.SHIFT_DOWN_MASK), "hidden", this::toggleHidden);
        bind(this, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        bind(table, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate", this::activateSelection);
        bind(table, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "up", model::goUp);
    }

    private static void bind(JComponent component, int condition, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(condition).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    /** Brings every component up to date with the model. */
    private void sync() {
        updating = true;
        try {
            backButton.setEnabled(model.canGoBack());
            forwardButton.setEnabled(model.canGoForward());
            upButton.setEnabled(model.canGoUp());
            pathBar.display();
            if (!search.getText().equals(model.filter())) search.setText(model.filter());
            search.putClientProperty("JTextField.placeholderText", "Search " + safe(model.name(model.location())));
            tableModel.set(model.entries());
            for (int column = 0; column < 3; column++)
                table.getColumnModel().getColumn(column).setHeaderValue(header(ChooserModel.Column.values()[column]));
            table.getTableHeader().repaint();
            syncTableSelection();
            syncNavigationSelection();
            status.setText(statusLine());
            if (model.loading() && model.error().isEmpty()) {
                if (!loadingDelay.isRunning()) loadingDelay.restart();
                if (model.entries().isEmpty()) {
                    centerMessage = Optional.empty();
                    centerCards.show(center, "table");
                } else {
                    showCenter();
                }
            } else {
                loadingDelay.stop();
                showCenter();
            }
            syncPreview();
        } finally {
            updating = false;
        }
    }

    private void syncTableSelection() {
        List<ChooserEntry> entries = model.entries();
        int[] wanted = model.selection().stream().mapToInt(entries::indexOf).filter(row -> row >= 0).sorted().toArray();
        if (Arrays.equals(wanted, table.getSelectedRows())) return;
        var selection = table.getSelectionModel();
        selection.clearSelection();
        for (int row : wanted) selection.addSelectionInterval(row, row);
        if (wanted.length > 0) table.scrollRectToVisible(table.getCellRect(wanted[0], 0, true));
    }

    private void syncNavigationSelection() {
        boolean was = updating;
        updating = true;
        try {
            Location shown = model.location();
            int sameRoot = -1;
            for (int index = 0; index < navigationItems.size(); index++) {
                if (!(navigationItems.get(index) instanceof Places.Place place)) continue;
                if (place.location().equals(shown)) {
                    navigation.setSelectedIndex(index);
                    return;
                }
                if (sameRoot < 0 && place.section() == Places.Section.SOURCE && place.location().source().equals(shown.source())
                        && place.location().root().equals(shown.root())) sameRoot = index;
            }
            if (sameRoot >= 0) navigation.setSelectedIndex(sameRoot);
            else navigation.clearSelection();
        } finally {
            updating = was;
        }
    }

    /** The message area: connecting, loading, an error with Retry, or an empty folder; otherwise the table. */
    private void showCenter() {
        Optional<String> text;
        Optional<String> action = Optional.empty();
        if (model.connectingTo().isPresent()) {
            text = Optional.of("Connecting to " + safe(model.connectingTo().get()) + "\u2026");
            action = Optional.of("Cancel connection");
            centerAction = model::cancelConnection;
        } else if (model.error().isPresent()) {
            text = Optional.of(safe(model.error().get()));
            if (model.retryable()) {
                action = Optional.of("Retry");
                centerAction = model::retry;
            }
        } else if (model.loading()) {
            text = model.entries().isEmpty() ? Optional.of("Loading\u2026") : Optional.empty();
        } else if (model.entries().isEmpty()) {
            text = Optional.of(model.filter().isBlank() ? "This folder is empty." : "No items match your search.");
        } else {
            text = Optional.empty();
        }
        centerMessage = text;
        message.setText(text.orElse(""));
        centerButton.setText(action.orElse(""));
        centerButton.setVisible(action.isPresent());
        centerCards.show(center, text.isPresent() ? "message" : "table");
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

    private String header(ChooserModel.Column column) {
        String name = switch (column) { case NAME -> "Name"; case MODIFIED -> "Modified"; case SIZE -> "Size"; };
        return column == model.sortColumn() ? name + (model.ascending() ? " \u25B4" : " \u25BE") : name;
    }

    private void selectionChanged() {
        List<ChooserEntry> entries = model.entries();
        List<ChooserEntry> chosen = Arrays.stream(table.getSelectedRows()).filter(row -> row < entries.size()).mapToObj(entries::get).toList();
        model.select(chosen);
        model.nameForSelection().ifPresent(nameField::setText);
        if (previewPane.isVisible()) model.requestPreview();
    }

    private void activateSelection() {
        List<ChooserEntry> selected = model.selection();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) handle(model.activate(selected.getFirst()));
        else approve();
    }

    void approve() { model.accept(nameField.getText(), this::handle); }

    void cancel() { done(List.of()); }

    private void handle(ChooserModel.Outcome outcome) {
        switch (outcome) {
            case ChooserModel.Outcome.Chosen chosen -> done(chosen.locations());
            case ChooserModel.Outcome.ConfirmReplace replace -> {
                if (confirmReplace.test(replace.file())) done(List.of(replace.file()));
            }
            case ChooserModel.Outcome.Invalid invalid -> status.setText(safe(invalid.message()));
            case ChooserModel.Outcome.Navigated navigated -> nameField.setText(model.request().mode() == ChooserRequest.Mode.SAVE
                ? model.request().suggestedName().orElse("") : "");
        }
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
        askFolderName.get().ifPresent(name -> model.createFolder(name, problem -> problem.ifPresent(text -> status.setText(safe(text)))));
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

    /** Selects the first entry whose name starts with what was typed in the last second. */
    void typeToSelect(char c) {
        if (Character.isISOControl(c)) return;
        long now = System.currentTimeMillis();
        typed = now - typedAt > 1000 ? String.valueOf(c) : typed + c;
        typedAt = now;
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<ChooserEntry> entries = model.entries();
        for (int row = 0; row < entries.size(); row++)
            if (entries.get(row).name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
    }

    private void setPreviewVisible(boolean visible) {
        prefs.setPreview(visible);
        previewToggle.setSelected(visible);
        previewPane.setVisible(visible);
        if (visible) model.requestPreview();
        syncPreview();
        revalidate();
        repaint();
    }

    private void syncPreview() {
        if (!previewPane.isVisible()) return;
        List<ChooserEntry> selected = model.selection();
        if (selected.size() != 1) {
            previewTitle.setText(selected.isEmpty() ? "No selection" : selected.size() + " items selected");
            previewDetails.setText("");
            previewText.setText("");
            return;
        }
        ChooserEntry entry = selected.getFirst();
        Optional<PreviewInfo> loaded = model.preview();
        previewTitle.setText(safe(entry.name()));
        previewDetails.setText(entry.directory()
            ? loaded.map(preview -> preview.items() == 1 ? "1 item" : preview.items() + " items").orElse("Folder")
            : sizeText(entry.size()) + (entry.modified() >= 0 ? " \u00B7 " + modifiedText(entry.modified()) : ""));
        previewText.setText(loaded.flatMap(PreviewInfo::text).orElse(loaded.filter(PreviewInfo::binary).isPresent() ? "Binary file" : ""));
        previewText.setCaretPosition(0);
    }

    private void rebuildNavigation() {
        boolean was = updating;
        updating = true;
        try {
            navigationItems.clear();
            String heading = null;
            Places.Section section = null;
            for (Places.Place place : Places.build(model.request().terminalFolder(), home, prefs.pinned(), prefs.recent(), model.sources(), Files::isDirectory)) {
                if (place.section() != section || !place.heading().equals(heading)) {
                    section = place.section();
                    heading = place.heading();
                    navigationItems.addElement(heading);
                }
                navigationItems.addElement(place);
            }
        } finally {
            updating = was;
        }
        syncNavigationSelection();
    }

    private void navigationPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int index = navigation.locationToIndex(event.getPoint());
        if (index >= 0 && navigationItems.get(index) instanceof Places.Place place && !place.location().path().equals(Location.HOME))
            pinMenu(event, place.location());
    }

    private void tablePopup(MouseEvent event) {
        if (!event.isPopupTrigger()) return;
        int row = table.rowAtPoint(event.getPoint());
        if (row >= 0 && row < model.entries().size() && model.entries().get(row).directory())
            pinMenu(event, model.location().at(model.entries().get(row).path()));
    }

    private void pinMenu(MouseEvent event, Location folder) {
        var menu = new JPopupMenu();
        var item = new JMenuItem(prefs.isPinned(folder) ? "Unpin" : "Pin");
        item.addActionListener(action -> togglePin(folder));
        menu.add(item);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private static JButton tool(String icon, String tip) {
        var button = new JButton(AppIcons.bundled(icon));
        button.setToolTipText(tip);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        return button;
    }

    private static <T extends JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
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

    private final class EntryTable extends AbstractTableModel {
        private List<ChooserEntry> rows = List.of();

        void set(List<ChooserEntry> entries) {
            if (entries.equals(rows)) return;
            rows = entries;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        /** Plain names: JTable asks while the panel's fields are still being built; {@code sync} adds the sort arrow. */
        @Override public String getColumnName(int column) { return switch (column) { case 0 -> "Name"; case 1 -> "Modified"; default -> "Size"; }; }
        @Override public Object getValueAt(int row, int column) { return rows.get(row); }
    }

    private final class EntryRenderer extends DefaultTableCellRenderer {
        EntryRenderer() { putClientProperty("html.disable", Boolean.TRUE); }

        @Override public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(owner, "", selected, false, row, column);
            ChooserEntry entry = (ChooserEntry) value;
            setIcon(null);
            setHorizontalAlignment(SwingConstants.LEADING);
            switch (column) {
                case 0 -> {
                    setText(safe(entry.name()));
                    setIcon(AppIcons.bundled(entry.directory() ? "nodes/folder"
                        : entry.kind() == ChooserEntry.Kind.FILE ? "fileTypes/text" : "fileTypes/unknown"));
                }
                case 1 -> setText(entry.modified() < 0 ? "" : modifiedText(entry.modified()));
                default -> {
                    setText(entry.directory() ? "" : sizeText(entry.size()));
                    setHorizontalAlignment(SwingConstants.TRAILING);
                }
            }
            boolean dimmed = column > 0 || model.request().mode() == ChooserRequest.Mode.SAVE && !entry.directory();
            if (!selected) setForeground(dimmed ? UIManager.getColor("Label.disabledForeground") : owner.getForeground());
            return this;
        }
    }

    private static final class NavigationRenderer extends DefaultListCellRenderer {
        NavigationRenderer() { putClientProperty("html.disable", Boolean.TRUE); }

        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            if (value instanceof Places.Place place) {
                super.getListCellRendererComponent(list, safe(place.label()), index, selected, false);
                Icon icon = AppIcons.bundled(place.icon());
                setIcon(place.connected() ? new ConnectedIcon(icon) : icon);
                setToolTipText(place.tooltip().isEmpty() ? null : safe(place.tooltip()));
                setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 6));
            } else {
                super.getListCellRendererComponent(list, safe(String.valueOf(value)).toUpperCase(Locale.ROOT), index, false, false);
                setIcon(null);
                setToolTipText(null);
                setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2));
                setForeground(UIManager.getColor("Label.disabledForeground"));
                setBorder(BorderFactory.createEmptyBorder(index == 0 ? 4 : 10, 8, 2, 6));
            }
            return this;
        }
    }

    /** A root's icon with a small green dot at its lower right: connected. */
    private record ConnectedIcon(Icon base) implements Icon {
        @Override public int getIconWidth() { return base.getIconWidth(); }
        @Override public int getIconHeight() { return base.getIconHeight(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            base.paintIcon(component, graphics, x, y);
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.max(5, getIconWidth() / 3);
                copy.setColor(CONNECTED);
                copy.fillOval(x + getIconWidth() - size, y + getIconHeight() - size, size, size);
            } finally {
                copy.dispose();
            }
        }
    }

    /** Clickable breadcrumbs; clicking the empty area or Cmd/Ctrl+L turns it into a text field for a typed path. */
    private final class PathBar extends JPanel {
        private final CardLayout cards = new CardLayout();
        private final JPanel crumbs = new JPanel(new TailLayout());
        private final JTextField field = new JTextField();
        private final List<String> labels = new ArrayList<>();

        PathBar() {
            setLayout(cards);
            crumbs.setBorder(UIManager.getBorder("TextField.border"));
            crumbs.setBackground(UIManager.getColor("TextField.background"));
            crumbs.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) { if (SwingUtilities.isLeftMouseButton(event)) edit(); }
            });
            add(crumbs, "crumbs");
            add(field, "field");
            field.addActionListener(event -> submit());
            bind(field, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "done", this::finishEditing);
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) { finishEditing(); }
            });
        }

        /** The crumbs for the place shown: a source's root label first, then its folders; while connecting, the root alone. */
        void display() {
            crumbs.removeAll();
            labels.clear();
            Location shown = model.location();
            if (!shown.isLocal()) crumb(model.rootLabel(shown), Location.home(shown.source(), shown.root()));
            if (!shown.path().equals(Location.HOME)) {
                List<Location> chain = model.chain();
                for (Location step : chain) crumb(model.name(step), step);
            }
            crumbs.revalidate();
            crumbs.repaint();
        }

        private void crumb(String text, Location target) {
            if (!labels.isEmpty()) crumbs.add(new JLabel(" \u203A "));
            String label = safe(text);
            labels.add(label);
            var crumb = plain(new JButton(label));
            crumb.putClientProperty("JButton.buttonType", "borderless");
            crumb.setFocusable(false);
            crumb.addActionListener(event -> model.navigate(target));
            if (!target.path().equals(Location.HOME))
                crumb.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                    @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) pinMenu(event, target); }
                });
            crumbs.add(crumb);
        }

        String text() { return String.join(" \u203A ", labels); }

        void edit() {
            field.setText(model.location().path().equals(Location.HOME) ? "" : model.location().path());
            cards.show(this, "field");
            field.requestFocusInWindow();
            field.selectAll();
        }

        void submit() {
            model.goTo(field.getText(), problem -> {
                if (problem.isPresent()) status.setText(safe(problem.get()));
                else finishEditing();
            });
        }

        void finishEditing() { cards.show(this, "crumbs"); }
    }

    /** Lays crumbs out left to right; when they do not fit, the leftmost slide out of view so the current folder stays visible. */
    private static final class TailLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = 0;
            int height = 0;
            for (Component component : parent.getComponents()) {
                Dimension size = component.getPreferredSize();
                width += size.width;
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int available = parent.getWidth() - insets.left - insets.right;
            int needed = preferredLayoutSize(parent).width - insets.left - insets.right;
            int x = insets.left + Math.min(0, available - needed);
            int height = parent.getHeight() - insets.top - insets.bottom;
            for (Component component : parent.getComponents()) {
                int width = component.getPreferredSize().width;
                component.setBounds(x, insets.top, width, height);
                x += width;
            }
        }
    }
}
