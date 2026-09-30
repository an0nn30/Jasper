package dev.jasper.app.terminalthemes;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.CorePages;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Settings > Themes > Alacritty Themes: every terminal theme with a colour strip, a sample terminal in the selected
 * theme's colours, Use (applied on Apply or OK), Install from Disk, Reveal Folder and Remove for the user's own files.
 * SettingsDialog owns each page; disposing it closes the sample terminal. EDT only.
 */
public final class AlacrittyThemesPage implements SettingsPage {
    /**
     * What the page needs from the application.
     *
     * @param catalog the terminal themes
     * @param rescan reads the folder again (the configuration reload does); called on the EDT
     * @param reveal shows a folder in the file manager
     * @param confirm asks a yes/no question over a component
     * @param sampleOptions the terminal font and behaviour the sample uses
     * @param edt runs work on the EDT, for results of {@code rescan}
     */
    public record Hooks(TerminalThemeCatalog catalog, Supplier<CompletableFuture<?>> rescan, Consumer<Path> reveal,
                        BiPredicate<Component, String> confirm, Supplier<TerminalOptions> sampleOptions, Executor edt) {
        public Hooks {
            Objects.requireNonNull(catalog); Objects.requireNonNull(rescan); Objects.requireNonNull(reveal);
            Objects.requireNonNull(confirm); Objects.requireNonNull(sampleOptions); Objects.requireNonNull(edt);
        }
    }

    private final PageContext context;
    private final Hooks hooks;
    private final TerminalThemeFiles files;
    private final DefaultListModel<TerminalTheme> model = new DefaultListModel<>();
    private final JList<TerminalTheme> list = new JList<>(model);
    private final JTextField search = new JTextField();
    private final JLabel title = new JLabel();
    private final JLabel source = new JLabel();
    private final JLabel swatches = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton use = new JButton("Use");
    private final JButton remove = new JButton("Remove…");
    private final JButton install = new JButton("Install from Disk…");
    private final JButton reveal = new JButton("Reveal Folder");
    private final JPanel sampleHolder = new JPanel(new BorderLayout());
    private List<TerminalTheme> all = List.of();
    private SampleTerminal sample;
    private FillingPage body;
    private Subscription draftListening;

    public AlacrittyThemesPage(PageContext context, Hooks hooks) {
        this.context = Objects.requireNonNull(context);
        this.hooks = Objects.requireNonNull(hooks);
        this.files = new TerminalThemeFiles(hooks.catalog().directory()
            .orElseThrow(() -> new IllegalArgumentException("The terminal theme catalog has no folder")));
    }

    /** A standard Yes/No question; the production {@link Hooks#confirm}. */
    public static boolean ask(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "Alacritty Themes", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override public String id() { return CorePages.ALACRITTY_THEMES; }
    @Override public String title() { return "Alacritty Themes"; }
    @Override public Optional<String> parent() { return Optional.of(CorePages.THEMES); }
    @Override public String helpAnchor() { return "alacritty-themes"; }
    @Override public List<String> searchTerms() {
        return List.of("Alacritty Themes", "terminal themes colours colors palette install toml");
    }

    @Override public JComponent component() {
        if (body != null) return body;
        for (JComponent label : List.of(title, source, status, use, remove, install, reveal)) label.putClientProperty("html.disable", true);
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "Search terminal themes");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { filter(search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { filter(search.getText()); }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Terminal themes");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "jasper.useTheme");
        list.getActionMap().put("jasper.useTheme", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { use(); }
        });
        use.addActionListener(event -> use());
        remove.addActionListener(event -> remove());
        install.addActionListener(event -> installFromDisk());
        reveal.addActionListener(event -> hooks.reveal().accept(files.folder()));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        toolbar.setOpaque(false);
        toolbar.add(install);
        toolbar.add(reveal);
        toolbar.add(status);
        var left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        left.add(search, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(use);
        buttons.add(remove);
        var details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        sampleHolder.setOpaque(false);
        for (JComponent part : List.of(title, source, swatches, sampleHolder, buttons)) { part.setAlignmentX(0f); details.add(part); }
        details.add(Box.createVerticalGlue());
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(details));
        split.setResizeWeight(0.35);
        split.setBorder(null);
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        source.setForeground(UIManager.getColor("Label.disabledForeground"));
        try {
            sample = new SampleTerminal(hooks.sampleOptions().get());
            sampleHolder.add(sample.component(), BorderLayout.CENTER);
        } catch (RuntimeException unavailable) {
            // Without a sample the colour strip alone previews the theme.
            sample = null;
        }
        body = new FillingPage(panel);
        draftListening = context.draft().onChanged(list::repaint);
        refresh(null);
        return body;
    }

    @Override public void selected() { component(); refresh(selectedName()); }
    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }

    @Override public void dispose() {
        if (draftListening != null) draftListening.close();
        if (sample != null) sample.close();
    }

    /** Re-reads the catalog, keeping the selection or selecting {@code prefer} when given. */
    private void refresh(String prefer) {
        all = hooks.catalog().latest().themes();
        String keep = prefer != null ? prefer : selectedName();
        show(keep);
    }

    void filter(String text) {
        String keep = selectedName();
        String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        model.clear();
        for (TerminalTheme theme : all) if (query.isEmpty() || theme.name().toLowerCase(Locale.ROOT).contains(query)) model.addElement(theme);
        select(keep);
        if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
        showDetails();
    }

    private void show(String keep) {
        filter(search.getText());
        if (keep != null) select(keep);
    }

