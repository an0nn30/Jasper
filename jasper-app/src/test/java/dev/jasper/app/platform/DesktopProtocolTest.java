package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static dev.jasper.app.platform.DesktopObservation.*;
import static org.assertj.core.api.Assertions.*;

class DesktopProtocolTest {
    @Test void preservesIntegerGenerationAndRejectsDuplicates() {
        String wire = "{\"v\":1,\"kind\":\"pong\",\"generation\":9007199254740993}";
        assertThat(DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8)))
            .isEqualTo(new DesktopProtocol.Pong(9007199254740993L));
        String duplicate = "{\"v\":1,\"v\":2,\"kind\":\"pong\",\"generation\":1}";
        assertThatIllegalArgumentException().isThrownBy(() ->
            DesktopProtocol.decode(duplicate.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void acceptsExactly64KibAndRejectsOneByteMoreBeforeDecoding() {
        String pong = "{\"v\":1,\"kind\":\"pong\",\"generation\":0}";
        String boundary = pong + " ".repeat(65536 - pong.length());
        assertThat(decode(boundary)).isEqualTo(new DesktopProtocol.Pong(0));
        rejects(boundary + " ");
    }

    @Test void rejectsMalformedUtf8RatherThanReplacingIt() {
        byte[] wire = "{\"v\":1,\"kind\":\"pong\",\"generation\":0,\"x\":\"!!\"}".getBytes(StandardCharsets.UTF_8);
        int offset = new String(wire, StandardCharsets.UTF_8).indexOf("!!");
        wire[offset] = (byte) 0xc3;
        wire[offset + 1] = (byte) 0x28;
        assertThatIllegalArgumentException().isThrownBy(() -> DesktopProtocol.decode(wire));
    }

    @Test void rejectsNestingArraysDuplicateEscapedKeysAndTrailingContent() {
        for (String extra : new String[]{"\"x\":{}", "\"x\":[]", "\"kind\":\"pong\"", "\"\\u0076\":1"})
            rejects("{\"v\":1,\"kind\":\"pong\",\"generation\":1," + extra + "}");
        rejects("{\"v\":1,\"kind\":\"pong\",\"generation\":1} garbage");
        rejects("{\"v\":1,\"kind\":\"pong\",\"generation\":1,}");
    }

    @Test void rejectsUnknownVersionKindAndWrongMandatoryTypes() {
        for (String wire : new String[]{
            "{\"v\":2,\"kind\":\"pong\",\"generation\":1}",
            "{\"v\":1.0,\"kind\":\"pong\",\"generation\":1}",
            "{\"v\":1,\"kind\":\"future\",\"generation\":1}",
            "{\"v\":1,\"kind\":\"pong\",\"generation\":\"1\"}",
            "{\"v\":1,\"kind\":\"pong\",\"generation\":null}",
            "{\"v\":1,\"kind\":\"pong\",\"generation\":1.0}"}) rejects(wire);
    }

    @Test void preservesSigned64BitBoundariesWithoutFloatingPointConversion() {
        assertThat(decode("{\"v\":1,\"kind\":\"pong\",\"generation\":9223372036854775807}"))
            .isEqualTo(new DesktopProtocol.Pong(Long.MAX_VALUE));
        assertThat(DesktopJson.parse("{\"min\":-9223372036854775808,\"fraction\":0.5}"))
            .containsEntry("min", Long.MIN_VALUE).containsEntry("fraction", 0.5d);
        for (String generation : new String[]{"-1", "-9223372036854775808", "9223372036854775808", "1e0", "01", "+1"})
            rejects("{\"v\":1,\"kind\":\"pong\",\"generation\":" + generation + "}");
    }

    @Test void unknownScalarFieldsAreIgnoredUpTo32Keys() {
        String base = "{\"v\":1,\"kind\":\"pong\",\"generation\":1";
        StringBuilder wire = new StringBuilder(base);
        for (int i = 0; i < 29; i++) wire.append(",\"extra").append(i).append("\":null");
        assertThat(decode(wire + "}")).isEqualTo(new DesktopProtocol.Pong(1));
        rejects(wire + ",\"oneMore\":true}");
        assertThat(DesktopJson.parse("{\"s\":\"a\",\"n\":null,\"b\":true,\"i\":2,\"d\":2e0}"))
            .containsEntry("n", null).containsEntry("i", 2L).containsEntry("d", 2d);
    }

    @Test void reasonsPermit1024CharactersAndEscapedControls() {
        assertThat(portal("\"reason\":\"" + "x".repeat(1024) + "\"").reason()).hasSize(1024);
        rejects(portalWire("\"reason\":\"" + "x".repeat(1025) + "\""));
        assertThat(portal("\"reason\":\"quote \\\" slash \\\\ \\b\\f\\n\\r\\t\\u0001\"").reason())
            .isEqualTo("quote \" slash \\ \b\f\n\r\t\001");
        assertThat(portal("\"reason\":\"\\uD83D\\uDE00\"").reason()).isEqualTo(new String(Character.toChars(0x1f600)));
        rejects(portalWire("\"reason\":\"\\uD83D\""));
        rejects(portalWire("\"reason\":\"\\uDE00\""));
        rejects(portalWire("\"reason\":\"raw\ncontrol\""));
    }

    @Test void invalidOrAbsentPairHalfDropsOnlyThatPair() {
        for (String color : new String[]{"NaN,0,0,1", "Infinity,0,0,1", "1e999,0,0,1", "-0.1,0,0,1", "1.1,0,0,1", "0,0,0", "0x1p0,0,0,1", "+0,0,0,1"}) {
            Palette value = palette("\"view.bg\":\"" + color + "\",\"view.fg\":\"1,1,1,1\"");
            assertThat(value.colors()).containsOnlyKeys(Pair.WINDOW);
        }
        assertThat(palette("\"view.bg\":\"0,0,0,1\"").colors()).containsOnlyKeys(Pair.WINDOW);
        assertThat(palette("\"view.bg\":false,\"view.fg\":\"1,1,1,1\"").colors()).containsOnlyKeys(Pair.WINDOW);
        var absent = (DesktopProtocol.PaletteMessage) decode(paletteWire("" ).replace(",\"window.bg\":\"0.12,0.12,0.12,1\"", ""));
        assertThat(absent.value().colors()).isEmpty();
    }

    @Test void portalPreferencesFailIndependently() {
        Portal value = portal("\"reason\":\"\",\"accent\":\"NaN,0,0,1\",\"highContrast\":true");
        assertThat(value.accent()).isEmpty();
        assertThat(value.highContrast()).contains(true);
        value = portal("\"reason\":\"\",\"accent\":\"0.1,0.2,0.3,1\",\"highContrast\":\"bad\"");
        assertThat(value.accent()).contains(new Rgba(0.1, 0.2, 0.3, 1));
        assertThat(value.highContrast()).isEmpty();
    }

    @Test void dotDecimalsRemainIndependentOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(palette("").colors().get(Pair.WINDOW).background()).isEqualTo(new Rgba(0.12, 0.12, 0.12, 1));
            assertThat(new String(DesktopProtocol.sample(9, true), StandardCharsets.UTF_8))
                .isEqualTo("{\"v\":1,\"kind\":\"sample\",\"generation\":9,\"appearance\":\"dark\"}\n");
        } finally { Locale.setDefault(previous); }
    }

    @Test void commandsUseIntegerGenerationsNewlineAndRejectNegatives() {
        assertThat(new String(DesktopProtocol.ping(Long.MAX_VALUE), StandardCharsets.UTF_8))
            .isEqualTo("{\"v\":1,\"kind\":\"ping\",\"generation\":9223372036854775807}\n");
        assertThat(new String(DesktopProtocol.sample(0, false), StandardCharsets.UTF_8))
            .contains("\"appearance\":\"light\"").endsWith("\n");
        assertThatIllegalArgumentException().isThrownBy(() -> DesktopProtocol.ping(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> DesktopProtocol.sample(-1, true));
    }

    @Test void observationsDefensivelyCopyMapsAndValidateValues() {
        Palette palette = palette("");
        Map<Source, Palette> mutable = new HashMap<>();
        mutable.put(Source.GTK4, palette);
        DesktopObservation observation = new DesktopObservation(portal("\"reason\":\"\""), mutable);
        mutable.clear();
        assertThat(observation.palettes()).containsOnlyKeys(Source.GTK4);
        Map<Pair, Colors> pairs = new HashMap<>(palette.colors());
        Palette copied = new Palette(1, Source.GTK3, Polarity.DARK, pairs, "gtk3", "3.24", "");
        pairs.clear();
        assertThat(copied.colors()).containsOnlyKeys(Pair.WINDOW);
        for (double component : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.01})
            assertThatIllegalArgumentException().isThrownBy(() -> new Rgba(component, 0, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Portal(-1, Polarity.UNKNOWN, Optional.empty(), Optional.empty(), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new Palette(-1, Source.GTK3, Polarity.UNKNOWN, Map.of(), "", "", ""));
        assertThatNullPointerException().isThrownBy(() -> new Colors(null, new Rgba(0, 0, 0, 1)));
        assertThatNullPointerException().isThrownBy(() -> new DesktopObservation(null, Map.of()));
    }

    @Test void readsSharedNativeVectorsWithExplicitExpectedResults() throws Exception {
        Path root = Path.of(System.getProperty("jasper.repoRoot"), "packaging/desktop-colors/test");
        var vectors = Files.readAllLines(root.resolve("vectors.jsonl"));
        var manifest = Files.readAllLines(root.resolve("vectors.tsv"));
        assertThat(vectors).hasSameSizeAs(manifest);
        for (int i = 0; i < vectors.size(); i++) {
            String expected = manifest.get(i).split("\\t")[1];
            String wire = vectors.get(i);
            if (expected.equals("reject")) rejects(wire);
            else {
                var message = decode(wire);
                switch (expected) {
                    case "pong" -> assertThat(message).isEqualTo(new DesktopProtocol.Pong(Long.parseLong(manifest.get(i).split("\\t")[3])));
                    case "palette-window" -> assertThat(((DesktopProtocol.PaletteMessage) message).value().colors()).containsOnlyKeys(Pair.WINDOW);
                    case "palette-empty" -> assertThat(((DesktopProtocol.PaletteMessage) message).value().colors()).isEmpty();
                    case "portal" -> assertThat(message).isInstanceOf(DesktopProtocol.PortalMessage.class);
                    default -> fail("Unknown vector expectation: " + expected);
                }
            }
        }
    }

    private static DesktopProtocol.Message decode(String wire) { return DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8)); }
    private static void rejects(String wire) { assertThatIllegalArgumentException().isThrownBy(() -> decode(wire)); }
    private static String portalWire(String fields) { return "{\"v\":1,\"kind\":\"portal\",\"owner\":2,\"appearance\":\"unknown\"," + fields + "}"; }
    private static Portal portal(String fields) { return ((DesktopProtocol.PortalMessage) decode(portalWire(fields))).value(); }
    private static String paletteWire(String fields) {
        return "{\"v\":1,\"kind\":\"palette\",\"generation\":7,\"source\":\"gtk4\",\"appearance\":\"dark\",\"implementation\":\"libadwaita\",\"libraryVersion\":\"1.6\",\"reason\":\"\",\"window.bg\":\"0.12,0.12,0.12,1\",\"window.fg\":\"1,1,1,1\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
    }
    private static Palette palette(String fields) { return ((DesktopProtocol.PaletteMessage) decode(paletteWire(fields))).value(); }
}
