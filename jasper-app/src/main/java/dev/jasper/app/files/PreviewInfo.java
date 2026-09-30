package dev.jasper.app.files;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What the preview pane shows.
 *
 * @param text the start of a text file, with carriage returns dropped and control characters replaced
 * @param binary the start of the file contains a NUL byte
 * @param items a folder's item count; 0 for files
 */
public record PreviewInfo(Optional<String> text, boolean binary, int items) {
    public static PreviewInfo folder(int items) { return new PreviewInfo(Optional.empty(), false, items); }

    /** The start of a file: binary when it contains a NUL byte, else UTF-8 text. */
    public static PreviewInfo file(byte[] head) {
        for (byte b : head) if (b == 0) return new PreviewInfo(Optional.empty(), true, 0);
        return new PreviewInfo(Optional.of(printable(new String(head, StandardCharsets.UTF_8))), false, 0);
    }

    /** Keeps line breaks and tabs, drops carriage returns, and replaces other control characters with U+FFFD. */
    static String printable(String text) {
        var out = new StringBuilder(text.length());
        text.chars().forEach(c -> {
            if (c == '\r') return;
            if (c == '\n' || c == '\t') out.append((char) c);
            else if (c < 0x20 || c == 0x7f) out.append('\uFFFD');
            else out.append((char) c);
        });
        return out.toString();
    }
}
