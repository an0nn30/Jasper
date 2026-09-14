package dev.jasper.app;

import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Manager contents in standard Swing: toolbar row, sidebar list, table over editor, status strip. */
final class VaultManagerPanel extends JPanel {
    enum Entry {
        VAULT_HEADER("VAULT"), ALL("All credentials"), LOGINS("Logins"), KEYS("SSH keys"),
        MANAGEMENT_HEADER("MANAGEMENT"), SETTINGS("Settings"), IMPORT("Import key");
        final String label;
        Entry(String label) { this.label = label; }
        boolean header() { return this == VAULT_HEADER || this == MANAGEMENT_HEADER; }
        boolean category() { return this == ALL || this == LOGINS || this == KEYS; }
    }

    final JTextField search = new JTextField(20);
    final JList<Entry> sidebar = new JList<>(Entry.values());
    final JTable table = new JTable();
    final VaultLoginForm login = new VaultLoginForm(true);
    final VaultKeyForm keyForm = new VaultKeyForm();
    final JLabel title = new JLabel("Select a credential"), kind = new JLabel(" ");
    final JLabel message = new JLabel(" "), status = new JLabel(" "), expiry = new JLabel(" ");
    Runnable onAdd = () -> {}, onImport = () -> {}, onSettings = () -> {}, onLock = () -> {};
    Runnable onSave = () -> {}, onRevert = () -> {}, onDelete = () -> {};
    Consumer<Runnable> navigate = Runnable::run;
    final Action addAction = VaultUi.action("Add credential", "add", () -> onAdd.run());
    final Action importAction = VaultUi.action("Import key", "import", () -> onImport.run());
    final Action settingsAction = VaultUi.action("Settings", "settings", () -> onSettings.run());
    final Action lockAction = VaultUi.action("Lock vault", "lock", () -> onLock.run());
    final Action saveAction = VaultUi.action("Save", "save", () -> onSave.run());
    final Action revertAction = VaultUi.action("Revert", null, () -> onRevert.run());
    final Action deleteAction = VaultUi.action("Delete", null, () -> onDelete.run());
    final JButton addTop = new JButton(addAction), importTop = new JButton(importAction);
    final JButton settingsTop = new JButton(settingsAction), lockTop = new JButton(lockAction);
    final JButton saveButton = new JButton(saveAction), revertButton = new JButton(revertAction), deleteButton = new JButton(deleteAction);

