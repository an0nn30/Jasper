package dev.jasper.app.platform;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native protocol fixtures, decoded by the same owner as worker replies; test-only. */
public final class DesktopRenderFixtures {
    private DesktopRenderFixtures() { }
    public record Fixture(String name, boolean dark, boolean high, DesktopObservation input) { }
    public static List<Fixture> read() throws java.io.IOException {
        try (var input = DesktopRenderFixtures.class.getResourceAsStream("/dev/jasper/app/appearance/desktop-palettes.jsonl")) {
            var result = new ArrayList<Fixture>();
            for (String line : new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                var fields = DesktopJson.parse(line);
                var palette = ((DesktopProtocol.PaletteMessage) DesktopProtocol.decode(line.getBytes(StandardCharsets.UTF_8))).value();
                var portalLine = "{\"v\":1,\"kind\":\"portal\",\"owner\":1,\"appearance\":\"unknown\",\"reason\":\"\",\"highContrast\":" + fields.get("highContrast")
                    + (fields.containsKey("accent") ? ",\"accent\":\"" + fields.get("accent") + "\"" : "") + "}";
                var portal = ((DesktopProtocol.PortalMessage) DesktopProtocol.decode(portalLine.getBytes(StandardCharsets.UTF_8))).value();
                result.add(new Fixture((String) fields.get("name"), (Boolean) fields.get("dark"), (Boolean) fields.get("highContrast"),
                    new DesktopObservation(portal, fields.containsKey("allUnavailable") ? Map.of() : Map.of(palette.source(), palette))));
            }
            return List.copyOf(result);
        }
    }
}
