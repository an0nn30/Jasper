package dev.jasper.app.layout;

import java.util.Map;
import java.util.Objects;

/**
 * How a plugin's tab comes back when the windows are restored (workspace tabs spec 13): the restorer kind its plugin
 * registered and the restore state it gave, the application's twin of the SDK's {@code RestoreState}.
 *
 * @param kind the restorer kind; not empty
 * @param state what the restorer rebuilds the tab from; copied
 */
public record TabRestore(String kind, Map<String, String> state) {
    /** Requires a kind and copies the state. */
    public TabRestore {
        Objects.requireNonNull(kind, "kind");
        if (kind.isEmpty()) throw new IllegalArgumentException("A restore names its restorer kind");
        state = Map.copyOf(state);
    }
}
