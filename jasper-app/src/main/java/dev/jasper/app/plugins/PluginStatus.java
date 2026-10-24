package dev.jasper.app.plugins;

/** What became of one discovered plugin, for the log now and the Plugins manager later. */
record PluginStatus(String id, String name, String version, PluginCandidate.Origin origin, State state, String reason) {
    enum State { ACTIVE, DISABLED, NEEDS_CONSENT, SKIPPED, FAILED }

    /** How the reason of a copy that lost to another copy of the same plugin begins. */
    static final String SUPERSEDED = "superseded by";
    /** Why a plugin the user marked for removal is disabled. */
    static final String MARKED_FOR_REMOVAL = "marked for removal";
    /** Why safe mode disables a user's plugin. */
    static final String SAFE_MODE = "safe mode";
    /** Why safe mode disables a presentation provider. */
    static final String SAFE_MODE_LOOK = "safe mode uses the standard look";
    /** How the reason of a provider whose kit is invalid begins. */
    static final String INVALID = "Presentation invalid: ";
    /** Why the attempt record skips a look. */
    static final String ATTEMPT_SKIP = "did not finish starting with this look last time";
    /** Why a provider whose kit was blamed at start is skipped (spec 4.7). */
    static final String LOOK_FAILED = "its look could not be installed; Jasper uses its standard look";
    /** Why a provider that needs another provider is skipped: only one of them can load. */
    static final String NEEDS_LOOK = "requires another look's plugin; one look can be active at a time";
    private static final String LOOK_ACTIVE = " look is active";

    /** Why a provider that lost to {@code winner} is skipped. */
    static String lookActive(String winner) { return "the " + winner + LOOK_ACTIVE; }

    static PluginStatus of(PluginCandidate candidate, State state, String reason) {
        return new PluginStatus(candidate.id(), candidate.descriptor().name(), candidate.descriptor().version().toString(),
            candidate.origin(), state, reason);
    }

    /** Whether this copy lost to another copy of the same plugin, which has a status of its own. */
    boolean superseded() { return state == State.SKIPPED && reason.startsWith(SUPERSEDED); }

    /** Whether this status posts no plugin problem: a superseded copy, a provider that lost the look, or a look the attempt record skipped. */
    boolean quiet() {
        return superseded() || state == State.SKIPPED && (reason.startsWith("the ") && reason.endsWith(LOOK_ACTIVE) || reason.equals(ATTEMPT_SKIP));
    }

    String formatted() {
        return id + " " + version + " [" + origin + "] " + state + (reason.isEmpty() ? "" : ": " + reason);
    }
}
