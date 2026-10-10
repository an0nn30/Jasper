package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import javax.swing.text.JTextComponent;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ItemListShortcutsTest {
    private static ItemList list(ControlState state, SelectionMode mode, String details, String copy, String cancel) {
        var row = new ListItem("one", Optional.empty(), List.of(new Text("name", ControlState.READY, "Server", TextRole.BODY)), List.of(), List.of());
        return new ItemList("servers", state, ListKind.DOUBLE, List.of(row), mode == SelectionMode.NONE ? Set.of() : Set.of("one"),
            "", List.of(), mode, Optional.empty(), OptionalInt.empty(), "", "", details, copy, cancel);
    }
    @Test void focusedRowCanvasOwnsDetailsCopyAndEscapeWithoutSelectionChange() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(list(ControlState.READY, SelectionMode.SINGLE, "details", "copy", "cancel")), events::add)) {
                var control = (ItemLists.ListControl) view.control("servers");
                control.focusItem = "one";
                String copyStroke = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? "meta C" : "ctrl C";
                for (var entry : List.of(new String[]{"D", "details"}, new String[]{copyStroke, "copy"}, new String[]{"ESCAPE", "cancel"})) {
                    Object key = control.canvas.getInputMap().get(KeyStroke.getKeyStroke(entry[0]));
                    assertThat(key).isNotNull();
                    var action = control.canvas.getActionMap().get(key);
                    assertThat(action.isEnabled()).isTrue();
                    action.actionPerformed(new ActionEvent(control.canvas, 0, ""));
                }
                assertThat(events).containsExactly(new UiEvent.Action("details"), new UiEvent.Action("copy"), new UiEvent.Action("cancel"));
                assertThat(control.selected).containsExactly("one");
                events.clear();
                SwingUtilities.processKeyBindings(new KeyEvent(control.canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'd'));
                int modifier = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                SwingUtilities.processKeyBindings(new KeyEvent(control.canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifier, KeyEvent.VK_C, 'c'));
                SwingUtilities.processKeyBindings(new KeyEvent(control.canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
                assertThat(events).containsExactly(new UiEvent.Action("details"), new UiEvent.Action("copy"), new UiEvent.Action("cancel"));
                assertThat(control.selected).containsExactly("one");
            } finally { KitViewTest.release(context); }
        });
    }
    @Test void blankHiddenBusyOrPassiveRowsLeaveBindingsDisabled() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var spec : List.of(
                    list(ControlState.READY, SelectionMode.SINGLE, "", "", ""),
                    list(new ControlState(false, true, false, ""), SelectionMode.SINGLE, "details", "copy", "cancel"),
                    list(new ControlState(true, true, true, ""), SelectionMode.SINGLE, "details", "copy", "cancel"),
                    list(ControlState.READY, SelectionMode.NONE, "details", "copy", "cancel"))) {
                    try (var view = new KitView(context, new ViewSpec(spec), e -> {})) {
                        var control = (ItemLists.ListControl) view.control("servers");
                        control.focusItem = "one";
                        for (var stroke : List.of("D", "ESCAPE")) {
                            Object key = control.canvas.getInputMap().get(KeyStroke.getKeyStroke(stroke));
                            assertThat(control.canvas.getActionMap().get(key).isEnabled()).isFalse();
                        }
                    }
                }
            } finally { KitViewTest.release(context); }
        });
    }
    @Test void adjacentFieldRetainsTypingCopyAndEscapeWhileListBindingsStayOnCanvas() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            var field = new Field("query", ControlState.READY, FieldKind.TEXT, "draft", "", "Query", "");
            var root = new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL,
                List.of(field, list(ControlState.READY, SelectionMode.SINGLE, "details", "copy", "cancel")));
            try (var view = new KitView(context, new ViewSpec(root), events::add)) {
                var input = KitViewTest.editor(view, "query");
                int modifier = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
                SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_D, 'd'));
                SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifier, KeyEvent.VK_C, 'c'));
                SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
                assertThat(events).noneMatch(event -> event instanceof UiEvent.Action);
                input.setCaretPosition(input.getDocument().getLength());
                assertThat(SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_TYPED,
                    System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'd'))).isTrue();
                assertThat(input.getText()).isEqualTo("draftd");
                input.select(0, 5);
                var clipboard = new Clipboard("field-copy");
                input.getTransferHandler().exportToClipboard(input, clipboard, TransferHandler.COPY);
                try { assertThat(clipboard.getContents(null).getTransferData(DataFlavor.stringFlavor)).isEqualTo("draft"); }
                catch (Exception failure) { throw new AssertionError(failure); }
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void inactiveRowEscapeFallsThroughToEnclosingStack() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var spec : List.of(
                    list(ControlState.READY, SelectionMode.SINGLE, "", "", ""),
                    list(new ControlState(false, true, false, ""), SelectionMode.SINGLE, "details", "copy", "cancel"),
                    list(new ControlState(true, false, false, ""), SelectionMode.SINGLE, "details", "copy", "cancel"),
                    list(new ControlState(true, true, true, ""), SelectionMode.SINGLE, "details", "copy", "cancel"),
                    list(ControlState.READY, SelectionMode.NONE, "details", "copy", "cancel"))) {
                    var events = new ArrayList<UiEvent>();
                    var root = new Nodes.Stack("outer", ControlState.READY, LayoutRole.VERTICAL, List.of(spec), "outer.cancel");
                    try (var view = new KitView(context, new ViewSpec(root), events::add)) {
                        var control = (ItemLists.ListControl) view.control("servers");
                        control.focusItem = "one";
                        assertThat(SwingUtilities.processKeyBindings(new KeyEvent(control.canvas, KeyEvent.KEY_PRESSED,
                            System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).isTrue();
                        assertThat(events).containsExactly(new UiEvent.Action("outer.cancel"));
                    }
                }
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void resultListsRejectFocusedActions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ItemList("results", ControlState.READY, ListKind.RESULTS,
            List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "", "details", "", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
