package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.*;

/** Bounded, deterministic legacy UI color evaluation, independent of LAF installation. */
final class AuthoredUiColors {
    private final ThemeDocument document;
    private final RuntimePlatform platform;
    private final Map<String, Object> values;

    AuthoredUiColors(
            ThemeDocument document, RuntimePlatform platform, Map<String, Object> defaults) {
        this.document = document;
        this.platform = Objects.requireNonNull(platform);
        values = new LinkedHashMap<>(defaults);
        document.ui()
                .forEach(
                        (key, raw) -> {
                            if (!UiColorKeys.supports(key, raw)) {
                                if (Arrays.stream(Token.values()).anyMatch(token -> UiTokenKeys.aliases(token).contains(key)) && raw instanceof String text
                                        && (text.startsWith("#") || text.startsWith("$") || text.startsWith("@") || text.contains("(") || text.contains(" ")))
                                    throw new IllegalArgumentException(document.origin() + ": invalid [ui] color " + key);
                                return;
                            }
                            Object value = branch(raw);
                            if (value == null) return;
                            if (key.startsWith("*."))
                                values.replaceAll(
                                        (existing, previous) ->
                                                existing.endsWith(key.substring(1))
                                                                && !existing.endsWith(
                                                                        ".hoverBackground")
                                                                && !existing.endsWith(
                                                                        ".pressedBackground")
                                                        ? value
                                                        : previous);
                            else {
                                values.put(key, value);
                                for (var token : Token.values())
                                    if (UiTokenKeys.aliases(token).contains(key))
                                        values.put(UiTokenKeys.KEYS.get(token), value);
                            }
                        });
    }

    Map<Token, Color> tokens() {
        var result = new EnumMap<Token, Color>(Token.class);
        for (var token : Token.values()) {
            if (document.tokens().containsKey(token)) continue;
            Set<String> keys = UiTokenKeys.aliases(token);
            Object supplied = null;
            String source = null;
            String start =
                    switch (token) {
                        case BUTTON_BACKGROUND -> "Button.startBackground";
                        case BUTTON_DEFAULT -> "Button.default.startBackground";
                        case BUTTON_BORDER -> "Button.startBorderColor";
                        default -> null;
                    };
            boolean gradient =
                    start != null
                            && document.ui().containsKey(start)
                            && document.ui().containsKey(start.replace(".start", ".end"));
            for (var entry : document.ui().entrySet()) {
                String key = entry.getKey();
                if (!UiColorKeys.supports(key, entry.getValue())) continue;
                if (gradient
                        ? key.equals(start)
                        : keys.contains(key)
                                || key.startsWith("*.")
                                        && !UiTokenKeys.KEYS.get(token).endsWith(".hoverBackground")
                                        && !UiTokenKeys.KEYS
                                                .get(token)
                                                .endsWith(".pressedBackground")
                                        && keys.stream()
                                                .anyMatch(k -> k.endsWith(key.substring(1)))) {
                    Object branch = branch(entry.getValue());
                    if (branch != null) {
                        supplied = branch;
                        source = key;
                    }
                }
            }
            if (source != null) {
                // Old native exports can contain an unresolved vendor palette name. Only a
                // top-level unprefixed bare name is optional; explicit/reference operands stay strict.
                if (supplied instanceof String text && text.matches("[A-Za-z][A-Za-z0-9_.-]*")
                        && !text.equals("null") && !values.containsKey(text) && !values.containsKey("ColorPalette." + text)) continue;
                try {
                    Color color = color(supplied, new LinkedHashSet<>(), 0);
                    if (color != null) result.put(token, color);
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException(
                            document.origin()
                                    + ": cannot resolve [ui] color "
                                    + source
                                    + "; use a literal color or resolvable reference/systemColor"
                                    + " fallback ("
                                    + failure.getMessage()
                                    + ")",
                            failure);
                }
            }
        }
        return result;
    }

    /**
     * Explicit authored colors for exact supported vendor keys, resolved against the same fallback snapshot as the
     * tokens. Wildcards do not apply; an unresolved bare vendor palette name is optional, as for token aliases.
     */
    Map<String, Color> explicit(Collection<String> keys) {
        var result = new LinkedHashMap<String, Color>();
        for (String key : keys) {
            Object raw = document.ui().get(key);
            if (raw == null || !UiColorKeys.supports(key, raw)) continue;
            Object supplied = branch(raw);
            if (supplied == null) continue;
            if (supplied instanceof String text && text.matches("[A-Za-z][A-Za-z0-9_.-]*")
                    && !text.equals("null") && !values.containsKey(text) && !values.containsKey("ColorPalette." + text)) continue;
            try {
                Color color = color(supplied, new LinkedHashSet<>(), 0);
                if (color != null) result.put(key, color);
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException(document.origin() + ": cannot resolve [ui] color " + key
                    + "; use a literal color or resolvable reference/systemColor fallback (" + failure.getMessage() + ")", failure);
            }
        }
        return result;
    }

