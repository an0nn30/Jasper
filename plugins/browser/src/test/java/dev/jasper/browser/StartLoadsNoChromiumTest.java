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
 * Starting and stopping the Browser costs no Chromium, and nor does opening a browser item that is not shown, or
 * restoring a window of them: no {@code org.cef} or {@code com.jetbrains.cef} class loads until a page is first shown.
 * Nor do they cost the browsing database (spec section 19): no {@code org.sqlite} class loads and no
 * {@code browser.sqlite} is made. A forked
 * headless JVM runs the plugin with the SDK's fake host and logs every class it
 * loads, because a class loaded in this JVM cannot be told apart from one another test loaded. The log goes to the
 * fork's standard output, not a file: {@code -Xlog} splits its options at colons, which Windows paths hold. The output
 * is read as ISO-8859-1, because the JVM writes it in the platform's encoding and only ASCII class names matter.
 */
class StartLoadsNoChromiumTest {
    private static final Pattern LOADED = Pattern.compile("\\[class,load]\\s+(\\S+)");
    @TempDir Path temp;

    @Test void startingAndStoppingTheBrowserLoadsNoChromiumClass() throws Exception {
        List<String> loaded = fork(Main.START);
        assertThat(loaded.contains(BrowserPlugin.class.getName())).as("the log shows the plugin's own class").isTrue();
        assertThat(chromium(loaded)).as("Chromium classes loaded").isEmpty();
        assertNoDatabase(loaded, Main.START);
    }

    @Test void openingABrowserItemThatIsNotShownLoadsNoChromiumClass() throws Exception {
        List<String> loaded = fork(Main.OPEN);
        assertThat(loaded).as("the log shows the item, its page and its canvas")
            .contains("dev.jasper.browser.item.BrowserItem", "dev.jasper.browser.page.Page", "dev.jasper.browser.page.PageView");
        assertThat(chromium(loaded)).as("Chromium classes loaded").isEmpty();
        assertNoDatabase(loaded, Main.OPEN);
    }

    /** Spec section 19: restored items hold only their saved state until shown. */
    @Test void restoringAWindowOfBrowserItemsLoadsNoChromiumClass() throws Exception {
        List<String> loaded = fork(Main.RESTORE);
        assertThat(loaded).as("the log shows the restore, the items and their pages")
            .contains("dev.jasper.browser.restore.BrowserRestore", "dev.jasper.browser.item.BrowserItem", "dev.jasper.browser.page.Page");
        assertThat(chromium(loaded)).as("Chromium classes loaded").isEmpty();
        assertNoDatabase(loaded, Main.RESTORE);
    }

    /**
     * Spec section 19: the Markdown previews' service costs no Chromium until a preview shows. A plugin that requires the
     * Browser, optionally, finds the published service, then makes a preview and themes it, shows it a document and an
     * image, finds, copies and selects all in it and closes it, the preview never shown (N-F28).
     */
    @Test void makingAndFeedingAnUnshownPreviewLoadsNoChromiumClass() throws Exception {
        List<String> loaded = fork(Main.PREVIEW);
        assertThat(loaded).as("the log shows the service, the preview and its page")
            .contains("dev.jasper.browser.preview.PreviewService", "dev.jasper.browser.preview.PagePreview",
                "dev.jasper.browser.page.PreviewPage", "dev.jasper.browser.page.PageView");
        assertThat(chromium(loaded)).as("Chromium classes loaded").isEmpty();
        assertNoDatabase(loaded, Main.PREVIEW);
    }

