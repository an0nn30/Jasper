package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.TabId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). A {@code detached} workspace is a
 * separate window for plugin views (workspace plan 4 replaces it). EDT only.
 *
 * @param id the window's id
 * @param detached whether it is a separate window for plugin views
 * @param attach shows a tab the registry placed in this window, at the end of its active group
 * @param detach takes a tab out of this window's layout, once the tab has closed (the registry disposes it first), as
 *     it moves to another window, or when the registry takes back an attach that failed
 * @param select shows a tab of this window and activates its group
 * @param toFront raises the window
 * @param groupOf the group that holds a tab of this window
 */
public record WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf) {
    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
    }

    /**
     * A workspace that keeps no groups, as a fake window in a test does.
     *
     * @param id the window's id
     * @param detached whether it is a separate window for plugin views
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     */
    public WorkspaceHost(UUID id, boolean detached, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                         Runnable toFront) {
        this(id, detached, attach, detach, select, toFront, tab -> Optional.empty());
    }
}
