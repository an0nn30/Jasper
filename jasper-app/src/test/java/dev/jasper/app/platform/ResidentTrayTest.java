package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentTrayTest {

    @Test void macOsAndWindowsDesktopsGetAnIconAndLinuxDoesNot() {
        assertThat(ResidentTray.applies("Windows 11", false)).isTrue();
        assertThat(ResidentTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Mac OS X", false)).as("the menu bar item").isTrue();
        assertThat(ResidentTray.applies("Mac OS X", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Linux", false)).isFalse();
        assertThat(ResidentTray.applies(null, false)).isFalse();
    }

    @Test void theMenuBarItemOffersANewWindowSettingsAndTheRealQuit() {
        assertThat(ResidentTray.entries(true)).extracting(entry -> entry.label)
            .containsExactly("New Window", "Settings…", null, "Quit Jasper Completely");
        assertThat(ResidentTray.entries(false)).extracting(entry -> entry.label)
            .as("the Windows tray menu is unchanged").containsExactly("Open Jasper", null, "Quit Jasper");
    }

    @Test void eachEntryRunsItsAction() {
        List<String> ran = new ArrayList<>();
        var actions = new ResidentTray.Actions(() -> ran.add("open"), () -> ran.add("new window"),
            () -> ran.add("settings"), () -> ran.add("quit"));
        for (ResidentTray.Entry entry : ResidentTray.Entry.values()) entry.action(actions).run();
        assertThat(ran).containsExactly("open", "new window", "settings", "quit", "quit");
    }

    @Test void theMenuBarGlyphIsAShapeAtOneAndTwoTimesScale() {
        Image glyph = ResidentTray.menuBarGlyph();
        assertThat(glyph).isInstanceOf(MultiResolutionImage.class);
        List<Image> variants = ((MultiResolutionImage) glyph).getResolutionVariants();
        assertThat(variants).extracting(image -> image.getWidth(null)).containsExactly(16, 32);
        BufferedImage large = (BufferedImage) variants.get(1);
        int opaque = 0, clear = 0;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int alpha = large.getRGB(x, y) >>> 24;
            if (alpha == 0) clear++;
            else if (alpha > 200) opaque++;
        }
        assertThat(opaque).as("a template image draws with its alpha").isPositive();
        assertThat(clear).as("and lets the menu bar show through elsewhere").isGreaterThan(opaque);
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(ResidentTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(ResidentTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(ResidentTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(ResidentTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void whereNoIconAppliesInstallChangesNothingAndRemovalIsANoOp() {
        // Headless only: elsewhere this would add an icon to the real menu bar or notification area.
        org.junit.jupiter.api.Assumptions.assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        var ran = new AtomicInteger();
        Runnable remove = ResidentTray.install(new ResidentTray.Actions(ran::incrementAndGet, ran::incrementAndGet,
            ran::incrementAndGet, ran::incrementAndGet));
        remove.run();
        assertThat(ran).hasValue(0);
    }
}
