package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerScopeTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    final List<String> events = new ArrayList<>();
    final List<ServerRegistry.View> views = new ArrayList<>();
    final WindowHandle window = new WindowHandle() {
        final UUID id = UUID.randomUUID();
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() { return List.of(); }
        @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
        @Override public boolean isActive() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public boolean isMain() { return true; }
        @Override public void toFront() { }
    };
    final PaletteQuery query = new PaletteQuery(window, Optional.empty(), 50, true);
    final FileServerScope scope = new FileServerScope(() -> views, HOME, (w, id) -> events.add("toggle " + id), (w, id) -> events.add("edit " + id),
        "dev.jasper.fileserver.servers");

    ServerRegistry.View view(String name, ServerState state, boolean temporary) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, Protocol.HTTP, HOME.resolve("work"), Bind.LOOPBACK, 8080, Optional.empty(),
            false, Login.ANONYMOUS, List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, temporary, state, List.of(), Optional.empty(), false);
        views.add(view);
        return view;
    }

    @Test void listsServersWithTheirStateAndRunsTheChosenVerb() {
        var docs = view("docs", ServerState.RUNNING, false);
        var firmware = view("firmware", ServerState.STOPPED, false);
        var quick = view("work (HTTP)", ServerState.STOPPING, true);
        assertThat(scope.spec().id()).isEqualTo(FileServerScope.ID);
        assertThat(scope.spec().verbs()).containsExactly(FileServerScope.TOGGLE, FileServerScope.EDIT);
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.fileserver.servers");
        assertThat(scope.spec().itemNoun()).as("spec 14.5").isEqualTo("file server");
        assertThat(scope.spec().hue()).isEqualTo(dev.jasper.sdk.ui.Nodes.Hue.NONE);
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).containsExactly("docs", "firmware", "work (HTTP)");
        assertThat(rows.getFirst().detail()).contains("HTTP :8080 · ~" + File.separator + "work");
        assertThat(rows.getFirst().tag()).contains("Running");
        assertThat(scope.search("FIRM", query).rows()).extracting(PaletteRow::title).containsExactly("firmware");
        assertThat(scope.available(rows.get(2), FileServerScope.TOGGLE, query)).as("stopping").isFalse();
        assertThat(scope.available(rows.get(2), FileServerScope.EDIT, query)).isTrue();
        scope.execute(rows.get(1), FileServerScope.TOGGLE, query);
        scope.execute(rows.get(0), FileServerScope.EDIT, query);
        assertThat(events).containsExactly("toggle " + firmware.definition().id(), "edit " + docs.definition().id());
        int[] changes = {0};
        var subscription = scope.onChanged(() -> changes[0]++);
        scope.changed();
        subscription.close();
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
        assertThat(quick.temporary()).isTrue();
    }
}
