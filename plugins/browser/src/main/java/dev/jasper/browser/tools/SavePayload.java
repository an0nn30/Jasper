package dev.jasper.browser.tools;

import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Save Page As's answer from {@code save-page.js} (plan 7's Task 7, F27): the page's snapshot as JSON, in parts the
 * script sends one at a time through the opener bridge as {@code savePage} requests,
 * {@code {"op": "savePage", "save": prefix, "part": n, "parts": count, "text": "…"}}. The parts are taken in order under
 * the save's prefix, at most {@value #MOST_PARTS}, each of at most {@value #PART_LIMIT} characters, and joined, then read
 * with {@link StrictJson} as one object three deep ({@link Snapshot}). Any script of the page can send a request, so a
 * part under another prefix, or none, is ignored, as another save's or a forgery is; anything else that is not the
 * save's answer fails the save with a reason it shows. Not thread-safe: the parts are taken on the UI thread, and the
 * snapshot may then be read on another.
 */
public final class SavePayload {
    /** The most parts a page's answer may come in (F27). */
    static final int MOST_PARTS = 64;
    /** The most characters one part's text holds: the opener bridge's 1 MiB message. */
    static final int PART_LIMIT = 1 << 20;
    /** Why the save fails for a page past {@value #MOST_PARTS} parts. */
    static final String TOO_LARGE = "the page is too large to save";
    /** Why the save fails for an answer that is not one. */
    static final String UNREADABLE = "the page's answer could not be read";
    /** A save's prefix: {@code jasper-save-}, 16 lower-case hex digits and {@code -}. */
    static final Pattern PREFIX = Pattern.compile("jasper-save-[0-9a-f]{16}-");
    private static final String OP = "savePage";
    private static final Set<String> REQUEST = Set.of("op", "save", "part", "parts", "text"),
        SNAPSHOT = Set.of("prefix", "html", "resources", "styles", "attributes", "base"), RESOURCE = Set.of("url", "kind");
    private final String prefix;
    private final List<String> parts = new ArrayList<>();
    private int expected;
    private boolean complete;

    /**
     * The answer of the save whose prefix is {@code prefix}, no part taken yet.
     *
     * @param prefix the prefix the save gave {@code save-page.js}: {@code jasper-save-}, 16 hex digits and {@code -}
     * @throws IllegalArgumentException for any other
     */
    public SavePayload(String prefix) {
        if (prefix == null || !PREFIX.matcher(prefix).matches()) throw new IllegalArgumentException("Not a save's prefix: " + prefix);
        this.prefix = prefix;
    }

    /**
     * Takes a request of the page: a part of the answer, and the snapshot once it is the last.
     *
     * @param json the request, as the page sent it
     * @return the snapshot after the last part; empty while more are to come, for a request under another prefix, and
     *     after the snapshot was given
     * @throws IOException with the reason the save shows: "the page is too large to save" past 64 parts, and "the page's
     *     answer could not be read" for a request or a snapshot that is not the save's answer
     */
    public Optional<Snapshot> add(String json) throws IOException {
        return take(json) ? Optional.of(snapshot()) : Optional.empty();
    }

    /**
     * Takes a request of the page, as {@link #add} does, without reading the snapshot.
     *
     * @param json the request
     * @return whether it was the last part, after which {@link #snapshot} reads the answer
     * @throws IOException as {@link #add} throws
     */
    boolean take(String json) throws IOException {
        if (complete) return false;
        Map<String, Object> request = StrictJson.object(json, 1).orElseThrow(SavePayload::unreadable);
        if (!prefix.equals(request.get("save"))) return false;
        if (!request.keySet().equals(REQUEST) || !OP.equals(request.get("op")) || !(request.get("part") instanceof Long part)
            || !(request.get("parts") instanceof Long count) || !(request.get("text") instanceof String text)) throw unreadable();
        if (count > MOST_PARTS) throw new IOException(TOO_LARGE);
        if (count < 1 || expected != 0 && count != expected || part != parts.size() || text.length() > PART_LIMIT) throw unreadable();
        expected = count.intValue();
        parts.add(text);
        complete = parts.size() == expected;
        return complete;
    }

