package dev.jasper.vault.ui;

import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.model.Grant;
import dev.jasper.vault.model.Note;
import dev.jasper.vault.model.SshKey;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Headless manager content. Borrowed model values are rendered synchronously and never retained. */
final class ManagerPanel extends JPanel implements AutoCloseable {
    final JList<VaultManager.Row> rows = new JList<>(new DefaultListModel<>());
    final JList<Grant> grants = new JList<>(new DefaultListModel<>());
    final JButton add = new JButton("Add"), edit = new JButton("Edit"), delete = new JButton("Delete");
    final JButton copyPublic = new JButton("Copy public key"), generate = new JButton("Generate Key...");
    final JButton changePassword = new JButton("Change Password..."), lock = new JButton("Lock"), unlock = new JButton("Unlock");
    final JButton revoke = new JButton("Revoke");
    final JComboBox<VaultManager.Type> kind = new JComboBox<>(VaultManager.Type.values());
    final SecretDocument note = new SecretDocument();
    final JTextArea metadata = new JTextArea(6, 35);
    final JLabel status = new JLabel(" ");
    private final JPanel cards = new JPanel(new CardLayout()), locked = new JPanel(new BorderLayout());
    private final JPanel passiveUnlock = new JPanel();
    private final Function<UUID, Optional<Object>> entry;

    ManagerPanel(Function<UUID, Optional<Object>> entry, Consumer<VaultManager.Type> onAdd,
                 Consumer<UUID> onEdit, Consumer<UUID> onDelete, Consumer<UUID> onCopy,
                 Consumer<Grant> onRevoke, Runnable onGenerate, Runnable onChangePassword,
                 Runnable onLock, Runnable onUnlock) {
        super(new BorderLayout(8, 8)); this.entry = entry;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        rows.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); grants.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        metadata.setEditable(false); metadata.setLineWrap(true); metadata.setWrapStyleWord(true);
        var noteArea = new JTextArea(note, null, 8, 35); noteArea.setEditable(false); noteArea.setLineWrap(true);
        var details = new JPanel(new BorderLayout(8, 8)); details.add(new JScrollPane(metadata), BorderLayout.NORTH);
        details.add(new JScrollPane(noteArea), BorderLayout.CENTER);
        var entries = new JPanel(new BorderLayout(8, 8));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(rows), details); split.setResizeWeight(.35);
        entries.add(split, BorderLayout.CENTER);
        var buttons = new JPanel();
        for (JComponent component : List.of(kind, add, edit, delete, copyPublic)) buttons.add(component);
        entries.add(buttons, BorderLayout.SOUTH);
        var grantPanel = new JPanel(new BorderLayout(8, 8)); grantPanel.add(new JScrollPane(grants), BorderLayout.CENTER);
        grantPanel.add(revoke, BorderLayout.SOUTH);
        var tabs = new JTabbedPane(); tabs.addTab("Entries", entries); tabs.addTab("Grants", grantPanel);
        var unlocked = new JPanel(new BorderLayout(8, 8)); unlocked.add(tabs, BorderLayout.CENTER);
        var tools = new JPanel(); tools.add(generate); tools.add(changePassword); tools.add(lock); unlocked.add(tools, BorderLayout.NORTH);
        passiveUnlock.add(new JLabel("Vault locked")); passiveUnlock.add(unlock); locked.add(passiveUnlock, BorderLayout.CENTER);
        cards.add(unlocked, "unlocked"); cards.add(locked, "locked"); add(cards, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        grants.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Grant grant ? grantLabel(grant) : value, index, selected, focus);
            }
        });
        rows.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) details(); });
        grants.addListSelectionListener(event -> revoke.setEnabled(grants.getSelectedValue() != null));
        add.addActionListener(event -> onAdd.accept((VaultManager.Type) kind.getSelectedItem()));
        edit.addActionListener(event -> selected().ifPresent(onEdit)); delete.addActionListener(event -> selected().ifPresent(onDelete));
        copyPublic.addActionListener(event -> selected().ifPresent(onCopy));
        revoke.addActionListener(event -> { Grant grant = grants.getSelectedValue(); if (grant != null) onRevoke.accept(grant); });
        generate.addActionListener(event -> onGenerate.run()); changePassword.addActionListener(event -> onChangePassword.run());
        lock.addActionListener(event -> onLock.run()); unlock.addActionListener(event -> onUnlock.run());
        details(); revoke.setEnabled(false);
    }
    Optional<UUID> selected() { return Optional.ofNullable(rows.getSelectedValue()).map(VaultManager.Row::id); }
    void refresh(List<VaultManager.Row> nextRows, List<Grant> nextGrants, String text, boolean unlocked) {
        Optional<UUID> selection = selected(); note.clear();
        var rowModel = (DefaultListModel<VaultManager.Row>) rows.getModel(); rowModel.clear(); rowModel.addAll(nextRows);
        var grantsModel = (DefaultListModel<Grant>) grants.getModel(); grantsModel.clear(); grantsModel.addAll(nextGrants);
        selection.ifPresent(this::select); status.setText(text);
        ((CardLayout) cards.getLayout()).show(cards, unlocked ? "unlocked" : "locked");
        details();
    }
    void select(UUID id) {
        for (int index = 0; index < rows.getModel().getSize(); index++) {
            if (rows.getModel().getElementAt(index).id().equals(id)) { rows.setSelectedIndex(index); rows.ensureIndexIsVisible(index); return; }
        }
    }
    void unlockContent(JComponent content) { locked.removeAll(); locked.add(content, BorderLayout.CENTER); locked.revalidate(); locked.repaint(); }
    void passiveUnlock() { unlockContent(passiveUnlock); }
    String grantLabel(Grant grant) {
        String name = grant.credentialId().toString();
        for (int i = 0; i < rows.getModel().getSize(); i++) {
            var row = rows.getModel().getElementAt(i); if (row.id().equals(grant.credentialId())) { name = row.name(); break; }
        }
        return grant.pluginId() + " \u2014 " + name;
    }
    private void details() {
        note.clear(); metadata.setText("");
        Optional<Object> selected = selected().flatMap(entry);
        edit.setEnabled(selected.isPresent()); delete.setEnabled(selected.isPresent());
        copyPublic.setEnabled(selected.filter(SshKey.class::isInstance).isPresent());
        selected.ifPresent(value -> {
            switch (value) {
                case Account a -> {
                    String authentication = switch (a.auth()) {
                        case Auth.Password p -> "Password stored";
                        case Auth.Key k -> "Private key: " + k.keyPath();
                        case Auth.KeyAndPassword both -> "Password stored\nPrivate key: " + both.keyPath();
                    };
                    metadata.setText(a.name() + "\nUsername: " + a.username() + "\n" + authentication + "\nUpdated: " + a.updated());
                }
                case SshKey k -> metadata.setText(k.name() + "\n" + k.algorithm() + "\n" + k.fingerprint()
                    + "\nPrivate: " + k.privatePath() + "\nPublic: " + k.publicPath() + "\n" + k.comment());
                case Note n -> { metadata.setText(n.name() + "\nUpdated: " + n.updated()); note.replace(n.text()); }
                default -> throw new IllegalArgumentException("Unknown vault entry");
            }
        });
    }
    @Override public void close() { note.clear(); metadata.setText(""); rows.clearSelection(); }
}
