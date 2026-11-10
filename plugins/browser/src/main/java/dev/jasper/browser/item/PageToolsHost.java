package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.pages.PageHtml;
import dev.jasper.browser.tools.PageTools;
import dev.jasper.browser.tools.ToolScripts;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

/**
 * One browser item's page tools (spec section 10, plan 7's F38): its page's {@link PageTools}, wired to the page's load
 * starts, load ends, address changes and tools reports, and their items first in the menu button. Reader Mode runs
 * through it, and shows the article in the workspace theme's colours as they are when it shows, as Jasper's own pages
 * take them ({@link PageHtml#colours(WorkspacePalette, String)}). Its item tells it each time its page shows, so a page
 * that finished loading in a background tab is checked then. UI thread.
 */
final class PageToolsHost implements AutoCloseable {
    private final PageTools tools;
    private final List<Subscription> heard;

    /**
     * The tools of {@code page}.
     *
     * @param page the item's page, whose top frame the tools' scripts run in
     * @param appearance the plugin's appearance, whose workspace theme the reader takes
     * @param zoom the zoom the page shows at, in percent, as the item shows it
     * @param later runs a task on the UI thread after a delay, answering what cancels it
     * @param render draws the item again, as the tools' items change
     */
    PageToolsHost(Page page, Appearance appearance, IntSupplier zoom, BiFunction<Duration, Runnable, Runnable> later, Runnable render) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(appearance, "appearance");
        tools = new PageTools(page::run, () -> readerCss(appearance.workspacePalette(), appearance.workspaceVariant() == Variant.DARK),
            zoom, page.view()::isShowing, later, render);
        heard = List.of(page.onLoadStart(tools::loadStarted), page.onLoadEnd(tools::loaded), page.onAddressChange(tools::addressChanged),
            page.onToolReport(tools::reported));
    }

    /**
     * The tools' group of the menu, which the menu shows first.
     *
     * @return Reader Mode, Remove Distractions and a separator while the page offers them; none otherwise
     */
    List<MenuItem> group() {
        return tools.menuItems();
    }

    /**
     * The menu reported {@code action}: Reader Mode's shows or hides the article. Remove Distractions' does nothing yet:
     * plan 7's Task 6 runs distraction removal.
     *
     * @param action the action
     * @return whether it was one of the tools' items
     */
    boolean choose(String action) {
        switch (action) {
            case PageTools.READER -> tools.toggleReader();
            case PageTools.DISTRACTIONS -> { }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The item's page shows: a check that waited for it runs. */
    void shown() {
        tools.shown();
    }

    /** The item closes: the tools hear the page no more, and do nothing from now on. */
    @Override public void close() {
        heard.forEach(Subscription::close);
        tools.close();
    }

    /**
     * The reader overlay's CSS: the theme's colours on its root, {@link ToolScripts#READER_ROOT}, as Jasper's own pages
     * name them, and its colour scheme, so its scroll bars and controls follow the theme.
     *
     * @param palette the workspace palette
     * @param dark whether the workspace theme is dark
     * @return the CSS
     */
    static String readerCss(WorkspacePalette palette, boolean dark) {
        return PageHtml.colours(palette, ToolScripts.READER_ROOT) + "\n" + ToolScripts.READER_ROOT + " { color-scheme: "
            + (dark ? "dark" : "light") + "; }";
    }
}
