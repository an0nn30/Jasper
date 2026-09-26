package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads the themes an IntelliJ theme plugin provides, straight from its {@code .jar} or Marketplace
 * {@code .zip} (a folder holding {@code lib/*.jar}): {@code META-INF/plugin.xml} names each
 * {@code .theme.json} through a themeProvider, and a theme may name its editor scheme. Nothing is
 * extracted, loaded or run; every read is capped, and problems are returned rather than thrown.
 */
final class ThemeArchive {
    static final long MAX_ARCHIVE = 64L << 20;
    static final int MAX_FILE = 4 << 20;
    static final int MAX_NESTED = 32 << 20;
    static final int MAX_NESTED_JARS = 16;
    static final int MAX_ENTRIES = 10_000;
    private static final String PLUGIN_XML = "META-INF/plugin.xml";

    /**
     * One provided theme: {@code label} ({@code archive!/entry}, or {@code archive!/lib-jar!/entry}) and its
     * JSON, with the scheme file's bytes, or the name of an IntelliJ scheme, when the theme names one.
     */
    record Found(String label, String json, byte[] schemeXml, String schemeName, String schemeLabel) {}

    record Contents(List<Found> themes, List<String> problems) {
        Contents { themes = List.copyOf(themes); problems = List.copyOf(problems); }
    }

    /** A jar and a reader for a set of its entries; names it lacks are absent from the result. */
    private record Jar(String label, Function<Set<String>, Map<String, byte[]>> reader) {
        byte[] read(String name) { return reader.apply(Set.of(name)).get(name); }
    }

    /** An entry over its cap; carries no stack trace. */
    private static final class TooLarge extends RuntimeException {
        TooLarge() { super(null, null, false, false); }
    }

    private ThemeArchive() {}

    static Contents read(Path archive) {
        var themes = new ArrayList<Found>();
        var problems = new ArrayList<String>();
        String name = archive.getFileName().toString();
        try {
            if (Files.size(archive) > MAX_ARCHIVE) {
                problems.add(name + " is larger than 64 MiB; skipped");
                return new Contents(themes, problems);
            }
            try (var zip = new ZipFile(archive.toFile())) {
                if (zip.size() > MAX_ENTRIES) {
                    problems.add(name + " has more than 10,000 entries; skipped");
                    return new Contents(themes, problems);
                }
                if (zip.getEntry(PLUGIN_XML) != null) plugin(new Jar(name, wanted -> fromZip(zip, wanted)), themes, problems);
                else nested(zip, name, themes, problems);
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(name + " cannot be read as a plugin archive: " + failure.getMessage());
        }
        return new Contents(themes, problems);
    }

    /** A Marketplace zip: the plugin is one of the jars in its {@code lib} folder. */
    private static void nested(ZipFile zip, String name, List<Found> themes, List<String> problems) {
        List<String> jars = zip.stream().map(ZipEntry::getName).filter(ThemeArchive::libJar).sorted().toList();
        if (jars.size() > MAX_NESTED_JARS) problems.add(name + " has more than 16 jars in lib/; only the first 16 were read");
        boolean found = false;
        for (String entry : jars.subList(0, Math.min(jars.size(), MAX_NESTED_JARS))) {
            byte[] bytes;
            try { bytes = entryBytes(zip, zip.getEntry(entry), MAX_NESTED); }
            catch (TooLarge tooLarge) { problems.add(name + "!/" + entry + " is larger than 32 MiB; skipped"); continue; }
            boolean hasPlugin;
            // An oversized plugin.xml still marks the plugin jar; plugin() reports its size.
            try { hasPlugin = !fromBytes(bytes, Set.of(PLUGIN_XML)).isEmpty(); }
            catch (TooLarge tooLarge) { hasPlugin = true; }
            if (!hasPlugin) continue;
            found = true;
            plugin(new Jar(name + "!/" + entry, wanted -> fromBytes(bytes, wanted)), themes, problems);
        }
        if (!found) problems.add(name + " has no META-INF/plugin.xml, in itself or in a lib/*.jar; it is not an IntelliJ plugin");
    }

    private static void plugin(Jar jar, List<Found> themes, List<String> problems) {
        String location = jar.label() + "!/" + PLUGIN_XML;
        Element root;
        try {
            byte[] xml = jar.read(PLUGIN_XML);
            root = SafeXml.parse(xml);
        } catch (TooLarge tooLarge) {
            problems.add(location + " is larger than 4 MiB; skipped");
            return;
        } catch (IllegalArgumentException failure) {
            problems.add(location + " is " + failure.getMessage());
            return;
        }
        List<String> paths = providers(root);
        if (paths.isEmpty()) {
            List<Element> names = SafeXml.children(root, "name");
            String plugin = names.isEmpty() || names.getFirst().getTextContent().isBlank() ? jar.label() : names.getFirst().getTextContent().strip();
            problems.add(plugin + " provides no theme files; Jasper can't use themes drawn in code");
            return;
        }
        for (String path : paths) theme(jar, path, themes, problems);
    }

    /** Every themeProvider path, in either declaration form. */
    private static List<String> providers(Element root) {
        var paths = new ArrayList<String>();
        NodeList extensions = root.getElementsByTagName("extensions");
        for (int i = 0; i < extensions.getLength(); i++) {
            var section = (Element) extensions.item(i);
            boolean intellij = "com.intellij".equals(section.getAttribute("defaultExtensionNs"));
            for (Element child : SafeXml.children(section)) {
                String tag = child.getTagName();
                if ((intellij && tag.equals("themeProvider")) || tag.equals("com.intellij.themeProvider")) {
                    String path = child.getAttribute("path");
                    if (!path.isBlank()) paths.add(path);
                }
            }
        }
        return paths;
    }

    private static void theme(Jar jar, String path, List<Found> themes, List<String> problems) {
        String entry = entry(path, "");
        if (entry == null) {
            problems.add(jar.label() + ": theme path \"" + path + "\" leaves the plugin; skipped");
            return;
        }
        String label = jar.label() + "!/" + entry;
        byte[] bytes;
        try { bytes = jar.read(entry); }
        catch (TooLarge tooLarge) { problems.add(label + " is larger than 4 MiB; skipped"); return; }
        if (bytes == null) {
            problems.add(label + " is named by plugin.xml but missing; skipped");
            return;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        byte[] schemeXml = null;
        String schemeName = null, schemeLabel = null;
        String scheme = editorScheme(json);
        if (scheme != null && !scheme.isBlank()) {
            if (!schemePath(scheme)) { schemeName = scheme; schemeLabel = label; }
            else {
                String schemeEntry = entry(scheme, directory(entry));
                if (schemeEntry == null)
                    problems.add(label + ": editor scheme \"" + scheme + "\" leaves the plugin; the terminal uses Jasper's palette");
                else {
                    schemeLabel = jar.label() + "!/" + schemeEntry;
                    try {
                        schemeXml = jar.read(schemeEntry);
                        if (schemeXml == null)
                            problems.add(schemeLabel + " is named by " + label + " but missing; the terminal uses Jasper's palette");
                    } catch (TooLarge tooLarge) {
                        problems.add(schemeLabel + " is larger than 4 MiB; the terminal uses Jasper's palette");
                    }
                }
            }
        }
        themes.add(new Found(label, json, schemeXml, schemeName, schemeLabel));
    }

    /** The theme's {@code editorScheme}, or null; an unparsable theme is left for ThemeLoader to report. */
    private static String editorScheme(String json) {
        try {
            return Json.parse(new StringReader(json)) instanceof Map<?, ?> map && map.get("editorScheme") instanceof String scheme ? scheme : null;
        } catch (IOException | ParseException failure) {
            return null;
        }
    }

    private static boolean schemePath(String scheme) {
        String lower = scheme.toLowerCase(Locale.ROOT);
        return scheme.contains("/") || lower.endsWith(".xml") || lower.endsWith(".icls");
    }

    /** The jar entry for {@code path}: from the jar root when it starts with {@code /}, else from {@code directory}; null when it leaves the jar. */
    private static String entry(String path, String directory) {
        String joined = path.startsWith("/") ? path.substring(1) : directory + path;
        var parts = new ArrayList<String>();
        for (String part : joined.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            parts.add(part);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static String directory(String entry) {
        int slash = entry.lastIndexOf('/');
        return slash < 0 ? "" : entry.substring(0, slash + 1);
    }

    private static boolean libJar(String name) {
        return name.matches("[^/]+/lib/[^/]+") && name.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static Map<String, byte[]> fromZip(ZipFile zip, Set<String> wanted) {
        var found = new HashMap<String, byte[]>();
        for (String name : wanted) {
            ZipEntry entry = zip.getEntry(name);
            if (entry != null && !entry.isDirectory()) found.put(name, entryBytes(zip, entry, MAX_FILE));
        }
        return found;
    }

    private static byte[] entryBytes(ZipFile zip, ZipEntry entry, int limit) {
        try (InputStream in = zip.getInputStream(entry)) {
            return capped(in, limit);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** One streaming pass over an in-memory jar, keeping the wanted entries. */
    private static Map<String, byte[]> fromBytes(byte[] jar, Set<String> wanted) {
        var found = new HashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            int count = 0;
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (++count > MAX_ENTRIES) throw new IOException("a nested jar has more than 10,000 entries");
                if (!entry.isDirectory() && wanted.contains(entry.getName())) found.put(entry.getName(), capped(in, MAX_FILE));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return found;
    }

    private static byte[] capped(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) throw new TooLarge();
        return bytes;
    }
}
