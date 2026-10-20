package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Tunnels tab: saved tunnels by name, then quick forwards, each with its mapping, state and host. Start or stop
 * (Enter), Open in browser (Cmd/Ctrl+Enter, a running local tunnel only), Edit tunnel (Shift+Enter, saved tunnels only).
 */
public final class TunnelsScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.tunnels.scope";
    public static final PaletteVerb TOGGLE = new PaletteVerb("toggle", "Start or stop");
    public static final PaletteVerb BROWSE = new PaletteVerb("browse", "Open in browser");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit tunnel…");
    static final String ERROR_ROW = "error";

    private final Supplier<List<Tunnel>> tunnels;
    private final Supplier<Optional<String>> error;
    private final Function<UUID, TunnelState> states;
    private final Function<UUID, Optional<String>> hostNames;
    private final BiConsumer<WindowHandle, Tunnel> toggle, browse, edit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public TunnelsScope(Supplier<List<Tunnel>> tunnels, Supplier<Optional<String>> error, Function<UUID, TunnelState> states,
                        Function<UUID, Optional<String>> hostNames, BiConsumer<WindowHandle, Tunnel> toggle,
                        BiConsumer<WindowHandle, Tunnel> browse, BiConsumer<WindowHandle, Tunnel> edit) {
        this.tunnels = tunnels; this.error = error; this.states = states; this.hostNames = hostNames;
        this.toggle = toggle; this.browse = browse; this.edit = edit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Tunnels", "Search tunnels", List.of(TOGGLE, BROWSE, EDIT)).withDescription("Start, stop or edit an SSH tunnel")
            .withItemNoun("tunnel");
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "tunnels.toml has errors: " + message).withEnabled(false)));
        tunnels.get().stream()
            .filter(tunnel -> TunnelRows.matches(tunnel, hostNames.apply(tunnel.hostId()).orElse(""), query))
            .sorted(Comparator.comparing(Tunnel::quick).thenComparing(tunnel -> TunnelRows.title(tunnel, states.apply(tunnel.id())).toLowerCase(Locale.ROOT)))
            .limit(Math.max(0, context.maxResults()))
            .forEach(tunnel -> {
                TunnelState state = states.apply(tunnel.id());
                rows.add(PaletteRow.of("tunnel." + tunnel.id(), TunnelRows.title(tunnel, state))
                    .withDetail(TunnelRows.mapping(tunnel, state) + " · " + TunnelRows.stateText(state))
                    .withTag(hostNames.apply(tunnel.hostId()).orElse(null)).withToken(tunnel));
            });
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof Tunnel tunnel)) return false;
        if (verb.equals(BROWSE)) return TunnelRows.browseUri(tunnel, states.apply(tunnel.id())).isPresent();
        if (verb.equals(EDIT)) return !tunnel.quick();
        return true;
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof Tunnel tunnel)) return;
        if (verb.equals(BROWSE)) browse.accept(context.window(), tunnel);
        else if (verb.equals(EDIT)) edit.accept(context.window(), tunnel);
        else toggle.accept(context.window(), tunnel);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Tells the palette the rows changed. */
    public void changed() { listeners.forEach(Runnable::run); }
}
