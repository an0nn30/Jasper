package dev.jasper.browser.permissions;

import java.time.Instant;
import java.util.Objects;

/**
 * A stored decision: what the user chose for one permission of one site.
 *
 * @param origin the site, as {@link Origins#of} gives it
 * @param permission the permission
 * @param decision what the user chose
 * @param created when the site's permission was first stored, to the millisecond
 * @param updated when it was last stored
 */
public record Grant(String origin, Permission permission, Decision decision, Instant created, Instant updated) {
    /** Checks that every part is given. */
    public Grant {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(created, "created");
        Objects.requireNonNull(updated, "updated");
    }
}
