package dev.jasper.app.themesui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded read of the notices shipped with Jasper, never a network source. */
final class ThemeNotices {
    private ThemeNotices() {}
    static String read() throws IOException {
        try (var in = ThemeNotices.class.getResourceAsStream("/dev/jasper/app/themes/THIRD-PARTY-THEMES.md")) {
            if (in == null) throw new IOException("Packaged theme notices are unavailable");
            byte[] bytes = in.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Theme notices exceed the size limit");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }
}
