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

    @Test void supplementaryDirectoryNamesKeepAllDescendantsBehindConflictDecision() throws Exception {
        try(var store=new TransferStore(root)) {
            String name="folder-\uD83D\uDE00";var job=store.create(request());
            var directory=new FileEntry(name,FileEntry.Kind.DIRECTORY,0,1000,0755,"");
            store.discover(job,List.of(new TransferStore.Discovered(name,"/source/"+name,"/destination/"+name,directory),
                new TransferStore.Discovered(name+"/child","/source/"+name+"/child","/destination/"+name+"/child",file("child",3))));
            var parent=store.entries(job,0,10).getFirst();var child=store.entries(job,0,10).get(1);
            store.attention(parent.id(),"Destination already exists");
            assertThat(store.pending(job,10)).isEmpty();
            store.renameTree(parent.id(),"/destination/renamed");
            store.decision(parent.id(),ConflictDecision.RENAME,"/destination/renamed");
            assertThat(store.entry(child.id()).target()).isEqualTo("/destination/renamed/child");
            assertThat(store.pending(job,10)).hasSize(2);
            store.skipTree(parent.id());
            assertThat(store.entry(child.id()).outcome()).isEqualTo(TransferEntry.Outcome.SKIPPED);
            assertThat(store.job(job).skippedEntries()).isEqualTo(2);
        }
    }

    @Test void localDestinationAliasesAreRejectedBeforeDispatchAndOnRename() throws Exception {
        try(var store=new TransferStore(root)) {
            for(var pair:List.of(List.of("A","a"),List.of("\u00e9","e\u0301"))) {
                var job=store.create(request());String first=pair.get(0),second=pair.get(1);
                store.discover(job,List.of(new TransferStore.Discovered(first,"/source/"+first,"/destination/"+first,file(first,3))));
                assertThatThrownBy(()->store.discover(job,List.of(new TransferStore.Discovered(second,"/source/"+second,"/destination/"+second,file(second,3)))))
                    .isInstanceOf(IOException.class).hasMessageContaining("collide");
                assertThat(store.job(job).scanned()).isFalse();
                store.discover(job,List.of(new TransferStore.Discovered("other","/source/other","/destination/other",file("other",3))));
                long other=store.entries(job,0,10).getLast().id();
                assertThatThrownBy(()->store.renameTree(other,"/destination/"+second)).isInstanceOf(IOException.class).hasMessageContaining("collide");
                assertThat(store.entry(other).target()).isEqualTo("/destination/other");
            }
        }
    }

    @Test void replayDoesNotReopenFinishedDirectoryListings() throws Exception {
        try(var store=new TransferStore(root)) {
            var job=store.create(request());var dir=new FileEntry("folder",FileEntry.Kind.DIRECTORY,0,1000,0755,"");
            var found=new TransferStore.Discovered("folder","/source/folder","/destination/folder",dir);
            store.discover(job,List.of(found),true);var frontier=store.frontier(job,10).getFirst();store.finishFrontier(frontier.id());
            store.discover(job,List.of(found),true);assertThat(store.frontier(job,10)).isEmpty();
        }
    }
    @Test void applyingRemainingPolicyLeavesDecisionsForBoundedDispatchAndHonorsType() throws Exception {
        try(var store=new TransferStore(root)) {
            var job=store.create(request());store.discover(job,List.of(discovered(0),discovered(1)));
            var rows=store.entries(job,0,10);var ordinary=rows.getFirst();var mismatch=rows.getLast();
            store.conflict(ordinary.id(),Optional.of(file("file-0",3)),"Destination already exists");
            store.conflict(mismatch.id(),Optional.of(new FileEntry("file-1",FileEntry.Kind.DIRECTORY,0,1000,0755,"")),"Destination already exists");
            store.policy(job,false,ConflictDecision.REPLACE);
            assertThat(store.entry(ordinary.id()).decision()).isEqualTo(ConflictDecision.ASK);
            assertThat(store.pending(job,1)).extracting(TransferEntry::id).containsExactly(ordinary.id());
            assertThat(store.entry(mismatch.id()).decision()).isEqualTo(ConflictDecision.ASK);
        }
    }

    @Test void publicationDecisionSurvivesReopenWithoutChangingOriginalEvidence() throws Exception {
        long entry;String original="/destination/original";
        try(var store=new TransferStore(root)) {
            var job=store.create(request());store.discover(job,List.of(new TransferStore.Discovered("file","/source/file",original,file("file",3))));entry=store.entries(job,0,1).getFirst().id();
            store.planTemporary(entry,"/destination/.partial");store.created(entry,file(".partial",3));store.publishing(entry,"a".repeat(64),TransferEntry.Publication.ATOMIC_REPLACE,Optional.of(file("original",1)));
            store.publicationDecision(entry,ConflictDecision.RENAME,"/destination/new");
        }
        try(var store=new TransferStore(root)) {
            assertThat(store.entry(entry).target()).isEqualTo(original);assertThat(store.entry(entry).phase()).isEqualTo(TransferEntry.Phase.PUBLISHING);
            assertThat(store.entry(entry).digest()).isEqualTo("a".repeat(64));assertThat(store.entry(entry).expectedTarget().orElseThrow().size()).isEqualTo(1);
            assertThat(store.publicationDecision(entry).orElseThrow().target()).isEqualTo("/destination/new");
        }
    }
    @Test void pendingCleanupExposesItsOwnedPathAndFailureInFileDetails() throws Exception {
        try(var store=new TransferStore(root)) {
            var job=store.create(request());store.discover(job,List.of(discovered(0)));long entry=store.entries(job,0,1).getFirst().id();
            store.planTemporary(entry,"/destination/.owned-partial");store.cleanup(entry,"Permission denied");
            assertThat(store.entries(job,0,10).getFirst().error()).contains("/destination/.owned-partial","Permission denied");
            store.cleaned(entry);assertThat(store.entry(entry).error()).doesNotContain("Permission denied");
        }
    }
    @Test void theFirstEntryNeedingAttentionIsFoundBeyondTheFirstPage() throws Exception {
        try (var db = new TransferStore(root)) {
            UUID id = db.create(request());
            for (int offset=0;offset<300;offset+=150) {
                var batch = new ArrayList<TransferStore.Discovered>();
                for (int i=offset;i<offset+150;i++) batch.add(discovered(i));
                db.discover(id,batch);
            }
            assertThat(db.firstAttention(id)).isEmpty();
            var entries = new ArrayList<TransferEntry>();
            for (long offset=0;offset<300;offset+=200) entries.addAll(db.entries(id,offset,200));
            db.outcome(entries.get(10).id(), TransferEntry.Outcome.SKIPPED, "Skipped");
            db.conflict(entries.get(250).id(), Optional.of(file("file-250",3)), "Destination already exists");
            db.conflict(entries.get(280).id(), Optional.of(file("file-280",3)), "Destination already exists");
            assertThat(db.firstAttention(id)).map(TransferEntry::id).contains(entries.get(250).id());
            assertThat(db.firstAttention(id).orElseThrow().error()).isEqualTo("Destination already exists");
        }
    }
    @Test void anExistingItemsChoiceBecomesTheJobsPolicies() throws Exception {
        try (var db = new TransferStore(root)) {
            UUID asked = db.create(request());
            assertThat(db.policy(asked, false)).isEqualTo(ConflictDecision.ASK);
            assertThat(db.policy(asked, true)).isEqualTo(ConflictDecision.ASK);
            UUID replace = db.create(request().withExisting(ConflictDecision.REPLACE));
            assertThat(db.policy(replace, false)).isEqualTo(ConflictDecision.REPLACE);
            assertThat(db.policy(replace, true)).isEqualTo(ConflictDecision.MERGE);
            UUID skip = db.create(request().withExisting(ConflictDecision.SKIP));
            assertThat(db.policy(skip, false)).isEqualTo(ConflictDecision.SKIP);
            assertThat(db.policy(skip, true)).isEqualTo(ConflictDecision.MERGE);
            assertThat(db.request(replace).existing()).as("the policy lives on the job, not the encoded request").isEqualTo(ConflictDecision.ASK);
        }
        assertThatThrownBy(() -> request().withExisting(ConflictDecision.RENAME)).isInstanceOf(IllegalArgumentException.class);
    }

}
