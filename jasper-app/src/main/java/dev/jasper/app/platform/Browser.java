package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import javax.swing.SwingUtilities;

/**
 * Web addresses: which text from outside Jasper is one, an internationalized host's ASCII form, and opening one in the
 * desktop's browser for plugins that ask through the SDK.
 */
public final class Browser {
    /**
     * What IDNA2003, which {@link IDN} follows, maps to another domain than browsers reach (UTS 46 nontransitional):
     * {@code \u00df} and {@code \u03c2}, and the zero-width non-joiner and joiner it drops.
     */
    private static final String IDNA2003_MAPS_ELSEWHERE = "\u00df\u03c2\u200c\u200d";

    private Browser() { }

    /**
     * {@code text} as a web address (browser plan 3): an absolute URI whose scheme is {@code http} or {@code https}, in
     * any case, with an authority, such as {@code https://example.com/a}. Anything else is empty: another scheme, an
     * opaque URI such as {@code https:example.com}, one without an authority such as {@code http:/notes}, a Windows path,
     * and text that does not parse. Any thread.
     *
     * @param text a {@code jasper} argument, a launch argument or an address the system handed Jasper
     * @return the address, or empty
     */
    public static Optional<URI> webAddress(String text) {
        try {
            var address = new URI(text);
            return isWebAddress(address) ? Optional.of(address) : Optional.empty();
        } catch (URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    /**
     * Whether {@code address} is a web address as {@link #webAddress(String)} reads one. Any thread.
     *
     * @param address any URI
     * @return true for an absolute http or https URI, in any case, with an authority
     */
    public static boolean isWebAddress(URI address) {
        String scheme = address.getScheme();
        boolean web = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        return web && !address.isOpaque() && address.getRawAuthority() != null;
    }

    /**
     * {@code address} with an internationalized host in its ASCII form (browser plan 4c, rulings Q17 and Q22): {@link URI}
     * reads such a host, in Unicode, {@code https://b\u00fccher.de/}, or percent-encoded, {@code https://b%C3%BCcher.de/},
     * as no host, and its ASCII form, {@code https://xn--bcher-kva.de/}, as one. The host is decoded and converted with
     * {@link IDN#toASCII(String, int)} under the STD3 rules, so only letters, digits, hyphens and dots come out, and the
     * result is kept only when it round-trips exactly: {@code URI} reads the converted host as the host, and the user
     * info, port, path, query and fragment as they were. A host whose decoded or mapped form holds a delimiter, such as
     * {@code http://evil.com%2F.good.com/} or a full-width {@code \uFF0F}, which would move part of it into the path,
     * therefore stays as it is. So does one with {@code \u00df}, {@code \u03c2} or a zero-width joiner or non-joiner:
     * {@code IDN} follows IDNA2003, which maps them to another domain than browsers reach ({@code fa\u00df.de} to
     * {@code fass.de}). Any other address comes back as it is too: one that has a host or is no web address
     * ({@link #isWebAddress}), and one whose host has no ASCII form, such as {@code http://my_service:8080/}. Any thread.
     *
     * @param address any URI
     * @return the address with its host in ASCII, or {@code address}
     */
    public static URI withAsciiHost(URI address) {
        if (!isWebAddress(address) || address.getHost() != null) return address;
        String authority = address.getRawAuthority();
        int at = authority.lastIndexOf('@');
        String userInfo = at < 0 ? null : authority.substring(0, at);
        String hostAndPort = authority.substring(at + 1);
        int colon = hostAndPort.lastIndexOf(':');
        boolean hasPort = colon >= 0 && hostAndPort.substring(colon + 1).chars().allMatch(c -> c >= '0' && c <= '9');
        String host = hasPort ? hostAndPort.substring(0, colon) : hostAndPort;
        String port = hasPort ? hostAndPort.substring(colon + 1) : "";
        if (host.isEmpty() || host.startsWith("[")) return address;
        try {
            String unicode = decoded(host);
            if (unicode.chars().anyMatch(c -> IDNA2003_MAPS_ELSEWHERE.indexOf(c) >= 0)) return address;
            String ascii = IDN.toASCII(unicode, IDN.USE_STD3_ASCII_RULES);
            var rebuilt = new StringBuilder(address.getScheme()).append("://");
            if (userInfo != null) rebuilt.append(userInfo).append('@');
            rebuilt.append(ascii);
            if (hasPort) rebuilt.append(':').append(port);
            if (address.getRawPath() != null) rebuilt.append(address.getRawPath());
            if (address.getRawQuery() != null) rebuilt.append('?').append(address.getRawQuery());
            if (address.getRawFragment() != null) rebuilt.append('#').append(address.getRawFragment());
            var converted = new URI(rebuilt.toString());
            boolean exact = ascii.equals(converted.getHost()) && Objects.equals(userInfo, converted.getRawUserInfo())
                && converted.getPort() == (port.isEmpty() ? -1 : Integer.parseInt(port))
                && Objects.equals(address.getRawPath(), converted.getRawPath())
                && Objects.equals(address.getRawQuery(), converted.getRawQuery())
                && Objects.equals(address.getRawFragment(), converted.getRawFragment());
            return exact ? converted : address;
        } catch (CharacterCodingException | IllegalArgumentException | URISyntaxException noAsciiHost) {
            return address;
        }
    }

    /** {@code text} with its percent-escapes decoded as UTF-8; a {@code %} that starts no escape stays. */
    private static String decoded(String text) throws CharacterCodingException {
        if (text.indexOf('%') < 0) return text;
        var bytes = new ByteArrayOutputStream(text.length());
        for (int i = 0; i < text.length(); ) {
            if (text.charAt(i) == '%' && i + 2 < text.length() && HexFormat.isHexDigit(text.charAt(i + 1))
                    && HexFormat.isHexDigit(text.charAt(i + 2))) {
                bytes.write(HexFormat.fromHexDigits(text, i + 1, i + 3));
                i += 3;
            } else {
                int character = text.codePointAt(i);
                bytes.writeBytes(Character.toString(character).getBytes(StandardCharsets.UTF_8));
                i += Character.charCount(character);
            }
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }

    /**
     * Opens an address in the desktop's browser; blocks while the desktop takes it, so never on the EDT.
     *
     * @param uri the address, already checked by the caller
     * @throws IllegalStateException on the event dispatch thread
     * @throws UnsupportedOperationException when this desktop cannot open a browser
     * @throws UncheckedIOException when the desktop fails to open it
     */
    public static void open(URI uri) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Opening a browser requires a worker thread");
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new UnsupportedOperationException("This desktop cannot open a browser");
        try { Desktop.getDesktop().browse(uri); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
