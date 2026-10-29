package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinuxTrayMenuTest {
    @Test void bothBackendsUseStableMenuActionsAndIgnoreUnknownIds() {
        var events = new java.util.ArrayList<String>();
        var actions = new ResidentTray.Actions(() -> events.add("open"), () -> events.add("new"),
            () -> events.add("settings"), () -> events.add("quit"));
        assertThat(LinuxTrayMenu.rows()).extracting(LinuxTrayMenu.Row::id).containsExactly(1, 2, 3, 4, 5);
        for (int id : new int[]{0, 1, 2, 3, 4, 5, 99}) LinuxTrayMenu.invoke(id, actions);
        assertThat(events).containsExactly("open", "new", "settings", "quit");
    }

    @Test void pixmapsUseNetworkOrderArgbIncludingTransparency() {
        var image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80123456);
        image.setRGB(1, 0, 0x00000000);
        assertThat(LinuxTrayMenu.argb(image)).containsExactly((byte) 0x80, (byte) 0x12, (byte) 0x34,
            (byte) 0x56, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }
}
