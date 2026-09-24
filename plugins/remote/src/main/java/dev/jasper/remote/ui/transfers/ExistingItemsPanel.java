package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.ConflictDecision;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Asked once before copying when selected items already exist at the destination. */
public final class ExistingItemsPanel extends JPanel {
    final JButton replace = new JButton("Replace"), skip = new JButton("Skip existing"), cancel = new JButton("Cancel");

    public ExistingItemsPanel(String message, Consumer<ConflictDecision> choose, Runnable close) {
        super(new BorderLayout(8, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(TransferStrip.label(message), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (var button : List.of(replace, skip, cancel)) buttons.add(button);
        add(buttons, BorderLayout.SOUTH);
        replace.addActionListener(event -> choose.accept(ConflictDecision.REPLACE));
        skip.addActionListener(event -> choose.accept(ConflictDecision.SKIP));
        cancel.addActionListener(event -> close.run());
    }

    public static String message(List<String> existing, int selected, String destination) {
        if (selected == 1) return "\"" + existing.getFirst() + "\" already exists in " + destination + ".";
        return existing.size() + " of " + selected + " items already " + (existing.size() == 1 ? "exists" : "exist") + " in " + destination + ".";
    }
}
