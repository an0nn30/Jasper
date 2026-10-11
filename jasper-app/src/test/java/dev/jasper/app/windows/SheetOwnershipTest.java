package dev.jasper.app.windows;

import dev.jasper.app.platform.WindowBody;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class SheetOwnershipTest {
    @Test void followsMeasuredOwnerScreenCoordinatesAndRecoversFromShortFallback() {
        var root = new JRootPane(); var body = new JPanel(); root.setContentPane(body); root.setSize(900, 555); layout(root);
        try (var lease = WindowBody.install(root, body, () -> new Rectangle(0, 0, body.getWidth(), body.getHeight()))) {
            var content = new JPanel(); content.setPreferredSize(new Dimension(400, 300)); content.setMinimumSize(new Dimension(180, 120));
            var surface = surface(); surface.setContent(content);
            var bounds = new AtomicReference<Rectangle>(); var origin = new Point(100, 200);
            var target = new AttachedSheet.Target(() -> true, () -> new Point(origin), () -> new Rectangle(0, 0, 1200, 900), bounds::set);
            try (var sheet = NativeShells.attachSheet(surface, lease, target)) {
                sheet.fitNow(); assertThat(bounds.get()).isEqualTo(new Rectangle(350, 200, 400, 300));
                origin.setLocation(200, 250); sheet.fitNow(); assertThat(bounds.get()).isEqualTo(new Rectangle(450, 250, 400, 300));
                root.setSize(900, 70); layout(root); sheet.fitNow();
                assertThat(bounds.get()).isEqualTo(new Rectangle(450, 250, 400, 300));
                assertThat(sheet.fallback()).isTrue();
                root.setSize(900, 555); layout(root); sheet.fitNow();assertThat(sheet.fallback()).isFalse();
            }
            assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(1);
        }
    }
    @Test void nestedLeasesShareOneScrimAndQueuedWorkCannotSurviveCloseOrReplacement() {
        var root = new JRootPane();var body = new JPanel();root.setContentPane(body);root.setSize(600, 400);layout(root);
        var original = WindowBody.install(root, body, () -> new Rectangle(0,0,600,400));
        var updates = new java.util.concurrent.atomic.AtomicInteger();
        var target = new AttachedSheet.Target(() -> true, Point::new, () -> new Rectangle(0,0,1000,800), value -> updates.incrementAndGet());
        var a = NativeShells.attachSheet(surface(), original, target);var b = NativeShells.attachSheet(surface(), original, target);
        assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(2);
        a.changed(); a.close();assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(2);
        b.close();assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(1);
        a.fitNow();b.fitNow();assertThat(updates).hasValue(0);
        var replacement=WindowBody.install(root,body,()->new Rectangle(0,0,500,300));original.close();
        assertThat(WindowBody.find(root)).containsSame(replacement);
        root.setContentPane(new JPanel());assertThat(replacement.boundsInRoot()).isEmpty();replacement.close();
    }
    @Test void realFormRewrapsAtConstrainedWidthAndKeepsFooterInsideTheSheet() {
        var controller = new dev.jasper.app.appearance.ThemeController();
        var theme = controller.current();var binding = new dev.jasper.ui.theme.ThemeBinding(theme.pair(), failure -> {throw new AssertionError(failure);});
        var icons = new dev.jasper.ui.icons.IconRegistry();var context = new dev.jasper.ui.swing.KitContext(binding, dev.jasper.ui.theme.ThemeRoute.CHROME,dev.jasper.ui.metrics.DesignMetrics.DEFAULT,icons,()->false);
        var node = new dev.jasper.ui.model.Nodes.Form("sheet",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.FormKind.SHEET,"Add item",Optional.empty(),java.util.List.of(
            new dev.jasper.ui.model.Nodes.Text("explanation",dev.jasper.ui.model.ControlState.READY,"A long explanation that must wrap when its native owner is made narrower. ".repeat(8),dev.jasper.ui.model.Nodes.TextRole.DESCRIPTION)),Optional.empty(),java.util.List.of(
            new dev.jasper.ui.model.Nodes.Button("cancel",dev.jasper.ui.model.ControlState.READY,"Cancel","",dev.jasper.ui.model.Nodes.ButtonKind.SECONDARY,"cancel",""),
            new dev.jasper.ui.model.Nodes.Button("save",dev.jasper.ui.model.ControlState.READY,"Save","",dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY,"save","")));
        var root=new JRootPane();var body=new JPanel();root.setContentPane(body);root.setSize(800,1000);layout(root);
        var surface=surface();
        try(var view=new dev.jasper.ui.swing.KitView(context,new dev.jasper.ui.model.ViewSpec(node),e->{});
            var lease=WindowBody.install(root,body,()->new Rectangle(0,0,body.getWidth(),body.getHeight()))) {
            surface.setContent(view);var bounds=new AtomicReference<Rectangle>();
            try(var sheet=NativeShells.attachSheet(surface,lease,new AttachedSheet.Target(()->true,Point::new,()->new Rectangle(0,0,1200,1200),value->{bounds.set(value);surface.holder().setSize(value.getSize());}))) {
                sheet.fitNow();int wide=bounds.get().height;
                root.setSize(320,1000);layout(root);sheet.fitNow();assertThat(bounds.get().width).isEqualTo(320);assertThat(bounds.get().height).isGreaterThan(wide);
                var save=find(view,"save");assertThat(save).isNotNull();var ancestry=save.getParent();
                var inHolder=SwingUtilities.convertRectangle(save.getParent(),save.getBounds(),surface.holder());assertThat(new Rectangle(surface.holder().getSize()).contains(inHolder)).isTrue();
                root.setSize(320,45);layout(root);sheet.fitNow();assertThat(sheet.fallback()).isTrue();
                root.setSize(800,1000);layout(root);sheet.fitNow();assertThat(sheet.fallback()).isFalse();assertThat(save.getParent()).isSameAs(ancestry);
            }
        } finally {binding.close();icons.close();controller.close();}
    }

    @Test void maximumFontOnAScreenShorterThanFooterRevealsEveryActionAndRecovers() {
        var controller = new dev.jasper.app.appearance.ThemeController();
        controller.configure(dev.jasper.app.appearance.Theme.DARK, new dev.jasper.app.config.UiFontConfig("system", 32f));
        var binding = new dev.jasper.ui.theme.ThemeBinding(controller.current().pair(), failure -> {throw new AssertionError(failure);});
        var icons = new dev.jasper.ui.icons.IconRegistry();
        var context = new dev.jasper.ui.swing.KitContext(binding, dev.jasper.ui.theme.ThemeRoute.CHROME,dev.jasper.ui.metrics.DesignMetrics.DEFAULT,icons,()->false);
        var node = new dev.jasper.ui.model.Nodes.Form("sheet",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.FormKind.SHEET,"Add item",Optional.empty(),java.util.List.of(
            new dev.jasper.ui.model.Nodes.Field("draft",dev.jasper.ui.model.ControlState.READY,dev.jasper.ui.model.Nodes.FieldKind.TEXT,"preserved draft","","Name","")),Optional.empty(),java.util.List.of(
            new dev.jasper.ui.model.Nodes.Button("cancel",dev.jasper.ui.model.ControlState.READY,"Cancel","",dev.jasper.ui.model.Nodes.ButtonKind.SECONDARY,"cancel",""),
            new dev.jasper.ui.model.Nodes.Button("save",dev.jasper.ui.model.ControlState.READY,"Save","",dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY,"save","")));
        var root=new JRootPane();var body=new JPanel();root.setContentPane(body);root.setSize(900,45);layout(root);
        var surface=surface();var usable=new Rectangle(0,0,1000,100);var bounds=new AtomicReference<Rectangle>();
        try(var view=new dev.jasper.ui.swing.KitView(context,new dev.jasper.ui.model.ViewSpec(node),e->{});
            var lease=WindowBody.install(root,body,()->new Rectangle(0,0,body.getWidth(),body.getHeight()))) {
            surface.setContent(view);
            try(var sheet=NativeShells.attachSheet(surface,lease,new AttachedSheet.Target(()->true,Point::new,()->new Rectangle(usable),value->{bounds.set(value);surface.holder().setSize(value.getSize());}))) {
                sheet.fitNow();assertThat(sheet.fallback()).isTrue();assertThat(bounds.get().height).isEqualTo(100);
                var save=(JButton)find(view,"save");var parent=save.getParent();var draft=(JTextField)find(view,"draft.input");
                assertThat(draft.getFont().getSize2D()).isEqualTo(32f);var document=draft.getDocument();
                var scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,save);
                assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
                for(String id:java.util.List.of("save","cancel")) {
                    var action=(JButton)find(view,id);
                    // Headless dispatch verifies the installed keyboard-focus reveal behavior, not native focus acquisition.
                    for(var listener:action.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(action,java.awt.event.FocusEvent.FOCUS_GAINED));
                    var actionBounds=SwingUtilities.convertRectangle(action.getParent(),action.getBounds(),scroll.getViewport().getView());
                    assertThat(scroll.getViewport().getViewRect().contains(actionBounds)).as("focus reveals "+id).isTrue();
                    scroll.getVerticalScrollBar().setValue(actionBounds.y);
                    assertThat(scroll.getViewport().getViewRect().contains(actionBounds)).as("scroll reveals "+id).isTrue();
                }
                usable.height=1000;root.setSize(900,800);layout(root);sheet.fitNow();
                assertThat(sheet.fallback()).isFalse();assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();
                assertThat(save.getParent()).isSameAs(parent);assertThat(draft.getDocument()).isSameAs(document);assertThat(draft.getText()).isEqualTo("preserved draft");
            }
        } finally {binding.close();icons.close();controller.configure(dev.jasper.app.appearance.Theme.DARK,dev.jasper.app.config.UiFontConfig.defaults());controller.close();}
    }
    private static Component find(Container root,String name){if(name.equals(root.getName()))return root;for(var child:root.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container c){var found=find(c,name);if(found!=null)return found;}}return null;}

    static AuxiliarySurface surface() {
        var surface = new AuxiliarySurface("", "Sheet", AuxiliarySurface.Kind.DIALOG, true, new Dimension(1,1), null, null, ignored -> {throw new AssertionError();});
        surface.attachedSheet(true);return surface;
    }
    static void layout(Container node) {node.doLayout();for(var child:node.getComponents())if(child instanceof Container c)layout(c);}
}
