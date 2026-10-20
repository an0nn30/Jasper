package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.ui.icons.ChromeArtwork;
import java.util.Objects;

/**
 * The workspace's chrome under the look (Enterprise presentation spec 3.3 and 9.1): the one reader of
 * {@link WorkspaceLook}'s variants, choosing each strategy once for every window it is given to. Everything else in the
 * workspace reads the strategies it holds.
 */
public final class WorkspaceChrome {
    /** The default look's chrome. */
    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"),
        WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, DockChrome.STANDARD, PaletteLook.DEFAULT,
        ChromeArtwork.NEW_UI);

    private final MenuBarLayout menus;
    private final TitleFormat title;
    /** The header's composition, made for each window by {@link #header()}. */
    private final WorkspaceLook.Header header;
    /** The editor tabs, made for each tab group and each window's parent-tab bar by {@link #tabs(StripSource)}. */
    private final WorkspaceLook.Tabs tabs;
    /** The status bar's layout, and whether it leads with the run item: made for each window by {@link #statusBar}. */
    private final WorkspaceLook.Status status;
    private final boolean runItem;
    /** The dock's look: read here once from the look's stripes and frames (spec 3.3). */
    private final DockChrome dock;
    /** The palette's projection (spec 11.5): the view PaletteViews builds from it. */
    private final PaletteLook palette;
    /** Where the palette's view lives in each window: picked here once from the palette's home. */
    private final PaletteHome paletteHome;
    /** The icon set each window's header and status bar receive: read here once from the look's icons. */
    private final ChromeArtwork artwork;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, WorkspaceLook.Header header, WorkspaceLook.Tabs tabs,
                            WorkspaceLook.Status status, boolean runItem, DockChrome dock, PaletteLook palette, ChromeArtwork artwork) {
        this.dock = Objects.requireNonNull(dock);
        this.palette = Objects.requireNonNull(palette, "palette");
        this.paletteHome = switch (palette.home()) {
            case TITLE_FIELD -> new TitleFieldHome();
            case UPPER -> new UpperHome();
        };
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
        this.header = Objects.requireNonNull(header);
        this.tabs = Objects.requireNonNull(tabs);
        this.status = Objects.requireNonNull(status);
        this.runItem = runItem;
        this.artwork = Objects.requireNonNull(artwork, "artwork");
    }

    /**
     * The strategies for {@code look}, with the default look's palette.
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title) { return of(look, title, PaletteLook.DEFAULT); }

    /**
     * The strategies for {@code look}, with {@code palette}'s view and home (Enterprise presentation spec 11.5: it "picks
     * them from PaletteLook.home").
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @param palette the palette's projection
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title, PaletteLook palette) {
        Objects.requireNonNull(look, "look");
        MenuBarLayout menus = switch (look.menus()) {
            case STANDARD -> new StandardMenuBar();
            case CLASSIC -> new ClassicMenuBar();
        };
        ChromeArtwork artwork = switch (look.icons()) {
            case NEW_UI -> ChromeArtwork.NEW_UI;
            case CLASSIC -> ChromeArtwork.CLASSIC;
        };
        return new WorkspaceChrome(menus, title, look.header(), look.tabs(), look.status(), look.runItem(),
            new DockChrome(look.stripes() == WorkspaceLook.Stripes.LABELLED, look.frames() == WorkspaceLook.FrameStyle.FLUSH), palette,
            artwork);
    }

    MenuBarLayout menus() { return menus; }
    TitleFormat title() { return title; }
    /** How this window docks its tool windows. */
    DockChrome dock() { return dock; }
    /** The palette's projection: the view PaletteViews builds from it. */
    PaletteLook palette() { return palette; }
    /** Where the palette's view lives in this window and how a drag attaches it there. */
    PaletteHome paletteHome() { return paletteHome; }
    /** The icon set this window's header and status bar are marked with (plan 12 Rulings 6). */
    ChromeArtwork chromeArtwork() { return artwork; }
    /** A new header composition for one window (spec 10.3). */
    HeaderComposition header() {
        return switch (header) {
            case UNIFIED -> new UnifiedHeader();
            case CLASSIC -> new ClassicHeader();
        };
    }

    /** One tab group's strip, or a window's parent-tab bar (spec 10.5; workspace tabs spec 10; parent tabs spec 7). */
    TabStrip tabs(StripSource source) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(source);
            case CLASSIC -> new ClassicTabs(source);
        };
    }

    /**
     * Whether a pane holding {@code count} tabs shows its strip (parent tabs spec 7): the classic tabs always show; the
     * equal tabs show unless the pane holds a single tab.
     */
    boolean showsStrip(int count) {
        return switch (tabs) {
            case EQUAL -> count != 1;
            case CLASSIC -> true;
        };
    }

    /**
     * Whether a window holding {@code count} parent tabs shows its parent-tab bar (parent tabs spec 7): the classic tabs
     * (Enterprise) always; the equal tabs from two parent tabs.
     */
    boolean showsParentTabBar(int count) {
        return switch (tabs) {
            case EQUAL -> count > 1;
            case CLASSIC -> true;
        };
    }

    /** A status bar for one window, led by "▶ Run…" ({@code open}, active while {@code paletteOpen}) when the look has it. */
    WindowStatusBar statusBar(javax.swing.Action open, java.util.function.BooleanSupplier paletteOpen) {
        StatusCells cells = switch (status) {
            case SLASH -> new SlashSegments();
            case RULED -> new RuledCells();
        };
        var bar = new WindowStatusBar(cells, runItem ? new RunItem(open, paletteOpen) : null);
        AppIcons.chromeReceiver(bar, artwork);
        return bar;
    }

    /**
     * Two chromes are equal when they choose the same strategies (the strategies hold no state), the same template and the
     * same icon set.
     */
    @Override public boolean equals(Object other) {
        return other instanceof WorkspaceChrome chrome && menus.getClass() == chrome.menus.getClass() && header == chrome.header
            && tabs == chrome.tabs && status == chrome.status && runItem == chrome.runItem && dock.equals(chrome.dock)
            && title.equals(chrome.title) && palette.equals(chrome.palette) && artwork == chrome.artwork;
    }

    @Override public int hashCode() { return Objects.hash(menus.getClass(), header, tabs, status, runItem, dock, title, palette, artwork); }
}
