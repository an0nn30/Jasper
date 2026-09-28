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
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * {@code >ssh}: WSL distributions, then saved hosts. Connect (Enter) opens a distribution or connects to a host,
 * Connect in split (Cmd/Ctrl+Enter) does the same beside the current pane, Edit host (Shift+Enter) is for hosts only.
 */
public final class RemoteScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.scope";
    public static final PaletteVerb CONNECT = new PaletteVerb("connect", "Connect");
    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit host\u2026");
    static final String CONNECT_ACTION = "dev.jasper.remote.connect";
    static final String ERROR_ROW = "error";

    private final Supplier<List<RemoteHost>> hosts;
    private final Supplier<Optional<String>> error;
    private final BiConsumer<WindowHandle, RemoteHost> connect;
    private final BiConsumer<PaneHandle, RemoteHost> connectSplit;
    private final BiConsumer<WindowHandle, RemoteHost> edit;
    private final Supplier<List<ShellProfileInfo>> distros;
    private final BiConsumer<WindowHandle, ShellProfileInfo> open;
    private final BiConsumer<PaneHandle, ShellProfileInfo> openSplit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit) {
        this(hosts, error, connect, connectSplit, edit, List::of, (window, profile) -> { }, (pane, profile) -> { });
    }

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit,
                       Supplier<List<ShellProfileInfo>> distros, BiConsumer<WindowHandle, ShellProfileInfo> open,
                       BiConsumer<PaneHandle, ShellProfileInfo> openSplit) {
        this.hosts = hosts; this.error = error; this.connect = connect; this.connectSplit = connectSplit; this.edit = edit;
        this.distros = distros; this.open = open; this.openSplit = openSplit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "SSH", "Search saved hosts", List.of(CONNECT, SPLIT, EDIT))
            .withDescription("Connect to a saved SSH host").withShortcutActionId(CONNECT_ACTION);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "hosts.toml has errors: " + message).withEnabled(false)));
        List<ShellProfileInfo> matchingDistros = distros.get().stream()
            .filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL && HostRows.matches(profile, query))
            .limit(context.maxResults()).toList();
        matchingDistros.forEach(profile -> rows.add(PaletteRow.of("wsl." + profile.name(), profile.name())
            .withDetail(profile.detail().orElse(null)).withTag(HostRows.WSL).withToken(profile)));
        hosts.get().stream().filter(host -> HostRows.matches(host, query))
            .sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
            .limit(context.maxResults() - matchingDistros.size())
            .forEach(host -> rows.add(PaletteRow.of("host." + host.id(), host.name()).withDetail(host.label()).withTag(host.group().isEmpty() ? null : host.group()).withToken(host)));
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        boolean distro = row.token() instanceof ShellProfileInfo;
        if (!distro && !(row.token() instanceof RemoteHost)) return false;
        if (distro && verb.equals(EDIT)) return false;
        return !verb.equals(SPLIT) || context.target().map(PaneHandle::isOpen).orElse(false);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (row.token() instanceof ShellProfileInfo profile) {
            if (verb.equals(SPLIT)) context.target().ifPresent(pane -> openSplit.accept(pane, profile));
            else if (!verb.equals(EDIT)) open.accept(context.window(), profile);
            return;
        }
        if (!(row.token() instanceof RemoteHost host)) return;
        if (verb.equals(EDIT)) edit.accept(context.window(), host);
        else if (verb.equals(SPLIT)) context.target().ifPresent(pane -> connectSplit.accept(pane, host));
        else connect.accept(context.window(), host);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void changed() { listeners.forEach(Runnable::run); }
}
