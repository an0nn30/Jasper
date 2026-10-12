package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.util.List;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transitional alias publication, not a second color derivation engine. Plan 2 Task 6c migrated the notification
 * surfaces to module roles (ruling G8): the five former notification aliases are no longer published.
 */
@ExtendWith(EdtTestExtension.class)
class ChromeKeysTest {
    private static final List<String> NOTIFICATION_ALIASES = List.of("Jasper.notification.background", "Jasper.notification.borderColor",
        "Jasper.notification.foreground", "Jasper.notification.mutedForeground", "Jasper.notification.linkForeground");

    @Test void notificationAliasesAreNoLongerPublishedByAnyInstall() {
        for (boolean themeDark : new boolean[]{true, false, true}) {
            ThemeTestSupport.install(themeDark);
            for (String key : NOTIFICATION_ALIASES) {
                assertThat(UIManager.getColor(key)).as(key).isNull();
                assertThat(((com.formdev.flatlaf.FlatLaf) UIManager.getLookAndFeel()).getExtraDefaults()).as(key).doesNotContainKey(key);
            }
        }
    }

    /**
     * An authored notification key is now an unsupported raw override (Task 11 owns its diagnostic): the native [ui]
     * last word still copies it for that install only, nothing reads it, and the next install does not keep it.
     */
    @Test void anAuthoredNotificationKeyIsARawOverrideForItsOwnInstallOnly() {
        var base = Theme.DARK.source().document();
        var ui = new java.util.LinkedHashMap<>(base.ui());
        ui.put("Jasper.notification.background", "#123456");
        var document = new ThemeDocument(base.origin(), base.name(), base.dark(), base.parent(), base.parentLine(),
            base.uiComplete(), base.metadata(), base.seeds(), base.ansi(), base.tokens(), base.syntax(), ui, base.icons(), base.workspace());
        ThemeManager.install(ThemeGenerator.generate(document));
        assertThat(UIManager.getColor("Jasper.notification.background")).isEqualTo(new Color(0x123456));
        ThemeManager.install(Theme.JASPER_LIGHT);
        assertThat(UIManager.getColor("Jasper.notification.background")).as("not stale after the next install").isNull();
        for (String key : NOTIFICATION_ALIASES.subList(1, NOTIFICATION_ALIASES.size())) assertThat(UIManager.getColor(key)).as(key).isNull();
    }
}
