package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeStyles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 6.6 and 16.1.7: geometry and type follow a kit. Outside the catalog's own packages
 * (dev.jasper.ui.metrics and dev.jasper.ui.kit) only AnimationSettings reads DesignMetrics.DEFAULT (ConfigSnapshot is
 * built before any window, so the tab durations come from the default catalog), nothing reads TypeScale.DEFAULT or
 * FaceSet.PLATFORM, only the classes that choose the start-up kit or fall back to it read Kit.DEFAULT, and no static
 * initializer reads a metric or builds a painter, itself or through the methods it calls.
 */
class StaticDefaultTest {
    static final String METRICS = "dev/jasper/ui/metrics/DesignMetrics", ROLE = "Ldev/jasper/ui/metrics/DesignMetrics$Role;";
    static final String ANIMATION = "dev/jasper/app/config/AnimationSettings", DEFAULT_KIT = "dev/jasper/ui/kit/Kit.DEFAULT";
    static final String PROBE = "dev/jasper/app/architecture/StaticDefaultTest$";
    /** How many calls deep a static initializer's calls are followed through the scanned classes. */
    static final int DEPTH = 4;
    /** Default catalogs that only their owners may read. */
    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/FaceSet.PLATFORM");
    /** Calls that read a metric; a static initializer makes none. */
    static final Set<String> METRIC_READS = Set.of(METRICS + ".value", METRICS + ".contentHeight", METRICS + ".contentPadding",
        METRICS + ".with", "dev/jasper/ui/kit/Kit.metrics", "dev/jasper/ui/kit/Kit.type", "dev/jasper/ui/swing/KitContext.metrics",
        "dev/jasper/ui/swing/KitContext.kit", "dev/jasper/ui/swing/FlatLafInstaller.installedKit");
    /** Painters a static initializer must not build or call: their kit would be fixed at class load. */
    static final Set<String> PAINTERS = Set.of("dev/jasper/ui/swing/NativeStyles", "dev/jasper/ui/swing/ChromePainter");
    /** The classes that read Kit.DEFAULT: the installed-kit fallback, the controllers made without a kit, start-up. */
    static final Set<String> DEFAULT_KIT_READERS = Set.of("dev/jasper/ui/swing/FlatLafInstaller",
        "dev/jasper/app/appearance/ThemeController", "dev/jasper/app/application/JasperApplication");

    static boolean owner(String type) { return type.startsWith("dev/jasper/ui/metrics/") || type.startsWith("dev/jasper/ui/kit/"); }

    /** The scanned classes, every method body among them by "type.name descriptor", and each class's superclass. */
    record Classes(List<ClassModel> models, Map<String, MethodModel> bodies, Map<String, String> supers) {
        static Classes of(List<byte[]> files) {
            var models = new ArrayList<ClassModel>();
            var bodies = new HashMap<String, MethodModel>();
            var supers = new HashMap<String, String>();
            for (var bytes : files) {
                var model = ClassFile.of().parse(bytes);
                String type = model.thisClass().asInternalName();
                models.add(model);
                model.superclass().ifPresent(parent -> supers.put(type, parent.asInternalName()));
                for (var method : model.methods())
                    bodies.put(type + "." + method.methodName().stringValue() + method.methodType().stringValue(), method);
            }
            return new Classes(models, bodies, supers);
        }

        /** The body {@code call} runs when it lies in these classes outside the catalog's owners; null otherwise. */
        MethodModel body(InvokeInstruction call) {
            String member = call.name().stringValue() + call.type().stringValue();
            boolean constructor = call.name().stringValue().equals("<init>");
            for (String type = call.owner().asInternalName(); type != null && !owner(type); type = constructor ? null : supers.get(type)) {
                var body = bodies.get(type + "." + member);
                if (body != null) return body;
            }
            return null;
        }
    }

