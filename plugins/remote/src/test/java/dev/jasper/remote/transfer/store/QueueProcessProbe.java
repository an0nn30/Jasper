package dev.jasper.remote.transfer.store;

import dev.jasper.remote.transfer.*;
import java.nio.file.*;
import java.util.List;

/** Headless child process used to test operating-system locks and abrupt writer death. */
public final class QueueProcessProbe {
    private QueueProcessProbe() {}
    public static void main(String[] args) throws Exception {
        try(var store=new TransferStore(Path.of(args[0]))) {
            var id=store.create(new TransferRequest(EndpointRef.local(),List.of("/source"),EndpointRef.local(),"/destination"));
            store.markRunning(id);
            System.out.println(id); System.out.flush();
            System.in.read();
        }
    }
}
