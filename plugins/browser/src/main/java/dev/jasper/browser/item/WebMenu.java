package dev.jasper.browser.item;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.ShortcutContext;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * The Web menu (browser spec section 7): Open Location…, then Back and Forward, then Reload Page. A window's menu bar
 * shows it only while that window's current item is a browser item. Each item is an action whose keys apply in the
 * Browser's items, where they win over the same keys bound elsewhere (Search Shell History's Cmd+R among them), and
 * each acts on the focused browser item of the window it runs in; with none there, it does nothing. Every one is bindable
 * in Settings › Keymap. UI thread.
 */
public final class WebMenu {
    static final String MENU = "dev.jasper.browser.web";
    static final String OPEN_LOCATION = "dev.jasper.browser.open_location", BACK = "dev.jasper.browser.back",
        FORWARD = "dev.jasper.browser.forward", RELOAD = "dev.jasper.browser.reload";
    private static final List<String> KEYWORDS = List.of("browser", "web", "page");

    /** One item: its action, title and keys on macOS and on Linux and Windows, and its own palette words. */
    private record Item(String id, String title, String macOs, String other, List<String> keywords) { }

    private static final List<Item> ITEMS = List.of(
        new Item(OPEN_LOCATION, "Open Location…", "cmd+l", "ctrl+l", List.of("address", "url", "location")),
        new Item(BACK, "Back", "cmd+[", "alt+left", List.of("history", "previous")),
        new Item(FORWARD, "Forward", "cmd+]", "alt+right", List.of("history", "next")),
        new Item(RELOAD, "Reload Page", "cmd+r", "ctrl+r", List.of("refresh")));

    private WebMenu() { }

    /**
     * Registers the Web menu's actions and the menu.
     *
     * @param context the plugin's context, while it starts
     * @param items the plugin's browser items
     */
    public static void register(PluginContext context, BrowserItems items) {
        register(context, items, WebMenu::run);
    }

    /**
     * As above, with what an item does to the browser item it acts on, for tests: Back, Forward and Reload Page change
     * nothing a test can see while a page has no browser.
     *
     * @param context the plugin's context
     * @param items the plugin's browser items
     * @param run runs the action named by its id on a browser item
     */
    static void register(PluginContext context, BrowserItems items, BiConsumer<String, BrowserItem> run) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(run, "run");
        for (Item item : ITEMS) {
            var keywords = new java.util.ArrayList<>(KEYWORDS);
            keywords.addAll(item.keywords());
            context.actions().register(ActionSpec.of(item.id(), item.title()).withKeywords(keywords)
                    .withDefaultBindings(item.macOs(), item.other()).withContext(ShortcutContext.content()),
                invoked -> items.focused(invoked.window()).ifPresent(browser -> run.accept(item.id(), browser)));
        }
        PluginMenu menu = context.menus().createForContent(MENU, "Web");
        menu.add(OPEN_LOCATION);
        menu.addSeparator();
        menu.add(BACK);
        menu.add(FORWARD);
        menu.addSeparator();
        menu.add(RELOAD);
    }

    /** What the action {@code id} does to {@code item}. */
    private static void run(String id, BrowserItem item) {
        switch (id) {
            case OPEN_LOCATION -> item.focusAddress();
            case BACK -> item.goBack();
            case FORWARD -> item.goForward();
            case RELOAD -> item.reload();
            default -> throw new IllegalArgumentException("Not a Web menu action: " + id);
        }
    }
}