    /** Every read the rules name in {@code classes}, as "type.method: what" (an initializer's call as "type.<clinit>: calls owner.member"). */
    static List<String> reads(Classes classes) {
        var found = new ArrayList<String>();
        for (var model : classes.models()) {
            String type = model.thisClass().asInternalName();
            if (owner(type)) continue;
            for (var method : model.methods()) {
                String name = method.methodName().stringValue();
                boolean initializer = name.equals("<clinit>");
                method.code().ifPresent(code -> {
                    for (var element : code) {
                        if (element instanceof FieldInstruction field && field.opcode() == Opcode.GETSTATIC) {
                            String read = field.owner().asInternalName() + "." + field.name().stringValue();
                            if (CATALOGS.contains(read) || read.equals(DEFAULT_KIT)) found.add(type + "." + name + ": " + read);
                        }
                        if (initializer && element instanceof InvokeInstruction call) {
                            String callee = call.owner().asInternalName() + "." + call.name().stringValue();
                            String descriptor = call.type().stringValue(), parameters = descriptor.substring(1, descriptor.indexOf(')'));
                            if (metricRead(call) || (!call.name().stringValue().equals("<init>") && parameters.contains(ROLE))) {
                                found.add(type + ".<clinit>: calls " + callee);
                            } else {
                                String reached = reaches(classes, call, 1, new HashSet<>());
                                if (reached != null) found.add(type + ".<clinit>: calls " + callee + ", which reaches " + reached);
                            }
                        }
                    }
                });
            }
        }
        return found;
    }

    /** A call that reads a metric or uses a painter. */
    static boolean metricRead(InvokeInstruction call) {
        String owner = call.owner().asInternalName();
        return METRIC_READS.contains(owner + "." + call.name().stringValue()) || PAINTERS.contains(owner);
    }

    /**
     * The first default-catalog read, metric read or painter use that {@code call} leads to, following calls through the
     * scanned classes up to {@link #DEPTH} deep, as "owner.member"; null for none. Calls into the catalog's owners, the JDK
     * and libraries are not followed, nor lambda bodies, which an initializer only creates.
     */
    static String reaches(Classes classes, InvokeInstruction call, int depth, Set<String> visited) {
        var body = classes.body(call);
        if (body == null || depth > DEPTH
                || !visited.add(call.owner().asInternalName() + "." + call.name().stringValue() + call.type().stringValue())) return null;
        var code = body.code().orElse(null);
        if (code == null) return null;
        for (var element : code) {
            if (element instanceof FieldInstruction field && field.opcode() == Opcode.GETSTATIC) {
                String read = field.owner().asInternalName() + "." + field.name().stringValue();
                if (CATALOGS.contains(read)) return read;
            }
            if (element instanceof InvokeInstruction next) {
                if (metricRead(next)) return next.owner().asInternalName() + "." + next.name().stringValue();
                String reached = reaches(classes, next, depth + 1, visited);
                if (reached != null) return reached;
            }
        }
        return null;
    }

    /** The class files in {@code jar}, which must hold at least {@code minimum}. */
    static List<byte[]> classFiles(Path jar, int minimum) throws IOException {
        var files = new ArrayList<byte[]>();
        try (var system = FileSystems.newFileSystem(jar); var walk = Files.walk(system.getPath("/"))) {
            for (var path : walk.filter(entry -> entry.toString().endsWith(".class")).toList()) files.add(Files.readAllBytes(path));
        }
        assertThat(files.size()).as("classes read from %s", jar).isGreaterThanOrEqualTo(minimum);
        return files;
    }

    @Test void onlyTheCatalogsOwnersAndAnimationSettingsReadTheDefaultCatalogs() throws IOException {
        // One set of classes, so an app initializer's call into jasper-ui is followed too.
        var files = new ArrayList<byte[]>(classFiles(Path.of(System.getProperty("jasper.appJar")), 400));
        files.addAll(classFiles(Path.of(System.getProperty("jasper.uiJar")), 100));
        var found = reads(Classes.of(files));
        assertThat(found.stream().filter(read -> read.equals(ANIMATION + ".<clinit>: " + METRICS + ".DEFAULT")))
            .as("the one allowed read, so the scan is live").hasSize(3);
        assertThat(found.stream().filter(read -> !read.startsWith(ANIMATION + ".<clinit>: ") && !read.endsWith(": " + DEFAULT_KIT)))
            .as("default-catalog reads and static metric finals outside the catalog").isEmpty();
        // A typed local: assertThat(stream.collect(toCollection(TreeSet::new))) is ambiguous among AssertJ's overloads.
        Set<String> readers = found.stream().filter(read -> read.endsWith(": " + DEFAULT_KIT))
            .map(read -> read.substring(0, read.indexOf('.'))).collect(Collectors.toCollection(TreeSet::new));
        assertThat(readers).as("the classes that read Kit.DEFAULT").containsExactlyInAnyOrderElementsOf(DEFAULT_KIT_READERS);
    }