    void select(String name) {
        if (name == null) return;
        for (int i = 0; i < model.size(); i++)
            if (model.get(i).name().equalsIgnoreCase(name)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); return; }
    }

    private void showDetails() {
        TerminalTheme theme = list.getSelectedValue();
        use.setVisible(theme != null);
        remove.setVisible(theme != null && !theme.bundled());
        if (theme == null) {
            title.setText(model.isEmpty() ? "No terminal themes match" : "");
            source.setText("");
            swatches.setIcon(null);
            return;
        }
        title.setText(theme.name());
        source.setText(theme.bundled() ? "Bundled with Jasper (alacritty-theme)" : "Your file: " + theme.file().orElseThrow().getFileName());
        swatches.setIcon(new Strip(theme.palette(), true));
        swatches.setText("Cursor and selection colours at the end");
        if (sample != null) sample.palette(theme.palette());
    }

    private void use() {
        TerminalTheme theme = list.getSelectedValue();
        if (theme == null) return;
        context.draft().stage(CoreSettings.TERMINAL_COLORS, Optional.of(theme.name()));
        message("“" + theme.name() + "” is chosen. Apply or OK switches the terminals to it.", false);
        list.repaint();
    }

    private void installFromDisk() {
        context.chooseFile().apply("Install Terminal Theme", Optional.empty()).ifPresent(file -> install(file, false));
    }

    private void install(Path file, boolean replace) {
        String name;
        try { name = files.install(file, replace); }
        catch (TerminalThemeFiles.Conflict conflict) {
            if (hooks.confirm().test(body, conflict.existing().getFileName() + " is already installed. Replace it?")) install(file, true);
            return;
        } catch (TerminalThemeFiles.ThemeFileException refused) {
            message(refused.getMessage(), true);
            return;
        }
        message("Installed " + name + ".", false);
        rescanThen(name);
    }

    private void remove() {
        TerminalTheme theme = list.getSelectedValue();
        if (theme == null || theme.bundled()) return;
        Path file = theme.file().orElseThrow();
        if (!hooks.confirm().test(body, "Remove " + theme.name() + "? This deletes " + file.getFileName() + ".")) return;
        try { files.remove(file); }
        catch (TerminalThemeFiles.ThemeFileException refused) { message(refused.getMessage(), true); return; }
        message("Removed " + theme.name() + ".", false);
        rescanThen(null);
    }

    /** Rescans through the configuration reload, then shows the new list selecting {@code prefer}. */
    private void rescanThen(String prefer) {
        CompletableFuture<?> done;
        try { done = hooks.rescan().get(); }
        catch (RuntimeException failure) { done = CompletableFuture.failedFuture(failure); }
        done.whenComplete((ignored, failure) -> hooks.edt().execute(() -> refresh(prefer)));
    }

    private void message(String text, boolean error) {
        status.setText(text);
        status.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    // Test access.
    List<String> listed() { var names = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) names.add(model.get(i).name()); return names; }
    String selectedName() { TerminalTheme theme = list.getSelectedValue(); return theme == null ? null : theme.name(); }
    JButton useButton() { return use; }
    JButton removeButton() { return remove; }
    JButton installButton() { return install; }
    String status() { return status.getText(); }
    SampleTerminal sample() { return sample; }

    /** The theme the draft's terminal colours name, or null. */
    String inUse() {
        TerminalColors colors = TerminalColors.parse(context.draft().value(CoreSettings.TERMINAL_COLORS));
        if (!colors.named()) return null;
        return all.stream().map(TerminalTheme::name).filter(name -> name.equalsIgnoreCase(colors.name())).findFirst().orElse(null);
    }

    /** Name in bold with a check when in use, the source, and the colour strip. */
    private final class Renderer extends JPanel implements ListCellRenderer<TerminalTheme> {
        private final JLabel name = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            name.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            var text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            text.add(name);
            text.add(line);
            add(text, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends TerminalTheme> owner, TerminalTheme value, int index,
                                                                boolean selected, boolean focused) {
            boolean current = value.name().equals(inUse());
            name.setText(current ? value.name() + "  ✓" : value.name());
            name.setFont(owner.getFont().deriveFont(Font.BOLD));
            line.setText(value.bundled() ? "Bundled" : "Your file");
            line.setIcon(new Strip(value.palette(), false));
            line.setHorizontalTextPosition(JLabel.LEFT);
            name.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            line.setForeground(selected ? owner.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            setOpaque(true);
            return this;
        }
    }

    /** The eight normal colours as squares on the theme's background; with the cursor and selection when asked. */
    private static final class Strip implements Icon {
        private static final int CELL = 12;
        private final Palette palette;
        private final boolean extras;

        Strip(Palette palette, boolean extras) { this.palette = palette; this.extras = extras; }

        @Override public int getIconWidth() { return (extras ? 10 : 8) * (CELL + 2) + 4; }
        @Override public int getIconHeight() { return CELL + 4; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(palette.background());
            g.fillRect(x, y, getIconWidth(), getIconHeight());
            var colours = new ArrayList<>(palette.ansi().subList(0, 8));
            if (extras) { colours.add(palette.cursor()); colours.add(palette.selection()); }
            for (int i = 0; i < colours.size(); i++) {
                g.setColor(colours.get(i));
                g.fillRect(x + 2 + i * (CELL + 2), y + 2, CELL, CELL);
            }
            g.setColor(new Color(palette.foreground().getRed(), palette.foreground().getGreen(), palette.foreground().getBlue(), 80));
            g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
        }
    }
}
