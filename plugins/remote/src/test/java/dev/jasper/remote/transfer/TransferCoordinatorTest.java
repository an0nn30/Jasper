package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TransferCoordinatorTest {
    @TempDir Path root;
    @Test void folderTransferPreservesEmptyDirectoriesLinksAndUsesBoundedQueue() throws Exception {
        root=root.toRealPath();Path source=Files.createDirectory(root.resolve("source")),dest=Files.createDirectory(root.resolve("dest"));
        Files.createDirectory(source.resolve("empty"));Files.createDirectory(source.resolve("nested"));
        Files.writeString(source.resolve("nested/file"),"binary\0payload");Files.createSymbolicLink(source.resolve("link"),Path.of("nested/file"));
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try {
                var id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(5,TimeUnit.SECONDS);
                var job=await(coordinator,id,TransferState.COMPLETED);
                assertThat(job.totalEntries()).isEqualTo(5);assertThat(Files.readString(dest.resolve("source/nested/file"))).isEqualTo("binary\0payload");
                assertThat(dest.resolve("source/empty")).isDirectory();assertThat(Files.readSymbolicLink(dest.resolve("source/link"))).isEqualTo(Path.of("nested/file"));
                assertThat(coordinator.maxObservedCopies()).isLessThanOrEqualTo(2);
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }
    @Test void cancelQueuedJobIsDurableAndResumeDoesNotUndoCancellation() throws Exception {
        root=root.toRealPath();Path source=Files.write(root.resolve("source"),new byte[1024]),dest=Files.createDirectory(root.resolve("dest"));
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new CompletableFuture<FileEndpoint>();
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->gate,()->2);
            UUID id;
            try {
                id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(5,TimeUnit.SECONDS);
                coordinator.cancel(id);await(coordinator,id,TransferState.CANCELLED);
                assertThatThrownBy(()->coordinator.resume(id,null).get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(java.io.IOException.class);
                assertThat(dest.resolve("source")).doesNotExist();
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
            var reopened=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try { assertThat(reopened.job(id).get(5,TimeUnit.SECONDS).state()).isEqualTo(TransferState.CANCELLED); }
            finally { reopened.close();reopened.stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void blockedPayloadDoesNotBlockPauseAndResumeUsesOwnedPartial() throws Exception {
        root=root.toRealPath();byte[] bytes=new byte[1024*1024];new Random(21).nextBytes(bytes);
        Path source=Files.write(root.resolve("source"),bytes),dest=Files.createDirectory(root.resolve("dest"));
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var blockOnce=new java.util.concurrent.atomic.AtomicBoolean(true);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)-> {
                var local=new LocalEndpoint();
                FileEndpoint delayed=(FileEndpoint)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileEndpoint.class},(proxy,method,args)-> {
                    if(method.getName().equals("abort")) release.countDown();
                    try {
                        Object value=method.invoke(local,args);
                        if(method.getName().equals("write")) {
                            var output=(WriteHandle)value;
                            return new WriteHandle() {
                                public void write(int b) throws java.io.IOException { write(new byte[]{(byte)b}); }
                                public void write(byte[] b,int off,int len) throws java.io.IOException {
                                    if(blockOnce.compareAndSet(true,false)) { started.countDown();try { if(!release.await(5,TimeUnit.SECONDS)) throw new java.io.IOException("Fixture timeout"); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new java.io.IOException(e); } }
                                    output.write(b,off,len);
                                }
                                public long checkpoint() throws java.io.IOException { return output.checkpoint(); }
                                public void abort() { output.abort();release.countDown(); }
                                public void close() throws java.io.IOException { output.close(); }
                            };
                        }
                        return value;
                    } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
                return CompletableFuture.completedFuture(delayed);
            },()->2);
            try {
                var id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();long before=System.nanoTime();coordinator.pause(id);
                await(coordinator,id,TransferState.PAUSED);assertThat(System.nanoTime()-before).isLessThan(TimeUnit.SECONDS.toNanos(3));
                coordinator.resume(id,null).get(3,TimeUnit.SECONDS);await(coordinator,id,TransferState.COMPLETED);
                assertThat(Files.readAllBytes(dest.resolve("source"))).isEqualTo(bytes);
            } finally { coordinator.close();release.countDown();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void independentFileFinishesWhileAnotherWaitsForConflictDecision() throws Exception {
        root=root.toRealPath();Path source=Files.createDirectory(root.resolve("source")),dest=Files.createDirectory(root.resolve("dest"));
        Path a=Files.writeString(source.resolve("a"),"new"),b=Files.writeString(source.resolve("b"),"second");Files.writeString(dest.resolve("a"),"original");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->1);
            try {
                var id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(a.toString(),b.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                await(coordinator,id,TransferState.NEEDS_ATTENTION);
                assertThat(Files.readString(dest.resolve("b"))).isEqualTo("second");assertThat(Files.readString(dest.resolve("a"))).isEqualTo("original");
                var entry=coordinator.entries(id,0,200).get(3,TimeUnit.SECONDS).stream().filter(e->e.relative().equals("a")).findFirst().orElseThrow();
                coordinator.resolve(entry.id(),ConflictDecision.SKIP,null).get(3,TimeUnit.SECONDS);coordinator.resume(id,null).get(3,TimeUnit.SECONDS);
                await(coordinator,id,TransferState.COMPLETED_WITH_ISSUES);
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void queuedSameDestinationDoesNotOccupyScannerNeededToFinishPriorJob() throws Exception {
        root=root.toRealPath();Path first=Files.write(root.resolve("a"),new byte[1024*1024]),second=Files.write(root.resolve("b"),new byte[1024*1024]),dest=Files.createDirectory(root.resolve("dest"));
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try {
                var a=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(first.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                var b=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(second.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                await(coordinator,a,TransferState.COMPLETED);await(coordinator,b,TransferState.COMPLETED);
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test void unicodeFolderConflictNeverCopiesChildrenBeforeExplicitDecision() throws Exception {
        root=root.toRealPath();String name="folder-\uD83D\uDE00";
        Path source=Files.createDirectory(root.resolve(name));Files.writeString(source.resolve("child"),"payload");
        for(var decision:List.of(ConflictDecision.MERGE,ConflictDecision.SKIP,ConflictDecision.RENAME)) {
            Path dest=Files.createDirectory(root.resolve(decision.name()));Files.createDirectory(dest.resolve(name));
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                var coordinator=new TransferCoordinator(root.resolve("queue-"+decision),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
                try {
                    var id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                    await(coordinator,id,TransferState.NEEDS_ATTENTION);
                    assertThat(dest.resolve(name).resolve("child")).doesNotExist();
                    var parent=coordinator.entries(id,0,10).get(3,TimeUnit.SECONDS).getFirst();
                    coordinator.resolve(parent.id(),decision,decision==ConflictDecision.RENAME?"renamed":null).get(3,TimeUnit.SECONDS);
                    coordinator.resume(id,null).get(3,TimeUnit.SECONDS);
                    await(coordinator,id,decision==ConflictDecision.SKIP?TransferState.COMPLETED_WITH_ISSUES:TransferState.COMPLETED);
                    if(decision==ConflictDecision.SKIP) assertThat(dest.resolve(name).resolve("child")).doesNotExist();
                    else assertThat(Files.readString(dest.resolve(decision==ConflictDecision.RENAME?"renamed":name).resolve("child"))).isEqualTo("payload");
                } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
            }
        }
    }

    @Test void explicitDecisionsRecoverFailedReplacementWithoutLosingOldPublicationEvidence() throws Exception {
        root=root.toRealPath();Path source=Files.writeString(root.resolve("source"),"new payload");
        for(boolean published:List.of(false,true)) for(var decision:List.of(ConflictDecision.SKIP,ConflictDecision.RENAME,ConflictDecision.REPLACE)) {
            String suffix=published+"-"+decision;Path dest=Files.createDirectory(root.resolve(suffix));Files.writeString(dest.resolve("source"),"original");
            var failOnce=new java.util.concurrent.atomic.AtomicBoolean(true);
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                var coordinator=new TransferCoordinator(root.resolve("queue-"+suffix),executor,Runnable::run,(ref,owner)-> {
                    var local=new LocalEndpoint();
                    var endpoint=(FileEndpoint)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileEndpoint.class},(proxy,method,args)-> {
                        try {
                            if(method.getName().equals("publish") && failOnce.compareAndSet(true,false)) {
                                if(published) method.invoke(local,args);
                                throw new java.io.IOException("Injected interruption at publication");
                            }
                            return method.invoke(local,args);
                        } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
                    return CompletableFuture.completedFuture(endpoint);
                },()->1);
                try {
                    var id=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                    await(coordinator,id,TransferState.NEEDS_ATTENTION);var entry=coordinator.entries(id,0,10).get(3,TimeUnit.SECONDS).getFirst();
                    coordinator.resolve(entry.id(),ConflictDecision.REPLACE,null).get(3,TimeUnit.SECONDS);coordinator.resume(id,null).get(3,TimeUnit.SECONDS);
                    await(coordinator,id,TransferState.INTERRUPTED);
                    coordinator.resolve(entry.id(),decision,decision==ConflictDecision.RENAME?"renamed":null).get(3,TimeUnit.SECONDS);coordinator.resume(id,null).get(3,TimeUnit.SECONDS);
                    await(coordinator,id,!published && decision==ConflictDecision.SKIP?TransferState.COMPLETED_WITH_ISSUES:TransferState.COMPLETED);
                    assertThat(Files.readString(dest.resolve("source"))).isEqualTo(published || decision==ConflictDecision.REPLACE?"new payload":"original");
                    if(!published && decision==ConflictDecision.RENAME) assertThat(Files.readString(dest.resolve("renamed"))).isEqualTo("new payload");
                    else assertThat(dest.resolve("renamed")).doesNotExist();
                } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
            }
        }
    }

    @Test void remainingPolicyForManyConflictsDoesNotDelayAnotherJobsStalledPause() throws Exception {
        root=root.toRealPath();Path queue=root.resolve("queue"),source=Files.write(root.resolve("payload"),new byte[1024]),dest=Files.createDirectory(root.resolve("dest"));
        UUID large;long first;
        try(var store=new dev.jasper.remote.transfer.store.TransferStore(queue)) {
            large=store.create(new TransferRequest(EndpointRef.local(),List.of("/source"),EndpointRef.local(),"/destination"));
            for(int offset=0;offset<100_000;offset+=250) {
                var batch=new ArrayList<dev.jasper.remote.transfer.store.TransferStore.Discovered>();
                for(int n=offset;n<offset+250;n++) batch.add(new dev.jasper.remote.transfer.store.TransferStore.Discovered("file-"+n,"/source/file-"+n,"/destination/file-"+n,new FileEntry("file-"+n,FileEntry.Kind.FILE,1,1000,0644,"")));
                store.discover(large,batch);
            }
            first=store.entries(large,0,1).getFirst().id();store.scanned(large);
        }
        // Seed a large already-discovered conflict set in one fixture transaction.
        try(var connection=new org.sqlite.JDBC().connect("jdbc:sqlite:"+queue.resolve("queue.sqlite"),new Properties());var statement=connection.createStatement()) {
            statement.executeUpdate("UPDATE entries SET expected=info,expected_kind=kind,error='Destination already exists'");
        }
        var reading=new CountDownLatch(1);var released=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(queue,executor,Runnable::run,(ref,owner)-> {
                var local=new LocalEndpoint();
                var endpoint=(FileEndpoint)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileEndpoint.class},(proxy,method,args)-> {
                    if(method.getName().equals("abort"))released.countDown();
                    try {
                        var value=method.invoke(local,args);
                        if(method.getName().equals("read") && args[0].equals(source.toString())) return new java.io.FilterInputStream((java.io.InputStream)value) {
                            public int read(byte[] bytes,int offset,int count) throws java.io.IOException {
                                reading.countDown();try { if(!released.await(10,TimeUnit.SECONDS))throw new java.io.IOException("Fixture timeout"); }catch(InterruptedException e){throw new java.io.IOException(e);}
                                return super.read(bytes,offset,count);
                            }
                        };
                        return value;
                    }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });return CompletableFuture.completedFuture(endpoint);
            },()->2);
            try {
                var active=coordinator.enqueue(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString()),null).get(3,TimeUnit.SECONDS);
                assertThat(reading.await(3,TimeUnit.SECONDS)).isTrue();
                var decision=coordinator.resolve(first,ConflictDecision.SKIP,null,true);decision.get(2,TimeUnit.SECONDS);
                coordinator.pause(active);assertThat(released.await(2,TimeUnit.SECONDS)).isTrue();await(coordinator,active,TransferState.PAUSED);
            }finally {coordinator.close();released.countDown();coordinator.stopped().get(5,TimeUnit.SECONDS);}
        }
    }
    @Test void anExistingItemsChoiceCoversConflictsInsideMergedFolders() throws Exception {
        root=root.toRealPath();Path source=Files.createDirectories(root.resolve("source/sub")).getParent(),dest=Files.createDirectories(root.resolve("dest/source/sub")).getParent().getParent();
        Files.writeString(source.resolve("sub/file"),"new");Files.writeString(source.resolve("fresh"),"fresh");Files.writeString(dest.resolve("source/sub/file"),"old");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try {
                var request=new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString());
                var replaced=coordinator.enqueue(request.withExisting(ConflictDecision.REPLACE),null).get(5,TimeUnit.SECONDS);
                await(coordinator,replaced,TransferState.COMPLETED);
                assertThat(Files.readString(dest.resolve("source/sub/file"))).isEqualTo("new");
                assertThat(Files.readString(dest.resolve("source/fresh"))).isEqualTo("fresh");
                assertThat(coordinator.request(replaced).get(5,TimeUnit.SECONDS).paths()).containsExactly(source.toString());
                Files.writeString(source.resolve("sub/file"),"newer");
                var skipped=coordinator.enqueue(request.withExisting(ConflictDecision.SKIP),null).get(5,TimeUnit.SECONDS);
                var job=await(coordinator,skipped,TransferState.COMPLETED_WITH_ISSUES);
                assertThat(Files.readString(dest.resolve("source/sub/file"))).as("existing file kept").isEqualTo("new");
                assertThat(job.skippedEntries()).isGreaterThanOrEqualTo(1);
                assertThat(job.failedEntries()).isZero();
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }
    static TransferJob await(TransferCoordinator coordinator,UUID id,TransferState state) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);TransferJob last=null;
        while(System.nanoTime()<deadline) { last=coordinator.job(id).get(3,TimeUnit.SECONDS);if(last.state()==state) return last;
            if(last.state()==TransferState.FAILED || last.state()==TransferState.NEEDS_ATTENTION) throw new AssertionError(last);
            Thread.sleep(10); }
        throw new AssertionError("Timed out awaiting "+state+": "+last);
    }
}
