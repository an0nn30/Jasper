package dev.jasper.indexer.ui;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Project Index's Settings page: the indexed projects with Add Folder…, Reindex and Remove, then the declared settings'
 * standard rows. Project actions take effect at once; the declared settings are saved on Apply. UI thread.
 */
public final class IndexerSettingsPanel implements SettingsPanel {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private final IndexerSettings settings;
    private final Projects projects;
    private final Indexer indexer;
    private final Function<ProjectKey, String> location;
    private final Runnable addFolder;
    private final Predicate<String> confirm;
    private Subscription listening = () -> { };

    /**
     * @param settings the declared settings
     * @param projects the indexed projects
     * @param indexer reindexes and removes them
     * @param location how the table names a project's location
     * @param addFolder runs Index Folder…
     * @param confirm asks a yes-or-no question; true means yes
     */
    public IndexerSettingsPanel(IndexerSettings settings, Projects projects, Indexer indexer, Function<ProjectKey, String> location,
                                Runnable addFolder, Predicate<String> confirm) {
        this.settings = settings; this.projects = projects; this.indexer = indexer; this.location = location;
        this.addFolder = addFolder; this.confirm = confirm;
    }

    @Override public JComponent create(SettingsPanelHost host) {
        var page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setOpaque(false);
        var model = new Model();
        var table = new JTable(model);
        table.setName("projects");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName("Indexed projects");
        var scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 160));
        var add = button("add-folder", "Add Folder…");
        var reindex = button("reindex", "Reindex");
        var remove = button("remove", "Remove");
        Runnable selectionChanged = () -> {
            boolean selected = table.getSelectedRow() >= 0;
            reindex.setEnabled(selected);
            remove.setEnabled(selected);
        };
        selectionChanged.run();
        table.getSelectionModel().addListSelectionListener(event -> selectionChanged.run());
        add.addActionListener(event -> addFolder.run());
        reindex.addActionListener(event -> selected(table, model).ifPresent(entry -> indexer.request(entry.project().key(), true, Optional.empty())));
        remove.addActionListener(event -> selected(table, model).ifPresent(entry -> {
            if (confirm.test("Remove " + entry.project().name() + " from Project Index? Its saved index is deleted."))
                indexer.remove(entry.project().key());
        }));
        listening = projects.onChanged(() -> {
            model.refresh();
            selectionChanged.run();
        });
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(reindex);
        buttons.add(remove);
        var section = new JPanel(new BorderLayout(0, 6));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        section.setAlignmentX(0);
        var heading = new JLabel("Projects");
        heading.putClientProperty("html.disable", true);
        section.add(heading, BorderLayout.NORTH);
        section.add(scroll, BorderLayout.CENTER);
        section.add(buttons, BorderLayout.SOUTH);
        page.add(section);
        for (Setting<?> setting : List.<Setting<?>>of(settings.prompt, settings.markers, settings.excludes, settings.ignored,
                settings.maxFiles, settings.refreshMinutes)) {
            JComponent row = host.row(setting);
            row.setAlignmentX(0);
            page.add(row);
        }
        return page;
    }

    @Override public List<String> keywords() { return List.of("project", "index", "files", "reindex", "remove", "add folder"); }

    @Override public void dispose() { listening.close(); }

    private static JButton button(String name, String text) {
        var button = new JButton(text);
        button.setName(name);
        return button;
    }

    private static Optional<Projects.Entry> selected(JTable table, Model model) {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.rows.get(table.convertRowIndexToModel(row)));
    }

    private static String label(Projects.State state) {
        return switch (state) {
            case FRESH -> "Fresh";
            case STALE -> "Stale";
            case TRUNCATED -> "Truncated";
            case MISSING -> "Missing";
            case NEEDS_REINDEX -> "Needs reindex";
            case INDEXING -> "Indexing…";
        };
    }

    private final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"Name", "Location", "Files", "Last indexed", "State"};
        private List<Projects.Entry> rows = projects.entries();

        void refresh() {
            rows = projects.entries();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override public Object getValueAt(int row, int column) {
            Projects.Entry entry = rows.get(row);
            return switch (column) {
                case 0 -> entry.project().name();
                case 1 -> location.apply(entry.project().key());
                case 2 -> entry.index().map(index -> String.format(Locale.ROOT, "%,d", index.size())).orElse("");
                case 3 -> entry.index().map(index -> WHEN.format(index.indexedAt())).orElse("");
                default -> label(entry.state());
            };
        }
    }
}
