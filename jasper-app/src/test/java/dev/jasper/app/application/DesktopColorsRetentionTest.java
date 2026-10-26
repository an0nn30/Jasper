package dev.jasper.app.application;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.platform.*;
import dev.jasper.app.plugins.DesktopEditorRetentionFixture;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.kit.Kit;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** Application transactions preserve visible content and dirty Settings across equal and unequal snapshots. */
class DesktopColorsRetentionTest {
    @TempDir Path home;
    @Test void visibleEditorDocumentCaretSelectionScrollAndUndoSurviveEqualAndUnequalSnapshots() throws Exception {
        for (var kit : java.util.List.of(Kit.DEFAULT,Kit.CLASSIC)) DesktopEditorRetentionFixture.verify(home.resolve(kit.id()),kit);
    }

    @Test void multipleWindowsWithOpenMenusRetainTerminalContentAcrossDesktopTransactions() throws Exception {
        dev.jasper.app.workspace.DesktopRetentionFixture.verify();
    }

    @Test void highContrastTransitionKeepsTheMountedSettingsDraftDirty() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try(var f = new DesktopAppearanceTest.Fixture(); var system = SystemAppearance.open()) {
                f.owner.update(DesktopAppearanceTest.config("qt",true)); f.drain();
                var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"),
                    "[ui.theme]\ndark='desktop-colors'\nlight='desktop-colors'\ndesktop_source='qt'\n",true).values());
                draft.stage(CoreSettings.WINDOW_TAB_HEIGHT,Optional.of(44));
                var choices = SettingsAppearance.choices(system,ThemeLibrary::bundledOnly,Optional.empty(),f.owner,true);
                var context = new PageContext(draft,id->{},Runnable::run,(title,initial)->Optional.empty(),()->{},()->null,
                    ()->f.themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));
                var actions = new CorePages.SystemActions(()->Path.of("config.toml"),()->{},()->{},()->{},()->{},()->true,Optional.empty());
                var page = CorePages.specs(choices,actions).getFirst().build(context);
                try {
                    var component = page.component(); component.setSize(800,900); KitTestSupport.layout(component);
                    var source = KitTestSupport.require(component,"ui.appearance:desktop-source.input",javax.swing.JComboBox.class);
                    f.nativeInput.unavailable(DesktopObservation.Source.QT6,"Qt 6","6.2.4","unavailable"); f.drain();
                    f.nativeInput.palette(DesktopObservation.Source.QT5,true,"0.1,0.1,0.1,1",""); f.drain();
                    f.nativeInput.portal(2,true,",\"accent\":\"1,1,0,1\",\"highContrast\":true"); f.drain();
                    assertThat(DesktopPaintPairs.validate(f.themes.current().chrome(),true)).isEmpty();
                    assertThat(page.component()).isSameAs(component);
                    assertThat(KitTestSupport.require(component,"ui.appearance:desktop-source.input",javax.swing.JComboBox.class)).isSameAs(source);
                    assertThat(draft.modified()).isTrue();
                    assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
                    assertThat(draft.edit().changes()).containsOnlyKeys(CoreSettings.WINDOW_TAB_HEIGHT);
                    var image = new java.awt.image.BufferedImage(800,900,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var g=image.createGraphics();try{component.paint(g);}finally{g.dispose();}
                    var output=java.nio.file.Files.createDirectories(Path.of("build/desktop-colors"));
                    javax.imageio.ImageIO.write(image,"png",output.resolve("dirty-settings-high-contrast.png").toFile());
                } finally {page.dispose();}
            } catch(Exception failure) {throw new AssertionError(failure);}
        });
    }
}
