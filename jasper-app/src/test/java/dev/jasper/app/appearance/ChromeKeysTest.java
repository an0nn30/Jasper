package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.Map;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Transitional alias publication, not a second color derivation engine. Task 6 removes notification aliases. */
@ExtendWith(EdtTestExtension.class)
class ChromeKeysTest {
    @Test void residualNotificationAliasesComeFromTheInstalledModuleSnapshotAndNeverStayStale() {
        for (var theme : java.util.List.of(Theme.DARK, Theme.JASPER_LIGHT, Theme.DARK)) {
            ThemeManager.install(theme);
            var palette = ThemeManager.generated(theme).semantic();
            Map.of("Jasper.notification.background", SemanticColor.SURFACE_CARD,
                "Jasper.notification.borderColor", SemanticColor.BORDER_SUBTLE,
                "Jasper.notification.foreground", SemanticColor.TEXT_PRIMARY,
                "Jasper.notification.mutedForeground", SemanticColor.TEXT_SECONDARY,
                "Jasper.notification.linkForeground", SemanticColor.TEXT_LINK).forEach((key, role) ->
                    assertThat(UIManager.getColor(key)).as(key).isEqualTo(palette.color(role)));
        }
    }
    @Test void explicitNativeResidualAliasWinsOnlyOnItsOwnInstall() {
        var base = Theme.DARK.source().document();
        var ui = new java.util.LinkedHashMap<>(base.ui());
        ui.put("Jasper.notification.background", "#123456");
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), base.parentLine(),
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(), ui, base.icons(), base.workspace());
        ThemeManager.install(ThemeGenerator.generate(document));
        assertThat(UIManager.getColor("Jasper.notification.background")).isEqualTo(new Color(0x123456));
        ThemeManager.install(Theme.JASPER_LIGHT);
        assertThat(UIManager.getColor("Jasper.notification.background"))
            .isEqualTo(ThemeManager.generated(Theme.JASPER_LIGHT).semantic().color(SemanticColor.SURFACE_CARD));
    }
}
