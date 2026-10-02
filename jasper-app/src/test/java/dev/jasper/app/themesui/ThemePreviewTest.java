package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemePreviewTest {
    @Test void twoZonesPaintEffectiveColorsWithoutInstallingAndReuseOneTerminal() {
        var light = ThemeManager.generated(Theme.INTELLIJ_LIGHT);
        var dark = ThemeManager.generated(ThemeCatalog.builtIn().latest().themes().stream().filter(t -> t.name().equals("Dracula")).findFirst().orElseThrow());
        var laf = UIManager.getLookAndFeel(); var panel = UIManager.get("Panel.background");
        var preview = new ThemePreview(ConfigSnapshot.defaults().viewOptions(12, dark.terminal()));
        try {
            preview.show(new ResolvedTheme(light, dark)); var sample = preview.sample;
            assertThat(sample.palette()).isEqualTo(dark.terminal());
            check(preview, "theme-preview-title", light.tokens().get(Token.TITLE_BAR));
            check(preview, "theme-preview-sidebar", light.tokens().get(Token.SIDEBAR));
            check(preview, "theme-preview-status", light.tokens().get(Token.STATUS_BAR));
            check(preview, "theme-preview-tabs", dark.tokens().get(Token.TAB_BAR));
            check(preview, "theme-preview-editor", dark.tokens().get(Token.EDITOR_BACKGROUND));
            preview.show(new ResolvedTheme(dark, light));
            check(preview, "theme-preview-title", dark.tokens().get(Token.TITLE_BAR));
            check(preview, "theme-preview-editor", light.tokens().get(Token.EDITOR_BACKGROUND));
            assertThat(preview.sample).isSameAs(sample); assertThat(sample.palette()).isEqualTo(light.terminal());
            assertThat(UIManager.getLookAndFeel()).isSameAs(laf); assertThat(UIManager.get("Panel.background")).isEqualTo(panel);
        } finally { preview.close(); preview.close(); }
        assertThat(preview.sample.closed()).isTrue();
    }
    @Test void stagedMatchAndMissingNamesLeaveControllerAndDraftUntouched() {
        var controller = new ThemeController(t -> true); var count = new AtomicInteger();
        var subscription = controller.subscribe((theme, chrome) -> count.incrementAndGet()); int before = count.get();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var context = new PageContext(draft, s -> {}, Runnable::run, (s,p) -> Optional.empty(), () -> {});
        var page = new ThemesPage(context, () -> new ThemeCatalog.Snapshot(List.of(Theme.JASPER_LIGHT), List.of()),
            () -> ConfigSnapshot.defaults().viewOptions(12, Theme.DARK.palette()));
        try {
            draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("Jasper Light")); page.component();
            assertThat(page.preview.themes.matching()).isTrue();
            draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("missing"));
            draft.stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of("missing workspace"));
            assertThat(page.preview.themes.chrome().dark()).isFalse();
            assertThat(page.preview.themes.matching()).isTrue();
            assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("missing");
            assertThat(count.get()).isEqualTo(before); assertThat(controller.choice()).isEqualTo(Theme.DARK);
        } finally { page.dispose(); subscription.close(); }
        assertThat(page.preview.sample.closed()).isTrue();
    }
    private static void check(ThemePreview preview, String name, Color expected) {
        var root = preview.component(); root.setSize(700, 380); layout(root);
        var found = find(root, name); assertThat(found).isNotNull();
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); root.paint(g); g.dispose();
        var point = SwingUtilities.convertPoint(found, found.getWidth()-8, found.getHeight()-8, root);
        assertThat(new Color(image.getRGB(point.x, point.y))).as(name).isEqualTo(expected);
    }
    private static void layout(Container c) { c.doLayout(); for (var child:c.getComponents()) if (child instanceof Container nested) layout(nested); }
    private static Component find(Container c,String name) {
        if(name.equals(c.getName())) return c;
        for(var child:c.getComponents()) { if(name.equals(child.getName())) return child; if(child instanceof Container nested) { var match=find(nested,name); if(match!=null)return match; } }
        return null;
    }
}
