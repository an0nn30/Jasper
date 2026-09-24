package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.transfer.store.TransferStore;
import java.nio.file.*;
import java.util.*;

/** Leaves a real durable protocol boundary for the parent to kill without shutdown hooks. */
public final class PublicationCrashProbe {
    private PublicationCrashProbe() {}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toRealPath(),source=Files.write(root.resolve("source"),new byte[]{1,2,3}),directory=Files.createDirectory(root.resolve("destination"));
        Path temp=directory.resolve(".partial"),target=directory.resolve("source");String stage=args[1];
        try(var endpoint=new LocalEndpoint();var db=new TransferStore(root.resolve("queue"))) {
            var job=db.create(new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),directory.toString()));db.markRunning(job);
            db.discover(job,List.of(new TransferStore.Discovered("source",source.toString(),target.toString(),endpoint.stat(source.toString()))));
            long id=db.entries(job,0,1).getFirst().id();db.planTemporary(id,temp.toString());
            try(var output=endpoint.write(temp.toString(),0,true)) { output.checkpoint(); }
            if(!stage.equals("planned")) db.created(id,endpoint.stat(temp.toString()));
            if(!stage.equals("planned") && !stage.equals("created")) {
                try(var output=endpoint.write(temp.toString(),0,false)) { output.write(new byte[]{1,2,3});output.checkpoint(); }
                String hash=TransferRecovery.digest(endpoint,temp.toString(),new TransferControl());db.checkpoint(id,0,3,hash,endpoint.stat(temp.toString()));
                if(!stage.equals("checkpoint")) {
                    boolean replace=stage.equals("replace");if(replace) Files.write(target,new byte[]{9,9,9});
                    db.publishing(id,hash,replace?TransferEntry.Publication.ATOMIC_REPLACE:TransferEntry.Publication.LOCAL_LINK,replace?Optional.of(endpoint.stat(target.toString())):Optional.empty());
                    if(stage.equals("link")) Files.createLink(target,temp);
                    if(stage.equals("published") || replace) endpoint.publish(temp.toString(),target.toString(),replace);
                }
            }
            System.out.println(job+" "+id);System.out.flush();System.in.read();
        }
    }
}
