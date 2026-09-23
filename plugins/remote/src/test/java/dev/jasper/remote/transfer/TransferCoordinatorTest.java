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
    static TransferJob await(TransferCoordinator coordinator,UUID id,TransferState state) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);TransferJob last=null;
        while(System.nanoTime()<deadline) { last=coordinator.job(id).get(3,TimeUnit.SECONDS);if(last.state()==state) return last;
            if(last.state()==TransferState.FAILED || last.state()==TransferState.NEEDS_ATTENTION) throw new AssertionError(last);
            Thread.sleep(10); }
        throw new AssertionError("Timed out awaiting "+state+": "+last);
    }
}
