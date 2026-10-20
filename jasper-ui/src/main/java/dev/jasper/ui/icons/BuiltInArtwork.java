package dev.jasper.ui.icons;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Complete immutable engine catalog, using exactly the external safe decoder. */
final class BuiltInArtwork {
    record Pair(DecodedArtwork light, DecodedArtwork dark) {
        DecodedArtwork select(boolean darkTheme) { return darkTheme ? dark : light; }
    }
    static final Set<String> IDENTITIES = Set.of("network", "tunnel", "more.vertical");
    /** The names {@link ClassicHolder} has, declared outside it so asking never decodes its files. */
    static final Set<String> CLASSIC_NAMES = Set.of("add", "split", "zoom", "settings", "window.add", "search");
    private static final class Holder {
        static final Map<String, Pair> CATALOG = Map.of(
            "settings", pair("general/settings", Hashes.SELECTED), "network", pair("toolwindows/web", Hashes.SELECTED),
            "tunnel", pair("actions/swapPanels", Hashes.SELECTED), "more.vertical", pair("general/moreVertical", Hashes.SELECTED),
            "chevron.right", pair("general/chevronRight", Hashes.SELECTED),
            "search", pair("general/search", Hashes.SELECTED), "file", pair("fileTypes/text", Hashes.SELECTED),
            "run", pair("classic/actions/execute", Hashes.CLASSIC),
            "application", single("classic/runConfigurations/application", Hashes.CLASSIC));
    }
    /**
     * Original desktop artwork for the meanings the native workspace chrome depicts (title bar, toolbar, tool window
     * stripes and status bar). Admitted only by {@link IconRegistry#chromeIcon}; descriptor views, panels, tiles and
     * plugin-registered artwork never receive it, so these meanings keep their ordinary engine glyphs everywhere else.
     */
    private static final class ChromeHolder {
        static final Map<String, Pair> CHROME = Map.of(
            "add", pair("general/add", Hashes.CHROME), "window.add", pair("actions/moveToWindow", Hashes.CHROME),
            "split", pair("general/splitVertically", Hashes.CHROME), "zoom", pair("inline/expand", Hashes.CHROME),
            "chevron.down", pair("general/chevronDown", Hashes.CHROME), "folder", pair("nodes/folder", Hashes.CHROME),
            "server", pair("webReferences/server", Hashes.CHROME), "bell", pair("toolwindows/notifications", Hashes.CHROME),
            "lock", pair("general/locked", Hashes.CHROME), "terminal", pair("terminal/toolwindow/terminal", Hashes.CHROME));
    }
    /**
     * IntelliJ's classic artwork for six of the meanings the native workspace chrome depicts. Admitted only by
     * {@link IconRegistry#chromeIcon(String, int, ChromeArtwork)} with {@link ChromeArtwork#CLASSIC}, and loaded on that
     * first call: the start-up prewarm never decodes it, since the default look never shows it.
     */
    private static final class ClassicHolder {
        static final Map<String, Pair> CLASSIC = Map.of(
            "add", pair("classic/general/add", Hashes.CLASSIC), "split", pair("classic/actions/splitVertically", Hashes.CLASSIC),
            "zoom", single("classic/general/expandComponent", Hashes.CLASSIC), "settings", pair("classic/general/settings", Hashes.CLASSIC),
            "window.add", pair("classic/actions/moveToWindow", Hashes.CLASSIC), "search", pair("classic/actions/find", Hashes.CLASSIC));
    }
    private static final class StripeHolder {
        static final Map<String, Pair> STRIPES = Map.of(
            "network", pair("classic/toolwindows/webToolWindow", Hashes.CLASSIC),
            "folder", pair("classic/toolwindows/toolWindowProject", Hashes.CLASSIC),
            "tunnel", pair("classic/toolwindows/toolWindowModuleDependencies", Hashes.CLASSIC),
            "download", pair("classic/toolwindows/toolWindowAnalyzeDataflow", Hashes.CLASSIC),
            "server", pair("classic/toolwindows/toolWindowServer", Hashes.CLASSIC),
            "bell", pair("classic/toolwindows/notifications", Hashes.CLASSIC));
    }
    static Map<String, Pair> catalog() { return Holder.CATALOG; }
    static Map<String, Pair> chrome() { return ChromeHolder.CHROME; }
    static Map<String, Pair> classicChrome() { return ClassicHolder.CLASSIC; }
    /**
     * IntelliJ's classic 13 px tool-window icons for a labelled stripe's buttons (Enterprise presentation spec 10.7).
     * Admitted only by {@link IconRegistry#stripeIcon} and loaded on its first call, never by the start-up prewarm or a
     * chrome receiver.
     */
    static Map<String, Pair> classicStripes() { return StripeHolder.STRIPES; }
    private static Pair pair(String stem, Map<String, String> hashes) {
        return new Pair(read(stem + ".svg", hashes), read(stem + "_dark.svg", hashes));
    }
    /** A file without a dark sibling serves both variants, as IntelliJ shows it on either theme. */
    private static Pair single(String stem, Map<String, String> hashes) {
        var artwork = read(stem + ".svg", hashes);
        return new Pair(artwork, artwork);
    }
    private static final class Hashes {
        static final Map<String,String> SELECTED = hashes("assets-selected.tsv", 14);
        static final Map<String,String> CHROME = hashes("assets-chrome.tsv", 20);
        static final Map<String,String> CLASSIC = hashes("assets-classic.tsv", 26);
        private static Map<String,String> hashes(String manifest, int count) {
            try(var input=BuiltInArtwork.class.getResourceAsStream(manifest)) {
                if(input==null)throw new IOException("Missing hash manifest " + manifest);
                var result=new java.util.LinkedHashMap<String,String>();
                for(String line:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                    var columns=line.split("\\t");
                    if(columns.length!=3 || result.put(columns[0],columns[2])!=null)throw new IOException("Invalid hash manifest " + manifest);
                }
                if(result.size()!=count)throw new IOException("Expected " + count + " original resources in " + manifest);
                return Map.copyOf(result);
            }catch(IOException failure){throw new IllegalStateException("Required bundled artwork: " + manifest,failure);}
        }
    }
    private static DecodedArtwork read(String file, Map<String, String> hashes) {
        String expected=hashes.get(file);
        try (var stream = BuiltInArtwork.class.getResourceAsStream(file)) {
            return decodeRequired(file,stream==null?null:stream.readAllBytes(),expected);
        } catch (IOException failure) {
            throw new IllegalStateException("Required bundled artwork: " + file + " SHA-256 " + expected, failure);
        }
    }
    static DecodedArtwork decodeRequired(String file, byte[] data, String expected) {
        try {
            if(data==null)throw new IOException("Missing bundled artwork");
            String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
            if(!actual.equals(expected))throw new IOException("Bundled artwork hash mismatch: " + actual);
            return new EncodedArtwork(EncodedArtwork.Format.SVG, data).decoded();
        } catch (IOException | java.security.NoSuchAlgorithmException | IllegalArgumentException failure) {
            throw new IllegalStateException("Required bundled artwork: " + file + " SHA-256 " + expected, failure);
        }
    }
}
