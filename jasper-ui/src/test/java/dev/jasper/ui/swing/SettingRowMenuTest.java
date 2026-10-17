package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1: a Settings row's own menu, by the popup trigger or the keyboard. */
class SettingRowMenuTest {
    static ViewSpec row(List<MenuItem> menu) {
        return new ViewSpec(new Nodes.SettingRow("row", ControlState.READY, "Visible rows", "", new Nodes.Field("rows", ControlState.READY,
            Nodes.FieldKind.TEXT, "8", "", "Visible rows", ""), false, false, "", menu));
    }
    static final MenuItem RESET = new MenuItem("rows:reset", "Reset to Default", "", "rows:reset", true, false);

    @Test void aRowsMenuReportsItsItemsActionAndARowWithoutOneHasNone() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, row(List.of(RESET)), events::add)) {
                var control = (SettingRows.RowControl) view.control("row");
                control.context(control.component, 4, 4);
                assertThat(control.popup.getComponentCount()).isEqualTo(1);
                var item = (JMenuItem) control.popup.getComponent(0);
                assertThat(item.getText()).isEqualTo("Reset to Default");
                item.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("rows:reset"));
                view.update(row(List.of()));
                control.context(control.component, 4, 4);
                assertThat(control.popup.getComponentCount()).isZero();
                assertThat(control.component.getMouseListeners()).as("a row without a menu takes no mouse events")
                    .noneMatch(listener -> listener.getClass().getName().contains("SettingRows"));
            } finally { release(context); }
        });
    }

    @Test void thePopupTriggerFillsTheMenuAndADisabledItemStaysDisabled() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(List.of(new MenuItem("rows:reset", "Reset to Default", "", "rows:reset", false, false))), event -> { })) {
                var control = (SettingRows.RowControl) view.control("row");
                var press = new MouseEvent(control.component, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON3_DOWN_MASK,
                    4, 4, 1, true, MouseEvent.BUTTON3);
                for (var listener : control.component.getMouseListeners()) listener.mousePressed(press);
                assertThat(control.popup.getComponentCount()).isEqualTo(1);
                assertThat(control.popup.getComponent(0).isEnabled()).isFalse();
            } finally { release(context); }
        });
    }

    @Test void theKeyboardOpensTheRowsMenu() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, row(List.of(RESET)), event -> { })) {
                var control = (SettingRows.RowControl) view.control("row");
                for (var stroke : List.of(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0))) {
                    control.clearPopup();
                    var name = control.component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
                    assertThat(name).as(stroke.toString()).isNotNull();
                    control.component.getActionMap().get(name).actionPerformed(new java.awt.event.ActionEvent(control.component, 0, ""));
                    assertThat(control.popup.getComponentCount()).as(stroke.toString()).isEqualTo(1);
                }
            } finally { release(context); }
        });
    }
}
