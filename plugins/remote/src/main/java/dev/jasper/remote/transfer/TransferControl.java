package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.FileEndpoint;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Urgent controls bypass work admission. Abort affects only this operation's owned endpoints. */
public final class TransferControl {
    private final AtomicReference<TransferJob.Intent> intent=new AtomicReference<>(TransferJob.Intent.RUN);
    private final Set<FileEndpoint> endpoints=ConcurrentHashMap.newKeySet();
    private volatile long requestedAt;
    public TransferJob.Intent intent() { return intent.get(); }
    public boolean running() { return intent.get()==TransferJob.Intent.RUN && !Thread.currentThread().isInterrupted(); }
    public void request(TransferJob.Intent value) {
        intent.updateAndGet(old -> old==TransferJob.Intent.CANCEL ? old : value);
        if(value!=TransferJob.Intent.RUN && requestedAt==0) requestedAt=System.nanoTime();
    }
    public void check() throws Stopped { if(!running()) throw new Stopped(); }
    public void own(FileEndpoint endpoint) { endpoints.add(endpoint); if(!running()) endpoint.abort(); }
    public void release(FileEndpoint endpoint) { endpoints.remove(endpoint); }
    public void abort() { endpoints.forEach(FileEndpoint::abort); }
    public void releaseAll() { abort();endpoints.clear(); }
    public void abortIfStalled() { if(requestedAt!=0 && System.nanoTime()-requestedAt>500_000_000L) abort(); }
    public static final class Stopped extends IOException {
        private static final long serialVersionUID=1L;
        public Stopped() { super("Transfer stopped"); }
    }
}
