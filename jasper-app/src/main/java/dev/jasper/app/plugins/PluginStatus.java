package dev.jasper.app.plugins;

/** What became of one discovered plugin, for the log now and the Plugins manager later. */
record PluginStatus(String id, String name, String version, PluginCandidate.Origin origin, State state, String reason) {
    enum State { ACTIVE, DISABLED, NEEDS_CONSENT, SKIPPED, FAILED }

    static PluginStatus of(PluginCandidate candidate, State state, String reason) {
        return new PluginStatus(candidate.id(), candidate.descriptor().name(), candidate.descriptor().version().toString(),
            candidate.origin(), state, reason);
    }

    String formatted() {
        return id + " " + version + " [" + origin + "] " + state + (reason.isEmpty() ? "" : ": " + reason);
    }
}
