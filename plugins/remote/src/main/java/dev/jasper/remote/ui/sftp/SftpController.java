package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** UI-owned intent and one admitted worker loop. Slow or stale responses never redirect a captured action. */
public final class SftpController implements AutoCloseable {
    public record Capture(ConnectionIdentity identity,String directory,List<FileEntry> selection) {
        public Capture { selection=List.copyOf(selection); }
    }
    public record Operations(Runnable upload,Runnable uploadFolder,Runnable download,Runnable newFolder,Runnable delete,Runnable copyPath,Runnable copyToHost,Runnable cancel) {
        static Operations none() { Runnable empty=()->{};return new Operations(empty,empty,empty,empty,empty,empty,empty,empty); }
    }
    private static final class State {
        final UUID pane;final ConnectionIdentity identity;String path,reported;boolean follow=true;
        final List<String> history=new ArrayList<>();int historyIndex=-1;
        State(UUID pane,ConnectionIdentity identity,String reported) { this.pane=pane;this.identity=identity;this.path=reported;this.reported=reported; }
    }
    private record View(ConnectionIdentity identity,String path) {}
    private final Path cacheDirectory;
    private final Executor ui;
    private final Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> opener;
    private final SftpPanel panel;
    private final Map<UUID,State> states=new HashMap<>();
    private final AtomicReference<Runnable> pending=new AtomicReference<>();
    private final ArrayBlockingQueue<Boolean> wake=new ArrayBlockingQueue<>(1);
    private final AtomicReference<FileEndpoint> active=new AtomicReference<>();
    private final CompletableFuture<Void> stopped=new CompletableFuture<>();
    private volatile long generation;
    private volatile boolean closed;
    private boolean visible=true,loading,foldersOnly;
    private volatile boolean showHidden;
    /** The order pages take: the panel's at each listing, then each page request's; a listing still loading takes a newer one. */
    private volatile DirectoryCache.Order order=DirectoryCache.Order.DEFAULT;
    private Consumer<UUID> followRequests=pane->{};
    private Consumer<Boolean> hiddenChanged=shown->{};
    public void foldersOnly(boolean value) { foldersOnly=value; }
    /** Whether hidden items (names starting with a dot) show, before the first folder loads. */
    public void showHidden(boolean shown) { showHidden=shown;panel.showHidden(shown); }
    /** Receives the user's choice when the panel's toggle shows or hides hidden items. */
    public void onShowHiddenChanged(Consumer<Boolean> listener) { hiddenChanged=listener; }
    private State state;
    private View displayed;
    private DirectoryCache cache; // worker-confined
    private View cachedView;
    private CompletableFuture<FileEndpoint> opening;
    private Operations operations=Operations.none();
    public SftpController(Path cacheDirectory,Executor background,Executor ui,Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> opener,SftpPanel panel) {
        this.cacheDirectory=cacheDirectory;this.ui=ui;this.opener=opener;this.panel=panel;
        panel.actions(new SftpPanel.Actions(this::manual,this::up,this::refresh,this::following,
            ()->operations.upload().run(),()->operations.uploadFolder().run(),()->operations.download().run(),()->operations.newFolder().run(),
            ()->operations.delete().run(),()->operations.copyPath().run(),()->operations.copyToHost().run(),()-> { cancel();operations.cancel().run(); },this::page,
            this::hidden));
        panel.navigation(this::back,this::forward);
        background.execute(this::loop);
    }
    public void operations(Operations operations) { this.operations=operations; }
    /** Receives the pane whose shell folder this view wants now. */
    public void onFollowRequested(Consumer<UUID> listener) { followRequests=listener; }
    /** True while this visible view follows {@code pane}'s terminal folder. */
    public boolean following(UUID pane) { return !closed && visible && state!=null && state.follow && state.pane.equals(pane); }
    /** Shows {@code text} while this visible view shows {@code pane}. */
    public void notice(UUID pane,String text) { if(!closed && visible && state!=null && state.pane.equals(pane)) panel.error(text); }
    private void requestFollow() { if(!closed && visible && state!=null && state.follow) followRequests.accept(state.pane); }
    public CompletableFuture<Void> stopped() { return stopped; }
    public Optional<Capture> capture() { return displayed==null?Optional.empty():Optional.of(new Capture(displayed.identity(),displayed.path(),panel.selection())); }
    public void open(UUID pane,ConnectionIdentity identity,String reported,boolean explicit) {
        if(closed) return;String hint=reported==null?"":reported;
        var remembered=states.get(pane);
        if(remembered==null || !remembered.identity.equals(identity)) { remembered=new State(pane,identity,hint);states.put(pane,remembered); }
        remembered.reported=hint;if(remembered.follow && !hint.isBlank()) remembered.path=hint;
        boolean changed=state!=remembered;state=remembered;panel.following(state.follow);
        if(changed) { displayed=null;panel.clear(""); }
        navigationAvailable();
        if(visible && (changed || explicit || displayed==null)) load(state.path,explicit);requestFollow();
    }
    public void follow(UUID pane,String reported) {
        var remembered=states.get(pane);if(remembered==null) return;remembered.reported=reported;
        if(remembered==state && remembered.follow && visible && !reported.isBlank() && !reported.equals(remembered.path)) { remembered.path=reported;load(reported,false); }
    }
    /** {@code pane}'s SSH session ended: forget it, and if this view shows it, stop loading and clear the view. */
    public void ended(UUID pane) {
        states.remove(pane);
        if(closed || state==null || !state.pane.equals(pane)) return;
        cancel();state=null;displayed=null;submit(()-> { closeCache();cachedView=null; });panel.clear("SSH session closed");
    }
    public void following(boolean follow) {
        if(state==null) return;state.follow=follow;panel.following(follow);
        if(follow && !state.reported.isBlank()) { state.path=state.reported;if(visible) load(state.path,false); }if(follow) requestFollow();
    }
    public void manual(String requested) {
        if(state==null || closed) return;
        try {
            if(requested==null || requested.isBlank()) return;
            String base=displayed!=null && displayed.identity().equals(state.identity)?displayed.path():state.path;
            String target=requested.startsWith("/")?FilePaths.absolute(requested):base.startsWith("/")?FilePaths.absolute(base+"/"+requested):requested;
            state.follow=false;panel.following(false);state.path=target;if(visible) load(target,true);
        } catch(IOException invalid) { panel.error(invalid.getMessage()); }
    }
    /** Browses a successfully visited directory in this connection's own history. */
    public void back() { history(-1); }
    public void forward() { history(1); }
    private void history(int direction) {
        if(closed || loading || !visible || state==null) return;
        int target=state.historyIndex+direction;if(target<0 || target>=state.history.size()) return;
        state.follow=false;panel.following(false);load(state.history.get(target),true,target);
    }
    private void navigationAvailable() {
        panel.navigationAvailable(state!=null && state.historyIndex>0,state!=null && state.historyIndex+1<state.history.size());
    }
    private void visited(State visiting,String directory,int target) {
        if(target>=0) { visiting.history.set(target,directory);visiting.historyIndex=target; }
        else if(visiting.historyIndex<0 || !visiting.history.get(visiting.historyIndex).equals(directory)) {
            while(visiting.history.size()>visiting.historyIndex+1) visiting.history.removeLast();
            visiting.history.add(directory);visiting.historyIndex=visiting.history.size()-1;
        }
        navigationAvailable();
    }
    public void up() { if(displayed==null) return;try { manual(FilePaths.parent(displayed.path())); } catch(IOException failure) { panel.error(failure.getMessage()); } }
    public void refresh() { if(state!=null && visible && !closed) load(state.path,false); }
    public void visible(boolean value) {
        if(closed || visible==value) return;visible=value;
        if(value) { if(state!=null) load(state.path,true);requestFollow(); }
        else { cancel();submit(()-> { closeCache();cachedView=null; }); }
    }
    public void cancel() {
        generation++;loading=false;if(opening!=null) { opening.thenAccept(FileEndpoint::abort);opening.cancel(true); }var endpoint=active.getAndSet(null);if(endpoint!=null) endpoint.abort();pending.set(null);
        if(!closed) panel.busy(false,"Cancelled");
    }
    private void load(String target,boolean focus) { load(target,focus,-1); }
    private void load(String target,boolean focus,int historyTarget) {
        cancel();long token=generation;var requestedState=state;order=panel.order();
        loading=true;panel.busy(true,"Loading folder…");
        try { opening=opener.apply(requestedState.identity); }
        catch(RuntimeException failure) { loading=false;panel.error(message(failure));return; }
        var future=opening;
        future.whenComplete((endpoint,failure)-> { if(endpoint!=null && (closed || generation!=token)) endpoint.abort(); });
        submit(()-> {
            DirectoryCache fresh=null;
            try {
                FileEndpoint endpoint;
                while(true) { check(token);try { endpoint=future.get(100,TimeUnit.MILLISECONDS);break; } catch(TimeoutException waiting) { /* cancellation owns the request */ } }
                active.set(endpoint);check(token);
                String requested=target.isBlank()?endpoint.home():target.startsWith("/")?FilePaths.absolute(target):FilePaths.absolute(endpoint.home()+"/"+target);
                String directory=endpoint.resolveDirectory(requested);
                fresh=new DirectoryCache(cacheDirectory);var writing=fresh;var batch=new ArrayList<FileEntry>(256);
                var listingEndpoint=endpoint;
                endpoint.list(directory,entry-> { try { check(token);listingEndpoint.child(directory,entry.name());batch.add(entry);if(batch.size()==256) { writing.append(batch);batch.clear(); } } catch(IOException error) { throw new UncheckedIOException(error); } });
                if(!batch.isEmpty()) fresh.append(batch);check(token);
                closeCache();cache=fresh;fresh=null;cachedView=new View(requestedState.identity,directory);
                boolean hidden=showHidden;var rows=cache.page(0,200,foldersOnly,hidden,order);long count=cache.count(foldersOnly,hidden);var view=cachedView;
                publish(token,()-> { loading=false;displayed=view;requestedState.path=directory;visited(requestedState,directory,historyTarget);panel.showPage(view.identity().host().name(),directory,rows,0,count,focus); });
            } catch(Exception failure) { publish(token,()-> { loading=false;panel.error(message(failure)); }); }
            finally {
                if(fresh!=null) try { fresh.close(); } catch(IOException ignored) { }
                var endpoint=active.getAndSet(null);if(endpoint!=null) endpoint.abort();
            }
        });
    }
    /** Shows the page at {@code offset} of the folder sorted in {@code order}. */
    public void page(long offset,DirectoryCache.Order order) {
        this.order=order;
        if(closed || loading || displayed==null || state==null || !displayed.identity().equals(state.identity)) return;long token=generation;
        boolean hidden=showHidden;var sorted=order;
        submit(()-> { try { if(cache==null || cachedView==null) return;var rows=cache.page(offset,200,foldersOnly,hidden,sorted);var view=cachedView;long count=cache.count(foldersOnly,hidden);publish(token,()-> { displayed=view;panel.showPage(view.identity().host().name(),view.path(),rows,offset,count,false); }); }
            catch(IOException failure) { publish(token,()-> { loading=false;panel.error(message(failure)); }); } });
    }
    /** The panel's toggle: the folder shown again from its first page, from the cache, without listing it again. */
    private void hidden(boolean shown) {
        if(closed) return;showHidden(shown);hiddenChanged.accept(shown);page(0,panel.order());
    }
    private void submit(Runnable task) { if(closed) return;pending.set(task);wake.offer(true); }
    private void loop() {
        try {
            while(!closed) { wake.poll(100,TimeUnit.MILLISECONDS);var task=pending.getAndSet(null);if(task!=null && !closed) task.run(); }
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { var endpoint=active.getAndSet(null);if(endpoint!=null) endpoint.abort();closeCache();stopped.complete(null); }
    }
    private void closeCache() { if(cache!=null) { try { cache.close(); } catch(IOException ignored) { }cache=null; } }
    private void check(long token) throws IOException { if(closed || generation!=token || Thread.currentThread().isInterrupted()) throw new IOException("Directory request cancelled"); }
    private void publish(long token,Runnable task) { ui.execute(()-> { if(!closed && visible && generation==token) task.run(); }); }
    private static String message(Throwable error) { while((error instanceof ExecutionException || error instanceof CompletionException || error instanceof UncheckedIOException) && error.getCause()!=null) error=error.getCause();return error.getMessage()==null?"Directory request failed":error.getMessage(); }
    @Override public void close() { if(closed) return;closed=true;cancel();pending.set(null);wake.offer(true);states.clear(); }
}
