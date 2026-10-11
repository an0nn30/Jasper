package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

class ButtonsTest {
    @Test void fiveKindsDispatchOnceAfterTenUpdatesAndUseSemanticColors() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            Node[] nodes = java.util.Arrays.stream(ButtonKind.values()).map(k -> (Node)new Nodes.Button(k.name(), ControlState.READY, "Run", "add", k, k.name(), "Run action")).toArray(Node[]::new);
            try (var view = new KitView(context, stack(nodes), events::add)) {
                for (int i = 0; i < 10; i++) view.update(stack(nodes));
                for (ButtonKind kind : ButtonKind.values()) find(view, kind.name(), JButton.class).doClick(0);
                assertThat(events).containsExactlyElementsOf(java.util.Arrays.stream(ButtonKind.values()).map(k -> new UiEvent.Action(k.name())).toList());
                assertThat(find(view, "PRIMARY", JButton.class).getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_ONACCENT));
                assertThat(find(view, "LINK", JButton.class).getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_LINK));
            } finally { release(context); }
        });
    }
    @Test void disabledAndBusyButtonsCannotHoverOrDispatchAndVisibleStateApplies() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(new Nodes.Button("b", new ControlState(true, false, false, ""), "Go", "", ButtonKind.PRIMARY, "go", "")), events::add)) {
                var b = find(view, "b", JButton.class); b.setSize(b.getPreferredSize());
                BufferedImage before = paint(b); b.getModel().setRollover(true); BufferedImage after = paint(b);
                assertThat(before.getRGB(0, 0, before.getWidth(), before.getHeight(), null, 0, before.getWidth()))
                    .containsExactly(after.getRGB(0, 0, after.getWidth(), after.getHeight(), null, 0, after.getWidth()));
                b.doClick(0); assertThat(events).isEmpty();
                view.update(stack(new Nodes.Button("b", new ControlState(false, true, true, ""), "Go", "", ButtonKind.PRIMARY, "go", "")));
                assertThat(b.isVisible()).isFalse(); assertThat(b.isEnabled()).isFalse(); b.doClick(0); assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void iconButtonsHaveTooltipAccessibleNameAndLiteralText() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Nodes.Button("b", ControlState.READY, "", "copy", ButtonKind.GHOST, "copy", "Copy Link"),
                    new Nodes.Button("literal", ControlState.READY, "<html>literal", "", ButtonKind.SECONDARY, "go", "")), e -> {})) {
                var b = find(view, "b", JButton.class); assertThat(b.getIcon()).isNotNull();
                assertThat(b.getToolTipText()).isEqualTo("Copy Link"); assertThat(b.getAccessibleContext().getAccessibleName()).isEqualTo("Copy Link");
                assertThat(find(view, "literal", JButton.class).getClientProperty("html")).isNull();
            } finally { release(context); }
        });
    }
    @Test void tooltipContentNeverActivatesHtml() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Nodes.Button("b", ControlState.READY, "", "copy", ButtonKind.GHOST, "copy", "<html>Copy")), e -> {})) {
                var b = find(view, "b", JButton.class); var tip = b.createToolTip(); tip.setTipText(b.getToolTipText());
                assertThat(tip.getClientProperty("html")).isNull();
                assertThat(tip.getTipText()).isEqualTo("<html>Copy");
            } finally { release(context); }
        });
    }
    @Test void compactFooterActionReservesItsFaceFocusAndHitAreaAcrossThemesAndFontOverrides() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var events=new ArrayList<UiEvent>();var old=UIManager.get("defaultFont");
            var action=new Nodes.Button("connect",ControlState.READY,"Connect","",ButtonKind.PRIMARY,"connect","Connect");
            var detail=new Nodes.Stack("detail",ControlState.READY,LayoutRole.DETAIL,List.of(action));
            var panel=new Nodes.Panel("panel",ControlState.READY,new Header("header",ControlState.READY,"Panel",java.util.OptionalInt.empty(),java.util.Optional.empty(),java.util.Optional.empty(),List.of()),java.util.Optional.empty(),new Text("body",ControlState.READY,"Body",TextRole.BODY),java.util.Optional.of(detail));
            try(var view=new KitView(ctx,new ViewSpec(panel),events::add)) {
                var button=((Buttons.ButtonControl)view.control("connect")).button;
                for(var theme:List.of(KitFixtures.dark(),KitFixtures.light(),KitFixtures.custom())) {
                    ctx.binding().publish(new ThemePair(theme,theme));
                    view.setSize(260,160);InteractiveRowsTest.layout(view);
                    assertThat(button.getPreferredSize().height).isEqualTo(28);
                    button.setSize(button.getPreferredSize());var before=paint(button);
                    assertThat(before.getRGB(button.getWidth()/2,2)>>>24).isZero();
                    assertThat(before.getRGB(button.getWidth()/2,3)).isEqualTo(theme.semantic().color(SemanticColor.ACCENT).getRGB());
                    button.getModel().setRollover(true);var hover=paint(button);
                    assertThat(hover.getRGB(button.getWidth()/2,3)).isEqualTo(theme.semantic().color(SemanticColor.ACCENT_HOVER).getRGB());
                    button.getModel().setRollover(false);button.setEnabled(false);var disabled=paint(button);button.getModel().setRollover(true);
                    assertThat(paint(button).getRGB(0,0,button.getWidth(),button.getHeight(),null,0,button.getWidth())).containsExactly(disabled.getRGB(0,0,button.getWidth(),button.getHeight(),null,0,button.getWidth()));
                    button.doClick(0);assertThat(events).isEmpty();button.setEnabled(true);button.getModel().setRollover(false);
                }
                UIManager.put("defaultFont",new Font(Font.MONOSPACED,Font.PLAIN,24));view.update(new ViewSpec(panel));InteractiveRowsTest.layout(view);
                assertThat(button.getFont().getFamily()).isEqualTo(Font.MONOSPACED);assertThat(button.getPreferredSize().height).isGreaterThan(28);
                assertThat(((Buttons.ButtonControl)view.control("connect")).button).isSameAs(button);
                button.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("connect"));
            } finally {if(old==null)UIManager.getDefaults().remove("defaultFont");else UIManager.put("defaultFont",old);release(ctx);}
        });
    }
    static BufferedImage paint(JComponent c) {
        var image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { c.paint(g); } finally { g.dispose(); } return image;
    }
}
