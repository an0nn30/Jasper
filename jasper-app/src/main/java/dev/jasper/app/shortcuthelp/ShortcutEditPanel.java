package dev.jasper.app.shortcuthelp;

import com.formdev.flatlaf.ui.FlatLineBorder;
import dev.jasper.app.config.KeyBindings;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** One row edit. Set hands it to the page, which stages it until Apply. Owned by ShortcutPanel. */
final class ShortcutEditPanel extends JPanel {
    final JButton set = new JButton("Set");
    final JButton cancel = new JButton("Cancel");
    final JButton remove = new JButton("Remove shortcut");
    final JButton restore = new JButton("Restore default");
    final JButton record = new JButton("Record shortcut");
    final JCheckBox reassign = new JCheckBox("Reassign from conflicting action");
    /**
     * The card's note. Container.validateTree lays out hidden children too, so while the card is hidden at 0x0 the note
     * is given a negative width; a wrapped text area keeps measuring at that width, a character per line, and the card
     * would ask to be ten times too tall on its first edit. A degenerate size is taken as none, which the text UI
     * measures as never laid out.
     */
    final JTextArea message = new JTextArea(2, 20) {
        @Override public void setBounds(int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) { width = 0; height = 0; }
            super.setBounds(x, y, width, height);
        }
    };
    private final JLabel title = new JLabel();
    private final JTextField value = new JTextField();
    private final boolean macOs;
    private final Runnable recordingStarted;
    private final Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer;
    private List<ShortcutEntry> rows = List.of();
    private ShortcutEntry entry;
    private Optional<String> binding;
    private KeyStroke candidate;
    private List<ShortcutEntry> conflicts = List.of();
    private boolean recording, saving, closed;

    ShortcutEditPanel(boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer, Runnable recordingStarted) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        reassign.setOpaque(false);
        this.macOs = macOs; this.writer = writer; this.recordingStarted = recordingStarted;
        add(title, BorderLayout.NORTH);
        var center = new JPanel(new BorderLayout(8, 6)); center.setOpaque(false);
        value.setEditable(false); value.getAccessibleContext().setAccessibleName("New keyboard shortcut");
        center.add(value, BorderLayout.CENTER); center.add(record, BorderLayout.EAST);
        message.setEditable(false); message.setFocusable(false); message.setOpaque(false);
        message.setLineWrap(true); message.setWrapStyleWord(true);
        center.add(message, BorderLayout.SOUTH); add(center, BorderLayout.CENTER);
        var bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false);
        bottom.add(reassign, BorderLayout.NORTH);
        var buttons = new JPanel(new WrapLayout(FlowLayout.LEADING, 6, 0)); buttons.setOpaque(false);
        buttons.add(set); buttons.add(cancel); buttons.add(remove); buttons.add(restore);
        bottom.add(buttons, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        record.addActionListener(e -> { recording = !recording; if (recording) recordingStarted.run(); update(); value.requestFocusInWindow(); });
        value.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (!saving) { recording = true; recordingStarted.run(); update(); } }
        });
        cancel.addActionListener(e -> dismiss());
        remove.addActionListener(e -> choose(Optional.of("none"), null));
        restore.addActionListener(e -> choose(Optional.empty(), KeyBindings.parse(entry.defaultBinding(), macOs).orElse(null)));
        reassign.addActionListener(e -> update());
        set.addActionListener(e -> set());
        setVisible(false);
    }

    @Override public void updateUI() {
        super.updateUI();
        var outline = UIManager.getColor("Component.borderColor");
        setBorder(new FlatLineBorder(new Insets(12, 14, 12, 14),
            outline != null ? outline : getForeground(), 1f, 16));
    }

    void setRows(List<ShortcutEntry> rows) {
        this.rows = rows;
        if (entry == null || saving) return;
        var current = rows.stream().filter(row -> row.id().equals(entry.id())).findFirst();
        if (current.isEmpty() || !current.get().equals(entry)) dismiss();
        else update();
    }

    void begin(ShortcutEntry row) {
        if (closed || saving || !row.editable()) return;
        entry = row; binding = null; candidate = row.stroke(); recording = true;
        reassign.setSelected(false);
        title.setText("Set shortcut for " + row.name());
        setVisible(true); update(); value.requestFocusInWindow();
    }

    boolean capture(KeyStroke stroke) {
        if (closed || !recording || saving || entry == null) return false;
        try {
            choose(Optional.of(KeyBindings.format(stroke, macOs)), stroke);
            recording = true; update();
        } catch (IllegalArgumentException invalid) {
            binding = null; candidate = null; update();
            message.setText("This key combination cannot be saved. Try another shortcut.");
        }
        return true;
    }

    boolean recording() { return recording && !closed; }
    void stopRecording() { recording = false; if (entry != null) update(); }

    private void choose(Optional<String> binding, KeyStroke stroke) {
        this.binding = binding; candidate = stroke; recording = false; reassign.setSelected(false); update();
    }

    private void update() {
        if (entry == null) return;
        var nextConflicts = candidate == null ? List.<ShortcutEntry>of() : rows.stream()
            .filter(row -> row.editable() && !row.id().equals(entry.id()) && candidate.equals(row.stroke())).toList();
        if (!nextConflicts.equals(conflicts)) reassign.setSelected(false);
        conflicts = nextConflicts;
        value.setText(recording && binding == null ? "Press a key combination…" : ShortcutText.format(candidate));
        record.setText(recording ? "Stop recording" : "Record shortcut");
        reassign.setVisible(!conflicts.isEmpty());
        reassign.setEnabled(!saving);
        set.setEnabled(!saving && binding != null && (conflicts.isEmpty() || reassign.isSelected()));
        cancel.setEnabled(!saving); remove.setEnabled(!saving); restore.setEnabled(!saving); record.setEnabled(!saving);
        message.setText(saving ? "Setting shortcut\u2026" : !conflicts.isEmpty()
            ? "Already used by: " + String.join(", ", conflicts.stream().map(ShortcutEntry::name).toList()) + ". Reassigning removes their shortcuts."
            : recording ? "Press the new shortcut, then click Set. Use Cancel to keep the current binding."
            : "Set stages this shortcut; Apply or OK in Settings saves it.");
        revalidate(); repaint();
    }

    private void set() {
        if (!set.isEnabled()) return;
        var changes = new LinkedHashMap<String, Optional<String>>();
        changes.put(entry.id(), binding);
        for (var conflict : conflicts) changes.put(conflict.id(), Optional.of("none"));
        saving = true; recording = false; update();
        CompletableFuture<Void> result;
        try { result = writer.apply(Map.copyOf(changes)); }
        catch (RuntimeException failure) { result = CompletableFuture.failedFuture(failure); }
        result.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            saving = false;
            if (failure == null) dismiss();
            else {
                update();
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                message.setText("Could not set: " + cause.getMessage());
            }
        }));
    }

    private void dismiss() { entry = null; binding = null; recording = false; setVisible(false); }
    void close() { closed = true; dismiss(); }
}
