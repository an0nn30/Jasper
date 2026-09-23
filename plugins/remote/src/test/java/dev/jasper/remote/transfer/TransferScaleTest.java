package dev.jasper.remote.transfer;

import dev.jasper.remote.transfer.store.TransferStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TransferScaleTest {
    @Test void greaterThanFourGiBAndHundredThousandEntriesFitIn64MiBHeap(@TempDir Path root) throws Exception {
        var locations=new LinkedHashSet<String>();
        for(Class<?> type:List.of(TransferScaleProbe.class,TransferStore.class,org.sqlite.JDBC.class,org.slf4j.Logger.class,org.apache.sshd.sftp.common.SftpException.class))
            locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        Path output=root.resolve("probe.log");
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-Djava.awt.headless=true","-cp",String.join(java.io.File.pathSeparator,locations),TransferScaleProbe.class.getName(),root.resolve("queue").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try { assertThat(child.waitFor(90,TimeUnit.SECONDS)).as("bounded scale probe deadline").isTrue();assertThat(child.exitValue()).as(Files.readString(output)).isZero();assertThat(Files.readString(output)).contains("VERIFIED 4294967313 bytes; 100001 entries"); }
        finally { child.destroyForcibly(); }
    }
}
