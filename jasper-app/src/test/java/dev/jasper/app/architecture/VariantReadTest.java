package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 16.1.7, the variant-read scan: ControlVariants is read only in dev.jasper.ui.kit,
 * dev.jasper.app.presentation (plan 5's parser) and KitFaces, which chooses each control's face once.
 */
class VariantReadTest {
    static final Pattern VARIANTS = Pattern.compile("dev/jasper/ui/kit/ControlVariants(?=[;$.]|$)");

    static boolean allowed(String type) {
        return type.startsWith("dev/jasper/ui/kit/") || type.startsWith("dev/jasper/app/presentation/")
            || type.equals("dev/jasper/ui/swing/KitFaces") || type.startsWith("dev/jasper/ui/swing/KitFaces$");
    }

    /** The classes in {@code files} that name ControlVariants, as internal names; {@code all} keeps the allowed ones too. */
    static List<String> readers(List<byte[]> files, boolean all) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (!all && allowed(scanned.name())) continue;
            if (scanned.strings().stream().anyMatch(s -> VARIANTS.matcher(s).find())) found.add(scanned.name());
        }
        return found;
    }

    static List<byte[]> jars() throws Exception {
        var files = new ArrayList<byte[]>(StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400));
        files.addAll(StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100));
        return files;
    }

    @Test void onlyTheKitPackageThePresentationPackageAndKitFacesReadTheVariants() throws Exception {
        var files = jars();
        assertThat(readers(files, true)).as("the scan is live").contains("dev/jasper/ui/swing/KitFaces", "dev/jasper/ui/kit/Kit");
        assertThat(readers(files, false)).isEmpty();
    }

    @Test void aReadElsewhereIsReported() throws Exception {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.VariantProbe"), builder -> builder
            .withField("controls", ClassDesc.of("dev.jasper.ui.kit.ControlVariants"), ClassFile.ACC_PRIVATE));
        assertThat(readers(List.of(probe), false)).containsExactly("dev/jasper/app/workspace/VariantProbe");
    }
}
