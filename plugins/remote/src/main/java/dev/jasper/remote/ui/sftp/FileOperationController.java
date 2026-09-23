package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Cancellable background mutations, independent of the browser and transfer payload channels. */
public final class FileOperationController implements AutoCloseable {
    public record Result(long completed,long failures,List<String> errors,boolean cancelled) { public Result { errors=List.copyOf(errors); } }
    private final Path directory;
    private final Executor ui;
    private final Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> opener;
    private final PathReservations reservations;
    private final BiConsumer<Boolean,String> status;
    private final Runnable refresh;
    private final ArrayBlockingQueue<Runnable> work=new ArrayBlockingQueue<>(1);
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    private volatile TransferControl control;
    private CompletableFuture<FileEndpoint> opening;
    private volatile boolean closed;
    private boolean busy;
    public FileOperationController(Path directory,Executor background,Executor ui,Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> opener,
                                   PathReservations reservations,BiConsumer<Boolean,String> status,Runnable refresh) {
        this.directory=directory;this.ui=ui;this.opener=opener;this.reservations=reservations;this.status=status;this.refresh=refresh;background.execute(this::loop);
    }
    public boolean busy() { return busy; }
    public CompletableFuture<Void> stopped() { return stopped; }
    public void delete(SftpController.Capture selection) { start(selection,null); }
    public void mkdir(SftpController.Capture selection,String name) { start(selection,name); }
    private void start(SftpController.Capture selection,String name) {
        if(closed || busy) return;busy=true;control=new TransferControl();var token=control;status.accept(true,name==null?"Deleting selected files…":"Creating folder…");
        try { opening=opener.apply(selection.identity()); } catch(RuntimeException failure) { busy=false;status.accept(false,failure.getMessage());return; }
        var future=opening;
        work.add(()-> {
            String result="";boolean changed=false;
            try {
                FileEndpoint endpoint;
                while(true) { token.check();try { endpoint=future.get(100,TimeUnit.MILLISECONDS);break; } catch(TimeoutException pending) { } }
                token.own(endpoint);
                try(var ownedEndpoint=endpoint) {
                    token.check();
                    if(name!=null) {
                        String path=endpoint.child(selection.directory(),name);
                        var key=new PathReservations.Key(endpoint.id(),endpoint.canonical(path),true);
                        try(var lease=reservations.acquire(UUID.randomUUID(),List.of(key)).orElseThrow(()->new IOException("This folder overlaps an active transfer"))) { token.check();endpoint.mkdir(path); }
                        result="Folder created";changed=true;
                    } else {
                        var paths=new ArrayList<String>();
                        for(var file:selection.selection()) { String path=endpoint.child(selection.directory(),file.name());if(!TransferRecovery.sameSource(file,endpoint.stat(path))) throw new IOException("Selection changed before deletion: "+file.name());paths.add(path); }
                        var last=new AtomicLong();
                        var outcome=delete(endpoint,paths,reservations,token,directory,count->{long now=System.nanoTime();if(now-last.get()>200_000_000L) { last.set(now);publish(()->status.accept(true,"Deleted "+count+" items…")); } });
                        result=(outcome.cancelled()?"Cancelled; ":"")+outcome.completed()+" items deleted"+(outcome.failures()>0?"; "+outcome.failures()+" errors: "+String.join("; ",outcome.errors()):"");changed=outcome.completed()>0;
                    }
                } finally { token.release(endpoint); }
            } catch(Exception failure) { result=token.running()?message(failure):"Cancelled; completed deletions were kept"; }
            String text=result;boolean updated=changed;
            publish(()-> { busy=false;opening=null;status.accept(false,text);if(updated) refresh.run(); });
        });
    }
    public void cancel() { var token=control;if(token!=null) { token.request(TransferJob.Intent.CANCEL);token.abort(); }if(opening!=null) { opening.thenAccept(FileEndpoint::abort);opening.cancel(true); } }
    private void publish(Runnable task) { ui.execute(()-> { if(!closed) task.run(); }); }
    private void loop() {
        try { while(!closed || !work.isEmpty()) { var task=work.poll(100,TimeUnit.MILLISECONDS);if(task!=null) task.run(); } }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { var token=control;if(token!=null) token.abort();stopped.complete(null); }
    }
    @Override public void close() { if(closed) return;closed=true;cancel(); }
    private static String message(Throwable failure) { while(failure.getCause()!=null && (failure instanceof ExecutionException || failure instanceof CompletionException || failure instanceof UncheckedIOException)) failure=failure.getCause();return failure.getMessage()==null?"File operation failed":failure.getMessage(); }

