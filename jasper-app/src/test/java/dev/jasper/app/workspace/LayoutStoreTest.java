package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.LayoutsFile;
import dev.jasper.app.persistence.LayoutsFile.Named;
import dev.jasper.app.persistence.ToolWindows;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full windows plan 4: the layout store keeps names valid and unique and writes every change in order. */
class LayoutStoreTest {
    @TempDir Path dir;
    private final Queue<Runnable> writes = new ArrayDeque<>();

    private Path file() { return dir.resolve("layouts.toml"); }

    private static WindowRecord record(int n) {
        TabId tab = TabId.parse("00000000-0000-4000-8000-%012d".formatted(n));
        GroupId group = GroupId.parse("00000000-0000-4000-9000-%012d".formatted(n));
        var layout = new Layout(new Group(group, List.of(tab), Optional.of(tab)), group, Optional.empty());
        var tabs = ParentTabs.of(new ParentTab(ParentTabId.parse("00000000-0000-4000-a000-%012d".formatted(n)), layout));
        return new WindowRecord(Optional.of(new UiState.Bounds(1, 2, 300, 200)), 0, tabs,
            Map.of(tab, new TabRecord.Terminal("", "/tmp/" + n, "")), ToolWindows.BUILT_IN);
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private LayoutStore store() { return new LayoutStore(file(), writes::add); }

    private void drain() { while (!writes.isEmpty()) writes.remove().run(); }

    @Test void aNameInUseIsRefusedAndNamesAreTrimmed() throws Exception {
        assertThat(LayoutStore.validName("  Work  ")).contains("Work");
        assertThat(LayoutStore.validName("   ")).isEmpty();
        assertThat(LayoutStore.validName("x".repeat(65))).isEmpty();
        assertThat(LayoutStore.validName("x".repeat(64))).isPresent();
        edt(() -> {
            LayoutStore store = store();
            assertThat(store.saveAsNew("Work", record(1))).isEqualTo(LayoutStore.Saved.SAVED);
            assertThat(store.saveAsNew("Work", record(2))).isEqualTo(LayoutStore.Saved.NAME_IN_USE);
            assertThat(store.saveAsNew("Other", record(3))).isEqualTo(LayoutStore.Saved.SAVED);
            assertThat(store.rename("Work", "Other")).isEqualTo(LayoutStore.Saved.NAME_IN_USE);
            assertThat(store.rename("Work", "Play")).isEqualTo(LayoutStore.Saved.SAVED);
            assertThat(store.rename("Missing", "Z")).isEqualTo(LayoutStore.Saved.MISSING);
            assertThat(store.named()).extracting(Named::name).containsExactly("Play", "Other");
            assertThat(store.named().getFirst().layout().bounds()).isEmpty();
            store.delete("Other");
            assertThat(store.named()).extracting(Named::name).containsExactly("Play");
        });
    }

    /** Ruling 1: a change that would take layouts.toml past its limit is refused, and nothing changes. */
    @Test void aChangePastTheFilesLimitIsRefused() throws Exception {
        var big = new WindowRecord(Optional.empty(), 0, record(1).layout(),
            Map.of(record(1).tabs().keySet().iterator().next(), new TabRecord.Terminal("t".repeat(LayoutsFile.MAX_BYTES), "/tmp", "")),
            ToolWindows.BUILT_IN);
        var changes = new AtomicInteger();
        edt(() -> {
            LayoutStore store = store();
            store.onChanged(changes::incrementAndGet);
            assertThat(store.saveAsNew("Big", big)).isEqualTo(LayoutStore.Saved.TOO_LARGE);
            assertThat(store.saveAsDefault(big)).isEqualTo(LayoutStore.Saved.TOO_LARGE);
            assertThat(store.named()).isEmpty();
            assertThat(store.defaultLayout()).isEmpty();
            assertThat(store.saveAsDefault(record(2))).isEqualTo(LayoutStore.Saved.SAVED);
        });
        assertThat(changes).hasValue(1);
    }

    @Test void resetDefaultForgetsTheSavedDefault() throws Exception {
        edt(() -> {
            LayoutStore store = store();
            store.saveAsDefault(record(1));
            assertThat(store.defaultLayout()).contains(LayoutsFile.asLayout(record(1)));
            store.resetDefault();
            assertThat(store.defaultLayout()).isEmpty();
        });
        drain();
        assertThat(LayoutsFile.read(file()).defaultLayout()).isEmpty();
        assertThat(Files.readString(file())).doesNotContain("[default]");
    }

    @Test void changesAreWrittenInOrderAndTheStoreReadsTheFileOnce() throws Exception {
        var changes = new AtomicInteger();
        edt(() -> {
            LayoutStore store = store();
            store.onChanged(changes::incrementAndGet);
            store.saveAsNew("A", record(1));
            store.saveAsNew("B", record(2));
            store.saveAsDefault(record(3));
        });
        assertThat(changes).hasValue(3);
        assertThat(file()).as("nothing runs until the writer does").doesNotExist();
        assertThat(writes).hasSize(1);
        drain();
        // The chain runs each write after the one before it; run until every one has.
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline && !(LayoutsFile.read(file()).defaultLayout().isPresent())) drain();
        var read = LayoutsFile.read(file());
        assertThat(read.named()).extracting(Named::name).containsExactly("A", "B");
        assertThat(read.defaultLayout()).contains(LayoutsFile.asLayout(record(3)));
        edt(() -> assertThat(new LayoutStore(file(), writes::add).named()).hasSize(2));
    }
}
