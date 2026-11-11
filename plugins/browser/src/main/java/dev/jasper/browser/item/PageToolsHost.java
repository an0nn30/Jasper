package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.pages.PageHtml;
import dev.jasper.browser.tools.PageTools;
import dev.jasper.browser.tools.SavePage;
import dev.jasper.browser.tools.ToolScripts;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

/**
 * One browser item's page tools (spec section 10, plan 7's F38): its page's {@link PageTools}, wired to the page's load
 * starts, load ends, address changes and tools reports, and their items first in the menu button. Reader Mode, Remove
 * Distractions and the page's right-click menu's Remove Element run through it, at the zoom the item shows its page at;
 * Reader Mode shows the article in the workspace theme's colours as they are when it shows, as Jasper's own pages
 * take them ({@link PageHtml#colours(WorkspacePalette, String)}). While the article shows, the page's find searches its
 * top frame alone, where the overlay is, and the find starts again as reader mode opens and closes. Its item tells it
 * each time its page shows, so a page that finished loading in a background tab is checked then. It also holds the
 * item's Save Page As saves under way ({@link SavePage}), handing each the page's {@code savePage} reports and load
 * starts until it is done, and its end as the item closes. UI thread.
 */
final class PageToolsHost implements AutoCloseable {
    private final Page page;
    private final Runnable render;
    private final PageTools tools;
    private final List<Subscription> heard;
    // The saves under way, which hear the page until each is done (plan 7's F38).
    private final List<SavePage> saves = new ArrayList<>();
    // Whether reader mode was on when the tools last changed: its opening or closing restarts the page's find; and
    // whether the item has closed.
    private boolean reading, closed;

    /**
     * The tools of {@code page}.
     *
     * @param page the item's page, whose top frame the tools' scripts run in
     * @param appearance the plugin's appearance, whose workspace theme the reader takes
     * @param zoom the zoom the page shows at, in percent, as the item shows it, which Remove Element divides by
     * @param later runs a task on the UI thread after a delay, answering what cancels it
     * @param render draws the item again, as the tools' items change
     */
    PageToolsHost(Page page, Appearance appearance, IntSupplier zoom, BiFunction<Duration, Runnable, Runnable> later, Runnable render) {
        this.page = Objects.requireNonNull(page, "page");
        this.render = Objects.requireNonNull(render, "render");
        Objects.requireNonNull(appearance, "appearance");
        tools = new PageTools(page::run, () -> readerCss(appearance.workspacePalette(), appearance.workspaceVariant() == Variant.DARK),
            zoom, page.view()::isShowing, later, page::focus, this::changed);
        page.findTopFrameOnly(tools::reading);
        heard = List.of(page.onLoadStart(this::loadStarted), page.onLoadEnd(tools::loaded), page.onAddressChange(tools::addressChanged),
            page.onToolReport(this::reported));
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
     * The menu reported {@code action}: Reader Mode's shows or hides the article, and Remove Distractions' starts or
     * stops distraction removal, each ending the other; then the page takes the keyboard focus.
     *
     * @param action the action
     * @return whether it was one of the tools' items
     */
    boolean choose(String action) {
        switch (action) {
            case PageTools.READER -> tools.toggleReader();
            case PageTools.DISTRACTIONS -> tools.toggleDistractions();
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * The tools' items changed: as reader mode opens or closes, the page's find starts again, as it then searches the
     * article in the overlay, or the page again (ruling D31); then the item draws.
     */
    private void changed() {
        if (tools.reading() != reading) {
            reading = tools.reading();
            page.restartFind();
        }
        render.run();
    }

    /**
     * Remove Element, from the page's right-click menu, at {@code x}, {@code y} on the page's canvas: the overlay of the
     * element there is hidden, the point divided by the zoom the item shows its page at.
     *
     * @param x the point's distance from the canvas's left
     * @param y its distance from the canvas's top
     */
    void removeElementAt(int x, int y) {
        tools.removeElementAt(x, y);
    }

    /**
     * Save Page As… started {@code save} for the item's page: it hears the page's {@code savePage} reports and load starts
     * until it is done. One given after the item closed ends at once.
     *
     * @param save the save
     */
    void saving(SavePage save) {
        if (closed) {
            save.pageClosed();
            return;
        }
        saves.removeIf(SavePage::done);
        saves.add(save);
    }

    /** The page's main frame starts loading {@code url}: the tools reset, and a save awaiting its answer fails. */
    private void loadStarted(String url) {
        tools.loadStarted(url);
        for (SavePage save : List.copyOf(saves)) save.loadStarted();
        saves.removeIf(SavePage::done);
    }

    /** A report of the page's tools: a {@code savePage} one goes to the saves under way, any other to the tools. */
    private void reported(String op, String json) {
        if (!SavePage.OP.equals(op)) {
            tools.reported(op, json);
            return;
        }
        for (SavePage save : List.copyOf(saves)) save.part(json);
        saves.removeIf(SavePage::done);
    }

    /** The item's page shows: a check that waited for it runs. */
    void shown() {
        tools.shown();
    }

    /** The item closes: the tools hear the page no more, and do nothing from now on; a save still under way is told. */
    @Override public void close() {
        closed = true;
        heard.forEach(Subscription::close);
        tools.close();
        List<SavePage> ending = List.copyOf(saves);
        saves.clear();
        ending.forEach(SavePage::pageClosed);
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