    public static Result delete(FileEndpoint endpoint,List<String> paths,PathReservations reservations,TransferControl control,Path cache,LongConsumer progress) throws IOException {
        var keys=new ArrayList<PathReservations.Key>();for(String path:paths) keys.add(new PathReservations.Key(endpoint.id(),endpoint.canonical(path),true));
        try(var lease=reservations.acquire(UUID.randomUUID(),keys).orElseThrow(()->new IOException("Selected paths overlap an active transfer"));var queue=new Deletions(cache)) {
            for(String path:paths) { control.check();queue.add(path,endpoint.stat(path)); }
            long removed=0,failures=0;var errors=new ArrayList<String>();
            while(control.running()) {
                var next=queue.next();if(next==null) break;
                try {
                    var now=endpoint.stat(next.path());
                    if(next.after()) {
                        if(now.kind()!=FileEntry.Kind.DIRECTORY || (!next.info().fileKey().isEmpty() && !next.info().fileKey().equals(now.fileKey()))) throw new IOException("Directory changed before deletion");
                        control.check();endpoint.remove(next.path(),true);removed++;
                    } else if(!TransferRecovery.sameSource(next.info(),now)) throw new IOException("Entry changed before deletion");
                    else if(now.kind()==FileEntry.Kind.DIRECTORY) {
                        queue.scan(endpoint,next,control);continue;
                    } else { control.check();endpoint.remove(next.path(),false);removed++; }
                    progress.accept(removed);
                } catch(IOException failure) { if(!control.running()) break;failures++;if(errors.size()<50) errors.add(next.path()+": "+failure.getMessage()); }
                queue.remove(next.id());
            }
            return new Result(removed,failures,errors,!control.running());
        }
    }
    /** Disk-backed deletion stack prevents a large folder from filling a Java collection. */
    private static final class Deletions implements AutoCloseable {
        record Item(long id,String path,FileEntry info,boolean after) {}
        final Path file;final Connection db;
        Deletions(Path directory) throws IOException {
            try {
                Files.createDirectories(directory);file=Files.createTempFile(directory,"delete-",".sqlite");var driver=new org.sqlite.JDBC();
                try { db=driver.connect("jdbc:sqlite:"+file,new Properties()); }
                finally { for(var registered:Collections.list(DriverManager.getDrivers())) if(registered instanceof org.sqlite.JDBC && registered.getClass().getClassLoader()==getClass().getClassLoader()) DriverManager.deregisterDriver(registered); }
                try(var s=db.createStatement()) { s.execute("PRAGMA journal_mode=OFF");s.execute("PRAGMA cache_size=-2048");s.execute("CREATE TABLE work(id INTEGER PRIMARY KEY,path TEXT NOT NULL UNIQUE,info TEXT NOT NULL,after INTEGER NOT NULL DEFAULT 0)"); }
            } catch(SQLException failure) { throw new IOException("Cannot prepare deletion queue",failure); }
        }
        void add(String path,FileEntry entry) throws IOException {
            try(var s=db.prepareStatement("INSERT OR IGNORE INTO work(path,info) VALUES(?,?)")) { s.setString(1,path);s.setString(2,TransferCodec.file(entry));s.executeUpdate(); }
            catch(SQLException failure) { throw new IOException("Cannot queue deletion",failure); }
        }
        Item next() throws IOException {
            try(var s=db.createStatement();var row=s.executeQuery("SELECT * FROM work ORDER BY id DESC LIMIT 1")) { return row.next()?new Item(row.getLong("id"),row.getString("path"),TransferCodec.file(row.getString("info")),row.getBoolean("after")):null; }
            catch(SQLException failure) { throw new IOException("Cannot read deletion queue",failure); }
        }
        void remove(long id) throws IOException { try(var s=db.prepareStatement("DELETE FROM work WHERE id=?")) { s.setLong(1,id);s.executeUpdate(); } catch(SQLException failure) { throw new IOException("Cannot update deletion queue",failure); } }
        void scan(FileEndpoint endpoint,Item item,TransferControl control) throws IOException {
            try {
                db.setAutoCommit(false);
                try {
                    endpoint.list(item.path(),entry->{ try { control.check();add(endpoint.child(item.path(),entry.name()),entry); } catch(IOException failure) { throw new UncheckedIOException(failure); } });
                    try(var s=db.prepareStatement("UPDATE work SET after=1 WHERE id=?")) { s.setLong(1,item.id());s.executeUpdate(); }db.commit();
                } catch(IOException | RuntimeException failure) { db.rollback();if(failure instanceof UncheckedIOException wrapped) throw wrapped.getCause();throw failure; }
                finally { db.setAutoCommit(true); }
            } catch(SQLException failure) { throw new IOException("Cannot expand deletion queue",failure); }
        }
        @Override public void close() throws IOException { try { db.close(); } catch(SQLException failure) { throw new IOException(failure); }finally { Files.deleteIfExists(file); } }
    }
}
