package dev.jasper.browser.tools;

import dev.jasper.browser.downloads.DownloadNames;
import dev.jasper.browser.tools.SavePayload.Kind;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The names Save Page As gives the files of one save (plan 7's Task 7, ruling F13), and the folder it suggests. A
 * resource is named after its address's last path segment, percent-decoded, with each character outside
 * {@code [A-Za-z0-9._-]} made {@code _} and its leading and trailing dots trimmed, or {@code resource} when nothing is
 * left; a style or script without {@code .css} or {@code .js} gets it, and anything else keeps its extension only when
 * it is a web resource's ({@code png}, {@code css}, {@code woff2} and the like), getting {@code .bin} appended
 * otherwise, so no saved file runs when opened; then the name is cut to {@value #LONGEST} characters, its extension
 * kept, a Windows device name such as {@code CON} gets a leading {@code _} as {@link DownloadNames#safeName} gives one,
 * and the name is made unique in the save, case ignored as APFS and NTFS ignore it: {@code name.ext}, then
 * {@code name-1.ext} and on. Not thread-safe: one save's names are given on one thread.
 */
public final class ResourceNames {
    /** The longest name, its extension included. */
    static final int LONGEST = 80;
    /** The name of a resource whose address leaves nothing, and of the folder of a page with no host. */
    static final String RESOURCE = "resource", PAGE = "page";
    /** The extensions a resource that is no style or script keeps (F13): a web page's images, fonts, media and data. */
    private static final Set<String> WEB = Set.of("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "css", "js", "mjs",
        "json", "woff", "woff2", "ttf", "otf", "eot", "mp4", "webm", "ogg", "ogv", "mp3", "wav", "m4a", "vtt", "txt", "xml");
    // The names given so far, in lower case.
    private final Set<String> taken = new HashSet<>();

    /** The names of one save, none given yet. */
    public ResourceNames() { }

    /**
     * The name of the resource at {@code url} in this save: free of every name given before, case ignored.
     *
     * @param url its address
     * @param kind what it is: a stylesheet's name ends in {@code .css}, a script's in {@code .js}
     * @return the name
     */
    public String name(URI url, Kind kind) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(kind, "kind");
        String path = url.getRawPath() == null ? "" : url.getRawPath();
        String name = safe(decoded(path.substring(path.lastIndexOf('/') + 1)));
        if (name.isEmpty()) name = RESOURCE;
        String extension = extension(name).toLowerCase(Locale.ROOT);
        name = switch (kind) {
            case STYLE -> extension.equals("css") ? name : name + ".css";
            case SCRIPT -> extension.equals("js") ? name : name + ".js";
            case OTHER -> WEB.contains(extension) ? name : name + ".bin";
        };
        return unique(DownloadNames.safeName(cut(name)));
    }

    /**
     * The folder Save Page As suggests for {@code page}: its host by the rule names follow, an IPv6 address without its
     * brackets and never the user name or password before it, or {@code page} for an address with none.
     *
     * @param page the page's address
     * @return the folder's name
     */
    public static String folderFor(URI page) {
        Objects.requireNonNull(page, "page");
        String host = page.getHost();
        String authority = page.getRawAuthority();
        if (host == null && authority != null && !page.isOpaque()) {
            host = authority.substring(authority.lastIndexOf('@') + 1);
            int colon = host.lastIndexOf(':');
            if (colon >= 0 && host.indexOf(']') < colon) host = host.substring(0, colon);
            host = decoded(host);
        }
        if (host == null) return PAGE;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        String name = safe(host);
        return name.isEmpty() ? PAGE : DownloadNames.safeName(cut(name));
    }

    /** {@code text} with each character outside {@code [A-Za-z0-9._-]} made {@code _}, and no leading or trailing dot. */
    private static String safe(String text) {
        var kept = new StringBuilder(text.length());
        text.codePoints().forEach(c -> kept.append(c < 0x80 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') ? (char) c : '_'));
        int start = 0, end = kept.length();
        while (start < end && kept.charAt(start) == '.') start++;
        while (end > start && kept.charAt(end - 1) == '.') end--;
        return kept.substring(start, end);
    }

    /** {@code text} with each {@code %XX} decoded as UTF-8; a {@code %} that starts no escape stays as written. */
    private static String decoded(String text) {
        if (text.indexOf('%') < 0) return text;
        var bytes = new ByteArrayOutputStream(text.length());
        for (int at = 0; at < text.length(); ) {
            int c = text.codePointAt(at);
            if (c == '%' && at + 2 < text.length() && Character.digit(text.charAt(at + 1), 16) >= 0
                    && Character.digit(text.charAt(at + 2), 16) >= 0) {
                bytes.write(Character.digit(text.charAt(at + 1), 16) << 4 | Character.digit(text.charAt(at + 2), 16));
                at += 3;
            } else {
                bytes.writeBytes(new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8));
                at += Character.charCount(c);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** {@code name}'s extension, without its dot; empty for none. */
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    /** {@code name} within {@value #LONGEST} characters, its extension kept. */
    private static String cut(String name) {
        if (name.length() <= LONGEST) return name;
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot < LONGEST ? name.substring(dot) : "";
        return name.substring(0, LONGEST - extension.length()) + extension;
    }

    /** {@code name}, or the first of {@code name-1.ext}, {@code name-2.ext} and on that no name given before takes. */
    private String unique(String name) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, extension = dot > 0 ? name.substring(dot) : "";
        String candidate = name;
        for (int n = 1; !taken.add(candidate.toLowerCase(Locale.ROOT)); n++) candidate = stem + "-" + n + extension;
        return candidate;
    }
}
