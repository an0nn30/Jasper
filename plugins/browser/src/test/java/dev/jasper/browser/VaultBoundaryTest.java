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
 * Plan 8 Task 3 (F20): the Credential Vault is an optional plugin, so the Browser names its API
 * ({@code dev.jasper.vault.api}) in one class only, {@code logins.VaultLogins} with its nested classes, which the plugin
 * uses only once the Vault's service was found. The scan is {@link ChromiumBoundaryTest}'s: every Utf8 entry of each
 * class's constant pool, so descriptors, generic signatures, lambda types and names in strings count as well as calls.
 */
class VaultBoundaryTest {
    private static final String OWNER = "dev/jasper/browser/logins/VaultLogins";
    private static final List<String> VAULT = List.of("dev/jasper/vault/", "dev.jasper.vault.");
    @TempDir Path temp;

    @Test void onlyVaultLoginsNamesTheVault() throws Exception {
        Path classes = Path.of(BrowserPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(classes).as("the plugin's compiled classes").isDirectory();
        assertThat(classes.resolve(OWNER + ".class")).as("the scan sees VaultLogins").isRegularFile();
        assertThat(violations(classes)).isEmpty();
        assertThat(names(classes.resolve(OWNER + ".class"))).as("VaultLogins is where the Vault is named").isTrue();
    }

    @Test void theScanFindsTheVaultNamedOutsideVaultLogins() throws Exception {
        Path bad = compile("dev.jasper.browser.item", "Holder",
            "class Holder { void descriptor(dev.jasper.vault.api.Kind kind) { } java.util.List<dev.jasper.vault.api.LockState> signature; }");
        assertThat(violations(bad)).hasSize(2)
            .allSatisfy(violation -> assertThat(violation).startsWith("dev/jasper/browser/item/Holder.class: "))
            .anySatisfy(violation -> assertThat(violation).contains("Ldev/jasper/vault/api/Kind;"))
            .anySatisfy(violation -> assertThat(violation).contains("Ldev/jasper/vault/api/LockState;"));
        Path sibling = compile("dev.jasper.browser.logins", "FieldButtons", "class FieldButtons { void use(dev.jasper.vault.api.Kind kind) { } }");
        assertThat(violations(sibling)).as("another class of the logins package").singleElement().asString()
            .startsWith("dev/jasper/browser/logins/FieldButtons.class: ");
        Path prefix = compile("dev.jasper.browser.logins", "VaultLoginsHelper", "class VaultLoginsHelper { void use(dev.jasper.vault.api.Kind kind) { } }");
        assertThat(violations(prefix)).as("a class whose name only starts with VaultLogins").singleElement().asString()
            .startsWith("dev/jasper/browser/logins/VaultLoginsHelper.class: ");
        Path dotted = compile("dev.jasper.browser", "Top", "class Top { String name = \"dev.jasper.vault.api.VaultApi\"; }");
        assertThat(violations(dotted)).as("a dotted name in a string").singleElement().asString().startsWith("dev/jasper/browser/Top.class: ");
    }

    @Test void theScanAcceptsVaultLoginsAndItsNestedClasses() throws Exception {
        Path classes = compile("dev.jasper.browser.logins", "VaultLogins",
            "final class VaultLogins { void use(dev.jasper.vault.api.Kind kind) { } static final class Answer { dev.jasper.vault.api.LockState state; } }");
        assertThat(classes.resolve(OWNER + "$Answer.class")).isRegularFile();
        assertThat(violations(classes)).isEmpty();
    }

    /** Each class but VaultLogins and its nested classes that has a Utf8 entry naming the Vault, as {@code class: entry}. */
    private static List<String> violations(Path classes) throws IOException {
        var found = new java.util.ArrayList<String>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                if (name.equals(OWNER + ".class") || name.startsWith(OWNER + "$")) continue;
                for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool())
                    if (entry instanceof Utf8Entry utf8 && VAULT.stream().anyMatch(utf8.stringValue()::contains))
                        found.add(name + ": " + utf8.stringValue());
            }
        }
        return found;
    }

    /** Whether the class file has a Utf8 entry naming the Vault. */
    private static boolean names(Path file) throws IOException {
        for (var entry : ClassFile.of().parse(Files.readAllBytes(file)).constantPool())
            if (entry instanceof Utf8Entry utf8 && VAULT.stream().anyMatch(utf8.stringValue()::contains)) return true;
        return false;
    }

    /** Compiles one source in the given package against the test's class path, which has the Vault's API. */
    private Path compile(String pack, String type, String body) throws IOException {
        Path root = Files.createTempDirectory(temp, "fixture");
        Path source = root.resolve("src").resolve(pack.replace('.', '/')).resolve(type + ".java"), classes = root.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, "package " + pack + ";\n" + body);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-cp", System.getProperty("java.class.path"),
            "-d", classes.toString(), source.toString());
        assertThat(result).as("fixture " + pack + "." + type + " compiles").isZero();
        return classes;
    }
}