    /** Runs {@link Main} in a forked JVM, in {@code mode}: the classes it loaded, in order. */
    private List<String> fork(String mode) throws Exception {
        Path output = temp.resolve(mode + "-output.txt");
        Process fork = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Djava.awt.headless=true", "-Xlog:class+load", "-cp", System.getProperty("java.class.path"),
            Main.class.getName(), temp.resolve(mode + "-plugins").toString(), mode)
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!fork.waitFor(90, TimeUnit.SECONDS)) {
            fork.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            List<String> lines = Files.readAllLines(output, StandardCharsets.ISO_8859_1);
            throw new AssertionError("the forked JVM did not exit within 90 s; output so far:\n" + diagnostics(lines));
        }
        List<String> lines = Files.readAllLines(output, StandardCharsets.ISO_8859_1);
        assertThat(fork.exitValue()).as("the fork's output:\n" + diagnostics(lines)).isZero();
        return lines.stream().map(LOADED::matcher).filter(Matcher::find).map(match -> match.group(1)).toList();
    }

    /**
     * Spec section 19 and F28: the fork in {@code mode} loaded no sqlite-jdbc class and left no {@code browser.sqlite}
     * under its plugins' folder, though the plugin's stores were made and closed.
     */
    private void assertNoDatabase(List<String> loaded, String mode) throws Exception {
        assertThat(loaded).as("the log shows the stores").contains("dev.jasper.browser.store.Stores");
        assertThat(loaded.stream().filter(name -> name.startsWith("org.sqlite.")).toList()).as("sqlite-jdbc classes loaded").isEmpty();
        Path plugins = temp.resolve(mode + "-plugins");
        assertThat(plugins.resolve("dev.jasper.browser").resolve("data")).as("the Browser's data folder, where it would be").isDirectory();
        try (var files = Files.walk(plugins)) {
            assertThat(files.filter(file -> file.getFileName().toString().startsWith("browser.sqlite")).toList()).as("database files").isEmpty();
        }
    }

    private static List<String> chromium(List<String> loaded) {
        return loaded.stream().filter(name -> name.startsWith("org.cef.") || name.startsWith("com.jetbrains.cef.")).toList();
    }

    /** The fork's output without its class-load lines, which run to thousands. */
    private static String diagnostics(List<String> lines) {
        return String.join("\n", lines.stream().filter(line -> !LOADED.matcher(line).find()).toList());
    }

    /**
     * The forked JVM's entry: starts the plugin on the fake host, on the UI thread as Jasper starts plugins; in
     * {@link #OPEN} mode runs New Browser Tab, whose item the fake host never shows; in {@link #RESTORE} mode restores a
     * window of saved browser items, which the fake host places and never shows; in {@link #PREVIEW} mode starts a plugin
     * that requires the Browser optionally and feeds a preview it makes, never shown; stops them; and exits non-zero on
     * any failure.
     */
    public static final class Main {
        static final String START = "start", OPEN = "open", RESTORE = "restore", PREVIEW = "preview";
        // A window of saved items: pages, a target, Jasper's own page, and one that saved no url (the start page).
        private static final List<java.util.Map<String, String>> SAVED = List.of(java.util.Map.of("url", "https://example.com/"),
            java.util.Map.of("url", "https://example.org/docs?q=1#top", "target", "docs"), java.util.Map.of("url", "jasper-about:welcome"),
            java.util.Map.of(), java.util.Map.of("url", "file:///tmp/index.html"), java.util.Map.of("url", "http://localhost:8080/"));

        private Main() { }

        public static void main(String[] args) throws Exception {
            var failures = new java.util.ArrayList<String>();
            java.awt.EventQueue.invokeAndWait(() -> {
                try (var host = new FakePluginHost(Path.of(args[0]))) {
                    var window = host.addTerminalWindow();
                    host.activateTerminalWindow(window);
                    host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of("browser.provide", "palette.contribute")), Set.of(), Set.of(), OfflinePlugins.plugin());
                    if (args[1].equals(OPEN) && (!host.invoke("dev.jasper.browser.new_tab", window, null) || host.contentTabs().size() != 1))
                        failures.add("New Browser Tab opened no item");
                    if (args[1].equals(RESTORE)) {
                        for (var values : SAVED)
                            if (host.restoreTab("dev.jasper.browser", window, "Saved", new dev.jasper.sdk.RestoreState("dev.jasper.browser.page", values)).isEmpty())
                                failures.add("A saved browser item did not come back: " + values);
                        if (host.contentTabs().size() != SAVED.size()) failures.add("Restored " + host.contentTabs().size() + " items");
                        failures.addAll(host.declinedRestores());
                    }
                    if (args[1].equals(PREVIEW)) failures.addAll(preview(host));
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

        /**
         * Starts a plugin that requires the Browser optionally, as the Editor does, and has it make, theme, show a document
         * and an image to, and close a preview, which nothing shows: what went wrong.
         */
        private static List<String> preview(FakePluginHost host) {
            var found = new java.util.concurrent.atomic.AtomicReference<java.util.Optional<dev.jasper.browser.api.MarkdownPreviews>>();
            host.start(new PluginInfo("dev.jasper.consumer", "Consumer", "0.1.0", Set.of()), Set.of(), Set.of("dev.jasper.browser"),
                context -> found.set(context.services().find(dev.jasper.browser.api.MarkdownPreviews.class)));
            if (found.get() == null || found.get().isEmpty()) return List.of("The consumer found no MarkdownPreviews");
            var previews = found.get().get();
            if (!previews.available()) return List.of("Previews are unavailable before Chromium is asked for");
            var told = new java.util.ArrayList<dev.jasper.browser.api.PreviewEvent>();
            var preview = previews.create(told::add);
            var palette = new dev.jasper.sdk.ui.WorkspacePalette(java.awt.Color.WHITE, java.awt.Color.LIGHT_GRAY, java.awt.Color.GRAY,
                java.awt.Color.BLACK, java.awt.Color.DARK_GRAY, java.awt.Color.GRAY, java.awt.Color.BLUE, java.awt.Color.WHITE,
                java.awt.Color.LIGHT_GRAY, java.awt.Color.BLUE, java.awt.Color.GREEN, java.awt.Color.ORANGE, java.awt.Color.RED);
            var styles = new java.util.EnumMap<dev.jasper.sdk.ui.SyntaxRole, dev.jasper.sdk.ui.SyntaxStyle>(dev.jasper.sdk.ui.SyntaxRole.class);
            for (var role : dev.jasper.sdk.ui.SyntaxRole.values()) styles.put(role, new dev.jasper.sdk.ui.SyntaxStyle(java.awt.Color.BLACK, false, false));
            preview.theme(new dev.jasper.browser.api.PreviewTheme(dev.jasper.sdk.Variant.LIGHT, palette, new dev.jasper.sdk.ui.SyntaxPalette(styles),
                "Inter", new dev.jasper.sdk.ui.TerminalTypography("Menlo", 13, List.of(), false, 1.2)));
            preview.show(1, "<h1 id=\"intro\" data-b=\"0\">Intro</h1>");
            preview.image(0, new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB));
            preview.imageReason(1, "Load external images to see it");
            preview.scrollTo(10);
            preview.markAll(new dev.jasper.browser.api.PreviewFind("Intro", false, false, false));
            preview.copy();
            preview.selectAll();
            preview.focus();
            if (preview.component() == null) return List.of("The preview has no canvas");
            preview.close();
            return told.isEmpty() ? List.of() : List.of("An unshown preview told " + told);
        }
    }
}