    private Object branch(Object value) {
        if (value instanceof Map<?, ?> map)
            return branch(
                    map.containsKey(platform.key())
                            ? map.get(platform.key())
                            : map.get("os.default"));
        return value;
    }

    private Color reference(String key, Set<String> path, int depth) {
        boolean optional = key.startsWith("?");
        if (optional) key = key.substring(1);
        if (!path.add(key)) throw new IllegalArgumentException("cyclic color reference");
        try {
            Object value = values.get(key);
            if (value == null && !key.startsWith("@")) value = values.get("ColorPalette." + key);
            if (value == null) {
                if (optional) return null;
                throw new IllegalArgumentException("unknown color reference " + key);
            }
            return color(value, path, depth + 1);
        } finally {
            path.remove(key);
        }
    }

    private Color color(Object raw, Set<String> path, int depth) {
        if (depth > 32) throw new IllegalArgumentException("color expression is too deep");
        Object value = branch(raw);
        if (value instanceof Color c) return c;
        if (!(value instanceof String text))
            throw new IllegalArgumentException("missing platform color");
        text = text.strip();
        if (text.equals("null")) return null;
        if (text.matches("#[0-9a-fA-F]{3,8}")) {
            String h = text.substring(1);
            if (h.length() == 3 || h.length() == 4) {
                var b = new StringBuilder();
                for (char c : h.toCharArray()) b.append(c).append(c);
                h = b.toString();
            }
            if (h.length() != 6 && h.length() != 8)
                throw new IllegalArgumentException("invalid color literal");
            long n = Long.parseLong(h.length() == 6 ? h + "ff" : h, 16);
            return new Color(
                    (int) (n >>> 24) & 255,
                    (int) (n >>> 16) & 255,
                    (int) (n >>> 8) & 255,
                    (int) n & 255);
        }
        if (text.startsWith("$")) return reference(text.substring(1), path, depth);
        if (text.startsWith("@")) return reference(text, path, depth);
        int open = text.indexOf('(');
        if (open < 0) return reference(text, path, depth);
        String fn = text.substring(0, open);
        List<String> a = arguments(text.substring(open + 1, text.length() - 1));
        if (fn.equals("systemColor")) {
            if (a.size() < 2)
                throw new IllegalArgumentException("systemColor requires explicit fallback");
            return required(a.get(1), path, depth);
        }
        if (fn.equals("lazy")) return reference(a.getFirst(), path, depth);
        if (fn.equals("if")) {
            String test = a.getFirst();
            boolean truth = !test.equals("false") && !test.equals("0");
            if (test.startsWith("@") || test.startsWith("$")) {
                try {
                    truth = color(test, path, depth + 1) != null;
                } catch (IllegalArgumentException missing) {
                    truth = false;
                }
            }
            return color(a.get(truth ? 1 : 2), path, depth + 1);
        }
        if (fn.equals("rgb") || fn.equals("rgba") && a.size() == 4)
            return new Color(
                    channel(a.get(0)),
                    channel(a.get(1)),
                    channel(a.get(2)),
                    a.size() == 4 ? channel(a.get(3)) : 255);
        if (fn.equals("rgba")) {
            Color c = required(a.get(0), path, depth);
            return alpha(c, channel(a.get(1)) / 255d);
        }
        if (fn.equals("hsl") || fn.equals("hsla"))
            return fromHsl(
                    Double.parseDouble(a.get(0)),
                    percent(a.get(1)),
                    percent(a.get(2)),
                    a.size() == 4 ? percent(a.get(3)) : 1);
        Color c = color(a.getFirst(), path, depth + 1);
        if (c == null) return null;
        if (fn.equals("contrast")) {
            double threshold = a.size() > 3 ? percent(a.get(3)) : .43;
            return required(a.get(ColorMath.luminance(c) < threshold ? 2 : 1), path, depth);
        }
        if (fn.equals("over")) {
            if (c.getAlpha() == 255) return c;
            Color bottom = color(a.get(1), path, depth + 1);
            Color opaque = alpha(c, 1);
            return bottom == null ? opaque : blend(opaque, bottom, c.getAlpha() / 255d);
        }
        if (fn.equals("mix") || fn.equals("tint") || fn.equals("shade")) {
            int weightAt = fn.equals("mix") ? 2 : 1;
            double weight =
                    a.size() > weightAt && a.get(weightAt).endsWith("%")
                            ? percent(a.get(weightAt))
                            : .5;
            return fn.equals("mix")
                    ? blend(c, required(a.get(1), path, depth), weight)
                    : blend(fn.equals("tint") ? Color.WHITE : Color.BLACK, c, weight);
        }
        double[] hsl = hsl(c);
        int index =
                switch (fn) {
                    case "spin", "changeHue" -> 0;
                    case "saturate", "desaturate", "changeSaturation" -> 1;
                    case "fade", "fadein", "fadeout", "changeAlpha" -> 3;
                    default -> 2;
                };
        double amount = index == 0 ? Double.parseDouble(a.get(1)) : percent(a.get(1));
        String options = a.size() > 2 ? a.get(2) : "";
        boolean subtract = Set.of("darken", "desaturate", "fadeout").contains(fn);
        boolean assign = fn.startsWith("change") || fn.equals("fade");
        boolean inverse =
                options.contains("autoInverse")
                        || options.contains("derived") && !options.contains("noAutoInverse");
        if (!assign && inverse && index != 0 && (subtract ? hsl[index] < .35 : hsl[index] > .65))
            subtract = !subtract;
        double result =
                assign
                        ? amount
                        : hsl[index]
                                + (subtract ? -1 : 1)
                                        * amount
                                        * (index != 0 && options.contains("relative") ? hsl[index] : 1);
        hsl[index] = index == 0 ? (result % 360 + 360) % 360 : Math.clamp(result, 0, 1);
        return fromHsl(hsl[0], hsl[1], hsl[2], hsl[3]);
    }

