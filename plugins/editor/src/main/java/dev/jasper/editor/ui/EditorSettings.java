package dev.jasper.editor.ui;

import dev.jasper.sdk.plugin.PluginConfig;
import java.awt.Font;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The Editor's settings file (spec 7); bad values are reported and their defaults used.
 *
 * @param fontFamily the editor font's family
 * @param fontSize points, 8 to 48
 * @param tabSize columns per tab stop, 1 to 16
 * @param softWrap whether new tabs wrap long lines
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib) {
    /** Tried in order for the default font. */
    public static final List<String> PREFERRED_FONTS = List.of("JetBrains Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

    /**
     * @param config the plugin's settings
     * @param installed the installed font families
     * @return the settings, reporting each bad value under its key
     */
    public static EditorSettings read(PluginConfig config, Set<String> installed) {
        String font = PREFERRED_FONTS.stream().filter(installed::contains).findFirst().orElse("Monospaced");
        var family = config.string("font_family");
        if (family.isPresent()) {
            if (installed.contains(family.get())) font = family.get();
            else config.report("font_family", "No installed font family is named \"" + family.get() + "\"; using " + font + ".");
        } else if (present(config, "font_family")) config.report("font_family", "Use a font family name in quotes; using " + font + ".");
        int size = number(config, "font_size", 13, 8, 48);
        int tab = number(config, "tab_size", 4, 1, 16);
        boolean wrap = flag(config, "soft_wrap", false);
        int max = number(config, "max_file_mib", 10, 1, 256);
        return new EditorSettings(font, size, tab, wrap, max);
    }

    /** @return {@link #maxFileMib()} in bytes */
    public long maxFileBytes() { return maxFileMib * 1024L * 1024L; }

    /** @return the plain editor font */
    public Font font() { return new Font(fontFamily, Font.PLAIN, fontSize); }

    private static int number(PluginConfig config, String key, int fallback, int min, int max) {
        OptionalLong value = config.integer(key);
        if (value.isEmpty()) {
            if (present(config, key)) config.report(key, "Use a whole number from " + min + " to " + max + "; using " + fallback + ".");
            return fallback;
        }
        if (value.getAsLong() < min || value.getAsLong() > max) {
            config.report(key, "Use " + min + " to " + max + "; using " + fallback + ".");
            return fallback;
        }
        return (int) value.getAsLong();
    }

    private static boolean flag(PluginConfig config, String key, boolean fallback) {
        var value = config.bool(key);
        if (value.isPresent()) return value.get();
        if (present(config, key)) config.report(key, "Use true or false; using " + fallback + ".");
        return fallback;
    }

    /** A value another accessor can see is present but of the wrong type; a float has no accessor and counts as absent. */
    private static boolean present(PluginConfig config, String key) {
        return config.string(key).isPresent() || config.integer(key).isPresent() || config.bool(key).isPresent()
            || !config.stringList(key).isEmpty() || config.table(key).isPresent();
    }
}
