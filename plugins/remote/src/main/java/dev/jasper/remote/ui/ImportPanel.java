package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.RemoteHost;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** "Import from ~/.ssh/config": one checkbox per entry with what it becomes; existing names start unchecked. */
public final class ImportPanel extends JPanel {
    final List<JCheckBox> checks = new ArrayList<>();
    final JButton importButton = new JButton("Import"), cancel = new JButton("Cancel");
    final JTextArea skipped = new JTextArea();

    public ImportPanel(List<ConfigImport.Candidate> candidates, List<String> skippedEntries, Consumer<List<RemoteHost>> importSelected, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        var rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        if (candidates.isEmpty()) rows.add(new JLabel("No importable Host entries found"));
        for (ConfigImport.Candidate candidate : candidates) {
            RemoteHost host = candidate.host();
            var notes = new ArrayList<String>(candidate.notes());
            if (candidate.exists()) notes.addFirst("exists");
            var check = new JCheckBox(host.name() + "  —  " + host.label() + (notes.isEmpty() ? "" : "  (" + String.join("; ", notes) + ")"), !candidate.exists());
            checks.add(check); rows.add(check);
        }
        add(new JScrollPane(rows), BorderLayout.CENTER);
        skipped.setEditable(false); skipped.setLineWrap(true);
        skipped.setText(skippedEntries.isEmpty() ? "" : "Not importable: " + String.join(", ", skippedEntries));
        var south = new JPanel(); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(skipped);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(importButton);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);
        importButton.setEnabled(!candidates.isEmpty());
        importButton.addActionListener(event -> {
            var chosen = new ArrayList<RemoteHost>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isSelected()) chosen.add(candidates.get(i).host());
            importSelected.accept(List.copyOf(chosen));
        });
        cancel.addActionListener(event -> onCancel.run());
    }
}
