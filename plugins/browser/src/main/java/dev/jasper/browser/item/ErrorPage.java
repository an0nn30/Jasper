package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.browser.pages.PageHtml;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * {@code jasper-about:error}, the page a browser item shows in place of a load that failed (spec section 14): a title
 * for the error, a message, the address without its user name and password, Try Again, which Enter and Space press as
 * the page opens with it focused, and "Check your internet connection and try reloading the page." A certificate error
 * adds "Jasper can’t verify the identity of the website …", why, and the subjects of the certificate chain; when Jasper
 * kept the certificate, a Details link reveals Continue to this site (unsafe), but for a revoked, invalid or malformed
 * certificate, which, as in Chrome, has no way past it.
 *
 * <p>The page is built from its address alone, on Chromium's IO thread, in the workspace theme's colours
 * ({@link PageHtml#colours}): the error's address and code, the item's token for the page, and for a certificate the
 * chain's subjects and the one-time token of Continue. Everything it shows is escaped, it runs no script, and its
 * content security policy loads nothing. Any of Jasper's pages, a local {@code file:} page or the address field can write
 * such an address, so what the page shows is only shown: its Try Again and Continue go to the {@value #TRY_AGAIN} and
 * {@value #PROCEED} actions with their tokens, and only the item whose error page it is acts on them, with the address
 * and certificate it kept itself ({@link Failure}).
 */
public final class ErrorPage {
    /** The page's name, and the names of the actions its Try Again and Continue go to. */
    public static final String NAME = "error", TRY_AGAIN = "error-retry", PROCEED = "error-continue";
    /** The title of an error without one of its own. */
    static final String GENERIC = "Page Can’t Be Loaded";
    /** What every error page says below Try Again. */
    static final String HINT = "Check your internet connection and try reloading the page.";
    /** The most characters the page shows of the address, of the chain's subjects, and of each subject. */
    static final int MAX_URL = 2_000, MAX_SUBJECTS = ServerCertificate.MAX_CHAIN, MAX_SUBJECT = 500;
    private static final String VERIFY = "Jasper can’t verify the identity of the website “%s”.";
    private static final String UNSAFE = "Someone may be impersonating “%s”. If you continue, Jasper accepts this certificate for it"
        + " until Jasper quits.";
    private static final String CONTINUE = "Continue to this site (unsafe)";
    private static final String TOKEN = "token", CODE = "code", URL = "url", PROCEED_TOKEN = "proceed", SUBJECT = "subject",
        SELF_SIGNED = "self-signed";
    // What Chromium names its errors; any other code the address carries is not shown.
    private static final Pattern CODES = Pattern.compile("ERR_[A-Z0-9_]{1,60}");
    private final Appearance appearance;
    private volatile Look look;

    /**
     * The page in the workspace theme, now and after each theme change. UI thread, while the plugin starts.
     *
     * @param appearance the plugin's appearance
     */
    public ErrorPage(Appearance appearance) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        rebuild();
        appearance.onChanged(variant -> rebuild());
    }

    /**
     * The page for {@code address}, in the theme as it was last read. Any thread.
     *
     * @param address the address Chromium asked for, its query with it
     * @return the page's HTML
     */
    public String html(URI address) {
        Look now = look;
        return html(address, now.palette(), now.dark());
    }

    /** The page for {@code address} in {@code palette}'s colours. */
    static String html(URI address, WorkspacePalette palette, boolean dark) {
        return render(Shown.of(address.toString()), palette, dark);
    }

    private void rebuild() {
        look = new Look(appearance.workspacePalette(), appearance.workspaceVariant() == Variant.DARK);
    }

    /**
     * The title for an error, by Chromium's name for it.
     *
     * @param code the error's name, such as {@code ERR_NAME_NOT_RESOLVED}
     * @return the title; empty for {@code ERR_ABORTED}, a load a download or the user stopped, which shows no page
     */
    static Optional<String> title(String code) {
        return "ERR_ABORTED".equals(code) ? Optional.empty() : Optional.of(Kind.of(code).title);
    }

    /**
     * What the page says of an error at {@code url}.
     *
     * @param code the error's name
     * @param url the address, without its user information
     * @return the message, naming the address's host, or the address when it has none
     */
    static String message(String code, String url) {
        return Kind.of(code).message.formatted(where(url));
    }

    /**
     * Why a certificate error's certificate was not trusted.
     *
     * @param code the error's name
     * @param selfSigned whether the certificate is its own issuer
     * @return the reason; empty for an error that is no certificate's
     */
    static Optional<String> reason(String code, boolean selfSigned) {
        if (!certificateError(code)) return Optional.empty();
        return Optional.of(switch (code) {
            case "ERR_CERT_AUTHORITY_INVALID" -> selfSigned ? "The certificate for this website is self-signed."
                : "The certificate for this website was issued by an authority this computer doesn’t trust.";
            case "ERR_CERT_DATE_INVALID" -> "The certificate for this website has expired or isn’t valid yet.";
            case "ERR_CERT_COMMON_NAME_INVALID" -> "The certificate for this website is for a different website.";
            case "ERR_CERT_REVOKED" -> "The certificate for this website has been revoked.";
            default -> "The certificate for this website isn’t valid.";
        });
    }

    /**
     * The token of the error page at {@code address}, which names the page to its item.
     *
     * @param address an address, as written or as Chromium writes it
     * @return the token; empty for an address that is no error page's, or whose token Jasper would not have made
     */
    static Optional<String> token(String address) {
        return AboutScheme.page(address).filter(NAME::equals).flatMap(page -> AboutScheme.parameter(address, TOKEN))
            .filter(PageHtml::isToken);
    }

    /**
     * The page showing {@code shown}, in {@code palette}'s colours.
     *
     * @param shown what the page shows
     * @param palette the workspace palette
     * @param dark whether the workspace theme is dark
     * @return the page's HTML; no transitions or animations
     */
    static String render(Shown shown, WorkspacePalette palette, boolean dark) {
        Kind kind = Kind.of(shown.code());
        String code = CODES.matcher(shown.code()).matches() ? "<p class=\"code\">" + PageHtml.escape(shown.code()) + "</p>\n" : "";
        return """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
            <meta name="color-scheme" content="%s">
            <title>%s</title>
            <style>
            %s
            html, body { margin: 0; background: var(--background); color: var(--text); }
            body { font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif; }
            main { max-width: 640px; margin: 0 auto; padding: 72px 24px; }
            h1 { margin: 0 0 12px; font-size: 28px; font-weight: 600; }
            p { margin: 0 0 12px; }
            .address, .code, ol { font: 13px/1.5 ui-monospace, Menlo, Consolas, monospace; overflow-wrap: anywhere; }
            .address, .hint, .code { color: var(--muted); }
            form { margin: 24px 0 12px; }
            button { padding: 8px 20px; border: 1px solid var(--border); border-radius: 6px; background: var(--card); color: var(--text);
                font: inherit; font-weight: 600; }
            button:hover { border-color: var(--accent); }
            button:focus-visible, summary:focus-visible, a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
            section { margin: 32px 0 12px; padding: 16px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); }
            h2 { margin: 0 0 8px; font-size: 15px; font-weight: 600; color: var(--warning); }
            ol { margin: 0 0 12px; padding-left: 24px; }
            summary { color: var(--accent); cursor: pointer; }
            details p { margin: 12px 0 0; }
            .unsafe { color: var(--error); }
            </style>
            </head>
            <body>
            <main>
            <h1>%s</h1>
            <p class="message">%s</p>
            <p class="address">%s</p>
            <form action="%s" method="get">
            <input type="hidden" name="token" value="%s">
            <button type="submit" autofocus>Try Again</button>
            </form>
            <p class="hint">%s</p>
            %s%s</main>
            </body>
            </html>
            """.formatted(dark ? "dark" : "light", PageHtml.escape(kind.title), PageHtml.colours(palette), PageHtml.escape(kind.title),
            PageHtml.escape(message(shown.code(), shown.url())), PageHtml.escape(shown.url()), AboutScheme.NAME + ":" + TRY_AGAIN,
            PageHtml.escape(shown.token()), HINT, certificate(shown), code);
    }

    /** A certificate error's part of the page: why, the chain, and Continue behind Details; nothing for another error. */
    private static String certificate(Shown shown) {
        if (!certificateError(shown.code())) return "";
        String host = where(shown.url());
        var html = new StringBuilder("<section aria-labelledby=\"certificate\">\n<h2 id=\"certificate\">")
            .append(PageHtml.escape(VERIFY.formatted(host))).append("</h2>\n");
        reason(shown.code(), shown.selfSigned()).ifPresent(reason -> html.append("<p>").append(PageHtml.escape(reason)).append("</p>\n"));
        if (!shown.subjects().isEmpty()) {
            html.append("<ol aria-label=\"Certificate chain\">\n");
            for (String subject : shown.subjects()) html.append("<li>").append(PageHtml.escape(subject)).append("</li>\n");
            html.append("</ol>\n");
        }
        shown.proceed().ifPresent(proceed -> html.append("<details>\n<summary>Details</summary>\n<p>")
            .append(PageHtml.escape(UNSAFE.formatted(host))).append("</p>\n<p><a class=\"unsafe\" href=\"")
            .append(PageHtml.escape(AboutScheme.NAME + ":" + PROCEED + "?" + TOKEN + "=" + URLEncoder.encode(proceed, StandardCharsets.UTF_8)))
            .append("\">").append(CONTINUE).append("</a></p>\n</details>\n"));
        return html.append("</section>\n").toString();
    }

    /** Whether Chromium's error is a certificate's, as {@code ERR_CERT_AUTHORITY_INVALID} is. */
    private static boolean certificateError(String code) {
        return code != null && code.startsWith("ERR_CERT");
    }

    /** The host of {@code url}, or the address itself when it has none. */
    private static String where(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (URISyntaxException notAUri) {
            return url;
        }
    }

    /**
     * A load that failed in a browser item, as the item keeps it for its error page: Java's own record, read from
     * Chromium and never from an address, so its Try Again loads only what failed in the item and its Continue allows
     * only the host and certificate Chromium reported.
     *
     * @param url the address that failed, as Chromium gave it, user information and all
     * @param code Chromium's name for the error
     * @param token the error page's token, 32 random hexadecimal digits
     * @param certificate the certificate Jasper refused for this load, or empty
     * @param proceed Continue's one-time token, while it has not been used; only a certificate error that can be gone
     *     past, with its certificate, has one
     */
    record Failure(String url, String code, String token, Optional<ServerCertificate> certificate, Optional<String> proceed) {
        /** Rejects nulls. */
        Failure {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(certificate, "certificate");
            Objects.requireNonNull(proceed, "proceed");
        }

        /**
         * The error page for {@code failure}, with new random tokens: a Continue for a certificate error Chrome lets the
         * user past ({@link ServerCertificate#overridable}), with its certificate; none for a revoked, invalid or
         * malformed certificate.
         */
        static Failure of(LoadFailure failure) {
            Optional<String> proceed = failure.certificate().isPresent() && ServerCertificate.overridable(failure.code())
                ? Optional.of(PageHtml.newToken()) : Optional.empty();
            return new Failure(failure.url(), failure.code(), PageHtml.newToken(), failure.certificate(), proceed);
        }

        /** The failure once Continue was used: it has no Continue any more. */
        Failure spent() {
            return new Failure(url, code, token, certificate, Optional.empty());
        }

        /** What the error page shows of it. */
        Shown shown() {
            return new Shown(url, code, token, certificate.map(ServerCertificate::subjects).orElse(List.of()),
                certificate.map(ServerCertificate::selfSigned).orElse(false), proceed);
        }

        /** The error page's address. */
        URI address() {
            return shown().address();
        }
    }

    /**
     * What an error page shows, as its address carries it: only shown, never acted on. The address is kept without its
     * user information and to {@link #MAX_URL} characters, the chain to its first {@link #MAX_SUBJECTS} subjects of
     * {@link #MAX_SUBJECT} characters each.
     *
     * @param url the address that failed
     * @param code Chromium's name for the error
     * @param token the page's token
     * @param subjects the certificate chain's subjects, the server's first; empty for none
     * @param selfSigned whether the certificate is its own issuer
     * @param proceed Continue's token, or empty for no Continue
     */
    record Shown(String url, String code, String token, List<String> subjects, boolean selfSigned, Optional<String> proceed) {
        /** Rejects nulls, and keeps the address and the chain within their bounds. */
        Shown {
            url = PageHtml.cut(NavigationHistory.withoutUserInfo(Objects.requireNonNull(url, "url")), MAX_URL);
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(token, "token");
            var kept = new ArrayList<String>();
            for (String subject : subjects) if (kept.size() < MAX_SUBJECTS) kept.add(PageHtml.cut(subject, MAX_SUBJECT));
            subjects = List.copyOf(kept);
            Objects.requireNonNull(proceed, "proceed");
        }

        /** What the error page at {@code address} shows: whatever its query says, as any page may have written it. */
        static Shown of(String address) {
            return new Shown(AboutScheme.parameter(address, URL).orElse(""), AboutScheme.parameter(address, CODE).orElse(""),
                AboutScheme.parameter(address, TOKEN).orElse(""), AboutScheme.parameters(address, SUBJECT),
                AboutScheme.parameter(address, SELF_SIGNED).isPresent(), AboutScheme.parameter(address, PROCEED_TOKEN));
        }

        /** The error page's address: {@code jasper-about:error?token=…&code=…&url=…}, then the certificate's parts. */
        URI address() {
            var query = new StringJoiner("&");
            query.add(parameter(TOKEN, token)).add(parameter(CODE, code)).add(parameter(URL, url));
            proceed.ifPresent(value -> query.add(parameter(PROCEED_TOKEN, value)));
            if (selfSigned) query.add(parameter(SELF_SIGNED, "1"));
            for (String subject : subjects) query.add(parameter(SUBJECT, subject));
            return URI.create(AboutScheme.NAME + ":" + NAME + "?" + query);
        }

        private static String parameter(String name, String value) {
            return name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    /** The theme the page is built in. */
    private record Look(WorkspacePalette palette, boolean dark) { }

    /** Each title spec section 14 gives, the errors that have it, and what the page then says. */
    private enum Kind {
        OFFLINE("No Internet Connection", "Jasper can’t open the page because your computer isn’t connected to the internet."),
        NOT_FOUND("Server Not Found", "Jasper can’t find the server “%s”."),
        TIMED_OUT("The Connection Timed Out", "The server “%s” took too long to respond."),
        REFUSED("Can’t Connect to Server", "Jasper can’t connect to the server “%s”."),
        LOST("Network Connection Lost", "The network connection was lost while the page loaded."),
        REDIRECTS("Too Many Redirects", "“%s” redirected Jasper too many times."),
        FILE("File Not Found", "Jasper can’t find the file “%s”."),
        SECURE("Secure Connection Failed", "Jasper can’t make a secure connection to the server “%s”."),
        UNAVAILABLE("Page Unavailable", "Jasper can’t show the page at “%s”."),
        OTHER(GENERIC, "Jasper can’t load the page at “%s”.");

        final String title, message;

        Kind(String title, String message) {
            this.title = title;
            this.message = message;
        }

        static Kind of(String code) {
            String name = code == null ? "" : code;
            return switch (name) {
                case "ERR_INTERNET_DISCONNECTED" -> OFFLINE;
                case "ERR_NAME_NOT_RESOLVED", "ERR_NAME_RESOLUTION_FAILED" -> NOT_FOUND;
                case "ERR_TIMED_OUT", "ERR_CONNECTION_TIMED_OUT" -> TIMED_OUT;
                case "ERR_CONNECTION_REFUSED", "ERR_CONNECTION_FAILED", "ERR_ADDRESS_UNREACHABLE" -> REFUSED;
                case "ERR_CONNECTION_RESET", "ERR_CONNECTION_CLOSED", "ERR_NETWORK_CHANGED" -> LOST;
                case "ERR_TOO_MANY_REDIRECTS" -> REDIRECTS;
                case "ERR_FILE_NOT_FOUND" -> FILE;
                case "ERR_BLOCKED_BY_CLIENT", "ERR_BLOCKED_BY_RESPONSE", "ERR_ACCESS_DENIED" -> UNAVAILABLE;
                default -> name.startsWith("ERR_SSL_") || certificateError(name) ? SECURE : OTHER;
            };
        }
    }
}
