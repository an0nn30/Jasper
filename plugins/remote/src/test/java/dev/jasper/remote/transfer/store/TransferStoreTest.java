package dev.jasper.remote.transfer.store;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TransferStoreTest {
    @TempDir Path root;
    static TransferRequest request() { return new TransferRequest(EndpointRef.local(), List.of("/source"), EndpointRef.local(), "/destination"); }
    static FileEntry file(String name, long size) { return new FileEntry(name, FileEntry.Kind.FILE, size, 1000, 0644, "", "file-1"); }
    static TransferStore.Discovered discovered(int i) { return new TransferStore.Discovered("file-"+i, "/source/file-"+i, "/destination/file-"+i, file("file-"+i, 3)); }

    @Test void checkpointsAndCreationEvidenceSurviveRestartAndRestorePaused() throws Exception {
        UUID job; long entry;
        try (var db = new TransferStore(root)) {
            job = db.create(request()); db.markRunning(job); db.discover(job, List.of(discovered(0)));
            entry = db.entries(job,0,50).getFirst().id();
            db.planTemporary(entry, "/destination/.jasper-opaque");
            assertThat(db.entry(entry).phase()).isEqualTo(TransferEntry.Phase.PLANNED_TEMP);
            assertThat(db.entry(entry).temporaryInfo()).isEmpty();
            db.created(entry, file(".jasper-opaque",0));
            db.checkpoint(entry, 0, 3, "a".repeat(64), file(".jasper-opaque",3));
            assertThatThrownBy(() -> db.checkpoint(entry,0,3,"a".repeat(64),file(".jasper-opaque",3))).isInstanceOf(IOException.class);
        }
        try (var db = new TransferStore(root)) {
            assertThat(db.job(job).state()).isEqualTo(TransferState.PAUSED);
            assertThat(db.entry(entry).confirmed()).isEqualTo(3);
            assertThat(db.entry(entry).temporaryInfo()).isPresent();
            assertThat(db.checkpoints(entry,0,50)).hasSize(1);
            assertThat(db.job(job).confirmedBytes()).isEqualTo(3);
        }
    }
    @Test void cancellationIntentNeverRestoresAsResumable() throws Exception {
        UUID job;
        try (var db = new TransferStore(root)) { job=db.create(request()); db.intent(job,TransferJob.Intent.CANCEL); }
        try (var db = new TransferStore(root)) {
            assertThat(db.job(job).state()).isEqualTo(TransferState.CANCELLED);
            assertThat(db.job(job).intent()).isEqualTo(TransferJob.Intent.CANCEL);
        }
    }
    @Test void discoveryReplaysIdempotentlyAndQueriesAreBounded() throws Exception {
        try (var db = new TransferStore(root)) {
            UUID id = db.create(request());
            for (int offset=0;offset<100_000;offset+=256) {
                var batch = new ArrayList<TransferStore.Discovered>();
                for (int i=offset;i<Math.min(100_000,offset+256);i++) batch.add(discovered(i));
                db.discover(id,batch);
            }
            db.discover(id,List.of(discovered(0)));
            assertThat(db.job(id).totalEntries()).isEqualTo(100_000);
            assertThat(db.job(id).totalBytes()).isEqualTo(300_000);
            assertThat(db.entries(id,0,200)).hasSize(200);
            assertThat(db.entries(id,99_999,200)).hasSize(1);
            assertThatThrownBy(() -> db.entries(id,0,100_000)).isInstanceOf(IllegalArgumentException.class);
            assertThat(db.frontier(id,50)).isEmpty();
        }
    }
    @Test void lockAndCorruptQueueNeverSilentlyResetData() throws Exception {
        try (var first = new TransferStore(root)) {
            first.create(request());
            assertThatThrownBy(() -> new TransferStore(root)).isInstanceOf(IOException.class).hasMessageContaining("another Jasper");
        }
        try (var reopened = new TransferStore(root)) { assertThat(reopened.jobs(0,50)).hasSize(1); }
        Path corrupt = root.resolve("corrupt"); Files.createDirectory(corrupt); byte[] bytes={1,2,3,4};
        Files.write(corrupt.resolve("queue.sqlite"),bytes);
        assertThatThrownBy(() -> new TransferStore(corrupt)).isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(corrupt.resolve("queue.sqlite"))).isEqualTo(bytes);
    }
    @Test void publicationIntentAndCleanupRemainVisibleAcrossRestart() throws Exception {
        UUID job; long entry;
        try (var db = new TransferStore(root)) {
            job=db.create(request()); db.discover(job,List.of(discovered(0))); entry=db.entries(job,0,1).getFirst().id();
            db.planTemporary(entry,"/destination/.temp"); db.created(entry,file(".temp",0));
            db.checkpoint(entry,0,3,"b".repeat(64),file(".temp",3));
            db.publishing(entry,"c".repeat(64),TransferEntry.Publication.LOCAL_LINK,Optional.empty());
            db.cleanup(entry,"Connection lost before cleanup");
        }
        try (var db = new TransferStore(root)) {
            assertThat(db.entry(entry).phase()).isEqualTo(TransferEntry.Phase.PUBLISHING);
            assertThat(db.entry(entry).digest()).isEqualTo("c".repeat(64));
            assertThat(db.cleanup(job,0,50)).hasSize(1);
            assertThatThrownBy(() -> db.clear(job,false)).isInstanceOf(IOException.class);
        }
    }
    @Test void killedWriterReleasesCrossProcessLockAndRestoresPaused() throws Exception {
        var locations=new LinkedHashSet<String>();
        for(Class<?> type:List.of(QueueProcessProbe.class,TransferStore.class,org.sqlite.JDBC.class,org.slf4j.Logger.class))
            locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-Djava.awt.headless=true","-cp",String.join(java.io.File.pathSeparator,locations),QueueProcessProbe.class.getName(),root.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            var reader=child.inputReader();
            var ready=java.util.concurrent.CompletableFuture.supplyAsync(() -> { try { return reader.readLine(); } catch(IOException e) { throw new java.io.UncheckedIOException(e); } });
            var id=UUID.fromString(ready.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertThatThrownBy(() -> new TransferStore(root)).isInstanceOf(IOException.class).hasMessageContaining("another Jasper");
            child.destroyForcibly(); assertThat(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try(var reopened=new TransferStore(root)) { assertThat(reopened.job(id).state()).isEqualTo(TransferState.PAUSED); }
        } finally { child.destroyForcibly(); }
    }
    @Test void newerSchemaIsPreservedAndDriverDoesNotRemainRegistered() throws Exception {
        try(var store=new TransferStore(root)) { store.create(request()); }
        var driver=new org.sqlite.JDBC();
        try(var connection=driver.connect("jdbc:sqlite:"+root.resolve("queue.sqlite"),new Properties());var statement=connection.createStatement()) { statement.execute("PRAGMA user_version=99"); }
        assertThatThrownBy(() -> new TransferStore(root)).isInstanceOf(IOException.class).hasMessageContaining("Unsupported");
        try(var connection=driver.connect("jdbc:sqlite:"+root.resolve("queue.sqlite"),new Properties());var statement=connection.createStatement();var result=statement.executeQuery("PRAGMA user_version")) { assertThat(result.getInt(1)).isEqualTo(99); }
        assertThat(Collections.list(java.sql.DriverManager.getDrivers())).noneMatch(d -> d instanceof org.sqlite.JDBC);
    }

}
