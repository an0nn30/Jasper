package dev.jasper.app.themesui;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.themeimport.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(EdtTestExtension.class)
class ThemeImportViewTest {
    @Test void distinctProviderDecisionsAreLiteralAndDefaultsSafe() {
        var conversion=new ConversionResult(Theme.DARK.source().document(),List.of("<html>console fallback"),"provider");
        var old=new ThemeImporter.Item("old",conversion,Optional.of(Path.of("mine.toml")),Optional.of("hash"),false);
        var fresh=new ThemeImporter.Item("fresh",conversion,Optional.empty(),Optional.empty(),true);
        var result=new AtomicReference<Map<String,ThemeImporter.ConflictChoice>>();
        var view=new ThemeImportView(new ThemeImporter.ImportPlan(List.of(old,fresh),List.of(new ThemeConverter.Failure("bad","broken"))),result::set,()->{});
        assertThat(view.choices()).containsEntry("old",ThemeImporter.ConflictChoice.SKIP).containsEntry("fresh",ThemeImporter.ConflictChoice.KEEP_BOTH);
        view.decisions.get("old").setSelectedItem(ThemeImporter.ConflictChoice.REPLACE); view.decisions.get("fresh").setSelectedItem(ThemeImporter.ConflictChoice.SKIP); view.commit.doClick();
        assertThat(result.get()).containsEntry("old",ThemeImporter.ConflictChoice.REPLACE).containsEntry("fresh",ThemeImporter.ConflictChoice.SKIP);
        assertThat(view.details.getText()).contains("<html>console fallback", "bad", "broken"); assertThat(view.details.getClientProperty("html")).isNull();
        var empty=new ThemeImportView(new ThemeImporter.ImportPlan(List.of(),List.of(new ThemeConverter.Failure("bad","broken"))),m->fail("commit"),()->{});
        assertThat(empty.commit.isEnabled()).isFalse();
    }
}
