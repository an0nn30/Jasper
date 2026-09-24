package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.ConflictDecision;
import dev.jasper.remote.transfer.TransferEntry;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** One transfer entry's decision: Replace for files, Merge for folders, Skip, Rename or Restart file. */
public final class ResolvePanel extends JPanel {
    public record Choice(ConflictDecision decision, boolean remaining) { }
    final JButton replace = new JButton("Replace"), merge = new JButton("Merge"), skip = new JButton("Skip"),
        rename = new JButton("Rename…"), restart = new JButton("Restart file"), cancel = new JButton("Cancel");
    final JCheckBox remaining = new JCheckBox("Apply to the rest of this transfer");

    public ResolvePanel(TransferEntry entry, Consumer<Choice> decide, Runnable restartFile, Runnable close) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        boolean folder = entry.sourceInfo().kind() == FileEntry.Kind.DIRECTORY;
        var text = new JPanel(new GridLayout(0, 1, 0, 4));
        text.add(TransferStrip.label(entry.relative()));
        text.add(TransferStrip.label(entry.error()));
        add(text, BorderLayout.NORTH);
        replace.setVisible(!folder);
        replace.setEnabled(entry.expectedTarget().filter(target -> target.kind() == entry.sourceInfo().kind()).isPresent());
        merge.setVisible(folder);
        merge.setEnabled(entry.expectedTarget().filter(target -> target.kind() == FileEntry.Kind.DIRECTORY).isPresent());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (var button : List.of(replace, merge, skip, rename, restart, cancel)) buttons.add(button);
        var south = new JPanel(new BorderLayout(0, 6));
        south.add(remaining, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        replace.addActionListener(event -> decide.accept(new Choice(ConflictDecision.REPLACE, remaining.isSelected())));
        merge.addActionListener(event -> decide.accept(new Choice(ConflictDecision.MERGE, remaining.isSelected())));
        skip.addActionListener(event -> decide.accept(new Choice(ConflictDecision.SKIP, remaining.isSelected())));
        rename.addActionListener(event -> decide.accept(new Choice(ConflictDecision.RENAME, false)));
        restart.addActionListener(event -> restartFile.run());
        cancel.addActionListener(event -> close.run());
    }
}
