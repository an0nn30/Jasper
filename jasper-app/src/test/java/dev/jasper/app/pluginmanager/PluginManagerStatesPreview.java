package dev.jasper.app.pluginmanager;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.*;
import dev.jasper.app.settingsui.*;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.*;
import dev.jasper.app.windows.*;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real runtime, manager, owned dialogs and RestartFlow; only process/native-shell boundaries are inert. */
public final class PluginManagerStatesPreview {
    private PluginManagerStatesPreview() {}
    public static void main(String[] args) throws Exception { capture(Path.of(args[0])); }
    public static void capture(Path output) throws Exception {
        Path home=Files.createTempDirectory("jasper-plugin-states-");
        var runtime=new PluginRuntime[1];var controller=new ThemeController[1];var windows=new AuxiliaryWindows[1];
        var manager=new PluginManager[1];var page=new SettingsPage[1];var flow=new RestartFlow[1];var workers=new ArrayDeque<Runnable>();
        String id="dev.example.review";
        try {
            PluginJars.build(home.resolve("plugins").resolve(id).resolve("jars"),"main.jar",PluginJars.descriptor(id,"1.0.0","fix.Main")+"capabilities = [\"terminal.observe\"]\n",Map.of(),List.of());
            Path config=home.resolve("config.toml");Files.writeString(config,"");
           
            SwingUtilities.invokeAndWait(()->{
                controller[0]=ThemeTestSupport.controller(true);
                windows[0]=new AuxiliaryWindows(UiState.inMemory(),surface->new AuxiliarySurface.Shell(()->{},()->{},()->{},title->{},()->new Rectangle(0,0,744,600),(title,initial)->Optional.empty()));
                runtime[0]=new PluginRuntime(new PluginRuntime.Options(null,home.resolve("plugins"),null,true,home.resolve("plugins.toml"),home.resolve("plugins.lock")),new ActivityNotifier(new BuddyTestSupport().companion(),()->{}),PluginRuntime.SettingsReports.IGNORED,new Contributions(),windows[0],new TerminalRegistry());runtime[0].start(Map.of(),true);
                var resident=new ResidentControl(()->true,()->false);
                flow[0]=new RestartFlow(resident,mode->false,workers::add,Runnable::run,Duration.ofMillis(20),Duration.ofMillis(1));
                manager[0]=new PluginManager(runtime[0],windows[0],new PluginManager.Hooks(surface->Optional.empty(),flow[0],()->{},resident,true,Runnable::run,path->{},path->{},uri->{}));
                var host=windows[0].window("app.settings","Settings",new Dimension(744,600),true);
                page[0]=manager[0].page(new PageContext(null,value->{},Runnable::run,(title,initial)->Optional.empty(),()->{},()->host,()->controller[0].appearance().context(ThemeRoute.CHROME)));
                host.setContent(page[0].component());page[0].selected();
            });
            var ready=new CompletableFuture<Void>();runtime[0].snapshot(outcome->ready.complete(null));ready.get(10,TimeUnit.SECONDS);
            for(String family:List.of("dark","light"))for(int font:List.of(0,20)) {
                SwingUtilities.invokeAndWait(()->{
                    controller[0].configure(family.startsWith("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));var m=manager[0];var panel=m.panel();Path dir=output.resolve(family+"-"+font);
                    panel.select(id);save(panel,dir.resolve("safe-mode-notice.png"),744,600);
                    var menu=PluginKit.rowMenu(panel,id);save(menu,dir.resolve("row-menu.png"),menu.getPreferredSize().width,menu.getPreferredSize().height);
                    PluginKit.press(panel.detailsPane().header(),PluginDetails.PRIMARY);
                    if(m.consent()==null)throw new AssertionError("Actual review did not open consent");
                    save(windows[0].open().getLast().holder(),dir.resolve("consent.png"),540,480);
                    save(windows[0].open().getLast().holder(),dir.resolve("consent-narrow.png"),360,480);
                    PluginKit.press(m.consent().view(),ConsentView.CANCEL);
                    PluginKit.clickMenu(panel,"Remove\u2026");
                    if(m.confirm()==null)throw new AssertionError("Actual remove did not open confirmation");
                    save(windows[0].open().getLast().holder(),dir.resolve("remove.png"),540,420);
                    save(windows[0].open().getLast().holder(),dir.resolve("remove-short.png"),540,240);
                    PluginKit.press(m.confirm().view(),ConfirmView.CANCEL);
                    flow[0].restartNormally();save(panel,dir.resolve("restart-probing.png"),744,600);
                    workers.remove().run();save(panel,dir.resolve("restart-resident-found.png"),744,600);
                    flow[0].askResidentToQuit();save(panel,dir.resolve("restart-waiting.png"),744,600);
                    workers.remove().run();save(panel,dir.resolve("restart-stuck.png"),744,600);save(panel,dir.resolve("restart-stuck-narrow.png"),440,400);
                    flow[0].launchAnyway();save(panel,dir.resolve("restart-unavailable.png"),744,600);flow[0].cancel();
                });
            }
        } finally {
            if(runtime[0]!=null){var pluginWorkers=dev.jasper.app.plugins.PluginShutdownTestSupport.capture(runtime[0]);var done=new CompletableFuture<List<CompletableFuture<?>>>();SwingUtilities.invokeAndWait(()->done.complete(runtime[0].stop()));CompletableFuture.allOf(done.get().toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);pluginWorkers.await();}
            SwingUtilities.invokeAndWait(()->{if(page[0]!=null)page[0].dispose();if(windows[0]!=null)windows[0].close();if(controller[0]!=null)controller[0].close();});
            try(var paths=Files.walk(home)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static void save(JComponent component,Path path,int width,int height) {
        try {
            component.setSize(width,height);KitTestSupport.layout(component);KitTestSupport.layout(component);
            var image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.scale(2,2);component.printAll(g);g.dispose();
            Files.createDirectories(path.getParent());ImageIO.write(image,"png",path.toFile());
            Files.writeString(path.resolveSibling(path.getFileName()+".txt"),"Actual PluginManager-owned component; 2x headless; native/pixel acceptance pending.\n"+String.join("\n",KitTestSupport.texts(component))+"\n");
        } catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
