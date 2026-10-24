package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.testsupport.LogRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 13: saved within a second of a change, at once when a window closes, off the EDT, in order. */
class WorkspaceStoreTest {
    /** What the store logs when a snapshot cannot be taken. */
    private static final String SNAPSHOT_FAILED = "Could not take the workspace's snapshot to save";
    @TempDir Path dir;
    /** The saves changes scheduled: each runs when the test says its second has passed. */
    private final Queue<Runnable> due = new ArrayDeque<>();
    private final Queue<Duration> delays = new ArrayDeque<>();
    private int cancelled;
    /** The writes asked for, run by the test thread, never the EDT. */
    private final Queue<Runnable> writes = new ArrayDeque<>();
    private final Queue<Path> setAside = new ArrayDeque<>();
    private Optional<WorkspaceFile.Snapshot> current = Optional.empty();
    /** Thrown by the next snapshot when set, as a window that cannot say what it holds would. */
    private RuntimeException failing;
    /** Thrown by the next snapshot when set: an {@code Error}, as a bug deep in a window's layout would throw. */
    private Error breaking;
    /** Refuses the next write when set, as an executor that is shutting down does. */
    private boolean refuseNext;
    private WorkspaceStore store;
    /** What the store logged during the test: its warnings are asserted here, never printed. */
    private LogRecorder storeLog;
    /** What the codec logged while it read the bad files some tests feed the store, recorded and never printed. */
    private LogRecorder codecLog;

    @BeforeEach void record() {
        storeLog = LogRecorder.of(WorkspaceStore.class.getName());
        codecLog = LogRecorder.of(WorkspaceFile.class.getName());
    }

    @AfterEach void stopRecording() {
        storeLog.close();
        codecLog.close();
    }

    /** What the store logs when a write to the workspace file does not happen. */
    private String saveFailed() { return "Could not save the workspace to " + file(); }

    private Path file() { return dir.resolve("workspace.toml"); }

    private void store() throws Exception {
        edt(() -> store = new WorkspaceStore(file(), () -> {
            if (failing != null) throw failing;
            if (breaking != null) throw breaking;
            return current;
        }, (delay, task) -> {
            delays.add(delay);
            due.add(task);
            return () -> { cancelled++; due.remove(task); };
        }, task -> {
            if (refuseNext) { refuseNext = false; throw new java.util.concurrent.RejectedExecutionException("shutting down"); }
            writes.add(task);
        }, setAside::add));
    }

