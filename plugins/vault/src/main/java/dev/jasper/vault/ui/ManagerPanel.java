package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.SshKey;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Searchable, single-list manager. Only non-secret row metadata is retained by the view. */
final class ManagerPanel extends JPanel implements AutoCloseable {
    final JList<VaultManager.Row> rows = new JList<>(new DefaultListModel<>());
    final JList<Grant> grants = new JList<>(new DefaultListModel<>());
    final JTextField search = new JTextField();
    final JComboBox<String> kind = new JComboBox<>(new String[]{"All Types", "Login", "SSH Key", "Secure Note"});
    final JButton add = action("Add entry", VaultIcons.Shape.ADD), edit = action("Edit entry", VaultIcons.Shape.EDIT);
    final JButton delete = action("Delete entry", VaultIcons.Shape.DELETE), copyPublic = action("Copy public key", VaultIcons.Shape.COPY);
    final JButton lock = action("Lock vault", VaultIcons.Shape.LOCK), more = action("More vault actions", VaultIcons.Shape.MORE);
    final JButton unlock = new JButton("Unlock"), revoke = new JButton("Revoke"), closeButton = new JButton("Okay"), cancelButton = new JButton("Cancel");
    final JLabel status = new JLabel(" ");
    final JPopupMenu addMenu = new JPopupMenu(), moreMenu = new JPopupMenu();
    private final JLabel empty = new JLabel("Nothing to show", SwingConstants.CENTER);
    private final JPanel listCards = new JPanel(new CardLayout());
    private final JPanel cards = new JPanel(new CardLayout()), locked = new JPanel(new BorderLayout());
    private final JPanel passiveUnlock = new JPanel();
    private final Function<UUID, Optional<Object>> entry;
    private List<VaultManager.Row> allRows = List.of();
    private boolean unlocked;

