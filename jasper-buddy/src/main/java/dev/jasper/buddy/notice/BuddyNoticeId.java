package dev.jasper.buddy.notice;

/** Source-qualified identity. The key must keep stable equality/hashCode and should not retain UI objects. */
public record BuddyNoticeId(String source, Object key) {
    public BuddyNoticeId {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(key, "key");
        if (source.isBlank()) throw new IllegalArgumentException("A notice source must not be blank");
    }
}
