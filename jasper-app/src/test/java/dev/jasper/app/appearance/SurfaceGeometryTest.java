package dev.jasper.app.appearance;

import dev.jasper.ui.model.Nodes;
import java.awt.Window;
import javax.swing.AbstractButton;
import javax.swing.JPasswordField;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SurfaceGeometryTest {
    @Test void recoveryWarningRemainsVisibleWithActualEnlargedSettingsAndConstrainedWidth() throws Exception {
        var themes = SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try (var open = SurfaceFixtures.open("vault-create", "entered")) {
            for (float size : new float[]{20, 32, 13}) SurfaceFixtures.edt(() -> {
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system", size));
                for (int width : new int[]{com.formdev.flatlaf.util.UIScale.scale(400), 340}) {
                    var view = open.view();
                    view.setSize(width, 2000); SurfaceFixtures.arrange(view);
                    view.setSize(width, view.getPreferredSize().height); SurfaceFixtures.arrange(view);
                    var label = (javax.swing.JLabel)open.component("form.warning.text");
                    var icon = open.component("form.warning.icon");
                    int lineHeight = label.getFontMetrics(label.getFont()).getHeight();
                    double textWidth = label.getFont().getStringBounds(label.getText(), new java.awt.font.FontRenderContext(null,true,true)).getWidth();
                    assertThat(label.getHeight()).as("full warning at size %s width %s", size,width)
                        .isGreaterThanOrEqualTo((int)Math.ceil(textWidth / label.getWidth()) * lineHeight);
                    assertThat(icon.getWidth()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(12));
                    assertThat(open.bounds("form.warning.text").y + label.getHeight()).isLessThanOrEqualTo(open.bounds("cancel").y);
                    assertThat(((javax.swing.JScrollPane)open.component("form.scroll")).getVerticalScrollBar().isVisible()).isFalse();
                    assertThat(((JPasswordField)open.component("confirm.input")).getPassword()).containsExactly("synthetic-preview-password".toCharArray());
                    var image = new java.awt.image.BufferedImage(width, view.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); try { view.paint(graphics); } finally { graphics.dispose(); }
                }
                return null;
            });
        } finally { SurfaceFixtures.edt(() -> { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, dev.jasper.app.config.UiFontConfig.defaults()); themes.close(); return null; }); }
    }

    @Test void actualVaultOwnersUseReferenceHeroGeometryWithoutCreatingWindows() throws Exception {
        int windows=Window.getWindows().length;
        for(String id:java.util.List.of("vault-create","vault-unlock")) try(var open=SurfaceFixtures.open(id,"empty")) {
            assertThat(Window.getWindows()).hasSize(windows);
            assertThat(open.preferred().width).isEqualTo(id.equals("vault-create")?400:380);
            assertThat(open.preferred().height).isEqualTo(id.equals("vault-create")?428:264);
            SurfaceFixtures.edt(() -> {
                for (String textId : id.equals("vault-create") ? java.util.List.of("form.heading", "form.warning.text") : java.util.List.of("form.heading")) {
                    var label = (javax.swing.JLabel)open.component(textId);
                    int painted = (int)Math.ceil(label.getFont().getStringBounds(label.getText(), new java.awt.font.FontRenderContext(null, true, true)).getWidth());
                    assertThat(label.getWidth()).as("actual label must not elide its complete text: %s", textId).isGreaterThanOrEqualTo(painted);
                }
                return null;
            });
            assertThat(open.bounds("form.hero.tile").width).isEqualTo(54);
            assertThat(open.bounds("password").height).isEqualTo(32);
            assertThat(open.bounds("password").x).isEqualTo(28);
            assertThat(open.bounds("submit").height).isEqualTo(30);
            assertThat(open.bounds("cancel").width).isEqualTo(open.bounds("submit").width);
            assertThat(open.bounds("submit").x-open.bounds("cancel").x-open.bounds("cancel").width).isEqualTo(8);
            assertThat(open.bounds("password").y).isEqualTo(id.equals("vault-create")?164:146);
        }
    }

    @Test void actualMountedEditsReadinessRevealThemeAndDisposalKeepSecretsOutOfDescriptors() throws Exception {
        JPasswordField[] retained=new JPasswordField[1];
        try(var open=SurfaceFixtures.open("vault-create","empty")) {
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isFalse(); return null; });
            open.type("password","1234567"); open.type("confirm","1234567");
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isFalse(); return null; });
            open.type("password","12345678"); open.type("confirm","12345678");
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isTrue(); return null; });
            open.click("password.reveal");
            var input=(JPasswordField)open.component("password.input"); retained[0]=input;
            SurfaceFixtures.edt(() -> { input.select(2,5); assertThat(input.getEchoChar()).isEqualTo((char)0); return null; });
            for(String theme:java.util.List.of("dark","light")) {
                open.theme(theme);
                SurfaceFixtures.edt(() -> {
                    assertThat(open.component("password.input")).isSameAs(input);
                    assertThat(input.getPassword()).containsExactly("12345678".toCharArray());
                    assertThat(input.getSelectionStart()).isEqualTo(2); assertThat(input.getSelectionEnd()).isEqualTo(5);
                    assertThat(input.getEchoChar()).isEqualTo((char)0);
                    assertThat(open.snapshot().toString()).doesNotContain("12345678");
                    return null;
                });
            }
            open.type("confirm", "");
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isFalse(); return null; });
        }
        SurfaceFixtures.edt(() -> { assertThat(retained[0].getPassword()).isEmpty(); return null; });
    }

    @Test void errorTransitionRepacksWithoutAStaleNarrowViewport() throws Exception {
        try (var open = SurfaceFixtures.open("vault-unlock", "empty")) {
            open.error();
            // Native validation/layout can run at the old height before DialogFit's queued repack.
            SurfaceFixtures.edt(() -> { SurfaceFixtures.arrange(open.view()); return null; });
            open.layout();
            SurfaceFixtures.edt(() -> {
                var scroll = (javax.swing.JScrollPane)open.component("form.scroll");
                assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();
                var message = open.component("message");
                assertThat(message.isVisible()).isTrue();
                assertThat(open.bounds("message").y + message.getHeight()).isLessThanOrEqualTo(open.bounds("cancel").y);
                assertThat(open.bounds("password").width).isEqualTo(324);
                return null;
            });
        }
    }

    @Test void unlockEmptyPrimaryIsDisabledAndAnyNonemptyInputEnablesIt() throws Exception {
        try(var open=SurfaceFixtures.open("vault-unlock","empty")) {
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isFalse(); return null; });
            open.type("password","x");
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isTrue(); return null; });
            open.type("password","");
            SurfaceFixtures.edt(() -> { assertThat(((AbstractButton)open.component("submit")).isEnabled()).isFalse(); return null; });
        }
    }
}
