package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "Copy to host" on one host-rendered kit form: a fixed source summary, the destination host and one destination browser
 * (folders only), never a second permanent explorer. Copy here applies once the browser shows a folder of the chosen
 * host. No button is the form's primary, so Enter copies nothing (the former dialog had no default button); Escape
 * cancels. A late connection or listing after {@link #close()} is ignored. UI thread only.
 */
public final class DestinationPanel implements AutoCloseable {
    public static final String FORM="destination", SUMMARY="destination.summary", HOST="destination.host", CANCEL="destination.cancel", COPY="destination.copy";
    private final List<RemoteHost> saved;
    private final String summary;
    private final SftpPanel browser;
    private final SftpController controller;
    private final Executor ui;
    private final Function<UUID,CompletableFuture<ConnectionIdentity>> resolve;
    private final Consumer<SftpController.Capture> choose;
    private final Runnable cancel;
    private final UiView view;
    private CompletableFuture<ConnectionIdentity> resolving;
    private String host="";
    private boolean copyable;
    private long generation;
    private boolean closed;

    public DestinationPanel(UiKit kit,String sourceSummary,List<RemoteHost> saved,Path cache,Executor background,Executor ui,
                            Function<UUID,CompletableFuture<ConnectionIdentity>> resolve,
                            Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> open,Consumer<SftpController.Capture> choose,
                            Consumer<SftpController.Capture> newFolder,Runnable cancelOperation,Runnable cancel) {
        this.saved=List.copyOf(saved);this.summary=sourceSummary;this.ui=ui;this.resolve=resolve;this.choose=choose;this.cancel=cancel;
        UiView[] owner={null};
        browser=SftpPanel.destination(()->{ if(owner[0]!=null && !closed) owner[0].update(new ViewSpec(form())); },id->{ if(owner[0]!=null && !closed) owner[0].focus(id); });
        controller=new SftpController(cache,background,ui,open,browser);controller.foldersOnly(true);
        Runnable nothing=()->{};
        controller.operations(new SftpController.Operations(nothing,nothing,nothing,()->controller.capture().ifPresent(newFolder),nothing,nothing,nothing,cancelOperation));
        browser.onLoaded(()-> { copyable=target().isPresent();render(); });
        view=kit.create(new ViewSpec(form()),this::handle);
        owner[0]=view;
    }

    public UiView view() { return view; }
    /** Whether the browser shows hidden items at first, and who hears the user's choice when it changes. */
    public void hiddenItems(boolean shown,Consumer<Boolean> changed) { controller.showHidden(shown);controller.onShowHiddenChanged(changed); }
    public void refresh() { controller.refresh(); }
    public void status(boolean busy,String text) { if(!closed) browser.operationStatus(busy,text); }

    /** The folder Copy here would copy into: shown by the browser, on the chosen host. */
    private Optional<SftpController.Capture> target() {
        return controller.capture().filter(capture->capture.identity().host().id().toString().equals(host));
    }

    private void handle(UiEvent event) {
        if(closed) return;
        switch(event) {
            case UiEvent.Selected chosen when chosen.id().equals(HOST) -> chosen.ids().stream().findFirst().ifPresent(this::chooseHost);
            case UiEvent.Action action when action.id().equals(COPY) -> { if(copyable) target().ifPresent(choose); }
            case UiEvent.Action action when action.id().equals(CANCEL) -> cancel.run();
            default -> browser.handle(event);
        }
    }

    /** Choosing a host (again) connects and lists it afresh, as the old combo box's action did: a retry after a failure. */
    private void chooseHost(String id) {
        long token=++generation;host=id;copyable=false;controller.cancel();if(resolving!=null) resolving.cancel(true);
        var chosen=saved.stream().filter(candidate->candidate.id().toString().equals(id)).findFirst();
        render();
        if(chosen.isEmpty()) return;
        var hostId=chosen.get().id();
        browser.busy(true,"Connecting…");resolving=resolve.apply(hostId);resolving.whenComplete((identity,failure)->ui.execute(()-> {
            if(closed || token!=generation) return;
            if(failure!=null) browser.error("Cannot connect: "+String.valueOf(failure.getMessage()));else controller.open(hostId,identity,"",true);
        }));
    }

    private void render() { if(!closed && view!=null) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var hosts=saved.stream().map(candidate->new Option(candidate.id().toString(),candidate.name()+" — "+candidate.hostname(),Optional.empty())).toList();
        var sections=List.<Nodes.Node>of(
            new Nodes.Text(SUMMARY,ControlState.READY,summary,Nodes.TextRole.BODY),
            new Nodes.Choice(HOST,ControlState.READY,Nodes.ChoiceKind.DROPDOWN,hosts,host,"Destination host",""),
            browser.destinationBody());
        var actions=List.of(
            new Nodes.Button(CANCEL,ControlState.READY,"Cancel","",Nodes.ButtonKind.SECONDARY,CANCEL,""),
            new Nodes.Button(COPY,new ControlState(true,copyable && target().isPresent(),false,""),"Copy here","",Nodes.ButtonKind.SECONDARY,COPY,""));
        return new Nodes.Form(FORM,ControlState.READY,Nodes.FormKind.FULL,"Copy to host",Optional.empty(),sections,Optional.empty(),actions,CANCEL);
    }

    SftpPanel browser() { return browser; }

    @Override public void close() {
        if(closed) return;closed=true;generation++;if(resolving!=null) resolving.cancel(true);controller.close();browser.close();view.close();
    }
}
