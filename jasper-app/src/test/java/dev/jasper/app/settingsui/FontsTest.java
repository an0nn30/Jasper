package dev.jasper.app.settingsui;

import java.awt.Font;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FontsTest {
    @Test void bundledFamilyAppearsInTheMonospacedPickerAndResolvesEveryStyle() {
        assertThat(Fonts.families(true)).contains("Cousine Nerd Font Mono");
        assertThat(Fonts.families(false)).contains("Cousine Nerd Font Mono");
        String[] postscript = {"CousineNFM-Regular", "CousineNFM-Bold", "CousineNFM-Italic", "CousineNFM-BoldItalic"};
        for (int style = 0; style < 4; style++) {
            var font = new Font("Cousine Nerd Font Mono", style, 12);
            assertThat(font.getFamily(Locale.ROOT)).isEqualTo("Cousine Nerd Font Mono");
            assertThat(font.getPSName()).isEqualTo(postscript[style]);
            assertThat(font.canDisplay(0xE0B0)).isTrue();
        }
    }

    @Test void preparingReadsTheFamiliesOnceInTheBackgroundAndCallersGetThatList() throws InterruptedException {
        Fonts.prepare();
        Fonts.prepare();
        var reader = Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().equals("jasper-font-families")).toList();
        assertThat(reader).hasSizeLessThanOrEqualTo(1).allMatch(Thread::isDaemon);
        for (var thread : reader) thread.join(10_000);
        assertThat(Fonts.families(false)).contains("Cousine Nerd Font Mono").isSameAs(Fonts.families(false));
        assertThat(Fonts.families(true)).contains("Cousine Nerd Font Mono");
    }
}
