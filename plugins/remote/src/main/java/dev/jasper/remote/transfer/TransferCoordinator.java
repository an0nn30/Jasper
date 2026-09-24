package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.store.TransferStore;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Shared queue owner. All long-lived loops are admitted before plugin teardown can close its executor. */
public final class TransferCoordinator implements AutoCloseable {
    public record Progress(UUID job,long bytes,long total,long sampledNanos) {}
    public record Summary(long totalBytes,long confirmedBytes,long remainingFiles,int runnable,int scanning,int paused,int attention) {}
    public record Snapshot(List<TransferJob> jobs,List<Progress> progress,int active,Summary summary,List<UUID> activeJobs) {
        public Snapshot(List<TransferJob> jobs,List<Progress> progress,int active) {
            this(jobs,progress,active,new Summary(jobs.stream().mapToLong(TransferJob::totalBytes).sum(),jobs.stream().mapToLong(TransferJob::confirmedBytes).sum(),jobs.stream().mapToLong(j->j.totalEntries()-j.completedEntries()-j.skippedEntries()-j.failedEntries()).sum(),(int)jobs.stream().filter(j->j.intent()==TransferJob.Intent.RUN && !j.state().terminal()).count(),(int)jobs.stream().filter(j->!j.scanned()).count(),(int)jobs.stream().filter(j->j.state()==TransferState.PAUSED).count(),(int)jobs.stream().filter(j->j.state()==TransferState.NEEDS_ATTENTION || j.state()==TransferState.INTERRUPTED).count()),jobs.stream().filter(j->j.intent()==TransferJob.Intent.RUN && !j.state().terminal()).map(TransferJob::id).toList());
        }
    }
    private final Path directory;
    private final Executor ui;
    private final BiFunction<EndpointRef,WindowHandle,CompletableFuture<FileEndpoint>> endpoints;
    private final IntSupplier parallel;
    private volatile BiFunction<EndpointRef,WindowHandle,CompletableFuture<Void>> validator=(ref,owner)->CompletableFuture.completedFuture(null);
    public void setResumeValidator(BiFunction<EndpointRef,WindowHandle,CompletableFuture<Void>> validator) { this.validator=Objects.requireNonNull(validator); }
    private final ArrayBlockingQueue<Runnable> commands=new ArrayBlockingQueue<>(256);
    private final ArrayBlockingQueue<Runnable> copies=new ArrayBlockingQueue<>(8),scans=new ArrayBlockingQueue<>(1);
    private final ConcurrentLinkedQueue<Runnable> completions=new ConcurrentLinkedQueue<>(); // at most nine admitted workers
    private final ConcurrentMap<UUID,Run> runs=new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,TransferJob.Intent> urgent=new ConcurrentHashMap<>();
    private final PathReservations reservations=new PathReservations();
    private final Map<String,Integer> endpointCopies=new HashMap<>();
    private final CountDownLatch workers=new CountDownLatch(9);
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    private final AtomicBoolean closing=new AtomicBoolean();
    private final AtomicInteger activeCopies=new AtomicInteger(),observedCopies=new AtomicInteger();
    private volatile TransferStore store;
    private volatile Throwable startupFailure;
    private boolean scanning;
    private int rotation;
    private final class Run {
        final ArrayBlockingQueue<Pair> idle=new ArrayBlockingQueue<>(2);
        final UUID id;final WindowHandle owner;final TransferControl control=new TransferControl();
        final Set<Long> busy=new HashSet<>();
        final boolean validateIdentity;final ConcurrentMap<Long,Progress> progress=new ConcurrentHashMap<>();
        volatile boolean activeScan;boolean finalizing,attention,cleanupOnly;
        Long restartEntry;
        volatile PathReservations.Lease reservation;
        Run(UUID id,WindowHandle owner) { this(id,owner,false); }
        Run(UUID id,WindowHandle owner,boolean validate) { this.id=id;this.owner=owner;this.validateIdentity=validate; }
    }
    public TransferCoordinator(Path directory,Executor background,Executor ui,
                               BiFunction<EndpointRef,WindowHandle,CompletableFuture<FileEndpoint>> endpoints,IntSupplier parallel) {
        this.directory=directory;this.ui=ui;this.endpoints=endpoints;this.parallel=parallel;
        try {
            for(int i=0;i<8;i++) background.execute(()->worker(copies));
            background.execute(()->worker(scans));background.execute(this::controlLoop);
        } catch(RuntimeException rejected) { closing.set(true);startupFailure=rejected;stopped.completeExceptionally(rejected);throw rejected; }
    }
    public PathReservations reservations() { return reservations; }
    public int maxObservedCopies() { return observedCopies.get(); }
    public CompletableFuture<Void> stopped() { return stopped; }
    private void worker(BlockingQueue<Runnable> queue) {
        try {
            while(!closing.get() || !queue.isEmpty()) { var task=queue.poll(100,TimeUnit.MILLISECONDS);if(task!=null) task.run(); }
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { workers.countDown(); }
    }
    private void controlLoop() {
        try(var opened=new TransferStore(directory)) {
            store=opened;
            while(!closing.get() || workers.getCount()>0 || !completions.isEmpty()) {
                Runnable completion;while((completion=completions.poll())!=null) completion.run();
                drainUrgent();
                for(var run:runs.values()) run.control.abortIfStalled();
                if(closing.get()) {
                    for(var run:runs.values()) { run.control.request(TransferJob.Intent.PAUSE);run.control.abort();opened.intent(run.id,run.control.intent()); }
                } else {
                    Runnable command=commands.poll(25,TimeUnit.MILLISECONDS);if(command!=null) command.run();
                    schedule();
                }
                settle();
                if(closing.get()) Thread.sleep(10);
            }
            for(var run:runs.values()) { opened.intent(run.id,run.control.intent());opened.state(run.id,run.control.intent()==TransferJob.Intent.CANCEL?TransferState.CANCELLED:TransferState.PAUSED,"Stopped");release(run); }
        } catch(Throwable failure) {
            startupFailure=failure;closing.set(true);runs.values().forEach(run->run.control.abort());
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            closing.set(true);Runnable command;while((command=commands.poll())!=null) command.run();
            if(startupFailure==null) stopped.complete(null);else stopped.completeExceptionally(startupFailure);
        }
    }
    private <T> CompletableFuture<T> command(Callable<T> task) {
        var result=new CompletableFuture<T>();
        if(closing.get()) { result.completeExceptionally(startupFailure!=null?startupFailure:new IOException("Transfers are closed"));return result; }
        Runnable work=()-> { try { if(closing.get()) throw new IOException("Transfers are closed",startupFailure);result.complete(task.call()); } catch(Exception failure) { result.completeExceptionally(failure); } };
        if(!commands.offer(work)) result.completeExceptionally(new IOException("Transfers are busy; try again"));return result;
    }
    public CompletableFuture<UUID> enqueue(TransferRequest request,WindowHandle owner) {
        return command(()-> { if(runs.size()>=256) throw new IOException("The transfer admission queue is full");UUID id=store.create(request);runs.put(id,new Run(id,owner));return id; });
    }
    public CompletableFuture<TransferJob> job(UUID id) { return command(()->store.job(id)); }
    public CompletableFuture<List<TransferEntry>> entries(UUID id,long offset,int limit) { return command(()->store.entries(id,offset,limit)); }
    public CompletableFuture<TransferRequest> request(UUID id) { return command(()->store.request(id)); }
    public CompletableFuture<Snapshot> snapshot(long offset,int limit) {
        return command(()-> {
            var progress=new ArrayList<Progress>();
            for(var run:runs.values()) for(var sample:run.progress.entrySet()) {
                var value=sample.getValue();long confirmed=store.entry(sample.getKey()).confirmed();
                progress.add(new Progress(run.id,Math.max(0,value.bytes()-confirmed),value.total(),value.sampledNanos()));
            }
            return new Snapshot(store.jobs(offset,limit),List.copyOf(progress),activeCopies.get(),store.summary(),runs.values().stream().filter(run->!run.cleanupOnly && run.control.running()).map(run->run.id).toList());
        });
    }
    public void pause(UUID id) { urgent(id,TransferJob.Intent.PAUSE); }
    public void cancel(UUID id) { urgent(id,TransferJob.Intent.CANCEL); }
    private void urgent(UUID id,TransferJob.Intent intent) {
        if(closing.get()) return;var run=runs.get(id);if(run!=null) run.control.request(intent);
        if(urgent.size()<256 || urgent.containsKey(id)) urgent.merge(id,intent,(old,next)->old==TransferJob.Intent.CANCEL?old:next);
    }
    private void drainUrgent() throws IOException {
        for(var item:urgent.entrySet()) if(urgent.remove(item.getKey(),item.getValue())) {
            var id=item.getKey();var intent=item.getValue();var job=store.job(id);if(job.state().terminal()) continue;
            store.intent(id,intent);var run=runs.get(id);
            if(run!=null) { run.control.request(intent);store.state(id,intent==TransferJob.Intent.CANCEL?TransferState.CANCELLING:TransferState.PAUSING,""); }
            else {
                if(intent==TransferJob.Intent.CANCEL) markCleanup(id);
                store.state(id,intent==TransferJob.Intent.CANCEL?TransferState.CANCELLED:TransferState.PAUSED,"");
            }
        }
    }
    public CompletableFuture<Void> resume(UUID id,WindowHandle owner) {
        return command(()-> { var job=store.job(id);if(job.intent()==TransferJob.Intent.CANCEL || job.state().terminal()) throw new IOException("This transfer cannot resume");
            if(runs.containsKey(id)) throw new IOException("Transfer has not stopped yet");if(runs.size()>=256) throw new IOException("The transfer admission queue is full");
            store.intent(id,TransferJob.Intent.RUN);store.state(id,TransferState.VALIDATING,"");runs.put(id,new Run(id,owner,true));return null; });
    }
    public CompletableFuture<Void> resolve(long entryId,ConflictDecision decision,String renamedTarget) { return resolve(entryId,decision,renamedTarget,false); }
    public CompletableFuture<Void> resolve(long entryId,ConflictDecision decision,String renamedTarget,boolean remaining) {
        return command(()-> { var entry=store.entry(entryId);if(runs.containsKey(entry.jobId())) throw new IOException("Wait for the transfer to stop before resolving it");
            if(decision==ConflictDecision.ASK) throw new IOException("Choose a conflict action");
            String target=entry.target();
            if(decision==ConflictDecision.RENAME) {
                if(renamedTarget==null || renamedTarget.isBlank()) throw new IOException("Choose a new filename");
                target=store.request(entry.jobId()).destination().hostId().isEmpty()?Path.of(target).getParent().resolve(validName(renamedTarget)).toString():FilePaths.child(FilePaths.parent(target),validName(renamedTarget));
                if(entry.phase()!=TransferEntry.Phase.PUBLISHING) store.renameTree(entryId,target);
            }
            if(entry.phase()==TransferEntry.Phase.PUBLISHING) {
                store.publicationDecision(entryId,decision,target);
                if(remaining) store.policy(entry.jobId(),false,decision);
                return null;
            }
            if(decision==ConflictDecision.SKIP && entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) store.skipTree(entryId);
            else store.decision(entryId,decision,target);
            if(remaining) store.policy(entry.jobId(),entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY,decision);return null; });
    }
    public CompletableFuture<Void> retry(UUID id,WindowHandle owner) { return resume(id,owner); }
    public CompletableFuture<Void> restart(long entryId,WindowHandle owner) {
        return command(()-> {
            var entry=store.entry(entryId);var job=store.job(entry.jobId());
            if(runs.containsKey(job.id()) || job.intent()==TransferJob.Intent.CANCEL || entry.outcome()!=TransferEntry.Outcome.PENDING && entry.outcome()!=TransferEntry.Outcome.FAILED) throw new IOException("Entry cannot restart while active or cancelled");
            var run=new Run(job.id(),owner,true);run.restartEntry=entryId;
            store.intent(job.id(),TransferJob.Intent.RUN);store.state(job.id(),TransferState.VALIDATING,"Checking partial before restart");runs.put(job.id(),run);return null;
        });
    }
    private static String validName(String name) throws IOException { if(name.equals(".") || name.equals("..") || name.indexOf('/')>=0 || name.indexOf('\\')>=0 || name.indexOf(0)>=0) throw new IOException("Enter one filename");return name; }
    public CompletableFuture<Void> clear(UUID id,boolean acknowledgeCleanup) { return command(()-> { store.clear(id,acknowledgeCleanup);return null; }); }
    public CompletableFuture<Void> cleanup(UUID id,WindowHandle owner) {
        return command(()-> { if(runs.containsKey(id)) throw new IOException("Transfer is still active");if(store.job(id).cleanupPending()==0) return null;
            var run=new Run(id,owner,true);run.cleanupOnly=true;runs.put(id,run);return null; });
    }
    private void schedule() throws IOException {
        var order=new ArrayList<>(runs.values());if(order.isEmpty()) return;Collections.rotate(order,rotation++%order.size());
        for(var run:order) {
            if(run.attention || !run.control.running() || run.finalizing) continue;
            var job=store.job(run.id);
            if(run.restartEntry!=null) { if(!run.activeScan && !scanning) dispatchRestart(run);continue; }
            if(run.cleanupOnly) { if(!run.activeScan && !scanning) dispatchScan(run,true);continue; }
            if(!job.scanned()) { if(!run.activeScan && !scanning) dispatchScan(run,false);continue; }
            if(run.activeScan) continue;
            var pending=store.pending(run.id,Math.min(200,8+run.busy.size()));
            if(pending.isEmpty() && run.busy.isEmpty()) {
                if(job.totalEntries()>job.completedEntries()+job.skippedEntries()+job.failedEntries()) {
                    store.intent(run.id,TransferJob.Intent.PAUSE);store.state(run.id,TransferState.NEEDS_ATTENTION,"Resolve unfinished entries in Details");release(run);runs.remove(run.id);
                } else if(!scanning) finalizeDirectories(run);
                continue;
            }
            int limit=Math.clamp(parallel.getAsInt(),1,8);
            for(var entry:pending) {
                if(activeCopies.get()>=limit || run.busy.size()>=2 || run.reservation==null && !run.busy.isEmpty()) break;
                if(run.busy.contains(entry.id())) continue;
                if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY && !run.busy.isEmpty()) break;
                if(!run.busy.isEmpty() && store.entry(run.busy.iterator().next()).sourceInfo().kind()==FileEntry.Kind.DIRECTORY) break;
                dispatchCopy(run,entry);
                if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) break;
            }
        }
    }
    private void dispatchRestart(Run run) {
        scanning=true;run.activeScan=true;
        scans.add(()-> {
            Throwable failure=null;
            try(var pair=open(run)) {
                reserve(run,pair);var entry=store.entry(run.restartEntry);
                TransferRecovery.cleanup(store,entry,pair.destination,run.control);
                if(store.entry(entry.id()).outcome()!=TransferEntry.Outcome.COMPLETE) {
                    var current=pair.source.stat(entry.source());
                    if((current.kind()!=entry.sourceInfo().kind() && entry.sourceInfo().kind()!=FileEntry.Kind.SPECIAL) || current.kind()==FileEntry.Kind.DIRECTORY) throw new TransferRecovery.Attention("Source type changed; skip this entry and queue a new transfer");
                    store.reset(entry.id(),current);
                }
            } catch(Throwable problem) { failure=problem; }
            Throwable result=failure;completions.add(()-> { scanning=false;run.activeScan=false;run.restartEntry=null;finishWork(run,result); });
        });
    }
    private void dispatchScan(Run run,boolean cleanup) throws IOException {
        scanning=true;run.activeScan=true;
        if(!cleanup) store.state(run.id,TransferState.SCANNING,"Discovering files");
        scans.add(()-> {
            Throwable failure=null;
            try(var pair=open(run)) {
                reserve(run,pair);
                if(cleanup) cleanupEntries(run,pair.destination);
                else TransferScan.scan(store,run.id,pair.source,pair.destination,run.control);
            } catch(Throwable problem) { failure=problem; }
            Throwable result=failure;completions.add(()-> { scanning=false;run.activeScan=false;finishWork(run,result);
                if(cleanup) { release(run);runs.remove(run.id); } });
        });
    }
    private void dispatchCopy(Run run,TransferEntry entry) throws IOException {
        run.busy.add(entry.id());int count=activeCopies.incrementAndGet();observedCopies.accumulateAndGet(count,Math::max);
        store.state(run.id,TransferState.RUNNING,"");
        copies.add(()-> {
            Throwable failure=null;
            try(var pair=open(run)) {
                reserve(run,pair);
                try(var permit=copyPermit(pair,run.control)) {
                    TransferCopy.copy(store,entry,pair.source,pair.destination,run.control,(done,total)->run.progress.put(entry.id(),new Progress(run.id,done,total,System.nanoTime())));
                }
            } catch(Throwable problem) { failure=problem; }
            Throwable result=failure;completions.add(()-> { activeCopies.decrementAndGet();run.busy.remove(entry.id());run.progress.remove(entry.id());
                if(result instanceof TransferRecovery.Attention && run.control.running()) {
                    try { store.attention(entry.id(),message(result)); } catch(IOException storeFailure) { finishWork(run,storeFailure); }
                } else finishWork(run,result); });
        });
    }
    private void finishWork(Run run,Throwable problem) {
        if(problem instanceof ReservationBusy) {
            if(run.busy.isEmpty() && !run.activeScan) release(run);
            try { store.state(run.id,TransferState.QUEUED,"Waiting for another transfer using this folder"); }catch(IOException failure){startupFailure=failure;close();}
            return;
        }
        if(problem==null || !run.control.running() || closing.get()) return;
        run.attention=true;run.control.request(TransferJob.Intent.PAUSE);
        if(run.cleanupOnly) { try { store.state(run.id,TransferState.CANCELLED,"Cleanup could not finish: "+message(problem)); } catch(IOException failure) { startupFailure=failure;close(); }return; }
        try { store.intent(run.id,TransferJob.Intent.PAUSE);store.state(run.id,problem instanceof TransferRecovery.Attention?TransferState.NEEDS_ATTENTION:TransferState.INTERRUPTED,message(problem)); }
        catch(IOException failedStore) { startupFailure=failedStore;close(); }
    }
    private static String message(Throwable problem) { while(problem instanceof CompletionException || problem instanceof ExecutionException) problem=problem.getCause();return problem.getMessage()==null?problem.getClass().getSimpleName():problem.getMessage(); }
    private void settle() throws IOException {
        for(var run:List.copyOf(runs.values())) if(!run.activeScan && run.busy.isEmpty() && !run.control.running()) {
            if(run.control.intent()==TransferJob.Intent.CANCEL) { markCleanup(run.id);store.state(run.id,TransferState.CANCELLED,""); }
            else if(!run.attention) store.state(run.id,TransferState.PAUSED,"");
            release(run);runs.remove(run.id);
            if(run.control.intent()==TransferJob.Intent.CANCEL && !closing.get() && store.job(run.id).cleanupPending()>0) {
                var cleanup=new Run(run.id,run.owner);cleanup.cleanupOnly=true;runs.put(run.id,cleanup);
            }
        }
    }
    private void markCleanup(UUID job) throws IOException { store.markCleanup(job); }
    private void cleanupEntries(Run run,FileEndpoint destination) throws IOException {
        long offset=0;while(true) { var page=store.cleanup(run.id,offset,200);if(page.isEmpty()) break;int failed=0;
            for(var item:page) { run.control.check();try { TransferRecovery.cleanup(store,store.entry(item.entryId()),destination,run.control); }
                catch(IOException failure) { store.cleanup(item.entryId(),message(failure));failed++; } }
            offset+=failed;
        }
    }
    private void finalizeDirectories(Run run) throws IOException {
        scanning=true;run.activeScan=true;run.finalizing=true;
        scans.add(()-> {
            Throwable failure=null;
            try(var pair=open(run)) {
                reserve(run,pair);long offset=0;
                while(true) { var page=store.directories(run.id,offset,200);if(page.isEmpty()) break;
                    for(var entry:page) if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) {
                        run.control.check();if(pair.destination.stat(entry.target()).kind()!=FileEntry.Kind.DIRECTORY) throw new TransferRecovery.Attention("Destination directory changed before metadata update");
                        try { pair.destination.metadata(entry.target(),entry.sourceInfo().modifiedMillis(),entry.sourceInfo().permissions()); }
                        catch(IOException | UnsupportedOperationException unsupported) { store.warning(entry.id(),"Metadata warning: "+unsupported.getMessage()); }
                    }
                    offset+=page.size();
                }
            } catch(Throwable problem) { failure=problem; }
            Throwable result=failure;completions.add(()-> { scanning=false;run.activeScan=false;run.finalizing=false;finishWork(run,result);
                if(result==null && run.control.running()) try { var job=store.job(run.id);store.state(run.id,job.failedEntries()>0 || job.skippedEntries()>0 || job.metadataWarnings()>0?TransferState.COMPLETED_WITH_ISSUES:TransferState.COMPLETED,"");release(run);runs.remove(run.id); }
                catch(IOException problem) { finishWork(run,problem); }
            });
        });
    }
    private final class Pair implements AutoCloseable {
        final FileEndpoint source,destination;final Run run;
        Pair(FileEndpoint source,FileEndpoint destination,Run run) { this.source=source;this.destination=destination;this.run=run; }
        @Override public void close() throws IOException {
            if(run.control.running() && !closing.get() && run.idle.offer(this)) return;
            try { source.close(); } finally { run.control.release(source);try { destination.close(); } finally { run.control.release(destination); } }
        }
    }
    private Pair open(Run run) throws Exception {
        run.control.check();var idle=run.idle.poll();if(idle!=null) return idle;
        var request=store.request(run.id);var source=open(request.source(),run);
        try { return new Pair(source,open(request.destination(),run),run); }
        catch(Exception failure) { source.close();run.control.release(source);throw failure; }
    }
    private FileEndpoint open(EndpointRef ref,Run run) throws Exception {
        var waiting=new CompletableFuture<FileEndpoint>();var request=new AtomicReference<CompletableFuture<FileEndpoint>>();
        ui.execute(()-> {
            if(!run.control.running() || closing.get()) { waiting.cancel(false);return; }
            try { var future=new CompletableFuture<FileEndpoint>();
                var stage=new AtomicReference<CompletableFuture<?>>();
                future.whenComplete((value,error)-> { if(future.isCancelled()) { var pending=stage.get();if(pending!=null) pending.cancel(true); } });
                var validation=run.validateIdentity?validator.apply(ref,run.owner):CompletableFuture.<Void>completedFuture(null);stage.set(validation);
                validation.whenComplete((ignored,problem)->ui.execute(()-> {
                    if(problem!=null) { future.completeExceptionally(problem);return; }
                    if(future.isDone()) return;
                    final CompletableFuture<FileEndpoint> opening;
                    try { opening=endpoints.apply(ref,run.owner); } catch(RuntimeException failure) { future.completeExceptionally(failure);return; }
                    stage.set(opening);
                    opening.whenComplete((value,error)-> { if(error!=null) future.completeExceptionally(error);else if(!future.complete(value)) value.abort(); });
                    if(future.isCancelled()) opening.cancel(true);
                }));
                request.set(future);
                future.whenComplete((endpoint,error)-> { if(error!=null) waiting.completeExceptionally(error);else if(!waiting.complete(endpoint)) endpoint.abort(); });
                if(waiting.isCancelled()) future.cancel(true);
            } catch(RuntimeException failure) { waiting.completeExceptionally(failure); }
        });
        try {
            while(true) { run.control.check();try { var endpoint=waiting.get(100,TimeUnit.MILLISECONDS);run.control.own(endpoint);run.control.check();return endpoint; } catch(TimeoutException pending) { /* urgent controls stay live */ } }
        } catch(Exception failure) { waiting.cancel(true);var pending=request.get();if(pending!=null) pending.cancel(true);throw failure; }
    }
    private void reserve(Run run,Pair pair) throws IOException {
        synchronized(run) { if(run.reservation!=null) return; }
        var request=store.request(run.id);var keys=new ArrayList<PathReservations.Key>();
        for(String source:request.paths()) keys.add(new PathReservations.Key(pair.source.id(),pair.source.canonical(source),false));
        var destination=new PathReservations.Key(pair.destination.id(),pair.destination.canonical(request.directory()),true);
        for(var source:keys) if(PathReservations.overlaps(source,destination)) throw new TransferRecovery.Attention("Source and destination overlap");
        keys.add(destination);
        run.control.check();
        synchronized(run) {
            if(run.reservation!=null)return;
            run.reservation=reservations.acquire(run.id,keys).orElseThrow(ReservationBusy::new);
        }
    }
    private static final class ReservationBusy extends IOException { private static final long serialVersionUID=1L; }
    private void release(Run run) { run.control.releaseAll();run.idle.clear();synchronized(run) { if(run.reservation!=null) { run.reservation.close();run.reservation=null; } } }
    private AutoCloseable copyPermit(Pair pair,TransferControl control) throws InterruptedException,IOException {
        var ids=new HashSet<>(List.of(pair.source.id(),pair.destination.id()));
        synchronized(endpointCopies) {
            while(ids.stream().anyMatch(id->endpointCopies.getOrDefault(id,0)>=2)) { control.check();endpointCopies.wait(50); }
            control.check();ids.forEach(id->endpointCopies.merge(id,1,Integer::sum));
        }
        return ()->{ synchronized(endpointCopies) { ids.forEach(id->{ int next=endpointCopies.get(id)-1;if(next==0) endpointCopies.remove(id);else endpointCopies.put(id,next); });endpointCopies.notifyAll(); } };
    }
    @Override public void close() {
        if(!closing.compareAndSet(false,true)) return;
        for(var run:runs.values()) { run.control.request(TransferJob.Intent.PAUSE);run.control.abort(); }
    }
}
