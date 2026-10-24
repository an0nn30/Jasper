package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentBarLayoutTest {
    private static Nodes.Button button(String id, String icon, String tooltip, boolean selected) {
        return new Nodes.Button(id, ControlState.READY, "", icon, Nodes.ButtonKind.GHOST, id, tooltip,
            Nodes.ButtonPresentation.STANDARD, Optional.empty(), Optional.of(selected));
    }
    private static ViewSpec actions(boolean classic) {
        String suffix = classic ? ".classic" : "";
        return document(new Nodes.Stack("bar", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Text("fill", ControlState.READY, "", Nodes.TextRole.CAPTION),
            button("source", "editor.source" + suffix, "Source", false),
            button("split", "editor.split" + suffix, "Source and preview", true),
            button("preview", "editor.preview" + suffix, "Preview", false),
            button("sync", "editor.scroll-sync" + suffix, "Synchronize scrolling", true))));
    }
    private static ViewSpec document(Nodes.Node row) {
        return new ViewSpec(new Nodes.Split("doc", ControlState.READY, Nodes.SplitKind.DOCUMENT,
            List.of(row, new Nodes.Text("body", ControlState.READY, "Document", Nodes.TextRole.BODY))));
    }
    @Test void actionOnlyRowsKeepNativeWidthsScrollOnKeyboardFocusAndHaveNoWindowWidthFloor() {
        Edt.run(() -> {
            for (boolean classic : List.of(false, true)) for (boolean dark : List.of(false, true)) {
                var theme = classic ? (dark ? KitFixtures.enterpriseDark() : KitFixtures.enterpriseLight())
                    : (dark ? KitFixtures.dark() : KitFixtures.light());
                var context = KitFixtures.context(theme, classic ? KitFixtures.enterprise() : dev.jasper.ui.kit.Kit.DEFAULT);
                var events = new java.util.ArrayList<UiEvent>();
                try (var view = new KitView(context, actions(classic), events::add)) {
                    KitFixtures.render(view, 420, 100, 2);
                    var source = KitViewTest.find(view, "source", AbstractButton.class);
                    var last = KitViewTest.find(view, "sync", AbstractButton.class);
                    assertThat(source.getX()).as("actions align at trailing edge").isGreaterThan(200);
                    assertThat(last.getAccessibleContext().getAccessibleName()).isEqualTo("Synchronize scrolling");
                    assertThat(last.isFocusable()).isTrue();
                    assertThat(last.isSelected()).isTrue();
                    assertThat(source.isSelected()).isFalse();
                    assertThat(KitViewTest.find(view, "split", AbstractButton.class).isSelected()).isTrue();
                    assertThat(KitViewTest.find(view, "preview", AbstractButton.class).isSelected()).isFalse();
                    for (String id : List.of("source", "split", "preview", "sync")) {
                        var action = KitViewTest.find(view, id, AbstractButton.class);
                        assertThat(action.getAccessibleContext().getAccessibleName()).isEqualTo(action.getToolTipText()).isNotBlank();
                        assertThat(action.isFocusable()).isTrue();
                    }
                    assertThat(view.control("fill").component.isFocusable()).isFalse();
                    var rendered = KitFixtures.render(view, 420, 100, 2);
                    var image = new java.awt.image.BufferedImage(rendered.getWidth(), rendered.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try { graphics.setColor(theme.semantic().color(SemanticColor.SURFACE_PANEL)); graphics.fillRect(0, 0, image.getWidth(), image.getHeight()); graphics.drawImage(rendered, 0, 0, null); }
                    finally { graphics.dispose(); }
                    try {
                        Path out = Path.of("build/task5-renders/" + (classic ? "classic" : "modern") + (dark ? "-dark.png" : "-light.png"));
                        Files.createDirectories(out.getParent()); javax.imageio.ImageIO.write(image, "png", out.toFile());
                    } catch (java.io.IOException e) { throw new AssertionError(e); }
                    KitFixtures.render(view, 64, 100, 1);
                    assertThat(last.getWidth()).isEqualTo(last.getPreferredSize().width).isPositive();
                    var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, last);
                    assertThat(scroll).as("a narrow action-only document row horizontally overflows").isNotNull();
                    assertThat(scroll.getHorizontalScrollBar().isVisible()).isTrue();
                    assertThat(view.getMinimumSize().width).as("a document never raises the native minimum width").isZero();
                    scroll.getViewport().setViewPosition(new Point());
                    for (var listener : last.getFocusListeners()) listener.focusGained(new FocusEvent(last, FocusEvent.FOCUS_GAINED));
                    assertThat(scroll.getViewport().getViewRect().contains(SwingUtilities.convertRectangle(last.getParent(), last.getBounds(), scroll.getViewport().getView()))).isTrue();
                    last.doClick(0);
                    assertThat(events).containsExactly(new UiEvent.Action("sync"));
                    view.update(actions(classic));
                    KitFixtures.render(view, 420, 100, 1);
                    assertThat(KitViewTest.find(view, "sync", AbstractButton.class)).isSameAs(last);
                } finally { context.binding().close(); context.icons().close(); }
            }
        });
    }
    @Test void findRowsKeepTheirExistingFillAndNeverReceiveTheActionOnlyOverflowTreatment() {
        Edt.run(() -> {
            var row = new Nodes.Stack("bar", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Field("query", ControlState.READY, Nodes.FieldKind.SEARCH, "", "Find", "Find", "", Optional.empty()),
                button("next", "chevron.down", "Next Match", false)));
            var context = KitFixtures.context();
            try (var view = new KitView(context, document(row), e -> {})) {
                KitFixtures.render(view, 420, 100, 1);
                var field = view.control("query").component;
                var button = KitViewTest.find(view, "next", AbstractButton.class);
                assertThat(field.getWidth()).isGreaterThan(200);
                assertThat(field.getX() + field.getWidth()).isLessThanOrEqualTo(button.getX());
                KitFixtures.render(view, 64, 100, 1);
                assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, button)).isNull();
                assertThat(view.control("bar").component.getLayout()).isInstanceOf(Stacks.DocumentBarLayout.class);
            } finally { context.binding().close(); context.icons().close(); }
        });
    }
}
