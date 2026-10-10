package dev.jasper.app.themeimport;
import dev.jasper.ui.themeimport.ConversionResult;

import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.themeio.ThemeTomlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.themeimport.ThemeFilesTest.named;
import static org.assertj.core.api.Assertions.*;

class ThemeImporterTest {
    @TempDir Path home;
    @Test void catalogAppearanceAdvisoriesStayWarningsWhileMalformedFilesStayFailures() throws Exception {
        Path themes = home.resolve("themes"); Files.createDirectories(themes);
        Path warned = themes.resolve("warned.toml");
        String original = "name='Warned'\nextends='Jasper Dark'\n[ui]\n'SearchEverywhere.SearchField.background'='#123456'\n";
        Files.writeString(warned, original);
        var importer = new ThemeImporter(home);
        var plan = importer.plan(source("New theme"));
        assertThat(plan.items()).hasSize(1);
        assertThat(plan.failures()).isEmpty();
        var report = importer.commit(plan, Map.of());
        assertThat(report.imported()).hasSize(1);
        assertThat(report.failures()).isEmpty();
        assertThat(report.warnings().stream().filter(w -> w.contains("warned.toml") && w.contains("SearchEverywhere.SearchField.background"))).hasSize(1);
        assertThat(Files.readString(warned)).isEqualTo(original);
        Files.writeString(themes.resolve("broken.toml"), "name = [not toml");
        var mixed = importer.plan(source("Another theme"));
        assertThat(mixed.items()).hasSize(1);
        assertThat(mixed.failures()).singleElement().satisfies(failure -> assertThat(failure.sourceEntry()).endsWith("broken.toml"));
        var imported = importer.commit(mixed, Map.of());
        assertThat(imported.imported()).hasSize(1);
        assertThat(imported.failures()).hasSize(1);
        assertThat(imported.warnings()).anySatisfy(warning -> assertThat(warning).contains("warned.toml", "SearchEverywhere.SearchField.background"));
    }

    @Test void planningRejectsUnresolvedColorsWithSourceDiagnostics() throws Exception {
        var importer = new ThemeImporter(home);
        for (var ui : ThemeFilesTest.invalidUi()) {
            Path source = home.resolve("invalid.toml");
            Files.writeString(source, ThemeTomlWriter.write(ThemeFilesTest.withUi("Invalid", ui)));
            byte[] original = Files.readAllBytes(source);
            var plan = importer.plan(source);
            assertThat(plan.items()).isEmpty();
            assertThat(plan.failures()).singleElement().satisfies(failure -> {
                assertThat(failure.sourceEntry()).contains("invalid.toml");
                assertThat(failure.message()).contains("cannot resolve [ui] color Panel.background");
            });
            assertThat(Files.readAllBytes(source)).isEqualTo(original);
        }
    }

