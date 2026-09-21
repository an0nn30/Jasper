package dev.jasper.app.platform;

import java.awt.Font;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SystemFontsTest {
    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    @Test void theStyleAndSizeAskedForAreTheOnesReturned() {
        Font font = SystemFonts.system(Font.BOLD, 15f);

        assertThat(font.isBold()).isTrue();
        assertThat(font.getSize2D()).isEqualTo(15f);
    }

    /**
     * Java answers with Dialog for a family it does not have, so a wrong name is silent. This is the
     * whole reason SystemFonts checks the resolved family instead of trusting the name it asked for.
     */
    @Test void sfProLooksLikeTheRightNameAndIsNot() {
        assumeTrue(mac(), "the macOS font stack");

        assertThat(new Font("SF Pro", Font.PLAIN, 13).getFamily()).isEqualToIgnoringCase(Font.DIALOG);
        assertThat(new Font("SF Pro Text", Font.PLAIN, 13).getFamily()).isEqualToIgnoringCase(Font.DIALOG);
    }

    @Test void theSystemFontResolvesOnMacAndIsNeverTheDialogFallback() {
        assumeTrue(mac(), "the macOS font stack");

        assertThat(new Font(SystemFonts.MAC_SYSTEM_FONT, Font.PLAIN, 13).getFamily())
            .isNotEqualToIgnoringCase(Font.DIALOG);
        assertThat(SystemFonts.system(Font.PLAIN, 13f).getFamily()).isNotEqualToIgnoringCase(Font.DIALOG);
    }
}
