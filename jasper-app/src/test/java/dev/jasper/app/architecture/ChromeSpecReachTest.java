package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 16.1.7: {@code ChromeSpec} is referenced only in dev.jasper.app.presentation, so no chrome
 * class branches on the raw spec; consumers receive projections. A class names it when any constant of its class file
 * does: a type, a field or a method descriptor.
 */
class ChromeSpecReachTest {
    static final String SPEC = "dev/jasper/app/presentation/ChromeSpec";
    static final String OWNER = "dev/jasper/app/presentation/";

    /** Whether the class file names {@link #SPEC}, a nested type of it included. */
    static boolean names(byte[] bytes) {
        for (var entry : ClassFile.of().parse(bytes).constantPool())
            if (entry instanceof Utf8Entry text && text.stringValue().contains(SPEC)) return true;
        return false;
    }

    @Test void onlyThePresentationPackageNamesTheChromeSpec() throws IOException {
        var found = new ArrayList<String>();
        boolean ownerNames = false;
        for (byte[] bytes : StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400)) {
            String type = ClassFile.of().parse(bytes).thisClass().asInternalName();
            if (!names(bytes)) continue;
            if (type.startsWith(OWNER) && type.indexOf('/', OWNER.length()) < 0) ownerNames = true;
            else found.add(type);
        }
        assertThat(ownerNames).as("the presentation package names it, so the scan is live").isTrue();
        assertThat(found).as("classes outside dev.jasper.app.presentation that name ChromeSpec").isEmpty();
    }

    // A probe compiled with this test, outside the package: what the rule reports and what it leaves alone. Never run.
    static final class ReadsTheRawSpec { Object chrome(dev.jasper.app.presentation.Presentation presentation) { return presentation.chrome(); } }
    static final class ReadsAProjection { Object look(dev.jasper.app.presentation.Presentation presentation) { return presentation.look(); } }

    @Test void theRuleReportsAReadOfTheRawSpec() throws IOException {
        assertThat(names(probe(ReadsTheRawSpec.class))).isTrue();
        assertThat(names(probe(ReadsAProjection.class))).isFalse();
    }

    static byte[] probe(Class<?> type) throws IOException {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) { return in.readAllBytes(); }
    }
}
