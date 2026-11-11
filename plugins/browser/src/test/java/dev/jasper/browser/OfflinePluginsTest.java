package dev.jasper.browser;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * F9 and the review's M1: no test makes the Browser plugin with the filter list's real client. The scan reads every
 * compiled test class for a call of the plugin's public constructor, which makes that client, and finds none: the tests
 * make it through {@link OfflinePlugins}, whose guard client fails the test if it is asked for.
 */
class OfflinePluginsTest {
    private static final String PLUGIN = "dev/jasper/browser/BrowserPlugin";

    @Test void noTestCallsThePluginsPublicConstructor() throws Exception {
        Path classes = Path.of(OfflinePluginsTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertThat(classes.resolve("dev/jasper/browser/BrowserPluginTest.class")).as("the scan sees the test classes").isRegularFile();
        assertThat(callers(classes, "()V")).isEmpty();
        assertThat(callers(classes, "(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/BiFunction;"
            + "Ljava/util/function/Function;)V"))
            .as("the scan sees calls of the seam")
            .contains("dev/jasper/browser/OfflinePlugins.class");
    }

    @Test void aPluginTestUsesTheGuardedPlugin() {
        assertThat(OfflinePlugins.plugin()).isNotNull();
        assertThat(OfflinePlugins.clientsAsked()).as("no test asked for a client").isZero();
        assertThat(dev.jasper.browser.downloads.DownloadsTestAccess.filesUsed()).as("no test showed or opened a file").isZero();
        assertThat(OfflinePlugins.saveClientsAsked()).as("no test fetched a saved page's resources").isZero();
    }

    /** Each test class that calls the plugin's constructor of {@code descriptor}. */
    private static List<String> callers(Path classes, String descriptor) throws IOException {
        var found = new java.util.ArrayList<String>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                var model = ClassFile.of().parse(Files.readAllBytes(file));
                boolean calls = false;
                for (MethodModel method : model.methods())
                    calls |= method.code().stream().flatMap(code -> code.elementStream())
                        .anyMatch(element -> element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals(PLUGIN)
                            && invoke.name().equalsString("<init>") && invoke.type().equalsString(descriptor));
                if (calls) found.add(classes.relativize(file).toString().replace('\\', '/'));
            }
        }
        return found;
    }
}
