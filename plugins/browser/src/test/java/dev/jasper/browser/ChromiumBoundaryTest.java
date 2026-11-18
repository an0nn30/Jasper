package dev.jasper.browser;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's types ({@code org.cef}, {@code com.jetbrains.cef}) are named only in the plugin's {@code engine},
 * {@code page}, {@code links} and {@code dialogs} packages, so the classes the start loads never pull Chromium in.
 * The scan reads every Utf8 entry of each class's constant pool, so descriptors, generic signatures and lambda types
 * count as well as calls.
 */
class ChromiumBoundaryTest {
    private static final List<String> ALLOWED = List.of("dev/jasper/browser/engine/", "dev/jasper/browser/page/",
        "dev/jasper/browser/links/", "dev/jasper/browser/dialogs/");
    private static final List<String> CHROMIUM = List.of("org/cef/", "com/jetbrains/cef/", "org.cef.", "com.jetbrains.cef.");
    @TempDir Path temp;

    @Test void onlyTheChromiumPackagesNameAChromiumType() throws Exception {
        Path classes = Path.of(BrowserPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(classes).as("the plugin's compiled classes").isDirectory();
        assertThat(classes.resolve("dev/jasper/browser/BrowserPlugin.class")).as("the scan sees the plugin's classes").isRegularFile();
        assertThat(violations(classes)).isEmpty();
    }

    @Test void theScanFindsAChromiumTypeInADescriptorAndAGenericSignatureOutsideTheChromiumPackages() throws Exception {
        Path bad = compile("dev.jasper.browser.item", "Holder",
            "class Holder { void descriptor(org.cef.browser.CefBrowser browser) { } java.util.List<com.jetbrains.cef.JCefAppConfig> signature; }");
        assertThat(violations(bad)).hasSize(2)
            .allSatisfy(violation -> assertThat(violation).startsWith("dev/jasper/browser/item/Holder.class: "))
            .anySatisfy(violation -> assertThat(violation).contains("Lorg/cef/browser/CefBrowser;"))
            .anySatisfy(violation -> assertThat(violation).contains("Lcom/jetbrains/cef/JCefAppConfig;"));
        Path top = compile("dev.jasper.browser", "Top", "class Top { void use(org.cef.CefApp app) { } }");
        assertThat(violations(top)).singleElement().asString().startsWith("dev/jasper/browser/Top.class: ");
    }

    @Test void theScanAcceptsAChromiumTypeInEachChromiumPackage() throws Exception {
        for (String name : List.of("engine", "page", "links", "dialogs"))
            assertThat(violations(compile("dev.jasper.browser." + name, "Holder", "class Holder { void use(org.cef.CefApp app) { } }")))
                .as(name).isEmpty();
    }

    /**
     * Plan 8's F4: the sign-in dialog lives in {@code dialogs}, beside the page's other dialogs, yet names no Chromium
     * type, no Vault type and nothing of the {@code logins} package, which names it.
     */
    @Test void theSignInDialogNamesNoChromiumVaultOrLoginsType() throws Exception {
        Path classes = Path.of(BrowserPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path dialogs = classes.resolve("dev/jasper/browser/dialogs");
        List<String> named = List.of("org/cef/", "com/jetbrains/cef/", "org.cef.", "com.jetbrains.cef.", "dev/jasper/vault/",
            "dev.jasper.vault.", "dev/jasper/browser/logins/", "dev.jasper.browser.logins.");
        var found = new java.util.ArrayList<String>();
        try (Stream<Path> files = Files.list(dialogs)) {
            List<Path> dialog = files.filter(file -> file.getFileName().toString().matches("SignInDialog(\\$.*)?\\.class"))
                .sorted().toList();
            assertThat(dialog).as("the dialog and its nested classes").hasSizeGreaterThan(1);
            for (Path file : dialog)
                for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool())
                    if (entry instanceof Utf8Entry utf8 && named.stream().anyMatch(utf8.stringValue()::contains))
                        found.add(file.getFileName() + ": " + utf8.stringValue());
        }
        assertThat(found).isEmpty();
    }

    /** Each class outside the Chromium packages that has a Utf8 entry naming a Chromium type, as {@code class: entry}. */
    private static List<String> violations(Path classes) throws IOException {
        var found = new java.util.ArrayList<String>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                if (ALLOWED.stream().anyMatch(name::startsWith)) continue;
                for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool())
                    if (entry instanceof Utf8Entry utf8 && CHROMIUM.stream().anyMatch(utf8.stringValue()::contains))
                        found.add(name + ": " + utf8.stringValue());
            }
        }
        return found;
    }

    /** Compiles one source in the given package against the runtime's modules, which include {@code jcef}. */
    private Path compile(String pack, String type, String body) throws IOException {
        Path root = Files.createTempDirectory(temp, "fixture");
        Path source = root.resolve("src").resolve(pack.replace('.', '/')).resolve(type + ".java"), classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, "package " + pack + ";\n" + body);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", classes.toString(), source.toString());
        assertThat(result).as("fixture " + pack + "." + type + " compiles").isZero();
        return classes;
    }
}