    ManagerPanel(Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate, Runnable onChangePassword,
                 Runnable onLock, Runnable onUnlock) {
        super(new BorderLayout(0, 18)); this.entry = entry;
        setBorder(BorderFactory.createEmptyBorder(18, 20, 10, 20));
        rows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); grants.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rows.setCellRenderer(new RowRenderer());
        search.putClientProperty("JTextField.leadingIcon", new VaultIcons(VaultIcons.Shape.SEARCH));
        search.getAccessibleContext().setAccessibleName("Search vault");
        search.setToolTipText("Search names, usernames and key fingerprints");
        kind.getAccessibleContext().setAccessibleName("Filter credential type");
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(kind);
        for (var button : List.of(add, edit, delete, copyPublic)) actions.add(button);
        var divider = new JSeparator(SwingConstants.VERTICAL); divider.setPreferredSize(new Dimension(1, 20)); actions.add(divider);
        actions.add(lock); actions.add(more);
        var header = new JPanel(new BorderLayout(14, 0)); header.add(search, BorderLayout.CENTER); header.add(actions, BorderLayout.EAST);
        listCards.add(new JScrollPane(rows), "rows");
        var emptyFrame = new JPanel(new BorderLayout()); emptyFrame.setBorder(BorderFactory.createLineBorder(borderColor()));
        empty.setForeground(mutedColor()); emptyFrame.add(empty); listCards.add(emptyFrame, "empty");
        var entries = new JPanel(new BorderLayout(0, 10)); entries.add(header, BorderLayout.NORTH); entries.add(listCards, BorderLayout.CENTER);
        var grantPanel = new JPanel(new BorderLayout(0, 12)); grantPanel.add(new JLabel("Saved plugin grants"), BorderLayout.NORTH);
        grantPanel.add(new JScrollPane(grants), BorderLayout.CENTER);
        var grantActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        var back = new JButton("Back to entries"); back.addActionListener(event -> showEntries()); grantActions.add(back); grantActions.add(revoke);
        grantPanel.add(grantActions, BorderLayout.SOUTH);
        passiveUnlock.add(new JLabel("Vault locked")); passiveUnlock.add(unlock); locked.add(passiveUnlock, BorderLayout.CENTER);
        cards.add(entries, "entries"); cards.add(grantPanel, "grants"); cards.add(locked, "locked"); add(cards, BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout(12, 0)); status.setForeground(mutedColor());
        status.setPreferredSize(new Dimension(0, 24)); footer.add(status, BorderLayout.CENTER);
        var closing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); closing.add(cancelButton); closing.add(closeButton); footer.add(closing, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
        for (var type : VaultManager.Type.values()) menu(addMenu, type.toString(), () -> onAdd.accept(type));
        addMenu.addSeparator(); menu(addMenu, "Generate SSH Key...", onGenerate);
        menu(moreMenu, "Saved plugin grants...", () -> { if (unlocked) ((CardLayout) cards.getLayout()).show(cards, "grants"); });
        menu(moreMenu, "Change Master Password...", onChangePassword);
        add.addActionListener(event -> popup(addMenu, add)); more.addActionListener(event -> popup(moreMenu, more));
        grants.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Grant grant ? grantLabel(grant) : value, index, selected, focus);
            }
        });
        rows.addListSelectionListener(event -> updateActions());
        grants.addListSelectionListener(event -> revoke.setEnabled(grants.getSelectedValue() != null));
        edit.addActionListener(event -> selected().ifPresent(onEdit)); delete.addActionListener(event -> selected().ifPresent(onDelete));
        copyPublic.addActionListener(event -> selected().ifPresent(onCopy));
        revoke.addActionListener(event -> { Grant grant = grants.getSelectedValue(); if (grant != null) onRevoke.accept(grant); });
        lock.addActionListener(event -> onLock.run()); unlock.addActionListener(event -> onUnlock.run());
        rows.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = rows.locationToIndex(event.getPoint());
                if (event.getClickCount() == 2 && index >= 0 && rows.getCellBounds(index, index).contains(event.getPoint())) edit.doClick();
            }
        });
        rows.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "edit-entry");
        rows.getActionMap().put("edit-entry", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { edit.doClick(); }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(); }
            @Override public void removeUpdate(DocumentEvent event) { filter(); }
            @Override public void changedUpdate(DocumentEvent event) { filter(); }
        });
        kind.addActionListener(event -> filter()); updateActions(); revoke.setEnabled(false);
    }
    private static JButton action(String title, VaultIcons.Shape shape) {
        var button = new JButton(new VaultIcons(shape)); button.setToolTipText(title);
        button.getAccessibleContext().setAccessibleName(title); button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setPreferredSize(new Dimension(24, 24)); return button;
    }
    private static void menu(JPopupMenu menu, String label, Runnable action) {
        var item = new JMenuItem(label); item.addActionListener(event -> action.run()); menu.add(item);
    }
    private static void popup(JPopupMenu menu, JButton button) { if (button.isShowing()) menu.show(button, 0, button.getHeight()); }
    private static Color mutedColor() { Color color = UIManager.getColor("Label.disabledForeground"); return color == null ? Color.GRAY : color; }
    private static Color borderColor() { Color color = UIManager.getColor("Component.borderColor"); return color == null ? Color.GRAY : color; }
    Optional<UUID> selected() { return Optional.ofNullable(rows.getSelectedValue()).map(VaultManager.Row::id); }
    private void showEntries() { ((CardLayout) cards.getLayout()).show(cards, unlocked ? "entries" : "locked"); }
    void refresh(List<VaultManager.Row> nextRows, List<Grant> nextGrants, String text, boolean unlocked) {
        boolean stateChanged = this.unlocked != unlocked; this.unlocked = unlocked;
        allRows = unlocked ? List.copyOf(nextRows) : List.of(); filter();
        var grantsModel = (DefaultListModel<Grant>) grants.getModel(); grantsModel.clear(); if (unlocked) grantsModel.addAll(nextGrants);
        status.setText(text); status.setToolTipText(text);
        if (stateChanged || !unlocked) { addMenu.setVisible(false); moreMenu.setVisible(false); showEntries(); }
    }
    private void filter() {
        Optional<UUID> selection = selected(); String query = search.getText().strip().toLowerCase(Locale.ROOT);
        String type = (String) kind.getSelectedItem();
        var model = (DefaultListModel<VaultManager.Row>) rows.getModel(); model.clear();
        allRows.stream().filter(row -> "All Types".equals(type) || row.type().toString().equals(type))
            .filter(row -> (row.name() + " " + row.subtitle()).toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(VaultManager.Row::name, String.CASE_INSENSITIVE_ORDER)).forEach(model::addElement);
        selection.ifPresent(this::selectVisible);
        empty.setText(allRows.isEmpty() ? "Nothing to show" : "No matching entries");
        ((CardLayout) listCards.getLayout()).show(listCards, model.isEmpty() ? "empty" : "rows"); updateActions();
    }
    void select(UUID id) {
        // Palette navigation must reveal the requested row even if a prior filter hid it.
        if (allRows.stream().noneMatch(row -> row.id().equals(id))) return;
        if (!selectVisible(id)) { search.setText(""); kind.setSelectedIndex(0); selectVisible(id); }
        showEntries();
    }
    private boolean selectVisible(UUID id) {
        for (int index = 0; index < rows.getModel().getSize(); index++) if (rows.getModel().getElementAt(index).id().equals(id)) {
            rows.setSelectedIndex(index); rows.ensureIndexIsVisible(index); return true;
        }
        return false;
    }
    void unlockContent(JComponent content) { locked.removeAll(); locked.add(content, BorderLayout.CENTER); locked.revalidate(); locked.repaint(); }
    void passiveUnlock() { unlockContent(passiveUnlock); }
    String grantLabel(Grant grant) {
        String name = allRows.stream().filter(row -> row.id().equals(grant.credentialId())).map(VaultManager.Row::name).findFirst().orElse(grant.credentialId().toString());
        return grant.pluginId() + " \u2014 " + name;
    }
    private void updateActions() {
        var selected = rows.getSelectedValue(); edit.setEnabled(selected != null); delete.setEnabled(selected != null);
        copyPublic.setEnabled(selected != null && selected.type() == VaultManager.Type.SSH_KEY);
    }
    private String subtitle(VaultManager.Row row) {
        return entry.apply(row.id()).map(value -> switch (value) {
            case Account a -> a.username() + " \u00b7 " + (a.auth() instanceof Auth.Password ? "password" : a.auth() instanceof Auth.Key ? "SSH key" : "SSH key and password");
            case SshKey k -> k.algorithm() + " \u00b7 " + k.fingerprint();
            default -> row.subtitle();
        }).orElse(row.subtitle());
    }
    private final class RowRenderer extends JPanel implements ListCellRenderer<VaultManager.Row> {
        private final JLabel name = new JLabel(), detail = new JLabel(), type = new JLabel();
        RowRenderer() {
            super(new BorderLayout(8, 0)); setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            var lines = new JPanel(new java.awt.GridLayout(2, 1)); lines.setOpaque(false); lines.add(name); lines.add(detail);
            for (JLabel label : List.of(name, detail, type)) label.putClientProperty("html.disable", true);
            add(lines, BorderLayout.CENTER); add(type, BorderLayout.EAST);
        }
        @Override public Component getListCellRendererComponent(JList<? extends VaultManager.Row> list, VaultManager.Row row, int index, boolean selected, boolean focus) {
            name.setText(row.name()); detail.setText(subtitle(row)); type.setText(row.type().toString());
            getAccessibleContext().setAccessibleName(row.name() + ", " + row.type() + ", " + detail.getText());
            name.setFont(list.getFont()); detail.setFont(list.getFont().deriveFont(list.getFont().getSize2D() - 1)); type.setFont(detail.getFont());
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            Color secondary = selected ? list.getSelectionForeground() : mutedColor(); detail.setForeground(secondary); type.setForeground(secondary);
            return this;
        }
    }
    @Override public void removeNotify() {
        if (getRootPane() != null && getRootPane().getDefaultButton() == closeButton)
            getRootPane().setDefaultButton(null);
        super.removeNotify();
    }
    @Override public void addNotify() {
        super.addNotify();
        if (getRootPane() != null) getRootPane().setDefaultButton(closeButton);
    }
    @Override public void close() { allRows = List.of(); ((DefaultListModel<?>) rows.getModel()).clear(); ((DefaultListModel<?>) grants.getModel()).clear(); addMenu.setVisible(false); moreMenu.setVisible(false); }
}
