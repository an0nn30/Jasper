package dev.jasper.app.platform;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static dev.jasper.app.platform.DesktopObservation.*;

/** Version 1 desktop helper messages. The process owner supplies complete lines. */
final class DesktopProtocol {
    static final int MAX_MESSAGE = 64 * 1024;
    private static final Pattern DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    sealed interface Message permits PaletteMessage, PortalMessage, Pong { }
    record PaletteMessage(Palette value) implements Message { }
    record PortalMessage(Portal value) implements Message { }
    record Pong(long generation) implements Message {
        Pong { DesktopObservation.generation(generation); }
    }

    static Message decode(byte[] line) {
        if (line.length > MAX_MESSAGE) throw invalid();
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(line)).toString();
        } catch (CharacterCodingException e) { throw invalid(); }
        Map<String, Object> fields = DesktopJson.parse(json);
        if (!(fields.get("v") instanceof Long version) || version != 1) throw invalid();
        return switch (string(fields, "kind")) {
            case "pong" -> new Pong(generation(fields, "generation"));
            case "palette" -> new PaletteMessage(palette(fields));
            case "portal" -> new PortalMessage(new Portal(generation(fields, "owner"), polarity(fields),
                rgba(fields.get("accent")), fields.get("highContrast") instanceof Boolean value ? Optional.of(value) : Optional.empty(), string(fields, "reason")));
            default -> throw invalid();
        };
    }

    private static Palette palette(Map<String, Object> fields) {
        Source source = switch (string(fields, "source")) {
            case "gtk3" -> Source.GTK3;
            case "gtk4" -> Source.GTK4;
            case "qt5" -> Source.QT5;
            case "qt6" -> Source.QT6;
            case "kde5" -> Source.KDE5;
            case "kde6" -> Source.KDE6;
            default -> throw invalid();
        };
        var colors = new EnumMap<Pair, Colors>(Pair.class);
        for (Pair pair : Pair.values()) {
            String prefix = pair.name().toLowerCase(Locale.ROOT);
            Optional<Rgba> background = rgba(fields.get(prefix + ".bg"));
            Optional<Rgba> foreground = rgba(fields.get(prefix + ".fg"));
            if (background.isPresent() && foreground.isPresent()) colors.put(pair, new Colors(background.get(), foreground.get()));
        }
        return new Palette(generation(fields, "generation"), source, polarity(fields), colors,
            string(fields, "implementation"), string(fields, "libraryVersion"), string(fields, "reason"));
    }

    private static Optional<Rgba> rgba(Object value) {
        if (!(value instanceof String text)) return Optional.empty();
        String[] parts = text.split(",", -1);
        if (parts.length != 4) return Optional.empty();
        double[] values = new double[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (!DECIMAL.matcher(parts[i]).matches()) return Optional.empty();
                values[i] = Double.parseDouble(parts[i]);
            }
            return Optional.of(new Rgba(values[0], values[1], values[2], values[3]));
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private static Polarity polarity(Map<String, Object> fields) {
        return switch (string(fields, "appearance")) {
            case "dark" -> Polarity.DARK; case "light" -> Polarity.LIGHT; case "unknown" -> Polarity.UNKNOWN;
            default -> throw invalid();
        };
    }
    private static long generation(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof Long value)) throw invalid();
        return DesktopObservation.generation(value);
    }
    private static String string(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof String value)) throw invalid();
        return value;
    }
    static byte[] sample(long generation, boolean dark) {
        return command("sample", generation, ",\"appearance\":\"" + (dark ? "dark" : "light") + "\"");
    }
    static byte[] ping(long generation) { return command("ping", generation, ""); }
    private static byte[] command(String kind, long generation, String fields) {
        DesktopObservation.generation(generation);
        return ("{\"v\":1,\"kind\":\"" + kind + "\",\"generation\":" + generation + fields + "}\n").getBytes(StandardCharsets.UTF_8);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid desktop protocol envelope"); }
}
