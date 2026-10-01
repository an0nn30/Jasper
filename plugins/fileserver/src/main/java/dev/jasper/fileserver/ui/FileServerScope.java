package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** The palette's "File Servers" scope: every server with its state; Enter starts or stops it. Searches do no I/O. */
public final class FileServerScope implements PaletteScope {
    public static final String ID = "dev.jasper.fileserver.scope";
    public static final PaletteVerb TOGGLE = new PaletteVerb("toggle", "Start or Stop");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit");

    private final Supplier<List<ServerRegistry.View>> servers;
    private final Path home;
    private final BiConsumer<WindowHandle, UUID> toggle, edit;
    private final String shortcutActionId;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public FileServerScope(Supplier<List<ServerRegistry.View>> servers, Path home, BiConsumer<WindowHandle, UUID> toggle,
                           BiConsumer<WindowHandle, UUID> edit, String shortcutActionId) {
        this.servers = servers; this.home = home; this.toggle = toggle; this.edit = edit; this.shortcutActionId = shortcutActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "File Servers", "Search file servers", List.of(TOGGLE, EDIT))
            .withDescription("Start or stop a file server").withShortcutActionId(shortcutActionId).withInRecents(false);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        var rows = new ArrayList<PaletteRow>();
        for (ServerRegistry.View view : servers.get()) {
            ServerDefinition definition = view.definition();
            String detail = definition.protocol().label() + " :" + definition.port() + " · " + ServerRows.folder(definition.folder(), home);
            if (!needle.isEmpty() && !(definition.name() + " " + detail).toLowerCase(Locale.ROOT).contains(needle)) continue;
            rows.add(PaletteRow.of("server." + definition.id(), definition.name()).withDetail(detail).withTag(ServerRows.tag(view.state()))
                .withToken(definition.id()));
            if (rows.size() == PaletteResults.MAX_ROWS) break;
        }
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        Optional<ServerRegistry.View> view = view(row);
        if (view.isEmpty()) return false;
        if (verb.equals(EDIT)) return true;
        return view.get().state().phase() != Phase.STOPPING;
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        view(row).ifPresent(view -> {
            if (verb.equals(EDIT)) edit.accept(context.window(), view.definition().id());
            else toggle.accept(context.window(), view.definition().id());
        });
    }

    @Override public Subscription onChanged(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void changed() { listeners.forEach(Runnable::run); }

    private Optional<ServerRegistry.View> view(PaletteRow row) {
        if (!(row.token() instanceof UUID id)) return Optional.empty();
        return servers.get().stream().filter(view -> view.definition().id().equals(id)).findFirst();
    }
}
