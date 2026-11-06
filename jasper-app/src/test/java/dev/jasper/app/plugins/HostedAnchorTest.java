package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
