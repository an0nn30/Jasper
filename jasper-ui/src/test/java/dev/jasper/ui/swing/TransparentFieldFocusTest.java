package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TransparentFieldFocusTest {
    @Test void focusedTransparentFieldHasOneInwardRingWithoutClippedOuterHalo() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var field = new Nodes.Field("query", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Search", "Search query", "", Optional.empty(), Nodes.FieldPresentation.TRANSPARENT);
            var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            try (var view = new KitView(context,new ViewSpec(field),event -> {})) {
                var input = KitViewTest.editor(view,"query");
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Component getFocusOwner(){return input;}});
                for(var listener:input.getFocusListeners())listener.focusGained(new FocusEvent(input,FocusEvent.FOCUS_GAINED));
                var surface = (TextFields.FieldSurface)input.getParent();
                for(int height:new int[]{32,72})for(double scale:new double[]{1,1.25,2}) {
                    surface.setSize(200,height); surface.doLayout();
                    var image = new BufferedImage((int)(200*scale),(int)(height*scale),BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics(); graphics.scale(scale,scale); surface.paint(graphics); graphics.dispose();
                    assertThat(surface.getClientProperty("Jasper.kit.halo")).isNull();
                    assertThat(image.getRGB(0,0)>>>24).as("rounded corner has only subpixel edge coverage at "+scale).isLessThanOrEqualTo(8);
                    if(scale==2) {
                        assertThat(image.getRGB(2,0)>>>24).as("source outer focus corner starts beyond two device pixels").isZero();
                        int focus = context.theme().semantic().color(SemanticColor.BORDER_FOCUS).getRGB();
                        for(int x=0;x<3;x++)assertThat(image.getRGB(x,height)).isEqualTo(focus);
                        assertThat(image.getRGB(3,height)>>>24).isZero();
                    }
                    assertThat(input.getX()).isGreaterThan(0);
                    assertThat(input.getX()+input.getWidth()).isLessThanOrEqualTo(surface.getWidth());
                }
                view.update(new ViewSpec(new Nodes.Field("query",new ControlState(true,false,false,""),Nodes.FieldKind.SEARCH,"","Search","Search query","",Optional.empty(),Nodes.FieldPresentation.TRANSPARENT)));
                assertThat(surface.focus.visible()).isFalse();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
                for(var listener:input.getFocusListeners())listener.focusLost(new FocusEvent(input,FocusEvent.FOCUS_LOST));
                input.getCaret().setVisible(false);
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(previous); KitViewTest.release(context); }
        });
    }
}
