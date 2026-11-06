package dev.jasper.browser.item;

import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One item's privacy group of the menu button (spec section 6): a separator, a Reset item for each permission its store
 * holds for the page's site, such as "Reset Camera Permission (Allowed)", and a separator. The menu drops a separator
 * that leads or follows another ({@link BrowserToolbar}), so with no permission stored the group shows nothing. From
 * {@link #follows}, the item draws again as its store's permissions change.
 *
 * <p>A Reset item chosen revokes its permission for the site the menu last offered that item for (ruling V17), even when
 * the page has gone to another site since, and the items of the store that show that site load again. The host updates
 * an open menu as the item draws, so the site is the one the row showed; where the new site offers the same Reset, the
 * row looks the same, and it counts as the new site's. UI thread.
 */
final class PrivacyMenu implements AutoCloseable {
    /** What a Reset item reports, before its permission's token. */
    static final String RESET = "browser.permission.reset.";
    private static final MenuItem START = new MenuItem("browser.permission.separator", "", "", "", true, true),
        END = new MenuItem("browser.permission.end", "", "", "", true, true);
    private final DataStore store;
    private final BrowserItems items;
    private final Supplier<String> address;
    private final Runnable reload;
    // The item's listening to its store's permissions, from follows() until close().
    private Subscription changed = () -> { };
    // For each permission, the site the menu last offered its Reset item for.
    private final Map<Permission, String> offered = new EnumMap<>(Permission.class);

    /**
     * The group of an item in {@code store}.
     *
     * @param store the item's data store, whose permissions the group offers to reset
     * @param items the plugin's items, of which those in {@code store} that show a site reset load again
     * @param address the address the item shows now
     * @param reload loads the item's page again, when it may
     */
    PrivacyMenu(DataStore store, BrowserItems items, Supplier<String> address, Runnable reload) {
        this.store = Objects.requireNonNull(store, "store");
        this.items = Objects.requireNonNull(items, "items");
        this.address = Objects.requireNonNull(address, "address");
        this.reload = Objects.requireNonNull(reload, "reload");
    }

    /**
     * The item is made: {@code render} draws it again as its store's permissions change, until {@link #close()}.
     *
     * @param render draws the item again
     */
    void follows(Runnable render) {
        changed.close();
        changed = store.permissions().onChanged(Objects.requireNonNull(render, "render"));
    }

    /**
     * The group as the menu shows it now, for the site of the page's address; each Reset item is then the one a choice
     * of its action revokes.
     *
     * @return the group's items, as {@link #items(List, boolean)} gives them
     */
    List<MenuItem> group() {
        Optional<String> site = Origins.of(address.get());
        List<Grant> grants = site.map(store.permissions()::of).orElse(List.of());
        for (Grant grant : grants) offered.put(grant.permission(), grant.origin());
        return items(grants, false);
    }

    /**
     * The menu reported {@code action}: a Reset item's revokes its permission for the site the menu offered it for, and
     * the store's items that show that site load again, this one among them.
     *
     * @param action the action
     * @return whether it was a Reset item's
     */
    boolean choose(String action) {
        Optional<Permission> permission = reset(action);
        if (permission.isEmpty()) return false;
        String site = offered.get(permission.get());
        if (site != null) items.reloadShowing(store, store.permissions().revoke(site, permission.get()));
        return true;
    }

    /**
     * The item's page loads again if it shows one of {@code sites}, whose permissions changed in its store.
     *
     * @param sites the sites, as {@link Origins#of} names them
     */
    void reloadShowing(Set<String> sites) {
        if (Origins.of(address.get()).filter(sites::contains).isPresent()) reload.run();
    }

    /** The item closes: it is drawn no more as the permissions change. */
    @Override public void close() {
        changed.close();
    }

    /**
     * The group's items.
     *
     * @param site the permissions stored for the page's site, in the order the items list them
     * @param muted whether the page is muted; plan 6's Task 7 adds Unmute Current Page for it, and nothing reads it yet
     * @return a separator, a Reset item for each grant, its id and action {@code browser.permission.reset.<token>}, and
     *     a separator
     */
    static List<MenuItem> items(List<Grant> site, boolean muted) {
        var items = new ArrayList<MenuItem>(site.size() + 2);
        items.add(START);
        for (Grant grant : site) {
            String id = RESET + grant.permission().token();
            items.add(new MenuItem(id, "Reset " + grant.permission().label() + " Permission (" + grant.decision().label() + ")", "", id, true, false));
        }
        items.add(END);
        return List.copyOf(items);
    }

    /**
     * The permission a Reset item's action resets.
     *
     * @param action an action the menu reported
     * @return the permission; empty for any other action
     */
    static Optional<Permission> reset(String action) {
        return action.startsWith(RESET) ? Permission.ofToken(action.substring(RESET.length())) : Optional.empty();
    }
}