    /** One parent tab of its own id holding {@code tab} alone. */
    private static ParentTabs only(TabId tab) {
        return ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.of(tab)));
    }

    /** A workspace of one main window holding one terminal tab in {@code directory}. */
    private static WorkspaceFile.Snapshot workspace(String directory) {
        TabId tab = TabId.random();
        return new WorkspaceFile.Snapshot(java.util.List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN, Optional.empty(), 0,
            only(tab), Map.of(tab, new WorkspaceFile.TabRecord.Terminal("", directory, "")))));
    }

    private void write() {
        while (!writes.isEmpty()) {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            writes.remove().run();
        }
    }

    private WorkspaceFile.Snapshot onDisk() {
        return ((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(file())).snapshot();
    }

    @Test void aChangeIsSavedOneSecondLaterWithEveryChangeMadeMeanwhile() throws Exception {
        store();
        current = Optional.of(workspace("/work/one"));
        edt(() -> { store.changed(); store.changed(); });
        assertThat(delays).containsExactly(Duration.ofSeconds(1));
        current = Optional.of(workspace("/work/two"));
        edt(() -> store.changed());
        assertThat(due).as("one save for every change within the second").hasSize(1);
        assertThat(file()).doesNotExist();
        edt(() -> due.remove().run());
        assertThat(file()).as("written off the EDT").doesNotExist();
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        edt(() -> store.changed());
        assertThat(due).as("the next change schedules the next save").hasSize(1);
    }

    @Test void aClosingWindowSavesAtOnceAndANothingChangedSaveWritesNothing() throws Exception {
        store();
        current = Optional.of(workspace("/work"));
        edt(() -> { store.changed(); store.save(); });
        assertThat(cancelled).as("the scheduled save is not needed").isEqualTo(1);
        assertThat(due).isEmpty();
        edt(() -> store.save());
        assertThat(writes).as("the same workspace is not written twice").hasSize(1);
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        current = Optional.empty();
        edt(() -> store.save());
        assertThat(writes).as("no main window: nothing to save").isEmpty();
    }

    /** Workspace plan 6a Task 5's review: a save with nothing to save yet leaves the save a change scheduled in place. */
    @Test void aSaveWithNothingToSaveKeepsTheSaveAChangeScheduled() throws Exception {
        store();
        edt(() -> { store.changed(); store.save(); });
        assertThat(cancelled).as("no main window yet: the scheduled save stays").isZero();
        assertThat(due).hasSize(1);
        failing = new IllegalStateException("a window cannot say what it holds");
        edt(() -> store.save());
        assertThat(due).as("nor does a snapshot that fails take it").hasSize(1);
        assertThat(storeLog.warnings()).as("the failed snapshot is logged").containsExactly(SNAPSHOT_FAILED);
        failing = null;
        current = Optional.of(workspace("/work"));
        edt(() -> due.remove().run());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void writesRunInTheOrderTheyWereAskedFor() throws Exception {
        store();
        current = Optional.of(workspace("/first"));
        edt(() -> store.save());
        current = Optional.of(workspace("/second"));
        edt(() -> store.save());
        assertThat(writes).hasSize(1);
        assertThat(store.written()).isNotDone();
        writes.remove().run();
        assertThat(writes).as("the second write waits for the first").hasSize(1);
        write();
        assertThat(store.written()).isDone();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    @Test void holdingSavesOnceThenNothingUntilTheWindowsAreRestoredFromWhatWasSaved() throws Exception {
        store();
        WorkspaceFile.Snapshot open = workspace("/work");
        current = Optional.of(open);
        edt(() -> store.hold());
        write();
        assertThat(onDisk()).isEqualTo(open);
        current = Optional.of(workspace("/elsewhere"));
        edt(() -> {
            assertThat(store.held()).isTrue();
            store.changed();
            store.save();
        });
        assertThat(due).isEmpty();
        assertThat(writes).as("closing the windows' tabs is not saved").isEmpty();
        Files.delete(file());
        edt(() -> {
            assertThat(store.load()).as("what this process saved last, not the file").isEqualTo(new WorkspaceFile.Loaded.Restore(open, Optional.empty()));
            assertThat(store.held()).isFalse();
            store.changed();
        });
        assertThat(due).hasSize(1);
    }

    /**
     * Workspace plan 6a Task 6's review: a hold while a save is scheduled and there is nothing to save yet leaves that save
     * to fire as a no-op, and the next change after {@link WorkspaceStore#load()} schedules one that writes once.
     */
    @Test void aSaveScheduledBeforeAHoldFiresAsANoOpAndTheNextChangeAfterLoadSavesOnce() throws Exception {
        store();
        edt(() -> {
            store.changed();
            store.hold();
        });
        assertThat(store.held()).as("held although there was nothing to save").isTrue();
        assertThat(due).as("nothing to save: the hold's save leaves the scheduled one").hasSize(1);
        edt(() -> due.remove().run());
        assertThat(writes).as("it fires as a no-op while held").isEmpty();
        assertThat(file()).doesNotExist();
        current = Optional.of(workspace("/work"));
        edt(() -> {
            store.load();
            store.changed();
        });
        assertThat(due).as("the fired save left nothing pending: the next change schedules a save").hasSize(1);
        edt(() -> due.remove().run());
        assertThat(writes).as("which writes once").hasSize(1);
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        edt(() -> store.save());
        assertThat(writes).as("the same windows are not written again").isEmpty();
    }

    @Test void theFirstLoadReadsTheFileAndTellsWhatWasSetAside() throws Exception {
        store();
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Fresh(Optional.empty())));
        assertThat(setAside).as("no file: nothing set aside").isEmpty();
        WorkspaceFile.Snapshot saved = workspace("/work");
        WorkspaceFile.write(file(), saved);
        store();
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(saved, Optional.empty())));
        Files.writeString(file(), "version = 1\n[[windows]]\nrole = ");
        store();
        Path bad = dir.resolve("workspace.toml.bad");
        edt(() -> assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Fresh(Optional.of(bad))));
        assertThat(setAside).containsExactly(bad);
        assertThat(bad).exists();
        assertThat(codecLog.warnings()).as("the codec logs what it set aside").containsExactly("Set aside the unreadable workspace state " + file() + " as " + bad);
    }

    @Test void aSnapshotThatFailsCostsOnlyThatSaveAndHoldingStillHolds() throws Exception {
        store();
        failing = new IllegalStateException("a window cannot say what it holds");
        edt(() -> {
            store.save();
            store.hold();
            assertThat(store.held()).as("held although its save failed").isTrue();
        });
        assertThat(writes).isEmpty();
        assertThat(storeLog.warnings()).as("the save's failed snapshot, and the one the hold's save takes").containsExactly(SNAPSHOT_FAILED, SNAPSHOT_FAILED);
        failing = null;
        current = Optional.of(workspace("/work"));
        edt(() -> { store.load(); store.save(); });
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    /** Rulings 22, workspace plan 6a Task 5's review: an Error from a snapshot costs that save alone, logged. */
    @Test void aSnapshotThatThrowsAnErrorCostsOnlyThatSave() throws Exception {
        store();
        breaking = new AssertionError("a layout that breaks its own rule");
        edt(() -> store.save());
        assertThat(writes).isEmpty();
        assertThat(storeLog.warnings()).containsExactly(SNAPSHOT_FAILED);
        breaking = null;
        current = Optional.of(workspace("/work"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    /** Workspace plan 6a Task 5's review: a write the writer refuses is logged as a failed one is, never dropped quietly. */
    @Test void aWriteTheWriterRefusesIsLogged() throws Exception {
        store();
        refuseNext = true;
        current = Optional.of(workspace("/work"));
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        assertThat(storeLog.warnings()).as("logged with the file it was for").containsExactly(saveFailed());
        assertThat(storeLog.records().getLast().getThrown()).as("with why").hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }

    @Test void aWriteThatFailsOrIsRefusedNeverStopsTheNextOne() throws Exception {
        store();
        refuseNext = true;
        current = Optional.of(workspace("/first"));
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        assertThat(storeLog.warnings()).as("the refusal is logged").containsExactly(saveFailed());
        current = Optional.of(workspace("/second"));
        edt(() -> store.save());
        write();
        assertThat(store.written()).as("never completed exceptionally").isCompletedWithValue(null);
        assertThat(onDisk()).isEqualTo(current.get());
        assertThat(storeLog.warnings()).as("a write that worked logs nothing").hasSize(1);
        Files.delete(file());
        Files.createDirectory(file());
        current = Optional.of(workspace("/third"));
        edt(() -> store.save());
        write();
        assertThat(file()).as("a write that failed").isDirectory();
        assertThat(storeLog.warnings()).as("is logged with the file it was for").containsExactly(saveFailed(), saveFailed());
        assertThat(storeLog.records().getLast().getThrown()).as("with why").isInstanceOf(java.io.IOException.class);
        Files.delete(file());
        current = Optional.of(workspace("/fourth"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).isEqualTo(current.get());
    }

    /** A write that failed, or that the writer refused, leaves the windows unsaved: the same windows are written again. */
    @Test void aWorkspaceWhoseWriteFailedOrWasRefusedIsWrittenAgainUnchanged() throws Exception {
        store();
        current = Optional.of(workspace("/work"));
        refuseNext = true;
        edt(() -> store.save());
        assertThat(writes).as("refused").isEmpty();
        Files.createDirectory(file());
        edt(() -> store.save());
        write();
        assertThat(file()).as("that write failed").isDirectory();
        assertThat(storeLog.warnings()).as("the refusal, and the failed write").containsExactly(saveFailed(), saveFailed());
        Files.delete(file());
        edt(() -> store.save());
        assertThat(writes).as("the same windows, still unsaved, are written again").hasSize(1);
        write();
        assertThat(onDisk()).isEqualTo(current.get());
        edt(() -> store.save());
        assertThat(writes).as("written once they are, not again").isEmpty();
    }

    @Test void windowsThatDidNotAllComeBackAreKeptAsTheBadFileAndTold() throws Exception {
        store();
        WorkspaceFile.Snapshot loaded = workspace("/work");
        WorkspaceFile.write(file(), loaded);
        edt(() -> {
            assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(loaded, Optional.empty()));
            store.setAside(loaded);
        });
        Path bad = dir.resolve("workspace.toml.bad");
        assertThat(setAside).containsExactly(bad);
        write();
        assertThat(((WorkspaceFile.Loaded.Restore) WorkspaceFile.read(bad)).snapshot()).isEqualTo(loaded);
        current = Optional.of(workspace("/fresh"));
        edt(() -> store.save());
        write();
        assertThat(onDisk()).as("the next save writes the windows as they are").isEqualTo(current.get());
    }

    /** Workspace plan 2's keptAside: a file whose reader dropped a window is kept aside as it was, and told once. */
    @Test void aFileThatDroppedAWindowOnReadingIsKeptAsideAsItWasAndToldOnce() throws Exception {
        WorkspaceFile.Snapshot kept = workspace("/work");
        TabId away = TabId.random();
        WorkspaceFile.write(file(), new WorkspaceFile.Snapshot(java.util.List.of(kept.main(), new WorkspaceFile.WindowRecord(
            WorkspaceFile.Role.CHILD, Optional.empty(), 0, only(away), Map.of(away, new WorkspaceFile.TabRecord.Terminal("", "/away", ""))))));
        String original = Files.readString(file()).replace("role = \"full\"", "role = \"side\"");
        Files.writeString(file(), original);
        store();
        Path bad = dir.resolve("workspace.toml.bad");
        edt(() -> {
            assertThat(store.load()).isEqualTo(new WorkspaceFile.Loaded.Restore(kept, Optional.of(bad)));
            store.setAside(kept);
        });
        write();
        assertThat(setAside).as("told once, for the notice").containsExactly(bad);
        assertThat(Files.readString(bad)).as("the original text, with the window the reader dropped").isEqualTo(original);
        assertThat(codecLog.warnings()).as("the codec logs the copy it kept").contains("Kept a copy of the workspace state " + file() + " that dropped entries as " + bad);
    }

    @Test void theStoreBelongsToTheEdt() throws Exception {
        store();
        assertThatThrownBy(() -> store.changed()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.load()).isInstanceOf(IllegalStateException.class);
    }
}
