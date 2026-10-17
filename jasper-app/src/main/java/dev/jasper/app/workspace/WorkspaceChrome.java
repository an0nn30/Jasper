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
    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"));

    private final MenuBarLayout menus;
    private final TitleFormat title;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title) {
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
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
        return new WorkspaceChrome(menus, title);
    }

    MenuBarLayout menus() { return menus; }
    TitleFormat title() { return title; }

    /** Two chromes are equal when they choose the same strategies (the strategies hold no state) and the same template. */
    @Override public boolean equals(Object other) {
        return other instanceof WorkspaceChrome chrome && menus.getClass() == chrome.menus.getClass() && title.equals(chrome.title);
    }

    @Override public int hashCode() { return Objects.hash(menus.getClass(), title); }
}
