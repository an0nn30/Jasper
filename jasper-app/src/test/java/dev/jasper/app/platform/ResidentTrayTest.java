package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentTrayTest {

    @Test void onlyADesktopWindowsSessionGetsATrayIcon() {
        assertThat(ResidentTray.applies("Windows 11", false)).isTrue();
        assertThat(ResidentTray.applies("Windows 11", true)).as("headless").isFalse();
        assertThat(ResidentTray.applies("Mac OS X", false)).as("macOS keeps its Dock icon").isFalse();
        assertThat(ResidentTray.applies("Linux", false)).isFalse();
        assertThat(ResidentTray.applies(null, false)).isFalse();
    }

    @Test void picksTheSmallestIconThatFillsTheSlot() {
        var images = ApplicationIcon.images("windows");
        assertThat(ResidentTray.image(images, new Dimension(16, 16)).getWidth(null)).isEqualTo(16);
        assertThat(ResidentTray.image(images, new Dimension(20, 20)).getWidth(null)).as("150% scaling").isEqualTo(20);
        assertThat(ResidentTray.image(images, new Dimension(33, 33)).getWidth(null)).isEqualTo(36);
        assertThat(ResidentTray.image(images, new Dimension(512, 512)).getWidth(null)).as("largest available").isEqualTo(256);
    }

    @Test void whereNoIconAppliesInstallChangesNothingAndRemovalIsANoOp() {
        // Headless only: elsewhere on Windows this would add an icon to the real notification area.
        org.junit.jupiter.api.Assumptions.assumeTrue(java.awt.GraphicsEnvironment.isHeadless());
        var ran = new AtomicInteger();
        Runnable remove = ResidentTray.install(ran::incrementAndGet, ran::incrementAndGet);
        remove.run();
        assertThat(ran).hasValue(0);
    }
}
