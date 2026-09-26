package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** {@code >ssh}: saved hosts. Connect (Enter), Connect in split (Cmd/Ctrl+Enter), Edit host (Shift+Enter). */
public final class RemoteScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.scope";
    public static final PaletteVerb CONNECT = new PaletteVerb("connect", "Connect");
    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit host…");
    static final String CONNECT_ACTION = "dev.jasper.remote.connect";
    static final String ERROR_ROW = "error";

    private final Supplier<List<RemoteHost>> hosts;
    private final Supplier<Optional<String>> error;
    private final BiConsumer<WindowHandle, RemoteHost> connect;
    private final BiConsumer<PaneHandle, RemoteHost> connectSplit;
    private final BiConsumer<WindowHandle, RemoteHost> edit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit) {
        this.hosts = hosts; this.error = error; this.connect = connect; this.connectSplit = connectSplit; this.edit = edit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "SSH", "Search saved hosts", List.of(CONNECT, SPLIT, EDIT))
            .withDescription("Connect to a saved SSH host").withShortcutActionId(CONNECT_ACTION);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "hosts.toml has errors: " + message).withEnabled(false)));
        hosts.get().stream().filter(host -> HostRows.matches(host, query))
            .sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
            .limit(context.maxResults())
            .forEach(host -> rows.add(PaletteRow.of("host." + host.id(), host.name()).withDetail(host.label()).withTag(host.group().isEmpty() ? null : host.group()).withToken(host)));
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RemoteHost)) return false;
        return !verb.equals(SPLIT) || context.target().map(PaneHandle::isOpen).orElse(false);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RemoteHost host)) return;
        if (verb.equals(EDIT)) edit.accept(context.window(), host);
        else if (verb.equals(SPLIT)) context.target().ifPresent(pane -> connectSplit.accept(pane, host));
        else connect.accept(context.window(), host);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void changed() { listeners.forEach(Runnable::run); }
}
