package dev.jasper.app.contributions;

import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One window's workspace as the tab registry sees it (workspace tabs spec 9.1). EDT only.
 *
 * @param id the window's id
 * @param attach shows a tab the registry placed in this window, at the end of its active group
 * @param detach takes a tab out of this window's layout, once the tab has closed (the registry disposes it first), as
 *     it moves to another window, or when the registry takes back an attach that failed
 * @param select shows a tab of this window and activates its group
 * @param toFront raises the window
 * @param groupOf the group that holds a tab of this window
 * @param attachBeside shows a tab the registry placed in this window in a new group on a side of one of its groups
 */
public record WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select,
                            Runnable toFront, Function<TabId, Optional<GroupId>> groupOf, Beside attachBeside) {
    /** Shows a tab in a new group beside a group of the window (workspace tabs spec 14's split placements; plan 5's drops). */
    @FunctionalInterface
    public interface Beside {
        /**
         * Shows {@code tab} in a new group on {@code side} of {@code group}.
         *
         * @param tab a tab the registry placed in this window
         * @param group a group of this window; null for its active group
         * @param side where the new group goes
         */
        void attach(Tab tab, GroupId group, Side side);
    }

    /** Requires every value. */
    public WorkspaceHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(attach, "attach"); Objects.requireNonNull(detach, "detach");
        Objects.requireNonNull(select, "select"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(groupOf, "groupOf");
        Objects.requireNonNull(attachBeside, "attachBeside");
    }

    /**
     * A workspace that places every tab at the end of its active group, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     * @param groupOf the group that holds a tab of this window
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront,
                         Function<TabId, Optional<GroupId>> groupOf) {
        this(id, attach, detach, select, toFront, groupOf, (tab, group, side) -> attach.accept(tab));
    }

    /**
     * A workspace that keeps no groups, as a fake window in a test does.
     *
     * @param id the window's id
     * @param attach shows a tab the registry placed in this window
     * @param detach takes a tab out of this window
     * @param select shows a tab of this window
     * @param toFront raises the window
     */
    public WorkspaceHost(UUID id, Consumer<Tab> attach, Consumer<Tab> detach, Consumer<Tab> select, Runnable toFront) {
        this(id, attach, detach, select, toFront, tab -> Optional.empty());
    }
}
