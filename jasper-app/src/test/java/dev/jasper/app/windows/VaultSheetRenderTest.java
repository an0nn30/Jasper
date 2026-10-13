package dev.jasper.app.windows;

import dev.jasper.app.appearance.ThemeTestSupport;
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
        var controller = SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for (String state : List.of("dark", "light", "enlarged", "short")) {
                SurfaceFixtures.edt(() -> {controller.configure(!state.equals("light"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, state.equals("enlarged") ? new UiFontConfig("system",20f) : UiFontConfig.defaults()); return null;});
                for (String id : List.of("vault-manager", "vault-login", "vault-key")) {
                    try(var fixture = new KitViewTestAccess(home.resolve(state+"-"+id), id, SurfaceFixtures.edt(controller::current))) {
                        SurfaceFixtures.edt(() -> {render(fixture.surface(),state,id);return null;});
                    }
                }
            }
        } finally { SurfaceFixtures.edt(() -> {controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());controller.close();return null;}); }
    }
    /** Unlock and Create are modal overlays inside the asking window: centred over its dimmed content. */
    @Test void unlockAndCreateAreModalOverlaysCentredOverTheDimmedWindow() throws Exception {
        var controller = SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for (String state : List.of("dark", "light")) {
                // At rest: the motion itself is the kit's ModalOverlayTest.
                SurfaceFixtures.edt(() -> {controller.configure(!state.equals("light"), dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
                    controller.configureAnimations(false); return null;});
                for (String id : List.of("vault-unlock", "vault-create")) {
                    try (var fixture = new KitViewTestAccess(home.resolve("overlay-" + state + "-" + id), id, SurfaceFixtures.edt(controller::current))) {
                        SurfaceFixtures.edt(() -> {
                            var surface = fixture.surface();
                            assertThat(surface.kind()).as("%s is an overlay", id).isEqualTo(AuxiliarySurface.Kind.OVERLAY);
                            assertThat(surface.modal()).isTrue();
                            // A stand-in terminal window: a title row and a body with some text.
                            var owner = new JRootPane(); var body = new JPanel(new BorderLayout());
                            var text = new JLabel("  dustin@mac ~ % ssh dustin@192.168.1.120"); text.setForeground(new Color(200, 200, 200));
                            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13)); text.setVerticalAlignment(SwingConstants.TOP);
                            body.add(text); body.setBackground(state.equals("light") ? Color.WHITE : new Color(30, 31, 34));
                            if (state.equals("light")) text.setForeground(new Color(40, 40, 40));
                            var title = new JPanel(); title.setPreferredSize(new Dimension(900, 38)); title.setBackground(state.equals("light") ? new Color(236, 236, 236) : new Color(43, 45, 48));
                            owner.setContentPane(new JPanel(new BorderLayout()));
                            owner.getContentPane().add(title, BorderLayout.NORTH); owner.getContentPane().add(body, BorderLayout.CENTER);
                            owner.setSize(900, 600); layout(owner);
                            var overlay = new WindowOverlay(owner, surface.holder(), controller.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), true);
                            try {
                                overlay.show(); layout(owner);
                                Rectangle card = overlay.cardBounds();
                                assertThat(card.x * 2 + card.width).as("centred across the window").isEqualTo(900);
                                assertThat(Math.abs(card.y * 2 + card.height - 600)).as("and down it").isLessThanOrEqualTo(1);
                                capture(state, id + "-overlay", owner, null, null, "card=" + card);
                            } finally { overlay.close(); }
                            return null;
                        });
                    }
                }
            }
        } finally { SurfaceFixtures.edt(() -> {controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); controller.close(); return null;}); }
    }
    @Test void populatedManagerKeepsFullActionsReachableAtNarrowAndEnlargedWidths() throws Exception {
        var controller=SurfaceFixtures.edt(() -> ThemeTestSupport.controller(true));
        try {
            for(boolean enlarged:new boolean[]{false,true}) {
                SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, enlarged?new UiFontConfig("system",20f):UiFontConfig.defaults());return null;});
                try(var fixture=new KitViewTestAccess(home.resolve("narrow-"+enlarged),"vault-manager",SurfaceFixtures.edt(controller::current))) {
                    SurfaceFixtures.edt(()->{
                        var surface=fixture.surface();var root=new JRootPane();
                        try(var chrome=NativeShells.installChrome(root,surface,true)) {
                            var edit=(JButton)find(surface.holder(),"edit");var delete=(JButton)find(surface.holder(),"delete");
                            var search=(JTextField)find(surface.holder(),"search.input");search.setText(" ");var document=search.getDocument();
                            Rectangle originalHeading=null;
                            for(int width:new int[]{com.formdev.flatlaf.util.UIScale.scale(900),com.formdev.flatlaf.util.UIScale.scale(540),com.formdev.flatlaf.util.UIScale.scale(400),400,com.formdev.flatlaf.util.UIScale.scale(900)}) {
                                int logical=com.formdev.flatlaf.util.UIScale.unscale(width),height=com.formdev.flatlaf.util.UIScale.scale(555);
                                root.setSize(width,height+chrome.bar().getPreferredSize().height);layout(root);
                                assertThat(find(surface.holder(),"search.input")).isSameAs(search);assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                                if(logical==900) {
                                    var bounds=find(surface.holder(),"detail.heading").getBounds();
                                    if(originalHeading==null)originalHeading=new Rectangle(bounds);else assertThat(bounds).isEqualTo(originalHeading);
                                }
                                for(var button:List.of(delete,edit)) {
                                    assertThat(find(surface.holder(),button.getName())).isSameAs(button);
                                    assertThat(button.getWidth()).as("%s at %s enlarged=%s",button.getName(),logical,enlarged).isGreaterThanOrEqualTo(button.getPreferredSize().width);
                                    assertThat(button.isFocusable()).isTrue();
                                    assertThat(button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("pressed SPACE"))).isNotNull();
                                    var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                    for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,button));
                                    layout(root);assertThat(button.getVisibleRect()).isEqualTo(new Rectangle(0,0,button.getWidth(),button.getHeight()));
                                }
                                for(String rowId:List.of("detail.username","detail.password")) {
                                    var row=find(surface.holder(),rowId);var title=find(row,rowId+".title");
                                    var value=rowId.endsWith("password")?ending(row,".value"):find(row,rowId+".description");
                                    assertThat(value.getWidth()).as("visible detail value %s at %s",rowId,width).isGreaterThan(value.getFontMetrics(value.getFont()).charWidth('W'));
                                    var valueBounds=SwingUtilities.convertRectangle(value.getParent(),value.getBounds(),row);
                                    var titleInkBounds=new Rectangle(title.getBounds());titleInkBounds.width=Math.min(titleInkBounds.width,title.getPreferredSize().width);
                                    assertThat(titleInkBounds.intersects(valueBounds)).as("label/value separation %s at %s",rowId,width).isFalse();
                                    for(var action:List.of(ending(row,".copy"),rowId.endsWith("password")?ending(row,".reveal"):ending(row,".copy"))) {
                                        assertThat(action.getWidth()).as("full detail action %s at %s",action.getName(),width).isGreaterThanOrEqualTo(action.getPreferredSize().width);
                                        var actionBounds=SwingUtilities.convertRectangle(action.getParent(),action.getBounds(),row);
                                        assertThat(new Rectangle(0,0,row.getWidth(),row.getHeight()).contains(actionBounds)).isTrue();
                                        assertThat(actionBounds.intersects(titleInkBounds)).as("label/action separation %s at %s",action.getName(),width).isFalse();assertThat(actionBounds.intersects(valueBounds)).as("value/action separation %s at %s",action.getName(),width).isFalse();
                                        var focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                                        for(var listener:focus.getPropertyChangeListeners("focusOwner"))listener.propertyChange(new java.beans.PropertyChangeEvent(focus,"focusOwner",null,action));
                                        layout(root);assertThat(action.getVisibleRect()).isEqualTo(new Rectangle(0,0,action.getWidth(),action.getHeight()));
                                    }
                                }
                                capture("fix1-"+(enlarged?"enlarged":"normal"),"manager-width-"+width,root,null,null,"actual populated manager; Delete/Edit full bounds, focusability, keyboard activation binding and focus-owner scroll reveal asserted; no native traversal peer");
                            }
                            controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());fixture.theme(controller.current());
                            root.setSize(900,555+chrome.bar().getPreferredSize().height);layout(root);
                            assertThat(find(surface.holder(),"edit")).isSameAs(edit);assertThat(find(surface.holder(),"delete")).isSameAs(delete);
                            assertThat(search.getDocument()).isSameAs(document);assertThat(search.getText()).isEqualTo(" ");
                            assertThat(edit.getWidth()).isEqualTo(edit.getPreferredSize().width);
                            assertThat(delete.getWidth()).isEqualTo(delete.getPreferredSize().width);
                            assertThat(find(surface.holder(),"detail.heading").getHeight()).isEqualTo(44);
                        }
                        return null;
                    });
                }
            }
        }finally{SurfaceFixtures.edt(()->{controller.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());controller.close();return null;});}
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
                        ()->new Rectangle(0,0,width,Math.max(900,height+100)),value->{
                            bounds.set(value);
                            if (!child.getSize().equals(value.getSize())) { child.setSize(value.getSize()); layout(child); }
                        }))) {
                    sheet.fitNow(); sheet.fitNow();
                    assertThat(surface.holder().getSize()).isEqualTo(child.getSize());
                    for (String action : List.of("cancel", "save")) {
                        var button = find(surface.holder(), action);
                        assertThat(button).as("actual footer action %s", action).isNotNull();
                        if (!state.equals("short"))
                            assertThat(new Rectangle(child.getSize()).contains(SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), child)))
                                .as("native final allocation contains %s", action).isTrue();
                    }
                    assertThat(sheet.fallback()).isEqualTo(state.equals("short"));
                    if (id.equals("vault-login")) {
                        // Measured form segment: 24 logical item inside a 3-logical track inset (30 outer at default).
                        int segmentHeight = com.formdev.flatlaf.util.UIScale.scale(24);
                        assertThat(find(surface.holder(), "auth.option.password").getHeight())
                            .as("actual Login segment preserves its nonheader height in %s", state).isEqualTo(segmentHeight);
                        assertThat(find(surface.holder(), "auth.input").getHeight())
                            .isEqualTo(segmentHeight + 2 * com.formdev.flatlaf.util.UIScale.scale(3));
                    }
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
    private static JComponent ending(Container root,String suffix) {
        if(root.getName()!=null&&root.getName().endsWith(suffix))return (JComponent)root;
        for(var child:root.getComponents())if(child instanceof Container nested){var result=ending(nested,suffix);if(result!=null)return result;}
        return null;
    }
    private static JComponent find(Container root,String name){if(name.equals(root.getName()))return (JComponent)root;for(var child:root.getComponents())if(child instanceof Container c){var found=find(c,name);if(found!=null)return found;}return null;}
    private static void layout(Container root){root.doLayout();for(var child:root.getComponents())if(child instanceof Container c)layout(c);}
}
