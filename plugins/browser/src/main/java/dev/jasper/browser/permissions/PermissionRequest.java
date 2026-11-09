package dev.jasper.browser.permissions;

import java.util.Objects;

/**
 * A site's request for a permission, which its stored decision answers or a prompt asks the user about.
 *
 * @param origin the site that asks, as {@link Origins#of} gives it
 * @param permission what it asks for
 */
public record PermissionRequest(String origin, Permission permission) {
    /** Checks that both parts are given. */
    public PermissionRequest {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(permission, "permission");
    }
}
