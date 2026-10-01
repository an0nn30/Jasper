package dev.jasper.fileserver.engine.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Paths, links and text as HTTP needs them. */
final class HttpPaths {
    private HttpPaths() { }

    /** Percent-decodes a raw request path as UTF-8; a plus stays a plus. */
    static String decode(String raw) {
        var bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length()) throw new IllegalArgumentException("A bad escape in the path");
                int high = Character.digit(raw.charAt(i + 1), 16), low = Character.digit(raw.charAt(i + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("A bad escape in the path");
                bytes.write(high * 16 + low);
                i += 2;
            } else if (c < 0x80) {
                bytes.write(c);
            } else {
                throw new IllegalArgumentException("The path is not encoded");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException notUtf8) {
            throw new IllegalArgumentException("The path is not UTF-8");
        }
    }

    /** One path segment with everything but unreserved characters percent-encoded. */
    static String segment(String name) {
        var out = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '.' || c == '_' || c == '~')
                out.append((char) c);
            else out.append('%').append(String.format(Locale.ROOT, "%02X", c));
        }
        return out.toString();
    }

    /** An absolute link to a share path; folders end in a slash. */
    static String href(List<String> segments, boolean directory) {
        if (segments.isEmpty()) return "/";
        var out = new StringBuilder();
        for (String name : segments) out.append('/').append(segment(name));
        return directory ? out.append('/').toString() : out.toString();
    }

    static String html(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Always an attachment: a shared HTML file never runs as this server's page. */
    static String contentDisposition(String name) {
        var fallback = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            fallback.append(c >= 0x20 && c < 0x7f && c != '"' && c != '\\' ? c : '_');
        }
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + segment(name);
    }
}
