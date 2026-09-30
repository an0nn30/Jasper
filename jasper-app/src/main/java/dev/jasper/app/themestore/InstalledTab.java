package dev.jasper.app.themestore;

import dev.jasper.app.settingsui.PageContext;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

/** The Installed tab: the theme plugins in the themes folder, Use, Update and Uninstall, and Install from Disk\u2026. EDT only. */
final class InstalledTab {
    private final ThemeStore store;
    private final PageContext context;
    private final Banner banner;
    private final Consumer<String> use;
    private final List<Runnable> changed = new ArrayList<>();
    private final DefaultListModel<InstalledTheme> model = new DefaultListModel<>();
    private final JList<InstalledTheme> list = new JList<>(model);
    private final JPanel component = new JPanel(new BorderLayout()) {
        @Override public void updateUI() {
            super.updateUI();
            // JPanel's constructor calls this before the fields exist.
            if (title != null) restyle();
        }
    };
    private final JPanel details = new JPanel();
    private final JLabel title = new JLabel();
    private final JLabel meta = new JLabel();
    private final JPanel uses = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
    private final JTextArea problems = new JTextArea();
    private final JButton update = new JButton();
    private final JButton uninstall = new JButton("Uninstall");
    private final JButton fromDisk = new JButton("Install from Disk\u2026");
    private final JButton reveal = new JButton("Reveal Folder");
    private Map<Path, MarketplaceClient.Release> updates = Map.of();
    private boolean marketplaceEnabled = true;
    private int generation;
    private boolean disposed;

