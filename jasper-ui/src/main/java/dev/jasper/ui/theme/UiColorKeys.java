package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Color-only legacy UI key policy; never admits geometry, fonts or delegates. */
public final class UiColorKeys {
    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");
    private static final Set<String> OPTIONS = Set.of("relative", "autoInverse", "noAutoInverse", "derived", "lazy");

    private UiColorKeys() {}

    /** Legacy Jasper color aliases used to avoid inventing explicit tokens during import conversion. */
    public static Map<String, Token> chromeAliases() { return UiTemplate.JASPER; }

    /** Whether a legacy UI key describes a color or a referenced color variable. */
    public static boolean supports(String key) {
        if (key.endsWith("UI")) return false;
        if (key.startsWith("ColorPalette.")) return NAME.matcher(key.substring("ColorPalette.".length())).matches();
        if (UiTemplate.JASPER.containsKey(key) || key.equals("Jasper.headerHighlight")) return true;
        String lower = key.toLowerCase(Locale.ROOT);
        // FlatLaf's border slots parse insets, line thickness and arcs, even without a class name.
        if (!key.startsWith("@") && lower.endsWith("border")) return false;
        return key.startsWith("@") || lower.endsWith("background") || lower.endsWith("foreground")
            || lower.endsWith("color") || lower.endsWith("thumb") || lower.endsWith("track")
            || lower.endsWith("caret") || lower.startsWith("actions.") || lower.startsWith("objects.");
    }

    /** Whether the key and value are color-only, including every per-platform choice. */
    public static boolean supports(String key, Object value) {
        if (!supports(key)) return false;
        if (value instanceof Color) return true;
        if (value instanceof String text) return text.length() <= 4096 && expression(text.strip(), 0);
        if (value instanceof Map<?, ?> choices && !choices.isEmpty())
            return choices.entrySet().stream().allMatch(entry -> entry.getKey() instanceof String os
                && os.startsWith("os.") && supports(key, entry.getValue()));
        return false;
    }

    /** Validates the color grammar only; rendering and reference resolution remain caller-owned. */
    private static boolean expression(String text, int depth) {
        // FlatLaf parses these as booleans before inferring color type. They are valid only as conditions.
        if (depth > 32 || text.isEmpty() || text.equals("true") || text.equals("false")) return false;
        if (HEX.matcher(text).matches() || text.equals("null")) return true;
        if (text.startsWith("@")) return NAME.matcher(text.substring(1)).matches();
        if (text.startsWith("$")) return property(text.substring(1));
        if (NAME.matcher(text).matches()) return !CLASS_NAME.matcher(text).matches();
        int open = text.indexOf('(');
        if (open < 1 || !text.endsWith(")")) return false;
        String function = text.substring(0, open);
        List<String> args = arguments(text.substring(open + 1, text.length() - 1));
        if (args == null) return false;
        int count = args.size();
        return switch (function) {
            case "rgb" -> count == 3 && channels(args, 255);
            case "rgba" -> count == 4 && channels(args, 255)
                || count == 2 && expression(args.get(0), depth + 1) && channel(args.get(1), 255);
            case "hsl", "hsla" -> count == (function.equals("hsl") ? 3 : 4)
                && integer(args.get(0), 0, 360) && percentage(args.get(1)) && percentage(args.get(2))
                && (count == 3 || percentage(args.get(3)));
            case "lighten", "darken", "saturate", "desaturate", "fadein", "fadeout", "fade",
                 "changeSaturation", "changeLightness", "changeAlpha" ->
                count >= 2 && count <= 3 && expression(args.get(0), depth + 1) && percentage(args.get(1))
                    && (count == 2 || options(args.get(2)));
            case "spin", "changeHue" -> count >= 2 && count <= 3 && expression(args.get(0), depth + 1)
                && integer(args.get(1), Integer.MIN_VALUE, Integer.MAX_VALUE) && (count == 2 || options(args.get(2)));
            case "mix" -> mix(args, 2, depth);
            case "tint", "shade" -> mix(args, 1, depth);
            case "contrast" -> (count == 3 || count == 4) && colors(args.subList(0, 3), depth)
                && (count == 3 || percentage(args.get(3)));
            case "over" -> count == 2 && colors(args, depth);
            case "lazy" -> count == 1 && property(args.getFirst());
            case "systemColor" -> (count == 1 || count == 2) && NAME.matcher(args.getFirst()).matches()
                && (count == 1 || expression(args.get(1), depth + 1));
            case "if" -> count == 3 && condition(args.get(0)) && colors(args.subList(1, 3), depth);
            default -> false;
        };
    }

    private static boolean property(String text) {
        // FlatLaf allows one optional marker after '$' (and inside lazy property names).
        if (text.startsWith("?")) text = text.substring(1);
        return NAME.matcher(text).matches() && supports(text) && !CLASS_NAME.matcher(text).matches();
    }

    private static boolean condition(String text) {
        return text.equals("true") || text.equals("false") || text.equals("0")
            || (text.startsWith("@") || text.startsWith("$")) && NAME.matcher(text.substring(1)).matches();
    }

    private static boolean colors(List<String> args, int depth) {
        return args.stream().allMatch(arg -> expression(arg, depth + 1));
    }

    private static boolean mix(List<String> args, int colorCount, int depth) {
        if (args.size() < colorCount || args.size() > colorCount + 2
                || !colors(args.subList(0, colorCount), depth)) return false;
        if (args.size() == colorCount) return true;
        String next = args.get(colorCount);
        if (percentage(next)) return args.size() == colorCount + 1 || options(args.get(colorCount + 1));
        return args.size() == colorCount + 1 && options(next);
    }

    private static boolean channels(List<String> args, int maximum) {
        return args.stream().allMatch(arg -> channel(arg, maximum));
    }

    private static boolean channel(String text, int maximum) {
        return percentage(text) || integer(text, 0, maximum);
    }

    private static boolean percentage(String text) {
        return text.endsWith("%") && integer(text.substring(0, text.length() - 1), 0, 100);
    }

    private static boolean integer(String text, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(text);
            return value >= minimum && value <= maximum;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean options(String text) {
        return !text.isBlank() && List.of(text.split("\\s+")).stream().allMatch(OPTIONS::contains);
    }

    /** Split only commas outside nested color functions, rejecting unmatched parentheses. */
    private static List<String> arguments(String text) {
        var result = new ArrayList<String>();
        int level = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') level++;
            else if (c == ')' && --level < 0) return null;
            else if (c == ',' && level == 0) {
                result.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        if (level != 0) return null;
        result.add(text.substring(start).strip());
        return result;
    }
}
