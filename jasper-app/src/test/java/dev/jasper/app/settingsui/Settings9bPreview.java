package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.*;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.*;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** Headless, actual Settings owners and bundled declarations. Captures never imply native acceptance. */
public final class Settings9bPreview {
    private Settings9bPreview() {}
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]),home=Files.createTempDirectory("jasper-settings9b-");
        boolean allPages=java.util.Arrays.asList(args).contains("--all-pages");
        var holder=new JComponent[1];
        var windows=new AuxiliaryWindows(UiState.inMemory(),surface->{holder[0]=surface.holder();return new AuxiliarySurface.Shell(()->{},()->{},()->{},title->{},()->new Rectangle(0,0,980,681),(title,initial)->Optional.empty());});
        var themes=new ThemeController[1];var runtime=new PluginRuntime[1];var manager=new PluginManager[1];var contributions=new Contributions();
        try {
            if(!allPages)Files.writeString(home.resolve("plugins.toml"),"version = 1\n[plugins.\"dev.jasper.sample\"]\nenabled = false\nconsented = []\n");
            Path file=home.resolve("config.toml");Files.writeString(file,SettingsSurfacePreview.CONFIG);
           var parsed=ConfigLoader.parse(file,SettingsSurfacePreview.CONFIG,true);
            var state=new ConfigService.State(parsed.snapshot(),parsed.diagnostics(),file,true,parsed.legacyKeys(),parsed.deadTables(),parsed.values(),false);
            SwingUtilities.invokeAndWait(()->{
                themes[0]=ThemeTestSupport.controller(true);
                runtime[0]=new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),home.resolve("plugins"),null,false,home.resolve("plugins.toml"),home.resolve("plugins.lock")),new ActivityNotifier(new BuddyTestSupport().companion(),()->{}),PluginRuntime.SettingsReports.IGNORED,contributions,windows,new TerminalRegistry());runtime[0].start(Map.of(),true);
                // The application's own contributed entries, registered as JasperApplication registers them.
                contributions.addAction("plugins.manage","Manage Plugins…",null,List.of("plugins","extensions","install","safe mode"),Optional.empty(),invocation->{});
                contributions.addAction("app.shortcuts","Keyboard Shortcuts…",null,List.of("help","keys","bindings","keymap","edit shortcuts","keyboard settings"),Optional.empty(),invocation->{});
                var restarts=new RestartFlow(ResidentControl.NONE,mode->true,Runnable::run,Runnable::run,Duration.ofMillis(20),Duration.ofMillis(1));
                manager[0]=new PluginManager(runtime[0],windows,new PluginManager.Hooks(surface->Optional.empty(),restarts,()->{},ResidentControl.NONE,false,Runnable::run,path->{},path->{},uri->{}));
            });
            for(String family:List.of("dark","light"))for(int font:List.of(0,20)) {
                var dialog=new SettingsDialog[1];
                SwingUtilities.invokeAndWait(()->{
                    themes[0].configure(family.startsWith("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));
                    var groups=List.of(group(runtime[0],"jasper.general","Jasper","Jasper","jasper"),group(runtime[0],"jasper.commands","Long-running commands","Jasper","jasper"),group(runtime[0],"dev.jasper.editor","Editor","Editor","dev.jasper.editor"),group(runtime[0],"dev.jasper.editor.files","Files","Editor","dev.jasper.editor"));
                    // One saved override (fixture) so a section shows its modified mark, as the reference does.
                    var hooks=new KeymapPage.Hooks(contributions,()->Map.of("toggle_zen_mode","ctrl+cmd+f"),()->Map.of("dev.jasper.editor","Editor"),true,edits->CompletableFuture.completedFuture(null));
                    var pages=new CorePages.Pages(context->new KeymapPage(hooks,context),context->manager[0].page(context),context->runtime[0].settingsPages(context));
                    dialog[0]=new SettingsDialog(windows,CorePages.pages(AppearanceFixtures.choices(()->Optional.of(false)),new CorePages.SystemActions(()->file,()->{},()->{},()->{},()->{},()->false, java.util.Optional.empty()),ShellChoices.NONE,new NotificationGroupChoices(()->groups),pages),new SettingsDialog.Hooks(edit->CompletableFuture.completedFuture(state),()->state,listener->new Subscription(()->{}),()->{},()->{},uri->{},(parent,count)->true,()->themes[0].appearance().context(ThemeRoute.CHROME)));
                    dialog[0].open(null,"plugins");
                });
                // Serial admin barrier and EDT callback: the real manager's preceding refresh has published.
                var ready=new CompletableFuture<Void>();runtime[0].snapshot(outcome->ready.complete(null));ready.get(10,TimeUnit.SECONDS);
                SwingUtilities.invokeAndWait(()->{
                    var d=dialog[0];Path dir=output.resolve(family+"-"+font);
                    try {
                        if(allPages) {
                            var inventory=new StringBuilder("id\ttitle\tactual owner\treference disposition\n");
                            var ids=d.pages().stream().map(SettingsPage::id).collect(java.util.stream.Collectors.toSet());
                            if(ids.size()!=21 || ids.contains("plugin.dev.jasper.snippets"))throw new AssertionError("Expected14core +7activeTools: "+ids);
                            for(var page:d.pages()) {
                                d.select(page.id());
                                String disposition=List.of("appearance","window","notifications","keymap","plugins").contains(page.id())?"pictured owner; separate source-state comparison":"unpictured actual owner viability";
                                inventory.append(page.id()).append('\t').append(page.title()).append('\t').append(page.getClass().getName()).append('\t').append(disposition).append('\n');
                                for(var size:List.of(new Dimension(980,681),new Dimension(640,681),new Dimension(980,400)))
                                    SettingsSurfacePreview.save(d,holder[0],dir.resolve("all-pages").resolve(page.id()+"-"+size.width+"x"+size.height+".png"),size.width,size.height);
                            }
                            var pluginPage=d.pages().stream().filter(page->page.id().equals("plugins")).findFirst().orElseThrow();
                            d.select("plugins");
                            for(String name:List.of("Credential Vault","Editor","File Server","Project Index","Remote","Sample","Shell History","Snippets")) {
                                pluginPage.highlight(name);
                                SettingsSurfacePreview.save(d,holder[0],dir.resolve("all-identities").resolve(name.replace(' ','-')+".png"),980,682);
                            }
                            for(var stub:dev.jasper.app.plugins.SettingsStubTestAccess.pages(()->themes[0].appearance().context(ThemeRoute.CHROME))) {
                                try {
                                    var component=stub.component();component.setSize(600,300);KitTestSupport.layout(component);
                                    var image=new java.awt.image.BufferedImage(1200,600,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.scale(2,2);component.printAll(g);}finally{g.dispose();}
                                    Path fileOut=dir.resolve("conditional-stubs").resolve(stub.id()+".png");Files.createDirectories(fileOut.getParent());javax.imageio.ImageIO.write(image,"png",fileOut.toFile());
                                    inventory.append(stub.id()).append('\t').append(stub.title()).append('\t').append(stub.getClass().getName()).append("\tunpictured conditional actual owner; isolated600x300 device2\n");
                                }finally{stub.dispose();}
                            }
                            Files.writeString(dir.resolve("all-pages/inventory.tsv"),inventory);
                            return;
                        }
                        d.pages().stream().filter(page->page.id().equals("plugins")).findFirst().orElseThrow().highlight("Credential Vault");
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("plugins-full.png"),980,682);
                        crop(dir.resolve("plugins-full.png"),dir.resolve("plugins-content.png"),472,0,1488,1364);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("plugins-narrow.png"),640,681);
                        var primary=KitTestSupport.require(d.view(),"primary",AbstractButton.class);
                        var tab=new java.awt.event.FocusEvent(primary,java.awt.event.FocusEvent.FOCUS_GAINED,false,null,java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
                        for(var listener:primary.getFocusListeners())listener.focusGained(tab);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("plugins-narrow-action.png"),640,681);
                        var sections=KitTestSupport.require(d.view(),"section.option.details",AbstractButton.class);
                        sections.scrollRectToVisible(new Rectangle(0,0,sections.getWidth(),sections.getHeight()));
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("plugins-narrow-detail.png"),640,681);
                        d.view().body.getViewport().setViewPosition(new Point());
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("plugins-short.png"),980,400);
                        d.select("notifications");SettingsSurfacePreview.save(d,holder[0],dir.resolve("notifications.png"),980,681);
                        d.select(PluginRuntime.settingsPageId("dev.jasper.editor"));SettingsSurfacePreview.save(d,holder[0],dir.resolve("tools-editor.png"),980,681);
                        d.select("keymap");SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-collapsed.png"),980,681);
                        click(KitTestSupport.require(d.view(),"@tree/shortcuts/group/Application/title",JLabel.class));
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap.png"),980,681);
                        click(KitTestSupport.require(d.view(),"@tree/shortcuts/action/new_window/title",JLabel.class));
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-recording.png"),980,681);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-narrow.png"),640,681);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-short.png"),980,400);
                        var keymap=(KeymapPage)d.pages().stream().filter(page->page.id().equals("keymap")).findFirst().orElseThrow();
                        KitTestSupport.require(d.view(),"editor.record",AbstractButton.class).doClick(0);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-stopped.png"),980,681);
                        KitTestSupport.require(d.view(),"editor.record",AbstractButton.class).doClick(0);
                        dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.key(keymap,java.awt.event.KeyEvent.VK_F12,java.awt.event.InputEvent.META_DOWN_MASK|java.awt.event.InputEvent.SHIFT_DOWN_MASK|java.awt.event.InputEvent.ALT_DOWN_MASK);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-captured.png"),980,681);
                        var popup=KitTestSupport.require(d.view(),"editor.options",JComponent.class).getComponentPopupMenu();
                        SettingsSurfacePreview.save(d,popup,dir.resolve("keymap-actions-popup.png"),popup.getPreferredSize().width,popup.getPreferredSize().height);
                        dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.action(keymap,"Record any key…");
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-literal-waiting.png"),980,681);
                        dev.jasper.app.shortcuthelp.ShortcutPreviewSupport.key(keymap,java.awt.event.KeyEvent.VK_ENTER,0);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-literal-return.png"),980,681);
                        KitTestSupport.require(d.view(),"editor.set",AbstractButton.class).doClick(0);
                        SettingsSurfacePreview.save(d,holder[0],dir.resolve("keymap-staging-busy.png"),980,681);
                    } catch(java.io.IOException failure) {throw new java.io.UncheckedIOException(failure);} finally {d.cancel();}
                });
            }
        } finally {
            if(runtime[0]!=null){var done=new CompletableFuture<List<CompletableFuture<?>>>();SwingUtilities.invokeAndWait(()->done.complete(runtime[0].stop()));CompletableFuture.allOf(done.get().toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);}
            SwingUtilities.invokeAndWait(()->{windows.close();if(themes[0]!=null)themes[0].close();});
            try(var paths=Files.walk(home)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static NotificationGroupChoices.Group group(PluginRuntime runtime,String id,String name,String owner,String ownerId) {
        return new NotificationGroupChoices.Group(id,name,owner,"card",ownerId,"plugin",Optional.ofNullable(runtime.pluginIcons().get(ownerId)).map(PluginRuntime.PluginIcon::artwork),Optional.ofNullable(runtime.pluginIcons().get(ownerId)).map(icon->icon.identityArtwork().tileHue()).orElse(dev.jasper.ui.model.Nodes.Hue.NEUTRAL),Optional.ofNullable(runtime.pluginIcons().get(ownerId)).map(icon->icon.identityArtwork().selectedInk()).orElse(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED));
    }
    private static void click(JComponent component) {
        component.dispatchEvent(new MouseEvent(component,MouseEvent.MOUSE_CLICKED,0,0,Math.max(1,component.getWidth()/2),Math.max(1,component.getHeight()/2),1,false,MouseEvent.BUTTON1));
    }
    private static void crop(Path source,Path target,int x,int y,int width,int height) {
        try{var image=javax.imageio.ImageIO.read(source.toFile());javax.imageio.ImageIO.write(image.getSubimage(x,y,width,height),"png",target.toFile());}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
