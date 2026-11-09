package dev.jasper.browser.links;

import dev.jasper.browser.permissions.Origins;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What keeps one page's document apart from another's, as Chromium decides it and the opener bridge must decide it
 * too, since a page's script can ask the bridge directly: a document's origin; whether a {@code Content-Security-Policy}
 * sandbox makes that origin opaque or blocks its popups; and whether a {@code Cross-Origin-Opener-Policy} cuts a page off
 * from its opener and its popups. Plain decisions, on any thread.
 */
final class Isolation {
    private static final Pattern WEB = Pattern.compile("(?i)(https?)://(?:[^/?#]*@)?(\\[[^\\]/?#]*\\]|[^:/?#\\[\\]@]+)(?::([0-9]*))?(?:[/?#].*)?",
        Pattern.DOTALL);

    private Isolation() { }

    /**
     * The origin of a web address: its scheme, host and port, the scheme's own port left out, as a page's
     * {@code location.origin} gives it for the canonical address Chromium reports.
     *
     * @param url an address
     * @return the origin; empty for an address of another scheme, whose origin is opaque
     */
    static Optional<String> origin(String url) {
        if (url == null) return Optional.empty();
        Matcher web = WEB.matcher(url);
        if (!web.matches()) return Optional.empty();
        String scheme = web.group(1).toLowerCase(Locale.ROOT), port = web.group(3);
        boolean usual = port == null || port.isEmpty() || port.equals(scheme.equals("http") ? "80" : "443");
        return Optional.of(scheme + "://" + web.group(2).toLowerCase(Locale.ROOT) + (usual ? "" : ":" + port));
    }

    /**
     * Whether a document at {@code url} is a secure context, where Chromium honours an opener policy: an {@code https}
     * address, or an {@code http} one on this computer.
     *
     * @param url the document's address
     * @return whether it is
     */
    static boolean secure(String url) {
        return origin(url).map(Origins::secure).orElse(false);
    }

    /**
     * A document's {@code Cross-Origin-Opener-Policy}. Chromium ignores it outside a secure context, and reads anything
     * but one known value, with or without parameters, as {@link #UNSAFE_NONE}.
     */
    enum OpenerPolicy {
        /** The default: the document shares its window group with its opener and its popups. */
        UNSAFE_NONE,
        /** Kept only with same-origin documents of the same policy, but the document's own popups keep it. */
        SAME_ORIGIN_ALLOW_POPUPS,
        /** Kept only with same-origin documents of the same policy. */
        SAME_ORIGIN,
        /** Never kept with its opener, but the document's own popups keep it. */
        NOOPENER_ALLOW_POPUPS;

        /**
         * The policy a main-frame response sets.
         *
         * @param header its {@code Cross-Origin-Opener-Policy} header, or null or empty for none
         * @param url the response's address
         * @return the policy
         */
        static OpenerPolicy of(String header, String url) {
            if (header == null || !secure(url)) return UNSAFE_NONE;
            return switch (header.split(";", 2)[0].strip()) {
                case "same-origin" -> SAME_ORIGIN;
                case "same-origin-allow-popups" -> SAME_ORIGIN_ALLOW_POPUPS;
                case "noopener-allow-popups" -> NOOPENER_ALLOW_POPUPS;
                default -> UNSAFE_NONE;
            };
        }
    }

    /**
     * What a {@code Content-Security-Policy} {@code sandbox} directive leaves a top-level document: whether it is
     * sandboxed, whether it keeps its origin ({@code allow-same-origin}) and whether it may open popups
     * ({@code allow-popups}).
     *
     * @param sandboxed whether any policy sandboxes it
     * @param sameOrigin whether it keeps its origin
     * @param popups whether it may open popups
     */
    record Sandbox(boolean sandboxed, boolean sameOrigin, boolean popups) {
        /** No sandbox. */
        static final Sandbox NONE = new Sandbox(false, true, true);

        /**
         * The sandbox a response's policies set, read conservatively. Each header can hold several policies, separated
         * by commas, of directives separated by semicolons, and any {@code sandbox} directive that lacks a token takes
         * that token away.
         *
         * @param policies the response's {@code Content-Security-Policy} headers
         * @return the sandbox
         */
        static Sandbox of(Collection<String> policies) {
            boolean sandboxed = false, sameOrigin = true, popups = true;
            for (String header : policies) {
                if (header == null) continue;
                for (String policy : header.split(","))
                    for (String directive : policy.split(";")) {
                        String[] tokens = directive.strip().split("[ \t\n\f\r]+");
                        if (!tokens[0].equalsIgnoreCase("sandbox")) continue;
                        Set<String> allowed = Arrays.stream(tokens).skip(1).map(token -> token.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toSet());
                        sandboxed = true;
                        sameOrigin &= allowed.contains("allow-same-origin");
                        popups &= allowed.contains("allow-popups");
                    }
            }
            return sandboxed ? new Sandbox(true, sameOrigin, popups) : NONE;
        }

        /**
         * Whether the sandbox makes the document's origin opaque: its messages come from "null", and only messages for
         * any origin reach it.
         *
         * @return whether it does
         */
        boolean opaque() {
            return sandboxed && !sameOrigin;
        }

        /**
         * Whether the sandbox blocks the document's popups.
         *
         * @return whether it does
         */
        boolean blocksPopups() {
            return sandboxed && !popups;
        }
    }

    /**
     * Whether a navigation moves a page to a new window group, which cuts it off from its opener and its popups: the
     * HTML standard's "check if COOP values require a browsing context group switch", which Chromium follows.
     *
     * @param initialAboutBlank whether the page shows its first, empty document, as a popup does until its first load
     * @param active the opener policy of the document the page shows
     * @param activeOrigin that document's origin; empty when opaque
     * @param response the opener policy of the document that replaces it
     * @param responseOrigin that document's origin; empty when opaque
     * @return whether the page leaves its window group
     */
    static boolean severs(boolean initialAboutBlank, OpenerPolicy active, Optional<String> activeOrigin, OpenerPolicy response,
                          Optional<String> responseOrigin) {
        if (matches(active, activeOrigin, response, responseOrigin)) return false;
        return !(initialAboutBlank && (active == OpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS || active == OpenerPolicy.NOOPENER_ALLOW_POPUPS)
            && response == OpenerPolicy.UNSAFE_NONE);
    }

    /** The standard's "match opener policy values": opaque origins are never the same. */
    private static boolean matches(OpenerPolicy document, Optional<String> documentOrigin, OpenerPolicy response, Optional<String> responseOrigin) {
        if (document == OpenerPolicy.UNSAFE_NONE && response == OpenerPolicy.UNSAFE_NONE) return true;
        if (document == OpenerPolicy.UNSAFE_NONE || response == OpenerPolicy.UNSAFE_NONE) return false;
        return document == response && documentOrigin.isPresent() && documentOrigin.equals(responseOrigin);
    }
}
