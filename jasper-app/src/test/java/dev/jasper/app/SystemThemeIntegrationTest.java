package dev.jasper.app;

import dev.jasper.terminal.Palette;
import java.awt.Color;
import java.util.ArrayDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class SystemThemeIntegrationTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void customPaletteSurvivesSystemChangeWithoutReplacingSession() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        ThemeController[] themes = new ThemeController[1];
        WindowContent[] owner = new WindowContent[1];
        edt(() -> {
            themes[0] = new ThemeController();
            owner[0] = content(launcher(pending), themes[0]);
        });
        pending.remove().run();
        edt(() -> {
            var pane = owner[0].currentPane();
            var session = pane.session();
            var palette = new Palette(new Color(0xffeeee), new Color(0x101820),
                new Color(0xffcc00), new Color(0x334455), Palette.jasperDark().ansi());
            themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), palette);
            var delegate = owner[0].getUI();
            themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), Palette.jasperDark());
            assertThat(owner[0].getUI()).isSameAs(delegate);
            themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), palette);
            themes[0].systemChanged(BuiltinTheme.LIGHT);
            assertThat(themes[0].current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, palette));
            assertThat(pane.session()).isSameAs(session);
            assertThat(pane.view().palette()).isEqualTo(palette);
            assertThat(pane.getBackground()).isEqualTo(palette.background());
            assertThat(owner[0].toolbar().getBackground()).isEqualTo(Palette.jasperLight().background());
        });
    }
}
