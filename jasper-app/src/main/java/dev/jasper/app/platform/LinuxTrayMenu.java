package dev.jasper.app.platform;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

final class LinuxTrayMenu {
    record Row(int id, String label, boolean separator) { }
    private static final List<Row> ROWS = List.of(new Row(1, "Open Jasper", false),
        new Row(2, "New Window", false), new Row(3, "Settings\u2026", false),
        new Row(4, "", true), new Row(5, "Quit Jasper", false));
    private LinuxTrayMenu() { }
    static List<Row> rows() { return ROWS; }
    static void invoke(int id, ResidentTray.Actions actions) {
        switch (id) {
            case 1 -> actions.open().run();
            case 2 -> actions.newWindow().run();
            case 3 -> actions.settings().run();
            case 5 -> actions.quit().run();
            default -> { }
        }
    }
    static byte[] argb(BufferedImage image) {
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(4,
            Math.multiplyExact(image.getWidth(), image.getHeight())));
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) bytes.putInt(image.getRGB(x, y));
        return bytes.array();
    }
}
