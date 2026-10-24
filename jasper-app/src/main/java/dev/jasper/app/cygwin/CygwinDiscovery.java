package dev.jasper.app.cygwin;
import java.util.Optional;
/** Background-only discovery of Jasper's owned root; never searches PATH. */
public final class CygwinDiscovery {
    private CygwinDiscovery() { }
    public static Optional<String> bash() {
        if (!CygwinLayout.windows()) return Optional.empty();
        var layout = CygwinLayout.system();
        return CygwinState.inspect(layout).phase() == CygwinState.Phase.READY
            ? Optional.of(layout.root().resolve("bin/bash.exe").toString()) : Optional.empty();
    }
}
