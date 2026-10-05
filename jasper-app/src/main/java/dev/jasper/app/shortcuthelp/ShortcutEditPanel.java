package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * One row edit as a kit card. Set hands it to the page, which stages it until Apply. The card's value is a button:
 * clicking it, like Record, starts recording. Owned by ShortcutPanel; the panel is the layout-only shell the card
 * mounts in, hidden while nothing is being edited. EDT only.
 */
final class ShortcutEditPanel extends JPanel {
    private final boolean macOs;
    private final Runnable recordingStarted;
    private final Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer;
    private final KitView view;
    private List<ShortcutEntry> rows = List.of();
    private ShortcutEntry entry;
    private Optional<String> binding;
    private KeyStroke candidate;
    private List<ShortcutEntry> conflicts = List.of();
    private boolean recording, saving, closed, reassign;
    private String problem;

    ShortcutEditPanel(KitContext context, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> writer,
                      Runnable recordingStarted) {
        super(new BorderLayout());
        setOpaque(false);
        this.macOs = macOs; this.writer = writer; this.recordingStarted = recordingStarted;
        view = new KitView(context, spec(), this::event);
        add(view, BorderLayout.CENTER);
        setVisible(false);
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
        entry = row; binding = null; candidate = row.stroke(); recording = true; reassign = false; problem = null;
        setVisible(true); update();
    }

    boolean capture(KeyStroke stroke) {
        if (closed || !recording || saving || entry == null) return false;
        try {
            choose(Optional.of(KeyBindings.format(stroke, macOs)), stroke);
            recording = true; update();
        } catch (IllegalArgumentException invalid) {
            binding = null; candidate = null; problem = "This key combination cannot be saved. Try another shortcut."; update();
        }
        return true;
    }

    boolean recording() { return recording && !closed; }
    void stopRecording() { recording = false; if (entry != null) update(); }

    private void choose(Optional<String> binding, KeyStroke stroke) {
        this.binding = binding; candidate = stroke; recording = false; reassign = false; problem = null; update();
    }

    private void event(UiEvent event) {
        if (closed || entry == null) return;
        switch (event) {
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case "editor.value" -> { if (!saving) { recording = true; recordingStarted.run(); problem = null; update(); } }
                    case "editor.record" -> { recording = !recording; if (recording) recordingStarted.run(); problem = null; update(); }
                    case "editor.cancel" -> dismiss();
                    case "editor.remove" -> choose(Optional.of("none"), null);
                    case "editor.restore" -> choose(Optional.empty(), KeyBindings.parse(entry.defaultBinding(), macOs).orElse(null));
                    case "editor.set" -> set();
                    default -> { }
                }
            }
            case UiEvent.Toggled toggled when toggled.id().equals("editor.reassign") -> { reassign = toggled.checked(); update(); }
            default -> { }
        }
    }

    private boolean settable() { return !saving && binding != null && (conflicts.isEmpty() || reassign); }

    private void update() {
        if (entry == null) return;
        var nextConflicts = candidate == null ? List.<ShortcutEntry>of() : rows.stream()
            .filter(row -> row.editable() && !row.id().equals(entry.id()) && candidate.equals(row.stroke())).toList();
        if (!nextConflicts.equals(conflicts)) reassign = false;
        conflicts = nextConflicts;
        view.update(spec());
        revalidate(); repaint();
    }

    private String message() {
        if (problem != null) return problem;
        return saving ? "Setting shortcut…" : !conflicts.isEmpty()
            ? "Already used by: " + String.join(", ", conflicts.stream().map(ShortcutEntry::name).toList()) + ". Reassigning removes their shortcuts."
            : recording ? "Press the new shortcut, then click Set. Use Cancel to keep the current binding."
            : "Set stages this shortcut; Apply or OK in Settings saves it.";
    }

    private ViewSpec spec() {
        String title = entry == null ? "Set shortcut" : "Set shortcut for " + entry.name();
        String value = recording && binding == null ? "Press a key combination…" : ShortcutText.format(candidate);
        var idle = new ControlState(true, !saving, false, "");
        var capture = new Nodes.Stack("editor.capture", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button("editor.value", idle, value, "", Nodes.ButtonKind.SECONDARY, "editor.value", "Click to record a new shortcut"),
            new Nodes.Button("editor.record", idle, recording ? "Stop recording" : "Record shortcut", "", Nodes.ButtonKind.SECONDARY, "editor.record", "")));
        var buttons = new Nodes.Stack("editor.buttons", ControlState.READY, Nodes.LayoutRole.ACTIONS, List.of(
            new Nodes.Button("editor.set", new ControlState(true, settable(), false, ""), "Set", "", Nodes.ButtonKind.PRIMARY, "editor.set", ""),
            new Nodes.Button("editor.cancel", idle, "Cancel", "", Nodes.ButtonKind.SECONDARY, "editor.cancel", ""),
            new Nodes.Button("editor.remove", idle, "Remove shortcut", "", Nodes.ButtonKind.SECONDARY, "editor.remove", ""),
            new Nodes.Button("editor.restore", idle, "Restore default", "", Nodes.ButtonKind.SECONDARY, "editor.restore", "")));
        return new ViewSpec(new Nodes.Card("editor", ControlState.READY, Nodes.CardKind.FORM, List.of(
            new Nodes.Text("editor.title", ControlState.READY, title, Nodes.TextRole.BODY_STRONG),
            new Nodes.FormRow("editor.shortcut", ControlState.READY, "New keyboard shortcut", "", capture, Optional.empty()),
            new Nodes.FormRow("editor.note", ControlState.READY, "", "", new Nodes.Text("editor.message", ControlState.READY, message(), Nodes.TextRole.DESCRIPTION), Optional.empty()),
            new Nodes.SettingRow("editor.reassign.row", new ControlState(!conflicts.isEmpty(), !saving, false, ""), "Reassign from conflicting action", "",
                new Nodes.Toggle("editor.reassign", new ControlState(!conflicts.isEmpty(), !saving, false, ""), "Reassign from conflicting action", reassign, "editor.reassign", false), false),
            buttons)));
    }

    private void set() {
        if (!settable()) return;
        var changes = new LinkedHashMap<String, Optional<String>>();
        changes.put(entry.id(), binding);
        for (var conflict : conflicts) changes.put(conflict.id(), Optional.of("none"));
        saving = true; recording = false; problem = null; update();
        CompletableFuture<Void> result;
        try { result = writer.apply(Map.copyOf(changes)); }
        catch (RuntimeException failure) { result = CompletableFuture.failedFuture(failure); }
        result.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (closed) return;
            saving = false;
            if (failure == null) dismiss();
            else {
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                problem = "Could not set: " + cause.getMessage();
                update();
            }
        }));
    }

    private void dismiss() { entry = null; binding = null; recording = false; problem = null; setVisible(false); }

    void close() { closed = true; dismiss(); view.close(); }
}
