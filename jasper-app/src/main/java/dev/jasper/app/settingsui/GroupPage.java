package dev.jasper.app.settingsui;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.JComponent;

/** A category page, as IntelliJ shows one: its title and a link to each child page. */
public final class GroupPage implements SettingsPage {
    private static final String LINK = "link:";
    private final String id;
    private final String title;
    private final Optional<String> parent;
    private final boolean hiddenWhenChildless;
    private final Supplier<List<SettingsPage>> children;
    private final PageContext context;
    private KitView view;
    private PageBody body;

    public GroupPage(String id, String title, Optional<String> parent, boolean hiddenWhenChildless,
                     Supplier<List<SettingsPage>> children, PageContext context) {
        this.id = id; this.title = title; this.parent = parent; this.hiddenWhenChildless = hiddenWhenChildless;
        this.children = children; this.context = context;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return parent; }
    @Override public List<String> searchTerms() { return List.of(title); }
    @Override public boolean hiddenWhenChildless() { return hiddenWhenChildless; }

    /** The title over one link button per child page. */
    ViewSpec viewSpec(List<SettingsPage> pages) {
        var nodes = new ArrayList<Nodes.Node>();
        nodes.add(new Nodes.Text("group-title", ControlState.READY, title, Nodes.TextRole.PANEL_TITLE));
        for (SettingsPage child : pages)
            nodes.add(new Nodes.Button(LINK + child.id(), ControlState.READY, child.title(), "", Nodes.ButtonKind.LINK, LINK + child.id(), ""));
        return new ViewSpec(new Nodes.Stack("group:" + id, ControlState.READY, Nodes.LayoutRole.DETAIL, nodes));
    }

    /** The page's kit view once built; for tests. */
    KitView view() { return view; }

    @Override public JComponent component() {
        if (body != null) return body;
        view = new KitView(context.kit().get(), viewSpec(children.get()), event -> {
            if (event instanceof UiEvent.Action action && action.id().startsWith(LINK)) context.navigate().accept(action.id().substring(LINK.length()));
        });
        body = new PageBody(view);
        return body;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { if (view != null) view.close(); }
}
