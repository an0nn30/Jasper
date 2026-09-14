package dev.jasper.app;

import org.junit.jupiter.api.Test;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class UiLookAndFeelTest {
    @Test void parsesEveryBuiltInChoiceWithoutPlatformFiltering() {
        for (String id : new String[]{"metal", "nimbus", "motif", "system", "aqua", "windows", "windows-classic", "gtk"}) {
            var result = ConfigLoader.parse(Path.of("config.toml"), "[ui]\nlaf = '" + id + "'", false);
            assertThat(result.rejected()).isFalse();
            assertThat(result.diagnostics()).as(id).isEmpty();
            assertThat(result.snapshot().laf().id()).isEqualTo(id);
        }
    }

    @Test void invalidChoiceUsesDefaultAndWrongTypeRejects() {
        var invalid = ConfigLoader.parse(Path.of("config.toml"), "[ui]\nlaf = 'java.lang.String'", true);
        assertThat(invalid.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        assertThat(invalid.rejected()).isFalse();
        assertThat(invalid.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.key()).isEqualTo("ui.laf");
            assertThat(d.line()).isEqualTo(2);
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(d.message()).doesNotContain("java.lang.String");
        });
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "ui.laf=42", true).rejected()).isTrue();
    }
    @Test void missingSelectionDefaultsToMotif() {
        assertThat(ConfigSnapshot.defaults().laf()).isEqualTo(UiLookAndFeel.MOTIF);
        assertThat(ConfigLoader.parse(Path.of("config.toml"), "", false).snapshot().laf())
            .isEqualTo(UiLookAndFeel.MOTIF);
    }

    @Test void installsCrossPlatformBuiltInsOnEdtAndRestoresPreviousLaf() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                for (var laf : new UiLookAndFeel[]{UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS, UiLookAndFeel.MOTIF}) {
                    assertThat(laf.install()).as(laf.id()).isEmpty();
                    assertThat(UIManager.getLookAndFeel().getID()).isEqualTo(switch (laf) {
                        case METAL -> "Metal";
                        case NIMBUS -> "Nimbus";
                        case MOTIF -> "Motif";
                        default -> throw new AssertionError();
                    });
                }
            } finally { restore(previous); }
        });
    }

    @Test void unavailablePlatformLafFallsBackToMetalWithWarning() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                var unavailable = System.getProperty("os.name").startsWith("Windows")
                    ? UiLookAndFeel.AQUA : UiLookAndFeel.WINDOWS;
                UiLookAndFeel.NIMBUS.install();
                assertThat(unavailable.install()).contains(unavailable.id(), "Metal");
                assertThat(UIManager.getLookAndFeel().getID()).isEqualTo("Metal");
            } finally { restore(previous); }
        });
    }

    @Test void systemUsesRuntimeSystemLookAndFeelOrWarnsWhenHeadlessUnsupported() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                String warning = UiLookAndFeel.SYSTEM.install();
                if (warning.isEmpty()) {
                    assertThat(UIManager.getLookAndFeel().getClass().getName())
                        .isEqualTo(UIManager.getSystemLookAndFeelClassName());
                } else {
                    assertThat(warning).contains("system", "Metal");
                    assertThat(UIManager.getLookAndFeel().getID()).isEqualTo("Metal");
                }
            } finally { restore(previous); }
        });
    }

    @Test void macCommandEditingShortcutsRemainAvailableWithCrossPlatformLookAndFeels() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Mac"));
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try {
                for (var laf : new UiLookAndFeel[]{UiLookAndFeel.METAL, UiLookAndFeel.NIMBUS, UiLookAndFeel.MOTIF}) {
                    laf.install();
                    for (var field : new javax.swing.text.JTextComponent[]{new javax.swing.JTextField(),
                            new javax.swing.JFormattedTextField(), new javax.swing.JPasswordField(),
                            new javax.swing.JTextArea(), new javax.swing.JTextPane(), new javax.swing.JEditorPane()}) {
                        var input = field.getInputMap();
                        assertThat(input.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
                            java.awt.event.InputEvent.META_DOWN_MASK))).as(laf.id() + " " + field.getClass())
                            .isEqualTo(javax.swing.text.DefaultEditorKit.copyAction);
                        assertThat(input.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V,
                            java.awt.event.InputEvent.META_DOWN_MASK))).isEqualTo(javax.swing.text.DefaultEditorKit.pasteAction);
                        assertThat(input.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X,
                            java.awt.event.InputEvent.META_DOWN_MASK))).isEqualTo(javax.swing.text.DefaultEditorKit.cutAction);
                        assertThat(input.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A,
                            java.awt.event.InputEvent.META_DOWN_MASK))).isEqualTo(javax.swing.text.DefaultEditorKit.selectAllAction);
                    }
                }
            } finally { restore(previous); }
        });
    }

    @Test void installationRequiresEdt() {
        assertThatIllegalStateException().isThrownBy(() -> UiLookAndFeel.METAL.install());
    }

    private static void restore(LookAndFeel previous) {
        try { UIManager.setLookAndFeel(previous); }
        catch (javax.swing.UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
    }
}
