package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.permissions.Grant;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@code jasper-about:permissions} (spec section 12), "Site Permissions": its template {@code permissions.html} and
 * script {@code permissions.js}, and the answers to what the script asks. The page lists the permissions stored, grouped
 * by site, searches them by site or permission and filters them by permission and status; each site's Clear forgets its
 * permissions, each permission's × revokes it once the user agrees, and Clear All Permissions forgets every one. It
 * reaches Java only through its queries ({@link AboutScheme#registerQueries}), answered from the asking browser's store.
 * Each request is one flat JSON object, and each answer one JSON object {@link StrictJson#write} writes:
 * <ul>
 *   <li>{@code {"op":"list"}}: every permission stored, by site, then in {@link Permission}'s order, as
 *       {@code {"grants":[{"origin","type","label","decision"}]}}, where {@code type} is the permission's token,
 *       {@code label} its label and {@code decision} {@code allowed} or {@code blocked};</li>
 *   <li>{@code {"op":"revoke","origin":o,"type":t}}: forgets the permission {@code t} of the site {@code o};</li>
 *   <li>{@code {"op":"clear-site","origin":o}}: forgets every permission of the site {@code o};</li>
 *   <li>{@code {"op":"clear-all"}}: forgets every permission stored.</li>
 * </ul>
 * The last three answer {@code {}}, and hand the sites whose permissions they changed, if any, to the store's
 * {@code reload}. Members a request does not use are ignored. Anything else fails with "Jasper does not understand this
 * request.".
 */
public final class PermissionsPage {
    /** The page's name. */
    public static final String NAME = "permissions";
    /** The page's address. */
    public static final URI ADDRESS = URI.create(AboutScheme.NAME + ":" + NAME);
    /** The reason a request the page cannot make fails with. */
    static final String NOT_UNDERSTOOD = HistoryPage.NOT_UNDERSTOOD;
    private static final String EMPTY = "{}";

    private PermissionsPage() { }

    /**
     * The answer to one of the page's requests, from {@code permissions}. Any thread: it reads and changes only the
     * permissions' cache, whose rows are written on the database's own thread.
     *
     * @param permissions the site permissions of the asking browser's store
     * @param reload given the sites a change affected, reloads that store's items that show them; called at once, on the
     *     thread that asks, so it hands the work to the UI thread
     * @param request what the page asked, as JSON
     * @return the answer's JSON; failed with "Jasper does not understand this request." for a request the page does not
     *     make. Whatever the request, never an exception
     */
    public static CompletableFuture<String> answer(PermissionStore permissions, Consumer<Set<String>> reload, String request) {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(reload, "reload");
        try {
            Map<String, Object> asked = StrictJson.object(request, 1).orElse(null);
            if (asked == null) return notUnderstood();
            return switch (asked.get("op") instanceof String op ? op : "") {
                case "list" -> CompletableFuture.completedFuture(listed(permissions.all()));
                case "revoke" -> {
                    Optional<Permission> type = asked.get("type") instanceof String token ? Permission.ofToken(token) : Optional.empty();
                    if (!(asked.get("origin") instanceof String origin) || type.isEmpty()) yield notUnderstood();
                    yield changed(permissions.revoke(origin, type.get()), reload);
                }
                case "clear-site" -> asked.get("origin") instanceof String origin ? changed(permissions.clearSite(origin), reload) : notUnderstood();
                case "clear-all" -> changed(permissions.clearAll().sites(), reload);
                default -> notUnderstood();
            };
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** A change made: the sites it affected reload, if any. */
    private static CompletableFuture<String> changed(Set<String> origins, Consumer<Set<String>> reload) {
        if (!origins.isEmpty()) reload.accept(origins);
        return CompletableFuture.completedFuture(EMPTY);
    }

    /** The permissions stored, as the page reads them. */
    private static String listed(List<Grant> grants) {
        var listed = new ArrayList<Object>(grants.size());
        for (Grant grant : grants) {
            var row = new LinkedHashMap<String, Object>();
            row.put("origin", grant.origin());
            row.put("type", grant.permission().token());
            row.put("label", grant.permission().label());
            row.put("decision", grant.decision().token());
            listed.add(row);
        }
        return StrictJson.write(Map.of("grants", listed));
    }

    private static CompletableFuture<String> notUnderstood() {
        return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_UNDERSTOOD));
    }
}
