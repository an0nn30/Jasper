package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelRegion;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class RailWidthTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void theRailLaysOutItsButtonsFromItsWidth() throws Exception {
        edt(() -> {
            var rail = new WindowRail();
            rail.render(List.of(new WindowRail.PanelButton("a", "A", null, PanelRegion.LEFT, false),
                new WindowRail.PanelButton("b", "B", null, PanelRegion.RIGHT, false)), List.of());
            for (int width : new int[]{28, 36, 64}) {
                rail.setRailWidth(width);
                assertThat(rail.getPreferredSize().width).as("width %d", width).isEqualTo(UIScale.scale(width));
                assertThat(rail.getMinimumSize().width).isEqualTo(UIScale.scale(width));
                rail.setSize(rail.getPreferredSize().width, UIScale.scale(400));
                rail.doLayout();
                int side = UIScale.scale(width - 6);
                var buttons = rail.buttons();
                assertThat(buttons.get(0).getBounds()).isEqualTo(new Rectangle(UIScale.scale(3), UIScale.scale(4), side, side));
                var separator = Arrays.stream(rail.getComponents()).filter(JSeparator.class::isInstance).findFirst().orElseThrow();
                assertThat(separator.getWidth()).isEqualTo(UIScale.scale(width - 14));
                assertThat(buttons.get(1).getY()).as("a button, then a separator").isEqualTo(UIScale.scale(4) + side + UIScale.scale(4) + UIScale.scale(8));
                assertThat(buttons.get(1).getWidth()).isEqualTo(side);
            }
        });
    }

    @Test void theWindowAppliesALiveWidthAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.railWidth()).isEqualTo(28);
            owner.setRailWidth(48);
            assertThat(owner.railWidth()).isEqualTo(48);
            assertThat(owner.rail().getPreferredSize().width).isEqualTo(UIScale.scale(48));
            assertThat(changes.get()).isPositive();
            for (int width : new int[]{27, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setRailWidth(width));
                assertThat(owner.railWidth()).isEqualTo(48);
            }
            assertThat(content(launcher(new ArrayDeque<>())).railWidth()).isEqualTo(28);
        });
    }

    @Test void theSavedWidthAppliesLiveAndAnUnrelatedReloadKeepsTheSessionChoice() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().railWidth(44).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.railWidth()).isEqualTo(44);
            owner.setRailWidth(52);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.railWidth()).as("an unrelated change keeps the session width").isEqualTo(52);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).railWidth(30).build(), true);
            assertThat(owner.railWidth()).isEqualTo(30);
        });
    }

    @Test void viewControlStartsAtCurrentWidthCommitsOnlyOnOkAndCanReset() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>())); owner.setRailWidth(40);
            JMenuItem item = widthItem(owner);
            owner.confirmRailWidth = panel -> {
                JSpinner spinner = spinner(panel);
                assertThat(spinner.getValue()).isEqualTo(40);
                assertThat(((SpinnerNumberModel) spinner.getModel()).getMinimum()).isEqualTo(28);
                assertThat(((SpinnerNumberModel) spinner.getModel()).getMaximum()).isEqualTo(64);
                ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("50");
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.railWidth()).isEqualTo(50);
            owner.confirmRailWidth = panel -> { spinner(panel).setValue(60); return JOptionPane.CANCEL_OPTION; };
            item.doClick(); assertThat(owner.railWidth()).isEqualTo(50);
            owner.confirmRailWidth = panel -> {
                ((JButton) WindowTabsTest.named(panel, "resetRailWidth")).doClick();
                assertThat(spinner(panel).getValue()).isEqualTo(28);
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.railWidth()).isEqualTo(28);
            var errors = new ArrayList<String>(); owner.onError = errors::add;
            owner.confirmRailWidth = panel -> {
                ((JSpinner.DefaultEditor) spinner(panel).getEditor()).getTextField().setText("99");
                return JOptionPane.OK_OPTION;
            };
            item.doClick(); assertThat(owner.railWidth()).isEqualTo(28);
            assertThat(errors).singleElement().asString().contains("28").contains("64");
            assertThat(owner.commands().entries().stream().map(entry -> entry.command().id())).contains("view.rail_width");
        });
    }

    @Test void closingTheWindowIgnoresLaterWidthChanges() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setRailWidth(44);
            owner.close();
            owner.setRailWidth(60);
            assertThat(owner.railWidth()).isEqualTo(44);
            assertThat(owner.confirmRailWidth.applyAsInt(new JPanel())).isEqualTo(JOptionPane.CANCEL_OPTION);
        });
    }

    private static JSpinner spinner(java.awt.Container panel) {
        return (JSpinner) WindowTabsTest.named(panel, "railWidth");
    }

    private static JMenuItem widthItem(WindowContent owner) {
        JMenu view = owner.menuBar().getMenu(2);
        for (int i = 0; i < view.getItemCount(); i++) {
            JMenuItem item = view.getItem(i);
            if (item != null && item.getText().equals("Rail width…")) return item;
        }
        throw new AssertionError("View must expose Rail width");
    }
}
