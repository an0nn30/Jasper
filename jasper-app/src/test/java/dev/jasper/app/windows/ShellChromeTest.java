package dev.jasper.app.windows;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 10.1: the shells' values under each look. */
class ShellChromeTest {
    @Test void aNativeTitledLookFollowsTheThemeOnMacOsOnly() throws Exception {
        edt(() -> {
            var root = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, true);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, false);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            var other = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), false).followTheme(other, null, true);
            ShellChrome.of(new ShellLook(false, false, false), true).followTheme(other, null, true);
            assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("off macOS, or a look that does not follow").isNull();
        });
    }

    /** Plan 14 (Rulings 12 and 16): under a classic title macOS frames carry the File menu for the screen menu bar; elsewhere none. */
    @Test void aClassicTitleOnMacOsGivesAuxiliaryFramesTheMenuTheScreenMenuBarShows() {
        var chrome = dev.jasper.app.testsupport.EnterpriseLook.titled(ChromeSpec.CLASSIC, ChromeSpec.TitleBar.CLASSIC);
        var mac = ShellChrome.of(Projections.of(chrome, true, true, "p", new Projections.Host(Projections.Platform.MAC, true)).shell(), true);
        assertThat(mac.frameMenuRow()).isTrue();
        assertThat(mac.unifiedTitle()).as("their system title bars").isFalse();
        for (var platform : new Projections.Platform[] {Projections.Platform.WINDOWS, Projections.Platform.LINUX})
            assertThat(ShellChrome.of(Projections.of(chrome, true, true, "p", new Projections.Host(platform, false)).shell(), false).frameMenuRow())
                .as("%s", platform).isFalse();
    }

    /** Plan 10b Rulings 14 (spec 10.1 as amended): a classic look's auxiliary frames show no menu row and close with their key. */
    @Test void aClassicLooksFramesHaveNoMenuRowAndCloseWithTheirKey() throws Exception {
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).frameMenuRow()).as("a classic look on macOS").isFalse();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), false).frameMenuRow()).as("a classic look elsewhere").isFalse();
        assertThat(ShellChrome.of(new ShellLook(false, false, false), true).frameMenuRow()).as("the default look").isTrue();
        assertThat(ShellChrome.standard().frameMenuRow()).isTrue();
        edt(() -> {
            var surface = new AuxiliarySurface("dev.jasper.vault.manager", "Credential Vault", AuxiliarySurface.Kind.WINDOW, false,
                new java.awt.Dimension(820, 571), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
            var commands = new java.util.concurrent.atomic.AtomicInteger();
            // Close Window closes as the close button does: through the window's closing guards, which keep it open here.
            surface.onClosing(() -> { commands.incrementAndGet(); return false; });
            var root = new JRootPane();
            NativeShells.bindCloseWindow(root, surface);
            int menu = com.formdev.flatlaf.util.SystemInfo.isMacOS ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK;
            var key = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, menu);
            var action = root.getActionMap().get(root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(key));
            assertThat(action).as("Close Window's key is bound on the frame").isNotNull();
            action.actionPerformed(new java.awt.event.ActionEvent(root, java.awt.event.ActionEvent.ACTION_PERFORMED, "close"));
            assertThat(commands).as("it asks the window's closing guards").hasValue(1);
            root.putClientProperty(dev.jasper.app.platform.WindowInput.SHORTCUT_RECORDER,
                new dev.jasper.app.platform.WindowInput.ShortcutRecorder(stroke -> stroke.equals(key)));
            action.actionPerformed(new java.awt.event.ActionEvent(root, java.awt.event.ActionEvent.ACTION_PERFORMED, "close"));
            assertThat(commands).as("a window recording shortcuts keeps the key").hasValue(1);
            surface.close();
        });
    }

    @Test void theTitleBarIsTheLooks() {
        assertThat(ShellChrome.of(new ShellLook(true, false, false), true).unifiedTitle()).isTrue();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).unifiedTitle()).isFalse();
        assertThat(ShellChrome.standard().unifiedTitle()).isEqualTo(dev.jasper.app.platform.MacTitleBar.isSupported());
    }
}
