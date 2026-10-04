package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.MotionPolicy;
import dev.jasper.ui.testsupport.*;
import java.awt.Color;
import java.awt.Font;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceTransactionTest {
    @Test void failedInstallationRestoresBothDefaultsLayersAndDoesNotPublish() {
        Edt.run(() -> {
            var oldLaf = UIManager.getLookAndFeel();
            var oldFont = UIManager.get("defaultFont");
            var oldSemantic = UIManager.get("Jasper.semantic.accent");
            try (var binding = new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.light()), t -> {})) {
                UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18));
                UIManager.put("Jasper.semantic.accent", Color.MAGENTA);
                Object lafFont = UIManager.getLookAndFeelDefaults().get("Label.font");
                var notifications = new ArrayList<ThemePair>();
                binding.subscribe(notifications::add);
                var pair = binding.current();
                var tx = new AppearanceTransaction(binding, theme -> {
                    FlatLafInstaller.install(theme);
                    UIManager.put("defaultFont", new Font("Dialog", Font.BOLD, 25));
                    UIManager.put("Jasper.semantic.accent", Color.RED);
                    UIManager.put("task.failure.only", Color.RED);
                    throw new IllegalStateException("injected installation failure");
                }, FlatLafInstaller::install);
                assertThatThrownBy(() -> tx.apply(new ThemePair(KitFixtures.light(), KitFixtures.dark())))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(UIManager.getLookAndFeel()).isSameAs(oldLaf);
                assertThat(UIManager.getFont("defaultFont").getSize()).isEqualTo(18);
                assertThat(UIManager.get("Jasper.semantic.accent")).isEqualTo(Color.MAGENTA);
                assertThat(UIManager.get("task.failure.only")).isNull();
                assertThat(UIManager.getLookAndFeelDefaults().get("Label.font")).isEqualTo(lafFont);
                assertThat(binding.current()).isSameAs(pair);
                assertThat(notifications).containsExactly(pair);
                // A later successful switch must not retain the restored LAF's values as global overrides.
                new AppearanceTransaction(binding, FlatLafInstaller::install, FlatLafInstaller::install)
                    .apply(new ThemePair(KitFixtures.light(), KitFixtures.dark()));
                assertThat(UIManager.getLookAndFeelDefaults().get("Jasper.semantic.surface.app"))
                    .isEqualTo(KitFixtures.light().semantic().color(SemanticColor.SURFACE_APP));
                assertThat(notifications).hasSize(2);
            } finally {
                UIManager.put("defaultFont", oldFont);
                UIManager.put("Jasper.semantic.accent", oldSemantic);
                UIManager.put("task.failure.only", null);
                try { UIManager.setLookAndFeel(oldLaf); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }
    @Test void restoreHookFailureStillRestoresGlobalsAndPreservesOriginalFailure() {
        Edt.run(() -> {
            var oldLaf = UIManager.getLookAndFeel();
            var oldFont = UIManager.get("defaultFont");
            var binding = new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.dark()), t -> {});
            var failure = new IllegalStateException("install");
            var restoreFailure = new IllegalStateException("restore");
            try {
                var tx = new AppearanceTransaction(binding, theme -> {
                    FlatLafInstaller.install(theme); UIManager.put("defaultFont", new Font("Dialog", Font.BOLD, 25)); throw failure;
                }, theme -> { throw restoreFailure; });
                assertThatThrownBy(() -> tx.apply(new ThemePair(KitFixtures.light(), KitFixtures.light())))
                    .isSameAs(failure).hasSuppressedException(restoreFailure);
                assertThat(UIManager.getLookAndFeel()).isSameAs(oldLaf);
                assertThat(UIManager.get("defaultFont")).isEqualTo(oldFont);
            } finally {
                binding.close(); UIManager.put("defaultFont", oldFont);
                try { UIManager.setLookAndFeel(oldLaf); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }
    @Test void logicalTypographyDoesNotConsumeFlatLafScaleTwice() {
        Edt.run(() -> {
            var oldLaf = UIManager.getLookAndFeel(); var oldFont = UIManager.get("defaultFont");
            var context = KitFixtures.context();
            try {
                UIManager.put("defaultFont", null); FlatLafInstaller.install(KitFixtures.dark());
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()).isEqualTo(13);
                UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18));
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()).isEqualTo(18);
                assertThat(context.theme()).isEqualTo(context.binding().current().workspace());
            } finally {
                context.binding().close(); context.icons().close(); UIManager.put("defaultFont", oldFont);
                try { UIManager.setLookAndFeel(oldLaf); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }
    @Test void publicationFailureAlsoRollsBackInstallation() {
        Edt.run(() -> {
            var oldLaf = UIManager.getLookAndFeel();
            var binding = new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.dark()), t -> {});
            try {
                var tx = new AppearanceTransaction(binding, theme -> { FlatLafInstaller.install(theme); binding.close(); }, theme -> {});
                assertThatThrownBy(() -> tx.apply(new ThemePair(KitFixtures.light(), KitFixtures.light())))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(UIManager.getLookAndFeel()).isSameAs(oldLaf);
            } finally {
                binding.close();
                try { UIManager.setLookAndFeel(oldLaf); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }
    @Test void replaySameVariantUpdatesListenerFailureAndDisposal() {
        Edt.run(() -> {
            var failures = new ArrayList<Throwable>();
            var values = new ArrayList<ThemePair>();
            var initial = new ThemePair(KitFixtures.dark(), KitFixtures.dark());
            var binding = new ThemeBinding(initial, failures::add);
            binding.subscribe(pair -> { throw new IllegalArgumentException("listener"); });
            var subscription = binding.subscribe(values::add);
            var next = new ThemePair(KitFixtures.custom(), KitFixtures.dark());
            binding.publish(next);
            binding.publish(next);
            assertThat(values).containsExactly(initial, next);
            assertThat(failures).hasSize(2);
            try { subscription.close(); subscription.close(); } catch (Exception e) { throw new AssertionError(e); }
            binding.publish(initial);
            assertThat(values).hasSize(2);
            binding.close(); binding.close();
            assertThatThrownBy(() -> binding.publish(next)).isInstanceOf(IllegalStateException.class);
        });
    }
    @Test void bindingRejectsAccessOutsideEdt() {
        assertThatThrownBy(() -> new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.light()), t -> {}))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void disablingAnimationsSettlesRunningEffects() {
        var enabled = new AtomicBoolean(true);
        var settled = new ArrayList<String>();
        var motion = new MotionPolicy(enabled::get);
        motion.register(() -> settled.add("settled"));
        enabled.set(false);
        assertThat(motion.enabled()).isFalse();
        assertThat(motion.enabled()).isFalse();
        assertThat(settled).containsExactly("settled");
    }
}