    InstalledTab(ThemeStore store, PageContext context, Banner banner, Consumer<String> use) {
        this.store = store;
        this.context = context;
        this.banner = banner;
        this.use = use;
        for (JComponent label : List.of(title, meta, update, uninstall, fromDisk, reveal)) label.putClientProperty("html.disable", true);
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        toolbar.setOpaque(false);
        toolbar.add(fromDisk);
        toolbar.add(reveal);
        fromDisk.addActionListener(event -> chooseFromDisk());
        reveal.addActionListener(event -> store.revealFolder());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Installed theme plugins");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        problems.setEditable(false);
        problems.setLineWrap(true);
        problems.setWrapStyleWord(true);
        problems.setOpaque(false);
        uses.setOpaque(false);
        update.addActionListener(event -> update(list.getSelectedValue()));
        uninstall.addActionListener(event -> uninstall());
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(update);
        buttons.add(uninstall);
        for (JComponent part : List.of(title, meta, uses, buttons, problems)) { part.setAlignmentX(0f); details.add(part); }
        details.add(Box.createVerticalGlue());
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), new JScrollPane(details));
        split.setResizeWeight(0.4);
        split.setBorder(null);
        component.setOpaque(false);
        component.add(toolbar, BorderLayout.NORTH);
        component.add(split, BorderLayout.CENTER);
        restyle();
        showDetails();
    }

    JComponent component() { return component; }

    /** Runs {@code listener} after each reload shows the folder's contents. */
    void onChanged(Runnable listener) { changed.add(listener); }

    void marketplaceEnabled(boolean enabled) { marketplaceEnabled = enabled; showDetails(); }

    /** Each installed Marketplace plugin's xmlId and version. */
    Map<String, String> installedVersions() {
        var versions = new HashMap<String, String>();
        for (int i = 0; i < model.size(); i++) model.get(i).source().ifPresent(source -> versions.put(source.xmlId(), source.version()));
        return versions;
    }

    /** Stops reacting to late results; the dialog disposing its page calls this. */
    void dispose() { disposed = true; generation++; }

    /** Re-reads the folder and shows it, without asking the Marketplace for updates. */
    void loadList() {
        int asked = ++generation;
        store.installed().whenComplete((themes, failure) -> {
            if (asked != generation || failure != null) return;
            show(themes);
        });
    }

    /** Re-reads the folder, then asks the Marketplace for updates to the themes installed from it. */
    void reload() {
        int asked = ++generation;
        store.installed().whenComplete((themes, failure) -> {
            if (asked != generation) return;
            if (failure != null) { banner.error(ThemeStore.message(failure)); return; }
            show(themes);
            if (!marketplaceEnabled) return;
            store.updates(themes).whenComplete((found, ignored) -> {
                if (asked != generation || found == null) return;
                updates = found;
                showDetails();
            });
        });
    }

    private void show(List<InstalledTheme> themes) {
        InstalledTheme selected = list.getSelectedValue();
        Path keep = selected == null ? null : selected.file();
        updates = Map.of();
        model.clear();
        themes.forEach(model::addElement);
        int index = 0;
        for (int i = 0; i < model.size(); i++) if (model.get(i).file().equals(keep)) index = i;
        if (!model.isEmpty()) list.setSelectedIndex(index);
        showDetails();
        changed.forEach(Runnable::run);
    }

    private void showDetails() {
        InstalledTheme theme = list.getSelectedValue();
        uses.removeAll();
        if (theme == null) {
            title.setText(model.isEmpty() ? "No theme plugins installed" : "");
            meta.setText(model.isEmpty() ? "Install one from the Marketplace, or a .jar or .zip from disk." : "");
            problems.setText("");
            update.setVisible(false);
            uninstall.setVisible(false);
        } else {
            title.setText(theme.title());
            meta.setText(String.join("  \u00b7  ", Stream.of(theme.version(), theme.origin(), theme.file().getFileName().toString())
                .filter(part -> !part.isEmpty()).toList()));
            for (String name : theme.themes()) {
                var button = new JButton("Use \u201c" + name + "\u201d");
                button.putClientProperty("html.disable", true);
                button.addActionListener(event -> use.accept(name));
                uses.add(button);
            }
            problems.setText(String.join("\n", theme.problems()));
            MarketplaceClient.Release release = updates.get(theme.file());
            update.setVisible(release != null && marketplaceEnabled);
            if (release != null) update.setText("Update to " + release.version());
            uninstall.setVisible(true);
        }
        details.revalidate();
        details.repaint();
    }

    private void update(InstalledTheme theme) {
        if (theme == null || theme.source().isEmpty()) return;
        var source = theme.source().get();
        busy(true);
        banner.info("Updating " + source.name() + "\u2026");
        store.install(source.marketplaceId(), source.xmlId(), source.name()).whenComplete((result, failure) -> {
            if (disposed) return;
            busy(false);
            if (failure != null) banner.errorOffer(ThemeStore.message(failure), "Retry", () -> update(theme));
            else banner.info("Updated " + source.name() + ".");
            reload();
        });
    }

    private void uninstall() {
        InstalledTheme theme = list.getSelectedValue();
        if (theme == null) return;
        String question = "Uninstall " + theme.title() + "?"
            + (theme.themes().isEmpty() ? "" : " Its themes: " + String.join(", ", theme.themes()) + ".");
        if (!store.confirm(component, question)) return;
        busy(true);
        store.uninstall(theme.file()).whenComplete((ignored, failure) -> {
            if (disposed) return;
            busy(false);
            if (failure != null) banner.error(ThemeStore.message(failure));
            else banner.info("Uninstalled " + theme.title() + ".");
            reload();
        });
    }

    private void chooseFromDisk() {
        context.chooseFile().apply("Install Theme Plugin", Optional.empty()).ifPresent(file -> installFromDisk(file, false));
    }

    private void installFromDisk(Path file, boolean replace) {
        busy(true);
        store.installFromDisk(file, replace).whenComplete((result, failure) -> {
            if (disposed) return;
            busy(false);
            if (ThemeStore.cause(failure) instanceof ThemeInstaller.Conflict conflict) {
                if (store.confirm(component, conflict.existing().getFileName() + " is already installed. Replace it?")) installFromDisk(file, true);
                return;
            }
            if (failure != null) { banner.error(ThemeStore.message(failure)); return; }
            reload();
            String first = result.themes().getFirst();
            banner.offer("Installed " + result.file().getFileName() + ".", "Use \u201c" + first + "\u201d", () -> use.accept(first));
        });
    }

    private void busy(boolean busy) {
        for (JButton button : List.of(update, uninstall, fromDisk)) button.setEnabled(!busy);
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        meta.setForeground(UIManager.getColor("Label.disabledForeground"));
        problems.setForeground(UIManager.getColor("Actions.Red"));
    }

    // Test access.
    List<String> listed() { var titles = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) titles.add(model.get(i).title()); return titles; }
    void select(String name) { for (int i = 0; i < model.size(); i++) if (model.get(i).title().equals(name)) list.setSelectedIndex(i); }
    String details() { return String.join("\n", title.getText(), meta.getText(), problems.getText()); }
    List<String> useButtons() { return Stream.of(uses.getComponents()).map(button -> ((JButton) button).getText()).toList(); }
    JButton updateButton() { return update; }
    JButton uninstallButton() { return uninstall; }
    JButton installFromDiskButton() { return fromDisk; }

    /** Title in bold, then the themes it provides, its version and source; problems in red. */
    private static final class Renderer extends JPanel implements ListCellRenderer<InstalledTheme> {
        private final JLabel name = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            name.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            add(name);
            add(line);
        }

        @Override public Component getListCellRendererComponent(JList<? extends InstalledTheme> list, InstalledTheme value, int index,
                                                                boolean selected, boolean focused) {
            name.setText(value.title());
            name.setFont(list.getFont().deriveFont(Font.BOLD));
            boolean broken = value.themes().isEmpty();
            line.setText(broken ? "Not usable: " + value.problems().stream().findFirst().orElse("no themes")
                : String.join(", ", value.themes()) + (value.version().isEmpty() ? "" : "  \u00b7  " + value.version()));
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            line.setForeground(selected ? list.getSelectionForeground()
                : UIManager.getColor(broken ? "Actions.Red" : "Label.disabledForeground"));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
