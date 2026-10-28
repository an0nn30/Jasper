package dev.jasper.browser;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starting and stopping the Browser costs no Chromium: no {@code org.cef} or {@code com.jetbrains.cef} class loads
 * until a page is first shown. A forked headless JVM runs the plugin with the SDK's fake host and logs every class it
 * loads, because a class loaded in this JVM cannot be told apart from one another test loaded. The log goes to the
 * fork's standard output, not a file: {@code -Xlog} splits its options at colons, which Windows paths hold. The output
 * is read as ISO-8859-1, because the JVM writes it in the platform's encoding and only ASCII class names matter.
 */
class StartLoadsNoChromiumTest {
    private static final Pattern LOADED = Pattern.compile("\\[class,load]\\s+(\\S+)");
    @TempDir Path temp;

    @Test void startingAndStoppingTheBrowserLoadsNoChromiumClass() throws Exception {
        Path output = temp.resolve("output.txt");
        Process fork = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Djava.awt.headless=true", "-Xlog:class+load", "-cp", System.getProperty("java.class.path"),
            Main.class.getName(), temp.resolve("plugins").toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!fork.waitFor(90, TimeUnit.SECONDS)) {
            fork.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            List<String> lines = Files.readAllLines(output, StandardCharsets.ISO_8859_1);
            throw new AssertionError("the forked JVM did not exit within 90 s; output so far:\n" + diagnostics(lines));
        }
        List<String> lines = Files.readAllLines(output, StandardCharsets.ISO_8859_1);
        assertThat(fork.exitValue()).as("the fork's output:\n" + diagnostics(lines)).isZero();
        List<String> loaded = lines.stream().map(LOADED::matcher).filter(Matcher::find).map(match -> match.group(1)).toList();
        assertThat(loaded.contains(BrowserPlugin.class.getName())).as("the log shows the plugin's own class").isTrue();
        assertThat(loaded.stream().filter(name -> name.startsWith("org.cef.") || name.startsWith("com.jetbrains.cef.")).toList())
            .as("Chromium classes loaded").isEmpty();
    }

    /** The fork's output without its class-load lines, which run to thousands. */
    private static String diagnostics(List<String> lines) {
        return String.join("\n", lines.stream().filter(line -> !LOADED.matcher(line).find()).toList());
    }

    /**
     * The forked JVM's entry: starts the plugin on the fake host, on the UI thread as Jasper starts plugins, stops it,
     * and exits non-zero on any failure.
     */
    public static final class Main {
        private Main() { }

        public static void main(String[] args) throws Exception {
            var failures = new java.util.ArrayList<String>();
            java.awt.EventQueue.invokeAndWait(() -> {
                try (var host = new FakePluginHost(Path.of(args[0]))) {
                    host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of("browser.provide")), Set.of(), Set.of(), new BrowserPlugin());
                    host.stopAll();
                    failures.addAll(host.failures());
                }
            });
            if (!failures.isEmpty()) {
                System.err.println(String.join("\n", failures));
                System.exit(2);
            }
            System.exit(0);
        }
    }
}
