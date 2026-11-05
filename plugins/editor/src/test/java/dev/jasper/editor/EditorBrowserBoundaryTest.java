package dev.jasper.editor;

import dev.jasper.browser.api.MarkdownPreviews;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser's API, {@code dev.jasper.browser.api}, is named only by {@code ChromiumPreview} and {@code ChromiumPreviews}
 * (and their nested classes): its classes are absent while the Browser is not installed or is off, so every other class
 * of the Editor loads without them. The scan reads every Utf8 entry of each class's constant pool, so descriptors, generic
 * signatures and lambda types count as well as calls.
 */
class EditorBrowserBoundaryTest {
    private static final String ALLOWED = "dev/jasper/editor/ui/ChromiumPreview";
    private static final List<String> BROWSER = List.of("dev/jasper/browser/", "dev.jasper.browser.");
    @TempDir Path temp;

    @Test void onlyTheChromiumPreviewsNameTheBrowser() throws Exception {
        Path classes = Path.of(EditorPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(classes.resolve("dev/jasper/editor/ui/ChromiumPreviews.class")).as("the scan sees the Editor's classes").isRegularFile();
        assertThat(violations(classes)).isEmpty();
    }

    @Test void theScanFindsTheBrowsersTypeInADescriptorAndASignatureElsewhere() throws Exception {
        Path bad = compile("dev.jasper.editor.ui", "Holder",
            "class Holder { void descriptor(dev.jasper.browser.api.MarkdownPreviews previews) { } "
                + "java.util.List<dev.jasper.browser.api.PreviewEvent> signature; }");
        assertThat(violations(bad)).hasSize(2).allSatisfy(violation -> assertThat(violation).startsWith("dev/jasper/editor/ui/Holder.class: "));
        assertThat(violations(compile("dev.jasper.editor.ui", "ChromiumPreviews",
            "class ChromiumPreviews { void use(dev.jasper.browser.api.MarkdownPreviews previews) { } }"))).isEmpty();
    }

    /** Each class but the Chromium previews' that has a Utf8 entry naming the Browser, as {@code class: entry}. */
    private static List<String> violations(Path classes) throws IOException {
        var found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                if (name.startsWith(ALLOWED)) continue;
                for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool())
                    if (entry instanceof Utf8Entry utf8 && BROWSER.stream().anyMatch(utf8.stringValue()::contains))
                        found.add(name + ": " + utf8.stringValue());
            }
        }
        return found;
    }

    /** Compiles one source in the given package against the Browser's classes. */
    private Path compile(String pack, String type, String body) throws Exception {
        Path root = Files.createTempDirectory(temp, "fixture");
        Path source = root.resolve("src").resolve(pack.replace('.', '/')).resolve(type + ".java"), classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, "package " + pack + ";\n" + body);
        String browser = Path.of(MarkdownPreviews.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-classpath", browser, "-d", classes.toString(),
            source.toString());
        assertThat(result).as("fixture " + pack + "." + type + " compiles").isZero();
        return classes;
    }
}
