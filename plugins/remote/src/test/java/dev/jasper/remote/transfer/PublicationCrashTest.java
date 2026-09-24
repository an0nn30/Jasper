package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.transfer.store.TransferStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PublicationCrashTest {
    @ParameterizedTest @ValueSource(strings={"planned","created","checkpoint","intent","link","published","replace"})
    void killedProcessReconcilesEachDurableBoundary(String stage,@TempDir Path root) throws Exception {
        root=root.toRealPath();var locations=new LinkedHashSet<String>();
        for(Class<?> type:List.of(PublicationCrashProbe.class,TransferStore.class,org.sqlite.JDBC.class,org.slf4j.Logger.class,org.apache.sshd.sftp.common.SftpException.class))
            locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-Djava.awt.headless=true","-cp",String.join(java.io.File.pathSeparator,locations),PublicationCrashProbe.class.getName(),root.toString(),stage).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            var line=CompletableFuture.supplyAsync(()-> { try { return child.inputReader().readLine(); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }).get(10,TimeUnit.SECONDS);
            assertThat(line).isNotNull();String[] fields=line.split(" ");UUID job=UUID.fromString(fields[0]);long id=Long.parseLong(fields[1]);
            child.destroyForcibly();assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();
            try(var db=new TransferStore(root.resolve("queue"));var source=new LocalEndpoint();var destination=new LocalEndpoint()) {
                assertThat(db.job(job).state()).isEqualTo(TransferState.PAUSED);
                if(stage.equals("planned")) {
                    assertThatThrownBy(()->TransferCopy.copy(db,db.entry(id),source,destination,new TransferControl(),(done,total)->{})).isInstanceOf(TransferRecovery.Attention.class);
                    assertThat(root.resolve("destination/.partial")).exists();assertThat(root.resolve("destination/source")).doesNotExist();
                } else {
                    TransferCopy.copy(db,db.entry(id),source,destination,new TransferControl(),(done,total)->{});
                    assertThat(db.entry(id).outcome()).isEqualTo(TransferEntry.Outcome.COMPLETE);
                    assertThat(Files.readAllBytes(root.resolve("destination/source"))).containsExactly(1,2,3);
                    assertThat(root.resolve("destination/.partial")).doesNotExist();
                }
                assertThat(Files.readAllBytes(root.resolve("source"))).containsExactly(1,2,3);
            }
        } finally { child.destroyForcibly(); }
    }
}
