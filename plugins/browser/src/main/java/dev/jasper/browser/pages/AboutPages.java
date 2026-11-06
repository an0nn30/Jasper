package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.Appearance;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jasper's own pages built from their templates (spec section 14): {@code pages/<name>.html}, bundled with the plugin,
 * filled in for each request. In a template, {@code {{style}}} becomes the workspace theme's colours as CSS variables
 * ({@link PageHtml#colours}) and {@code about.css}; {@code {{script}}} becomes {@code about.js}, which defines
 * {@code jasper}, and then the page's own {@code <name>.js}, if it has one; and each other {@code {{slot}}} its value,
 * escaped, so it shows as text.
 *
 * <p>Every page's head opens with a content security policy under which it loads nothing from anywhere: styles are its
 * own, images only {@code data:} ones, no base or form leads elsewhere, and the one script that runs is the one render
 * wrote, by a nonce made for that render alone. {@code about.js} is given the page's {@link AboutScheme#secret secret},
 * which {@code jasper.ask} sends with each query, so only this page's own document can ask as this page.
 *
 * <p>The theme's variables are built on the UI thread as the plugin starts and again after each theme change, so the
 * thread that renders, Chromium's IO thread, reads only the last ones built. Each file is read on its first use and
 * kept. A page already open shows a theme change when it loads again.
 */
public final class AboutPages {
    /** The content security policy of every page, with its script's nonce in place of {@code %s}. */
    static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-%s'; img-src data:; base-uri 'none'; "
        + "form-action 'none'";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern SLOT = Pattern.compile("\\{\\{([a-z][a-z0-9-]*)}}");
    private static final String STYLE = "style", SCRIPT = "script", HEAD = "<head>";
    private final Appearance appearance;
    private final AboutScheme about;
    private final Function<String, String> resources;
    // Each file read on its first use, then kept, absent or not.
    private final Map<String, Optional<String>> files = new ConcurrentHashMap<>();
    private volatile String variables;

    /**
     * The pages, in the workspace theme now and after each theme change. UI thread, while the plugin starts.
     *
     * @param appearance the plugin's appearance
     * @param about the pages' registry, whose secrets the pages' scripts are given
     */
    public AboutPages(Appearance appearance, AboutScheme about) {
        this(appearance, about, AboutPages::resource);
    }

    /**
     * As the public constructor, with the pages' files read from {@code resources}.
     *
     * @param resources a file's text by its name under {@code dev/jasper/browser/pages/}; null when there is none
     */
    AboutPages(Appearance appearance, AboutScheme about, Function<String, String> resources) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.about = Objects.requireNonNull(about, "about");
        this.resources = Objects.requireNonNull(resources, "resources");
        rebuild();
        appearance.onChanged(variant -> rebuild());
    }

    /**
     * The page {@code name}, its template filled in as the class says, with a nonce of its own. Any thread: Chromium's
     * IO thread asks it for each request.
     *
     * @param name the page's name, which is its template's and, for its queries, its {@code jasper-about:} page's
     * @param slots the value of each of the template's other slots, shown as text
     * @return the page's HTML
     * @throws IllegalArgumentException for a page Jasper has no template for, a slot the template has and
     *     {@code slots} lacks, or a value given for {@code style} or {@code script}
     * @throws IllegalStateException for a template with no {@code <head>} for the policy
     */
    public String render(String name, Map<String, String> slots) {
        Objects.requireNonNull(slots, "slots");
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("No Jasper page is called " + name);
        String template = file(name + ".html").orElseThrow(() -> new IllegalArgumentException("Jasper has no page " + name));
        if (slots.containsKey(STYLE) || slots.containsKey(SCRIPT))
            throw new IllegalArgumentException("{{style}} and {{script}} are Jasper's own slots");
        int head = template.indexOf(HEAD);
        if (head < 0) throw new IllegalStateException("pages/" + name + ".html has no " + HEAD);
        String nonce = PageHtml.newToken();
        head += HEAD.length();
        String withPolicy = template.substring(0, head) + "\n<meta http-equiv=\"Content-Security-Policy\" content=\"" + POLICY.formatted(nonce)
            + "\">" + template.substring(head);
        // One pass: no value is read again as a slot.
        var out = new StringBuilder(withPolicy.length() + 8_192);
        Matcher slot = SLOT.matcher(withPolicy);
        while (slot.find()) {
            String key = slot.group(1);
            String value = switch (key) {
                case STYLE -> style();
                case SCRIPT -> script(name, nonce);
                default -> {
                    String given = slots.get(key);
                    if (given == null) throw new IllegalArgumentException("pages/" + name + ".html needs a value for {{" + key + "}}");
                    yield PageHtml.escape(given);
                }
            };
            slot.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        slot.appendTail(out);
        return out.toString();
    }

    /** {@code name}, one of the pages' files, as the plugin bundles it; null when there is none. */
    static String resource(String name) {
        try (InputStream in = AboutPages.class.getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The style element: the theme's variables, then {@code about.css}. */
    private String style() {
        return "<style>\n" + variables + "\n" + required("about.css").strip() + "\n</style>";
    }

    /** The script element: {@code about.js} given the page's secret as {@code jasper}, then the page's own script. */
    private String script(String name, String nonce) {
        String secret = about.secret(name).orElse("");
        String own = file(name + ".js").map(script -> script.strip() + "\n").orElse("");
        return "<script nonce=\"" + nonce + "\">\nconst jasper = " + required("about.js").strip() + "(" + StrictJson.quote(secret) + ");\n"
            + own + "</script>";
    }

    private String required(String name) {
        return file(name).orElseThrow(() -> new IllegalStateException("The Browser plugin has no pages/" + name));
    }

    /**
     * One of the pages' files, read on its first use. A script that could end its element, or a style sheet that could
     * end its own, is refused, as it would let the page's text run.
     */
    private Optional<String> file(String name) {
        return files.computeIfAbsent(name, this::read);
    }

    private Optional<String> read(String name) {
        String text = resources.apply(name);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (name.endsWith(".js") && lower.contains("</script") || name.endsWith(".css") && lower.contains("</style"))
            throw new IllegalStateException("pages/" + name + " would end its own element");
        return Optional.ofNullable(text);
    }

    /** The theme's colours, read now. UI thread. */
    private void rebuild() {
        boolean dark = appearance.workspaceVariant() == Variant.DARK;
        variables = PageHtml.colours(appearance.workspacePalette()) + "\n:root { color-scheme: " + (dark ? "dark" : "light") + "; }";
    }
}
