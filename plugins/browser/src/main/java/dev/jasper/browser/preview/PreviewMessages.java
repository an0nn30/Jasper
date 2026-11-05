package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.json.StrictJson;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a preview's page tells Jasper through the preview client's router, read strictly: one flat JSON object of at most
 * {@value #LIMIT} characters, read with {@link StrictJson#object(String, int)}, whose {@code op} is one of
 * {@link #OPS} and whose other members are exactly that op's. Numbers come as strings, as {@code preview.js} writes
 * them, and every value is a string or a boolean, but for {@code find.js}'s answers ({@code find}), whose ids and
 * counts are integers, as the browser's find in page has them (plan 9's N-F13):
 *
 * <ul>
 *   <li>{@code {"op":"ready"}}: the page has loaded;</li>
 *   <li>{@code {"op":"shown","rev":"<n>"}}: the document of revision {@code n} is in the page and laid out;</li>
 *   <li>{@code {"op":"geometry","rev":"<n>","tops":"<y0>,<y1>,…","height":"<h>","viewport":"<v>"}}: where that
 *   document's blocks are, {@code NaN} for a block with no box, and an empty {@code tops} for no blocks;</li>
 *   <li>{@code {"op":"scrolled","y":"<y>","user":true|false}};</li>
 *   <li>{@code {"op":"link","id":"<n>"}}: the user activated a link;</li>
 *   <li>{@code {"op":"find","search":<n>,"count":<c>,"invalid":true|false}}: a Find request's matches, and
 *   {@code {"op":"find","search":<n>,"selection":"…"}}: the selected text a request asked for.</li>
 * </ul>
 *
 * A revision or a link id is a non-negative decimal as {@code Long.toString} writes it; a length is a finite JavaScript
 * number, as its {@code String} writes it. Anything else is no message.
 */
final class PreviewMessages {
    /** The ops a page may send. */
    static final Set<String> OPS = Set.of("ready", "shown", "geometry", "scrolled", "link", "find");
    /** The most characters a request may hold: 4 MiB. */
    static final int LIMIT = 4 << 20;
    /** The most matches {@code find.js} counts, and the longest selection it answers. */
    private static final int MAX_MATCHES = 10_000, MAX_SELECTION = 1_000;
    // A finite number as JavaScript's String writes one: an optional sign, digits, a fraction, an exponent.
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private PreviewMessages() { }

    /** A message from the page. */
    sealed interface Message { }

    /** The page has loaded. */
    record Ready() implements Message { }

    /**
     * A document is in the page and laid out.
     *
     * @param revision its revision
     */
    record Shown(long revision) implements Message { }

    /**
     * Where a document's blocks are laid out.
     *
     * @param geometry the layout
     */
    record Laid(PreviewEvent.Geometry geometry) implements Message { }

    /**
     * The page scrolled.
     *
     * @param y the top of the view
     * @param user whether the user scrolled it
     */
    record Scrolled(double y, boolean user) implements Message { }

    /**
     * The user activated a link.
     *
     * @param id its {@code jasper-link:} id
     */
    record Link(int id) implements Message { }

    /**
     * {@code find.js} answers a Find request.
     *
     * @param request the request's id
     * @param count its matches
     * @param invalid whether the page's {@code RegExp} refused the pattern
     */
    record Found(long request, int count, boolean invalid) implements Message { }

    /**
     * {@code find.js} answers the selection a request asked for.
     *
     * @param request its id
     * @param text the selected text
     */
    record Selected(long request, String text) implements Message { }

    /**
     * The message {@code request} is.
     *
     * @param request what the page sent
     * @return the message; empty for anything else, never an exception
     */
    static Optional<Message> read(String request) {
        if (request == null || request.length() > LIMIT) return Optional.empty();
        Optional<Map<String, Object>> members = StrictJson.object(request, 1).filter(read -> read.values().stream()
            .allMatch(value -> value instanceof String || value instanceof Boolean || value instanceof Long));
        if (members.isEmpty() || !(members.get().get("op") instanceof String op) || !OPS.contains(op)) return Optional.empty();
        Map<String, Object> message = members.get();
        try {
            return Optional.ofNullable(switch (op) {
                case "ready" -> message.size() == 1 ? new Ready() : null;
                case "shown" -> only(message, "rev") ? new Shown(revision(message.get("rev"))) : null;
                case "geometry" -> only(message, "rev", "tops", "height", "viewport") ? geometry(message) : null;
                case "scrolled" -> only(message, "y", "user") && message.get("user") instanceof Boolean user
                    ? new Scrolled(number(message.get("y")), user) : null;
                case "link" -> only(message, "id") ? new Link(Math.toIntExact(revision(message.get("id")))) : null;
                case "find" -> found(message);
                default -> null;
            });
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            return Optional.empty();
        }
    }

    private static Laid geometry(Map<String, Object> message) {
        long revision = revision(message.get("rev"));
        if (!(message.get("tops") instanceof String written)) throw new IllegalArgumentException("tops");
        String[] entries = written.isEmpty() ? new String[0] : written.split(",", -1);
        var tops = new double[entries.length];
        // A block with no box is NaN, so that one such block is not an empty list, which is none.
        for (int at = 0; at < entries.length; at++) tops[at] = entries[at].equals("NaN") ? Double.NaN : number(entries[at]);
        double height = length(message.get("height")), viewport = length(message.get("viewport"));
        return new Laid(new PreviewEvent.Geometry(revision, tops, height, viewport));
    }

    private static Message found(Map<String, Object> message) {
        if (!(message.get("search") instanceof Long request) || request <= 0) return null;
        if (only(message, "search", "count", "invalid") && message.get("count") instanceof Long count && count >= 0
                && count <= MAX_MATCHES && message.get("invalid") instanceof Boolean invalid)
            return new Found(request, count.intValue(), invalid);
        if (only(message, "search", "selection") && message.get("selection") instanceof String text && text.length() <= MAX_SELECTION)
            return new Selected(request, text);
        return null;
    }

    /** Whether the message has exactly {@code op} and these members. */
    private static boolean only(Map<String, Object> message, String... names) {
        if (message.size() != names.length + 1) return false;
        for (String name : names) if (!message.containsKey(name)) return false;
        return true;
    }

    /** A non-negative decimal as {@code Long.toString} writes it. */
    private static long revision(Object value) {
        if (!(value instanceof String text) || text.isEmpty() || text.length() > 19 || text.length() > 1 && text.charAt(0) == '0')
            throw new IllegalArgumentException("not a revision");
        for (int at = 0; at < text.length(); at++) if (text.charAt(at) < '0' || text.charAt(at) > '9') throw new IllegalArgumentException("not a revision");
        return Long.parseLong(text); // a 19-digit text past Long.MAX_VALUE throws NumberFormatException, an IllegalArgumentException
    }

    /** A finite number as JavaScript's {@code String} writes one. */
    private static double number(Object value) {
        if (!(value instanceof String text) || !NUMBER.matcher(text).matches()) throw new IllegalArgumentException("not a number");
        double number = Double.parseDouble(text);
        if (!Double.isFinite(number)) throw new IllegalArgumentException("not finite");
        return number;
    }

    /** A number, not negative. */
    private static double length(Object value) {
        double length = number(value);
        if (length < 0) throw new IllegalArgumentException("negative");
        return length;
    }
}
