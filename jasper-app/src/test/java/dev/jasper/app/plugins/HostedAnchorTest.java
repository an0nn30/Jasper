package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostedAnchorTest {
    @Test void popupFitsBelowOrAboveTheOwnerAndClampsToUsableScreen() {
        var screen=new java.awt.Rectangle(10,20,300,200);
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,30,190,22),new java.awt.Dimension(220,100),screen,screen)).isEqualTo(new java.awt.Rectangle(40,52,220,100));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(250,190,190,22),new java.awt.Dimension(220,100),screen,screen)).isEqualTo(new java.awt.Rectangle(90,90,220,100));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,100,190,22),new java.awt.Dimension(500,400),screen,screen)).isEqualTo(new java.awt.Rectangle(10,20,300,200));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,30,190,22),new java.awt.Dimension(150,100),screen,screen)).as("as wide as its anchor").isEqualTo(new java.awt.Rectangle(40,52,190,100));
    }

    /**
     * H21: a popover starts at its control's left edge while its window has room to the right; else its right edge meets
     * the control's, as Chrome's bookmark bubble under its menu button and IntelliJ's popups do, inside the window.
     */
    @Test void popupKeepsInsideItsAnchorsWindowRightAligningWhereTheWindowHasNoRoomToTheRight() {
        var screen=new java.awt.Rectangle(0,0,1000,800);
        var window=new java.awt.Rectangle(100,100,600,400);
        assertThat(HostedPopover.fit(new java.awt.Rectangle(300,120,30,22),new java.awt.Dimension(240,150),screen,window))
            .as("room to the right").isEqualTo(new java.awt.Rectangle(300,142,240,150));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(660,120,30,22),new java.awt.Dimension(240,150),screen,window))
            .as("a menu button at the window's right edge").isEqualTo(new java.awt.Rectangle(450,142,240,150));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(110,120,30,22),new java.awt.Dimension(650,150),screen,window))
            .as("no room to the control's left either: at the window's left edge").isEqualTo(new java.awt.Rectangle(100,142,650,150));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(960,120,30,22),new java.awt.Dimension(240,150),screen,new java.awt.Rectangle(900,100,100,400)))
            .as("a window at the screen's edge: the screen still bounds it").isEqualTo(new java.awt.Rectangle(760,142,240,150));
    }

    /** Browser plan 8 Task 1b: an area popover is fitted from the area on the screen, below it or above it. */
    @Test void anAreaPopoverFitsBelowTheArea() {
        var screen=new java.awt.Rectangle(0,0,1000,800);
        var area=new java.awt.Rectangle(10,20,30,8);
        var onScreen=HostedPopover.onScreen(new java.awt.Point(100,100),new java.awt.Dimension(640,480),area);
        assertThat(onScreen).isEqualTo(new java.awt.Rectangle(110,120,30,8));
        assertThat(HostedPopover.fitBeside(onScreen,new java.awt.Dimension(200,100),0,screen,screen)).isEqualTo(new java.awt.Rectangle(110,128,200,100));
        assertThat(HostedPopover.fitBeside(HostedPopover.onScreen(new java.awt.Point(100,700),new java.awt.Dimension(640,480),area),new java.awt.Dimension(200,100),0,screen,screen))
            .as("no room below: above the area").isEqualTo(new java.awt.Rectangle(110,620,200,100));
        assertThat(HostedPopover.fitBeside(onScreen,new java.awt.Dimension(20,100),0,screen,screen).width).as("as wide as the area").isEqualTo(30);
        assertThat(HostedPopover.onScreen(new java.awt.Point(100,100),new java.awt.Dimension(190,22),null))
            .as("a control's anchor is its bounds").isEqualTo(new java.awt.Rectangle(100,100,190,22));
    }

    /**
     * Browser plan 8's L37: an area anchor's popover never covers its area, so it never opens over the press that opened
     * it. It is no taller than the larger room the usable screen leaves below or above the area, below the area where it
     * fits there and otherwise above it; a control's popover keeps its clamp.
     */
    @Test void anAreaPopoverNeverCoversItsArea() {
        var screen=new java.awt.Rectangle(0,0,1000,800);
        var window=new java.awt.Rectangle(100,100,600,600);
        var tall=new java.awt.Rectangle(110,100,580,600);
        var preferred=new java.awt.Dimension(220,300);
        assertThat(HostedPopover.room(tall,screen)).isEqualTo(100);
        assertThat(HostedPopover.fitBeside(tall,preferred,0,screen,window)).as("an area filling its window's height: below it, squeezed")
            .isEqualTo(new java.awt.Rectangle(110,700,580,100));
        assertThat(HostedPopover.fit(tall,preferred,screen,window).intersects(tall)).as("a control's placement clamps over it").isTrue();

        var shortScreen=new java.awt.Rectangle(0,0,800,300);
        var middle=new java.awt.Rectangle(50,120,200,60);
        assertThat(HostedPopover.fitBeside(middle,new java.awt.Dimension(220,200),0,shortScreen,shortScreen)).as("the middle of a short screen: below, squeezed")
            .isEqualTo(new java.awt.Rectangle(50,180,220,120));
        var lower=new java.awt.Rectangle(50,150,200,60);
        assertThat(HostedPopover.fitBeside(lower,new java.awt.Dimension(220,200),0,shortScreen,shortScreen)).as("more room above: above, squeezed")
            .isEqualTo(new java.awt.Rectangle(50,0,220,150));

        var bottom=new java.awt.Rectangle(10,700,300,100);
        assertThat(HostedPopover.fitBeside(bottom,preferred,0,screen,screen)).as("an area at the screen's bottom: above it")
            .isEqualTo(new java.awt.Rectangle(10,400,300,300));

        for(var area:List.of(tall,middle,lower,bottom,new java.awt.Rectangle(10,20,30,8),new java.awt.Rectangle(0,-50,40,500),new java.awt.Rectangle(0,650,40,500)))
            for(var usable:List.of(screen,shortScreen)){
                var fitted=HostedPopover.fitBeside(area,new java.awt.Dimension(400,900),0,usable,usable);
                if(fitted!=null)assertThat(fitted.intersects(area)).as("%s on %s: %s",area,usable,fitted).isFalse();
            }
    }

    /** L37: with no room below or above the area, there is nowhere off it to put a popover; L40: nor with less than the content's minimum. */
    @Test void anAreaFillingTheScreenLeavesNoRoom() {
        var screen=new java.awt.Rectangle(0,20,1000,780);
        assertThat(HostedPopover.room(new java.awt.Rectangle(0,20,1000,780),screen)).isZero();
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(0,20,1000,780),new java.awt.Dimension(200,100),0,screen,screen)).isNull();
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(300,0,30,900),new java.awt.Dimension(200,100),0,screen,screen)).as("past both edges").isNull();
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(300,21,30,778),new java.awt.Dimension(200,100),0,screen,screen)).as("a pixel either side: below it")
            .isEqualTo(new java.awt.Rectangle(300,799,200,1));
        // L40: a room smaller than the content's minimum height is no room.
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(300,21,30,778),new java.awt.Dimension(200,100),31,screen,screen)).as("a pixel, under a minimum of 31").isNull();
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(300,51,30,718),new java.awt.Dimension(200,100),31,screen,screen)).as("31 either side, a minimum of 31")
            .isEqualTo(new java.awt.Rectangle(300,769,200,31));
        assertThat(HostedPopover.fitBeside(new java.awt.Rectangle(300,50,30,720),new java.awt.Dimension(200,100),31,screen,screen)).as("30 either side, a minimum of 31").isNull();
    }

    /**
     * L37: placing an area anchor's popover where neither side of the area has room closes it on the next turn, as a
     * dismissal does, so its producer hears it closed; with room it stays open beside the area.
     */
    @Test void anAreaPopoverWithNoRoomBesideItsAreaCloses() throws Exception {
        var page=new AtomicReference<Page>();var closed=new AtomicInteger();var popover=new AtomicReference<HostedPopover>();
        var screen=new java.awt.Rectangle(0,0,1000,800);
        SwingUtilities.invokeAndWait(() -> {
            page.set(Page.open("test.area.room"));
            popover.set(page.get().popover());popover.get().onClosed(closed::incrementAndGet);
            var area=new java.awt.Rectangle(100,700,30,90);
            var placed=popover.get().place(area,screen,screen);
            assertThat(placed).isNotNull();assertThat(placed.intersects(area)).isFalse();
            assertThat(placed.y+placed.height).as("above the area").isEqualTo(700);
            assertThat(popover.get().place(new java.awt.Rectangle(100,0,30,800),screen,screen)).as("no room").isNull();
            assertThat(closed).as("not while it is being placed").hasValue(0);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(closed).as("closed on the next turn").hasValue(1);
            assertThat(page.get().anchor().isOpen()).as("the anchor stays open").isTrue();
            page.get().close();
        });
    }

    /**
     * L40: a room beside the area smaller than the content's minimum height, a picker menu's search box, is no room: the
     * popover would be a sliver taking the keyboard where the user cannot see it, so it closes on the next turn; a room
     * of the minimum shows it.
     */
    @Test void aRoomBelowTheContentsMinimumHeightIsNoRoom() throws Exception {
        var page=new AtomicReference<Page>();var closed=new AtomicInteger();var minimum=new AtomicInteger();
        var screen=new java.awt.Rectangle(0,0,1000,800);
        // An area leaving `room` both below and above it.
        java.util.function.IntFunction<java.awt.Rectangle> leaving=room->new java.awt.Rectangle(100,room,30,800-2*room);
        SwingUtilities.invokeAndWait(() -> {
            page.set(Page.open("test.area.sliver"));
            java.util.function.Supplier<HostedPopover> menu=() -> {
                var popover=(HostedPopover) page.get().ui().windows().popover(page.get().anchor());
                var view=page.get().kit().create(new ViewSpec(new Nodes.PickerMenu("menu",ControlState.READY,"example","Search credentials",
                    List.of(new Nodes.PickerItem("login","example.com","alice","user",Nodes.Hue.BLUE)),"","","No matches","New Credential…","create","cancel")),e -> {});
                popover.setContent(view);popover.show();
                minimum.set(view.component().getMinimumSize().height);
                return popover;
            };
            var shown=menu.get();
            assertThat(minimum.get()).as("precondition: the menu has a minimum height, its search box").isGreaterThan(1);
            var placed=shown.place(leaving.apply(minimum.get()),screen,screen);
            assertThat(placed).as("a room of the minimum").isNotNull();
            assertThat(placed.height).isEqualTo(minimum.get());assertThat(placed.intersects(leaving.apply(minimum.get()))).isFalse();
            shown.close();
            var sliver=menu.get();sliver.onClosed(closed::incrementAndGet);
            assertThat(sliver.place(leaving.apply(minimum.get()-1),screen,screen)).as("a pixel less: no room").isNull();
            assertThat(closed).as("not while it is being placed").hasValue(0);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(closed).as("closed on the next turn").hasValue(1);
            assertThat(page.get().anchor().isOpen()).as("the anchor stays open").isTrue();
            page.get().close();
        });
    }

    /** A view on a page: a Custom node whose canvas content is registered, in a content tab, and an area anchor on it. */
    private record Page(HostedUi ui,HostedKit kit,HostedKitView view,JComponent canvas,dev.jasper.sdk.ui.ContentTab window,HostedAnchor anchor) {
        static Page open(String id) {
            var windows=AppContractTest.headlessWindows();
            var model=new dev.jasper.app.contributions.Contributions();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(java.util.UUID.randomUUID(),tab -> {},tab -> {},tab -> {},() -> {}));
            var ui=new HostedUi(id,model,new Containment(() -> true),Runnable::run,() -> true,() -> true,Page.class.getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},windows,null);
            var kit=new HostedKit(id,() -> true,ui::kitThemes,null,new Containment(() -> true));ui.kit(kit);
            var view=kit.create(new ViewSpec(new Nodes.Custom("page",ControlState.READY,"page.content",Nodes.CustomKind.PREVIEW,"Page")),e -> {});
            var canvas=new JComponent(){};canvas.setSize(640,480);
            view.registerContent(new CustomContent("page.content",Nodes.CustomKind.PREVIEW,canvas,() -> {}));
            var window=ui.windows().openTab(new ContentSpec("owner","Owner"),null,view);
            return new Page(ui,kit,view,canvas,window,(HostedAnchor) view.anchorAt("page",new ContentArea(10,20,30,8)));
        }
        HostedPopover popover() {
            var popover=(HostedPopover) ui.windows().popover(anchor);
            popover.setContent(kit.create(picker(ControlState.READY),e -> {}));
            popover.show();
            return popover;
        }
        void close() { window.close();kit.close();ui.closeAll(); }
    }

    /** F16: the anchor closes at the content's first resize, without waiting to be asked, but not for a resize event that changed nothing. */
    @Test void anAreaAnchorClosesWhenItsContentIsResized() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var page=Page.open("test.area.resize");
            assertThat(page.anchor().target).as("the registered content").isSameAs(page.canvas());
            var closes=new AtomicInteger();page.anchor().onClosed(closes::incrementAndGet);
            page.canvas().dispatchEvent(new java.awt.event.ComponentEvent(page.canvas(),java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            assertThat(closes).as("a resize event at the same size").hasValue(0);
            page.canvas().setSize(800,480);
            page.canvas().dispatchEvent(new java.awt.event.ComponentEvent(page.canvas(),java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            assertThat(closes).as("closed by the resize itself").hasValue(1);
            assertThat(page.anchor().isOpen()).isFalse();
            page.close();
        });
    }

    /**
     * F16: an area's content is the page, so only a press inside the area is consumed when it closes the popover (a press on
     * the field's own button there does not open the picker again); a press elsewhere on the page reaches the page.
     */
    @Test void aPressOutsideTheAreaReachesThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var page=Page.open("test.area.press");
            java.util.function.BiFunction<java.awt.Component,java.awt.Point,java.awt.event.MouseEvent> press=(source,at) ->
                new java.awt.event.MouseEvent(source,java.awt.event.MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,at.x,at.y,1,false);
            var closed=new AtomicInteger();
            var popover=page.popover();popover.onClosed(closed::incrementAndGet);
            var inside=press.apply(page.canvas(),new java.awt.Point(15,25));
            popover.dismissed(inside);
            assertThat(closed).hasValue(1);
            assertThat(inside.isConsumed()).as("a press inside the area").isTrue();

            popover=page.popover();var outside=press.apply(page.canvas(),new java.awt.Point(200,200));
            popover.dismissed(outside);
            assertThat(outside.isConsumed()).as("a press elsewhere on the page").isFalse();

            var child=new JComponent(){};child.setBounds(100,10,50,50);page.canvas().add(child);
            popover=page.popover();var onChild=press.apply(child,new java.awt.Point(5,15));
            popover.dismissed(onChild);
            assertThat(onChild.isConsumed()).as("a press at (105, 25) of the content, right of the area").isFalse();
            child.setLocation(0,0);
            popover=page.popover();var inChild=press.apply(child,new java.awt.Point(12,22));
            popover.dismissed(inChild);
            assertThat(inChild.isConsumed()).as("a press at (12, 22) of the content, in the area").isTrue();

            popover=page.popover();var corner=press.apply(page.canvas(),new java.awt.Point(40,28));
            popover.dismissed(corner);
            assertThat(corner.isConsumed()).as("the area's far edges are outside it").isFalse();
            assertThat(page.anchor().isOpen()).isTrue();
            page.close();
        });
    }

    /** F16: an area's content is no picker, so its popover sets neither picker property on it. */
    @Test void anAreaPopoverSetsNoPickerProperties() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var page=Page.open("test.area.properties");
            var popover=page.popover();
            assertThat(page.canvas().getClientProperty("Jasper.pickerExpanded")).isNull();
            assertThat(page.canvas().getClientProperty("Jasper.pickerDismiss")).isNull();
            popover.close();
            page.anchor().close();
            assertThat(page.canvas().getClientProperty("Jasper.pickerExpanded")).as("nor when it closes").isNull();
            assertThat(page.canvas().getClientProperty("Jasper.pickerDismiss")).isNull();
            page.close();
        });
    }

    static ViewSpec picker(ControlState state) { return new ViewSpec(new Nodes.Choice("pick",state,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick")); }
    @Test void mountedCapabilitySurvivesValueUpdatesButDiesOnDisableAndCannotRevive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var windows=AppContractTest.headlessWindows();
            // A workspace for the view's content tab, where it may grant anchors.
            var model=new dev.jasper.app.contributions.Contributions();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(java.util.UUID.randomUUID(),tab -> {},tab -> {},tab -> {},() -> {}));
            var ui=new HostedUi("test.picker",model,new Containment(() -> true),Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},windows,null);
            var kit=new HostedKit("test.picker",() -> true,ui::kitThemes,null,new Containment(() -> true));ui.kit(kit);
            var view=kit.create(picker(ControlState.READY),e -> {});
            assertThatThrownBy(() -> view.anchor("pick")).isInstanceOf(IllegalArgumentException.class);
            var window=ui.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("owner","Owner"),null,view);
            var anchor=view.anchor("pick");var closes=new AtomicInteger();anchor.onClosed(closes::incrementAndGet);
            view.update(picker(ControlState.READY));assertThat(anchor.isOpen()).isTrue();
            var child=ui.windows().dialog(new DialogSpec("Child",anchor,true));child.show();
            assertThat(anchor.isOpen()).isTrue();
            view.update(picker(new ControlState(true,false,false,"")));
            assertThat(anchor.isOpen()).isFalse();assertThat(closes).hasValue(1);
            view.update(picker(ControlState.READY));
            assertThatThrownBy(() -> ui.windows().popover(anchor)).isInstanceOf(IllegalArgumentException.class);
            window.close();kit.close();ui.closeAll();
        });
    }

    /**
     * Swing closes a popup on the press that lands outside it, and that press then reaches the control under it. A press that
     * dismisses an anchored popover on the popover's own anchor is consumed, so the anchor's action does not run and the
     * popover is not opened again by the click that closed it; a press elsewhere, and Escape, close it and consume nothing.
     */
    @Test void aPressOnAnOpenPopoversAnchorClosesItAndIsConsumedAnyOtherDismissalIsNot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var windows=AppContractTest.headlessWindows();
            var model=new dev.jasper.app.contributions.Contributions();
            model.tabs().addHost(new dev.jasper.app.contributions.WorkspaceHost(java.util.UUID.randomUUID(),tab -> {},tab -> {},tab -> {},() -> {}));
            var ui=new HostedUi("test.popover",model,new Containment(() -> true),Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},windows,null);
            var kit=new HostedKit("test.popover",() -> true,ui::kitThemes,null,new Containment(() -> true));ui.kit(kit);
            var view=kit.create(picker(ControlState.READY),e -> {});
            var window=ui.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("owner","Owner"),null,view);
            var anchor=view.anchor("pick");
            var target=((HostedAnchor) anchor).target;
            java.util.function.Supplier<HostedPopover> open=() -> {
                var popover=(HostedPopover) ui.windows().popover(anchor);
                popover.setContent(kit.create(picker(ControlState.READY),e -> {}));
                popover.show();
                return popover;
            };
            java.util.function.BiFunction<java.awt.Component,Integer,java.awt.event.MouseEvent> mouse=(source,id) ->
                new java.awt.event.MouseEvent(source,id,System.currentTimeMillis(),0,2,2,1,false);

            var closed=new AtomicInteger();
            var popover=open.get();popover.onClosed(closed::incrementAndGet);
            var press=mouse.apply(target,java.awt.event.MouseEvent.MOUSE_PRESSED);
            popover.dismissed(press);
            assertThat(closed).as("the press on the anchor closes it").hasValue(1);
            assertThat(press.isConsumed()).as("and the anchor's action does not run").isTrue();

            popover=open.get();var elsewhere=mouse.apply(new JButton(),java.awt.event.MouseEvent.MOUSE_PRESSED);
            popover.dismissed(elsewhere);
            assertThat(elsewhere.isConsumed()).as("a press elsewhere").isFalse();

            popover=open.get();var release=mouse.apply(target,java.awt.event.MouseEvent.MOUSE_RELEASED);
            popover.dismissed(release);
            assertThat(release.isConsumed()).as("only a press is consumed").isFalse();

            popover=open.get();var escape=new java.awt.event.KeyEvent(target,java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_ESCAPE,(char) 27);
            popover.dismissed(escape);
            assertThat(escape.isConsumed()).as("Escape").isFalse();

            popover=open.get();popover.dismissed(null);
            assertThat(anchor.isOpen()).as("a later click on the anchor finds it open to open a popover again").isTrue();
            window.close();kit.close();ui.closeAll();
        });
    }
}
