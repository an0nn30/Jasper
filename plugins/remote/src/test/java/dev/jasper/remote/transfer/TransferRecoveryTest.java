package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.store.TransferStore;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TransferRecoveryTest {
    @TempDir Path root;
    @org.junit.jupiter.api.BeforeEach void canonicalRoot() throws Exception { root=root.toRealPath(); }
    static String digest(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    @Test void hardLinkPublicationCrashNeverTruncatesPublishedFile() throws Exception {
        var source=Files.write(root.resolve("source"),new byte[]{1,2,3});
        var directory=Files.createDirectory(root.resolve("dest"));
        var temporary=Files.write(directory.resolve(".partial"),new byte[]{1,2,3});
        var target=directory.resolve("source");
        try(var endpoint=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            UUID job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),directory.toString()));
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),endpoint.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();db.planTemporary(id,temporary.toString());db.created(id,endpoint.stat(temporary.toString()));
            db.checkpoint(id,0,3,digest(new byte[]{1,2,3}),endpoint.stat(temporary.toString()));
            db.publishing(id,digest(new byte[]{1,2,3}),TransferEntry.Publication.LOCAL_LINK,Optional.empty());
            Files.createLink(target,temporary);
            TransferRecovery.reconcile(db,db.entry(id),endpoint,new TransferControl());
            assertThat(db.entry(id).outcome()).isEqualTo(TransferEntry.Outcome.COMPLETE);
            assertThat(Files.readAllBytes(target)).containsExactly(1,2,3);assertThat(temporary).doesNotExist();
        }
    }
    @Test void unprovenTemporaryNameIsNeverOwnedAndPathReservationsExcludeAncestors() throws Exception {
        var registry=new PathReservations();var owner=UUID.randomUUID();
        try(var lease=registry.acquire(owner,List.of(new PathReservations.Key("ssh:key:user","/data/projects",true))).orElseThrow()) {
            assertThat(registry.acquire(UUID.randomUUID(),List.of(new PathReservations.Key("ssh:key:user","/data",true)))).isEmpty();
            assertThat(registry.acquire(UUID.randomUUID(),List.of(new PathReservations.Key("ssh:key:user","/data/projects/file",false)))).isEmpty();
            assertThat(registry.acquire(UUID.randomUUID(),List.of(new PathReservations.Key("ssh:other:user","/data",true)))).isPresent();
        }
        assertThat(registry.acquire(UUID.randomUUID(),List.of(new PathReservations.Key("ssh:key:user","/data",true)))).isPresent();
    }
    @Test void copyPauseResumeValidatesEveryCheckpointAndPreservesOriginalOnConflict() throws Exception {
        byte[] bytes=new byte[9*1024*1024+123];new Random(42).nextBytes(bytes);
        var source=Files.write(root.resolve("source"),bytes);var directory=Files.createDirectory(root.resolve("dest"));var target=directory.resolve("source");
        try(var src=new LocalEndpoint();var dst=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),directory.toString()));
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),src.stat(source.toString()))));var entry=db.entries(job,0,1).getFirst();
            TransferCopy.copy(db,entry,src,dst,new TransferControl(),(done,total) -> {});
            assertThat(db.entry(entry.id()).outcome()).isEqualTo(TransferEntry.Outcome.COMPLETE);
            assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
            var second=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),directory.toString()));
            db.discover(second,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),src.stat(source.toString()))));
            assertThatThrownBy(() -> TransferCopy.copy(db,db.entries(second,0,1).getFirst(),src,dst,new TransferControl(),(done,total)->{})).isInstanceOf(TransferRecovery.Attention.class);
            assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
        }
    }
    @Test void pauseCommitsShortSegmentResumeTruncatesOnlyOwnedTailAndRejectsEditedPrefix() throws Exception {
        byte[] bytes=new byte[2*1024*1024];new Random(7).nextBytes(bytes);
        Path source=Files.write(root.resolve("source"),bytes),dir=Files.createDirectory(root.resolve("dest"));
        try(var src=new LocalEndpoint();var dst=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dir.toString()));
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),dir.resolve("source").toString(),src.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();var control=new TransferControl();
            assertThatThrownBy(()->TransferCopy.copy(db,db.entry(id),src,dst,control,(done,total)->control.request(TransferJob.Intent.PAUSE))).isInstanceOf(TransferControl.Stopped.class);
            var paused=db.entry(id);assertThat(paused.confirmed()).isEqualTo(256*1024);
            Files.write(Path.of(paused.temporary()),new byte[]{9,9,9},StandardOpenOption.APPEND);
            TransferCopy.copy(db,paused,src,dst,new TransferControl(),(done,total)->{});
            assertThat(Files.readAllBytes(dir.resolve("source"))).isEqualTo(bytes);
            Files.delete(dir.resolve("source"));
            var second=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dir.toString()));
            db.discover(second,List.of(new TransferStore.Discovered("source",source.toString(),dir.resolve("source").toString(),src.stat(source.toString()))));
            long next=db.entries(second,0,1).getFirst().id();var stop=new TransferControl();
            assertThatThrownBy(()->TransferCopy.copy(db,db.entry(next),src,dst,stop,(done,total)->stop.request(TransferJob.Intent.PAUSE))).isInstanceOf(TransferControl.Stopped.class);
            var mtime=Files.getLastModifiedTime(source);bytes[0]^=1;Files.write(source,bytes);Files.setLastModifiedTime(source,mtime);
            assertThatThrownBy(()->TransferCopy.copy(db,db.entry(next),src,dst,new TransferControl(),(done,total)->{})).isInstanceOf(TransferRecovery.Attention.class).hasMessageContaining("content changed");
            assertThat(dir.resolve("source")).doesNotExist();
        }
    }
    @Test void cancellationOfUnpublishedIntentDeletesOnlyKnownPartialWithoutPublishing() throws Exception {
        Path source=Files.write(root.resolve("source"),new byte[]{4}),temp=Files.write(root.resolve(".partial"),new byte[]{4}),target=root.resolve("target");
        try(var endpoint=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),root.toString()));
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),endpoint.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();db.planTemporary(id,temp.toString());db.created(id,endpoint.stat(temp.toString()));db.checkpoint(id,0,1,digest(new byte[]{4}),endpoint.stat(temp.toString()));
            db.publishing(id,digest(new byte[]{4}),TransferEntry.Publication.LOCAL_LINK,Optional.empty());
            TransferRecovery.cleanup(db,db.entry(id),endpoint,new TransferControl());
            assertThat(target).doesNotExist();assertThat(temp).doesNotExist();assertThat(source).exists();
        }
    }

    @Test void replacedPartialAndUnrelatedFinalAreNeverTruncatedOrDeleted() throws Exception {
        Path source=Files.write(root.resolve("source"),new byte[]{1,2,3}),temp=Files.write(root.resolve(".partial"),new byte[]{1,2,3}),target=root.resolve("target");
        try(var endpoint=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),root.toString()));
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),endpoint.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();db.planTemporary(id,temp.toString());db.created(id,endpoint.stat(temp.toString()));db.checkpoint(id,0,3,digest(new byte[]{1,2,3}),endpoint.stat(temp.toString()));
            Path replacement=Files.write(root.resolve("replacement"),new byte[]{9,9,9,9});Files.move(replacement,temp,StandardCopyOption.REPLACE_EXISTING);
            assertThatThrownBy(()->TransferRecovery.validatePrefix(db,db.entry(id),endpoint,endpoint,new TransferControl(),TransferRecovery.sha())).isInstanceOf(TransferRecovery.Attention.class);
            assertThatThrownBy(()->TransferRecovery.cleanup(db,db.entry(id),endpoint,new TransferControl())).isInstanceOf(TransferRecovery.Attention.class);
            assertThat(Files.readAllBytes(temp)).containsExactly(9,9,9,9);
            db.publishing(id,digest(new byte[]{1,2,3}),TransferEntry.Publication.LOCAL_LINK,Optional.empty());Files.write(target,new byte[]{8,8,8});
            assertThatThrownBy(()->TransferRecovery.reconcile(db,db.entry(id),endpoint,new TransferControl())).isInstanceOf(TransferRecovery.Attention.class);
            assertThat(Files.readAllBytes(target)).containsExactly(8,8,8);assertThat(Files.readAllBytes(temp)).containsExactly(9,9,9,9);
        }
    }

    @Test void unsupportedMetadataPublishesContentAndRecordsAVisibleWarning() throws Exception {
        Path source=Files.write(root.resolve("source"),new byte[]{1,2,3}),dir=Files.createDirectory(root.resolve("dest"));
        try(var src=new LocalEndpoint();var dst=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            FileEndpoint unsupported=(FileEndpoint)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileEndpoint.class},(proxy,method,args)-> {
                if(method.getName().equals("metadata"))throw new java.io.IOException("Permissions unsupported");
                try{return method.invoke(dst,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
            });
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dir.toString()));db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),dir.resolve("source").toString(),src.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();TransferCopy.copy(db,db.entry(id),src,unsupported,new TransferControl(),(done,total)->{});
            assertThat(db.entry(id).outcome()).isEqualTo(TransferEntry.Outcome.COMPLETE);assertThat(db.entry(id).error()).contains("Metadata warning");assertThat(db.job(job).metadataWarnings()).isEqualTo(1);assertThat(Files.readAllBytes(dir.resolve("source"))).containsExactly(1,2,3);
        }
    }

}
