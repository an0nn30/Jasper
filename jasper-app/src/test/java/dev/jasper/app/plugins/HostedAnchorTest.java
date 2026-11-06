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
}
