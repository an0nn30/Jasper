package dev.jasper.app.appearance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Test-only IntelliJ theme plugins, laid out the way the JetBrains Marketplace ships them. */
public final class ThemeFixtures {
    private ThemeFixtures() {}

    /** Former bundled themes retained only as fixtures for imported-theme rendering coverage. */
    public static final Theme INTELLIJ_LIGHT = BuiltInThemes.toml("intellij-light", "intellij-light.toml");
    public static final Theme DARCULA = BuiltInThemes.toml("darcula", "darcula.toml");

    /** A converted native fixture with real archive/scheme provenance for installation tests. */
    public static Path nativePlugin(Path file, String name, boolean dark, String background, String console) {
        Path source = file.resolveSibling("." + file.getFileName() + ".jar");
        plugin(source, name, dark, background, console);
        try {
            var converted = dev.jasper.app.themeimport.ThemeConverter.convert(source).getFirst();
            Files.writeString(file, ThemeTomlWriter.write(converted.document()));
            Files.delete(source); return file;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** A {@code .theme.json} naming its editor scheme ({@code null} for none) and setting {@code Panel.background}. */
    public static String theme(String name, boolean dark, String editorScheme, String panelBackground) {
        return "{\"name\": \"" + name + "\", \"dark\": " + dark + ", \"author\": \"Fixture\""
            + (editorScheme == null ? "" : ", \"editorScheme\": \"" + editorScheme + "\"")
            + ", \"ui\": {\"Panel.background\": \"" + panelBackground + "\"}}";
    }

    /** An editor scheme setting the console background and ANSI red; every other key comes from {@code parent}. */
    public static String scheme(String name, String parent, String background, String red) {
        return "<scheme name=\"" + name + "\" version=\"142\" parent_scheme=\"" + parent + "\">\n"
            + "  <colors>\n    <option name=\"CONSOLE_BACKGROUND_KEY\" value=\"" + background + "\"/>\n  </colors>\n"
            + "  <attributes>\n    <option name=\"CONSOLE_RED_OUTPUT\">\n      <value>\n"
            + "        <option name=\"FOREGROUND\" value=\"" + red + "\"/>\n      </value>\n    </option>\n  </attributes>\n"
            + "</scheme>\n";
    }

    /** A {@code plugin.xml} whose themeProvider elements name {@code themePaths}. */
    public static String pluginXml(String name, String... themePaths) {
        var providers = new StringBuilder();
        for (int i = 0; i < themePaths.length; i++)
            providers.append("    <themeProvider id=\"theme").append(i).append("\" path=\"").append(themePaths[i]).append("\"/>\n");
        return "<idea-plugin>\n  <id>fixture." + name.replace(' ', '-') + "</id>\n  <name>" + name + "</name>\n"
            + "  <extensions defaultExtensionNs=\"com.intellij\">\n" + providers + "  </extensions>\n</idea-plugin>\n";
    }

    /**
     * A typical theme plugin jar: one theme under {@code /themes/} with its editor scheme, whose parent is
     * Darcula for a dark theme and Default for a light one. Colours: {@code panelBackground} as {@code #rrggbb},
     * {@code consoleBackground} as IntelliJ writes scheme colours ({@code rrggbb}); ANSI red is {@code ff5555}.
     */
    public static Path plugin(Path file, String name, boolean dark, String panelBackground, String consoleBackground) {
        String base = name.replace(' ', '_');
        return jar(file, entries(
            "META-INF/plugin.xml", pluginXml(name, "/themes/" + base + ".theme.json"),
            "themes/" + base + ".theme.json", theme(name, dark, "/themes/" + base + ".xml", panelBackground),
            "themes/" + base + ".xml", scheme(name, dark ? "Darcula" : "Default", consoleBackground, "ff5555")));
    }

    /** Entries from alternating names and contents (a String, written as UTF-8, or a byte[]), in order. */
    public static Map<String, byte[]> entries(Object... namesAndContents) {
        var entries = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < namesAndContents.length; i += 2)
            entries.put((String) namesAndContents[i], namesAndContents[i + 1] instanceof byte[] bytes ? bytes
                : namesAndContents[i + 1].toString().getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    /** Writes {@code entries} as a zip (a jar is a zip) to {@code file}, creating its folder. */
    public static Path jar(Path file, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, jarBytes(entries));
            return file;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static byte[] jarBytes(Map<String, byte[]> entries) {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }
}
