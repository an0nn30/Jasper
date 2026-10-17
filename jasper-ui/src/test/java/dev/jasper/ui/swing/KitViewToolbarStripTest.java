package dev.jasper.ui.swing;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Plan 3d: a contributed toolbar view is a toolbar strip, which presents an icon-only menu as a title strip does. */
class KitViewToolbarStripTest {
    static ViewSpec iconMenu() {
        return new ViewSpec(new Nodes.MenuButton("more", ControlState.READY, "", "more", Nodes.ButtonKind.SECONDARY, "",
            List.of(new MenuItem("manage", "Manage Sessions", "", "manage", true, false)), "More"));
    }

    static int[] pixels(Consumer<KitView> presentation) {
        var context = KitFixtures.context();
        try (var view = new KitView(context, iconMenu(), event -> { })) {
            presentation.accept(view);
            return KitFixtures.render(view, 120, 30, 1).getRGB(0, 0, 120, 30, null, 0, 120);
        } finally { context.binding().close(); context.icons().close(); }
    }

    @Test void aToolbarStripPresentsAnIconOnlyMenuAsATitleStripDoes() {
        Edt.run(() -> {
            int[] plain = pixels(view -> { }), title = pixels(view -> view.titleStrip(true)), toolbar = pixels(view -> view.toolbarStrip(true));
            assertThat(title).as("a strip changes the icon-only menu").isNotEqualTo(plain);
            assertThat(toolbar).isEqualTo(title);
        });
    }

    /** Plans 3b and 3d merged: a toolbar strip draws a Separator as a hairline centred between two controls. */
    @Test void aToolbarStripDrawsASeparatorBetweenItsControlsWithoutGrowingTaller() {
        Edt.run(() -> {
            var ready = ControlState.READY;
            var label = new Nodes.Text("bar.label", ready, "Session", Nodes.TextRole.LABEL);
            var connect = new Nodes.Button("bar.connect", ready, "Connect", "", Nodes.ButtonKind.PRIMARY, "connect", "");
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                int plainHeight;
                try (var plain = new KitView(context, new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(label, connect))), event -> { })) {
                    plain.toolbarStrip(true);
                    plainHeight = plain.getPreferredSize().height;
                }
                var ruled = new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(label, new Nodes.Separator("bar.rule", ready), connect)));
                try (var view = new KitView(context, ruled, event -> { })) {
                    view.toolbarStrip(true);
                    var size = view.getPreferredSize();
                    assertThat(size.height).as("the rule does not make the strip taller").isEqualTo(plainHeight);
                    var image = KitFixtures.render(view, size.width, size.height, 1);
                    var rule = KitViewTest.find(view, "bar.rule", javax.swing.JComponent.class);
                    var before = KitViewTest.find(view, "bar.label", javax.swing.JComponent.class);
                    var after = KitViewTest.find(view, "bar.connect", javax.swing.JComponent.class);
                    assertThat(rule.isVisible()).isTrue();
                    assertThat(rule.getWidth()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE)));
                    assertThat(rule.getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(context.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_CONTROL)))
                        .isLessThan(after.getHeight());
                    assertThat(rule.getX()).isGreaterThanOrEqualTo(before.getX() + before.getWidth()).isLessThan(after.getX());
                    assertThat(rule.getY() + rule.getHeight() / 2).as("centred on the button").isCloseTo(after.getY() + after.getHeight() / 2, within(1));
                    var at = javax.swing.SwingUtilities.convertPoint(rule.getParent(), rule.getX(), rule.getY() + rule.getHeight() / 2, view);
                    assertThat(image.getRGB(at.x, at.y)).isEqualTo(KitFixtures.marker(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE).getRGB());
                }
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    /** Plan 10a: a toolbar view's row is one line, so its LABEL text names a dropdown, whose own label stays its accessible name. */
    @Test void aToolbarStripShowsNoCaptionAboveADropdown() {
        Edt.run(() -> {
            var ready = ControlState.READY;
            var spec = new ViewSpec(new Nodes.Stack("bar", ready, Nodes.LayoutRole.INLINE, List.of(
                new Nodes.Text("bar.label", ready, "Session:", Nodes.TextRole.LABEL),
                new Nodes.Choice("bar.host", ready, Nodes.ChoiceKind.DROPDOWN, List.of(new dev.jasper.ui.model.Option("mac", "Mac", java.util.Optional.empty())),
                    "mac", "Session", "host"))));
            var context = KitFixtures.context();
            try (var view = new KitView(context, spec, event -> { })) {
                var choice = (Choices.ChoiceControl) view.control("bar.host");
                assertThat(choice.label.isVisible()).as("a caption outside a toolbar").isTrue();
                int captioned = view.getPreferredSize().height;
                view.toolbarStrip(true);
                assertThat(choice.label.isVisible()).as("no caption in a toolbar strip").isFalse();
                assertThat(choice.input.getAccessibleContext().getAccessibleName()).isEqualTo("Session");
                assertThat(view.getPreferredSize().height).as("one line").isLessThan(captioned);
                view.toolbarStrip(false);
                assertThat(choice.label.isVisible()).isTrue();
            } finally { context.binding().close(); context.icons().close(); }
        });
    }

    @Test void theFlagIsReportedAndAClosedViewRefusesIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                var view = new KitView(context, iconMenu(), event -> { });
                assertThat(view.toolbarStrip()).isFalse();
                view.toolbarStrip(true);
                assertThat(view.toolbarStrip()).isTrue();
                assertThat(view.titleStrip()).as("a separate flag").isFalse();
                view.close();
                assertThatIllegalStateException().isThrownBy(() -> view.toolbarStrip(false));
            } finally { context.binding().close(); context.icons().close(); }
        });
    }
}
