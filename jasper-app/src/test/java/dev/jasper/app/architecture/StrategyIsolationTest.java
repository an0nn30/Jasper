package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 16.1.7: faces never reference Kit, ControlVariants or ChromeSpec (they take what they
 * paint as arguments), and the reconciler references control classes only, never a face or KitFaces.
 */
class StrategyIsolationTest {
    static final String FACE = "dev/jasper/ui/swing/Face", RECONCILER = "dev/jasper/ui/swing/ViewReconciler";
    static final Pattern FORBIDDEN = Pattern.compile(
        "dev/jasper/ui/kit/(?:Kit|ControlVariants)(?=[;$.]|$)|dev/jasper/app/presentation/ChromeSpec(?=[;$.]|$)");

    /** A class's name, the interfaces it names and every string in its constant pool. */
    record Scanned(String name, List<String> interfaces, Set<String> strings) {
        static Scanned of(byte[] bytes) {
            var model = ClassFile.of().parse(bytes);
            var strings = new HashSet<String>();
            for (var entry : model.constantPool()) if (entry instanceof Utf8Entry utf8) strings.add(utf8.stringValue());
            return new Scanned(model.thisClass().asInternalName(), model.interfaces().stream().map(i -> i.asInternalName()).toList(), strings);
        }
    }

    /** The face classes: every class implementing Face or an interface that extends it, and their nested classes. */
    static Set<String> faces(List<Scanned> classes) {
        var interfaces = new HashSet<String>(Set.of(FACE));
        for (boolean grew = true; grew; ) {
            grew = false;
            for (var c : classes) if (!interfaces.contains(c.name()) && c.interfaces().stream().anyMatch(interfaces::contains)) {
                interfaces.add(c.name()); grew = true;
            }
        }
        var faces = new HashSet<String>();
        for (var c : classes) if (interfaces.contains(c.name()) || c.interfaces().stream().anyMatch(interfaces::contains)) faces.add(c.name());
        for (var c : classes) for (var face : List.copyOf(faces)) if (c.name().startsWith(face + "$")) faces.add(c.name());
        return faces;
    }

    static List<String> violations(List<Scanned> classes) {
        var faces = faces(classes);
        var found = new ArrayList<String>();
        for (var c : classes) {
            if (faces.contains(c.name()))
                for (var s : c.strings()) { var m = FORBIDDEN.matcher(s); if (m.find()) found.add(c.name() + " references " + m.group()); }
            if (c.name().equals(RECONCILER) || c.name().startsWith(RECONCILER + "$"))
                for (var s : c.strings()) {
                    if (s.equals("dev/jasper/ui/swing/KitFaces") || s.contains("Ldev/jasper/ui/swing/KitFaces;")) { found.add(c.name() + " references " + s); continue; }
                    for (var face : faces) if (s.equals(face) || s.contains("L" + face + ";")) { found.add(c.name() + " references " + s); break; }
                }
        }
        return found;
    }

    static List<Scanned> uiClasses() throws Exception {
        var classes = new ArrayList<Scanned>();
        for (var bytes : StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.uiJar")), 100)) classes.add(Scanned.of(bytes));
        return classes;
    }

    @Test void facesNeverReferenceTheKitAndTheReconcilerNoFace() throws Exception {
        var classes = uiClasses();
        assertThat(faces(classes)).as("the scan is live").contains("dev/jasper/ui/swing/DefaultToggleFace", "dev/jasper/ui/swing/ClassicToggleFace");
        assertThat(violations(classes)).isEmpty();
    }

    /** A probe face holding a Kit field, compiled here, is reported; so is a reconciler naming a face. */
    @Test void aFaceThatReadsTheKitIsReported() throws Exception {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.ui.swing.ProbeFace"), builder -> builder
            .withInterfaceSymbols(ClassDesc.of("dev.jasper.ui.swing.ToggleFace"))
            .withField("kit", ClassDesc.of("dev.jasper.ui.kit.Kit"), ClassFile.ACC_PRIVATE));
        var classes = new ArrayList<>(uiClasses());
        classes.add(Scanned.of(probe));
        assertThat(violations(classes)).contains("dev/jasper/ui/swing/ProbeFace references dev/jasper/ui/kit/Kit");
        byte[] reconciler = ClassFile.of().build(ClassDesc.of("dev.jasper.ui.swing.ViewReconciler$Probe"), builder -> builder
            .withField("faces", ClassDesc.of("dev.jasper.ui.swing.KitFaces"), ClassFile.ACC_PRIVATE));
        assertThat(violations(List.of(Scanned.of(reconciler)))).contains("dev/jasper/ui/swing/ViewReconciler$Probe references Ldev/jasper/ui/swing/KitFaces;");
    }
}
