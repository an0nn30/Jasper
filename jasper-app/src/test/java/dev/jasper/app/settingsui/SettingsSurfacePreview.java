package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.*;
import dev.jasper.buddy.view.BuddyTestSupport;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exact 2x renders of the actual three Settings pages, their real dialog/draft and bundled plugin pages. No native window. */
public final class SettingsSurfacePreview {
    private SettingsSurfacePreview() { }
    public static final String CONFIG="""
        [ui]
        appearance = "dark"
        [ui.workspace]
        opacity = 90
        [window]
        toolbar = "icons_and_labels"
        toolbar_height = 34
        status_bar = true
        status_bar_height = 28
        tab_height = 28
        rail_width = 28
        columns = 129
        lines = 47
        [notifications]
        system = true
        long_command_seconds = 10
        """;
    public static void main(String[] args) throws Exception { capture(Path.of(args[0])); }
    public static void capture(Path output) throws Exception {
        Path home=Files.createTempDirectory("jasper-settings9a-");
        try {
            Path file=home.resolve("config.toml");Files.writeString(file,CONFIG);
           var parsed=ConfigLoader.parse(file,CONFIG,true);
            var state=new ConfigService.State(parsed.snapshot(),parsed.diagnostics(),file,true,parsed.legacyKeys(),parsed.deadTables(),parsed.values(),false);
            var views=new JComponent[1];var windows=new AuxiliaryWindows(UiState.inMemory(),surface->{views[0]=surface.holder();return new AuxiliarySurface.Shell(()->{},()->{},()->{},title->{},()->new Rectangle(0,0,980,681),(title,initial)->Optional.empty());});
            var controller=new ThemeController[1];var runtime=new PluginRuntime[1];
            SwingUtilities.invokeAndWait(()->{
                controller[0]=ThemeTestSupport.controller(true);
                runtime[0]=new PluginRuntime(new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")),home.resolve("plugins"),null,false,home.resolve("plugins.toml"),home.resolve("plugins.lock")),new ActivityNotifier(new BuddyTestSupport().companion(),()->{}),PluginRuntime.SettingsReports.IGNORED,new Contributions(),windows,new TerminalRegistry());runtime[0].start(Map.of(),true);
            });
            try {
                for(String family:List.of("dark","light"))for(int font:List.of(0,20)) {
                    SwingUtilities.invokeAndWait(()->{
                        controller[0].configure(family.startsWith("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system",font));
                        var groups=List.of(group("jasper.general","Jasper","card","Jasper","jasper","appearance"),group("jasper.config","Configuration","sticky","Jasper","jasper","appearance"),group("jasper.plugins","Plugin problems","card","Jasper","jasper","appearance"),group("jasper.internal","Internal errors","sticky","Jasper","jasper","appearance"),group("jasper.commands","Long-running commands","card","Jasper","jasper","appearance"),group("dev.jasper.editor","Editor","card","Editor","dev.jasper.editor","edit"),group("dev.jasper.editor.files","Files","card","Editor","dev.jasper.editor","edit"));
                        var pages=new CorePages.Pages(context->new GroupPage("keymap","Keymap",Optional.empty(),false,List::of,context),context->new GroupPage("plugins","Plugins",Optional.empty(),false,List::of,context),context->runtime[0].settingsPages(context));
                        var systemDark=new java.util.concurrent.atomic.AtomicReference<>(Optional.of(false));
                        var dialog=new SettingsDialog(windows,CorePages.pages(AppearanceFixtures.choices(systemDark::get),new CorePages.SystemActions(()->file,()->{},()->{},()->{},()->{},()->false, java.util.Optional.empty()),ShellChoices.NONE,new NotificationGroupChoices(()->groups),pages),new SettingsDialog.Hooks(edit->CompletableFuture.completedFuture(state),()->state,listener->new Subscription(()->{}),()->{},()->{},uri->{},(parent,count)->true,()->controller[0].appearance().context(ThemeRoute.CHROME)));
                        try {
                            dialog.open(null,"appearance");
                            for(String page:List.of("appearance","window","notifications","system","terminal.font")) {
                                dialog.select(page);Path directory=output.resolve("settings").resolve(family+"-"+font).resolve(page);
                                save(dialog,views[0],directory.resolve("default.png"),980,681);
                                save(dialog,views[0],directory.resolve("short.png"),980,400);
                                save(dialog,views[0],directory.resolve("narrow.png"),640,681);
                                if(page.equals("appearance")) {
                                    systemDark.set(Optional.empty());
                                    dialog.draft().stage(CoreSettings.UI_APPEARANCE,Optional.of(AppearanceMode.SYSTEM));
                                    save(dialog,views[0],directory.resolve("system-unavailable.png"),980,681);
                                    dialog.draft().reset(List.of(CoreSettings.UI_APPEARANCE));
                                    systemDark.set(Optional.of(false));
                                }
                                if(page.equals("notifications")){save(dialog,views[0],directory.resolve("popup-base.png"),980,681);popup(dialog,views[0],directory.resolve("popup.png"));}
                                if(page.equals("window")){
                                    var input=KitTestSupport.require(dialog.view(),"window.columns.input",JTextField.class);String saved=input.getText();input.setText("");
                                    save(dialog,views[0],directory.resolve("error.png"),980,681);input.setText(saved);
                                }
                            }
                        }finally{dialog.cancel();}
                    });
                }
            } finally {
                var stopping=new CompletableFuture<List<CompletableFuture<?>>>();SwingUtilities.invokeAndWait(()->stopping.complete(runtime[0].stop()));CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);
                SwingUtilities.invokeAndWait(()->{windows.close();controller[0].close();});
            }
        } finally {try(var paths=Files.walk(home)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static NotificationGroupChoices.Group group(String id,String name,String mode,String owner,String ownerId,String icon){return new NotificationGroupChoices.Group(id,name,owner,mode,ownerId,icon);}
    static void save(SettingsDialog dialog,JComponent view,Path file,int width,int height){
        view.setSize(width,height);for(int i=0;i<4;i++)KitTestSupport.layout(view);
        write(view,file,width,height);
        var text=new StringBuilder("actual SettingsDialog and page owner; deviceScale=2; uiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor()+"\n");
        describe(view,view,text);text.append("viewport=").append(dialog.view().body.getViewport().getExtentSize()).append("; view=").append(dialog.view().body.getViewport().getViewSize()).append('\n');
        try{Files.writeString(file.resolveSibling(file.getFileName()+".txt"),text);}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static void popup(SettingsDialog dialog,JComponent root,Path file){
        var combo=KitTestSupport.require(dialog.view(),"notifications.groups:group-1.input",JComboBox.class);
        var popup=(javax.swing.plaf.basic.ComboPopup)combo.getUI().getAccessibleChild(combo,0);var list=popup.getList();list.setSelectedIndex(combo.getSelectedIndex());
        JComponent content=(JComponent)popup;int width=combo.getWidth();
        content.setSize(width,1);KitTestSupport.layout(content);
        int height=list.getPreferredSize().height+content.getInsets().top+content.getInsets().bottom;
        content.setSize(width,height);KitTestSupport.layout(content);
        var anchor=SwingUtilities.convertPoint(combo,0,combo.getHeight(),root);
        var image=new BufferedImage(root.getWidth()*2,root.getHeight()*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try{g.scale(2,2);root.printAll(g);g.translate(anchor.x,anchor.y);content.printAll(g);}finally{g.dispose();}
        try{ImageIO.write(image,"png",file.toFile());Files.writeString(file.resolveSibling("popup-geometry.txt"),"Headless actual BasicComboPopup over actual SettingsDialog at combo lower edge; not a native window. Anchor="+anchor+"; size="+content.getSize()+"; selected="+list.getSelectedIndex()+"; preferredList="+list.getPreferredSize()+"; list="+list.getBounds()+" insets="+list.getInsets()+"; popupInsets="+content.getInsets()+"; cell="+((javax.swing.ListCellRenderer<Object>)combo.getRenderer()).getListCellRendererComponent(list,combo.getItemAt(0),0,false,false).getPreferredSize()+"; comboWidth="+combo.getWidth()+"; viewport="+javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,list).getBounds()+"\n");}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        write(content,file.resolveSibling("popup-only.png"),width,height);
    }
    private static void write(JComponent view,Path file,int width,int height){
        var image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.scale(2,2);view.printAll(g);}finally{g.dispose();}
        try{Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static void describe(Component component,JComponent root,StringBuilder text){
        if(component.isVisible()&&component.getName()!=null)text.append(component.getName()).append('=').append(SwingUtilities.convertRectangle(component.getParent(),component.getBounds(),root)).append("; font=").append(component.getFont()).append('\n');
        if(component instanceof Container container)for(var child:container.getComponents())describe(child,root,text);
    }
}
