package dev.jasper.sdk.ui;

/** Stable document identity, scoped to the calling plugin, and initial literal title.
 * @param key stable nonblank identity; reopening selects the existing content
 * @param title initial literal tab title
 * @since 0.8.5
 */
public record ContentSpec(String key, String title) {
    /** Validates required values. */
    public ContentSpec {
        if(key==null||key.isBlank())throw new IllegalArgumentException("Content needs a stable key");
        java.util.Objects.requireNonNull(title,"title");
    }
}
