package dev.jasper.buddy.config;

/** Immutable host-supplied appearance and callbacks. Build performs no I/O; callbacks execute on the EDT. */
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
    public static Builder builder(java.awt.Font font) { return new Builder(font); }
    public Builder toBuilder() {
        return new Builder(primaryFont).dark(dark).initialPosition(initialPosition)
            .positionChanged(positionChanged).activateHost(activateHost).toggleRequested(toggleRequested);
    }
    public java.awt.Font primaryFont() { return primaryFont; }
    public boolean dark() { return dark; }
    public java.util.Optional<BuddyPosition> initialPosition() { return java.util.Optional.ofNullable(initialPosition); }
    public java.util.function.Consumer<BuddyPosition> positionChanged() { return positionChanged; }
    public Runnable activateHost() { return activateHost; }
    public Runnable toggleRequested() { return toggleRequested; }
    public static final class Builder {
        private java.awt.Font primaryFont;
        private boolean dark = true;
        private BuddyPosition initialPosition;
        private java.util.function.Consumer<BuddyPosition> positionChanged = position -> {};
        private Runnable activateHost = () -> {}, toggleRequested = () -> {};
        private Builder(java.awt.Font font) { primaryFont = java.util.Objects.requireNonNull(font, "primaryFont"); }
        public Builder primaryFont(java.awt.Font value) { primaryFont = java.util.Objects.requireNonNull(value); return this; }
        public Builder dark(boolean value) { dark = value; return this; }
        public Builder initialPosition(BuddyPosition value) { initialPosition = value; return this; }
        public Builder positionChanged(java.util.function.Consumer<BuddyPosition> value) {
            positionChanged = java.util.Objects.requireNonNull(value); return this;
        }
        public Builder activateHost(Runnable value) { activateHost = java.util.Objects.requireNonNull(value); return this; }
        public Builder toggleRequested(Runnable value) { toggleRequested = java.util.Objects.requireNonNull(value); return this; }
        public BuddyOptions build() { return new BuddyOptions(this); }
    }
}