    /**
     * The snapshot the parts make, once the last is in; the parts are let go.
     *
     * @return the snapshot
     * @throws IOException with "the page's answer could not be read" for an answer that is no snapshot of this save
     * @throws IllegalStateException before the last part
     */
    Snapshot snapshot() throws IOException {
        if (!complete) throw new IllegalStateException("The answer is not complete");
        String whole = String.join("", parts);
        parts.clear();
        Map<String, Object> answer = StrictJson.object(whole, 3).orElseThrow(SavePayload::unreadable);
        if (!answer.keySet().equals(SNAPSHOT) || !prefix.equals(answer.get("prefix")) || !(answer.get("html") instanceof String html)
            || !(answer.get("resources") instanceof List<?> listed) || !(answer.get("base") instanceof String base)) throw unreadable();
        var resources = new ArrayList<Resource>(listed.size());
        for (Object each : listed) resources.add(resource(each));
        return new Snapshot(prefix, html, resources, strings(answer.get("styles")), strings(answer.get("attributes")), address(base));
    }

    /** What a snapshot names a resource as: {@code {"url": address, "kind": "style" | "script" | "other"}}. */
    private static Resource resource(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> members) || !members.keySet().equals(RESOURCE) || !(members.get("url") instanceof String url)
            || !(members.get("kind") instanceof String kind)) throw unreadable();
        Kind read = switch (kind) {
            case "style" -> Kind.STYLE;
            case "script" -> Kind.SCRIPT;
            case "other" -> Kind.OTHER;
            default -> throw unreadable();
        };
        return new Resource(address(url), read);
    }

    /** An absolute address, as a URI takes it and without user information. */
    private static URI address(String text) throws IOException {
        URI address = SaveAddresses.parse(text).orElseThrow(SavePayload::unreadable);
        if (!address.isAbsolute()) throw unreadable();
        return address;
    }

    private static List<String> strings(Object value) throws IOException {
        if (!(value instanceof List<?> values)) throw unreadable();
        var texts = new ArrayList<String>(values.size());
        for (Object each : values) {
            if (!(each instanceof String text)) throw unreadable();
            texts.add(text);
        }
        return texts;
    }

    private static IOException unreadable() {
        return new IOException(UNREADABLE);
    }

    /** What a resource is to the save: a stylesheet, whose references are saved too, a script, or anything else. */
    public enum Kind {
        /** A stylesheet: {@code link rel=stylesheet}. */
        STYLE,
        /** A script: {@code script src}. */
        SCRIPT,
        /** Anything else: an image, a font, a medium, an icon. */
        OTHER
    }

    /**
     * A resource the page refers to.
     *
     * @param url its absolute address, its fragment kept, without user information
     * @param kind what it is
     */
    public record Resource(URI url, Kind kind) {
        /** Rejects nulls. */
        public Resource {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * The page as {@code save-page.js} took it: a copy of its document with its references made tokens of the save's
     * prefix, {@code <prefix>r<N>-} for the Nth resource, {@code <prefix>s<K>-} for the Kth style element's text and
     * {@code <prefix>a<K>-} for the Kth style attribute's.
     *
     * @param prefix the save's prefix, which its tokens start with
     * @param html the copy's HTML, its doctype first
     * @param resources the resources, by the number their tokens carry
     * @param styles the style elements' texts
     * @param attributes the style attributes' texts
     * @param base the document's base address, which the styles' relative references resolve against
     */
    public record Snapshot(String prefix, String html, List<Resource> resources, List<String> styles, List<String> attributes, URI base) {
        /** Copies the lists; rejects nulls. */
        public Snapshot {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(html, "html");
            resources = List.copyOf(resources);
            styles = List.copyOf(styles);
            attributes = List.copyOf(attributes);
            Objects.requireNonNull(base, "base");
        }
    }
}
