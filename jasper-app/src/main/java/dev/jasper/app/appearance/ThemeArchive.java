package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    static final int MAX_PROVIDERS = 64;
    static final long MAX_INFLATED = 256L << 20;
    private static final String PLUGIN_XML = "META-INF/plugin.xml";

    /**
     * One provided theme: {@code label} ({@code archive!/entry}, or {@code archive!/lib-jar!/entry}) and its
     * JSON, with the scheme file's bytes, or the name of an IntelliJ scheme, when the theme names one.
     */
    record Found(String label, String json, byte[] schemeXml, String schemeName, String schemeLabel) {}

    record Contents(List<Found> themes, List<String> problems) {
        Contents { themes = List.copyOf(themes); problems = List.copyOf(problems); }
    }

    /** A batch of reads over a jar's entries: the bytes found, and which wanted names were over their cap. */
    private record Batch(Map<String, byte[]> bytes, Set<String> tooLarge) {}

    /** A jar and a reader for a set of its entries; names it lacks are absent from the result. */
    private record Jar(String label, Function<Set<String>, Batch> reader) {
        byte[] read(String name) {
            Batch batch = reader.apply(Set.of(name));
            if (batch.tooLarge().contains(name)) throw new TooLarge();
            return batch.bytes().get(name);
        }
    }

    /** An entry over its cap; carries no stack trace. */
    private static final class TooLarge extends RuntimeException {
        TooLarge() { super(null, null, false, false); }
    }

    /**
     * How many bytes a nested jar's entries have inflated to so far, shared across every {@link #fromBytes}
     * pass over one {@link #read(Path)} call (including its other nested jars): a 3 KiB zip that inflates to
     * 1 GiB must not take unbounded time or memory just because most of that is entries nobody wants.
     */
    private static final class Inflated {
        private long used;
        void add(int amount) throws IOException {
            used += amount;
            if (used > MAX_INFLATED) throw new IOException("inflates to more than 256 MiB");
        }
    }

    private ThemeArchive() {}

    static Contents read(Path archive) {
        var themes = new ArrayList<Found>();
        var problems = new ArrayList<String>();
        Path fileName = archive.getFileName();
        String name = fileName == null ? archive.toString() : fileName.toString();
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
                else nested(zip, name, themes, problems, new Inflated());
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(name + " cannot be read as a plugin archive: " + failure.getMessage());
        }
        return new Contents(themes, problems);
    }

    /** A Marketplace zip: the plugin is one of the jars in its {@code lib} folder. */
    private static void nested(ZipFile zip, String name, List<Found> themes, List<String> problems, Inflated budget) {
        List<String> jars = zip.stream().map(ZipEntry::getName).filter(ThemeArchive::libJar).sorted().toList();
        if (jars.size() > MAX_NESTED_JARS) problems.add(name + " has more than 16 jars in lib/; only the first 16 were read");
        boolean found = false;
        for (String entry : jars.subList(0, Math.min(jars.size(), MAX_NESTED_JARS))) {
            String label = name + "!/" + entry;
            byte[] bytes;
            boolean hasPlugin;
            try {
                bytes = entryBytes(zip, zip.getEntry(entry), MAX_NESTED);
                // An oversized plugin.xml still marks the plugin jar; plugin() reports its size.
                Batch probe = fromBytes(bytes, Set.of(PLUGIN_XML), budget);
                hasPlugin = !probe.bytes().isEmpty() || probe.tooLarge().contains(PLUGIN_XML);
            } catch (TooLarge tooLarge) {
                problems.add(label + " is larger than 32 MiB; skipped");
                continue;
            } catch (UncheckedIOException failure) {
                // One unreadable jar in lib/ does not hide the others.
                problems.add(label + " cannot be read: " + failure.getCause().getMessage());
                continue;
            }
            if (!hasPlugin) continue;
            found = true;
            try { plugin(new Jar(label, wanted -> fromBytes(bytes, wanted, budget)), themes, problems); }
            catch (UncheckedIOException failure) { problems.add(label + " cannot be read: " + failure.getCause().getMessage()); }
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
            problems.add(pluginName(root, jar) + " provides no theme files; Jasper can't use themes drawn in code");
            return;
        }
        // Repeated providers (even hundreds of them) must not multiply the work; keep only the first of each path.
        List<String> distinct = paths.stream().distinct().toList();
        if (distinct.size() > MAX_PROVIDERS) {
            problems.add(pluginName(root, jar) + " declares more than " + MAX_PROVIDERS + " themes; only the first "
                + MAX_PROVIDERS + " were read");
            distinct = distinct.subList(0, MAX_PROVIDERS);
        }
        providedThemes(jar, distinct, themes, problems);
    }

    /** The plugin's declared {@code <name>}, or its label when it has none. */
    private static String pluginName(Element root, Jar jar) {
        List<Element> names = SafeXml.children(root, "name");
        return names.isEmpty() || names.getFirst().getTextContent().isBlank() ? jar.label() : names.getFirst().getTextContent().strip();
    }

    /** Every themeProvider path, in either declaration form. */
    private static List<String> providers(Element root) {
        var paths = new ArrayList<String>();
        NodeList extensions = root.getElementsByTagName("extensions");
        for (int i = 0; i < extensions.getLength(); i++) {
            var section = (Element) extensions.item(i);
            boolean intellij = "com.intellij".equals(section.getAttribute("defaultExtensionNs"))
                || "com.intellij".equals(section.getAttribute("xmlns"));
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

    /** One provider whose theme path resolved to a jar entry, pending the batched theme-file read. */
    private record Candidate(String entry, String label) {}

    /** One theme whose file read, pending the batched scheme read (when it names a scheme path). */
    private record Pending(String label, String json, String schemeEntry, String schemeLabelIfPath) {}

    /**
     * Reads every provided theme in at most two more reader calls beyond {@code plugin.xml} itself: one
     * batched read for every theme file's bytes, then one batched read for every editor-scheme file's
     * bytes, whatever the provider count. A nested jar's entries would otherwise be streamed once per
     * provider, and streaming past unwanted entries inflates them uncounted.
     */
    private static void providedThemes(Jar jar, List<String> paths, List<Found> themes, List<String> problems) {
        var candidates = new ArrayList<Candidate>();
        var wantedThemeEntries = new LinkedHashSet<String>();
        for (String path : paths) {
            String entry = entry(path, "");
            if (entry == null) {
                problems.add(jar.label() + ": theme path \"" + path + "\" leaves the plugin; skipped");
                continue;
            }
            candidates.add(new Candidate(entry, jar.label() + "!/" + entry));
            wantedThemeEntries.add(entry);
        }
        if (candidates.isEmpty()) return;
        Batch themeBatch = jar.reader().apply(wantedThemeEntries);
        var pending = new ArrayList<Pending>();
        var wantedSchemeEntries = new LinkedHashSet<String>();
        for (Candidate candidate : candidates) {
            if (themeBatch.tooLarge().contains(candidate.entry())) {
                problems.add(candidate.label() + " is larger than 4 MiB; skipped");
                continue;
            }
            byte[] bytes = themeBatch.bytes().get(candidate.entry());
            if (bytes == null) {
                problems.add(candidate.label() + " is named by plugin.xml but missing; skipped");
                continue;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            String scheme = editorScheme(json);
            if (scheme == null || scheme.isBlank()) {
                pending.add(new Pending(candidate.label(), json, null, null));
            } else if (!schemePath(scheme)) {
                // A named IntelliJ scheme (not a path) has no file to read; carried through as schemeName below.
                pending.add(new Pending(candidate.label(), json, null, scheme));
            } else {
                String schemeEntry = entry(scheme, directory(candidate.entry()));
                if (schemeEntry == null) {
                    problems.add(candidate.label() + ": editor scheme \"" + scheme + "\" leaves the plugin; the terminal uses Jasper's palette");
                    pending.add(new Pending(candidate.label(), json, null, null));
                } else {
                    wantedSchemeEntries.add(schemeEntry);
                    pending.add(new Pending(candidate.label(), json, schemeEntry, null));
                }
            }
        }
        Batch schemeBatch = wantedSchemeEntries.isEmpty()
            ? new Batch(Map.of(), Set.of()) : jar.reader().apply(wantedSchemeEntries);
        for (Pending theme : pending) {
            if (theme.schemeEntry() == null) {
                // schemeLabelIfPath carries a named (not a path) scheme; schemeXml stays null either way.
                themes.add(new Found(theme.label(), theme.json(), null, theme.schemeLabelIfPath(),
                    theme.schemeLabelIfPath() == null ? null : theme.label()));
                continue;
            }
            String schemeLabel = jar.label() + "!/" + theme.schemeEntry();
            byte[] schemeXml;
            if (schemeBatch.tooLarge().contains(theme.schemeEntry())) {
                problems.add(schemeLabel + " is larger than 4 MiB; the terminal uses Jasper's palette");
                schemeXml = null;
            } else {
                schemeXml = schemeBatch.bytes().get(theme.schemeEntry());
                if (schemeXml == null)
                    problems.add(schemeLabel + " is named by " + theme.label() + " but missing; the terminal uses Jasper's palette");
            }
            themes.add(new Found(theme.label(), theme.json(), schemeXml, null, schemeLabel));
        }
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

    private static Batch fromZip(ZipFile zip, Set<String> wanted) {
        var found = new HashMap<String, byte[]>();
        var tooLarge = new HashSet<String>();
        for (String name : wanted) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) continue;
            try { found.put(name, entryBytes(zip, entry, MAX_FILE)); }
            catch (TooLarge large) { tooLarge.add(name); }
        }
        return new Batch(found, tooLarge);
    }

    private static byte[] entryBytes(ZipFile zip, ZipEntry entry, int limit) {
        try (InputStream in = zip.getInputStream(entry)) {
            return capped(in, limit);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * One streaming pass over an in-memory jar, keeping the wanted entries. Every entry is drained in
     * full, wanted or not, so its inflated size always counts against {@code budget}: skipping past an
     * unwanted entry without reading it would let it inflate uncounted (a stream, unlike a {@link ZipFile},
     * cannot seek past compressed data it never decompressed).
     */
    private static Batch fromBytes(byte[] jar, Set<String> wanted, Inflated budget) {
        var found = new HashMap<String, byte[]>();
        var tooLarge = new HashSet<String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            int count = 0;
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (++count > MAX_ENTRIES) throw new IOException("a nested jar has more than 10,000 entries");
                boolean want = !entry.isDirectory() && wanted.contains(entry.getName());
                byte[] bytes = drainEntry(in, want, budget);
                if (want) {
                    if (bytes == null) tooLarge.add(entry.getName());
                    else found.put(entry.getName(), bytes);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return new Batch(found, tooLarge);
    }

    /**
     * Reads {@code in} (one zip entry) to its end, charging every byte read to {@code budget} regardless
     * of {@code wanted}. Returns the entry's bytes when {@code wanted} and within {@link #MAX_FILE}; null
     * when unwanted, or wanted but over that cap.
     */
    private static byte[] drainEntry(InputStream in, boolean wanted, Inflated budget) throws IOException {
        byte[] buffer = new byte[8192];
        var kept = wanted ? new ByteArrayOutputStream() : null;
        long total = 0;
        boolean tooLarge = false;
        int read;
        while ((read = in.read(buffer)) != -1) {
            budget.add(read);
            total += read;
            if (wanted && !tooLarge) {
                if (total > MAX_FILE) tooLarge = true;
                else kept.write(buffer, 0, read);
            }
        }
        return wanted && !tooLarge ? kept.toByteArray() : null;
    }

    private static byte[] capped(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) throw new TooLarge();
        return bytes;
    }
}
