package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.config.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Path;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class SettingsCompositionTest {
    @Test void embeddedNumbersKeepAccessibleNamesWithoutRepeatingTheirVisibleRowLabel() {
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var page = SchemaPage.page("fixture", "Fixture").row(CoreSettings.WINDOW_COLUMNS, Editors.integer("columns")).build(PageKit.context(draft, id -> {}, () -> {}));
        try {
            var label = PageKit.kit(page, "window.columns.label", JLabel.class);
            assertThat(label.isVisible()).isFalse();
            var input = PageKit.kit(page, "window.columns.input", javax.swing.text.JTextComponent.class);
            assertThat(input.getAccessibleContext().getAccessibleName()).isEqualTo(CoreSettings.WINDOW_COLUMNS.label());
        } finally { page.dispose(); }
    }
    @Test void theRealGroupedNotificationPageFitsTheAvailableViewportWidth() {
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var groups = new NotificationGroupChoices(() -> List.of(new NotificationGroupChoices.Group("configuration", "Configuration", "Jasper", "sticky"), new NotificationGroupChoices.Group("editor", "Editor", "Editor", "card")));
        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), ()->{}, ()->{}, ()->{}, ()->{}, ()->false);
        var page = CorePages.specs(AppearanceFixtures.CHOICES, system, ShellChoices.NONE, id->{}, true, groups).stream().filter(spec -> spec.id().equals("notifications")).findFirst().orElseThrow().build(PageKit.context(draft, id->{}, ()->{}));
        try {
            var body = (PageBody)page.component(); var viewport = new JViewport(); viewport.setSize(752, 580); viewport.setView(body); viewport.doLayout();
            assertThat(body.getScrollableTracksViewportWidth()).isTrue();
            assertThat(body.getMinimumSize().width).isLessThanOrEqualTo(752);
        } finally { page.dispose(); }
    }
}
