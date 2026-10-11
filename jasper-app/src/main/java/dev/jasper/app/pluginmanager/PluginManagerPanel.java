package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * The Plugins page's passive view: a search field and gear menu, the restart banner and notice, the plugin rows and the
 * selected plugin's details, and a message line. It is an app-owned arrangement (Plan 3 recomposes it) of kit views on
 * the chrome context; it renders a snapshot and reports what the user asks for; it decides nothing. EDT only; close it
 * with the page.
 */
final class PluginManagerPanel extends JPanel {
    /** What the user can ask for. */
    record Handlers(Consumer<PluginRuntime.Row> toggle, Consumer<PluginRuntime.Row> review, Consumer<PluginRuntime.Row> remove,
                    Consumer<PluginRuntime.Row> discard, Runnable install, Consumer<PluginRuntime.Row> openSettings,
                    Consumer<PluginRuntime.Row> openFolder, Consumer<PluginRuntime.Row> openData, Consumer<URI> browse) { }

    /** One button of the banner. */
    record BannerAction(String label, Runnable run) { }

    static final String SEARCH = "search", GEAR = "gear", INSTALL = "install", BANNER = "banner:";
    /** The list's share of extra width when the page grows, as before. */
    private static final double LIST_SHARE = 0.4;
    private final KitContext kit;
    private final Handlers handlers;
    private final KitView searchView, gearView, headerView, messageView;
    private final PluginCardList list;
    private final PluginDetails details;
    private final JPanel bar = new JPanel(new BorderLayout()), top = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final AutoCloseable theme;
    private String bannerText = "";
    private List<BannerAction> bannerActions = List.of();
    private String notice = "", message = "";
    private boolean error, busy, closed;

    PluginManagerPanel(KitContext kit, Handlers handlers) {
        super(new BorderLayout());
        this.kit = kit;
        this.handlers = handlers;
        setOpaque(false);
        searchView = new KitView(kit, new ViewSpec(new Nodes.Field(SEARCH, ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search plugins",
            "Search plugins", "")), this::event);
        gearView = new KitView(kit, gearSpec(), this::event);
        headerView = new KitView(kit, headerSpec(), this::event);
        messageView = new KitView(kit, messageSpec(), this::event);
        details = new PluginDetails(kit, this::event);
        // The list reports its first selection while it is being built, before this field is set.
        list = new PluginCardList(kit, this::event, () -> { if (cardList() != null) details.show(cardList().selectedRow(), busy); });
        bar.setOpaque(false);
        bar.add(searchView, BorderLayout.CENTER);
        bar.add(gearView, BorderLayout.EAST);
        top.setOpaque(false);

        top.add(headerView, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        var listLane=new JPanel(new BorderLayout());listLane.setOpaque(false);listLane.add(bar,BorderLayout.NORTH);listLane.add(list.view(),BorderLayout.CENTER);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listLane, details.component()) {
            @Override public void updateUI() {
                setUI(dev.jasper.ui.swing.NativeControlStyles.splitPaneUI(kit::theme));
                setOpaque(false);
            }
        };
        split.setResizeWeight(LIST_SHARE);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setDividerLocation(metric(DesignMetrics.Role.PLUGIN_LIST_WIDTH));
        add(split, BorderLayout.CENTER);
        add(messageView, BorderLayout.SOUTH);
        theme = kit.binding().subscribe(pair -> restyle());
        restyle();
        details.show(list.selectedRow(), false);
    }

    @Override public java.awt.Dimension getMinimumSize() {
        var minimum=super.getMinimumSize();
        if(split!=null && split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT) {
            var insets=getInsets();
            // Preserve the user's allocated list lane while keeping the detail receiver readable.
            // The Settings viewport scrolls this minimum instead of silently compressing the right pane.
            minimum.width=Math.max(minimum.width,insets.left+insets.right+Math.max(split.getLeftComponent().getMinimumSize().width,split.getDividerLocation())+split.getDividerSize()+split.getRightComponent().getMinimumSize().width);
        }
        return minimum;
    }

    private PluginCardList cardList() { return list; }

    private int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    /** The former paddings and gaps, as named metrics. */
    private void restyle() {
        int pad = metric(DesignMetrics.Role.PLUGIN_MANAGER_PADDING), gap = metric(DesignMetrics.Role.PLUGIN_MANAGER_GAP);
        int side=metric(DesignMetrics.Role.PLUGIN_MANAGER_SIDE_PADDING);
        setBorder(BorderFactory.createEmptyBorder(pad, side, pad, pad));
        bar.setBorder(BorderFactory.createEmptyBorder(0,side,0,side));
        list.view().setBorder(BorderFactory.createEmptyBorder(0,0,0,side));
        ((BorderLayout) getLayout()).setVgap(gap);
        ((BorderLayout) bar.getLayout()).setHgap(gap);
        top.setVisible(!bannerText.isEmpty() || !notice.isEmpty());
        headerView.setBorder(BorderFactory.createEmptyBorder(bannerText.isEmpty() && notice.isEmpty() ? 0 : gap, 0, 0, 0));
        split.setDividerSize(metric(DesignMetrics.Role.WORKSPACE_GUTTER));
        revalidate();
        repaint();
    }

