package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ThemeDuplicateTest {
    @TempDir Path home;
    private static GeneratedTheme effective(Theme theme) throws Exception {
        GeneratedTheme[] value = new GeneratedTheme[1]; edt(() -> value[0] = ThemeManager.generated(theme)); return value[0];
    }
    @Test void inheritedTemplateCommentsEveryCurrentValueWithoutActivatingOverrides() throws Exception {
        var generated = effective(Theme.DARK);
        String text = ThemeDuplicate.text(Theme.DARK, generated, "My \"Night\"\nTheme \uD83C\uDF19");
        var copy = ThemeToml.parse(text, "copy.toml");
        assertThat(copy.name()).isEqualTo("My \"Night\"\nTheme \uD83C\uDF19");
        assertThat(copy.parent()).contains("Jasper Dark");
        assertThat(copy.seeds()).isEmpty(); assertThat(copy.tokens()).isEmpty(); assertThat(copy.ansi()).isEmpty();
        assertThat(org.tomlj.Toml.parse(text).keySet()).containsExactlyInAnyOrder("name", "extends");
        String seeds = text.substring(text.indexOf("# [colors]"), text.indexOf("# [colors.normal]"));
        String tokens = text.substring(text.indexOf("# [tokens]"));
        for (Seed seed : Seed.values()) assertThat(seeds.lines().filter(line -> line.startsWith("# " + seed.key() + " = ")).count()).isEqualTo(1);
        for (Token token : Token.values()) assertThat(tokens.lines().filter(line -> line.startsWith("# " + token.key() + " = ")).count()).isEqualTo(1);
        assertThat(seeds).contains("# background = \"#191c20\"", "# foreground = \"#d5dee5\"");
        var uncommented = ThemeToml.parse(text.replaceAll("(?m)^# ", ""), "expanded.toml");
        assertThat(uncommented.ansi()).hasSize(16); assertThat(uncommented.tokens()).hasSize(Token.values().length);
        assertThat(ThemeResolver.resolve(copy, name -> List.of(Theme.DARK.source().document())).seeds()).isEqualTo(Theme.DARK.source().document().seeds());
    }
    @Test void duplicateRejectsTakenDisplayNameAndChangedParent() throws Exception {
        var files = new ThemeFiles(home); var generated = effective(Theme.DARK);
        for (String name : List.of("jasper DARK", "match", "  "))
            assertThatThrownBy(() -> files.duplicate(Theme.DARK, generated, name)).isInstanceOf(IOException.class);
        Path base = files.create(ThemeFilesTest.named("Parent"));
        Theme original = ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Parent").orElseThrow();
        var values = effective(original);
        Files.writeString(base, Files.readString(base).replace("#191c20", "#112233"));
        assertThatThrownBy(() -> files.duplicate(original, values, "Child")).isInstanceOf(IOException.class);
        assertThat(home.resolve("themes/child.toml")).doesNotExist();
    }
    @Test void duplicateNeverOverwritesOccupiedFilename() throws Exception {
        var files = new ThemeFiles(home); Files.createDirectories(home.resolve("themes"));
        Path occupied = Files.writeString(home.resolve("themes/copy.toml"), "keep this invalid file");
        Path created = files.duplicate(Theme.DARK, effective(Theme.DARK), "Copy");
        assertThat(created.getFileName().toString()).isEqualTo("copy-2.toml");
        assertThat(Files.readString(occupied)).isEqualTo("keep this invalid file");
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Copy")).isPresent();
    }
    @Test void duplicateRechecksParentAfterPreparingTemporary() throws Exception {
        var files = new ThemeFiles(home); Path base = files.create(ThemeFilesTest.named("Parent"));
        var original = ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Parent").orElseThrow();
        var values = effective(original);
        var racing = new ThemeFiles(home, (stage, target) -> {
            if (stage.equals("publish")) try { Files.writeString(base, "name='Parent'\nextends='Jasper Light'\n"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        assertThatThrownBy(() -> racing.duplicate(original, values, "Child")).isInstanceOf(IOException.class);
        assertThat(home.resolve("themes/child.toml")).doesNotExist();
    }
    @Test void duplicateRequiresWorkerThread() throws Exception {
        var files = new ThemeFiles(home); var generated = effective(Theme.DARK);
        edt(() -> assertThatThrownBy(() -> files.duplicate(Theme.DARK, generated, "Copy")).isInstanceOf(IllegalStateException.class));
    }
}
