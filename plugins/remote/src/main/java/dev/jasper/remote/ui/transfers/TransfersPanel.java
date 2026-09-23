package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.table.*;

/** Two paged native tables, never one component/future per discovered file. */
public final class TransfersPanel extends JPanel {
    public record Actions(Consumer<UUID> selected,Consumer<UUID> pause,Consumer<UUID> resume,Consumer<UUID> cancel,Consumer<UUID> retry,
                          Consumer<UUID> cleanup,Consumer<UUID> clear,BiConsumer<TransferEntry,ConflictDecision> resolve,Consumer<TransferEntry> restart,
                          Consumer<Long> jobsPage,Consumer<Long> entriesPage) {}
    private final JTable jobs=new ReadableTable(),entries=new ReadableTable();
    private final Jobs jobModel=new Jobs();private final Entries entryModel=new Entries();
    private final JLabel message=new JLabel(""),details=new JLabel("Select a transfer to inspect its files");
    private final JCheckBox remaining=new JCheckBox("Apply to remaining conflicts of this type");
    private final Map<String,JButton> controls=new HashMap<>();
    private List<TransferJob> jobRows=List.of();private List<TransferEntry> entryRows=List.of();
    private Actions actions;private boolean updating;private long jobOffset,entryOffset;
    public TransfersPanel(Function<IconName,Icon> icons) {
        super(new BorderLayout(4,6));setBorder(BorderFactory.createEmptyBorder(6,8,6,8));message.putClientProperty("html.disable",true);details.putClientProperty("html.disable",true);
        var toolbar=new JPanel(new GridLayout(0,5,4,4));
        add(toolbar,"Pause",IconName.PAUSE,icons,()->selectedJob().ifPresent(actions.pause()));add(toolbar,"Resume",IconName.RESUME,icons,()->selectedJob().ifPresent(actions.resume()));
        add(toolbar,"Cancel",IconName.CLOSE,icons,()->selectedJob().ifPresent(actions.cancel()));add(toolbar,"Retry",IconName.REFRESH,icons,()->selectedJob().ifPresent(actions.retry()));
        add(toolbar,"Retry cleanup",IconName.DELETE,icons,()->selectedJob().ifPresent(actions.cleanup()));add(toolbar,"Clear",IconName.REMOVE,icons,()->selectedJob().ifPresent(actions.clear()));
        add(toolbar,"Previous jobs",IconName.UP,icons,()->actions.jobsPage().accept(Math.max(0,jobOffset-50)));add(toolbar,"Next jobs",IconName.DOWNLOAD,icons,()->actions.jobsPage().accept(jobOffset+50));add(toolbar,BorderLayout.NORTH);
        jobs.setModel(jobModel);entries.setModel(entryModel);jobs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);entries.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        var fileArea=new JPanel(new BorderLayout(4,4));fileArea.add(details,BorderLayout.NORTH);fileArea.add(new JScrollPane(entries),BorderLayout.CENTER);
        var fileButtons=new JPanel(new GridLayout(0,5,4,4));
        for(var decision:List.of(ConflictDecision.REPLACE,ConflictDecision.MERGE,ConflictDecision.SKIP,ConflictDecision.RENAME)) {
            String label=decision.name().substring(0,1)+decision.name().substring(1).toLowerCase(Locale.ROOT);
            add(fileButtons,label,decision==ConflictDecision.SKIP?IconName.REMOVE:IconName.COPY,icons,()->selectedEntry().ifPresent(entry->actions.resolve().accept(entry,decision)));
        }
        add(fileButtons,"Restart file",IconName.REFRESH,icons,()->selectedEntry().ifPresent(actions.restart()));
        add(fileButtons,"Previous files",IconName.UP,icons,()->actions.entriesPage().accept(Math.max(0,entryOffset-200)));add(fileButtons,"Next files",IconName.DOWNLOAD,icons,()->actions.entriesPage().accept(entryOffset+200));var fileFooter=new JPanel(new BorderLayout(4,4));fileFooter.add(fileButtons,BorderLayout.CENTER);fileFooter.add(remaining,BorderLayout.SOUTH);fileArea.add(fileFooter,BorderLayout.SOUTH);
        var split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(jobs),fileArea);split.setResizeWeight(.45);split.setBorder(BorderFactory.createEmptyBorder());add(split,BorderLayout.CENTER);add(message,BorderLayout.SOUTH);
        jobs.getSelectionModel().addListSelectionListener(event->{if(!updating && !event.getValueIsAdjusting()) { refreshControls();if(actions!=null) selectedJob().ifPresent(actions.selected()); }});
        entries.getSelectionModel().addListSelectionListener(event->refreshControls());refreshControls();setPreferredSize(new Dimension(760,320));
    }
    private void add(JPanel panel,String text,IconName name,Function<IconName,Icon> icons,Runnable action) {
        var button=new JButton(text,icons.apply(name));button.setToolTipText(text);button.addActionListener(event->{if(actions!=null) action.run();});controls.put(text,button);panel.add(button);
    }
    public void actions(Actions actions) { this.actions=actions; }
    public JTable jobsTable() { return jobs; }
    public Optional<UUID> selectedJob() { int row=jobs.getSelectedRow();return row<0 || row>=jobRows.size()?Optional.empty():Optional.of(jobRows.get(row).id()); }
    public Optional<TransferEntry> selectedEntry() { int row=entries.getSelectedRow();return row<0 || row>=entryRows.size()?Optional.empty():Optional.of(entryRows.get(row)); }
    public boolean applyRemaining() { return remaining.isSelected(); }
    public Optional<TransferJob> job(UUID id) { return jobRows.stream().filter(job->job.id().equals(id)).findFirst(); }
    public void jobs(List<TransferJob> values,long offset) {
        var selected=selectedJob();updating=true;jobRows=List.copyOf(values);jobOffset=offset;jobModel.fireTableDataChanged();
        int row=0;if(selected.isPresent()) for(int i=0;i<jobRows.size();i++) if(jobRows.get(i).id().equals(selected.orElseThrow())) row=i;
        if(!jobRows.isEmpty()) jobs.setRowSelectionInterval(row,row);updating=false;refreshControls();
        if(!Objects.equals(selected,selectedJob()) && actions!=null) selectedJob().ifPresent(actions.selected());
    }
    public void entries(UUID job,List<TransferEntry> values,long offset) {
        if(!selectedJob().filter(job::equals).isPresent()) return;var selected=selectedEntry().map(TransferEntry::id);entryRows=List.copyOf(values);entryOffset=offset;entryModel.fireTableDataChanged();
        int row=0;if(selected.isPresent()) for(int i=0;i<entryRows.size();i++) if(entryRows.get(i).id()==selected.orElseThrow()) row=i;if(!entryRows.isEmpty()) entries.setRowSelectionInterval(row,row);
        var summary=job(job);details.setText(summary.map(j->j.completedEntries()+" completed · "+j.skippedEntries()+" skipped · "+j.failedEntries()+" failed · "+j.cleanupPending()+" cleanup pending · "+j.metadataWarnings()+" metadata warnings"+(j.detail().isEmpty()?"":" — "+j.detail())).orElse(""));refreshControls();
    }
    public void error(String error) { message.setText(error);message.setToolTipText(error); }
    private void refreshControls() {
        var selected=selectedJob().flatMap(this::job);var job=selected.orElse(null);boolean stopped=job!=null && (job.state()==TransferState.PAUSED || job.state()==TransferState.NEEDS_ATTENTION || job.state()==TransferState.INTERRUPTED || job.state()==TransferState.FAILED);
        for(String name:List.of("Pause","Resume","Cancel","Retry","Retry cleanup","Clear")) if(controls.containsKey(name)) controls.get(name).setEnabled(job!=null);
        if(job!=null) { controls.get("Pause").setEnabled(!job.state().terminal() && !stopped);controls.get("Resume").setEnabled(stopped && job.intent()!=TransferJob.Intent.CANCEL);controls.get("Retry").setEnabled(stopped && job.intent()!=TransferJob.Intent.CANCEL);controls.get("Cancel").setEnabled(!job.state().terminal());controls.get("Clear").setEnabled(job.state().terminal());controls.get("Retry cleanup").setEnabled(job.cleanupPending()>0); }
        var entry=selectedEntry().orElse(null);boolean resolve=stopped && entry!=null && selectedJob().filter(entry.jobId()::equals).isPresent() && entry.outcome()==TransferEntry.Outcome.PENDING;
        for(String name:List.of("Replace","Merge","Skip","Rename","Restart file")) if(controls.containsKey(name)) controls.get(name).setEnabled(resolve);
        if(entry!=null) { controls.get("Restart file").setEnabled((resolve || job!=null && job.state()==TransferState.COMPLETED_WITH_ISSUES && entry.outcome()==TransferEntry.Outcome.FAILED) && entry.outcome()!=TransferEntry.Outcome.SKIPPED);controls.get("Replace").setEnabled(resolve && entry.sourceInfo().kind()!=FileEntry.Kind.DIRECTORY && entry.expectedTarget().filter(t->t.kind()==entry.sourceInfo().kind()).isPresent());controls.get("Merge").setEnabled(resolve && entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY && entry.expectedTarget().filter(t->t.kind()==FileEntry.Kind.DIRECTORY).isPresent()); }
        if(controls.containsKey("Previous jobs")) { controls.get("Previous jobs").setEnabled(jobOffset>0);controls.get("Next jobs").setEnabled(jobRows.size()==50);controls.get("Previous files").setEnabled(entryOffset>0);controls.get("Next files").setEnabled(entryRows.size()==200); }
    }
    private final class Jobs extends AbstractTableModel {
        public int getRowCount(){return jobRows.size();}public int getColumnCount(){return 4;}public String getColumnName(int c){return new String[]{"Source","Destination","State","Progress"}[c];}
        public Object getValueAt(int r,int c){var j=jobRows.get(r);return switch(c){case 0->j.source();case 1->j.destination();case 2->TransferPresentation.state(j.state());default->TransferPresentation.bytes(j.confirmedBytes())+" / "+TransferPresentation.bytes(j.totalBytes());};}
    }
    private final class Entries extends AbstractTableModel {
        public int getRowCount(){return entryRows.size();}public int getColumnCount(){return 4;}public String getColumnName(int c){return new String[]{"File","State","Confirmed","Details"}[c];}
        public Object getValueAt(int r,int c){var e=entryRows.get(r);return switch(c){case 0->e.relative();case 1->e.outcome()==TransferEntry.Outcome.PENDING?e.phase().name():e.outcome().name();case 2->TransferPresentation.bytes(e.confirmed());default->e.error();};}
    }
    private static final class ReadableTable extends JTable {
        ReadableTable(){setFillsViewportHeight(true);setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){ {putClientProperty("html.disable",true);} });}
        @Override public void setFont(Font font){super.setFont(font);if(font!=null)setRowHeight(Math.max(20,getFontMetrics(font).getHeight()+4));}
    }
}
