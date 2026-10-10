package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

@SuppressWarnings("unchecked")
class ChoicesTest {
    static Choice choice(ChoiceKind kind, String selected, ControlState state) {
        return new Choice("choice", state, kind, List.of(new Option("a", "<html>Alpha", Optional.of(Hue.BLUE)), new Option("b", "Beta", Optional.empty())), selected, "Protocol", "choose-protocol");
    }
    @Test void dropdownRetainsKitDelegateSelectionAndPopupStyleAcrossSwingUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "b", ControlState.READY)), events::add)) {
                var combo = find(view, "choice.input", JComboBox.class);
                var delegate = combo.getUI().getClass();
                for (int i = 0; i < 3; i++) {
                    SwingUtilities.updateComponentTreeUI(view);
                    assertThat(combo.getUI().getClass()).isEqualTo(delegate);
                    assertThat(combo.getSelectedItem()).isEqualTo(choice(ChoiceKind.DROPDOWN, "b", ControlState.READY).options().get(1));
                    var popup = (javax.swing.plaf.basic.BasicComboPopup) combo.getUI().getAccessibleChild(combo, 0);
                    assertThat(popup.getList().getBackground()).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_MENU));
                    assertThat(popup.getList().getFixedCellHeight()).isGreaterThan(0);
                    combo.setSize(240, 40); combo.doLayout();
                    assertThat(ButtonsTest.paint(combo).getRGB(120, 6)).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL).getRGB());
                }
                assertThat(events).isEmpty();
                combo.setSelectedIndex(0);
                assertThat(events).containsExactly(new UiEvent.Selected("choice", Set.of("a")));
            } finally { release(context); }
        });
    }
    @Test void existingDropdownPopupTracksSemanticMenuColorsAndRows() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            try(var view=new KitView(context,stack(choice(ChoiceKind.DROPDOWN,"a",ControlState.READY)),e -> {})) {
                var combo=find(view,"choice.input",JComboBox.class);
                var popup=(javax.swing.plaf.basic.BasicComboPopup)combo.getUI().getAccessibleChild(combo,0);
                var list=popup.getList();
                for(var color:List.of(java.awt.Color.RED,java.awt.Color.GREEN)) {
                    var base=KitFixtures.dark();var colors=new EnumMap<dev.jasper.ui.theme.SemanticColor,java.awt.Color>(dev.jasper.ui.theme.SemanticColor.class);
                    for(var role:dev.jasper.ui.theme.SemanticColor.values())colors.put(role,base.semantic().color(role));
                    colors.put(dev.jasper.ui.theme.SemanticColor.SURFACE_MENU,color);
                    var theme=new dev.jasper.ui.theme.GeneratedTheme(base.name(),base.dark(),base.author(),base.tokens(),base.syntax(),base.terminal(),base.uiKeys(),base.icons(),base.glass(),new dev.jasper.ui.theme.SemanticPalette(colors));
                    context.binding().publish(new dev.jasper.ui.theme.ThemePair(theme,theme));
                    assertThat(popup.getList()).isSameAs(list);assertThat(list.getBackground()).isEqualTo(color);
                    assertThat(list.getFixedCellHeight()).isGreaterThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(28));
                    var row=(JComponent)combo.getRenderer().getListCellRendererComponent(list,combo.getItemAt(0),0,true,false);
                    row.setSize(com.formdev.flatlaf.util.UIScale.scale(220),list.getFixedCellHeight());
                    assertThat(ButtonsTest.paint(row).getRGB(com.formdev.flatlaf.util.UIScale.scale(200),list.getFixedCellHeight()/2)).isEqualTo(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT).getRGB());
                    popup.setSize(com.formdev.flatlaf.util.UIScale.scale(180),com.formdev.flatlaf.util.UIScale.scale(80));
                    assertThat(ButtonsTest.paint(popup).getRGB(com.formdev.flatlaf.util.UIScale.scale(90),com.formdev.flatlaf.util.UIScale.scale(2))).isEqualTo(color.getRGB());
                }
            }finally{release(context);}
        });
    }
    @Test void pickerUsesChoiceSurfaceWhileSecondaryButtonsRetainTheirIndependentColors() {
        Edt.run(() -> {
            int width = com.formdev.flatlaf.util.UIScale.scale(240), height = com.formdev.flatlaf.util.UIScale.scale(40);
            int sampleX = com.formdev.flatlaf.util.UIScale.scale(120), sampleY = com.formdev.flatlaf.util.UIScale.scale(6);
            var base = KitFixtures.dark();
            var colors = new EnumMap<dev.jasper.ui.theme.SemanticColor, java.awt.Color>(dev.jasper.ui.theme.SemanticColor.class);
            for (var role : dev.jasper.ui.theme.SemanticColor.values()) colors.put(role, base.semantic().color(role));
            colors.put(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL, new java.awt.Color(23, 87, 139));
            colors.put(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL_HOVER, new java.awt.Color(42, 121, 175));
            colors.put(dev.jasper.ui.theme.SemanticColor.BUTTON_SECONDARY_BG, new java.awt.Color(193, 32, 68));
            colors.put(dev.jasper.ui.theme.SemanticColor.BUTTON_SECONDARY_BG_HOVER, new java.awt.Color(228, 61, 102));
            var theme = new dev.jasper.ui.theme.GeneratedTheme(base.name(), base.dark(), base.author(), base.tokens(), base.syntax(),
                base.terminal(), base.uiKeys(), base.icons(), base.glass(), new dev.jasper.ui.theme.SemanticPalette(colors));
            var context = KitFixtures.context(); context.binding().publish(new dev.jasper.ui.theme.ThemePair(theme, theme));
            var secondary = new Nodes.Button("secondary", ControlState.READY, "Browse", "", ButtonKind.SECONDARY, "browse", "");
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY), secondary), e -> {})) {
                var dropdown = find(view, "choice.input", JComboBox.class); dropdown.setSize(width, height); dropdown.doLayout();
                assertThat(ButtonsTest.paint(dropdown).getRGB(sampleX, sampleY)).isEqualTo(new java.awt.Color(23, 87, 139).getRGB());
                view.update(stack(choice(ChoiceKind.PICKER, "a", ControlState.READY), secondary));
                var picker = find(view, "choice.input", JButton.class); picker.setSize(width, height);
                assertThat(ButtonsTest.paint(picker).getRGB(sampleX, sampleY)).isEqualTo(new java.awt.Color(23, 87, 139).getRGB());
                picker.getModel().setRollover(true);
                assertThat(ButtonsTest.paint(picker).getRGB(sampleX, sampleY)).isEqualTo(new java.awt.Color(42, 121, 175).getRGB());
                var button = find(view, "secondary", JButton.class); button.setSize(width, height);
                assertThat(ButtonsTest.paint(button).getRGB(sampleX, sampleY)).isEqualTo(new java.awt.Color(193, 32, 68).getRGB());
                button.getModel().setRollover(true);
                assertThat(ButtonsTest.paint(button).getRGB(sampleX, sampleY)).isEqualTo(new java.awt.Color(228, 61, 102).getRGB());
            } finally { release(context); }
        });
    }
    @Test void dropdownDispatchesNodeAndImmutableSelectionWithoutEventsFromUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)), events::add)) {
                var combo = find(view, "choice.input", JComboBox.class); combo.setSelectedIndex(1);
                assertThat(events).containsExactly(new UiEvent.Selected("choice", Set.of("b")));
                assertThatThrownBy(() -> ((UiEvent.Selected)events.getFirst()).ids().clear()).isInstanceOf(UnsupportedOperationException.class);
                events.clear(); view.update(stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)));
                assertThat(find(view, "choice.input", JComboBox.class)).isSameAs(combo); assertThat(combo.getSelectedIndex()).isZero(); assertThat(events).isEmpty();
                var renderer = combo.getRenderer().getListCellRendererComponent(new JList<>(), combo.getItemAt(0), 0, false, false);
                assertThat(((JComponent)renderer).getClientProperty("html")).isNull();
            } finally { release(context); }
        });
    }
    @Test void segmentsKeyboardSelectAndPickerOpensItsConfiguredAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(choice(ChoiceKind.SEGMENTED, "a", ControlState.READY)), events::add)) {
                var a = find(view, "choice.option.a", JButton.class);
                assertThat(a.getFont().getAttributes().get(java.awt.font.TextAttribute.WEIGHT)).isEqualTo(java.awt.font.TextAttribute.WEIGHT_MEDIUM);
                var key = a.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT")); assertThat(key).isNotNull();
                a.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(a, 0, ""));
                assertThat(events).containsExactly(new UiEvent.Selected("choice", Set.of("b")));
                events.clear(); view.update(stack(choice(ChoiceKind.PICKER, "a", ControlState.READY)));
                find(view, "choice.input", JButton.class).doClick(0); assertThat(events).containsExactly(new UiEvent.Action("choose-protocol"));
            } finally { release(context); }
        });
    }
    @Test void emptyPickerUsesPlaceholderRoleAndTrailingChevron() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(choice(ChoiceKind.PICKER, "", ControlState.READY)), e -> {})) {
                var picker = find(view, "choice.input", JButton.class); assertThat(picker.getText()).isEqualTo("Choose…");
                assertThat(picker.getForeground()).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                picker.setSize(180, 34); var image = ButtonsTest.paint(picker); int right = picker.getWidth()-com.formdev.flatlaf.util.UIScale.scale(16);
                int count=0; int color=context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL).getRGB();
                for (int x=right-5; x<right+5; x++) for (int y=10; y<24; y++) if (image.getRGB(x,y)!=color) count++;
                assertThat(count).isPositive();
            } finally { release(context); }
        });
    }
    @Test void disabledSegmentTrackUsesDisabledOpacityOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(choice(ChoiceKind.SEGMENTED, "a", new ControlState(true, false, false, ""))), e -> {})) {
                var track = find(view, "choice.input", JComponent.class); track.setSize(200, 32);
                int alpha=(ButtonsTest.paint(track).getRGB(100, com.formdev.flatlaf.util.UIScale.scale(1))>>>24)&255;
                assertThat(alpha).isBetween(106,108);
            } finally { release(context); }
        });
    }
    @Test void busyAndParentDisabledChoicesCannotDispatchAndSelectionCallbacksMayUpdate() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>(); KitView[] owner = {null};
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)), event -> {
                events.add(event); owner[0].update(stack(choice(ChoiceKind.DROPDOWN, "b", new ControlState(true, true, true, ""))));
            })) {
                owner[0] = view; var combo = find(view, "choice.input", JComboBox.class); combo.setSelectedIndex(1);
                assertThat(combo.isEnabled()).isFalse(); combo.setSelectedIndex(0); assertThat(events).hasSize(1);
                assertThat(combo.getAccessibleContext().getAccessibleName()).isEqualTo("Protocol");
            } finally { release(context); }
        });
    }
}
