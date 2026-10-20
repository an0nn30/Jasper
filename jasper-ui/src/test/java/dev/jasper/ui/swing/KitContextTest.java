package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.UiKeySet;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.3: every context is made with a kit, and what it measures or draws follows that kit. */
class KitContextTest {
    @Test void everyContextIsMadeWithAKitThatAViewsCopyKeeps() {
        Edt.run(() -> {
            assertThat(KitContext.class.getConstructors()).isEmpty();
            var kit = KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_HEIGHT, 40));
            var context = KitFixtures.context(KitFixtures.dark(), kit);
            try (var view = new KitView(context, new ViewSpec(field()), event -> {})) {
                assertThat(context.kit()).isSameAs(kit);
                assertThat(context.metrics()).isSameAs(kit.metrics());
                assertThat(view.context()).as("the view's private copy").isNotSameAs(context);
                assertThat(view.context().kit()).isSameAs(kit);
            } finally { release(context); }
        });
    }

    /** The seam: one altered metric moves the context's metric, a measured height and a mounted control. */
    @Test void aKitWithOneAlteredMetricMovesWhatReadsIt() {
        Edt.run(() -> {
            var standard = KitFixtures.context();
            var tall = KitFixtures.context(KitFixtures.dark(), KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_HEIGHT, 40)));
            try (var a = new KitView(standard, new ViewSpec(field()), event -> {});
                 var b = new KitView(tall, new ViewSpec(field()), event -> {})) {
                assertThat(ThemePainting.metric(standard, DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(UIScale.scale(24));
                assertThat(ThemePainting.metric(tall, DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(UIScale.scale(40));
                assertThat(ThemePainting.height(tall, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)).isGreaterThanOrEqualTo(UIScale.scale(40));
                assertThat(b.getPreferredSize().height).as("a mounted field grows with its kit").isGreaterThan(a.getPreferredSize().height);
            } finally { release(standard); release(tall); }
        });
    }

    /** Review Focus 4: the user's font settings reach a kit's typography exactly as they reached the former global one. */
    @Test void typographyFollowsTheKitAndTheUserFontModes() {
        Edt.run(() -> {
            // Only the developer values, as AppearanceTransactionTest does: UIManager.get would fall through to the look and
            // feel's fonts and pin them as developer defaults for the rest of the test JVM.
            var keys = List.of("defaultFont", "Label.font", "Jasper.uiFontFamilyOverride");
            var saved = new java.util.HashMap<Object, Object>();
            UIManager.getDefaults().forEach((key, value) -> { if (keys.contains(key)) saved.put(key, value); });
            var context = KitFixtures.context();
            var large = KitFixtures.context(KitFixtures.dark(), new Kit("large-body", DesignMetrics.DEFAULT, TypeScale.DEFAULT.withBody(20),
                FaceSet.PLATFORM, dev.jasper.ui.kit.ControlVariants.DEFAULT, dev.jasper.ui.kit.ColorSlots.DEFAULT, UiKeySet.DEFAULT));
            try {
                UIManager.put("defaultFont", null); UIManager.put("Label.font", null); UIManager.put("Jasper.uiFontFamilyOverride", null);
                same(context, Typography.UserFont.NONE);
                assertThat(large.typography().font(Typography.Role.BODY).getSize2D()).as("no user size: the kit's body").isEqualTo(20f);
                assertThat(large.typography().font(Typography.Role.LABEL).getSize2D()).isEqualTo(12.5f * 20f / 13f);
                // Size only: ThemeController writes the flag false for the "system" family.
                UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18)); UIManager.put("Jasper.uiFontFamilyOverride", false);
                same(context, new Typography.UserFont(Optional.empty(), Optional.of(18f)));
                assertThat(large.typography().font(Typography.Role.BODY).getSize2D()).as("a user size wins over the kit's body").isEqualTo(18f);
                // A named family; Label.font's size wins over defaultFont's.
                var serif = new Font("Serif", Font.PLAIN, 20);
                UIManager.put("defaultFont", serif); UIManager.put("Label.font", serif.deriveFont(19.5f));
                UIManager.put("Jasper.uiFontFamilyOverride", true);
                same(context, new Typography.UserFont(Optional.of(serif.getFamily()), Optional.of(19.5f)));
                // The calibration fixtures' mode: the platform family named without the flag is an explicit family.
                var platform = new Font(KitFixtures.platformFamily(), Font.PLAIN, 13);
                UIManager.put("Label.font", null); UIManager.put("Jasper.uiFontFamilyOverride", null); UIManager.put("defaultFont", platform);
                same(context, new Typography.UserFont(Optional.of(platform.getFamily()), Optional.of(13f)));
            } finally {
                for (var key : keys) UIManager.put(key, saved.get(key)); // null removes a key that was absent
                release(context); release(large);
            }
        });
    }

    private static void same(KitContext context, Typography.UserFont user) {
        var expected = Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, user);
        assertThat(context.typography().family()).isEqualTo(expected.family());
        for (var role : Typography.Role.values()) assertThat(context.typography().font(role)).as(role.name()).isEqualTo(expected.font(role));
    }

    private static Nodes.Field field() {
        return new Nodes.Field("name", ControlState.READY, Nodes.FieldKind.TEXT, "", "Name", "Name", "");
    }

    private static void release(KitContext context) { context.binding().close(); context.icons().close(); }
}
