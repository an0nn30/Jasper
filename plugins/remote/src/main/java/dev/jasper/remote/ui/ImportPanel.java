package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.ConfigImport;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.*;

/** Passive, editable config preview. Existing rows require an explicit Update selection. */
public final class ImportPanel extends JPanel {
    final List<JCheckBox> checks = new ArrayList<>();
    final List<JButton> choose = new ArrayList<>();
    private final List<JTextArea> details = new ArrayList<>();
    private final List<ConfigImport.Candidate> candidates;
    final JButton importButton = new JButton("Import"), cancel = new JButton("Cancel"), refresh = new JButton("Refresh");
    final JTextArea skipped = text(), status = text();
    public ImportPanel(List<ConfigImport.Candidate> candidates, List<String> skippedEntries,
            Consumer<List<ConfigImport.Candidate>> importSelected, Consumer<ConfigImport.Candidate> pick, Runnable onRefresh, Runnable onCancel) {
        super(new BorderLayout(0, 8)); this.candidates = List.copyOf(candidates);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        var rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        if (candidates.isEmpty()) rows.add(new JLabel("No importable Host entries found"));
        for (var c : candidates) {
            var row = new JPanel(new BorderLayout(6, 2));
            var check = new JCheckBox((c.previous().isPresent() ? "Update: " : "New: ") + c.name() + " — " + c.username() + "@" + c.hostname() + ":" + c.port(), c.previous().isEmpty() && c.errors().isEmpty());
            check.putClientProperty("html.disable", true);
            checks.add(check); row.add(check, BorderLayout.NORTH);
            var detail = text();
            detail.setText(!c.errors().isEmpty() ? String.join("; ", c.errors()) : c.identities().isEmpty() ? "Choose a stored Vault key or password" : String.join("\n", c.identities().stream().map(Object::toString).toList()));
            details.add(detail); row.add(detail, BorderLayout.CENTER);
            var button = new JButton("Choose credential…"); choose.add(button); button.addActionListener(e -> pick.accept(c));
            if (c.identities().isEmpty()) row.add(button, BorderLayout.EAST);
            rows.add(row);
        }
        var scroll = new JScrollPane(rows); scroll.setPreferredSize(new java.awt.Dimension(660, 320)); add(scroll, BorderLayout.CENTER);
        skipped.setText(skippedEntries.isEmpty() ? "" : "Not importable: " + String.join(", ", skippedEntries));
        var south = new JPanel(); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS)); south.add(skipped); south.add(status);
        var buttons = new JPanel(); buttons.add(refresh); buttons.add(cancel); buttons.add(importButton); south.add(buttons); add(south, BorderLayout.SOUTH);
        importButton.addActionListener(e -> {
            var selected = new ArrayList<ConfigImport.Candidate>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isSelected() && candidates.get(i).errors().isEmpty()) selected.add(candidates.get(i));
            importSelected.accept(List.copyOf(selected));
        });
        cancel.addActionListener(e -> onCancel.run()); refresh.addActionListener(e -> onRefresh.run()); editable();
    }
    private static JTextArea text() { var t = new JTextArea(); t.setEditable(false); t.setLineWrap(true); t.setWrapStyleWord(true); t.setOpaque(false); return t; }
    public void credential(UUID candidate, String name) {
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).id().equals(candidate)) details.get(i).setText("Vault: " + name);
    }
    public void busy() { enableControls(false); status.setText("Reviewing credentials in Vault…"); }
    public void editable() { enableControls(true); }
    public void failed(String message) { editable(); status.setText(message); }
    public void completed(String message) { enableControls(false); cancel.setText("Close"); status.setText(message); }
    private void enableControls(boolean enabled) {
        refresh.setEnabled(enabled); importButton.setEnabled(enabled && !candidates.isEmpty());
        for (int i = 0; i < candidates.size(); i++) { checks.get(i).setEnabled(enabled && candidates.get(i).errors().isEmpty()); choose.get(i).setEnabled(enabled && candidates.get(i).errors().isEmpty()); }
    }
}
