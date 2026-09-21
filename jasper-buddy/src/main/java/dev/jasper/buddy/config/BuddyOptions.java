package dev.jasper.buddy.config;

/**
 * Immutable host-supplied appearance and callback references. Building is thread-independent and
 * performs no I/O; presentation invokes callbacks on the EDT. A builder is caller-confined.
 * The host owns persistence, visibility decisions and producer lifetimes.
 */
public final class BuddyOptions {
    private final java.awt.Font primaryFont;
    private final boolean dark;
    private final BuddyPosition initialPosition;
    private final java.util.function.Consumer<BuddyPosition> positionChanged;
    private final Runnable activateHost, toggleRequested;
    private BuddyOptions(Builder b) {
        primaryFont = java.util.Objects.requireNonNull(b.primaryFont, "primaryFont");
        dark = b.dark; initialPosition = b.initialPosition;
        positionChanged = java.util.Objects.requireNonNull(b.positionChanged, "positionChanged");
        activateHost = java.util.Objects.requireNonNull(b.activateHost, "activateHost");
        toggleRequested = java.util.Objects.requireNonNull(b.toggleRequested, "toggleRequested");
    }
    /** Starts with an explicit nonnull resolved font, dark mode, no saved position and inert callbacks. */
    public static Builder builder(java.awt.Font font) { return new Builder(font); }
    /** Copies every option and callback so a targeted change preserves unrelated host configuration. */
    public Builder toBuilder() {
        return new Builder(primaryFont).dark(dark).initialPosition(initialPosition)
            .positionChanged(positionChanged).activateHost(activateHost).toggleRequested(toggleRequested);
    }
    /** Returns the resolved host font from which presentation derives fixed styles and point sizes. Changing this font's point size alone is not a global scale setting. */
    public java.awt.Font primaryFont() { return primaryFont; }
    /** Returns the explicit notice-card material mode; the menu bubble remains dark. No look-and-feel vendor detection occurs in this library. */
    public boolean dark() { return dark; }
    /** Returns the optional initial location; applied and monitor-clamped only on first native realization. */
    public java.util.Optional<BuddyPosition> initialPosition() { return java.util.Optional.ofNullable(initialPosition); }
    /** Returns the nonnull drag-end callback. It runs once per completed drag on EDT, not per pointer frame. */
    public java.util.function.Consumer<BuddyPosition> positionChanged() { return positionChanged; }
    /** Returns the nonnull EDT host-activation request, used on double click. */
    public Runnable activateHost() { return activateHost; }
    /** Returns the nonnull EDT visibility-toggle request used by the menu; the host applies its own policy. */
    public Runnable toggleRequested() { return toggleRequested; }
    /** Mutable caller-confined builder. Values are copied by build; no native resource is acquired. */
    public static final class Builder {
        private java.awt.Font primaryFont;
        private boolean dark = true;
        private BuddyPosition initialPosition;
        private java.util.function.Consumer<BuddyPosition> positionChanged = position -> {};
        private Runnable activateHost = () -> {}, toggleRequested = () -> {};
        private Builder(java.awt.Font font) { primaryFont = java.util.Objects.requireNonNull(font, "primaryFont"); }
        /** Uses a nonnull resolved host font; does not discover an OS font. */
        public Builder primaryFont(java.awt.Font value) { primaryFont = java.util.Objects.requireNonNull(value); return this; }
        /** Selects dark or light notice-card material independently of the installed look and feel. */
        public Builder dark(boolean value) { dark = value; return this; }
        /** Sets initial screen coordinates; null clears them and chooses the default corner. */
        public Builder initialPosition(BuddyPosition value) { initialPosition = value; return this; }
        /** Sets a nonnull EDT drag-end callback; the host owns persistence and failure reporting. */
        public Builder positionChanged(java.util.function.Consumer<BuddyPosition> value) {
            positionChanged = java.util.Objects.requireNonNull(value); return this;
        }
        /** Sets a nonnull EDT activation request. Keep callback work nonblocking. */
        public Builder activateHost(Runnable value) { activateHost = java.util.Objects.requireNonNull(value); return this; }
        /** Sets a nonnull EDT toggle request. Keep callback work nonblocking. */
        public Builder toggleRequested(Runnable value) { toggleRequested = java.util.Objects.requireNonNull(value); return this; }
        /** Returns validated immutable options, preserving all current fields and acquiring no resources. */
        public BuddyOptions build() { return new BuddyOptions(this); }
    }
}
