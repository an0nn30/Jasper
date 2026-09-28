package dev.jasper.app.plugins;

/** What became of one discovered plugin, for the log now and the Plugins manager later. */
record PluginStatus(String id, String name, String version, PluginCandidate.Origin origin, State state, String reason) {
    enum State { ACTIVE, DISABLED, NEEDS_CONSENT, SKIPPED, FAILED }

    /** How the reason of a copy that lost to another copy of the same plugin begins. */
    static final String SUPERSEDED = "superseded by";

    static PluginStatus of(PluginCandidate candidate, State state, String reason) {
        return new PluginStatus(candidate.id(), candidate.descriptor().name(), candidate.descriptor().version().toString(),
            candidate.origin(), state, reason);
    }

    /** Whether this copy lost to another copy of the same plugin, which has a status of its own. */
    boolean superseded() { return state == State.SKIPPED && reason.startsWith(SUPERSEDED); }

    String formatted() {
        return id + " " + version + " [" + origin + "] " + state + (reason.isEmpty() ? "" : ": " + reason);
    }
}
