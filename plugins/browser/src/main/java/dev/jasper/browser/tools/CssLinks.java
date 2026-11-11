package dev.jasper.browser.tools;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The references of CSS that Save Page As fetches and rewrites (plan 7's Task 7): those of a stylesheet it fetched, of a
 * style element and of a style attribute. A reference is a {@code url()}, quoted or bare, or the string of an
 * {@code @import}, whose {@code url()} form is a {@code url()}; CSS escapes in it are read, and a relative one resolves
 * against the base, as the stylesheet's own address or the document's base. A {@code data:} reference and a fragment
 * alone, such as an SVG gradient's {@code url(#fill)}, are left alone, and a comment, a string elsewhere and a function
 * whose name only ends in {@code url} hold none. An address is never kept with its user name or password (4a's R46).
 * Pure.
 */
public final class CssLinks {
    private CssLinks() { }

    /**
     * The addresses {@code css} refers to, in order, each resolved against {@code base}.
     *
     * @param css the CSS
     * @param base the address its relative references resolve against
     * @return the addresses, without user information; none for a reference left alone or one no address can be made of
     */
    public static List<URI> references(String css, URI base) {
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(base, "base");
        var found = new ArrayList<URI>();
        for (Reference reference : scan(css)) resolved(reference.value(), base).ifPresent(found::add);
        return found;
    }

    /**
     * {@code css} with each reference rewritten: to the name {@code local} gives its address, its fragment kept, or else
     * to its absolute address; either is written as a double-quoted string, inside {@code url()} where it was one. A
     * reference left alone, or one no address can be made of, stays as it was written.
     *
     * @param css the CSS
     * @param base the address its relative references resolve against
     * @param local the local name of an address, told without its fragment; empty for none
     * @return the rewritten CSS
     */
    public static String rewrite(String css, URI base, Function<URI, Optional<String>> local) {
        Objects.requireNonNull(css, "css");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(local, "local");
        var out = new StringBuilder(css.length() + 64);
        int written = 0;
        for (Reference reference : scan(css)) {
            Optional<URI> address = resolved(reference.value(), base);
            if (address.isEmpty()) continue;
            URI absolute = address.get();
            String fragment = absolute.getRawFragment() == null ? "" : "#" + absolute.getRawFragment();
            String target = local.apply(SaveAddresses.withoutFragment(absolute)).map(name -> name + fragment).orElseGet(absolute::toASCIIString);
            out.append(css, written, reference.start()).append(reference.url() ? "url(" + quoted(target) + ")" : quoted(target));
            written = reference.end();
        }
        return out.append(css, written, css.length()).toString();
    }

    /** A reference: the span it takes, its value with CSS escapes read, and whether it is a {@code url()}. */
    private record Reference(int start, int end, String value, boolean url) { }

    /** The address {@code value} names against {@code base}; empty for one left alone and one no address is made of. */
    private static Optional<URI> resolved(String value, URI base) {
        String text = value.strip();
        if (text.isEmpty() || text.startsWith("#") || text.regionMatches(true, 0, "data:", 0, 5)) return Optional.empty();
        try {
            return SaveAddresses.parse(text).map(base::resolve).map(SaveAddresses::withoutUserInfo);
        } catch (IllegalArgumentException unresolvable) {
            return Optional.empty();
        }
    }

    /** The references of {@code css}, in order, outside comments and other strings. */
    private static List<Reference> scan(String css) {
        var found = new ArrayList<Reference>();
        int n = css.length();
        for (int at = 0; at < n; ) {
            char c = css.charAt(at);
            if (c == '/' && at + 1 < n && css.charAt(at + 1) == '*') {
                int close = css.indexOf("*/", at + 2);
                at = close < 0 ? n : close + 2;
            } else if (c == '"' || c == '\'') {
                at = stringEnd(css, at);
            } else if (c == '\\') {
                at += 2;
            } else if ((c == 'u' || c == 'U') && css.regionMatches(true, at, "url(", 0, 4) && !identifierBefore(css, at)) {
                at = url(css, at, found);
            } else if (c == '@' && css.regionMatches(true, at, "@import", 0, 7) && !identifier(css, at + 7)) {
                int next = skipBlank(css, at + 7);
                if (next < n && (css.charAt(next) == '"' || css.charAt(next) == '\'')) {
                    int end = stringEnd(css, next);
                    if (end > next + 1 && css.charAt(end - 1) == css.charAt(next) && !escapedQuote(css, end - 1))
                        found.add(new Reference(next, end, unescaped(css.substring(next + 1, end - 1)), false));
                    at = end;
                } else {
                    at = next;
                }
            } else {
                at++;
            }
        }
        return found;
    }

    /** Reads the {@code url(} at {@code start}: its reference, if it is a whole one; answers where scanning goes on. */
    private static int url(String css, int start, List<Reference> found) {
        int n = css.length(), at = skipWhite(css, start + 4);
        if (at >= n) return n;
        char c = css.charAt(at);
        if (c == '"' || c == '\'') {
            int end = stringEnd(css, at);
            boolean closed = end > at + 1 && css.charAt(end - 1) == c && !escapedQuote(css, end - 1);
            int close = skipWhite(css, end);
            if (closed && close < n && css.charAt(close) == ')') {
                found.add(new Reference(start, close + 1, unescaped(css.substring(at + 1, end - 1)), true));
                return close + 1;
            }
            return end;
        }
        // A bare address: up to its ')', past white space only at its end; a quote, a '(' or a control makes it a bad url.
        int end = at;
        boolean bad = false;
        while (end < n && css.charAt(end) != ')') {
            char d = css.charAt(end);
            if (d == '\\') {
                end = escapeEnd(css, end);
                continue;
            }
            if (Character.isWhitespace(d)) {
                int after = skipWhite(css, end);
                if (after < n && css.charAt(after) != ')') bad = true;
                end = after;
                continue;
            }
            if (d == '"' || d == '\'' || d == '(' || d < 0x20 || d == 0x7F) bad = true;
            end++;
        }
        if (end >= n) return n;
        if (!bad) found.add(new Reference(start, end + 1, unescaped(css.substring(at, end).strip()), true));
        return end + 1;
    }

    /** Where the escape at {@code at}, a backslash, ends: past up to six hex digits and one white space after them, or one character. */
    private static int escapeEnd(String css, int at) {
        int n = css.length(), digits = 0;
        while (digits < 6 && at + 1 + digits < n && Character.digit(css.charAt(at + 1 + digits), 16) >= 0) digits++;
        if (digits == 0) return Math.min(n, at + 2);
        int end = at + 1 + digits;
        return end < n && Character.isWhitespace(css.charAt(end)) ? end + 1 : end;
    }

    /** Where the string starting with the quote at {@code start} ends: past its closing quote, or at its line's end. */
    private static int stringEnd(String css, int start) {
        char quote = css.charAt(start);
        int at = start + 1, n = css.length();
        while (at < n) {
            char c = css.charAt(at);
            if (c == '\\') at += 2;
            else if (c == quote) return at + 1;
            else if (c == '\n' || c == '\r' || c == '\f') return at;
            else at++;
        }
        return n;
    }

    /** Whether the quote at {@code at} is escaped: an odd run of backslashes before it. */
    private static boolean escapedQuote(String css, int at) {
        int slashes = 0;
        for (int before = at - 1; before >= 0 && css.charAt(before) == '\\'; before--) slashes++;
        return slashes % 2 == 1;
    }

    /** {@code text} with CSS's escapes read: up to six hex digits and one white space after, a line break dropped, else the character. */
    private static String unescaped(String text) {
        if (text.indexOf('\\') < 0) return text;
        var out = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c != '\\' || at + 1 >= text.length()) {
                if (c != '\\') out.append(c);
                continue;
            }
            int hex = at + 1, digits = 0;
            while (digits < 6 && hex + digits < text.length() && Character.digit(text.charAt(hex + digits), 16) >= 0) digits++;
            if (digits > 0) {
                int code = Integer.parseInt(text, hex, hex + digits, 16);
                out.appendCodePoint(code == 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF) ? 0xFFFD : code);
                at = hex + digits - 1;
                if (at + 1 < text.length() && Character.isWhitespace(text.charAt(at + 1))) at++;
            } else {
                char next = text.charAt(at + 1);
                if (next != '\n' && next != '\r' && next != '\f') out.append(next);
                at++;
            }
        }
        return out.toString();
    }

    private static int skipWhite(String css, int at) {
        while (at < css.length() && Character.isWhitespace(css.charAt(at))) at++;
        return at;
    }

    /** Past white space and comments, as between {@code @import} and its string. */
    private static int skipBlank(String css, int at) {
        while (true) {
            at = skipWhite(css, at);
            if (at + 1 < css.length() && css.charAt(at) == '/' && css.charAt(at + 1) == '*') {
                int close = css.indexOf("*/", at + 2);
                if (close < 0) return css.length();
                at = close + 2;
            } else {
                return at;
            }
        }
    }

    /** Whether the character before {@code at} continues an identifier, so the {@code url(} there is another function. */
    private static boolean identifierBefore(String css, int at) {
        return at > 0 && identifier(css, at - 1);
    }

    private static boolean identifier(String css, int at) {
        if (at < 0 || at >= css.length()) return false;
        char c = css.charAt(at);
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '\\' || c >= 0x80;
    }

    /** {@code text} as a double-quoted CSS string: its quotes and backslashes escaped, and a line break as {@code \a }. */
    private static String quoted(String text) {
        var out = new StringBuilder(text.length() + 2).append('"');
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\a ");
            else if (c == '\r') out.append("\\d ");
            else if (c == '\f') out.append("\\c ");
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
