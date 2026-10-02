package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.appearance.ThemeToml;
import dev.jasper.app.appearance.ThemeTomlWriter;
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
            .containsEntry(dev.jasper.app.appearance.Seed.BACKGROUND, new java.awt.Color(0x123456));
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
