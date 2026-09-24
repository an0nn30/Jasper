package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.*;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.*;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/** Per-window explorers and captured picker flows; the coordinator owns all queued jobs. */
public final class SftpUi implements AutoCloseable {
    public static final String PANEL="dev.jasper.remote.sftp.panel";
    public record PaneTarget(UUID pane,ConnectionIdentity identity,String directory) {}
    private record View(PanelHost host,SftpPanel panel,SftpController controller,FileOperationController operations) {}
    private final PluginContext context;
    private final Executor ui,background;
    private final Connections connections;
    private final EndpointFactory endpoints;
    private final Supplier<TransferCoordinator> transfers;
    private final Supplier<List<RemoteHost>> hosts;
    private final Function<WindowHandle,Optional<PaneTarget>> activePane;
    private final Function<WindowHandle,JComponent> transferStrip;
    private final Consumer<WindowHandle> releaseStrip;
    private final Consumer<String> clipboard;
    private final Map<UUID,View> views=new HashMap<>();
    private final Set<PluginDialog> dialogs=new HashSet<>();
    private final Map<UUID,CompletableFuture<ConnectionIdentity>> browsing=new HashMap<>();
    private Consumer<UUID> followRequested=pane->{};
    private volatile boolean closed;
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,JComponent> transferStrip,Consumer<WindowHandle> releaseStrip) {
        this(context,ui,background,connections,endpoints,transfers,hosts,activePane,transferStrip,releaseStrip,text->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text),null));
    }
    public SftpUi(PluginContext context,Executor ui,Executor background,Connections connections,EndpointFactory endpoints,Supplier<TransferCoordinator> transfers,
                  Supplier<List<RemoteHost>> hosts,Function<WindowHandle,Optional<PaneTarget>> activePane,Function<WindowHandle,JComponent> transferStrip,Consumer<WindowHandle> releaseStrip,Consumer<String> clipboard) {
        this.context=context;this.ui=ui;this.background=background;this.connections=connections;this.endpoints=endpoints;this.transfers=transfers;this.hosts=hosts;this.activePane=activePane;this.transferStrip=transferStrip;this.releaseStrip=releaseStrip;this.clipboard=clipboard;
        context.panels().register(new PanelSpec(PANEL,"SFTP",context.appearance().icon(IconName.FOLDER),Anchor.LEFT),this::create);
    }
    private JComponent create(PanelHost host) {
        var panel=new SftpPanel(context.appearance()::icon);var window=host.window();
        panel.transfers(transferStrip.apply(window));
        Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> open=identity->endpoints.open(Optional.of(identity),window,status->{});
        var controller=new SftpController(context.dataDirectory().resolve("browser-cache"),background,ui,open,panel);
        controller.onFollowRequested(pane->followRequested.accept(pane));
        var operations=new FileOperationController(context.dataDirectory().resolve("browser-cache"),background,ui,open,transfers.get().reservations(),panel::operationStatus,controller::refresh);
        var view=new View(host,panel,controller,operations);views.put(window.id(),view);
        controller.operations(new SftpController.Operations(()->upload(view,false),()->upload(view,true),()->download(view),()->newFolder(view),()->delete(view),()->copyPaths(view),()->copyHost(view),operations::cancel));
        controller.visible(host.visible());
        host.onVisibility(visible->{controller.visible(visible);if(visible) activePane.apply(window).ifPresent(pane->controller.open(pane.pane(),pane.identity(),pane.directory(),false));});
        host.onClosed(()-> { releaseStrip.accept(window);cancelBrowse(window.id());views.remove(window.id());controller.close();operations.close(); });return panel;
    }
    private View showView(WindowHandle window) {
        var view=views.get(window.id());if(view==null) { context.panels().toggle(PANEL,window);view=views.get(window.id()); }else view.host().show();return view;
    }
    public void show(WindowHandle window) {
        if(closed) return;var view=showView(window);if(view==null) return;
        var pane=activePane.apply(window);
        if(pane.isPresent()) { var target=pane.orElseThrow();view.controller().open(target.pane(),target.identity(),target.directory(),true); }
        else if(view.controller().capture().isEmpty()) chooseHost(window);
    }
    public void show(WindowHandle window,PaneTarget pane) {
        if(closed)return;var view=showView(window);if(view!=null)view.controller().open(pane.pane(),pane.identity(),pane.directory(),true);
    }
    public void toggle(WindowHandle window) { if(!closed) context.panels().toggle(PANEL,window); }
    /** Shows the window's SFTP sidebar without choosing a host. */
    public void reveal(WindowHandle window) { if(!closed) showView(window); }
    public void browse(WindowHandle window,RemoteHost host) {
        if(closed) return;var view=showView(window);if(view==null) return;view.panel().busy(true,"Connecting…");
        cancelBrowse(window.id());var request=connections.resolveIdentity(host.id(),window);browsing.put(window.id(),request);
        request.whenComplete((identity,error)->ui.execute(()-> { if(closed || !window.isOpen() || browsing.get(window.id())!=request) return;browsing.remove(window.id());if(error!=null) view.panel().error(message(error));else view.controller().open(host.id(),identity,"",true); }));
    }
    private void cancelBrowse(UUID window) { var request=browsing.remove(window);if(request!=null) request.cancel(true); }
    public void pane(WindowHandle window,PaneTarget pane) { cancelBrowse(window.id());var view=views.get(window.id());if(view!=null) view.controller().open(pane.pane(),pane.identity(),pane.directory(),false); }
    public void directory(UUID pane,String path) { for(var view:views.values()) view.controller().follow(pane,path); }
    /** {@code pane}'s SSH session ended (closed, exited or lost): views showing it disconnect and clear. */
    public void ended(UUID pane) { for(var view:views.values()) view.controller().ended(pane); }
    /** Receives the pane whose shell folder a view wants now: shown, focused, or follow turned back on. */
    public void onFollowRequested(Consumer<UUID> listener) { followRequested=listener; }
    /** True while any visible view follows {@code pane}. */
    public boolean following(UUID pane) { return views.values().stream().anyMatch(view->view.controller().following(pane)); }
    public void notice(UUID pane,String text) { for(var view:views.values()) view.controller().notice(pane,text); }
    private PluginDialog dialog(WindowHandle owner,String title) { var dialog=context.windows().dialog(new DialogSpec(title,owner,false));dialogs.add(dialog);dialog.onClosed(()->dialogs.remove(dialog));return dialog; }
    private void chooseHost(WindowHandle window) {
        var chooser=dialog(window,"Browse files on SSH host");var selection=new JComboBox<>(hosts.get().toArray(RemoteHost[]::new));selection.setRenderer(hostRenderer());
        var panel=new JPanel(new BorderLayout(8,8));panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));panel.add(selection,BorderLayout.CENTER);var open=new JButton("Browse files");open.setEnabled(selection.getItemCount()>0);panel.add(open,BorderLayout.SOUTH);
        open.addActionListener(event->{var host=(RemoteHost)selection.getSelectedItem();if(host!=null) { chooser.close();browse(window,host); }});chooser.setContent(panel);chooser.show();
    }
    private static ListCellRenderer<Object> hostRenderer() { return new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) { super.getListCellRendererComponent(list,value,index,selected,focus);putClientProperty("html.disable",true);setText(value instanceof RemoteHost host?host.name()+" — "+host.hostname():"");return this; } }; }
    private void upload(View view,boolean folder) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed) return;var target=captured.orElseThrow();WindowHandle owner=view.host().window();
        List<Path> paths=folder?context.windows().chooseDirectory(owner,"Upload folder",Optional.empty()).map(List::of).orElse(List.of()):context.windows().chooseFiles(owner,"Upload files",Optional.empty());
        if(paths.isEmpty() || closed || !owner.isOpen()) return;
        background(()-> { var selected=new ArrayList<String>();for(Path path:paths) selected.add(canonicalSelection(path));return new Queued(new TransferRequest(EndpointRef.local(),selected,EndpointRef.remote(target.identity()),target.directory()),selected.stream().map(value->Path.of(value).getFileName().toString()).toList()); },owner);
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
            catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); }); } });
    }
    /** Asks once before copying onto items that already exist; one stat per selected item, off the UI thread. */
    private void check(Queued queued,Optional<ConnectionIdentity> identity,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        endpoints.open(identity,owner,status->{}).whenComplete((endpoint,error)-> {
            if(error!=null) { ui.execute(()-> { if(!closed) context.notices().error(message(error)); });return; }
            background.execute(()-> { List<String> existing;
                try { existing=ExistingItems.existing(endpoint,queued.request().directory(),queued.names()); }
                catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); });return; }
                finally { endpoint.abort(); }
                ui.execute(()->decide(queued,identity,existing,owner)); });
        });
    }
    private void decide(Queued queued,Optional<ConnectionIdentity> identity,List<String> existing,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        if(existing.isEmpty()) { enqueue(queued.request(),owner);return; }
        String where=identity.map(value->value.host().name()+":").orElse("")+queued.request().directory();
        var dialog=dialog(owner,"Items already exist");
        dialog.setContent(new dev.jasper.remote.ui.transfers.ExistingItemsPanel(dev.jasper.remote.ui.transfers.ExistingItemsPanel.message(existing,queued.names().size(),where),
            choice-> { dialog.close();enqueue(queued.request().withExisting(choice),owner); },dialog::close));dialog.show();
    }
    private void enqueue(TransferRequest request,WindowHandle owner) {
        transfers.get().enqueue(request,owner).whenComplete((id,error)->ui.execute(()-> { if(!closed && error!=null) context.notices().error(message(error)); }));
    }
    private void copyPaths(View view) {
        view.controller().capture().ifPresent(captured->{try { if(!captured.selection().isEmpty()) clipboard.accept(String.join("\n",paths(captured))); }
            catch(IOException | IllegalStateException failure) { view.panel().error(message(failure)); } });
    }
    private void newFolder(View view) { view.controller().capture().ifPresent(captured->nameDialog(view.host().window(),captured,view.operations()::mkdir)); }
    private void nameDialog(WindowHandle owner,SftpController.Capture captured,BiConsumer<SftpController.Capture,String> create) {
        var dialog=dialog(owner,"New folder");var panel=new JPanel(new BorderLayout(6,6));panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));var name=new JTextField(24);name.getAccessibleContext().setAccessibleName("Folder name");var error=new JLabel();error.putClientProperty("html.disable",true);var button=new JButton("Create");
        var center=new JPanel(new BorderLayout(6,6));center.add(name,BorderLayout.NORTH);center.add(error,BorderLayout.SOUTH);panel.add(center,BorderLayout.CENTER);panel.add(button,BorderLayout.SOUTH);
        Runnable submit=()-> { try { FilePaths.child(captured.directory(),name.getText());if(name.getText().indexOf('\\')>=0) throw new IOException("Enter one folder name");create.accept(captured,name.getText());dialog.close(); }catch(IOException invalid) { error.setText(invalid.getMessage()); } };
        button.addActionListener(event->submit.run());name.addActionListener(event->submit.run());dialog.setContent(panel);dialog.show();
    }
    private void delete(View view) {
        var selected=view.controller().capture();if(selected.isEmpty() || selected.orElseThrow().selection().isEmpty() || view.operations().busy()) return;var captured=selected.orElseThrow();
        var dialog=dialog(view.host().window(),"Delete selected files");var panel=new dev.jasper.remote.ui.ConfirmPanel("Delete "+captured.selection().size()+" selected items from "+captured.identity().host().name()+":"+captured.directory()+"?", "Delete",()-> { dialog.close();if(!closed) view.operations().delete(captured); },dialog::close);dialog.setContent(panel);dialog.show();
    }
    private void copyHost(View view) {
        var selected=view.controller().capture();if(selected.isEmpty() || selected.orElseThrow().selection().isEmpty()) return;var source=selected.orElseThrow();var owner=view.host().window();var dialog=dialog(owner,"Copy to host");
        var pickerReference=new java.util.concurrent.atomic.AtomicReference<DestinationPanel>();
        var operations=new FileOperationController(context.dataDirectory().resolve("browser-cache"),background,ui,identity->endpoints.open(Optional.of(identity),owner,status->{}),transfers.get().reservations(),(busy,text)-> { var current=pickerReference.get();if(current!=null) current.status(busy,text); },()-> { var current=pickerReference.get();if(current!=null) current.refresh(); });
        var picker=new DestinationPanel(source.selection().size()+" items from "+source.identity().host().name()+":"+source.directory(),hosts.get(),context.dataDirectory().resolve("browser-cache"),background,ui,context.appearance()::icon,
            id->connections.resolveIdentity(id,owner),identity->endpoints.open(Optional.of(identity),owner,status->{}),destination->{dialog.close();background(()->new Queued(new TransferRequest(EndpointRef.remote(source.identity()),paths(source),EndpointRef.remote(destination.identity()),destination.directory()),source.selection().stream().map(FileEntry::name).toList()),owner);},
            destination->nameDialog(owner,destination,operations::mkdir),operations::cancel,dialog::close);
        pickerReference.set(picker);dialog.onClosed(()-> { picker.close();operations.close(); });dialog.setContent(picker);dialog.show();
    }
    private static String message(Throwable failure) { while((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();return failure.getMessage()==null?"SFTP operation failed":failure.getMessage(); }
    @Override public void close() { if(closed) return;closed=true;for(var request:List.copyOf(browsing.values())) request.cancel(true);browsing.clear();for(var dialog:List.copyOf(dialogs)) dialog.close();for(var view:List.copyOf(views.values())) { view.controller().close();view.operations().close(); }views.clear(); }
}
