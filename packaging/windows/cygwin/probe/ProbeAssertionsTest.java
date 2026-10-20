import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProbeAssertionsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("runtime")) {
            var temp = Files.createTempDirectory("probe-runtime-test-").toRealPath();
            try {
                var image = temp.resolve("runtime");
                var command = new java.util.ArrayList<>(java.util.List.of(
                    java.nio.file.Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "jlink.exe" : "jlink").toString(),
                    "--add-modules", "java.base", "--output", image.toString()));
                command.addAll(java.util.List.of(InstallerProbe.runtimeOptions().split(" ")));
                ProbeSupport.success(command, temp.resolve("jlink.log"));
                if (!Files.isRegularFile(image.resolve(System.getProperty("os.name").startsWith("Windows") ? "bin/java.exe" : "bin/java")))
                    throw new AssertionError("Packaged helper runtime has no java executable");
            } finally {
                try (var paths = Files.walk(temp)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
            System.out.println("Cygwin helper runtime check passed"); return;
        }
        if (!ConPtyProbe.quietMode().equals("hidden")) throw new AssertionError("Setup would open its wizard");
        checks++;
        String template = "<Wix><Product><InstallExecuteSequence></InstallExecuteSequence></Product></Wix>";
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
            InstallerProbe.withHook(template, "a & b").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var actions = doc.getElementsByTagName("CustomAction");
        if (actions.getLength() != 1) throw new AssertionError("No installer provisioning action");
        var action = (org.w3c.dom.Element) actions.item(0);
        if (!action.getAttribute("Return").equals("check") || !action.getAttribute("Execute").equals("deferred")
            || !action.getAttribute("Impersonate").equals("yes")) throw new AssertionError("Unsafe action scheduling");
        if (!action.getAttribute("ExeCommand").endsWith("a & b")) throw new AssertionError("Arguments corrupted");
        rejects(() -> InstallerProbe.withHook("<Wix/>", "args"), "unknown template");
        checks += 2;
        var complete = new LinkedHashMap<String, String>(Map.of(
            "installingUser", "S-1-5-21-test", "helperUser", "S-1-5-21-test",
            "helperFilesAvailable", "true", "helperFinishedBeforeInstallerSuccess", "true",
            "silentModeRanHelper", "true", "helperFailureFailedInstaller", "true",
            "uninstallPreservedSentinel", "true", "upgradePreservedSentinel", "true",
            "repairRanHelper", "true"));
        rejects(() -> ProbeAssertions.installer(Map.of()), "missing installer evidence");
        ProbeAssertions.installer(complete); checks++;
        for (String key : complete.keySet()) {
            var missing = new LinkedHashMap<>(complete); missing.remove(key);
            rejects(() -> ProbeAssertions.installer(missing), "missing " + key);
            var wrong = new LinkedHashMap<>(complete); wrong.put(key, "wrong");
            rejects(() -> ProbeAssertions.installer(wrong), "wrong " + key);
        }
        rejects(() -> ProbeAssertions.await(() -> false, Duration.ofMillis(15), "missing title"), "timeout");
        ProbeAssertions.await(() -> true, Duration.ZERO, "ready"); checks++;
        var parent = Files.createTempDirectory("cygwin-probe-tests-").toRealPath();
        try {
            rejects(() -> ProbeAssertions.freshRoot("relative-root"), "relative root");
            rejects(() -> ProbeAssertions.freshRoot(parent.toString()), "existing root");
            var fresh = parent.resolve("new root");
            if (!ProbeAssertions.freshRoot(fresh.toString()).equals(fresh)) throw new AssertionError("wrong root");
            checks++;
            rejects(() -> ProbeAssertions.freshRoot(fresh.toString()), "reuse root");
            Files.delete(fresh.resolve(".jasper-cygwin-probe")); Files.delete(fresh);
        } finally { Files.delete(parent); }
        System.out.println("Cygwin probe assertions passed: " + checks);
    }
    private static void rejects(Checked action, String label) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | java.io.IOException | java.util.concurrent.TimeoutException expected) {
            checks++; return;
        }
        throw new AssertionError("Accepted " + label);
    }
    private interface Checked { void run() throws Exception; }
}
