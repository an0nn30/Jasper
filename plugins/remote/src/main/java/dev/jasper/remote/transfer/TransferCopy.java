package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.store.TransferStore;
import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;

/** One bounded copy, owning no threads and retaining at most one 256-KiB application buffer. */
public final class TransferCopy {
    private static final long SEGMENT=8L*1024*1024;
    private TransferCopy() {}
    public static void copy(TransferStore store,TransferEntry entry,FileEndpoint source,FileEndpoint destination,
                            TransferControl control,BiConsumer<Long,Long> progress) throws IOException {
        control.check();
        if(entry.outcome()!=TransferEntry.Outcome.PENDING) return;
        if(entry.phase()==TransferEntry.Phase.PUBLISHING) { if(!TransferRecovery.resolvePublication(store,entry,destination,control)) return;entry=store.entry(entry.id()); }
        if(entry.decision()==ConflictDecision.SKIP) {
            try { TransferRecovery.cleanup(store,entry,destination,control); }
            catch(IOException failure) { store.cleanup(entry.id(),failure.getMessage()); }
            store.outcome(entry.id(),TransferEntry.Outcome.SKIPPED,"Skipped");return;
        }
        if(!TransferRecovery.sameSource(entry.sourceInfo(),source.stat(entry.source()))) throw new TransferRecovery.Attention("Source changed; choose Restart or Skip");
        if(entry.sourceInfo().kind()==FileEntry.Kind.SPECIAL) { store.outcome(entry.id(),TransferEntry.Outcome.FAILED,"Special files are not transferable");return; }
        var target=TransferRecovery.optional(destination,entry.target());
        if(target.isPresent() && target.orElseThrow().kind()==entry.sourceInfo().kind() && entry.decision()==ConflictDecision.ASK) {
            var policy=store.policy(entry.jobId(),entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY);
            if(policy!=ConflictDecision.ASK) {
                if(policy==ConflictDecision.SKIP) {
                    if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) store.skipTree(entry.id());
                    else { store.decision(entry.id(),policy,entry.target());copy(store,store.entry(entry.id()),source,destination,control,progress); }
                    return;
                }
                store.conflict(entry.id(),target,"");store.decision(entry.id(),policy,entry.target());entry=store.entry(entry.id());
            }
        }
        if(target.isPresent()) {
            boolean merge=entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY && target.orElseThrow().kind()==FileEntry.Kind.DIRECTORY && entry.decision()==ConflictDecision.MERGE;
            boolean replace=entry.sourceInfo().kind()!=FileEntry.Kind.DIRECTORY && target.orElseThrow().kind()==entry.sourceInfo().kind() && entry.decision()==ConflictDecision.REPLACE;
            if(!(merge || replace) || entry.expectedTarget().isEmpty() || !TransferRecovery.sameSource(entry.expectedTarget().orElseThrow(),target.orElseThrow())) {
                store.conflict(entry.id(),target,"Destination already exists"); throw new TransferRecovery.Attention("Destination already exists; choose Replace, Merge, Skip or Rename as applicable");
            }
        } else if(entry.expectedTarget().isPresent() && entry.decision()==ConflictDecision.REPLACE) {
            store.conflict(entry.id(),Optional.empty(),"Destination changed"); throw new TransferRecovery.Attention("Destination changed since the replacement decision");
        }
        if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) {
            if(target.isEmpty()) destination.mkdir(entry.target());
            store.outcome(entry.id(),TransferEntry.Outcome.COMPLETE,"");return;
        }
        if(entry.phase()==TransferEntry.Phase.PLANNED_TEMP) throw new TransferRecovery.Attention("Interrupted creation has no ownership proof; inspect the partial before Restart or Skip");
        if(entry.phase()==TransferEntry.Phase.PENDING) {
            String temporary=destination.child(destination.parent(entry.target()),".jasper-"+UUID.randomUUID()+".partial");
            store.planTemporary(entry.id(),temporary);
            if(entry.sourceInfo().kind()==FileEntry.Kind.LINK) destination.symlink(temporary,entry.sourceInfo().linkTarget());
            else try(var created=destination.write(temporary,0,true)) { created.checkpoint(); }
            store.created(entry.id(),destination.stat(temporary));entry=store.entry(entry.id());
        }
        String digest;
        if(entry.sourceInfo().kind()==FileEntry.Kind.LINK) {
            var temporary=destination.stat(entry.temporary());
            if(!TransferRecovery.owned(entry,temporary) || !entry.sourceInfo().linkTarget().equals(temporary.linkTarget())) throw new TransferRecovery.Attention("Partial link changed");
            digest=TransferRecovery.linkDigest(entry.sourceInfo().linkTarget());
        } else {
            var full=TransferRecovery.sha();
            TransferRecovery.validatePrefix(store,entry,source,destination,control,full);
            long offset=entry.confirmed(),segmentStart=offset,segmentBytes=0;var segment=TransferRecovery.sha();byte[] buffer=new byte[256*1024];
            try(var input=source.read(entry.source(),offset);var output=destination.write(entry.temporary(),offset,false)) {
                while(offset<entry.sourceInfo().size() && control.running()) {
                    int count=(int)Math.min(buffer.length,Math.min(entry.sourceInfo().size()-offset,SEGMENT-segmentBytes));
                    TransferRecovery.readFully(input,buffer,count);output.write(buffer,0,count);segment.update(buffer,0,count);full.update(buffer,0,count);offset+=count;segmentBytes+=count;
                    progress.accept(offset,entry.sourceInfo().size());
                    if(segmentBytes==SEGMENT) {
                        checkpoint(store,entry,destination,output,segmentStart,segmentBytes,TransferRecovery.hex(segment));
                        segmentStart=offset;segmentBytes=0;segment=TransferRecovery.sha();entry=store.entry(entry.id());
                    }
                }
                if(segmentBytes>0) checkpoint(store,entry,destination,output,segmentStart,segmentBytes,TransferRecovery.hex(segment));
                output.checkpoint();control.check();
            }
            if(!TransferRecovery.sameSource(entry.sourceInfo(),source.stat(entry.source()))) throw new TransferRecovery.Attention("Source changed during copy; choose Restart or Skip");
            digest=TransferRecovery.hex(full);
            try { destination.metadata(entry.temporary(),entry.sourceInfo().modifiedMillis(),entry.sourceInfo().permissions()); }
            catch(IOException | UnsupportedOperationException unsupported) { store.warning(entry.id(),"Metadata warning: "+unsupported.getMessage()); }
            store.refreshTemporary(entry.id(),destination.stat(entry.temporary()));
        }
        control.check();entry=store.entry(entry.id());
        var current=TransferRecovery.optional(destination,entry.target());
        if(current.isPresent()!=target.isPresent() || (current.isPresent() && !TransferRecovery.sameSource(target.orElseThrow(),current.orElseThrow()))) {
            store.conflict(entry.id(),current,"Destination changed while copying");throw new TransferRecovery.Attention("Destination changed while copying; review the conflict again");
        }
        var method=target.isPresent()?TransferEntry.Publication.ATOMIC_REPLACE:destination.id().equals("local")?
            (entry.sourceInfo().kind()==FileEntry.Kind.LINK?TransferEntry.Publication.LOCAL_SYMLINK:TransferEntry.Publication.LOCAL_LINK):TransferEntry.Publication.RENAME;
        store.publishing(entry.id(),digest,method,target);
        destination.publish(entry.temporary(),entry.target(),target.isPresent());
        store.outcome(entry.id(),TransferEntry.Outcome.COMPLETE,"");
    }
    private static void checkpoint(TransferStore store,TransferEntry entry,FileEndpoint destination,WriteHandle output,long start,long length,String digest) throws IOException {
        long confirmed=output.checkpoint();if(confirmed!=start+length) throw new IOException("Destination did not acknowledge the checkpoint");
        var now=destination.stat(entry.temporary());
        var proof=entry.temporaryInfo().orElseThrow();
        if(now.kind()!=FileEntry.Kind.FILE || (!proof.fileKey().isEmpty() && !proof.fileKey().equals(now.fileKey())) || now.size()!=confirmed)
            throw new TransferRecovery.Attention("Partial file changed while copying");
        store.checkpoint(entry.id(),start,length,digest,now);
    }
}