    private Color required(String value, Set<String> path, int depth) {
        Color c = color(value, path, depth + 1);
        if (c == null) throw new IllegalArgumentException("null color argument");
        return c;
    }

    private static List<String> arguments(String text) {
        var result = new ArrayList<String>();
        int level = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') level++;
            else if (c == ')') level--;
            else if (c == ',' && level == 0) {
                result.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        result.add(text.substring(start).strip());
        return result;
    }

    private static double percent(String value) {
        return Double.parseDouble(value.substring(0, value.length() - 1)) / 100;
    }

    private static int channel(String value) {
        return value.endsWith("%")
                ? (int) (255 * percent(value))
                : Integer.parseInt(value);
    }

    private static Color alpha(Color c, double value) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * value));
    }

    private static Color blend(Color a, Color b, double amount) {
        return new Color(
                (int) Math.round(a.getRed() * amount + b.getRed() * (1 - amount)),
                (int) Math.round(a.getGreen() * amount + b.getGreen() * (1 - amount)),
                (int) Math.round(a.getBlue() * amount + b.getBlue() * (1 - amount)),
                (int) Math.round(a.getAlpha() * amount + b.getAlpha() * (1 - amount)));
    }

    private static double[] hsl(Color c) {
        double r = c.getRed() / 255d, g = c.getGreen() / 255d, b = c.getBlue() / 255d;
        double max = Math.max(r, Math.max(g, b)),
                min = Math.min(r, Math.min(g, b)),
                delta = max - min,
                l = (max + min) / 2;
        double h =
                delta == 0
                        ? 0
                        : max == r
                                ? 60 * ((g - b) / delta % 6)
                                : max == g
                                        ? 60 * ((b - r) / delta + 2)
                                        : 60 * ((r - g) / delta + 4);
        return new double[] {
            (h + 360) % 360,
            delta == 0 ? 0 : delta / (1 - Math.abs(2 * l - 1)),
            l,
            c.getAlpha() / 255d
        };
    }

    private static Color fromHsl(double h, double s, double l, double a) {
        // Float hue interpolation matches the pinned FlatLaf channel materialization.
        float hue = (float) ((h % 360 + 360) % 360) / 360f;
        float saturation = (float) s, lightness = (float) l;
        float q = lightness < .5f ? lightness * (1 + saturation)
            : lightness + saturation - saturation * lightness;
        float p = 2 * lightness - q;
        return new Color(hueChannel(p, q, hue + 1f / 3f), hueChannel(p, q, hue),
            hueChannel(p, q, hue - 1f / 3f), (float) a);
    }

    private static float hueChannel(float p, float q, float hue) {
        if (hue < 0) hue += 1;
        if (hue > 1) hue -= 1;
        float value = 6 * hue < 1 ? p + (q - p) * 6 * hue
            : 2 * hue < 1 ? q : 3 * hue < 2 ? p + (q - p) * 6 * (2f / 3f - hue) : p;
        return Math.clamp(value, 0f, 1f);
    }
}
