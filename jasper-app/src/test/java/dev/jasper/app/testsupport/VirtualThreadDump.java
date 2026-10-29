package dev.jasper.app.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.PreInterruptCallback;
import org.junit.jupiter.api.extension.PreInterruptContext;

/** JUnit's standard timeout dump omits virtual threads such as command readers and writers. */
public final class VirtualThreadDump implements PreInterruptCallback {
    @Override public void beforeThreadInterrupt(PreInterruptContext interrupted, ExtensionContext context) throws Exception {
        Path output = Path.of("build/reports/tests/command-listener-threads").toAbsolutePath();
        Files.createDirectories(output);
        Path dump = Files.createTempFile(output, "threads-", ".txt");
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        Process diagnostic = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "jcmd" + suffix).toString(),
            Long.toString(ProcessHandle.current().pid()), "Thread.dump_to_file", "-overwrite", dump.toString())
            .redirectErrorStream(true).redirectOutput(output.resolve("jcmd.log").toFile()).start();
        try {
            if (diagnostic.waitFor(5, TimeUnit.SECONDS)) System.out.println(Files.readString(dump));
            else System.out.println("Virtual thread dump timed out: " + dump);
        } finally { diagnostic.destroyForcibly(); }
    }
}