    private final JPanel body = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final JPanel editor = new JPanel(new BorderLayout());
    private final List<UUID> rows = new ArrayList<>();
    private VaultSnapshot snapshot;
    private UUID selected;
    private Entry category = Entry.ALL;
    private String filter = "", acceptedSearch = "";
    private boolean loading;
    private final AbstractTableModel model = new AbstractTableModel() {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int column) { return new String[]{"Name", "Username", "Authentication"}[column]; }
        @Override public Object getValueAt(int row, int column) {
            UUID id = rows.get(row); var l = findLogin(id); var k = findKey(id);
            return switch (column) {
                case 0 -> l != null ? l.name() : k.name();
                case 1 -> l != null ? l.username() : "";
                default -> l != null ? (l.keyId() != null ? (l.hasPassword() ? "SSH key + Password" : "SSH key") : "Password") : k.algorithm();
            };
        }
    };

    VaultManagerPanel() {
        super(new BorderLayout());
        setPreferredSize(new Dimension(900, 600)); setMinimumSize(new Dimension(0, 0));
        search.putClientProperty("JTextField.placeholderText", "Search credentials");
        search.getAccessibleContext().setAccessibleName("Search credentials");
        settingsTop.setText(""); settingsTop.setToolTipText("Vault settings");
        settingsTop.getAccessibleContext().setAccessibleName("Vault settings");
        JPanel toolbar = new JPanel(new BorderLayout()); VaultUi.pad(toolbar, 8);
        toolbar.add(VaultUi.row(search, addTop, importTop));
        toolbar.add(VaultUi.row(settingsTop, lockTop), BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setCellRenderer(new SidebarRenderer());
        sidebar.setFixedCellHeight(28);
        sidebar.getAccessibleContext().setAccessibleName("Vault navigation");
        sidebar.setSelectedValue(Entry.ALL, false);
        sidebar.addListSelectionListener(e -> { if (!loading && !e.getValueIsAdjusting()) sidebarSelected(); });
        JScrollPane side = new JScrollPane(sidebar); side.setPreferredSize(new Dimension(160, 0)); side.setMinimumSize(new Dimension(120, 0));

        table.setModel(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28); table.setShowVerticalLines(false); table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName("Credentials");
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, focus, row, column);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
                label.setIcon(column == 0 ? VaultIcons.icon(findLogin(rows.get(row)) != null ? "login" : "key") : null);
                return label;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            UUID id = rows.get(table.getSelectedRow());
            loading = true; restoreSelection(); loading = false;
            if (!Objects.equals(id, selected)) navigate.accept(() -> select(id));
        });
        JScrollPane list = new JScrollPane(table); list.setMinimumSize(new Dimension(0, 80));

        JPanel detail = new JPanel(new BorderLayout(0, 10)); VaultUi.pad(detail, 12);
        JPanel heading = new JPanel(new BorderLayout());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        heading.add(title); heading.add(VaultUi.row(kind, deleteButton), BorderLayout.EAST);
        detail.add(heading, BorderLayout.NORTH);
        JScrollPane editorScroll = new JScrollPane(editor); editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.setMinimumSize(new Dimension(0, 0));
        detail.add(editorScroll);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(message); bottom.add(VaultUi.row(revertButton, saveButton), BorderLayout.EAST);
        detail.add(bottom, BorderLayout.SOUTH);
        detail.setMinimumSize(new Dimension(0, 160));

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, detail);
        vertical.setResizeWeight(0.4); vertical.setBorder(BorderFactory.createEmptyBorder());
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, vertical);
        split.setResizeWeight(0); split.setBorder(BorderFactory.createEmptyBorder());
        body.add(split); add(body);

        JPanel footer = new JPanel(new BorderLayout()); VaultUi.pad(footer, 8);
        footer.add(status); footer.add(expiry, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);

        VaultUi.changes(search, () -> {
            if (loading) return;
            String text = search.getText();
            SwingUtilities.invokeLater(() -> {
                if (loading || !search.getText().equals(text)) return;
                navigate.accept(() -> { acceptedSearch = text; filter = text.toLowerCase(Locale.ROOT).strip(); rebuild(); });
            });
        });
        login.changed = () -> refreshButtons(false);
        keyForm.changed = () -> refreshButtons(false);
        refreshButtons(false);
    }

    private void sidebarSelected() {
        Entry entry = sidebar.getSelectedValue();
        if (entry == null) return;
        if (entry.header()) { syncSidebar(); return; }
        if (entry == Entry.SETTINGS) { syncSidebar(); onSettings.run(); return; }
        if (entry == Entry.IMPORT) { syncSidebar(); onImport.run(); return; }
        syncSidebar();
        navigate.accept(() -> { category = entry; syncSidebar(); rebuild(); });
    }

    private void syncSidebar() {
        boolean before = loading; loading = true;
        sidebar.setSelectedValue(category, false);
        loading = before;
    }

    void showSnapshot(VaultSnapshot s) {
        snapshot = s;
        int total = s.logins().size() + s.keys().size();
        status.setText(total + " credentials    " + (s.settings().autoLockMinutes() == 0 ? "Auto-lock off" : "Auto-lock " + s.settings().autoLockMinutes() + " min"));
        expiry.setText(VaultUi.rememberedText(s.rememberedUntil(), Instant.now()));
        sidebar.repaint();
        if (s.locked()) {
            loading = true; selected = null; rows.clear(); model.fireTableDataChanged();
            login.clear(); keyForm.clear(); editor.removeAll();
            title.setText("Vault locked"); title.setIcon(null); kind.setText(" "); loading = false;
        } else {
            body.removeAll(); body.add(split); rebuild();
        }
        refreshButtons(false); revalidate(); repaint();
    }

    void lockedContent(JComponent form) { body.removeAll(); body.add(form); revalidate(); repaint(); }

    private void rebuild() {
        if (snapshot == null || snapshot.locked()) return;
        loading = true; syncSidebar(); rows.clear();
        List<Object[]> entries = new ArrayList<>();
        if (category != Entry.KEYS) for (var l : snapshot.logins())
            if ((l.name() + " " + l.username()).toLowerCase(Locale.ROOT).contains(filter)) entries.add(new Object[]{l.name(), l.id()});
        if (category != Entry.LOGINS) for (var k : snapshot.keys())
            if ((k.name() + " " + k.algorithm() + " " + k.fingerprint()).toLowerCase(Locale.ROOT).contains(filter)) entries.add(new Object[]{k.name(), k.id()});
        entries.sort(Comparator.comparing(e -> ((String) e[0]).toLowerCase(Locale.ROOT)));
        for (Object[] e : entries) rows.add((UUID) e[1]);
        model.fireTableDataChanged(); loading = false;
        select(rows.contains(selected) ? selected : rows.isEmpty() ? null : rows.getFirst());
    }

    void select(UUID id) {
        loading = true; selected = id; restoreSelection(); editor.removeAll();
        var l = findLogin(id); var k = findKey(id);
        if (l != null) {
            title.setText(l.name()); title.setIcon(VaultIcons.icon("login")); kind.setText("Login");
            login.load(l, snapshot.keys()); keyForm.clear(); editor.add(login, BorderLayout.NORTH);
        } else if (k != null) {
            title.setText(k.name()); title.setIcon(VaultIcons.icon("key")); kind.setText("SSH key");
            keyForm.load(k); login.clear(); editor.add(keyForm, BorderLayout.NORTH);
        } else {
            login.clear(); keyForm.clear(); title.setText("Select a credential"); title.setIcon(null); kind.setText(" ");
        }
        loading = false; refreshButtons(false); editor.revalidate(); editor.repaint();
    }

    private void restoreSelection() {
        int row = rows.indexOf(selected);
        if (row < 0) table.clearSelection(); else table.setRowSelectionInterval(row, row);
    }

    VaultSnapshot.LoginInfo findLogin(UUID id) {
        return snapshot == null || id == null ? null : snapshot.logins().stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }
    VaultSnapshot.KeyInfo findKey(UUID id) {
        return snapshot == null || id == null ? null : snapshot.keys().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    }
    void restoreSearch() { loading = true; search.setText(acceptedSearch); syncSidebar(); loading = false; }
    UUID selected() { return selected; }
    Entry category() { return category; }
    boolean dirty() { return login.dirty() || keyForm.dirty(); }

    void refreshButtons(boolean busy) {
        boolean open = snapshot != null && !snapshot.locked();
        saveAction.setEnabled(open && !busy && dirty()); revertAction.setEnabled(open && !busy && dirty());
        deleteAction.setEnabled(open && !busy && selected != null);
        addAction.setEnabled(open && !busy); importAction.setEnabled(open && !busy); settingsAction.setEnabled(open && !busy);
        lockAction.setEnabled(snapshot != null && snapshot.exists() && !busy);
        table.setEnabled(!busy); search.setEnabled(!busy); sidebar.setEnabled(!busy);
        VaultUi.enabled(editor, !busy);
    }

    private final class SidebarRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Entry entry = (Entry) value;
            String text = entry.label;
            if (snapshot != null && entry.category()) {
                int count = switch (entry) {
                    case ALL -> snapshot.logins().size() + snapshot.keys().size();
                    case LOGINS -> snapshot.logins().size();
                    default -> snapshot.keys().size();
                };
                text = entry.label + "  " + count;
            }
            JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected && !entry.header(), cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder(0, entry.header() ? 8 : 14, 0, 8));
            label.setFont(label.getFont().deriveFont(entry.header() ? Font.BOLD : Font.PLAIN, entry.header() ? 10f : label.getFont().getSize2D()));
            label.setEnabled(!entry.header());
            label.setIcon(switch (entry) {
                case ALL -> VaultIcons.icon("unlock"); case LOGINS -> VaultIcons.icon("login"); case KEYS -> VaultIcons.icon("key");
                case SETTINGS -> VaultIcons.icon("settings"); case IMPORT -> VaultIcons.icon("import"); default -> null;
            });
            return label;
        }
    }
}
