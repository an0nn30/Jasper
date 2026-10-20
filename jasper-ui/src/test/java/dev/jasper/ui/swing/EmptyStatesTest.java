package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.FormsTest.s;
import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;

import javax.swing.*;

class EmptyStatesTest {
    @Test
    void dropdownActionStaysInteractiveAndEmptyContentCentersInFullBody() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var empty =
                            new Empty(
                                    "empty",
                                    ControlState.READY,
                                    new Nodes.Icon(
                                            "icon",
                                            ControlState.READY,
                                            "folder",
                                            Hue.BLUE,
                                            IconSize.EMPTY,
                                            "Folder"),
                                    "No Credentials Yet",
                                    "Add a credential to get started.",
                                    List.of(MenusTest.menu("")));
                    try (var view = new KitView(ctx, new ViewSpec(empty), e -> {})) {
                        view.setSize(s(500), s(420));
                        FormsTest.layout(view);
                        var actions = find(view, "menu", JComponent.class);
                        var title = find(view, "empty.title", JComponent.class);
                        var tile = find(view, "empty.tile", JComponent.class);
                        assertThat(tile).isNotNull();
                        var image = ButtonsTest.paint(tile);
                        assertThat(image.getRGB(s(5), s(20)))
                                .isEqualTo(
                                        ctx.theme()
                                                .semantic()
                                                .color(
                                                        dev.jasper.ui.theme.SemanticColor
                                                                .SURFACE_CONTROL)
                                                .getRGB());
                        Point center =
                                SwingUtilities.convertPoint(title, title.getWidth() / 2, 0, view);
                        assertThat(center.x).isEqualTo(s(250));
                        assertThat(title.getWidth()).isLessThanOrEqualTo(s(300));
                        assertThat(SwingUtilities.convertPoint(actions, 0, 0, view).y)
                                .isGreaterThan(s(210));
                        find(view, "menu.main", JButton.class).doClick(0);
                        assertThat(actions.getComponentPopupMenu().isVisible()).isTrue();
                    } finally {
                        release(ctx);
                    }
                });
    }
}
