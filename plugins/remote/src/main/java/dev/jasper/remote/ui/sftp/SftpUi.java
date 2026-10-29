package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.*;
import dev.jasper.remote.edit.RemoteEditing;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.*;
import dev.jasper.sdk.ui.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * The explorers, one in each window's SFTP panel (full windows spec 9), and captured picker flows; the coordinator owns
 * all queued jobs. The explorer is an owned kit view ({@code register}); its dialogs (host chooser, new folder, delete, existing items, Copy to host) are
 * kit forms from {@code context.kit()}, each closed with its dialog. Back, Forward and the parent folder are panel
 * shortcuts (Alt+Left, Alt+Right, Alt+Up by default) anywhere in the SFTP panel, listed in the Keymap.
 */
public final class SftpUi implements AutoCloseable {
    public static final String PANEL="dev.jasper.remote.sftp.panel";
    /** The action that toggles the panel; registered by the plugin before this UI, so View &gt; Tool Windows lists it once. */
    public static final String TOGGLE="dev.jasper.remote.sftp.toggle";
    /** The panel's history and parent folder shortcuts, in the panel's own shortcut context. */
    public static final String BACK="dev.jasper.remote.sftp.back",FORWARD="dev.jasper.remote.sftp.forward",PARENT="dev.jasper.remote.sftp.parent";
    public record PaneTarget(UUID pane,ConnectionIdentity identity,String directory) {}
    private record View(PanelHost host,SftpPanel panel,SftpController controller,FileOperationController operations) {}
    private final PluginContext context;
    private final Executor ui,background;
    private final Connections connections;
    private final EndpointFactory endpoints;
    private final Supplier<TransferCoordinator> transfers;
    private final Supplier<List<RemoteHost>> hosts;
    private final Function<WindowHandle,Optional<PaneTarget>> activePane;
    private final Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip;
    private final Consumer<WindowHandle> releaseStrip;
    private final Consumer<String> clipboard;
    /** One explorer in each window's dock that built the SFTP panel, by window id. */
    private final Map<UUID,View> explorers=new LinkedHashMap<>();
    private final Set<PluginDialog> dialogs=new HashSet<>();
    /** The host each window's Browse is resolving, by window id, until it connects or another Browse there replaces it. */
    private final Map<UUID,CompletableFuture<ConnectionIdentity>> browsing=new HashMap<>();
    private Consumer<UUID> followRequested=pane->{};
    private volatile boolean showHidden;
    private Consumer<Boolean> rememberHidden=shown->{};
    private SftpColumns columns=SftpColumns.memory();
    private RemoteEditing editing;
    private volatile boolean closed;
    /** Which composition the sidebars take (spec 14.4). */
    private final dev.jasper.remote.workstation.Layout layout;
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->context.platform().copy(text));
    }
    /** The sidebars in {@code layout}'s composition; under CLASSIC the window's transfers are the Transfers panel's. */
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,
                  dev.jasper.remote.workstation.Layout layout) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->context.platform().copy(text),layout);
    }
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,clipboard,dev.jasper.remote.workstation.Layout.ROWS);
    }
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,dev.jasper.remote.ui.transfers.TransferStrip> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard,
                  dev.jasper.remote.workstation.Layout layout) {
        this.layout=layout;
        this.context=context;this.ui=ui;this.background=background;this.connections=connections;this.endpoints=endpoints;this.transfers=transfers;this.hosts=hosts;this.activePane=activePane;this.transferStrip=transferStrip;this.releaseStrip=releaseStrip;this.clipboard=clipboard;
        // Enterprise's default slots (spec 14.4): SFTP leads the left side, File Servers under it.
        context.panels().register(layout==dev.jasper.remote.workstation.Layout.CLASSIC ? new PanelSpec(PANEL,"SFTP","folder",Anchor.LEFT).withToggleActionId(TOGGLE)
            : new PanelSpec(PANEL,"SFTP","folder",Anchor.LEFT).withToggleActionId(TOGGLE).withSplit(),this::create);
        // Ruling G6: the old panel-wide Alt+Left/Alt+Right and the table's Alt+Up, now rebindable panel shortcuts.
        shortcut(BACK,"SFTP: Back","alt+left",SftpPanel::back);
        shortcut(FORWARD,"SFTP: Forward","alt+right",SftpPanel::forward);
        shortcut(PARENT,"SFTP: Parent Folder","alt+up",SftpPanel::parent);
    }
    private void shortcut(String id,String title,String binding,Consumer<SftpPanel> command) {
        context.actions().register(ActionSpec.of(id,title).withKeywords(List.of("sftp","files","folder","history")).withDefaultBinding(binding)
            .withContext(ShortcutContext.panel(PANEL)),invoked-> { var view=closed?null:explorers.get(invoked.window().id());if(view!=null) command.accept(view.panel()); });
    }
    /**
     * Whether the SFTP browsers show hidden items, and where a change is remembered; each browser takes the choice as it
     * opens, and a toggle in any of them changes it.
     */
    public void hiddenItems(boolean shown,Consumer<Boolean> remember) { showHidden=shown;rememberHidden=remember; }
    /**
     * Where every SFTP browser and Copy to host's browser keep their sort and column widths; call before any panel opens.
     * A failed write is logged.
     */
    public void columns(ColumnStateFile file) {
        columns=new SftpColumns(file,failure->context.log().log(System.Logger.Level.WARNING,"SFTP columns: {0}",failure.getMessage()));
    }
    private void hiddenChanged(boolean shown) { showHidden=shown;rememberHidden.accept(shown); }
    /** The first open explorer, or null when no SFTP panel is open. */
    SftpPanel panel() { return explorers.values().stream().findFirst().map(View::panel).orElse(null); }
    /** {@code window}'s explorer, or null when its SFTP panel is not open. */
    SftpPanel panel(UUID window) { var view=explorers.get(window);return view==null?null:view.panel(); }
    /** The host of {@code window}'s SFTP panel, while it is open. */
    Optional<PanelHost> panelHost(UUID window) { return Optional.ofNullable(explorers.get(window)).map(View::host); }
    private UiView create(PanelHost host) {
        var panel=new SftpPanel(context.kit(),layout);var window=host.window();panel.columns(columns);
        panel.onHeaderChanged(host::setHeader);
        // In a window of its own or docked at the bottom the table adds Modified and Permissions; the panel is right from the
        // start and follows each move.
        panel.wide(dev.jasper.remote.workstation.Layout.wide(host.placement()));
        host.onPlacement(placement->panel.wide(dev.jasper.remote.workstation.Layout.wide(placement)));
        panel.onChooseSession(chosen-> { if(!closed && window.isOpen()) browse(window,chosen); });
        panel.sessions(hosts.get());
        if(layout==dev.jasper.remote.workstation.Layout.ROWS) panel.transfers(transferStrip.apply(window));
        Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> open=identity->endpoints.open(Optional.of(identity),window,status->{});
        var controller=new SftpController(context.dataDirectory().resolve("browser-cache"),background,ui,open,panel);
        controller.onFollowRequested(pane->followRequested.accept(pane));
        controller.showHidden(showHidden);controller.onShowHiddenChanged(this::hiddenChanged);
        var operations=new FileOperationController(context.dataDirectory().resolve("browser-cache"),background,ui,open,transfers.get().reservations(),panel::operationStatus,controller::refresh);
        var view=new View(host,panel,controller,operations);explorers.put(window.id(),view);
        controller.operations(new SftpController.Operations(()->upload(view,false),()->upload(view,true),()->download(view),()->newFolder(view),()->delete(view),()->copyPaths(view),()->copyHost(view),operations::cancel));
        if(editing!=null) panel.editing(new SftpPanel.Editing(entry->activate(view,entry),()->openSelected(view)));
        controller.visible(host.visible());
        host.onVisibility(visible->{controller.visible(visible);if(visible) activePane.apply(window).ifPresent(pane->controller.open(pane.pane(),pane.identity(),pane.directory(),false));});
        host.onClosed(()-> { if(layout==dev.jasper.remote.workstation.Layout.ROWS) releaseStrip.accept(window);if(explorers.remove(window.id(),view)) cancelBrowse(window.id());controller.close();operations.close();panel.close(); });return panel.view();
    }
    /** {@code window}'s explorer, shown in its dock; null for a window that closed, which shows nothing. */
    private View showView(WindowHandle window) {
        if(!window.isOpen()) return null;
        context.panels().show(PANEL,window);return explorers.get(window.id());
    }
    public void show(WindowHandle window) {
        if(closed) return;var view=showView(window);if(view==null) return;
        var pane=activePane.apply(window);
        if(pane.isPresent()) { var target=pane.orElseThrow();view.controller().open(target.pane(),target.identity(),target.directory(),true); }
        else if(view.controller().capture().isEmpty()) chooseHost(view.host().window());
    }
    public void show(WindowHandle window,PaneTarget pane) {
        if(closed)return;var view=showView(window);if(view!=null)view.controller().open(pane.pane(),pane.identity(),pane.directory(),true);
    }
    public void toggle(WindowHandle window) { if(!closed) context.panels().toggle(PANEL,window); }
    /** Saved hosts changed: every disconnected sidebar's Choose Session menu follows. */
    public void hostsChanged() { if(closed) return;for(var view:List.copyOf(explorers.values())) view.panel().sessions(hosts.get()); }
    /** Shows the SFTP sidebar without choosing a host. */
    public void reveal(WindowHandle window) { if(!closed) showView(window); }
    public void browse(WindowHandle window,RemoteHost host) {
        if(closed) return;var view=showView(window);if(view==null) return;UUID id=window.id();view.panel().busy(true,"Connecting…");
        cancelBrowse(id);var request=connections.resolveIdentity(host.id(),window);browsing.put(id,request);
        // The follow-up is the requesting window's explorer's: a window that closed meanwhile took its explorer and the attempt with it.
        request.whenComplete((identity,error)->ui.execute(()-> { if(closed || !window.isOpen() || browsing.get(id)!=request || explorers.get(id)!=view) return;browsing.remove(id);
            if(error!=null) view.panel().error(message(error));else view.controller().open(host.id(),identity,"",true); }));
    }
    private void cancelBrowse(UUID window) { var request=browsing.remove(window);if(request!=null) request.cancel(true); }
    /** {@code window}'s explorer, if open, shows {@code pane}, which that window focused. */
    public void pane(WindowHandle window,PaneTarget pane) { cancelBrowse(window.id());var view=explorers.get(window.id());if(view!=null) view.controller().open(pane.pane(),pane.identity(),pane.directory(),false); }
    public void directory(UUID pane,String path) { for(var view:List.copyOf(explorers.values())) view.controller().follow(pane,path); }
    /** {@code pane}'s SSH session ended (closed, exited or lost): each explorer showing it disconnects and clears. */
    public void ended(UUID pane) { for(var view:List.copyOf(explorers.values())) view.controller().ended(pane); }
    /** Receives the pane whose shell folder a view wants now: shown, focused, or follow turned back on. */
    public void onFollowRequested(Consumer<UUID> listener) { followRequested=listener; }
    /** Sends files to the Editor. Call before any panel opens, and only when the Editor is installed. */
    public void editing(RemoteEditing editing) { this.editing=editing; }
    /** True while an explorer, visible, follows {@code pane}. */
    public boolean following(UUID pane) { return explorers.values().stream().anyMatch(view->view.controller().following(pane)); }
    public void notice(UUID pane,String text) { for(var view:List.copyOf(explorers.values())) view.controller().notice(pane,text); }
    private PluginDialog dialog(WindowHandle owner,String title) { var dialog=context.windows().dialog(new DialogSpec(title,owner,false));dialogs.add(dialog);dialog.onClosed(()->dialogs.remove(dialog));return dialog; }
    /** Mounts an owned kit view on {@code dialog}, closes it with the dialog and shows the dialog. */
    private static void show(PluginDialog dialog,UiView view,AutoCloseable owner) {
        dialog.setContent(view);dialog.onClosed(()-> { try { owner.close(); } catch(Exception ignored) { } });dialog.show();
    }
    private void chooseHost(WindowHandle window) {
        var chooser=dialog(window,"Browse files on SSH host");
        var panel=new HostChooser(context.kit(),hosts.get(),host-> { chooser.close();browse(window,host); },chooser::close);
        show(chooser,panel.view(),panel);
    }
    private void upload(View view,boolean folder) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed) return;var target=captured.orElseThrow();WindowHandle owner=view.host().window();
        List<Path> paths=folder?context.windows().chooseDirectory(owner,"Upload folder",Optional.empty()).map(List::of).orElse(List.of()):context.windows().chooseFiles(owner,"Upload files",Optional.empty());
        if(paths.isEmpty() || closed || !owner.isOpen()) return;
        background(()-> { var selected=new ArrayList<String>();for(Path path:paths) selected.add(canonicalSelection(path));return new Queued(new TransferRequest(EndpointRef.local(),selected,EndpointRef.remote(target.identity()),target.directory()),selected.stream().map(value->{ Path name=Path.of(value).getFileName();return name==null?value:name.toString(); }).toList()); },owner);
    }
    private void download(View view) {
        var selected=view.controller().capture();if(selected.isEmpty() || selected.orElseThrow().selection().isEmpty()) return;var source=selected.orElseThrow();var owner=view.host().window();
        var directory=context.windows().chooseDirectory(owner,"Download selected files to",Optional.empty());if(directory.isEmpty() || closed || !owner.isOpen()) return;
        background(()->new Queued(new TransferRequest(EndpointRef.remote(source.identity()),paths(source),EndpointRef.local(),directory.orElseThrow().toRealPath().toString()),source.selection().stream().map(FileEntry::name).toList()),owner);
    }
    private static String canonicalSelection(Path path) throws IOException { Path absolute=path.toAbsolutePath().normalize();return absolute.getParent()==null?absolute.toRealPath().toString():absolute.getParent().toRealPath().resolve(absolute.getFileName()).toString(); }
    private static List<String> paths(SftpController.Capture captured) throws IOException { var paths=new ArrayList<String>();for(var file:captured.selection()) paths.add(FilePaths.child(captured.directory(),file.name()));return List.copyOf(paths); }
    private record Queued(TransferRequest request,List<String> names) {}
    private void background(Callable<Queued> prepare,WindowHandle owner) {
        background.execute(()-> { try { var queued=prepare.call();var identity=queued.request().destination().identity();ui.execute(()->check(queued,identity,owner)); }
            catch(Exception failure) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not prepare the transfer",message(failure),failure); }); } });
    }
    /** Asks once before copying onto items that already exist; one stat per selected item, off the UI thread. */
    private void check(Queued queued,Optional<ConnectionIdentity> identity,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        endpoints.open(identity,owner,status->{}).whenComplete((endpoint,error)-> {
            if(error!=null) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not connect for the transfer",message(error),error); });return; }
            background.execute(()-> { List<String> existing;
                try { existing=ExistingItems.existing(endpoint,queued.request().directory(),queued.names()); }
                catch(Exception failure) { ui.execute(()-> { if(!closed) context.notifications().group().error("Could not check for existing items",message(failure),failure); });return; }
                finally { endpoint.abort(); }
                ui.execute(()->decide(queued,identity,existing,owner)); });
        });
    }
    private void decide(Queued queued,Optional<ConnectionIdentity> identity,List<String> existing,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        if(existing.isEmpty()) { enqueue(queued.request(),owner);return; }
        String where=identity.map(value->value.host().name()+":").orElse("")+queued.request().directory();
        var dialog=dialog(owner,"Items already exist");
        var panel=new dev.jasper.remote.ui.transfers.ExistingItemsPanel(context.kit(),dev.jasper.remote.ui.transfers.ExistingItemsPanel.message(existing,queued.names().size(),where),
            choice-> { dialog.close();if(!closed && owner.isOpen()) enqueue(queued.request().withExisting(choice),owner); },dialog::close);
        show(dialog,panel.view(),panel);
    }
    private void enqueue(TransferRequest request,WindowHandle owner) {
        transfers.get().enqueue(request,owner).whenComplete((id,error)->ui.execute(()-> { if(!closed && error!=null) context.notifications().group().error("Could not queue the transfer",message(error),error); }));
    }
    private void copyPaths(View view) {
        view.controller().capture().ifPresent(captured->{try { if(!captured.selection().isEmpty()) clipboard.accept(String.join("\n",paths(captured))); }
            catch(IOException | IllegalStateException failure) { view.panel().error(message(failure)); } });
    }
    private void newFolder(View view) { view.controller().capture().ifPresent(captured->nameDialog(view.host().window(),captured,view.operations()::mkdir)); }
    private void nameDialog(WindowHandle owner,SftpController.Capture captured,BiConsumer<SftpController.Capture,String> create) {
        var dialog=dialog(owner,"New folder");
        var panel=new FolderNamePanel(context.kit(),name-> {
            try { FilePaths.child(captured.directory(),name);if(name.indexOf('\\')>=0) throw new IOException("Enter one folder name");create.accept(captured,name);dialog.close();return Optional.empty(); }
            catch(IOException invalid) { return Optional.of(String.valueOf(invalid.getMessage())); }
        },dialog::close);
        show(dialog,panel.view(),panel);
    }
    private void delete(View view) {
        var selected=view.controller().capture();if(selected.isEmpty() || selected.orElseThrow().selection().isEmpty() || view.operations().busy()) return;var captured=selected.orElseThrow();
        var dialog=dialog(view.host().window(),"Delete selected files");var panel=new dev.jasper.remote.ui.ConfirmPanel(context.kit(),"Delete "+captured.selection().size()+" selected items from "+captured.identity().host().name()+":"+captured.directory()+"?", "Delete",()-> { dialog.close();if(!closed) view.operations().delete(captured); },dialog::close);dialog.setContent(panel.view());dialog.onClosed(panel::close);dialog.show();
    }
    private void copyHost(View view) {
        var selected=view.controller().capture();if(selected.isEmpty() || selected.orElseThrow().selection().isEmpty()) return;var source=selected.orElseThrow();var owner=view.host().window();var dialog=dialog(owner,"Copy to host");
        var pickerReference=new java.util.concurrent.atomic.AtomicReference<DestinationPanel>();
        var operations=new FileOperationController(context.dataDirectory().resolve("browser-cache"),background,ui,identity->endpoints.open(Optional.of(identity),owner,status->{}),transfers.get().reservations(),(busy,text)-> { var current=pickerReference.get();if(current!=null) current.status(busy,text); },()-> { var current=pickerReference.get();if(current!=null) current.refresh(); });
        var picker=new DestinationPanel(context.kit(),source.selection().size()+" items from "+source.identity().host().name()+":"+source.directory(),hosts.get(),context.dataDirectory().resolve("browser-cache"),background,ui,
            id->connections.resolveIdentity(id,owner),identity->endpoints.open(Optional.of(identity),owner,status->{}),destination->{dialog.close();background(()->new Queued(new TransferRequest(EndpointRef.remote(source.identity()),paths(source),EndpointRef.remote(destination.identity()),destination.directory()),source.selection().stream().map(FileEntry::name).toList()),owner);},
            destination->nameDialog(owner,destination,operations::mkdir),operations::cancel,dialog::close);
        picker.hiddenItems(showHidden,this::hiddenChanged);picker.columns(columns);
        pickerReference.set(picker);dialog.onClosed(()-> { picker.close();operations.close(); });dialog.setContent(picker.view());dialog.show();
    }
    /** Whether {@code view} is still {@code owner}'s open explorer, showing the same identity and directory {@code capture} was taken from: a late result from the per-identity {@link RemoteEditing} queue must not act on a view that moved on in the meantime. */
    private boolean stillShowing(View view,WindowHandle owner,SftpController.Capture capture) {
        return explorers.get(owner.id())==view && view.controller().capture().filter(now->now.identity().equals(capture.identity()) && now.directory().equals(capture.directory())).isPresent();
    }
    private EditorOpening.Browser browser(View view,WindowHandle owner) {
        return new EditorOpening.Browser(ui,()->!closed && owner.isOpen(),capture->stillShowing(view,owner,capture),view.panel()::error,view.controller()::manual,()->{});
    }
    /** Double-click or Enter on one file or link; see {@link EditorOpening#activate}. */
    private void activate(View view,FileEntry entry) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;WindowHandle owner=view.host().window();
        EditorOpening.activate(editing,browser(view,owner),captured.orElseThrow(),entry,owner);
    }
    /** Open in Editor on the selection; see {@link EditorOpening#openSelected}. */
    private void openSelected(View view) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;WindowHandle owner=view.host().window();
        EditorOpening.openSelected(editing,browser(view,owner),captured.orElseThrow(),owner);
    }
    private static String message(Throwable failure) { while((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();return failure.getMessage()==null?"SFTP operation failed":failure.getMessage(); }
    @Override public void close() {
        if(closed) return;closed=true;for(var window:List.copyOf(browsing.keySet())) cancelBrowse(window);for(var dialog:List.copyOf(dialogs)) dialog.close();
        var views=List.copyOf(explorers.values());explorers.clear();for(var view:views) { view.controller().close();view.operations().close();view.panel().close(); }
    }
}