    /** Routes the kit views' events: the search, the gear, the banner, the rows' switches and menus and the details. */
    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(SEARCH) -> list.search(edited.text());
            case UiEvent.Toggled toggled when toggled.id().startsWith(PluginCard.TOGGLE) -> {
                var row = list.row(toggled.id().substring(PluginCard.TOGGLE.length()));
                if (row != null && !busy && PluginCard.toggleable(row)) (row.needsConsent() ? handlers.review() : handlers.toggle()).accept(row);
            }
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    private void act(String id) {
        if (id.equals(INSTALL)) { if (!busy) handlers.install().run(); return; }
        if (id.startsWith(BANNER)) {
            int index = Integer.parseInt(id.substring(BANNER.length()));
            if (index < bannerActions.size()) bannerActions.get(index).run().run();
            return;
        }
        var shown = list.selectedRow();
        switch (id) {
            case PluginDetails.PRIMARY -> { if (shown != null && !busy) (shown.needsConsent() ? handlers.review() : handlers.toggle()).accept(shown); return; }
            case PluginDetails.HOMEPAGE -> { if (shown != null) PluginDetails.homepage(shown).ifPresent(handlers.browse()); return; }
            default -> { }
        }
        if (busy) return;
        for (var entry : List.<java.util.Map.Entry<String, Consumer<PluginRuntime.Row>>>of(
                java.util.Map.entry(PluginCard.SETTINGS, handlers.openSettings()), java.util.Map.entry(PluginCard.FOLDER, handlers.openFolder()),
                java.util.Map.entry(PluginCard.DATA, handlers.openData()), java.util.Map.entry(PluginCard.REMOVE, handlers.remove()),
                java.util.Map.entry(PluginCard.DISCARD, handlers.discard())))
            if (id.startsWith(entry.getKey())) {
                var row = list.row(id.substring(entry.getKey().length()));
                if (row != null) entry.getValue().accept(row);
                return;
            }
    }

    private ViewSpec gearSpec() {
        return new ViewSpec(new Nodes.MenuButton(GEAR, ControlState.READY, "", "more", Nodes.ButtonKind.GHOST, "",
            List.of(new MenuItem(INSTALL, "Install from Zip…", "", INSTALL, !busy, false)), "Plugin options"));
    }

    private ViewSpec headerSpec() {
        var hidden = new ControlState(false, true, false, "");
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < bannerActions.size(); i++)
            buttons.add(new Nodes.Button(BANNER + i, ControlState.READY, bannerActions.get(i).label(), "", Nodes.ButtonKind.SECONDARY, BANNER + i, ""));
        var banner = new Nodes.Card("banner", bannerText.isEmpty() ? hidden : ControlState.READY, Nodes.CardKind.MIXED, List.of(
            new Nodes.Text("banner-text", ControlState.READY, bannerText.isEmpty() ? " " : bannerText, Nodes.TextRole.BODY_STRONG),
            new Nodes.Stack("banner-actions", buttons.isEmpty() ? hidden : ControlState.READY, Nodes.LayoutRole.ACTIONS, buttons)));
        return new ViewSpec(new Nodes.Stack("plugins-header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(banner,
            new Nodes.Text("notice", notice.isEmpty() ? hidden : ControlState.READY, notice.isEmpty() ? " " : notice, Nodes.TextRole.DESCRIPTION))));
    }

    private ViewSpec messageSpec() {
        return new ViewSpec(new Nodes.Stack("message-line", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.StatusDot("message-dot", error && !message.isEmpty() ? ControlState.READY : new ControlState(false, true, false, ""), Nodes.StatusKind.DANGER),
            new Nodes.Text("message", ControlState.READY, message.isEmpty() ? " " : message, Nodes.TextRole.BODY))));
    }

    private void render(KitView view, ViewSpec spec) { if (!view.isClosed()) view.update(spec); }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(PluginRuntime.Snapshot snapshot) { list.show(snapshot.rows()); }

    void busy(boolean value) {
        busy = value;
        list.busy(value);
        render(gearView, gearSpec());
        details.show(list.selectedRow(), value);
    }

    /** An empty text hides the banner. */
    void banner(String text, List<BannerAction> actions) {
        bannerText = text;
        bannerActions = text.isEmpty() ? List.of() : List.copyOf(actions);
        render(headerView, headerSpec());
        restyle();
    }

    void notice(String text) { notice = text; render(headerView, headerSpec()); restyle(); }

    void message(String text, boolean error) {
        message = text;
        this.error = error;
        render(messageView, messageSpec());
    }

    /** Closes the kit views; the page calls this when Settings lets it go. Idempotent. */
    void close() {
        if (closed) return;
        closed = true;
        try { theme.close(); } catch (Exception ignored) { /* nothing else holds the subscription */ }
        searchView.close(); gearView.close(); headerView.close(); messageView.close();
        list.close();
        details.close();
    }

    boolean closed() { return closed; }

    List<String> listed() { return list.listed(); }
    List<String> groupTitles() { return list.groupTitles(); }
    String selected() { return list.selected(); }
    void select(String id) { list.select(id); }
    PluginRuntime.Row row(String id) { return list.row(id); }
    boolean busy() { return busy; }
    PluginDetails detailsPane() { return details; }
    KitView listView() { return list.view(); }
    KitView searchView() { return searchView; }
    KitView gearView() { return gearView; }
    KitView headerView() { return headerView; }
    KitView messageView() { return messageView; }
    JSplitPane split() { return split; }
    List<String> menuLabels() { return details.menuLabels(); }
    boolean homepageShown() { return details.homepageShown(); }

    /** The gear menu's labels, from its descriptor. */
    List<String> gearLabels() { return ((Nodes.MenuButton) gearView.spec().root()).items().stream().map(MenuItem::label).toList(); }

    /** The selected plugin's details as text, or the list's empty text when nothing is selected. */
    String details() { return list.selectedRow() == null ? list.emptyText() : details.text(); }

    String bannerText() { return bannerText; }
    String noticeText() { return notice; }
    String messageText() { return message.strip(); }
    boolean messageIsError() { return error && !message.isEmpty(); }
    List<String> bannerLabels() { return bannerActions.stream().map(BannerAction::label).toList(); }
}