    // Probes compiled with this test: what the rules report and what they leave alone. They are never initialized.
    static final class DefaultInAMethod { int gap() { return DesignMetrics.DEFAULT.value(DesignMetrics.Role.HAIRLINE); } }
    static final class TypeDefaultInAMethod { Object scale() { return TypeScale.DEFAULT; } }
    static final class FinalFromTheInstalledKit {
        static final int GAP = FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE);
    }
    static final class FinalThroughAHelper {
        static final int GAP = metric(DesignMetrics.Role.HAIRLINE);
        private static int metric(DesignMetrics.Role role) { return role.ordinal(); }
    }
    static final class FinalThroughAPlainHelper {
        static final int GAP = gap();
        static int gap() { return FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE); }
    }
    static final class FinalThroughAConstructor {
        static final FinalThroughAConstructor INSTANCE = new FinalThroughAConstructor();
        final int gap = NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE);
    }
    static final class StylesAtClassLoad { static final NativeStyles STYLES = new NativeStyles(Kit.DEFAULT); }
    static final class LiveRead { static int gap() { return FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE); } }
    static final class LazySupplier {
        static final IntSupplier GAP = () -> FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE);
    }

    @Test void theRulesReportDefaultReadsStaticMetricFinalsAndStaticPainters() {
        assertThat(probe(DefaultInAMethod.class)).containsExactly(PROBE + "DefaultInAMethod.gap: " + METRICS + ".DEFAULT");
        assertThat(probe(TypeDefaultInAMethod.class)).containsExactly(PROBE + "TypeDefaultInAMethod.scale: dev/jasper/ui/metrics/TypeScale.DEFAULT");
        assertThat(probe(FinalFromTheInstalledKit.class))
            .contains(PROBE + "FinalFromTheInstalledKit.<clinit>: calls dev/jasper/ui/swing/FlatLafInstaller.installedKit");
        assertThat(probe(FinalThroughAHelper.class))
            .containsExactly(PROBE + "FinalThroughAHelper.<clinit>: calls " + PROBE + "FinalThroughAHelper.metric");
        assertThat(probe(FinalThroughAPlainHelper.class)).as("a helper without a role parameter is followed")
            .containsExactly(PROBE + "FinalThroughAPlainHelper.<clinit>: calls " + PROBE
                + "FinalThroughAPlainHelper.gap, which reaches dev/jasper/ui/swing/FlatLafInstaller.installedKit");
        assertThat(probe(FinalThroughAConstructor.class)).as("so is a constructor")
            .containsExactly(PROBE + "FinalThroughAConstructor.<clinit>: calls " + PROBE
                + "FinalThroughAConstructor.<init>, which reaches dev/jasper/ui/swing/NativeStyles.installed");
        assertThat(probe(StylesAtClassLoad.class)).containsExactly(PROBE + "StylesAtClassLoad.<clinit>: " + DEFAULT_KIT,
            PROBE + "StylesAtClassLoad.<clinit>: calls dev/jasper/ui/swing/NativeStyles.<init>");
        assertThat(probe(LiveRead.class)).as("a read when used is the point").isEmpty();
        assertThat(probe(LazySupplier.class)).as("a lambda body is not the initializer").isEmpty();
    }

    static List<String> probe(Class<?> type) {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) { return reads(Classes.of(List.of(in.readAllBytes()))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
