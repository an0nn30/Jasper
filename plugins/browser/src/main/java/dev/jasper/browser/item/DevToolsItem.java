package dev.jasper.browser.item;

import dev.jasper.browser.page.DevTools;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ContentCommand;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.CustomContent;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A page's DevTools in a pane of their own below the page's (spec section 10's Inspect Element): a content tab whose one
 * view is the canvas Chromium's DevTools are drawn on, a custom content of kind PREVIEW, titled "DevTools — " and the
 * title of the page's tab, which it follows. It saves no restore state, so it does not come back when Jasper restores
 * its windows. It closes as any pane does, with its page, and when Chromium closes its DevTools; its close closes them.
 * Each page has one: Inspect Element while it is open shows it, and the page's DevTools made at the new point take the
 * place of those it showed. Its commands are Copy and Paste, which run in the DevTools' focused frame; Jasper's Find and
 * font size commands are not its, so their keys reach DevTools, which has its own, and so does the right-click, which
 * shows no menu, as Chromium shows none off screen. The Web menu's keys win in it, as in every content tab of the
 * plugin's, and act on its page's item ({@link BrowserItems#focused}). UI thread.
 */
final class DevToolsItem implements AutoCloseable {
    /** What the tab's title starts with, before the title of the page's tab. */
    static final String TITLE = "DevTools — ";
    private static final String KEY = "browser.devtools:", CANVAS = "browser.devtools", CONTENT = "browser.devtools.content.";
    private final UiView view;
    private ContentTab tab;
    // The DevTools the item shows, and how many it has shown: the canvas's content id counts them.
    private DevTools tools;
    private int shown;
    private Subscription ending = () -> { };
    private String title;
    private boolean closed;

    private DevToolsItem(PluginContext context, DevTools tools, String pageTitle) {
        this.tools = tools;
        title = TITLE + pageTitle;
        view = context.kit().create(spec(), event -> { });
        try {
            view.registerContent(content(tools));
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
    }

    /**
     * Opens {@code tools} in a new DevTools item, in a pane below the page's, whose tab is selected first, as a split
     * opens below the focused pane of its window's shown tab.
     *
     * @param context the plugin's context
     * @param page the tab of the page's item
     * @param tools the page's DevTools, which the item closes with itself
     * @param pageTitle the title of the page's tab
     * @return the item
     */
    static DevToolsItem open(PluginContext context, ContentTab page, DevTools tools, String pageTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(pageTitle, "pageTitle");
        DevToolsItem item;
        try {
            item = new DevToolsItem(context, tools, pageTitle);
        } catch (RuntimeException | Error failure) {
            tools.close();
            throw failure;
        }
        item.attach(context, page);
        return item;
    }

    private void attach(PluginContext context, ContentTab page) {
        if (!page.isActive()) page.select();
        try {
            tab = context.windows().openTab(new ContentSpec(KEY + UUID.randomUUID(), title), page, view, TabPlacement.SPLIT_DOWN);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        tab.setFocusTarget(() -> { if (!closed) tools.focus(); });
        tab.setCommands(List.of(new ContentCommand("copy", "Copy", null, true, () -> { if (!closed) tools.copy(); }),
            new ContentCommand("paste", "Paste", null, true, () -> { if (!closed) tools.paste(); })));
        tab.onClosed(this::tabClosed);
        ending = tools.onEnd(this::close);
        present();
    }

    /**
     * Shows {@code next}, the page's DevTools made at a new point, in place of those the item shows, which close, and
     * selects the item.
     *
     * @param next the page's new DevTools
     */
    void show(DevTools next) {
        Objects.requireNonNull(next, "next");
        if (closed) {
            next.close();
            return;
        }
        ending.close();
        DevTools before = tools;
        tools = next;
        shown++;
        view.update(spec()); // the canvas before leaves the view, which closes its DevTools
        before.close();
        view.registerContent(content(next));
        ending = next.onEnd(this::close);
        tab.select();
    }

    /**
     * The page's tab is now called {@code pageTitle}: so is the item, after "DevTools — ".
     *
     * @param pageTitle the title of the page's tab
     */
    void retitle(String pageTitle) {
        Objects.requireNonNull(pageTitle, "pageTitle");
        if (closed) return;
        title = TITLE + pageTitle;
        present();
    }

    /** The item's tab. */
    ContentTab tab() {
        return tab;
    }

    /** Whether the item has closed. */
    boolean closed() {
        return closed;
    }

    /** Closes the item without asking, and its DevTools with it. */
    @Override public void close() {
        if (tab != null) tab.close();
        else view.close();
    }

    /** The tab closed, as any pane closes, or with the page: the DevTools close with it. */
    private void tabClosed() {
        closed = true;
        ending.close();
        view.close();
        tools.close();
    }

    private void present() {
        if (!closed) tab.setPresentation(new ContentPresentation(title, "", BrowserItems.GLOBE, false, "", ""));
    }

    private ViewSpec spec() {
        return new ViewSpec(new Nodes.Custom(CANVAS, ControlState.READY, CONTENT + shown, Nodes.CustomKind.PREVIEW, "DevTools"));
    }

    private CustomContent content(DevTools shownTools) {
        return new CustomContent(CONTENT + shown, Nodes.CustomKind.PREVIEW, shownTools.view(), shownTools);
    }
}
