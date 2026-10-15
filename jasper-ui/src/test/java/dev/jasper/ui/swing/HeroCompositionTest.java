package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Font;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HeroCompositionTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    @Test void supportingTextPaintsEveryLineBesideANaturalFirstLineAdornment() {
        Edt.run(() -> {
            var previous = new java.util.HashMap<String,Object>();
            UIManager.getDefaults().forEach((key,value) -> { if (key.equals("defaultFont") || key.equals("Label.font")) previous.put((String)key,value); });
            var context = KitFixtures.context();
            String text = "Your master password cannot be recovered if forgotten. Keep it safe for future use.";
            try {
                for (int size : List.of(13,20,32)) {
                    var font = new Font("Serif", Font.PLAIN, size);
                    UIManager.put("defaultFont",font); UIManager.put("Label.font",font);
                    var spec = new ViewSpec(new Nodes.Stack("support",ControlState.READY,LayoutRole.SUPPORTING,List.of(
                        new Nodes.Icon("icon",ControlState.READY,"warning",Hue.NONE,IconSize.CAPTION,"Warning"),
                        new Text("body",ControlState.READY,text,TextRole.CAPTION))));
                    try (var view = new KitView(context,spec,event -> {})) {
                        for (int width : List.of(344,180)) {
                            view.setSize(width,1000); view.doLayout();
                            int height = view.getPreferredSize().height;
                            var painted = KitFixtures.render(view,width,height,1);
                            var label = (Labels.LiteralLabel)view.control("body").component;
                            var icon = view.control("icon").component;
                            assertThat(icon.getWidth()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(12));
                            assertThat(label.getX()).isEqualTo(icon.getWidth()+com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.INLINE_GAP)));
                            assertThat(icon.getY()).isEqualTo(Math.max(0,(label.getFontMetrics(label.getFont()).getHeight()-icon.getHeight())/2));
                            assertThat(label.wraps()).isTrue();
                            var lines = label.wrapped.lines(label.getWidth());
                            assertThat(String.join(" ",lines)).isEqualTo(text);
                            assertThat(lines).hasSizeGreaterThan(1);
                            assertThat(label.getHeight()).isEqualTo(lines.size()*label.getFontMetrics(label.getFont()).getHeight());
                            int lineHeight = label.getFontMetrics(label.getFont()).getHeight();
                            for (int line=0;line<lines.size();line++) {
                                boolean ink=false;
                                for(int y=line*lineHeight;y<(line+1)*lineHeight;y++) for(int x=label.getX();x<width;x++)
                                    ink |= (painted.getRGB(x,y) >>> 24) != 0;
                                assertThat(ink).as("painted line %s at font %s width %s",line,size,width).isTrue();
                            }
                        }
                    }
                }
            } finally { previous.forEach(UIManager::put); if(!previous.containsKey("defaultFont"))UIManager.put("defaultFont",null); if(!previous.containsKey("Label.font"))UIManager.put("Label.font",null); KitViewTest.release(context); }
        });
    }

    @Test void strengthSpacingUsesOnlyPreviousVisibleSiblings() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var meter = new Strength("meter", ControlState.READY, 0, "");
            var preceding = new Text("before", ControlState.READY, "Before", TextRole.BODY);
            try (var view = new KitView(context, KitViewTest.stack(meter), event -> {})) {
                for (var children : List.of(List.<Node>of(meter), List.<Node>of(meter, preceding),
                        List.<Node>of(preceding, meter), List.<Node>of(new Text("before", new ControlState(false,true,false,""), "Before", TextRole.BODY), meter))) {
                    view.update(new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, children)));
                    var preferred = view.getPreferredSize();
                    assertThat(preferred.height).isGreaterThanOrEqualTo(3);
                    KitFixtures.render(view, 300, preferred.height, 1);
                    var control = view.control("meter").component;
                    int expected = children.getFirst() == preceding ? view.control("before").component.getHeight() + m(dev.jasper.ui.metrics.DesignMetrics.Role.STRENGTH_TOP_GAP) : 0;
                    assertThat(control.getY()).isEqualTo(expected);
                    assertThat(control.getHeight()).isEqualTo(3);
                    assertThat(control.getY()+control.getHeight()).isLessThanOrEqualTo(preferred.height);
                }
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void confirmationFollowsGroupRevealAndRetainsSubmitAndWipe() {
        Edt.run(() -> {
            var context=KitFixtures.context(); var events=new ArrayList<UiEvent>();
            var spec=KitViewTest.stack(new Secret("password",ControlState.READY,"Password","Password","master"),
                new Secret("confirm",ControlState.READY,"Confirm","Confirm","master",SecretKind.CONFIRMATION,"","submit"));
            JPasswordField[] retained={null};
            try(var view=new KitView(context,spec,events::add)) {
                var first=(SecretFields.SecretControl)view.control("password");
                var confirm=(SecretFields.SecretControl)view.control("confirm"); retained[0]=confirm.input;
                first.input.setText("synthetic-value"); confirm.input.setText("synthetic-value");
                assertThat(first.reveal.isVisible()).isTrue(); assertThat(confirm.reveal.isVisible()).isFalse(); assertThat(confirm.reveal.getParent()).isNull();
                first.reveal.doClick(0); assertThat(first.input.getEchoChar()).isEqualTo((char)0); assertThat(confirm.input.getEchoChar()).isEqualTo((char)0);
                context.binding().publish(new ThemePair(KitFixtures.custom(),KitFixtures.custom()));
                assertThat(confirm.input.getPassword()).containsExactly("synthetic-value".toCharArray());
                SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(confirm.input, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, (char)10)); assertThat(events).contains(new UiEvent.Action("submit"));
                first.reveal.doClick(0); assertThat(confirm.input.getEchoChar()).isNotEqualTo((char)0);
                view.clearSecret("password"); view.clearSecret("confirm"); assertThat(confirm.input.getPassword()).isEmpty();
                confirm.input.setText("synthetic-close");
            } finally { KitViewTest.release(context); }
            assertThat(retained[0].getPassword()).isEmpty();
        });
    }

    @Test void settingAnnotationWrapsAndUsesItsOwnToneAtDefaultAndEnlargedUserFonts() {
        Edt.run(() -> {
            var previous=new java.util.HashMap<String,Object>();
            UIManager.getDefaults().forEach((key,value) -> { if (key.equals("defaultFont") || key.equals("Label.font")) previous.put((String)key,value); });
            var context=KitFixtures.context();
            try {
                for(int size:List.of(13,20)) {
                    var font=new Font(new dev.jasper.ui.metrics.Typography().family(),Font.PLAIN,size);
                    UIManager.put("defaultFont",font); UIManager.put("Label.font",font);
                    var row=new SettingRow("setting",ControlState.READY,"Bind to this device","Device description",new Toggle("toggle",ControlState.READY,"Bind",true,"bind",false),false,false,"Recommended");
                    try(var view=new KitView(context,new ViewSpec(row), e -> {})) {
                        var control=(SettingRows.RowControl)view.control("setting");
                        var image=KitFixtures.render(view,260,160,1);
                        assertThat(image).isNotNull();
                        assertThat(control.title.noteStart).isEqualTo(row.title().length());
                        assertThat(control.title.noteColor).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_TERTIARY));
                        // The title and its annotation wrap once wider than their line: at 13 pt with the macOS faces, at 20 with any.
                        var titleLines=control.title.lines(control.title.getWidth());
                        assertThat(titleLines.size()>1).isEqualTo(control.title.advance(control.title.text)>control.title.getWidth());
                        if(size>13||com.formdev.flatlaf.util.SystemInfo.isMacOS)assertThat(titleLines).hasSizeGreaterThan(1);
                        assertThat(control.title.getHeight()).isEqualTo(control.title.height(control.title.getWidth()));
                        assertThat(control.description.getY()).isGreaterThanOrEqualTo(control.title.getY()+control.title.getHeight());
                        var toggle = (Switches.SwitchButton)view.control("toggle").component;
                        int ring = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING);
                        assertThat(control.component.getWidth() - (toggle.getX() + toggle.getWidth() - ring))
                            .as("setting rows align the painted switch to the shared setting inset").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.SETTING_SIDE_PADDING)));
                        assertThat(toggle.getX() + toggle.getWidth()).isLessThanOrEqualTo(control.component.getWidth());
                        assertThat(control.title.getX() + control.title.getWidth()).isLessThan(toggle.getX());
                        var previousFocus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
                        try {
                            java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(new java.awt.DefaultKeyboardFocusManager() {
                                @Override public java.awt.Component getFocusOwner() { return toggle; }
                            });
                            var event = new java.awt.event.FocusEvent(toggle, java.awt.event.FocusEvent.FOCUS_GAINED, false, null, java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
                            for (var listener : toggle.getFocusListeners()) listener.focusGained(event);
                            var focused = KitFixtures.render(view, 260, 160, 1);
                            int x = toggle.getX() + toggle.getWidth() - 2, y = toggle.getY() + toggle.getHeight()/2;
                            assertThat(focused.getRGB(x, y)).as("trailing focus halo remains painted inside the row").isNotEqualTo(image.getRGB(x, y));
                            view.update(new ViewSpec(new SettingRow("setting", ControlState.READY, row.title(), row.description(),
                                new Toggle("toggle", ControlState.READY, "Bind", false, "bind", false), false, false, row.titleNote())));
                            KitFixtures.render(view, 260, 160, 1);
                            assertThat(view.control("toggle").component).isSameAs(toggle);
                            assertThat(control.component.getWidth() - (toggle.getX() + toggle.getWidth() - ring)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.SETTING_SIDE_PADDING)));
                        } finally { java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocus); }
                    }
                }
            } finally { UIManager.put("defaultFont",previous.get("defaultFont")); UIManager.put("Label.font",previous.get("Label.font")); KitViewTest.release(context); }
        });
    }
}
