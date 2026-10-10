package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Plan 2 Task 9-kit: what the Editor's chrome needs from the kit. A DOCUMENT split whose specialized document fills the
 * height, field Shift+Enter and Escape, an Escape covering an inline bar, a field driving a list with its arrow keys,
 * pressed option buttons, a command line's caret and severity text, and a view re-routed to the workspace slot over an
 * opaque surface. The last tests prove earlier kit consumers keep their behaviour.
 */
@SuppressWarnings("try")
class EditorKitTest {
    static final ControlState READY = ControlState.READY;
    static final ControlState HIDDEN = new ControlState(false, true, false, "");

    static KeyEvent key(Component on, int code, int modifiers) {
        return new KeyEvent(on, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    static Field findField(String alternate, String cancel) {
        return new Field("find", READY, FieldKind.SEARCH, "needle", "Find", "Find", "next", Optional.empty(), FieldPresentation.EMBEDDED, alternate, cancel, "");
    }

    static Nodes.Button option(String id, String label, Optional<Boolean> selected) {
        return new Nodes.Button(id, READY, label, "", ButtonKind.GHOST, id, label, ButtonPresentation.STANDARD, Optional.empty(), selected);
    }

    static ViewSpec bar(Field field, String stackCancel) {
        return new ViewSpec(new Stack("bar", READY, LayoutRole.INLINE, List.of(field, option("next-button", "Next", Optional.empty())), stackCancel));
    }

    @Test void passiveListsAndTablesKeepReadableScrollableRowsWithoutSelectionOrActivation() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var items = new ArrayList<ListItem>(); var rows = new ArrayList<TableRow>();
            for (int i = 0; i < 30; i++) {
                items.add(new ListItem("row" + i, Optional.empty(), List.of(new Text("text" + i, READY, "Output " + i,
                    TextRole.MONO, Optional.of(Severity.DANGER), OptionalInt.empty())), List.of(),
                    List.of(new Nodes.Button("action" + i, READY, "Copy", "", ButtonKind.GHOST, "copy", ""))));
                rows.add(new TableRow("row" + i, Map.of("name", "Output " + i), Optional.empty()));
            }
            var list = new ItemList("output", READY, ListKind.COMPACT, items, Set.of(), "activate", List.of(), SelectionMode.NONE,
                Optional.empty(), OptionalInt.of(12));
            var table = new Table("output", READY, List.of(new Column("name", "Name", TextRole.MONO, false)), rows,
                Set.of(), "activate", List.of(), SelectionMode.NONE);
            for (Nodes.Node node : List.of(list, table)) {
                try (var view = new KitView(context, new ViewSpec(node), events::add)) {
                    view.setSize(500, 250); InteractiveRowsTest.layout(view); events.clear();
                    var control = (InteractiveRows.RowsControl) view.control("output");
                    assertThat(control.canvas.isFocusable()).isFalse();
                    var row = control.mounted.get("row0");
                    var click = new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1);
                    row.dispatchEvent(click);
                    row.dispatchEvent(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 2, false, java.awt.event.MouseEvent.BUTTON1));
                    SwingUtilities.processKeyBindings(key(control.canvas, KeyEvent.VK_DOWN, 0));
                    SwingUtilities.processKeyBindings(key(control.canvas, KeyEvent.VK_SPACE, 0));
                    SwingUtilities.processKeyBindings(key(control.canvas, KeyEvent.VK_ENTER, 0));
                    assertThat(control.selected).isEmpty(); assertThat(control.focusItem).isNull(); assertThat(events).isEmpty();
                    assertThat(control.canvas.isEnabled()).isTrue();
                    if (node instanceof ItemList) {
                        assertThat(control.canvas.getPreferredScrollableViewportSize().height).isEqualTo(12 * control.rowHeight);
                        var text = find(view, "text0", JLabel.class);
                        assertThat(text.isEnabled()).as("passive does not dim error text").isTrue();
                        assertThat(text.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.STATUS_DANGER));
                        var pixels=paint(text,140,28); int opaqueInk=0;
                        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)
                            if((pixels.getRGB(x,y)>>>24)>200)opaqueInk++;
                        assertThat(opaqueInk).as("passive error glyphs paint at full opacity").isGreaterThan(10);
                        find(view, "action0", AbstractButton.class).doClick();
                        assertThat(events).containsExactly(new UiEvent.Action("copy"));
                        view.reveal("text29");
                    } else control.ensure(29);
                    assertThat(control.scroll.getViewport().getViewPosition().y).isGreaterThan(0);
                }
            }
            release(context);
        });
    }

    @Test void changingExistingRowsToPassiveClearsTheirFocusAndReturningToSingleRestoresInput() {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            var items=List.of(language("a","Alpha"),language("b","Beta"));
            var active=new ItemList("rows",READY,ListKind.COMPACT,items,Set.of("a"),"activate",List.of());
            var passive=new ItemList("rows",READY,ListKind.COMPACT,items,Set.of(),"activate",List.of(),SelectionMode.NONE,Optional.empty());
            try(var view=new KitView(context,new ViewSpec(active),events::add)) {
                view.setSize(300,180);InteractiveRowsTest.layout(view);
                var control=(InteractiveRows.RowsControl)view.control("rows");
                SwingUtilities.processKeyBindings(key(control.canvas,KeyEvent.VK_DOWN,0));
                assertThat(control.selected).containsExactly("b");
                view.update(new ViewSpec(passive)); events.clear();
                assertThat(control.canvas.isFocusable()).isFalse(); assertThat(control.focusItem).isNull();assertThat(control.selected).isEmpty();
                SwingUtilities.processKeyBindings(key(control.canvas,KeyEvent.VK_ENTER,0));assertThat(events).isEmpty();
                view.update(new ViewSpec(active));events.clear();
                assertThat(control.canvas.isFocusable()).isTrue();
                SwingUtilities.processKeyBindings(key(control.canvas,KeyEvent.VK_DOWN,0));
                assertThat(events).containsExactly(new UiEvent.Selected("rows",Set.of("b")));
            }finally {release(context);}
        });
    }

    @Test void passiveDataRowsLayOutEverySlotAndDeliverPointerInputWhileHeadingsKeepTheirRule() {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            var item=new ListItem("data",Optional.of(new Text("leading",READY,"L",TextRole.BODY)),
                List.of(new Text("title",READY,"Title",TextRole.BODY)),
                List.of(new Text("subtitle",READY,"Subtitle",TextRole.CAPTION)),
                List.of(new Nodes.Button("copy",READY,"Copy","",ButtonKind.GHOST,"copy","")));
            var heading=new ListItem("heading",Optional.empty(),List.of(new Text("section",READY,"Section",TextRole.BODY)),List.of(),List.of(),true);
            var list=new ItemList("rows",READY,ListKind.DOUBLE,List.of(item,heading),Set.of(),"activate",List.of(),SelectionMode.NONE,Optional.empty());
            try(var view=new KitView(context,new ViewSpec(list),events::add)) {
                view.setSize(500,200);InteractiveRowsTest.layout(view);
                var control=(InteractiveRows.RowsControl)view.control("rows");var row=control.mounted.get("data");
                for(var id:List.of("leading","title","subtitle","copy")) {
                    var component=find(view,id,JComponent.class);
                    var bounds=SwingUtilities.convertRectangle(component.getParent(),component.getBounds(),row);
                    assertThat(bounds.width).as("%s visible width",id).isPositive();
                    assertThat(bounds.height).as("%s visible height",id).isPositive();
                    assertThat(row.getBounds().width).isGreaterThanOrEqualTo(bounds.x+bounds.width);
                    assertThat(component.getParent().getWidth()).as("%s parent width",id).isPositive();
                    assertThat(component.getParent().getHeight()).as("%s parent height",id).isPositive();
                }
                var button=find(view,"copy",AbstractButton.class);
                var point=SwingUtilities.convertPoint(button,button.getWidth()/2,button.getHeight()/2,row);
                var target=SwingUtilities.getDeepestComponentAt(row,point.x,point.y);
                assertThat(target).as("laid-out pointer hit target").isSameAs(button);
                pointerClick(target,SwingUtilities.convertPoint(row,point,target));
                assertThat(events).containsExactly(new UiEvent.Action("copy"));
                assertThat(control.selected).isEmpty();
                // NONE must not paint data rows as section headings, nor paint selection/hover/focus effects.
                row.hover=true;control.selected.add("data");control.focusItem="data";
                assertThat(rowBackground(row).getRGB(0,0)).isZero();
                assertThat(rowBackground(row).getRGB(5,5)).isZero();
                var section=control.mounted.get("heading");
                assertThat(section.title.getWidth()).isPositive();
                for(var part:List.of(section.leading,section.subtitle,section.trailing))assertThat(part.getBounds()).isEqualTo(new Rectangle());
                assertThat(rowBackground(section).getRGB(0,0)).isEqualTo(context.theme().semantic().color(SemanticColor.BORDER_SUBTLE).getRGB());
                assertThat(rowBackground(section).getRGB(5,5)).isZero();
                pointerClick(section,new Point(5,5));assertThat(events).containsExactly(new UiEvent.Action("copy"));
            }finally{release(context);}
        });
    }

    @Test void passiveTablesKeepColumnWidthsAndOrdinaryRowInsets() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            var table=new Table("rows",READY,List.of(new Column("name","Name",TextRole.BODY,false,1,Optional.empty()),
                new Column("size","Size",TextRole.MONO,true,2,Optional.empty())),
                List.of(new TableRow("a",Map.of("name","Alpha","size","123"),Optional.empty()),
                    new TableRow("b",Map.of("name","Beta","size","456"),Optional.empty())),Set.of(),"",List.of(),SelectionMode.NONE);
            try(var view=new KitView(context,new ViewSpec(table),event -> {})) {
                view.setSize(500,180);InteractiveRowsTest.layout(view);
                var control=(Tables.TableControl)view.control("rows");var row=control.mounted.get("a");
                int pad=ThemePainting.metric(context,DesignMetrics.Role.ICON_GAP);
                assertThat(row.title.getBounds()).isEqualTo(new Rectangle(pad,0,row.getWidth()-2*pad,row.getHeight()));
                assertThat(row.cells.get(0).getWidth()).isPositive();
                assertThat(row.cells.get(1).getWidth()).isBetween(2*row.cells.get(0).getWidth()-1,2*row.cells.get(0).getWidth()+1);
                assertThat(row.cells.get(1).getX()).isEqualTo(row.cells.get(0).getWidth());
                assertThat(row.cells.get(0).getHeight()).isEqualTo(row.getHeight());
                assertThat(((JLabel)row.cells.get(1)).getHorizontalAlignment()).isEqualTo(SwingConstants.RIGHT);
                assertThat(rowBackground(control.mounted.get("b")).getRGB(0,0)).as("ordinary table row has no heading rule").isZero();
            }finally{release(context);}
        });
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void passiveTransitionClearsUserSelectionEvenWhenDeclaredSelectionStaysEmpty(boolean table) {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            try(var view=new KitView(context,new ViewSpec(transitionRows(table,SelectionMode.SINGLE,Set.of())),events::add)) {
                view.setSize(300,180);InteractiveRowsTest.layout(view);
                var control=(InteractiveRows.RowsControl)view.control("rows");
                pointerClick(control.mounted.get("b"),new Point(5,5));
                assertThat(control.selected).containsExactly("b");assertThat(control.focusItem).isEqualTo("b");assertThat(control.anchor).isEqualTo("b");
                assertThat(events).contains(new UiEvent.Selected("rows",Set.of("b")));
                view.update(new ViewSpec(transitionRows(table,SelectionMode.NONE,Set.of())));events.clear();
                assertThat(control.selected).as("NONE internal selection").isEmpty();
                assertThat(control.focusItem).isNull();assertThat(control.focusControl).isNull();assertThat(control.anchor).isNull();assertThat(control.pendingReveal).isNull();
                view.update(new ViewSpec(transitionRows(table,SelectionMode.SINGLE,Set.of())));
                assertThat(control.selected).isEmpty();assertThat(control.focusItem).isNull();assertThat(control.anchor).isNull();assertThat(control.pendingReveal).isNull();
                SwingUtilities.processKeyBindings(key(control.canvas,KeyEvent.VK_DOWN,0));
                assertThat(events).containsExactly(new UiEvent.Selected("rows",Set.of("a")));
            }finally{release(context);}
        });
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void enteringPassiveCancelsADeferredSelectionRevealBeforeFirstLayout(boolean table) {
        Edt.run(() -> {
            var context=KitFixtures.context();
            try(var view=new KitView(context,new ViewSpec(transitionRows(table,SelectionMode.SINGLE,Set.of("b"))),event -> {})) {
                var control=(InteractiveRows.RowsControl)view.control("rows");
                assertThat(control.pendingReveal).isEqualTo("b");
                view.update(new ViewSpec(transitionRows(table,SelectionMode.NONE,Set.of())));
                assertThat(control.pendingReveal).as("stale selection reveal").isNull();
                view.update(new ViewSpec(transitionRows(table,SelectionMode.SINGLE,Set.of())));
                view.setSize(300,180);InteractiveRowsTest.layout(view);
                assertThat(control.selected).isEmpty();assertThat(control.focusItem).isNull();assertThat(control.anchor).isNull();
                assertThat(control.scroll.getViewport().getViewPosition().y).isZero();
            }finally{release(context);}
        });
    }

    static Nodes.Node transitionRows(boolean table,SelectionMode mode,Set<String> selected) {
        return table ? new Table("rows",READY,List.of(new Column("name","Name",TextRole.BODY,false)),
            List.of(new TableRow("a",Map.of("name","Alpha"),Optional.empty()),new TableRow("b",Map.of("name","Beta"),Optional.empty())),selected,"activate",List.of(),mode)
            : new ItemList("rows",READY,ListKind.COMPACT,List.of(language("a","Alpha"),language("b","Beta")),selected,"activate",List.of(),mode,Optional.empty());
    }

    static void pointerClick(Component target,Point at) {
        for(int type:new int[]{java.awt.event.MouseEvent.MOUSE_PRESSED,java.awt.event.MouseEvent.MOUSE_RELEASED,java.awt.event.MouseEvent.MOUSE_CLICKED})
            target.dispatchEvent(new java.awt.event.MouseEvent(target,type,0,type==java.awt.event.MouseEvent.MOUSE_PRESSED?InputEvent.BUTTON1_DOWN_MASK:0,at.x,at.y,1,false,java.awt.event.MouseEvent.BUTTON1));
    }

    static BufferedImage rowBackground(InteractiveRows.Row row) {
        var image=new BufferedImage(row.getWidth(),row.getHeight(),BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try{row.paintComponent(g);}finally{g.dispose();}return image;
    }

    @Test void revealingAnInlineRowBeforeLayoutWaitsForTheViewportWithoutChangingFocus() {
        Edt.run(() -> {
            var context=KitFixtures.context(); var items=new ArrayList<ListItem>();
            for(int i=0;i<30;i++)items.add(language("row"+i,"Line "+i));
            var list=new ItemList("output",READY,ListKind.COMPACT,items,Set.of(),"",List.of(),SelectionMode.NONE,Optional.empty(),OptionalInt.of(12));
            try(var view=new KitView(context,new ViewSpec(list),event -> {})) {
                view.reveal("row29.name");
                view.setSize(400,250); InteractiveRowsTest.layout(view);
                var control=(InteractiveRows.RowsControl)view.control("output");
                assertThat(control.scroll.getViewport().getViewPosition().y).isGreaterThan(0);
                assertThat(control.focusItem).isNull(); assertThat(control.focusControl).isNull();
                var label=find(view,"row29.name",JLabel.class);
                var bounds=SwingUtilities.convertRectangle(label.getParent(),label.getBounds(),control.canvas);
                assertThat(control.scroll.getViewport().getViewRect().intersects(bounds)).isTrue();
            }finally {release(context);}
        });
    }

    @Test void editorCanvasAdmissionRejectsOrdinaryRootsAndDisposesTheAcceptedWrapper() {
        Edt.run(() -> {
            var context = KitFixtures.context(); int[] disposed = {0};
            var spec = new ViewSpec(new Nodes.Custom("node", READY, "content", CustomKind.EDITOR, "Editor"));
            try (var view = new KitView(context, spec, event -> {})) {
                for (var ordinary : List.of(new JPanel(), new JButton(), new JLabel(), new JList<>(),
                        new JTable(), new JTree(), new JComboBox<>(), new JSpinner(), new JSlider(), new JScrollPane(new JPanel())))
                    assertThatThrownBy(() -> view.registerContent("content", ordinary, () -> {})).isInstanceOf(IllegalArgumentException.class);
                var document = new JComponent() {};
                document.setLayout(new BorderLayout()); document.add(new JScrollPane(new JTextArea()));
                view.registerContent("content", document, () -> disposed[0]++);
            } finally { release(context); }
            assertThat(disposed[0]).isEqualTo(1);
        });
    }

    @Test void explicitSelectAllUsesLiveFieldTextWithoutEditingOrChangingFocus() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, bar(findField("", ""), ""), events::add)) {
                var input = editor(view, "find"); input.setText("live query"); events.clear();
                var focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                view.selectAll("find");
                assertThat(input.getSelectedText()).isEqualTo("live query");
                assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isSameAs(focused);
                assertThat(events).isEmpty();
                assertThatThrownBy(() -> view.selectAll("next-button")).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> view.selectAll("missing")).isInstanceOf(IllegalArgumentException.class);
            } finally { release(context); }
        });
    }

    @Test void shiftEnterInAFieldRunsItsAlternateActionAndPassesOnWithoutOne() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, bar(findField("previous", ""), ""), events::add)) {
                var input = editor(view, "find");
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("previous"), new UiEvent.Action("next"));
                events.clear();
                view.update(bar(findField("", ""), ""));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).as("blank passes Shift+Enter on").isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void escapeRunsTheFieldsCancelFirstThenTheEnclosingStacksFromAnyControlInside() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, bar(findField("", "field-close"), "bar-close"), events::add)) {
                var input = editor(view, "find");
                var next = find(view, "next-button", Buttons.KitButton.class);
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).as("a button inside the bar").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("field-close"), new UiEvent.Action("bar-close"));
                events.clear();
                view.update(bar(findField("", ""), "bar-close"));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).as("a field without one passes to the bar").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("bar-close"));
                events.clear();
                view.update(bar(findField("", ""), ""));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).as("nothing takes Escape").isFalse();
                assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    /** A form around the bar: Escape the bar does not take reaches the form's Cancel. */
    static ViewSpec barInForm(ControlState barState) {
        var bar = new Stack("bar", barState, LayoutRole.INLINE, List.of(findField("", ""), option("next-button", "Next", Optional.empty())), "bar-close");
        return new ViewSpec(new Form("form", READY, FormKind.SHEET, "Find", Optional.empty(), List.of(bar), Optional.empty(), List.of(), "form-cancel"));
    }

    @Test void aDisabledBusyOrHiddenStackLeavesEscapeToTheEnclosingFormsCancel() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, barInForm(READY), events::add)) {
                var next = find(view, "next-button", Buttons.KitButton.class);
                assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).isTrue();
                assertThat(events).as("an interactive bar takes Escape").containsExactly(new UiEvent.Action("bar-close"));
                for (var state : List.of(new ControlState(true, false, false, ""), new ControlState(true, true, true, ""), HIDDEN)) {
                    events.clear();
                    view.update(barInForm(state));
                    assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).as("%s", state).isTrue();
                    assertThat(events).as("%s passes Escape to the form", state).containsExactly(new UiEvent.Action("form-cancel"));
                }
            } finally { release(context); }
        });
    }

    static ListItem language(String id, String name) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".name", READY, name, TextRole.BODY)), List.of(), List.of());
    }

    static ViewSpec picker(Set<String> selected, String listId) {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < 40; i++) items.add(language("l" + i, "Language " + i));
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", listId);
        return new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(filter,
            new ItemList("languages", READY, ListKind.SINGLE, items, selected, "choose", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(10)))));
    }

    @Test void arrowKeysInAFieldMoveItsListsSelectionAndLeaveTheFocusInTheField() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, picker(Set.of("l0"), "languages"), events::add)) {
                view.setSize(400, 600); InteractiveRowsTest.layout(view);
                var input = editor(view, "filter");
                var list = (ItemLists.ListControl) view.control("languages");
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_UP, 0))).isTrue();
                assertThat(events).containsExactly(new UiEvent.Selected("languages", Set.of("l1")), new UiEvent.Selected("languages", Set.of("l2")),
                    new UiEvent.Selected("languages", Set.of("l1")));
                events.clear();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_PAGE_DOWN, 0))).isTrue();
                int page = list.page();
                assertThat(page).isGreaterThan(1);
                assertThat(events).containsExactly(new UiEvent.Selected("languages", Set.of("l" + (1 + page))));
                events.clear();
                for (int i = 0; i < 10; i++) SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_PAGE_UP, 0));
                assertThat(events.getLast()).as("clamped at the first row").isEqualTo(new UiEvent.Selected("languages", Set.of("l0")));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(events.getLast()).as("Enter stays the field's own action").isEqualTo(new UiEvent.Action("choose"));
            } finally { release(context); }
        });
    }

    static ViewSpec filtered(int count, String listId) {
        return filtered(count, listId, ListKind.COMPACT, OptionalInt.of(14));
    }

    static ViewSpec filtered(int count, String listId, ListKind kind, OptionalInt visibleRows) {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < count; i++) items.add(language("l" + i, "Language " + i));
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", listId);
        return new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(filter,
            new ItemList("languages", READY, kind, items, count == 0 ? Set.of() : Set.of("l0"), "choose", List.of(), SelectionMode.SINGLE,
                Optional.empty(), visibleRows))));
    }

    /** Plan 2 Task 9a ruling (a): a list a field names as its listId keeps its visible-rows height while its filter narrows it. */
    @Test void aListDrivenByAFieldKeepsItsVisibleRowsHeightForAnyRowCountAndOtherListsSizeToTheirRows() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, filtered(40, "languages"), e -> { })) {
                var list = (ItemLists.ListControl) view.control("languages");
                int full = view.getPreferredSize().height, listFull = list.component.getPreferredSize().height;
                assertThat(list.canvas.getPreferredScrollableViewportSize().height).as("fourteen rows").isEqualTo(14 * list.rowHeight);
                for (int count : new int[] {1, 0, 13, 14, 40}) {
                    view.update(filtered(count, "languages"));
                    assertThat(view.getPreferredSize().height).as("%d rows with a driving field", count).isEqualTo(full);
                    assertThat(list.component.getPreferredSize().height).isEqualTo(listFull);
                }
                view.update(filtered(1, ""));
                assertThat(list.component.getPreferredSize().height).as("without a driving field the list sizes to its row").isLessThan(listFull);
                int one = view.getPreferredSize().height;
                view.update(filtered(0, ""));
                assertThat(view.getPreferredSize().height).as("and shrinks again when empty").isLessThan(one);
                view.update(filtered(40, ""));
                assertThat(list.component.getPreferredSize().height).as("a full list is the same either way").isEqualTo(listFull);
                view.update(filtered(1, "languages"));
                assertThat(list.component.getPreferredSize().height).as("naming the list again restores its height").isEqualTo(listFull);
            } finally { release(context); }
        });
    }

    @Test void everyListKindReservesItsRequestedHeightEvenWhenItStartsEmpty() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (var kind : ListKind.values()) {
                    try (var view = new KitView(context, filtered(0, "languages", kind, OptionalInt.of(14)), e -> { })) {
                        var list = (ItemLists.ListControl) view.control("languages");
                        int full = list.component.getPreferredSize().height;
                        int expectedRowHeight = kind == ListKind.RESULTS
                            ? context.metrics().value(DesignMetrics.Role.RESULTS_ROW_HEIGHT) : list.rowHeight;
                        assertThat(list.canvas.getPreferredScrollableViewportSize().height)
                            .as("%s starts with fourteen empty rows", kind).isEqualTo(14 * expectedRowHeight);
                        for (int count : new int[] {1, 14, 40, 0}) {
                            view.update(filtered(count, "languages", kind, OptionalInt.of(14)));
                            assertThat(list.component.getPreferredSize().height).as("%s with %d rows", kind, count).isEqualTo(full);
                        }
                    }
                }
            } finally { release(context); }
        });
    }

    @Test void aFieldDrivenListWithoutVisibleRowsStillSizesToItsRows() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, filtered(1, "languages", ListKind.COMPACT, OptionalInt.empty()), e -> { })) {
                int one = view.getPreferredSize().height;
                view.update(filtered(0, "languages", ListKind.COMPACT, OptionalInt.empty()));
                assertThat(view.getPreferredSize().height).isLessThan(one);
            } finally { release(context); }
        });
    }

    @Test void anOptionButtonShowsItsPressedStateReportsItToAssistiveTechnologyAndNeverFlipsByItself() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(option("case", "Aa", Optional.of(true)), option("word", "W", Optional.of(false)), option("plain", "Next", Optional.empty()))));
            try (var view = new KitView(context, spec, events::add)) {
                var pressed = find(view, "case", Buttons.KitButton.class); var released = find(view, "word", Buttons.KitButton.class); var plain = find(view, "plain", Buttons.KitButton.class);
                assertThat(pressed.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.TOGGLE_BUTTON);
                assertThat(pressed.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)).isTrue();
                assertThat(released.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.TOGGLE_BUTTON);
                assertThat(released.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)).isFalse();
                assertThat(plain.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
                released.doClick();
                assertThat(events).containsExactly(new UiEvent.Action("word"));
                assertThat(released.isSelected()).as("the owner confirms the new state").isFalse();
                assertThat(colourAt(released, 40, 30)).as("an unpressed ghost button has no fill").isNotEqualTo(colourAt(pressed, 40, 30));
                assertThat(colourAt(pressed, 40, 30)).isEqualTo(context.theme().semantic().color(SemanticColor.SEGMENT_SELECTED_BG).getRGB());
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(option("case", "Aa", Optional.of(false)), option("word", "W", Optional.of(true)), option("plain", "Next", Optional.empty())))));
                assertThat(find(view, "word", Buttons.KitButton.class)).as("same control").isSameAs(released);
                assertThat(released.isSelected()).isTrue(); assertThat(pressed.isSelected()).isFalse();
            } finally { release(context); }
        });
    }

    /** The colour painted at the centre of a lone component of this size, over a transparent backdrop. */
    static int colourAt(JComponent component, int width, int height) {
        component.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(width / 2, 2);
    }

    @Test void severityTextTakesItsColourAndAMonospacedCaretDrawsABarAfterItsCharacters() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                new Text("error", READY, "E492: Not an editor command", TextRole.MONO, Optional.of(Severity.DANGER), OptionalInt.empty()),
                new Text("bell", READY, "-- INSERT --", TextRole.MONO, Optional.of(Severity.INFO), OptionalInt.empty()),
                new Text("line", READY, ":abc", TextRole.MONO, Optional.empty(), OptionalInt.of(2)),
                new Text("plain", READY, ":abc", TextRole.MONO))));
            try (var view = new KitView(context, spec, e -> { })) {
                var semantic = context.theme().semantic();
                assertThat(find(view, "error", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.STATUS_DANGER));
                assertThat(find(view, "bell", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.ACCENT));
                assertThat(find(view, "plain", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                var line = find(view, "line", Labels.LiteralLabel.class); var plain = find(view, "plain", Labels.LiteralLabel.class);
                var withCaret = paint(line, 200, 24); var without = paint(plain, 200, 24);
                int x = line.getInsets().left + line.getFontMetrics(line.getFont()).stringWidth(":a");
                assertThat(columnInk(withCaret, x)).as("a bar after two characters").isGreaterThan(columnInk(without, x) + 10);
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                    new Text("error", READY, "E492: Not an editor command", TextRole.MONO),
                    new Text("bell", READY, "-- INSERT --", TextRole.MONO),
                    new Text("line", READY, ":abc", TextRole.MONO),
                    new Text("plain", READY, ":abc", TextRole.MONO)))));
                assertThat(find(view, "error", JLabel.class).getForeground()).as("the tint goes with the severity").isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                assertThat(columnInk(paint(line, 200, 24), x)).isEqualTo(columnInk(without, x));
            } finally { release(context); }
        });
    }

    static BufferedImage paint(JComponent component, int width, int height) {
        component.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    /** Opaque pixels in one column, a count of how much ink is there. */
    static int columnInk(BufferedImage image, int x) {
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) > 128) ink++;
        return ink;
    }

    /** A document tab: a banner, the document (or its loading state), a find bar and a command line. */
    static ViewSpec documentTab(boolean loading, boolean findOpen) {
        return new ViewSpec(new Split("tab", READY, SplitKind.DOCUMENT, List.of(
            new Text("banner", READY, "notes.txt changed on disk.", TextRole.BODY),
            new Custom("document", loading ? HIDDEN : READY, "text", CustomKind.EDITOR, "Document"),
            new Empty("state", loading ? READY : HIDDEN, new Nodes.Icon("state.icon", READY, "file", Hue.NEUTRAL, IconSize.EMPTY, "File"), "Loading notes.txt…", "", List.of()),
            new Stack("bar", findOpen ? READY : HIDDEN, LayoutRole.INLINE, List.of(findField("previous", "close")), "close"),
            new Text("vim", READY, "-- NORMAL --", TextRole.MONO))));
    }

    @Test void aDocumentSplitGivesTheVisibleDocumentOrStateTheHeightTheBarsLeave() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var text = new JTextArea(String.join("\n", Collections.nCopies(400, "a long document line")));
            var scroll = new JScrollPane(text); int disposed[] = {0};
            try (var view = new KitView(context, documentTab(false, true), e -> { })) {
                view.registerContent("text", scroll, () -> disposed[0]++);
                view.setSize(600, 500); InteractiveRowsTest.layout(view);
                var banner = find(view, "banner", JComponent.class); var vim = find(view, "vim", JComponent.class);
                var document = view.control("document").component; var bar = view.control("bar").component;
                assertThat(banner.getY()).isZero();
                assertThat(document.getY()).isEqualTo(banner.getHeight());
                int bars = banner.getHeight() + bar.getHeight() + vim.getHeight();
                assertThat(document.getHeight()).as("the document fills what the bars leave, not its own preferred height").isEqualTo(500 - bars);
                assertThat(vim.getY() + vim.getHeight()).isEqualTo(500);
                assertThat(scroll.getHeight()).isEqualTo(document.getHeight());

                view.update(documentTab(true, false)); InteractiveRowsTest.layout(view);
                var state = view.control("state").component;
                assertThat(document.isVisible()).isFalse(); assertThat(bar.isVisible()).isFalse();
                assertThat(state.getHeight()).as("the loading state fills instead").isEqualTo(500 - banner.getHeight() - vim.getHeight());
                assertThat(disposed[0]).as("a hidden document keeps its registration").isZero();

                view.update(documentTab(false, false)); InteractiveRowsTest.layout(view);
                assertThat(document.getHeight()).isEqualTo(500 - banner.getHeight() - vim.getHeight());
                assertThat(scroll.getParent()).isNotNull();
            } finally { release(context); }
            assertThat(disposed[0]).isEqualTo(1);
        });
    }

    @Test void theOtherSplitKindsStillPlaceTheirChildrenSideBySide() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Split("split", READY, SplitKind.SIDEBAR_DETAIL, List.of(new Text("left", READY, "Sidebar", TextRole.BODY), new Text("right", READY, "Detail", TextRole.BODY))));
            try (var view = new KitView(context, spec, e -> { })) {
                view.setSize(600, 100); InteractiveRowsTest.layout(view);
                var left = find(view, "left", JComponent.class); var right = find(view, "right", JComponent.class);
                assertThat(left.getY()).isEqualTo(right.getY()); assertThat(right.getX()).isGreaterThan(left.getX() + left.getWidth() - 1);
            } finally { release(context); }
        });
    }

    /** Light chrome and a dark workspace, so a re-route is visible. */
    static KitContext mixed() {
        return new KitContext(new ThemeBinding(new ThemePair(KitFixtures.light(), KitFixtures.dark()), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.CHROME, DesignMetrics.DEFAULT, new IconRegistry(), () -> false);
    }

    @Test void reRoutingAViewRepaintsItsControlsFromTheWorkspaceSlotInPlaceAndLeavesOtherViewsAlone() {
        Edt.run(() -> {
            var context = mixed();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(new Text("title", READY, "Find", TextRole.BODY), findField("previous", "close"))));
            try (var view = new KitView(context, spec, e -> { }); var other = new KitView(context, spec, e -> { })) {
                var title = find(view, "title", JLabel.class); var input = editor(view, "find");
                input.setText("typed"); input.setCaretPosition(2);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(view.route()).isEqualTo(ThemeRoute.CHROME);
                view.route(ThemeRoute.WORKSPACE);
                assertThat(view.route()).isEqualTo(ThemeRoute.WORKSPACE);
                assertThat(find(view, "title", JLabel.class)).as("same control").isSameAs(title);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.dark().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(input.getText()).isEqualTo("typed"); assertThat(input.getCaretPosition()).isEqualTo(2);
                assertThat(other.route()).as("a view sharing the caller's context keeps its slot").isEqualTo(ThemeRoute.CHROME);
                assertThat(find(other, "title", JLabel.class).getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(context.route()).as("the caller's context is unchanged").isEqualTo(ThemeRoute.CHROME);
                view.route(ThemeRoute.CHROME);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }

    @Test void aViewPaintsItsRoutesPanelSurfaceOpaqueOnlyWhenAsked() {
        Edt.run(() -> {
            var context = mixed();
            try (var view = new KitView(context, new ViewSpec(new Text("t", READY, "", TextRole.BODY)), e -> { })) {
                assertThat(view.isOpaque()).isFalse();
                assertThat(paint(view, 40, 40).getRGB(39, 39) >>> 24).as("transparent by default").isZero();
                view.route(ThemeRoute.WORKSPACE); view.paintsSurface(true);
                assertThat(view.isOpaque()).isTrue();
                assertThat(paint(view, 40, 40).getRGB(39, 39)).isEqualTo(KitFixtures.dark().semantic().color(SemanticColor.SURFACE_PANEL).getRGB());
                view.paintsSurface(false);
                assertThat(view.isOpaque()).isFalse();
            } finally { release(context); }
        });
    }

    @Test void aThemePublishAfterReRoutingFollowsTheWorkspaceSlot() {
        Edt.run(() -> {
            var context = mixed();
            try (var view = new KitView(context, new ViewSpec(new Text("t", READY, "x", TextRole.BODY)), e -> { })) {
                view.route(ThemeRoute.WORKSPACE);
                context.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.custom()));
                assertThat(find(view, "t", JLabel.class).getForeground()).isEqualTo(KitFixtures.custom().semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }
}
