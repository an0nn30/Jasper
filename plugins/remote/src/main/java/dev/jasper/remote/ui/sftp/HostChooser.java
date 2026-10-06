package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Browse files on SSH host" on a host-rendered kit form: the saved hosts ("name — hostname"), the first chosen, and
 * Browse files, which is enabled while there is a host. As before there is no Cancel button and no default button, so
 * Enter browses nothing; Escape closes the dialog. Host text is literal. UI thread only; {@link #close()} makes late
 * callbacks inert.
 */
final class HostChooser implements AutoCloseable {
    static final String FORM="browse-host", HOST="browse-host.host", BROWSE="browse-host.browse", CLOSE="browse-host.close";
    private final List<RemoteHost> hosts;
    private final UiView view;
    private String chosen;
    private boolean closed;

    HostChooser(UiKit kit,List<RemoteHost> hosts,Consumer<RemoteHost> browse,Runnable close) {
        this.hosts=List.copyOf(hosts);
        chosen=this.hosts.isEmpty()?"":this.hosts.getFirst().id().toString();
        view=kit.create(new ViewSpec(form()),event-> {
            if(closed) return;
            switch(event) {
                case UiEvent.Selected selected when selected.id().equals(HOST) -> selected.ids().stream().findFirst().ifPresent(id-> { chosen=id;render(); });
                case UiEvent.Action action when action.id().equals(BROWSE) -> host().ifPresent(browse);
                case UiEvent.Action action when action.id().equals(CLOSE) -> close.run();
                default -> { }
            }
        });
    }

    UiView view() { return view; }

    private Optional<RemoteHost> host() { return hosts.stream().filter(host->host.id().toString().equals(chosen)).findFirst(); }

    private void render() { if(!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var options=hosts.stream().map(host->new Option(host.id().toString(),host.name()+" — "+host.hostname(),Optional.empty())).toList();
        return new Nodes.Form(FORM,ControlState.READY,Nodes.FormKind.FULL,"Browse files on SSH host",Optional.empty(),
            List.of(new Nodes.Choice(HOST,ControlState.READY,Nodes.ChoiceKind.DROPDOWN,options,chosen,"SSH host","")),Optional.empty(),
            List.of(new Nodes.Button(BROWSE,new ControlState(true,host().isPresent(),false,""),"Browse files","",Nodes.ButtonKind.SECONDARY,BROWSE,"")),CLOSE);
    }

    @Override public void close() { if(closed) return;closed=true;view.close(); }
}
