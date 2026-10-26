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
    public record Activity(FakeKitView view) {
        public String getText() { return ((Nodes.ItemList) node(view, "fileserver.activity.log")).items().stream()
            .map(item -> ((Nodes.Text) item.title().getFirst()).text()).collect(java.util.stream.Collectors.joining("\n")); }
    }
    public static Button button(dev.jasper.sdk.ui.UiView view, String id) {
        return new Button(() -> {
            var found = node(fake(view), id);
            if (!(found instanceof Nodes.Button actual)) throw new AssertionError("Missing button " + id);
            fake(view).emit(new UiEvent.Action(actual.action()));
        });
    }
    public static Button add(FileServersPanel panel) { return button(panel.view(), "fileserver.empty.new"); }
    public static Button toggle(FileServersPanel panel) { return new Button(() -> panel.selected().ifPresent(id -> fake(panel.view()).emit(new UiEvent.Toggled("switch:" + id, true)))); }
    public static Activity activity(FileServersPanel panel) { return new Activity(fake(panel.view())); }
    public static Label cardHostKey(FileServersPanel panel) { return new Label(fake(panel.view()), "fileserver.card.hostkey"); }
    public static Label cardTls(FileServersPanel panel) { return new Label(fake(panel.view()), "fileserver.card.tls"); }
    public static Field name(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.NAME); }
    /** Choose… beside the read-only folder: the host's next queued directory, or the editor's own chooser. */
    public static Button chooseFolder(ServerEditor editor) { return button(editor.view(), ServerEditor.CHOOSE_FOLDER); }
    public static Field port(ServerEditor editor) { return new Field(fake(editor.view()), ServerEditor.PORT); }
    public static Button save(ServerEditor editor) { return button(editor.view(), ServerEditor.SAVE); }
    public static Label message(ConfirmPanel panel) { return new Label(fake(panel.view()), "fileserver.confirm.message"); }
    public static Button confirm(ConfirmPanel panel) { return button(panel.view(), "fileserver.confirm.accept"); }
    public static Button cancel(ConfirmPanel panel) { return button(panel.view(), "fileserver.confirm.cancel"); }
    public static JPopupMenu menuFor(FileServersPanel panel, int index) {
        var menu = new JPopupMenu();
        edt(() -> {
            var list = (Nodes.ItemList) node(fake(panel.view()), FileServersPanel.ROWS);
            ListItem item = list.items().stream().filter(row -> row.id().startsWith("server:")).toList().get(index);
            item.contextActions().orElseThrow().forEach(action -> {
                if (action.separator()) { menu.addSeparator(); return; }
                var button = new JMenuItem(action.label());
                button.setEnabled(action.enabled());
                button.addActionListener(event -> fake(panel.view()).emit(new UiEvent.Action(action.action())));
                menu.add(button);
            });
        });
        return menu;
    }
    public static FakeKitView fake(dev.jasper.sdk.ui.UiView view) { return (FakeKitView) view; }
    /** The node {@code id} of the view's current spec, or empty when the view has none. */
    public static java.util.Optional<Nodes.Node> find(FakeKitView view, String id) { return java.util.Optional.ofNullable(node(view, id)); }
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
            case Nodes.Split value -> children.addAll(value.children());
            case Nodes.Table value -> value.empty().ifPresent(children::add);
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
