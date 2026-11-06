package dev.jasper.browser.item;

import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The menu button's privacy group (spec section 6): a separator, a Reset item for each permission stored for the page's
 * site, such as "Reset Camera Permission (Allowed)", and a separator. The menu drops a separator that leads or follows
 * another ({@link BrowserToolbar}), so with no permission stored the group shows nothing. Choosing a Reset item revokes
 * that permission for the site, and the items of the store that show the site load again.
 */
final class PrivacyMenu {
    /** What a Reset item reports, before its permission's token. */
    static final String RESET = "browser.permission.reset.";
    private static final MenuItem START = new MenuItem("browser.permission.separator", "", "", "", true, true),
        END = new MenuItem("browser.permission.end", "", "", "", true, true);

    private PrivacyMenu() { }

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