    @Test void invalidReplacementPreservesOriginalBytesCatalogAndSelectedTheme() throws Exception {
        var files = new ThemeFiles(home);
        Path target = files.create(named("Example"));
        byte[] original = Files.readAllBytes(target);
        var catalog = dev.jasper.app.appearance.ThemeCatalog.beside(home.resolve("config.toml"));
        var selected = catalog.scan().find("Example").orElseThrow();
        var controller = new dev.jasper.app.appearance.ThemeController[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> controller[0] = new dev.jasper.app.appearance.ThemeController(selected));
        try {
            var importer = new ThemeImporter(home);
            var valid = importer.plan(source("Example")).items().getFirst();
            for (var ui : ThemeFilesTest.invalidUi()) {
                // Public frozen plans can be supplied directly: commit must independently validate them.
                var invalid = new ThemeImporter.Item(valid.id(),
                    new ConversionResult(ThemeFilesTest.withUi("Example", ui), List.of(), "invalid.toml"),
                    valid.target(), valid.expectedSha256(), valid.shadowsBundled());
                var report = importer.commit(new ThemeImporter.ImportPlan(List.of(invalid), List.of()),
                    Map.of(valid.id(), ThemeImporter.ConflictChoice.REPLACE));
                assertThat(report.imported()).isEmpty();
                assertThat(report.failures()).singleElement().satisfies(failure ->
                    assertThat(failure.message()).contains("cannot resolve [ui] color Panel.background"));
                assertThat(Files.readAllBytes(target)).isEqualTo(original);
                assertThat(catalog.scan().find("Example")).containsSame(selected);
                assertThat(catalog.latest().problems()).allSatisfy(problem -> {
                    assertThat(problem.advisory()).isTrue();
                    assertThat(problem.message()).contains("SearchEverywhere.SearchField.background", "[semantic]");
                });
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    assertThat(controller[0].choice()).isSameAs(selected);
                    assertThat(controller[0].current().chrome().name()).isEqualTo("Example");
                });
            }
        } finally { javax.swing.SwingUtilities.invokeAndWait(controller[0]::close); }
    }

    @Test void nativeSemanticIntentAndDiscardDiagnosticsSurviveKeepBothWithoutTouchingSource() throws Exception {
        new ThemeFiles(home).create(named("Authored"));
        Path source = home.resolve("authored.toml");
        Files.writeString(source, "name='Authored'\nextends='Jasper Dark'\n[semantic]\n'surface.card'='#12345680'\n[ui]\n'Button.arc'=99\n[workspace]\nopacity=0.7\n");
        byte[] before = Files.readAllBytes(source);
        var importer = new ThemeImporter(home);
        var plan = importer.plan(source);
        assertThat(plan.failures()).isEmpty();
        var item = plan.items().getFirst();
        assertThat(item.conversion().warnings().stream().filter(w -> w.contains("Button.arc"))).hasSize(1);
        var report = importer.commit(plan, Map.of(item.id(), ThemeImporter.ConflictChoice.KEEP_BOTH));
        assertThat(report.failures()).isEmpty();
        var loaded = ThemeToml.parse(Files.readString(report.imported().getFirst().path()), "installed");
        assertThat(loaded.name()).isEqualTo("Authored 2");
        assertThat(loaded.semanticOverrides()).containsEntry(dev.jasper.ui.theme.SemanticColor.SURFACE_CARD, new java.awt.Color(0x12,0x34,0x56,0x80));
        assertThat(loaded.workspace().levels().opacity()).isEqualTo(.7);
        assertThat(loaded.ui()).doesNotContainKey("Button.arc");
        assertThat(Files.readAllBytes(source)).isEqualTo(before);
    }

    private Path source(String name) throws Exception {
        Path file = home.resolve("source.toml"); Files.writeString(file, ThemeTomlWriter.write(named(name))); return file;
    }
    @Test void keepBothPreservesOriginal() throws Exception {
        var files = new ThemeFiles(home);
        Path first = files.create(named("Example")), second = files.create(named("Example 2"));
        byte[] before = Files.readAllBytes(first), before2 = Files.readAllBytes(second);
        var importer = new ThemeImporter(home); var plan = importer.plan(source("Example"));
        var report = importer.commit(plan, Map.of(plan.items().getFirst().id(), ThemeImporter.ConflictChoice.KEEP_BOTH));
        assertThat(report.failures()).isEmpty();
        assertThat(report.imported()).singleElement().satisfies(item -> assertThat(item.name()).isEqualTo("Example 3"));
        assertThat(Files.readAllBytes(first)).isEqualTo(before); assertThat(Files.readAllBytes(second)).isEqualTo(before2);
    }
    @Test void replaceRequiresUnchangedTarget() throws Exception {
        Path target = new ThemeFiles(home).create(named("Example"));
        var importer = new ThemeImporter(home); var plan = importer.plan(source("Example"));
        Files.writeString(target, "# edit\n" + Files.readString(target)); String edited = Files.readString(target);
        var report = importer.commit(plan, Map.of(plan.items().getFirst().id(), ThemeImporter.ConflictChoice.REPLACE));
        assertThat(report.imported()).isEmpty(); assertThat(report.failures()).hasSize(1);
        assertThat(Files.readString(target)).isEqualTo(edited);
    }
    @Test void newConflictAfterPlanIsNotOverwritten() throws Exception {
        var importer = new ThemeImporter(home); var plan = importer.plan(source("Example"));
        Path target = new ThemeFiles(home).create(named("Example")); String original = Files.readString(target);
        var report = importer.commit(plan, Map.of());
        assertThat(report.imported()).isEmpty(); assertThat(report.failures()).hasSize(1);
        assertThat(Files.readString(target)).isEqualTo(original);
    }
    @Test void reportsPartialArchiveByProviderAndStableIndependentChoices() throws Exception {
        Path archive = ThemeFixtures.jar(home.resolve("many.jar"), ThemeFixtures.entries(
            "META-INF/plugin.xml", ThemeFixtures.pluginXml("Many", "one.theme.json", "two.theme.json", "bad.theme.json"),
            "one.theme.json", ThemeFixtures.theme("Twin", true, null, "#123456"),
            "two.theme.json", ThemeFixtures.theme("Twin", true, null, "#654321"), "bad.theme.json", "broken"));
        var importer = new ThemeImporter(home); var plan = importer.plan(archive);
        assertThat(plan.items()).hasSize(2); assertThat(plan.items().stream().map(ThemeImporter.Item::id)).doesNotHaveDuplicates();
        assertThat(importer.plan(archive).items().stream().map(ThemeImporter.Item::id).toList()).isEqualTo(plan.items().stream().map(ThemeImporter.Item::id).toList());
        Files.delete(archive); // Commit consumes the frozen immutable plan, not a reread source.
        var report = importer.commit(plan, Map.of());
        assertThat(report.imported().stream().map(ThemeImporter.Imported::name).toList()).containsExactly("Twin", "Twin 2");
        assertThat(report.failures()).hasSize(1); assertThat(report.warnings()).anyMatch(warning -> warning.contains("console"));
        for (var item : report.imported()) assertThat(ThemeToml.parse(Files.readString(item.path()), item.id()).name()).isEqualTo(item.name());
    }
    @Test void conflictsDefaultToSkipAndBuiltinOverlapIsExplicit() throws Exception {
        new ThemeFiles(home).create(named("Example")); var importer = new ThemeImporter(home);
        assertThat(importer.commit(importer.plan(source("Example")), Map.of()).imported()).isEmpty();
        var builtin = importer.plan(source("Jasper Dark"));
        assertThat(builtin.items().getFirst().shadowsBundled()).isTrue();
        var report = importer.commit(builtin, Map.of()); assertThat(report.imported()).hasSize(1);
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("bundled"));
    }
    @Test void validatesWholeSelectionBeforeWriting() throws Exception {
        var importer = new ThemeImporter(home); var valid = importer.plan(source("Valid")).items().getFirst();
        var invalid = new ThemeImporter.Item("bad", new ConversionResult(named("x".repeat(300_000)), List.of(), "bad"),
            java.util.Optional.empty(), java.util.Optional.empty(), false);
        var report = importer.commit(new ThemeImporter.ImportPlan(List.of(valid, invalid), List.of()), Map.of());
        assertThat(report.imported()).isEmpty(); assertThat(report.failures()).isNotEmpty();
        try (var files = Files.list(home.resolve("themes"))) { assertThat(files.filter(p -> p.toString().endsWith(".toml"))).isEmpty(); }
    }

    @Test void partialPublicationReportsExactlyTheFilesThatCommitted() throws Exception {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        var files = new ThemeFiles(home, (stage, path) -> {
            if (stage.equals("publish") && counter.incrementAndGet() == 2)
                throw new java.io.UncheckedIOException(new java.io.IOException("second publication failed"));
        });
        var importer = new ThemeImporter(home, files);
        var first = importer.plan(source("First")).items().getFirst();
        var second = importer.plan(source("Second")).items().getFirst();
        // They share the physical source path but represent separate frozen provider identities here.
        second = new ThemeImporter.Item("second-provider", second.conversion(), second.target(), second.expectedSha256(), false);
        var report = importer.commit(new ThemeImporter.ImportPlan(List.of(first, second), List.of()), Map.of());
        assertThat(report.imported()).singleElement().satisfies(item -> assertThat(item.name()).isEqualTo("First"));
        assertThat(report.failures()).singleElement().satisfies(failure -> assertThat(failure.message()).contains("second publication"));
        try (var entries = Files.list(home.resolve("themes"))) { assertThat(entries.map(p -> p.getFileName().toString()).toList()).containsExactly("first.toml"); }
    }

    @Test void freshReplacementPublishesThePlannedBytesAtTheSamePath() throws Exception {
        Path target = new ThemeFiles(home).create(named("Example"));
        Path source = source("Example");
        Files.writeString(source, Files.readString(source).replace("#191c20", "#123456"));
        var importer = new ThemeImporter(home); var plan = importer.plan(source);
        var report = importer.commit(plan, Map.of(plan.items().getFirst().id(), ThemeImporter.ConflictChoice.REPLACE));
        assertThat(report.failures()).isEmpty();
        assertThat(report.imported()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Example");
            assertThat(item.path()).hasSameBinaryContentAs(target);
        });
        assertThat(ThemeToml.parse(Files.readString(target), "saved").seeds())
            .containsEntry(dev.jasper.ui.theme.Seed.BACKGROUND, new java.awt.Color(0x123456));
    }

    @Test void sameNamedProvidersCanBeChosenIndependently() {
        Path archive = ThemeFixtures.jar(home.resolve("twins.jar"), ThemeFixtures.entries(
            "META-INF/plugin.xml", ThemeFixtures.pluginXml("Twins", "one.theme.json", "two.theme.json"),
            "one.theme.json", ThemeFixtures.theme("Twin", true, null, "#123456"),
            "two.theme.json", ThemeFixtures.theme("Twin", true, null, "#654321")));
        var importer = new ThemeImporter(home); var plan = importer.plan(archive);
        var report = importer.commit(plan, Map.of(plan.items().getFirst().id(), ThemeImporter.ConflictChoice.SKIP));
        assertThat(report.failures()).isEmpty();
        assertThat(report.imported()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(plan.items().getLast().id());
            assertThat(item.name()).isEqualTo("Twin");
        });
    }

    @Test void oneUnsafeConflictDoesNotHideOtherProviders() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Path original = Files.writeString(home.resolve("elsewhere.toml"), ThemeTomlWriter.write(named("Blocked")));
        Files.createSymbolicLink(home.resolve("themes/blocked.toml"), original);
        Path archive = ThemeFixtures.jar(home.resolve("many.jar"), ThemeFixtures.entries(
            "META-INF/plugin.xml", ThemeFixtures.pluginXml("Many", "one.theme.json", "two.theme.json", "three.theme.json"),
            "one.theme.json", ThemeFixtures.theme("One", true, null, "#123456"),
            "two.theme.json", ThemeFixtures.theme("Blocked", true, null, "#123456"),
            "three.theme.json", ThemeFixtures.theme("Three", true, null, "#123456")));
        var plan = new ThemeImporter(home).plan(archive);
        assertThat(plan.items().stream().map(item -> item.conversion().document().name()).toList()).containsExactly("One", "Three");
        assertThat(plan.failures()).singleElement().satisfies(failure -> assertThat(failure.sourceEntry()).isEqualTo("two.theme.json"));
    }
}
