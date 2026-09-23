package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** One aggregate status handle and independent paged views over the plugin-owned queue. */
public final class TransferUi implements AutoCloseable {
    public static final String SHOW="dev.jasper.remote.transfers",TOGGLE=SHOW+".toggle",CANCEL=SHOW+".cancel",PANEL=SHOW+".panel";
    private final PluginContext context;private final TransferCoordinator coordinator;private final Executor ui;
    private final Map<UUID,View> views=new HashMap<>();private final TransferPresentation presentation=new TransferPresentation();
    private final StatusProgress status;private final javax.swing.Timer timer;private PluginAction toggle;
    private TransferCoordinator.Snapshot latest;private boolean statusPending;private volatile boolean closed;
    private static final class View {
        final PanelHost host;final TransfersPanel panel;long offset,entryOffset;UUID job;boolean pending,entryPending;long generation;
        View(PanelHost host,TransfersPanel panel){this.host=host;this.panel=panel;}
    }
    public TransferUi(PluginContext context,TransferCoordinator coordinator,Executor ui) {
        this.context=context;this.coordinator=coordinator;this.ui=ui;
        context.actions().register(ActionSpec.of(SHOW,"Transfers").withIcon(context.appearance().icon(IconName.DOWNLOAD)).withKeywords(List.of("sftp","upload","download","queue")),invoked->show(invoked.window()));
        context.actions().register(ActionSpec.of(CANCEL,"Cancel transfer").withIcon(context.appearance().icon(IconName.CLOSE)),invoked->{if(latest!=null && latest.activeJobs().size()==1) coordinator.cancel(latest.activeJobs().getFirst());else show(invoked.window());});
        configureBinding(null);context.panels().register(new PanelSpec(PANEL,"Transfers",context.appearance().icon(IconName.DOWNLOAD),Anchor.BOTTOM),this::create);
        status=context.statusBar().addProgress(new StatusItemSpec(SHOW+".progress",Side.RIGHT,65));status.setVisible(false);
        timer=new javax.swing.Timer(200,event->refresh());timer.start();refresh();
    }
    public void configureBinding(String binding) { if(toggle!=null) toggle.close();toggle=context.actions().register(ActionSpec.of(TOGGLE,"Transfers panel").withDefaultBinding(binding),invoked->context.panels().toggle(PANEL,invoked.window())); }
    public void show(WindowHandle window) { if(closed) return;var view=views.get(window.id());if(view==null) context.panels().toggle(PANEL,window);else view.host.show();refresh(); }
    private JComponent create(PanelHost host) {
        var panel=new TransfersPanel(context.appearance()::icon);var view=new View(host,panel);views.put(host.window().id(),view);
        panel.actions(new TransfersPanel.Actions(id->{view.job=id;view.entryOffset=0;view.generation++;entries(view);},coordinator::pause,id->report(view,coordinator.resume(id,host.window())),coordinator::cancel,
            id->report(view,coordinator.retry(id,host.window())),id->report(view,coordinator.cleanup(id,host.window())),id->clear(view,id),
            (entry,decision)->resolve(view,entry,decision),entry->report(view,coordinator.restart(entry.id(),host.window())),offset->{view.offset=offset;refreshView(view);},offset->{view.entryOffset=offset;view.generation++;entries(view);}));
        host.onVisibility(visible->{if(visible)refreshView(view);});host.onClosed(()->{views.remove(host.window().id());view.generation++;});refreshView(view);return panel;
    }
    private void refresh() {
        if(closed) return;
        if(!statusPending) { statusPending=true;coordinator.snapshot(0,1).whenComplete((snapshot,error)->ui.execute(()-> {
            statusPending=false;if(closed)return;
            if(error!=null) { status.update(new StatusProgressState("Transfers unavailable",message(error),message(error),OptionalDouble.empty(),SHOW,null));status.setVisible(true);return; }
            latest=snapshot;status.update(presentation.update(snapshot,System.nanoTime()));var summary=snapshot.summary();status.setVisible(summary.runnable()>0 || summary.paused()>0 || summary.attention()>0);
        })); }
        for(var view:List.copyOf(views.values())) if(view.host.visible()) refreshView(view);
    }
    private void refreshView(View view) {
        if(closed || view.pending) return;view.pending=true;long offset=view.offset;
        coordinator.snapshot(offset,50).whenComplete((snapshot,error)->ui.execute(()-> { view.pending=false;if(closed || views.get(view.host.window().id())!=view || view.offset!=offset)return;
            if(error!=null) view.panel.error(message(error));else { view.panel.jobs(snapshot.jobs(),offset);entries(view); }
        }));
    }
    private void entries(View view) {
        if(closed || view.job==null || view.entryPending)return;view.entryPending=true;long token=view.generation,offset=view.entryOffset;UUID job=view.job;
        coordinator.entries(job,offset,200).whenComplete((rows,error)->ui.execute(()-> {view.entryPending=false;if(closed || views.get(view.host.window().id())!=view || view.generation!=token)return;
            if(error!=null)view.panel.error(message(error));else view.panel.entries(job,rows,offset);
        }));
    }
    private void report(View view,CompletableFuture<?> operation) { operation.whenComplete((ignored,error)->ui.execute(()-> {if(closed)return;if(error!=null)view.panel.error(message(error));else view.panel.error("");refreshView(view);})); }
    private void resolve(View view,TransferEntry entry,ConflictDecision decision) {
        if(decision==ConflictDecision.RENAME) {
            var dialog=context.windows().dialog(new DialogSpec("Rename destination",view.host.window(),false));var body=new JPanel(new BorderLayout(6,6));body.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));var name=new JTextField(entry.sourceInfo().name(),24);var apply=new JButton("Use name");body.add(name,BorderLayout.CENTER);body.add(apply,BorderLayout.SOUTH);apply.addActionListener(event->{dialog.close();resolved(view,entry,decision,name.getText(),false);});dialog.setContent(body);dialog.show();
        } else resolved(view,entry,decision,null,view.panel.applyRemaining());
    }
    private void resolved(View view,TransferEntry entry,ConflictDecision decision,String name,boolean remaining) {
        coordinator.resolve(entry.id(),decision,name,remaining).whenComplete((ignored,error)->ui.execute(()-> {
            if(closed)return;if(error!=null) {view.panel.error(message(error));return;}report(view,coordinator.resume(entry.jobId(),view.host.window()));
        }));
    }
    private void clear(View view,UUID id) {
        var job=view.panel.job(id);if(job.isEmpty())return;
        if(job.orElseThrow().cleanupPending()==0) {report(view,coordinator.clear(id,false));return;}
        var dialog=context.windows().dialog(new DialogSpec("Clear transfer history",view.host.window(),false));var panel=new dev.jasper.remote.ui.ConfirmPanel("Unfinished cleanup will be forgotten. Partial files may remain. Clear this history?","Clear history",()->{dialog.close();report(view,coordinator.clear(id,true));},dialog::close);dialog.setContent(panel);dialog.show();
    }
    private static String message(Throwable error) {while((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause()!=null)error=error.getCause();return error.getMessage()==null?"Transfer operation failed":error.getMessage();}
    @Override public void close() { if(closed)return;closed=true;timer.stop();status.close();views.clear(); }
}
