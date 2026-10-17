package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 9.1 and 16.1.7 for the window chrome: each look projection's variants are read only by
 * the factory or supplier that owns them, besides the presentation package that computes them. Plans 6b to 9 add their
 * points to {@link #PROJECTION_READERS}.
 */
class ProjectionReadTest {
    /** A variant pattern over internal names, and the classes that may read it. */
    static final Map<Pattern, Predicate<String>> PROJECTION_READERS = Map.of(
        Pattern.compile("dev/jasper/app/presentation/WorkspaceLook\\$(?:Menus|Header|Stripes|Tabs|Status|FrameStyle)(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/workspace/WorkspaceChrome")
                || type.startsWith("dev/jasper/app/workspace/WorkspaceChrome$"),
        Pattern.compile("dev/jasper/app/presentation/Motion(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/application/LookMotion"),
        Pattern.compile("dev/jasper/app/presentation/DialogMapping\\$Kind(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/windows/AuxiliaryWindows"),
        // Plan 9a (spec 9.1 and 16.1.7): naming Composition's class (a constant, a method, a switch) is a read, which only
        // the composition roots make; a class that only carries the value names it in descriptors, which this does not match.
        Pattern.compile("^dev/jasper/app/presentation/Composition$"), ProjectionReadTest::compositionRoot,
        // Plan 8b (spec 3.3 and 16.1.7): the palette's view is read only by PaletteViews, its home only by WorkspaceChrome.
        Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$View(?=[;$.]|$)"), type -> owns(type, "dev/jasper/app/palette/PaletteViews"),
        Pattern.compile("dev/jasper/app/presentation/PaletteLook\\$Home(?=[;$.]|$)"), type -> owns(type, "dev/jasper/app/workspace/WorkspaceChrome"));

    /** Whether {@code type} (an internal name) is the presentation package's, {@code owner} or a class nested in it. */
    static boolean owns(String type, String owner) {
        return type.startsWith("dev/jasper/app/presentation/") || type.equals(owner) || type.startsWith(owner + "$");
    }

    /**
     * A class's internal name and every string in its constant pool.
     *
     * @param name the internal name
     * @param strings the constant pool's strings
     */
    record Scanned(String name, Set<String> strings) {
        static Scanned of(byte[] bytes) {
            var model = ClassFile.of().parse(bytes);
            var strings = new HashSet<String>();
            for (var entry : model.constantPool()) if (entry instanceof Utf8Entry utf8) strings.add(utf8.stringValue());
            return new Scanned(model.thisClass().asInternalName(), strings);
        }
    }

    /** The classes in {@code files} that read a projection's variants without being allowed to, as "class reads pattern". */
    static List<String> projectionReaders(List<byte[]> files) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = Scanned.of(bytes);
            PROJECTION_READERS.forEach((pattern, allowed) -> {
                if (!allowed.test(scanned.name()) && scanned.strings().stream().anyMatch(s -> pattern.matcher(s).find()))
                    found.add(scanned.name() + " reads " + pattern.pattern());
            });
        }
        return found;
    }

    @Test void onlyTheFactoriesReadTheLooksVariants() throws Exception {
        var files = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        var live = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = Scanned.of(bytes);
            if (scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/WorkspaceLook$Menus"))) live.add(scanned.name());
        }
        assertThat(live).as("the scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/workspace/WorkspaceChrome"));
        assertThat(files.stream().map(Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/DialogMapping$Kind")))
                .map(Scanned::name))
            .as("the dialog mapping's scan is live").contains("dev/jasper/app/windows/AuxiliaryWindows");
        // Plan 8b: the palette's two scans are live.
        assertThat(files.stream().map(Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/PaletteLook$View")))
                .map(Scanned::name))
            .as("the palette view's scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/palette/PaletteViews"));
        assertThat(files.stream().map(Scanned::of)
                .filter(scanned -> scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/PaletteLook$Home")))
                .map(Scanned::name))
            .as("the palette home's scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/workspace/WorkspaceChrome"));
        assertThat(projectionReaders(files)).isEmpty();
    }

    @Test void aLookReadElsewhereIsReported() {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.WindowChromeProbe"), builder -> builder
            .withField("menus", ClassDesc.of("dev.jasper.app.presentation.WorkspaceLook$Menus"), ClassFile.ACC_PRIVATE)
            .withField("motion", ClassDesc.of("dev.jasper.app.presentation.Motion"), ClassFile.ACC_PRIVATE)
            .withField("dialogs", ClassDesc.of("dev.jasper.app.presentation.DialogMapping$Kind"), ClassFile.ACC_PRIVATE)
            .withField("view", ClassDesc.of("dev.jasper.app.presentation.PaletteLook$View"), ClassFile.ACC_PRIVATE)
            .withField("home", ClassDesc.of("dev.jasper.app.presentation.PaletteLook$Home"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(5);
    }

    /** Plan 8b (spec 16.1.8): the palette's strategies, its two views and two homes. */
    static final List<String> PALETTE_STRATEGIES = List.of("dev/jasper/app/palette/CardView", "dev/jasper/app/palette/RunWindowView",
        "dev/jasper/app/workspace/TitleFieldHome", "dev/jasper/app/workspace/CornerHome");

    /** Plan 8b (spec 3.3): the palette's strategies never name the kit, its variants or ChromeSpec. */
    @Test void thePalettesStrategiesNeverNameTheKit() throws Exception {
        var seen = new ArrayList<String>();
        var found = new ArrayList<String>();
        for (var bytes : StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400)) {
            var scanned = Scanned.of(bytes);
            if (PALETTE_STRATEGIES.stream().noneMatch(strategy -> scanned.name().equals(strategy) || scanned.name().startsWith(strategy + "$"))) continue;
            seen.add(scanned.name());
            for (String s : scanned.strings()) if (StrategyIsolationTest.FORBIDDEN.matcher(s).find()) found.add(scanned.name() + " names " + s);
        }
        assertThat(seen).as("the scan is live").containsAll(PALETTE_STRATEGIES);
        assertThat(found).isEmpty();
    }

    /** Plan 9a: spec 9.1's composition roots, the only classes besides the presentation package that read Composition. */
    static final Set<String> COMPOSITION_ROOTS = Set.of("dev/jasper/app/settingsui/CorePages",
        "dev/jasper/app/settingsui/NotificationGroupsEditor", "dev/jasper/app/settingsui/Editors",
        "dev/jasper/app/shortcuthelp/KeymapPage", "dev/jasper/app/pluginmanager/PluginsPage");

    /** Whether the class {@code type} (an internal name) may read Composition: the presentation package, a root or a class nested in one. */
    static boolean compositionRoot(String type) {
        if (type.startsWith("dev/jasper/app/presentation/")) return true;
        int nested = type.indexOf('$');
        return COMPOSITION_ROOTS.contains(nested < 0 ? type : type.substring(0, nested));
    }

    /** Plan 9a: the Composition scan is live, a read elsewhere is reported, and a class that only carries the value is not. */
    @Test void compositionIsReadOnlyByItsRoots() throws Exception {
        var files = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        var read = Pattern.compile("^dev/jasper/app/presentation/Composition$");
        assertThat(files.stream().map(Scanned::of).filter(scanned -> scanned.strings().stream().anyMatch(s -> read.matcher(s).find()))
                .map(Scanned::name))
            .as("the composition scan is live").contains("dev/jasper/app/presentation/Projections");
        var composition = ClassDesc.of("dev.jasper.app.presentation.Composition");
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.CompositionProbe"), builder -> builder
            .withMethodBody("probe", java.lang.constant.MethodTypeDesc.of(composition), ClassFile.ACC_STATIC,
                code -> code.getstatic(composition, "CLASSIC", composition).areturn()));
        byte[] carrier = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.CompositionCarrier"), builder -> builder
            .withField("composition", composition, ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe, carrier)))
            .containsExactly("dev/jasper/app/workspace/CompositionProbe reads " + read.pattern());
    }
}
