package dev.jasper.app.settingsui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * The Settings dialog's panel, arranged as IntelliJ's: search and page tree at left; header, banner and page at right;
 * buttons below. The arrangement is app-owned (Plan 3 unifies the shell); every control in it is a kit view, so the
 * theme supplies only colors. EDT only.
 */
final class SettingsView extends JPanel implements AutoCloseable {
    static final String SEARCH = "search", BACK = "back", FORWARD = "forward", HELP = "help", CANCEL = "cancel", APPLY = "apply",
        OK = "ok", BANNER_ACTION = "banner-action";

    final PageTree tree = new PageTree();
    final KitView search;
    final KitView navigation;
    final KitView header;
    final KitView banner;
    final KitView footer;
    final JScrollPane body = new JScrollPane();
    private final dev.jasper.ui.swing.SettingsShell shell;
    private String query = "";
    private String breadcrumb = "";
    private String bannerText;
    private String bannerAction;
    private String message = "";
    private boolean back, forward, apply, ok;

    SettingsView(KitContext context, Consumer<UiEvent> events, Runnable cancelled) {
        super(new BorderLayout());
        setOpaque(false);
        tree.artwork(context.icons());
        search = new KitView(context, searchSpec(), events::accept);
        navigation = new KitView(context, new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree.node()))), events::accept);
        header = new KitView(context, headerSpec(), events::accept);
        banner = new KitView(context, bannerSpec(), events::accept);
        banner.setVisible(false);
        footer = new KitView(context, footerSpec(), events::accept);

        body.setBorder(BorderFactory.createEmptyBorder());
        body.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        body.setOpaque(false); body.getViewport().setOpaque(false);
        shell = new dev.jasper.ui.swing.SettingsShell(context, search, navigation, header, banner, body, footer);
        add(shell, BorderLayout.CENTER);

        bind(KeyEvent.VK_ESCAPE, "cancelSettings", cancelled);
    }

    private Runnable enter = () -> { };
    private JRootPane root;

    /**
     * Enter outside a control that takes it presses OK, as the dialog's default button did. The binding lives on the
     * root pane, where a closed dropdown forwards Enter just as it would to a default button. Like a disabled default
     * button, the binding is disabled while OK is, so Enter is then not consumed and does nothing.
     */
    void onEnter(Runnable ok) { enter = ok; }

    @Override public void addNotify() {
        super.addNotify();
        root = SwingUtilities.getRootPane(this);
        if (root == null) return;
        root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "okSettings");
        root.getActionMap().put("okSettings", new AbstractAction() {
            @Override public boolean isEnabled() { return ok; }
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { if (ok) enter.run(); }
        });
    }

    @Override public void removeNotify() {
        if (root != null) {
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            root.getActionMap().remove("okSettings");
            root = null;
        }
        super.removeNotify();
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    private ViewSpec searchSpec() {
        return new ViewSpec(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search settings", "Search settings", ""));
    }

    private ViewSpec headerSpec() {
        int divider=breadcrumb.lastIndexOf(" › ");
        String parent=divider<0?"":breadcrumb.substring(0,divider)+" › ";String current=divider<0?breadcrumb:breadcrumb.substring(divider+3);
        return new ViewSpec(new Nodes.Stack("header",ControlState.READY,Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Stack("history",ControlState.READY,Nodes.LayoutRole.JOINED_ACTIONS,List.of(
                new Nodes.Button(BACK,enabled(back),"","chevron.left",Nodes.ButtonKind.GHOST,BACK,"Back"),
                new Nodes.Button(FORWARD,enabled(forward),"","chevron.right",Nodes.ButtonKind.GHOST,FORWARD,"Forward"))),
            new Nodes.Text("breadcrumb-parent",new ControlState(!parent.isBlank(),true,false,""),parent,Nodes.TextRole.DESCRIPTION),
            new Nodes.Text("breadcrumb",ControlState.READY,current,Nodes.TextRole.WINDOW_TITLE))));
    }

    private ViewSpec bannerSpec() {
        String text = bannerText == null ? "" : bannerText;
        String action = bannerAction == null || bannerAction.isBlank() ? "Open" : bannerAction;
        return new ViewSpec(new Nodes.Card("banner", ControlState.READY, Nodes.CardKind.MIXED, List.of(
            new Nodes.Stack("banner-line", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                new Nodes.StatusDot("banner-status", ControlState.READY, Nodes.StatusKind.WARNING),
                new Nodes.Text("banner-text", ControlState.READY, text, Nodes.TextRole.BODY),
                new Nodes.Button(BANNER_ACTION, ControlState.READY, action, "", Nodes.ButtonKind.SECONDARY, BANNER_ACTION, ""))))));
    }

    private ViewSpec footerSpec() {
        var failed = new ControlState(!message.isEmpty(), true, false, "");
        return new ViewSpec(new Nodes.Stack("footer", ControlState.READY, Nodes.LayoutRole.SETTINGS_FOOTER, List.of(
            new Nodes.Button(HELP, ControlState.READY, "", "question", Nodes.ButtonKind.GHOST, HELP, "Help", Nodes.ButtonPresentation.CIRCULAR),
            new Nodes.Stack("message-line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.StatusDot("message-status", failed, Nodes.StatusKind.DANGER),
                new Nodes.Text("message", ControlState.READY, message, Nodes.TextRole.BODY))),
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(APPLY, enabled(apply), "Apply", "", Nodes.ButtonKind.SECONDARY, APPLY, ""),
            new Nodes.Button(OK, enabled(ok), "OK", "", Nodes.ButtonKind.PRIMARY, OK, ""))));
    }

    private static ControlState enabled(boolean value) { return new ControlState(true, value, false, ""); }

    /** The search text the user typed; kept in the field's descriptor so a re-render never resets it. */
    void query(String value) { query = value; if (!search.isClosed()) search.update(searchSpec()); }

    void tree() { if (!navigation.isClosed()) tree.publish(() -> navigation.update(new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree.node()))))); }

    void breadcrumb(String value) { breadcrumb = value; header(); }

    void buttons(boolean back, boolean forward, boolean apply, boolean ok) {
        this.back = back; this.forward = forward; this.apply = apply; this.ok = ok;
        header();
        footer();
    }

    private void header() { if (!header.isClosed()) header.update(headerSpec()); }
    private void footer() { if (!footer.isClosed()) footer.update(footerSpec()); }

    void message(String value) { message = value == null ? "" : value; footer(); }

    /** Shows the banner with its button, or hides it when {@code text} is null. */
    void banner(String text, String action) {
        bannerText = text;
        bannerAction = action;
        banner.setVisible(text != null);
        if (!banner.isClosed()) banner.update(bannerSpec());
        revalidate();
    }

    boolean enabled(String button) {
        return switch (button) {
            case BACK -> back; case FORWARD -> forward; case APPLY -> apply; case OK -> ok;
            default -> true;
        };
    }
    String breadcrumb() { return breadcrumb; }
    String message() { return message; }
    String bannerText() { return bannerText; }
    String query() { return query; }

    private final java.util.Map<Component,java.awt.Point> scrollPositions = new java.util.IdentityHashMap<>();
    private long pageGeneration, revealGeneration;
    void page(JComponent content) {
        Component before=body.getViewport().getView();if(before==content)return;
        if(before!=null)scrollPositions.put(before,body.getViewport().getViewPosition());
        ++revealGeneration;
        long generation=++pageGeneration;
        body.setViewportView(content);body.getViewport().setOpaque(false);
        var position=scrollPositions.getOrDefault(content,new java.awt.Point());
        body.getViewport().setViewPosition(position);
        SwingUtilities.invokeLater(()->{if(generation==pageGeneration&&body.getViewport().getView()==content)body.getViewport().setViewPosition(position);});
    }

    /** Search owns the final scroll position; ordinary page navigation keeps its remembered position. */
    void highlight(Runnable action, boolean revealing) {
        long generation = ++revealGeneration;
        if (revealing) ++pageGeneration;
        action.run();
        if (revealing) {
            Component content = body.getViewport().getView();
            SwingUtilities.invokeLater(() -> {
                if (generation == revealGeneration && body.getViewport().getView() == content) action.run();
            });
        }
    }

    /** The mounted kit control named {@code id} in any of the shell's views; for tests and previews. */
    <T extends Component> T control(String id, Class<T> type) {
        for (var view : List.of(search, navigation, header, banner, footer)) {
            T found = find(view, id, type);
            if (found != null) return found;
        }
        return null;
    }

    static <T extends Component> T find(Component component, String id, Class<T> type) {
        if (type.isInstance(component) && id.equals(component.getName())) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, id, type);
            if (found != null) return found;
        }
        return null;
    }

    @Override public void close() {
        ++pageGeneration; ++revealGeneration; scrollPositions.clear();
        RuntimeException failure=null;
        for(AutoCloseable owned:List.<AutoCloseable>of(shell,search,navigation,header,banner,footer,tree::close)) {
            try {owned.close();} catch(Exception problem) {
                var error=problem instanceof RuntimeException runtime?runtime:new IllegalStateException(problem);
                if(failure==null)failure=error;else failure.addSuppressed(error);
            }
        }
        if(failure!=null)throw failure;
    }
}
