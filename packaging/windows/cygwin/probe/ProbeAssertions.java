import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Fail-closed evidence checks; Java assertions need not be enabled. */
final class ProbeAssertions {
    static void installer(Map<String, String> evidence) {
        String user = evidence.get("installingUser");
        if (user == null || user.isBlank() || !user.equals(evidence.get("helperUser")))
            throw new IllegalArgumentException("Missing or mismatched installing/helper identity");
        for (String key : new String[]{"helperFilesAvailable", "helperFinishedBeforeInstallerSuccess",
                "silentModeRanHelper", "helperFailureFailedInstaller", "uninstallPreservedSentinel",
                "upgradePreservedSentinel", "repairRanHelper"}) {
            if (!"true".equals(evidence.get(key))) throw new IllegalArgumentException("Missing or failed evidence: " + key);
        }
    }
    static void await(BooleanSupplier condition, Duration timeout, String description) throws Exception {
        long start = System.nanoTime();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - start >= timeout.toNanos()) throw new TimeoutException(description);
            Thread.sleep(10);
        }
    }
    static Path freshRoot(String value) throws IOException {
        Path root = Path.of(value);
        if (!root.isAbsolute() || !root.normalize().equals(root) || root.getParent() == null)
            throw new IllegalArgumentException("Probe root must be an absolute normalized new directory");
        for (Path parent = root.getParent(); parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent) || Files.readAttributes(parent, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isOther()) throw new IOException("Linked probe ancestor: " + parent);
        }
        Files.createDirectory(root); // Fails if anything already occupies it; never delete or adopt a tree.
        Files.writeString(root.resolve(".jasper-cygwin-probe"), "scratch probe v1\n");
        return root;
    }
    static void require(boolean condition, String description) throws IOException {
        if (!condition) throw new IOException(description);
    }
}
