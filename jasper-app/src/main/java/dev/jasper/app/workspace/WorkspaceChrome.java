package dev.jasper.app.workspace;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import java.util.Objects;

/**
 * The workspace's chrome under the look (Enterprise presentation spec 3.3 and 9.1): the one reader of
 * {@link WorkspaceLook}'s variants, choosing each strategy once for every window it is given to. Everything else in the
 * workspace reads the strategies it holds.
 */
public final class WorkspaceChrome {
    /** The default look's chrome. */
    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"),
        WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL);

    private final MenuBarLayout menus;
    private final TitleFormat title;
    /** The header's composition, made for each window by {@link #header()}. */
    private final WorkspaceLook.Header header;
    /** The editor tabs, made for each window by {@link #tabs(WindowContent)}. */
    private final WorkspaceLook.Tabs tabs;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title, WorkspaceLook.Header header, WorkspaceLook.Tabs tabs) {
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
        this.header = Objects.requireNonNull(header);
        this.tabs = Objects.requireNonNull(tabs);
    }

    /**
     * The strategies for {@code look}.
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title) {
        Objects.requireNonNull(look, "look");
        MenuBarLayout menus = switch (look.menus()) {
            case STANDARD -> new StandardMenuBar();
            case CLASSIC -> new ClassicMenuBar();
        };
        return new WorkspaceChrome(menus, title, look.header(), look.tabs());
    }

    MenuBarLayout menus() { return menus; }
    TitleFormat title() { return title; }
    /** A new header composition for one window (spec 10.3). */
    HeaderComposition header() {
        return switch (header) {
            case UNIFIED -> new UnifiedHeader();
            case CLASSIC -> new ClassicHeader();
        };
    }

    /** The tab strip for {@code owner} (spec 10.5). */
    TabStrip tabs(WindowContent owner) {
        return switch (tabs) {
            case EQUAL -> new WindowTabs(owner);
            case CLASSIC -> new ClassicTabs(owner);
        };
    }

    /** Two chromes are equal when they choose the same strategies (the strategies hold no state) and the same template. */
    @Override public boolean equals(Object other) {
        return other instanceof WorkspaceChrome chrome && menus.getClass() == chrome.menus.getClass() && header == chrome.header
            && tabs == chrome.tabs && title.equals(chrome.title);
    }

    @Override public int hashCode() { return Objects.hash(menus.getClass(), header, tabs, title); }
}
