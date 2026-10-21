package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Plan 15b (Rulings 10): production code never checks the Enterprise plugin's identity; a look is data the host reads. */
class LookIdentityTest {
    static final Pattern IDENTITY = Pattern.compile("(?i)enterprise");

    /** "internal name: string" for each constant-pool string in {@code classes} that names Enterprise, ignoring case. */
    static List<String> mentions(List<byte[]> classes) {
        var found = new ArrayList<String>();
        for (byte[] bytes : classes) {
            var scanned = ProjectionReadTest.Scanned.of(bytes);
            scanned.strings().stream().filter(s -> IDENTITY.matcher(s).find()).sorted()
                .forEach(s -> found.add(scanned.name() + ": " + s));
        }
        return found;
    }

    /** The classes under {@code dev/jasper/} in every staged plugin but Enterprise; bundled libraries are not ours. */
    static List<byte[]> pluginClasses() throws Exception {
        var classes = new ArrayList<byte[]>();
        try (var plugins = Files.list(Path.of(System.getProperty("jasper.stagedPlugins")))) {
            for (Path plugin : plugins.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("dev.jasper.enterprise")).toList())
                for (Path jar : jars(plugin))
                    for (byte[] bytes : StaticDefaultTest.classFiles(jar, 0))
                        if (ProjectionReadTest.Scanned.of(bytes).name().startsWith("dev/jasper/")) classes.add(bytes);
        }
        return classes;
    }

    /** The jars in {@code directory}; a copy of {@code plugins.ManagedVaultLoadingTest.jars}, which is package-private there. */
    private static List<Path> jars(Path directory) throws Exception {
        try (var files = Files.list(directory)) { return files.filter(p -> p.toString().endsWith(".jar")).toList(); }
    }

    @Test void noProductionClassNamesEnterprise() throws Exception {
        var app = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        var ui = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100);
        var plugins = pluginClasses();
        assertThat(plugins).as("the plugin scan is live").hasSizeGreaterThan(100);
        assertThat(mentions(app)).isEmpty();
        assertThat(mentions(ui)).isEmpty();
        assertThat(mentions(plugins)).isEmpty();
    }

    @Test void aMentionIsReported() {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.LookProbe"), builder -> builder
            .withMethodBody("look", MethodTypeDesc.of(ConstantDescs.CD_String), ClassFile.ACC_STATIC,
                code -> code.ldc("dev.jasper.enterprise").areturn()));
        assertThat(mentions(List.of(probe))).containsExactly("dev/jasper/app/workspace/LookProbe: dev.jasper.enterprise");
    }
}
