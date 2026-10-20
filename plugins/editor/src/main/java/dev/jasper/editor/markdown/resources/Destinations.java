package dev.jasper.editor.markdown.resources;

import dev.jasper.editor.api.RelativePaths;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Interprets authored destinations without borrowing a base URI from the process or a different provider. */
public final class Destinations {
    private Destinations() { }

    /**
     * @param destination the original Markdown destination
     * @return its supported kind, or an explanation when it cannot be followed
     */
    public static Destination parse(String destination) {
        if (destination == null || destination.isEmpty()) return new Destination.Unsupported("Empty destination");
        try {
            requireText(destination);
            // Classify schemes before decoding so web URLs retain their URI escapes.
            if (destination.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
                var uri = new URI(destination);
                if ((uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                        && uri.getHost() != null && uri.getRawUserInfo() == null)
                    return new Destination.Web(uri);
                return new Destination.Unsupported("Only absolute HTTP and HTTPS URLs are supported");
            }
            int hash = destination.indexOf('#');
            String rawPath = hash < 0 ? destination : destination.substring(0, hash);
            Optional<String> fragment = hash < 0 ? Optional.empty() : Optional.of(decode(destination.substring(hash + 1)));
            if (rawPath.isEmpty()) return new Destination.Fragment(fragment.orElseThrow());
            return new Destination.Relative(RelativePaths.requireValid(decode(rawPath)), fragment);
        } catch (IllegalArgumentException | URISyntaxException | CharacterCodingException invalid) {
            return new Destination.Unsupported("Invalid or unsupported destination");
        }
    }

    private static String decode(String value) throws CharacterCodingException {
        var decoded = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            if (value.charAt(offset) != '%') {
                decoded.append(value.charAt(offset++));
                continue;
            }
            var bytes = new ByteArrayOutputStream();
            while (offset < value.length() && value.charAt(offset) == '%') {
                if (offset + 2 >= value.length()) throw new IllegalArgumentException("Incomplete percent escape");
                int high = hex(value.charAt(offset + 1));
                int low = hex(value.charAt(offset + 2));
                if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid percent escape");
                bytes.write(high * 16 + low);
                offset += 3;
            }
            decoded.append(StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())));
        }
        String result = decoded.toString();
        requireText(result);
        return result;
    }

    private static int hex(char value) {
        return value <= 0x7F ? Character.digit(value, 16) : -1;
    }

    private static void requireText(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF || codePoint == '\\')
                throw new IllegalArgumentException("Invalid destination text");
            offset += Character.charCount(codePoint);
        }
    }
}
