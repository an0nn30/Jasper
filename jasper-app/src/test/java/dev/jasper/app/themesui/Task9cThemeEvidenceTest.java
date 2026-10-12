package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.*;
import dev.jasper.app.themeimport.ThemeImporter;
import dev.jasper.app.windows.*;
import dev.jasper.ui.theme.ThemeRoute;
import dev.jasper.ui.themeimport.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Actual ThemeDialogs lifecycle with actual import controller; no theme files are published by these captures. */
class Task9cThemeEvidenceTest {
    @Test void importsAndReportsKeepTheirOwnedSheetsLiteralAndClosable() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var themes=new ThemeController();
            var windows=new AuxiliaryWindows(UiState.inMemory(),surface->new AuxiliarySurface.Shell(()->{},()->{},()->{},title->{},()->new Rectangle(0,0,680,440),(title,initial)->Optional.empty()));
            var owner=windows.window("settings","Settings",new Dimension(980,681),false);owner.show();
            try {
                for(var theme:List.of(Theme.DARK,Theme.JASPER_LIGHT))for(float font:List.of(13f,32f)) {
                    themes.configure(theme,new UiFontConfig("system",font));
                    String family=(theme.dark()?"dark":"light")+"-"+font;
                    try(var dialogs=new ThemeDialogs(windows,()->owner,()->themes.appearance().context(ThemeRoute.CHROME))) {
                        var conversion=new ConversionResult(Theme.DARK.source().document(),List.of("A long provider warning retains literal <html> text and explains the actual fallback without widening the dialog"),"archive/provider/a-long-source-entry-name.toml");
                        var old=new ThemeImporter.Item("old",conversion,Optional.of(Path.of("existing.toml")),Optional.of("hash"),false);
                        var fresh=new ThemeImporter.Item("fresh",conversion,Optional.empty(),Optional.empty(),true);
                        var accepted=new java.util.concurrent.atomic.AtomicInteger();var cancelled=new java.util.concurrent.atomic.AtomicInteger();
                        dialogs.view("Import Themes",(accept,reject)->new ThemeImportView(dialogs.kit(),new ThemeImporter.ImportPlan(List.of(old,fresh),List.of(new ThemeConverter.Failure("invalid provider","Literal parse failure"))),choices->accepted.incrementAndGet(),reject).view,cancelled::incrementAndGet);
                        var sheet=windows.open().getLast();save(sheet.holder(),family,"multi-provider",680,440);save(sheet.holder(),family,"narrow-short",400,300);
                        var combo=KitTestSupport.require(sheet.holder(),"decision:old.input",JComboBox.class);
                        var bodyScroll=KitTestSupport.require(sheet.holder(),"theme-import.scroll",JScrollPane.class);
                        bodyScroll.getVerticalScrollBar().setValue(SwingUtilities.convertPoint(combo,0,0,bodyScroll.getViewport().getView()).y);
                        combo.scrollRectToVisible(new Rectangle(0,0,combo.getWidth(),combo.getHeight()));
                        assertThat(combo.getVisibleRect().height).as("actual constrained import choice reachable").isPositive();
                        save(sheet.holder(),family,"narrow-short-choice",400,300);
                        var importAction=KitTestSupport.require(sheet.holder(),"import",JButton.class);
                        for(var listener:importAction.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(importAction,java.awt.event.FocusEvent.FOCUS_GAINED));
                        assertThat(importAction.getVisibleRect().height).as("actual constrained import action reachable").isPositive();
                        save(sheet.holder(),family,"narrow-short-actions",400,300);
                        var popup=(javax.swing.plaf.basic.ComboPopup)combo.getUI().getAccessibleChild(combo,0);
                        var menu=(JComponent)popup;menu.setSize(Math.max(1,combo.getWidth()),menu.getPreferredSize().height);
                        save(menu,family,"conflict-popup",Math.max(1,combo.getWidth()),Math.max(1,menu.getPreferredSize().height));
                        KitTestSupport.require(sheet.holder(),"import",JButton.class).doClick(0);
                        assertThat(accepted).hasValue(1);save(sheet.holder(),family,"committing",680,440);
                        sheet.close();assertThat(cancelled).hasValue(1);
                        var imported=new ThemeImporter.ImportReport(List.of(new ThemeImporter.Imported("example","Example",Path.of("/fixture/home/themes/example.toml")),
                            new ThemeImporter.Imported("example-light","Example Light with a long literal <html> name",Path.of("/fixture/home/themes/a-long-directory-name/example-light.toml"))),
                            List.of("One warning: fallback font preserved. No theme was selected."),List.of(new ThemeConverter.Failure("archive/broken.theme.json","Literal parse failure")));
                        dialogs.sheet(ThemeImport.reportTitle(imported),ThemeImport.report(imported));
                        save(windows.open().getLast().holder(),family,"success-report",680,440);
                        save(windows.open().getLast().holder(),family,"success-report-narrow",400,300);
                        windows.open().getLast().close();
                        var nothing=new ThemeImporter.ImportReport(List.of(),List.of(),List.of());
                        dialogs.sheet(ThemeImport.reportTitle(nothing),ThemeImport.report(nothing));
                        save(windows.open().getLast().holder(),family,"nothing-report",680,240);
                        windows.open().getLast().close();
                        dialogs.message("Theme Import Failed","<html>Literal failure\nThe selected archive could not be parsed.\nNo theme was installed or selected.");
                        save(windows.open().getLast().holder(),family,"error-report",400,300);
                        windows.open().getLast().close();
                    }
                }
            } finally {windows.close();themes.configure(Theme.DARK,UiFontConfig.defaults());themes.close();}
        });
    }
    private static void save(JComponent panel,String theme,String state,int width,int height) {
        SurfaceEvidence.save(Path.of("build/theme-surfaces9c/themes",theme,state+".png"),panel,width,height,2);
    }
}
