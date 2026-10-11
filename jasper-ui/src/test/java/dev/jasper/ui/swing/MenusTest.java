package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.FormsTest.s;
import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.util.*;

import javax.swing.*;

@SuppressWarnings("try")
class MenusTest {
    static MenuButton menu(String action) {
        return new MenuButton(
                "menu",
                ControlState.READY,
                "Add",
                "",
                ButtonKind.SECONDARY,
                action,
                List.of(
                        new MenuItem("login", "Login", "", "add-login", true, false),
                        new MenuItem("disabled", "Unavailable", "", "disabled", false, false)),
                "");
    }

    @Test void categoryMenuUsesTheMeasuredPopupEnvelope() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var items=List.of(new MenuItem("a","Login","user","a",true,false,Hue.BLUE),new MenuItem("b","SSH Key","key","b",true,false,Hue.GREEN),
                new MenuItem("c","Secure Note","note","c",true,false,Hue.AMBER),new MenuItem("sep","","","",true,true),new MenuItem("d","Generate SSH Key...","sparkle","d",true,false,Hue.PURPLE));
            try(var view=new KitView(context,stack(new MenuButton("menu",ControlState.READY,"Add","",ButtonKind.GHOST,"",items,"")),e->{})) {
                var popup=find(view,"menu",JComponent.class).getComponentPopupMenu();
                assertThat(popup.getPreferredSize()).isEqualTo(new java.awt.Dimension(s(222),s(137)));
            }finally{release(context);}
        });
    }

    @Test
    void menuOnlyOpensPopupAndSplitMainDispatchesSeparately() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    try (var view = new KitView(ctx, stack(menu("")), events::add)) {
                        find(view, "menu.main", JButton.class).doClick(0);
                        var popup = find(view, "menu", JComponent.class).getComponentPopupMenu();
                        assertThat(popup.isVisible()).isTrue();
                        assertThat(events).isEmpty();
                        ((JMenuItem) popup.getComponent(0)).doClick(0);
                        assertThat(events).containsExactly(new UiEvent.Action("add-login"));
                        assertThat(popup.isVisible()).isFalse();
                        events.clear();
                        view.update(stack(menu("add")));
                        find(view, "menu.main", JButton.class).doClick(0);
                        assertThat(events).containsExactly(new UiEvent.Action("add"));
                        find(view, "menu.arrow", JButton.class).doClick(0);
                        assertThat(popup.isVisible()).isTrue();
                        assertThat(events).hasSize(1);
                        ((JMenuItem) popup.getComponent(1)).doClick(0);
                        assertThat(events).hasSize(1);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void escapeDismissesAndCloseRemovesOutsideListener() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    int baseline =
                            java.awt.Toolkit.getDefaultToolkit()
                                    .getAWTEventListeners(java.awt.AWTEvent.MOUSE_EVENT_MASK)
                                    .length;
                    try (var view = new KitView(ctx, stack(menu("")), e -> {})) {
                        var root = find(view, "menu", JComponent.class);
                        find(view, "menu.main", JButton.class).doClick(0);
                        var key =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ESCAPE"));
                        root.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(root.getComponentPopupMenu().isVisible()).isFalse();
                        find(view, "menu.main", JButton.class).doClick(0);
                        view.close();
                        assertThat(root.getComponentPopupMenu().isVisible()).isFalse();
                        assertThat(
                                        java.awt.Toolkit.getDefaultToolkit()
                                                .getAWTEventListeners(
                                                        java.awt.AWTEvent.MOUSE_EVENT_MASK))
                                .hasSize(baseline);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void outsideClickClosesPopupAndAnchorAlignsAndClampsToHostContent() {
        assertThat(
                        Menus.anchor(
                                new java.awt.Rectangle(250, 20, 40, 28),
                                new java.awt.Dimension(200, 90),
                                new java.awt.Rectangle(0, 0, 300, 200)))
                .isEqualTo(new java.awt.Point(90, 48));
        assertThat(
                        Menus.anchor(
                                new java.awt.Rectangle(5, 180, 40, 28),
                                new java.awt.Dimension(200, 90),
                                new java.awt.Rectangle(0, 0, 300, 200)))
                .isEqualTo(new java.awt.Point(5, 90));
        assertThat(
                        Menus.anchor(
                                new java.awt.Rectangle(5, 5, 20, 28),
                                new java.awt.Dimension(400, 400),
                                new java.awt.Rectangle(0, 0, 300, 200)))
                .isEqualTo(new java.awt.Point(0, 0));
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, stack(menu("")), e -> {})) {
                        find(view, "menu.main", JButton.class).doClick(0);
                        var outside = new JButton("Outside");
                        var event =
                                new java.awt.event.MouseEvent(
                                        outside,
                                        java.awt.event.MouseEvent.MOUSE_PRESSED,
                                        0,
                                        0,
                                        1,
                                        1,
                                        1,
                                        false);
                        for (var listener :
                                java.awt.Toolkit.getDefaultToolkit()
                                        .getAWTEventListeners(java.awt.AWTEvent.MOUSE_EVENT_MASK))
                            listener.eventDispatched(event);
                        assertThat(
                                        find(view, "menu", JComponent.class)
                                                .getComponentPopupMenu()
                                                .isVisible())
                                .isFalse();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void menuOnlyHasChevronAndSplitArrowHasNoIndependentBorder() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view = new KitView(ctx, stack(menu("")), e -> {})) {
                        var main = find(view, "menu.main", JButton.class);
                        var menuSurface = find(view, "menu", JComponent.class);
                        menuSurface.setSize(s(180), s(34));
                        FormsTest.layout(menuSurface);
                        var image = ButtonsTest.paint(menuSurface);
                        int background =
                                ctx.theme()
                                        .semantic()
                                        .color(
                                                dev.jasper.ui.theme.SemanticColor
                                                        .BUTTON_SECONDARY_BG)
                                        .getRGB();
                        int count = 0;
                        for (int x = s(158); x < s(171); x++)
                            for (int y = s(10); y < s(24); y++)
                                if (image.getRGB(x, y) != background) count++;
                        assertThat(count).isPositive();
                        view.update(stack(menu("add")));
                        var root = find(view, "menu", JComponent.class);
                        root.setSize(s(180), s(34));
                        FormsTest.layout(root);
                        image = ButtonsTest.paint(root);
                        int join = find(view, "menu.arrow", JButton.class).getX();
                        assertThat(image.getRGB(join + s(3), s(6))).isEqualTo(background);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void optionalMenuIconUsesSourceCategoryTileAndItemsFitFontGrowth() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    Object old = UIManager.get("defaultFont");
                    try {
                        UIManager.put(
                                "defaultFont",
                                new java.awt.Font("Dialog", java.awt.Font.PLAIN, 18));
                        var menu =
                                new MenuButton(
                                        "menu",
                                        ControlState.READY,
                                        "Add",
                                        "",
                                        ButtonKind.SECONDARY,
                                        "",
                                        List.of(
                                                new MenuItem(
                                                        "key", "SSH Key", "key", "add-key", true,
                                                        false)),
                                        "");
                        try (var view = new KitView(ctx, stack(menu), e -> {})) {
                            var popup =
                                    find(view, "menu", JComponent.class).getComponentPopupMenu();
                            var item = (JMenuItem) popup.getComponent(0);
                            assertThat(item.getIcon().getIconWidth()).isEqualTo(s(18));
                            assertThat(item.getPreferredSize().height).isGreaterThan(s(28));
                        }
                    } finally {
                        UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }
    @Test void contextualSplitMenuKeepsItsArrowUnpaintedUntilItsRowIsActive() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();
            var action=new MenuButton("menu",ControlState.READY,"Add","",ButtonKind.GHOST,"add",menu("add").items(),"Add",ButtonPresentation.CONTEXTUAL_EMBEDDED);
            var item=new ListItem("item",Optional.empty(),List.of(new Text("title",ControlState.READY,"Row",TextRole.BODY)),List.of(),List.of(action));
            try(var view=new KitView(ctx,new ViewSpec(new ItemList("list",ControlState.READY,ListKind.SINGLE,List.of(item),Set.of(),"",List.of())),e->{})) {
                view.setSize(260,100);InteractiveRowsTest.layout(view);
                var rows=(ItemLists.ListControl)view.control("list");var row=rows.mounted.get("item");var menu=(Menus.MenuControl)row.controls.get("menu");
                assertThat(RemoteKitTest.ink(menu.main)).isZero();assertThat(RemoteKitTest.ink(menu.arrow)).isZero();
                row.hover=true;assertThat(RemoteKitTest.ink(menu.main)).isPositive();assertThat(RemoteKitTest.ink(menu.arrow)).isPositive();
            }finally{release(ctx);}
        });
    }

}
