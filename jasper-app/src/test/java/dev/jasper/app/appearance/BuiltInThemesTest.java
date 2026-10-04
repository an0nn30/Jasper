package dev.jasper.app.appearance;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** The bundled themes load without their [ui] and [icons] tables, which are nearly all of their bytes. */
class BuiltInThemesTest {
    @Test void aBundledThemeReadsItsDocumentOnFirstUseAndOnce() {
        var document = ThemeResolver.resolve(ThemeToml.parse(ThemeManager.read("jasper-dark.toml"), "jasper-dark.toml"));
        var reads = new AtomicInteger();
        var source = new Theme.Bundled("jasper-dark.toml", () -> { reads.incrementAndGet(); return document; });
        assertThat(reads).hasValue(0);
        assertThat(source.document()).isSameAs(document);
        assertThat(source.document()).isSameAs(document);
        assertThat(reads).hasValue(1);
    }

    @Test void bundledThemesAreEqualByFileWithoutReadingTheirDocuments() {
        var dark = new Theme.Bundled("jasper-dark.toml", BuiltInThemesTest::unread);
        assertThat(dark).isEqualTo(new Theme.Bundled("jasper-dark.toml", BuiltInThemesTest::unread))
            .hasSameHashCodeAs(new Theme.Bundled("jasper-dark.toml", BuiltInThemesTest::unread))
            .isNotEqualTo(new Theme.Bundled("darcula.toml", BuiltInThemesTest::unread));
    }

    @Test void theHeadEndsAtTheFirstUiOrIconsTable() {
        String head = "name = \"X\"\nui_complete = true\n\n[colors]\nbackground = \"#000000\"\n";
        assertThat(BuiltInThemes.head(head + "[ui]\nA = \"#111111\"\n[icons]\nB = \"x\"\n")).isEqualTo(head);
        assertThat(BuiltInThemes.head(head + "[ui.Button]\nA = \"#111111\"\n")).isEqualTo(head);
        assertThat(BuiltInThemes.head(head + "[icons]\nB = \"x\"\n")).isEqualTo(head);
        assertThat(BuiltInThemes.head(head)).isEqualTo(head);
    }

    @Test void everyBundledThemeIsWhatItsWholeFileGenerates() {
        for (Theme theme : BuiltInThemes.all())
            assertThat(Theme.of(theme.id(), theme.source())).as(theme.id()).isEqualTo(theme);
    }

    private static ThemeDocument unread() { throw new AssertionError("A bundled theme's document was read"); }
}
