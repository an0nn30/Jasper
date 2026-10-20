package dev.jasper.app.cygwin;

import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Read-only readiness inspection; package database and user root are never rewritten. */
public record CygwinState(Phase phase, String diagnostic, String seed) {
    public enum Phase { ABSENT, INITIALIZING, READY, DAMAGED, FOREIGN }
    public static CygwinState inspect(CygwinLayout layout) {
        if (!Files.exists(layout.base(), LinkOption.NOFOLLOW_LINKS)) return new CygwinState(Phase.ABSENT, "Not installed", "");
        try { layout.requireOwned(); }
        catch (Exception failure) { return new CygwinState(Phase.FOREIGN, failure.getMessage(), ""); }
        try {
            if (!Files.exists(layout.base().resolve("state.json"), LinkOption.NOFOLLOW_LINKS) && layout.emptyInitialization())
                return new CygwinState(Phase.INITIALIZING, "Initialization was not completed", "unselected");
            var state = CygwinLayout.read(layout.base().resolve("state.json"));
            String seed = CygwinJson.string(state, "seed");
            String phase = CygwinJson.string(state, "phase");
            if (phase.equals("INITIALIZING")) return new CygwinState(Phase.INITIALIZING, "Initialization was not completed", seed);
            if (!phase.equals("READY")) throw new java.io.IOException("Unknown Cygwin state");
            if (!Files.isRegularFile(layout.root().resolve("etc/setup/installed.db"))
                    || !Files.isRegularFile(layout.root().resolve("bin/bash.exe"))
                    || !Files.isRegularFile(layout.root().resolve("bin/cygpath.exe"))
                    || !Files.isRegularFile(layout.root().resolve("bin/cygwin1.dll")))
                return new CygwinState(Phase.DAMAGED, "Cygwin Bash or its package database is missing; use Manage Cygwin packages", seed);
            return new CygwinState(Phase.READY, "Ready", seed);
        } catch (Exception failure) { return new CygwinState(Phase.DAMAGED, "Invalid Cygwin state: " + failure.getMessage(), ""); }
    }
}
