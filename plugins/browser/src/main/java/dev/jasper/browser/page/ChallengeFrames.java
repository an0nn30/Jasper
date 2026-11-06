package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Origins;
import java.util.Locale;
import java.util.Optional;

/**
 * The frames Jasper's page scripts stay out of (spec section 5): the challenge providers' frames, which tell a person
 * from a script and may take any script of a page's for an automation's. They are known by their address: Cloudflare
 * Turnstile's {@code challenges.cloudflare.com}; hCaptcha's {@code hcaptcha.com} and its subdomains; reCAPTCHA's paths
 * under {@code /recaptcha/} on {@code www.google.com} and {@code www.recaptcha.net}; and the subdomains of Arkose Labs'
 * {@code arkoselabs.com} and {@code funcaptcha.com}. The host is the one Chromium reads from the address
 * ({@link Origins#of}), in any case and with a trailing dot or not. The {@code links} package's privacy router refuses
 * a challenge frame's report of sound, which only a page's own script can send there. Pure; any thread.
 */
public final class ChallengeFrames {
    private ChallengeFrames() { }

    /**
     * Whether {@code address} is a challenge frame's.
     *
     * @param address a frame's address, as Chromium names it
     * @return true for an http or https address of one of the providers; false for any other, and for none
     */
    public static boolean is(String address) {
        Optional<String> origin = Origins.of(address);
        if (origin.isEmpty() || !origin.get().startsWith("http")) return false;
        String host = host(origin.get());
        if (host.equals("challenges.cloudflare.com") || host.equals("hcaptcha.com") || host.endsWith(".hcaptcha.com")
                || host.endsWith(".arkoselabs.com") || host.endsWith(".funcaptcha.com"))
            return true;
        return (host.equals("www.google.com") || host.equals("www.recaptcha.net")) && path(address).startsWith("/recaptcha/");
    }

    /** The host of {@code origin}, an http or https one as {@link Origins#of} writes it, without a trailing dot. */
    private static String host(String origin) {
        String authority = origin.substring(origin.indexOf("://") + 3);
        int port = authority.startsWith("[") ? authority.indexOf(']') + 1 : authority.lastIndexOf(':');
        String host = port > 0 && port < authority.length() ? authority.substring(0, port) : authority;
        return (host.endsWith(".") ? host.substring(0, host.length() - 1) : host).toLowerCase(Locale.ROOT);
    }

    /**
     * The path of {@code address}, an http or https one: from where its host and port end, as {@link Origins#of} ends
     * them, to its query or fragment; empty for none.
     */
    private static String path(String address) {
        int start = address.indexOf("://") + 3, end = start;
        while (end < address.length() && "/?#\\".indexOf(address.charAt(end)) < 0) end++;
        int stop = end;
        while (stop < address.length() && "?#".indexOf(address.charAt(stop)) < 0) stop++;
        return address.substring(end, stop).replace('\\', '/');
    }
}
