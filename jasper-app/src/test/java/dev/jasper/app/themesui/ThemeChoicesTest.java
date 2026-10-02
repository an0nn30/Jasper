package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeChoicesTest {
    static Theme user(String name) {
        var base = Theme.DARK.source().document();
        var document = new ThemeDocument("user.toml", name, base.dark(), base.parent(), 0, base.uiComplete(), base.metadata(),
            base.seeds(), base.ansi(), base.tokens(), base.syntax(), base.ui(), base.icons());
        return Theme.of("user:fixture", new Theme.UserFile(Path.of("themes/user.toml"), document));
    }
    @Test void queryAndBothFiltersCombineWithoutChangingCatalogOrder() {
        Theme mine = user("Mine Dark");
        var catalog = new ThemeCatalog.Snapshot(List.of(mine, Theme.JASPER_LIGHT, Theme.DARK), List.of());
        assertThat(ThemeChoices.filter(catalog, "dArK", ThemeChoices.Tone.DARK, ThemeChoices.Source.ALL))
            .containsExactly(Theme.DARK, mine);
        assertThat(ThemeChoices.filter(catalog, "dark", ThemeChoices.Tone.ALL, ThemeChoices.Source.MINE)).containsExactly(mine);
        assertThat(ThemeChoices.filter(catalog, "", ThemeChoices.Tone.LIGHT, ThemeChoices.Source.BUNDLED)).containsExactly(Theme.JASPER_LIGHT);
        assertThat(ThemeChoices.filter(catalog, "missing", ThemeChoices.Tone.ALL, ThemeChoices.Source.ALL)).isEmpty();
        assertThat(catalog.themes()).containsExactly(mine, Theme.JASPER_LIGHT, Theme.DARK);
    }
    @Test void userWinnerOfBundledNameBelongsToMineOnly() {
        var override = user("Jasper Dark");
        var snapshot = new ThemeCatalog.Snapshot(List.of(override, Theme.JASPER_LIGHT), List.of());
        assertThat(ThemeChoices.filter(snapshot, "Jasper Dark", ThemeChoices.Tone.ALL, ThemeChoices.Source.MINE)).containsExactly(override);
        assertThat(ThemeChoices.filter(snapshot, "Jasper Dark", ThemeChoices.Tone.ALL, ThemeChoices.Source.BUNDLED)).isEmpty();
    }
}
