package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.store.TransferStore;
import java.io.*;
import java.util.*;

/** One streaming scanner with a durable, replayable breadth-first frontier and bounded SQL batches. */
public final class TransferScan {
    private TransferScan() {}
    public static void scan(TransferStore store,UUID job,FileEndpoint source,FileEndpoint destination,TransferControl control) throws IOException {
        var request=store.request(job);
        if(destination.stat(request.directory()).kind()!=FileEntry.Kind.DIRECTORY) throw new TransferRecovery.Attention("Destination is not a directory");
        for(String selected:request.paths()) {
            control.check();var info=source.stat(selected);String target=destination.child(request.directory(),info.name());
            var read=new PathReservations.Key(source.id(),source.canonical(selected),false);
            var write=new PathReservations.Key(destination.id(),destination.canonical(target),true);
            if(PathReservations.overlaps(read,write)) throw new TransferRecovery.Attention("Destination overlaps the source");
            store.discover(job,List.of(new TransferStore.Discovered(info.name(),selected,target,info)),true);
        }
        while(true) {
            control.check();var page=store.frontier(job,1);if(page.isEmpty()) break;var next=page.getFirst();
            var expected=source.stat(next.source());if(expected.kind()!=FileEntry.Kind.DIRECTORY) throw new TransferRecovery.Attention("Source directory changed into a link or file");
            var batch=new ArrayList<TransferStore.Discovered>(256);
            try {
                source.list(next.source(),info -> {
                    try {
                        control.check();String from=source.child(next.source(),info.name()),to=destination.child(next.target(),info.name());
                        String relative=next.relative()+"/"+info.name();
                        batch.add(new TransferStore.Discovered(relative,from,to,info));
                        if(batch.size()==256) { store.discover(job,batch,true);batch.clear(); }
                    } catch(IOException error) { throw new UncheckedIOException(error); }
                });
            } catch(UncheckedIOException failure) { throw failure.getCause(); }
            if(!batch.isEmpty()) store.discover(job,batch,true);
            var after=source.stat(next.source());
            if(after.kind()!=FileEntry.Kind.DIRECTORY || (!expected.fileKey().isEmpty() && !expected.fileKey().equals(after.fileKey()))) throw new TransferRecovery.Attention("Source directory changed during discovery");
            store.finishFrontier(next.id());
        }
        store.scanned(job);
    }
}
