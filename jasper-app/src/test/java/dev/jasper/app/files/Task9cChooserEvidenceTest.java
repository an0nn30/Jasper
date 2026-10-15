package dev.jasper.app.files;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.*;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Actual chooser controllers, owned temporary files and inert remote connection promises. Whole states are unpictured. */
class Task9cChooserEvidenceTest {
    @TempDir Path home;
    @Test void modesFiltersPreviewAndConnectionStatesUseTheRealOwner() throws Exception {
        Files.createDirectories(home.resolve("empty"));
        Files.writeString(home.resolve("example.txt"),"Literal fixture preview\n<html>never markup\n");
        Files.write(home.resolve("binary.bin"),new byte[]{0,1,2,3});
        Files.createDirectories(home.resolve("a-long-directory-name").resolve("another-long-component"));
        SwingUtilities.invokeAndWait(()->{
            var themes=ThemeTestSupport.controller(true);
            try {
                for (boolean themeDark : new boolean[]{true, false})for(float font:List.of(13f,20f,32f)) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));
                    var sourceKit=themes.appearance().context(ThemeRoute.CHROME);
                    var kit=dev.jasper.ui.swing.KitContext.of(sourceKit.kit(), sourceKit.binding(), sourceKit.route(), sourceKit.icons(), ()->false, action -> "");
                    String family=(themeDark?"dark":"light")+"-"+font;
                    for(var mode:ChooserRequest.Mode.values()) {
                        var request=new ChooserRequest("Synthetic chooser",mode,mode==ChooserRequest.Mode.OPEN,Location.local(home),Optional.empty(),Optional.of("example.txt"),Optional.empty(),Optional.empty());
                        var prefs=new ChooserPrefs(UiState.inMemory());
                        var model=new ChooserModel(request,List.of(new LocalSource(new LocalBrowser(home))),Runnable::run,Runnable::run,false);
                        var completed=new ArrayList<List<Location>>();var replace=new java.util.concurrent.atomic.AtomicInteger();
                        var panel=new FileChooserPanel(kit,model,prefs,home,completed::add,location->{replace.incrementAndGet();return false;},()->Optional.of("empty"));
                        try {
                            model.start();save(panel,family,mode+"-fresh-narrow-short",600,320);save(panel,family,mode+"-populated",920,560);
                            save(panel,family,mode+"-narrow-short",600,320);
                            KitTestSupport.require(panel,"native.surface.scroll",JScrollPane.class).getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                            save(panel,family,mode+"-narrow-short-scrolled",600,320);
                            if(mode==ChooserRequest.Mode.SAVE) {
                                var input=panel.nameInput().orElseThrow();var document=input.getDocument();input.select(1,4);
                                themes.configure(!themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH,new UiFontConfig("system",font));
                                assertThat(panel.nameInput().orElseThrow().getDocument()).isSameAs(document);
                                assertThat(input.getSelectedText()).isEqualTo("xam");
                                panel.approve();assertThat(replace).hasValue(1);assertThat(completed).isEmpty();
                                save(panel,family,"SAVE-replace-declined",920,560);
                                themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));
                            }
                            model.setFilter("no matching fixture");save(panel,family,mode+"-filtered-empty",920,560);
                            assertThat(panel.centerMessage()).isPresent();model.setFilter("");
                            model.navigate(Location.local(home.resolve("empty")));save(panel,family,mode+"-empty",920,560);
                            assertThat(panel.centerMessage()).contains("This folder is empty.");
                            model.navigate(Location.local(home));
                            if(mode==ChooserRequest.Mode.SAVE) {
                                KitTestSupport.require(panel,"newFolder",AbstractButton.class).doClick(0);
                                save(panel,family,mode+"-folder-failure",920,560);
                            }
                            panel.editPath();save(panel,family,mode+"-edit-location",600,420);
                            if(mode==ChooserRequest.Mode.OPEN) {
                                if(!ChooserKit.previewToggle(panel).isSelected())ChooserKit.previewToggle(panel).doClick(0);
                                ChooserKit.selectRow(panel,ChooserKit.rows(panel).indexOf("example.txt"));save(panel,family,"OPEN-text-preview",920,560);
                                assertThat(ChooserKit.previewToggle(panel).isSelected()).isTrue();assertThat(prefs.preview()).isTrue();
                                var toggle=ChooserKit.previewToggle(panel);
                                var painted=new java.awt.image.BufferedImage(toggle.getWidth(),toggle.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                var graphics=painted.createGraphics();toggle.printAll(graphics);graphics.dispose();
                                int accent=kit.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT).getRGB();
                                assertThat(java.util.Arrays.stream(painted.getRGB(0,0,painted.getWidth(),painted.getHeight(),null,0,painted.getWidth())).filter(pixel->pixel==accent).count())
                                    .as("settled checked switch paints its receiving accent track").isPositive();
                                save(panel,family,"OPEN-text-preview-narrow",600,320);
                                KitTestSupport.require(panel,"native.surface.scroll",JScrollPane.class).getVerticalScrollBar().setValue(Integer.MAX_VALUE);
                                save(panel,family,"OPEN-text-preview-narrow-scrolled",600,320);
                                ChooserKit.selectRow(panel,ChooserKit.rows(panel).indexOf("binary.bin"));save(panel,family,"OPEN-binary-preview",920,560);
                            }
                        } finally {panel.close();model.close();}
                    }
                    // The chooser's own sheets (replace, new folder) through a modal that captures them as shown.
                    var prompts=new dev.jasper.app.windows.ChooserPrompts(()->kit,title->new dev.jasper.app.windows.ChooserPrompts.Modal(){
                        JComponent content;
                        @Override public void setContent(JComponent value){content=value;}
                        @Override public void show(Runnable shown){
                            shown.run();
                            // A modal packs the sheet at its width: lay it out there before reading its height.
                            content.setSize(480,2000);for(int i=0;i<3;i++)KitTestSupport.layout(content);
                            save(content,family,"prompt-"+title.replace(' ','-').toLowerCase(java.util.Locale.ROOT),480,content.getPreferredSize().height);
                        }
                        @Override public void close(){}
                    });
                    assertThat(prompts.confirmReplace("a-long-literal-file-name <html>.txt")).isFalse();
                    assertThat(prompts.askFolderName()).isEmpty();
                    var attempts=new ArrayList<CompletableFuture<Browser>>();
                    ChooserSource remote=new ChooserSource(){
                        public String id(){return "fixture.remote";}public String heading(){return "Remote fixture";}
                        public List<Root> roots(){return List.of(new Root("host","Long synthetic host label","fixture.invalid",false));}
                        public Runnable onChanged(Runnable listener){return ()->{};}
                        public CompletableFuture<Browser> connect(String root,Optional<UUID> owner){var result=new CompletableFuture<Browser>();attempts.add(result);return result;}
                    };
                    var request=new ChooserRequest("Synthetic remote",ChooserRequest.Mode.OPEN,false,Location.home(remote.id(),"host"),Optional.empty(),Optional.empty(),Optional.of(".txt"),Optional.empty());
                    var model=new ChooserModel(request,List.of(remote),Runnable::run,Runnable::run,false);
                    var panel=new FileChooserPanel(kit,model,new ChooserPrefs(UiState.inMemory()),home,ignored->{},ignored->false,Optional::empty);
                    try {
                        model.start();save(panel,family,"connecting",920,560);
                        attempts.getLast().completeExceptionally(new IllegalStateException("Synthetic connection failure; no remote connection attempted"));
                        save(panel,family,"connection-error-retry",920,560);
                        KitTestSupport.require(panel,"center",AbstractButton.class).doClick(0);
                        assertThat(attempts).hasSize(2);save(panel,family,"retry-connecting",920,560);
                        panel.close();model.close();attempts.getLast().completeExceptionally(new IllegalStateException("late synthetic failure"));
                    } finally {panel.close();model.close();}
                }
            } finally {themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());themes.close();}
        });
    }
    private static void save(JComponent panel,String theme,String state,int width,int height) {
        var path=Path.of("build/theme-surfaces9c/chooser",theme,state+".png");
        SurfaceEvidence.save(path,panel,width,height,2);
        try {Files.writeString(path.resolveSibling(path.getFileName()+".txt"),"fixtureAnimations=false; settled owner state\n",java.nio.file.StandardOpenOption.APPEND);}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
