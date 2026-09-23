package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.ui.StatusProgressState;
import java.util.*;

/** Pure aggregate status formatting, with a smoothed payload-only rate and 64-bit byte counts. */
public final class TransferPresentation {
    private long lastBytes,lastTime;private double speed;private Set<UUID> lastJobs=Set.of();
    public StatusProgressState update(TransferCoordinator.Snapshot snapshot,long now) {
        var summary=snapshot.summary();var jobs=Set.copyOf(snapshot.activeJobs());
        long done=summary.confirmedBytes();
        boolean transferring=summary.runnable()>0 && summary.scanning()==0;
        if(!transferring || !lastJobs.equals(jobs) || done<lastBytes) { speed=0;lastTime=0; }
        if(lastTime>0 && now>lastTime) { double instant=Math.max(0,done-lastBytes)*1_000_000_000.0/(now-lastTime);speed=speed==0?instant:speed*.7+instant*.3; }
        lastTime=transferring?now:0;lastBytes=done;lastJobs=jobs;
        String text,detail;OptionalDouble fraction=OptionalDouble.empty();
        if(summary.scanning()>0) { text="Scanning…";detail=summary.runnable()+" transfers"; }
        else if(summary.runnable()>0) {
            text=summary.runnable()+" transfer"+(summary.runnable()==1?"":"s");detail=bytes((long)speed)+"/s · "+bytes(Math.max(0,summary.totalBytes()-done))+" left · "+summary.remainingFiles()+" files";
            if(summary.totalBytes()>0) fraction=OptionalDouble.of(Math.clamp((double)done/summary.totalBytes(),0,1));
        } else { text="Transfers";detail=summary.attention()>0?summary.attention()+" need attention":summary.paused()>0?summary.paused()+" paused":"No active transfers"; }
        return new StatusProgressState(text,detail,text+", "+detail,fraction,TransferUi.SHOW,summary.runnable()>0?TransferUi.CANCEL:null);
    }
    public static String bytes(long value) {
        if(value<1024) return value+" B";if(value<1024L*1024) return String.format(Locale.ROOT,"%.1f KiB",value/1024.0);
        if(value<1024L*1024*1024) return String.format(Locale.ROOT,"%.1f MiB",value/(1024.0*1024));return String.format(Locale.ROOT,"%.1f GiB",value/(1024.0*1024*1024));
    }
    public static String state(TransferState state) { return switch(state) {
        case QUEUED->"Queued";case SCANNING->"Scanning";case VALIDATING->"Checking partial file";case RUNNING->"Transferring";case PAUSING->"Pausing";case PAUSED->"Paused";case CANCELLING->"Cancelling";case CANCELLED->"Cancelled";case COMPLETED->"Completed";case COMPLETED_WITH_ISSUES->"Completed with issues";case NEEDS_ATTENTION->"Needs attention";case INTERRUPTED->"Interrupted";case FAILED->"Failed";
    }; }
}
