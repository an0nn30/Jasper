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

}
