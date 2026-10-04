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
    static BufferedImage paint(JComponent c) {
        var image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try { c.paint(g); } finally { g.dispose(); } return image;
    }
}
