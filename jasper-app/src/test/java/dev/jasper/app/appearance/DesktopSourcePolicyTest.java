package dev.jasper.app.appearance;

import dev.jasper.app.platform.DesktopEnvironment;
import dev.jasper.app.platform.DesktopObservation.Source;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static dev.jasper.app.appearance.DesktopPalette.SourceMode.AUTO;
import static dev.jasper.app.appearance.DesktopPalette.SourceMode.QT;
import static dev.jasper.app.platform.DesktopObservation.Source.*;
import static org.assertj.core.api.Assertions.*;

class DesktopSourcePolicyTest {
    @ParameterizedTest(name = "{0}") @MethodSource("orders")
    void ordersEveryCandidateFromCapturedHints(String name, Map<String, String> hints, List<Source> automatic, List<Source> qt) {
        var environment = DesktopEnvironment.from(hints);
        assertThat(DesktopPalette.preferredSources(AUTO, environment)).containsExactlyElementsOf(automatic);
        assertThat(DesktopPalette.preferredSources(QT, environment)).containsExactlyElementsOf(qt);
        assertThat(DesktopPalette.preferredSources(DesktopPalette.SourceMode.GTK3, environment)).containsExactly(Source.GTK3);
        assertThat(DesktopPalette.preferredSources(DesktopPalette.SourceMode.GTK4, environment)).containsExactly(Source.GTK4);
    }
    static Stream<Arguments> orders() {
        var kde5 = List.of(KDE5, QT5, KDE6, QT6, Source.GTK3, Source.GTK4);
        var kde6 = List.of(KDE6, QT6, KDE5, QT5, Source.GTK3, Source.GTK4);
        var qt6 = List.of(QT6, QT5, Source.GTK3, Source.GTK4);
        var qt5 = List.of(QT5, QT6, Source.GTK3, Source.GTK4);
        var gnome = List.of(Source.GTK4, Source.GTK3, QT6, QT5);
        var unknown = List.of(Source.GTK3, Source.GTK4, QT6, QT5);
        return Stream.of(
            row("KDE5", "KDE", "5", "", "", kde5, List.of(KDE5, QT5, KDE6, QT6)),
            row("trimmed KDE5", " KDE ", " 5 ", "", "qt6ct", kde5, List.of(KDE5, QT5, KDE6, QT6)),
            row("KDE6", "KDE", "6", "", "qt5ct", kde6, List.of(KDE6, QT6, KDE5, QT5)),
            row("KDE absent version", "KDE", "", "", "", kde6, List.of(KDE6, QT6, KDE5, QT5)),
            row("KDE malformed version", "KDE", "5.0", "", "", kde6, List.of(KDE6, QT6, KDE5, QT5)),
            row("KDE unknown version", "KDE", "bogus", "", "", kde6, List.of(KDE6, QT6, KDE5, QT5)),
            row("LXQt", "LXQt", "5", "", "", qt6, List.of(QT6, QT5)),
            row("GNOME", "GNOME", "", "", "qt5ct", gnome, List.of(QT6, QT5)),
            row("first GNOME token", " vendor : gNoMe : KDE ", "5", "true", "qt5ct", gnome, List.of(QT6, QT5)),
            row("first KDE token", "KDE:GNOME", "5", "", "", kde5, List.of(KDE5, QT5, KDE6, QT6)),
            row("first LXQt token", "vendor:LXQt:KDE", "5", "true", "", qt6, List.of(QT6, QT5)),
            row("unknown", "not-gnome", "5", "1", "", unknown, List.of(QT6, QT5)),
            row("blank", "", "", "", "  ", unknown, List.of(QT6, QT5)),
            row("KDE full session", "vendor", "5", " TrUe ", "", kde5, List.of(KDE5, QT5, KDE6, QT6)),
            row("full session exact only", "vendor", "5", "truthy", "", unknown, List.of(QT6, QT5)),
            row("LXQt Qt5 parameters", "LXQt", "", "", "qt5ct:parameters", qt5, List.of(QT5, QT6)),
            row("unknown Qt5 mixed case", "vendor", "", "", " notqt5ct ; Qt5Ct : options ", qt5, List.of(QT5, QT6)),
            row("unknown Qt6", "vendor", "", "", "qt6ct", qt6, List.of(QT6, QT5)),
            row("first Qt5 key", "LXQt", "", "", "unknown;qt5ct;qt6ct", qt5, List.of(QT5, QT6)),
            row("first Qt6 key", "vendor", "", "", "unknown:qt5ct; QT6CT:arg;qt5ct", qt6, List.of(QT6, QT5)),
            row("unknown substrings", "vendor", "", "", "notqt5ct;qt6ct-extra;other:qt5ct", unknown, List.of(QT6, QT5)),
            Arguments.of("installed libraries irrelevant", Map.of("QT_STYLE_OVERRIDE", "breeze", "QT_PLUGIN_PATH", "/qt5ct",
                "LD_LIBRARY_PATH", "/Qt5:/KF5"), unknown, List.of(QT6, QT5)));
    }
    private static Arguments row(String name, String desktop, String version, String full, String hint, List<Source> auto, List<Source> qt) {
        return Arguments.of(name, Map.of("XDG_CURRENT_DESKTOP", desktop, "KDE_SESSION_VERSION", version,
            "KDE_FULL_SESSION", full, "QT_QPA_PLATFORMTHEME", hint), auto, qt);
    }
    @Test void policyIsLocaleIndependentAndReturnsImmutableOrders() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var order = DesktopPalette.preferredSources(AUTO, DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "lxqt")));
            assertThat(order).containsExactly(QT6, QT5, Source.GTK3, Source.GTK4);
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> order.add(KDE5));
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() ->
                DesktopPalette.preferredSources(QT, DesktopEnvironment.from(Map.of())).add(Source.GTK3));
        } finally { Locale.setDefault(before); }
    }
}
