package dev.jasper.sdk.ui;

import dev.jasper.sdk.RestoreState;
import java.util.Optional;

/** Stable document identity, scoped to the calling plugin, initial literal title, and how the tab comes back.
 * @param key stable nonblank identity; reopening selects the existing content
 * @param title initial literal tab title
 * @param restore how the tab comes back when Jasper restores its windows (since 0.21.0); empty, and the tab is not saved
 * @since 0.8.5
 */
public record ContentSpec(String key, String title, Optional<RestoreState> restore) {
    /** Validates required values. */
    public ContentSpec {
        if(key==null||key.isBlank())throw new IllegalArgumentException("Content needs a stable key");
        java.util.Objects.requireNonNull(title,"title");
        java.util.Objects.requireNonNull(restore,"restore");
    }

    /**
     * Content that does not come back when Jasper restores its windows.
     *
     * @param key stable nonblank identity
     * @param title initial literal tab title
     */
    public ContentSpec(String key, String title) { this(key, title, Optional.empty()); }

    /**
     * This content, coming back as {@code state} says when Jasper restores its windows.
     *
     * @param state the restorer kind and what it rebuilds the tab from
     * @return the spec
     * @since 0.21.0
     */
    public ContentSpec withRestore(RestoreState state) { return new ContentSpec(key, title, Optional.of(state)); }
}
