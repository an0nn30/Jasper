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
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,30,190,22),new java.awt.Dimension(220,100),screen)).isEqualTo(new java.awt.Rectangle(40,52,220,100));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(250,190,190,22),new java.awt.Dimension(220,100),screen)).isEqualTo(new java.awt.Rectangle(90,90,220,100));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,100,190,22),new java.awt.Dimension(500,400),screen)).isEqualTo(new java.awt.Rectangle(10,20,300,200));
        assertThat(HostedPopover.fit(new java.awt.Rectangle(40,30,190,22),new java.awt.Dimension(150,100),screen)).as("as wide as its anchor").isEqualTo(new java.awt.Rectangle(40,52,190,100));
    }

    static ViewSpec picker(ControlState state) { return new ViewSpec(new Nodes.Choice("pick",state,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick")); }
    @Test void mountedCapabilitySurvivesValueUpdatesButDiesOnDisableAndCannotRevive() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var windows=AppContractTest.headlessWindows();
            var ui=new HostedUi("test.picker",new dev.jasper.app.contributions.Contributions(),new Containment(() -> true),Runnable::run,() -> true,() -> true,getClass().getClassLoader(),() -> dev.jasper.sdk.Variant.DARK,h -> () -> {},windows,null);
            var kit=new HostedKit("test.picker",() -> true,ui::kitThemes,null,new Containment(() -> true));ui.kit(kit);
            var view=kit.create(picker(ControlState.READY),e -> {});
            assertThatThrownBy(() -> view.anchor("pick")).isInstanceOf(IllegalArgumentException.class);
            var window=ui.windows().create(new WindowSpec("test.picker.owner","Owner",new java.awt.Dimension(300,200),false));
            window.setContent(view);window.show();
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
