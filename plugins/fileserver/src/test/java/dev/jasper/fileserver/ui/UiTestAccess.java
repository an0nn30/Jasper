package dev.jasper.fileserver.ui;

import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Test-only event and descriptor bridge for the fake kit. */
public final class UiTestAccess {
    private UiTestAccess() { }
    public record Button(Runnable action) { public void doClick() { edt(action); } }
    public record Field(FakeKitView view, String id) {
        public void setText(String text) { edt(() -> view.emit(new UiEvent.Edited(id, text))); }
        public String getText() { return ((Nodes.Field) node(view, id)).value(); }
    }
    public record Label(FakeKitView view, String id) {
        public String getText() { return ((Nodes.Text) node(view, id)).text(); }
    }
    public static Button add(FileServersPanel panel) { return new Button(() -> fake(panel.view()).emit(new UiEvent.Action("fileserver.empty.new"))); }
    public static Button toggle(FileServersPanel panel) { return new Button(() -> panel.selected().ifPresent(id -> fake(panel.view()).emit(new UiEvent.Toggled("switch:" + id, true)))); }
    public static Label activity(FileServersPanel panel) { return new Label(fake(panel.view()), "fileserver.activity.log"); }
    public static Label cardHostKey(FileServersPanel panel) { return new Label(fake(panel.view()), "fileserver.card.hostkey"); }
    public static Label cardTls(FileServersPanel panel) { return new Label(fake(panel.view()), "fileserver.card.tls"); }
    public static Field name(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.NAME); }
    public static Field folder(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.FOLDER); }
    public static Field port(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.PORT); }
    public static Button save(ServerEditor editor) { return new Button(() -> fake(editor.view()).emit(new UiEvent.Action(ServerEditor.SAVE))); }
    public static Label message(ConfirmPanel panel) { return new Label(fake(panel.view()), "fileserver.confirm.message"); }
    public static Button confirm(ConfirmPanel panel) { return new Button(() -> fake(panel.view()).emit(new UiEvent.Action("fileserver.confirm.accept"))); }
    public static Button cancel(ConfirmPanel panel) { return new Button(() -> fake(panel.view()).emit(new UiEvent.Action("fileserver.confirm.cancel"))); }
    public static JPopupMenu menuFor(FileServersPanel panel, int index) {
        var menu = new JPopupMenu();
        edt(() -> {
            var list = (Nodes.ItemList) node(fake(panel.view()), FileServersPanel.ROWS);
            ListItem item = list.items().stream().filter(row -> row.id().startsWith("server:")).toList().get(index);
            item.contextActions().orElseThrow().forEach(action -> {
                var button = new JMenuItem(action.label());
                button.addActionListener(event -> fake(panel.view()).emit(new UiEvent.Action(action.action())));
                menu.add(button);
            });
        });
        return menu;
    }
    public static FakeKitView fake(dev.jasper.sdk.ui.UiView view) { return (FakeKitView) view; }
    public static Nodes.Node node(FakeKitView view, String id) {
        AtomicReference<Nodes.Node> found = new AtomicReference<>();
        edt(() -> walk(view.spec().root(), id, found));
        return found.get();
    }
    private static void walk(Nodes.Node node, String id, AtomicReference<Nodes.Node> found) {
        if (node.id().equals(id)) { found.set(node); return; }
        var children = new ArrayList<Nodes.Node>();
        switch (node) {
            case Nodes.Panel value -> { children.add(value.header()); value.searchOrNavigation().ifPresent(children::add); children.add(value.body()); value.footer().ifPresent(children::add); }
            case Nodes.Header value -> { value.context().ifPresent(children::add); value.primary().ifPresent(children::add); }
            case Nodes.Stack value -> children.addAll(value.children());
            case Nodes.Form value -> { value.hero().ifPresent(children::add); children.addAll(value.sections()); value.preview().ifPresent(children::add); children.addAll(value.actions()); }
            case Nodes.Card value -> children.addAll(value.children());
            case Nodes.Section value -> children.add(value.body());
            case Nodes.FormRow value -> { children.add(value.control()); value.trailing().ifPresent(children::add); }
            case Nodes.SettingRow value -> children.add(value.control());
            case Nodes.Empty value -> { children.add(value.icon()); children.addAll(value.actions()); }
            case Nodes.ItemList value -> { value.addAction().ifPresent(children::add); for (var item : value.items()) { item.leading().ifPresent(children::add); children.addAll(item.title()); children.addAll(item.subtitle()); children.addAll(item.trailing()); } }
            default -> { }
        }
        for (var child : children) { if (found.get() != null) return; walk(child, id, found); }
    }
    public static void edt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) { action.run(); return; }
        try { SwingUtilities.invokeAndWait(action); } catch (Exception failure) { throw new AssertionError(failure); }
    }
}
