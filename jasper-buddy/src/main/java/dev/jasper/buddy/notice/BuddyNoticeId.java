package dev.jasper.buddy.notice;

/**
 * Source-qualified identity. Equality uses both components; a source is not normalized or trimmed.
 * The key must keep stable equality/hashCode and should not retain UI objects.
 * @param source nonnull, nonblank producer namespace
 * @param key nonnull identity unique within that source for the intended notice lifetime
 */
public record BuddyNoticeId(String source, Object key) {
    public BuddyNoticeId {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(key, "key");
        if (source.isBlank()) throw new IllegalArgumentException("A notice source must not be blank");
    }
}
