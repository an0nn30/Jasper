package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The kit an appearance is installed under travels with its look and feel, like the theme, and gives FlatLaf's geometry. */
class FlatLafInstallerKitTest {
    /** The default kit with other geometry: control and field radius 2 (arcs 4), a 2 px focus ring and a 40% dark popup shadow. */
    private static Kit other() {
        return KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.CONTROL_RADIUS, 2).with(DesignMetrics.Role.FIELD_RADIUS, 2)
            .with(DesignMetrics.Role.FOCUS_RING, 2).with(DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK, 40));
    }

    /** Enterprise presentation plan 4: a field's arc follows FIELD_RADIUS, and the control arcs keep CONTROL_RADIUS. */
    @Test void theTextComponentArcFollowsTheFieldRadiusAlone() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(KitFixtures.dark(), KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_RADIUS, 1)));
                assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2);
                for (var key : List.of("Button.arc", "Component.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(8);
            } finally { set(prior); }
        });
    }

    /**
     * Plan 4a: the classic overlay reaches FlatLaf's installed defaults; the new keys win over the wildcards before them,
     * and the default button's border is read from {@code startBorderColor}.
     */
    @Test void theClassicKeySetReachesTheInstalledDefaults() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                    FlatLafInstaller.install(theme, Kit.CLASSIC);
                    var expected = new java.util.LinkedHashMap<String, ColorRole>();
                    for (var key : List.of("Table", "Tree")) {
                        expected.put(key + ".selectionBackground", SemanticColor.SELECTION_BG);
                        expected.put(key + ".selectionForeground", SemanticColor.TEXT_ONSELECTION);
                    }
                    expected.put("Button.default.startBorderColor", DerivedColor.ACCENT_EDGE);
                    expected.put("Button.endBackground", DerivedColor.BUTTON_END);
                    expected.put("TableHeader.background", DerivedColor.SURFACE_TABLE_HEADER);
                    expected.put("Table.alternateRowColor", DerivedColor.SURFACE_ZEBRA);
                    expected.forEach((key, role) -> assertThat(ColorMath.hex(UIManager.getColor(key))).as(theme.name() + " " + key)
                        .isEqualTo(ColorMath.hex(role.color(theme))));
                    assertThat(UIManager.getColor("Table.selectionBackground")).as("not the terminal selection")
                        .isNotEqualTo(theme.terminal().selection());
                }
            } finally { set(prior); }
        });
    }

    /** Review Focus 1: before any Jasper look and feel the installed kit is the default; it never throws. */
    @Test void withoutAJasperLookAndFeelTheInstalledKitIsTheDefault() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                set(new javax.swing.plaf.metal.MetalLookAndFeel());
                assertThat(FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
                assertThatIllegalStateException().as("the theme, unlike the kit, has no default").isThrownBy(FlatLafInstaller::installedTheme);
            } finally { set(prior); }
        });
    }

    /** The seam: one altered metric moves FlatLaf's extras; a same-instance reinstall republishes the kit (Review Focus 3). */
    @Test void theGeometryAndThePopupShadowFollowTheInstalledKit() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                var kit = other();
                FlatLafInstaller.install(KitFixtures.dark(), kit);
                assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                for (var key : List.of("Button.arc", "Component.arc", "TextComponent.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(4);
                assertThat(UIManager.getInt("Component.focusWidth")).isEqualTo(2);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.4, Offset.offset(1e-6));
                set(UIManager.getLookAndFeel());
                assertThat(FlatLafInstaller.installedKit()).as("a same-instance reinstall republishes the kit").isSameAs(kit);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.4, Offset.offset(1e-6));
                FlatLafInstaller.install(KitFixtures.dark(), Kit.DEFAULT);
                assertThat(FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
                assertThat(UIManager.getInt("Button.arc")).isEqualTo(8);
                assertThat(UIManager.getInt("Component.focusWidth")).isEqualTo(3);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.5, Offset.offset(1e-6));
            } finally { set(prior); }
        });
    }

    /** Review Focus 2: a rolled-back install restores the previous kit with the previous theme. */
    @Test void aFailedInstallRestoresThePreviousKitAndItsGeometry() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            var kit = other();
            try (var binding = new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.dark()), failure -> { throw new AssertionError(failure); })) {
                FlatLafInstaller.install(KitFixtures.dark(), kit);
                var transaction = new AppearanceTransaction(binding, theme -> {
                    FlatLafInstaller.install(theme, Kit.DEFAULT);
                    throw new IllegalStateException("injected");
                }, theme -> {});
                assertThatIllegalStateException().isThrownBy(() -> transaction.apply(new ThemePair(KitFixtures.light(), KitFixtures.light())))
                    .withMessage("injected");
                assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                assertThat(FlatLafInstaller.installedTheme()).isSameAs(KitFixtures.dark());
                assertThat(UIManager.getInt("Component.arc")).isEqualTo(4);
            } finally { set(prior); }
        });
    }

    /** Review Focus 1: plugin threads read the installed kit (named icons, presentation icons) off the EDT. */
    @Test void theInstalledKitCanBeReadOffTheEventThread() throws Exception {
        var kit = other();
        var prior = new AtomicReference<LookAndFeel>();
        Edt.run(() -> { prior.set(UIManager.getLookAndFeel()); FlatLafInstaller.install(KitFixtures.dark(), kit); });
        try {
            var seen = new AtomicReference<Object>();
            var reader = new Thread(() -> {
                try { seen.set(FlatLafInstaller.installedKit()); } catch (RuntimeException failure) { seen.set(failure); }
            });
            reader.start();
            reader.join(10_000);
            assertThat(seen.get()).isSameAs(kit);
        } finally { Edt.run(() -> set(prior.get())); }
    }

    private static void set(LookAndFeel laf) {
        try { UIManager.setLookAndFeel(laf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
    }
}
