package dev.jasper.app.windows;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.SurfaceFixtures;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.plugins.KitViewTestAccess;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Actual staged owners through the production chrome/attachment helpers. No native peers are created. */
class VaultSheetRenderTest {
    @TempDir Path home;
    @Test void mountedTitleMenuAndAttachedFormsRenderAcrossThemesFontsAndShortOwners() throws Exception {
        var controller = SurfaceFixtures.edt(ThemeController::new);
        try {
            for (String state : List.of("calibration", "dark", "light", "custom", "enlarged", "short")) {
                Theme selected = state.equals("light") ? Theme.JASPER_LIGHT : Theme.DARK;
                if (state.equals("calibration")) {
                    Path file = Path.of("src/test/resources/dev/jasper/app/appearance/desktop-vault.toml");
                    selected = Theme.of("task4-vault", new Theme.UserFile(file, dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(file), file.toString())));
                }
                if (state.equals("custom")) {
                    Path file = Path.of("src/test/resources/dev/jasper/app/appearance/custom-hero.toml");
                    selected = Theme.of("task4-custom", new Theme.UserFile(file, dev.jasper.ui.themeio.ThemeToml.parse(Files.readString(file), file.toString())));
                }
                Theme install = selected;
                SurfaceFixtures.edt(() -> {controller.configure(install, state.equals("enlarged") ? new UiFontConfig("system",20f) : UiFontConfig.defaults()); return null;});
                for (String id : List.of("vault-manager", "vault-login", "vault-key")) {
                    try(var fixture = new KitViewTestAccess(home.resolve(state+"-"+id), id, SurfaceFixtures.edt(controller::current))) {
                        SurfaceFixtures.edt(() -> {render(fixture.surface(),state,id);return null;});
                    }
                }
            }
        } finally { SurfaceFixtures.edt(() -> {controller.configure(Theme.DARK,UiFontConfig.defaults());controller.close();return null;}); }
    }
    private static void render(AuxiliarySurface surface,String state,String id) {
        boolean editor = surface.attachedSheet();var manager = editor ? surface.ownerSurface().orElseThrow() : surface;
        var root = new JRootPane();var child = new JRootPane();
        try(var chrome=NativeShells.installChrome(root,manager,true)) {
            int width = state.equals("enlarged") ? 1350 : 900;
            int height = state.equals("short") ? 45 : state.equals("enlarged") ? 833 : 555;
            root.setSize(width,height+chrome.bar().getPreferredSize().height);layout(root);
            assertThat(chrome.body().boundsInRoot().orElseThrow().height).isEqualTo(height);
            if (!editor) {
                capture(state,id,root,null,null,"body="+chrome.body().boundsInRoot().orElseThrow());
                var menu = find(manager.titleActions(),"add").getComponentPopupMenu();menu.setSize(menu.getPreferredSize());layout(menu);
                capture(state,"vault-menu",menu,null,null,"actual title MenuButton descriptor; headless menu rendering");
                if(state.equals("short")) {
                    var done=(JButton)find(manager.holder(),"okay");
                    for(var listener:done.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(done,java.awt.event.FocusEvent.FOCUS_GAINED));
                    layout(root);
                    assertThat(done.getVisibleRect().height).isEqualTo(done.getHeight());
                    capture(state,"vault-manager-footer-focused",root,null,null,"actual mounted WINDOW after footer focus listener; full action visible at45logical; below-action viewport remains explicitly constrained");
                }
                return;
            }
            try(var sheetChrome=NativeShells.installChrome(child,surface,true)) {
                var bounds = new java.util.concurrent.atomic.AtomicReference<Rectangle>();
                try(var sheet=NativeShells.attachSheet(surface,chrome.body(),new AttachedSheet.Target(()->true,Point::new,
                        ()->new Rectangle(0,0,width,Math.max(900,height+100)),value->{bounds.set(value);child.setSize(value.getSize());layout(child);}))) {
                    sheet.fitNow();layout(child);
                    assertThat(sheet.fallback()).isEqualTo(state.equals("short"));
                    capture(state,id,root,child,bounds.get(),"body="+chrome.body().boundsInRoot().orElseThrow()+"\nsheet="+bounds.get()+"\nfallback="+sheet.fallback());
                    capture(state,id+"-sheet",child,null,null,"same actual mounted attached sheet; no native shadow/peer");
                }
            }
        }
    }
    private static void capture(String state,String id,JComponent root,JComponent child,Rectangle at,String metadata) {
        int width=Math.max(root.getWidth(),at==null?0:at.x+at.width),height=Math.max(root.getHeight(),at==null?0:at.y+at.height);
        var image=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
        try {g.scale(2,2);root.printAll(g);if(child!=null){g.translate(at.x,at.y);child.printAll(g);}} finally {g.dispose();}
        var text=new StringBuilder("provenance=real staged Vault + production NativeShells chrome/attachment; headless only\ndeviceScale=2\nuiScale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor()+"\nlogicalSize="+width+"x"+height+"\n"+metadata+"\n");
        describe(root,root,text);if(child!=null)describe(child,child,text);
        try {var output=Files.createDirectories(Path.of("build/task-4b-captures",state));ImageIO.write(image,"png",output.resolve(id+".png").toFile());Files.writeString(output.resolve(id+".txt"),text);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static void describe(Container component,JComponent root,StringBuilder text){
        if(component instanceof JComponent c && c.getName()!=null) text.append(c.getName()).append(" bounds=").append(SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root)).append(" font=").append(c.getFont()).append(" baseline=").append(c.getWidth() < 0 || c.getHeight() < 0 ? -1 : c.getBaseline(c.getWidth(),c.getHeight())).append('\n');
        for(var child:component.getComponents())if(child instanceof Container c)describe(c,root,text);
    }
    private static JComponent find(Container root,String name){if(name.equals(root.getName()))return (JComponent)root;for(var child:root.getComponents())if(child instanceof Container c){var found=find(c,name);if(found!=null)return found;}return null;}
    private static void layout(Container root){root.doLayout();for(var child:root.getComponents())if(child instanceof Container c)layout(c);}
}
