package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.TerminalColors;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Reads an Alacritty colour theme (TOML) into Jasper's terminal palette. Pure; any thread. */
public final class AlacrittyTheme {
    /**
     * A read theme.
     *
     * @param palette the palette, or empty when the theme cannot be used
     * @param problems why it cannot be used, and colours that were ignored
     */
    public record Result(Optional<TerminalColors> palette, List<String> problems) {
        public Result { problems = List.copyOf(problems); }
    }

    static final List<String> COLOURS = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
    private static final Pattern HEX = Pattern.compile("(?:#|0[xX])([0-9a-fA-F]{6})");
    private static final List<String> KEYWORDS = List.of("cellforeground", "cellbackground");

    private AlacrittyTheme() { }

    /** Reads a theme's text; never throws for bad input. */
    public static Result read(String toml) {
        try {
            return parse(toml);
        } catch (RuntimeException | StackOverflowError unreadable) {
            // tomlj recurses per nesting level; a deeply nested file overflows the stack.
            return new Result(Optional.empty(), List.of("It cannot be read as TOML."));
        }
    }

    private static Result parse(String toml) {
        TomlParseResult parsed = Toml.parse(toml);
        if (parsed.hasErrors())
            return new Result(Optional.empty(), List.of("It is not valid TOML: " + parsed.errors().getFirst().getMessage()));
        var problems = new ArrayList<String>();
        Color background = colour(parsed, "colors.primary.background", problems);
        Color foreground = colour(parsed, "colors.primary.foreground", problems);
        if (background == null || foreground == null) {
            problems.add("It has no colors.primary background and foreground.");
            return new Result(Optional.empty(), problems);
        }
        TerminalColors fallback = luminance(background) < 0.5 ? ImportPalettes.dark() : ImportPalettes.light();
        var ansi = new ArrayList<Color>(16);
        for (int i = 0; i < 8; i++) {
            Color normal = colour(parsed, "colors.normal." + COLOURS.get(i), problems);
            ansi.add(normal != null ? normal : fallback.ansi().get(i));
        }
        for (int i = 0; i < 8; i++) {
            Color bright = colour(parsed, "colors.bright." + COLOURS.get(i), problems);
            ansi.add(bright != null ? bright : ansi.get(i));
        }
        Color cursor = colour(parsed, "colors.cursor.cursor", problems);
        Color selection = colour(parsed, "colors.selection.background", problems);
        return new Result(Optional.of(new TerminalColors(background, foreground, cursor != null ? cursor : foreground,
            selection != null ? selection : blend(foreground, background, 0.3), ansi)), problems);
    }

    /** The name a theme file offers: its base name with {@code _} and {@code -} as spaces and each word capitalised. */
    public static String displayName(String fileName) {
        String base = fileName.toLowerCase(Locale.ROOT).endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        var words = new ArrayList<String>();
        for (String word : base.split("[_\\-\\s]+"))
            if (!word.isEmpty()) words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return String.join(" ", words);
    }

    /** The colour at {@code dotted}; null when missing, a keyword, or not a colour (then a problem is added). */
    private static Color colour(TomlParseResult toml, String dotted, List<String> problems) {
        Object value;
        try { value = toml.get(dotted); }
        catch (RuntimeException notATable) { return null; }
        if (value == null) return null;
        if (value instanceof String text) {
            var match = HEX.matcher(text.strip());
            if (match.matches()) return new Color(Integer.parseInt(match.group(1), 16));
            if (KEYWORDS.contains(text.strip().toLowerCase(Locale.ROOT))) return null;
        }
        problems.add(dotted + " is not a colour (#rrggbb or 0xrrggbb): " + value + "; ignored.");
        return null;
    }

    private static double luminance(Color colour) {
        return (0.2126 * colour.getRed() + 0.7152 * colour.getGreen() + 0.0722 * colour.getBlue()) / 255.0;
    }

    private static Color blend(Color over, Color under, double amount) {
        return new Color((int) Math.round(over.getRed() * amount + under.getRed() * (1 - amount)),
            (int) Math.round(over.getGreen() * amount + under.getGreen() * (1 - amount)),
            (int) Math.round(over.getBlue() * amount + under.getBlue() * (1 - amount)));
    }
}
