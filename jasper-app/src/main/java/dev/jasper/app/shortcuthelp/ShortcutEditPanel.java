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
    private dev.jasper.ui.swing.TreeDetailHandle detail;
    private KitView host;
    private boolean literal;
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
        setVisible(false);
    }

    void setRows(List<ShortcutEntry> rows) {
        this.rows = rows;
        if (entry == null || saving) return;
        var current = rows.stream().filter(row -> row.id().equals(entry.id())).findFirst();
        if (current.isEmpty() || !current.get().equals(entry)) dismiss();
        else update();
    }

    void attach(KitView host, String itemId) {
        if(entry==null || detail!=null)return;
        this.host=host;
        detail=host.attachTreeDetail(ShortcutTree.ID,itemId,spec(),this::event,this::dismiss);
        update();detail.focus("editor.value");
    }
    boolean visibleEdit(){return entry!=null && !closed;}
    boolean special(KeyStroke stroke) {
        int mask=java.awt.event.InputEvent.CTRL_DOWN_MASK|java.awt.event.InputEvent.ALT_DOWN_MASK|java.awt.event.InputEvent.SHIFT_DOWN_MASK|java.awt.event.InputEvent.META_DOWN_MASK|java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK;
        return visibleEdit() && !literal && (stroke.getModifiers()&mask)==0 && (stroke.getKeyCode()==java.awt.event.KeyEvent.VK_ENTER || stroke.getKeyCode()==java.awt.event.KeyEvent.VK_ESCAPE);
    }
    /** Ordinary capture leaves traversal and real auxiliary-control activation to Swing. */
    boolean auxiliaryKey(java.awt.Component source, KeyStroke stroke) {
        if (!visibleEdit() || literal) return false;
        int modifiers=stroke.getModifiers() & (java.awt.event.InputEvent.CTRL_DOWN_MASK
                |java.awt.event.InputEvent.ALT_DOWN_MASK|java.awt.event.InputEvent.META_DOWN_MASK
                |java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK|java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        if (stroke.getKeyCode()==java.awt.event.KeyEvent.VK_TAB
                && (modifiers & ~java.awt.event.InputEvent.SHIFT_DOWN_MASK)==0) return true;
        // These are the actual owned descriptor controls; the receiving value still records Space.
        return modifiers==0 && stroke.getKeyCode()==java.awt.event.KeyEvent.VK_SPACE
                && source instanceof javax.swing.AbstractButton && host!=null
                && SwingUtilities.isDescendingFrom(source,host) && source.getName()!=null
                && java.util.Set.of("editor.cancel","editor.set","editor.record","editor.options.main",
                        "editor.options.arrow","editor.reassign").contains(source.getName());
    }
    boolean gesture(KeyStroke stroke) {
        if(!special(stroke))return false;
        if(stroke.getKeyCode()==java.awt.event.KeyEvent.VK_ENTER)set();
        else if(!saving)dismiss();
        return true;
    }
    void begin(ShortcutEntry row) {
        if (closed || saving || !row.editable()) return;
        dismiss();
        entry = row; binding = null; candidate = row.stroke(); recording = true; literal = false; reassign = false; problem = null;
        setVisible(true); update();
    }

    boolean capture(KeyStroke stroke) {
        if (closed || !recording || saving || entry == null) return false;
        try {
            literal = false;
            choose(Optional.of(KeyBindings.format(stroke, macOs)), stroke);
            recording = true; update();
        } catch (IllegalArgumentException invalid) {
            binding = null; candidate = null; problem = "This key combination cannot be saved. Try another shortcut."; update();
        }
        return true;
    }

    boolean recording() { return recording && !closed; }
    void stopRecording() { recording = false; literal = false; if (entry != null) update(); }

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
                    case "editor.literal" -> { if(!saving) { literal=true; recording=true; recordingStarted.run(); problem=null; update(); } }
                    case "editor.cancel" -> { if(!saving)dismiss(); }
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
        if(detail!=null && detail.isAttached()) {
            detail.update(spec());
            host.setButtonValuePresentation("editor.value", recording ? dev.jasper.ui.swing.ButtonValuePresentation.RECORDER_ARMED : dev.jasper.ui.swing.ButtonValuePresentation.RECORDER);
            host.setButtonKeycaps("editor.value",recording && binding==null?Optional.empty():ShortcutText.keycaps(candidate,macOs));
        }
        revalidate(); repaint();
    }

    private String message() {
        if (problem != null) return problem;
        return saving ? "Setting shortcut…" : literal ? "Press any key, including Return or Escape. The next key is recorded literally." : !conflicts.isEmpty()
            ? "Already used by: " + String.join(", ", conflicts.stream().map(ShortcutEntry::name).toList()) + ". Reassigning removes their shortcuts."
            : recording ? "Press the new shortcut · Return to set · Esc to cancel"
            : "Set stages this shortcut; Apply or OK in Settings saves it.";
    }

    private ViewSpec spec() {
        String value = recording && binding == null ? "Press a key combination…" : ShortcutText.format(candidate);
        var idle = new ControlState(true, !saving, false, "");
        var options=List.of(
            new dev.jasper.ui.model.MenuItem("literal","Record any key…","","editor.literal",!saving,false),
            new dev.jasper.ui.model.MenuItem("remove","Remove shortcut","","editor.remove",!saving,false),
            new dev.jasper.ui.model.MenuItem("restore","Restore default","","editor.restore",!saving,false));
        var candidateLane = new Nodes.Stack("editor.candidate",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Button("editor.value", idle, value, "", Nodes.ButtonKind.SECONDARY, "editor.value", "Click to record a new shortcut")));
        var capture = new Nodes.Stack("editor.capture", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(candidateLane,
            new Nodes.Button("editor.cancel", idle, "Cancel", "", Nodes.ButtonKind.SECONDARY, "editor.cancel", ""),
            new Nodes.Button("editor.set", new ControlState(true, settable(), false, ""), "Set", "", Nodes.ButtonKind.PRIMARY, "editor.set", "")));
        var note = new Nodes.Stack("editor.note", ControlState.READY, Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Text("editor.message", ControlState.READY, message(), Nodes.TextRole.CAPTION),
            new Nodes.Button("editor.record", idle, recording ? "Stop" : "Record", "", Nodes.ButtonKind.GHOST, "editor.record", "Start or stop recording"),
            new Nodes.MenuButton("editor.options",idle,"","more",Nodes.ButtonKind.GHOST,"",options,"Shortcut actions")));
        return new ViewSpec(new Nodes.Stack("editor", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(capture,note,
            new Nodes.SettingRow("editor.reassign.row", new ControlState(!conflicts.isEmpty(), !saving, false, ""), "Reassign from conflicting action", "",
                new Nodes.Toggle("editor.reassign", new ControlState(!conflicts.isEmpty(), !saving, false, ""), "Reassign from conflicting action", reassign, "editor.reassign", false), false))));
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

    void dismiss() {
        var old=detail; detail=null; host=null;
        entry=null; binding=null; recording=false; literal=false; problem=null; setVisible(false);
        if(old!=null)old.close();
    }

    void close() { closed = true; dismiss(); }
}
