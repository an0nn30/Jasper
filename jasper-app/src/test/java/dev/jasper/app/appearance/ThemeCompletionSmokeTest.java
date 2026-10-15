package dev.jasper.app.appearance;

import dev.jasper.app.config.UiFontConfig;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Completion smoke over both Slate Cobalt variants, with real live app publication. */
class ThemeCompletionSmokeTest {
    /** The loop leaves a 24-point interface font installed; a fresh controller restores the platform's. */
    @AfterEach void resetInterfaceFont() throws Exception { SwingUtilities.invokeAndWait(() -> ThemeTestSupport.controller(true)); }
    @org.junit.jupiter.api.io.TempDir Path temporary;
    @Test void bothVariantsKeepControlsAndInputAcrossBothRoutesAndFontSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var controller = ThemeTestSupport.controller(true)) {
                var state = ControlState.READY;
                var spec = new ViewSpec(new Nodes.Stack("smoke", state, Nodes.LayoutRole.VERTICAL, List.of(
                    new Nodes.Text("title",state,"Theme completion",Nodes.TextRole.PANEL_TITLE),
                    new Nodes.Field("name",state,Nodes.FieldKind.TEXT,"retained draft","Name","Name",""),
                    new Nodes.Choice("choice",state,Nodes.ChoiceKind.DROPDOWN,List.of(new Option("a","Alpha",Optional.empty()),new Option("b","Beta",Optional.empty())),"b","Choice",""),
                    new Nodes.Button("go",state,"Continue","",Nodes.ButtonKind.PRIMARY,"continue",""))));
                try (var chrome = new KitView(controller.appearance().context(ThemeRoute.CHROME),spec,e -> {});
                     var workspace = new KitView(controller.appearance().context(ThemeRoute.WORKSPACE),spec,e -> {})) {
                    var field = find(workspace,"name.input",JTextField.class);
                    field.select(2,8);
                    var choice = find(workspace,"choice.input",JComboBox.class);
                    for (int size : List.of(13,24)) for (boolean dark : new boolean[]{true, false}) {
                        controller.configure(dark,dev.jasper.app.config.TerminalSchemeChoice.MATCH,new UiFontConfig("system",size));
                        for (var view : List.of(chrome,workspace)) {
                            SwingUtilities.updateComponentTreeUI(view);
                            for (var role:SemanticColor.values()) assertThat(view==chrome
                                ? controller.appearance().context(ThemeRoute.CHROME).theme().semantic().color(role)
                                : controller.appearance().context(ThemeRoute.WORKSPACE).theme().semantic().color(role)).isNotNull();
                            ThemeMigrationCapture.save("completion-controls",(dark ? "slate-cobalt-dark" : "slate-cobalt-light")+"-font"+size,
                                view==chrome ? "chrome" : "workspace",view,560,440);
                        }
                        assertThat(find(workspace,"name.input",JTextField.class)).isSameAs(field);
                        assertThat(field.getText()).isEqualTo("retained draft");
                        assertThat(field.getSelectionStart()).isEqualTo(2);
                        assertThat(field.getSelectionEnd()).isEqualTo(8);
                        assertThat(find(workspace,"choice.input",JComboBox.class)).isSameAs(choice);
                        assertThat(((Option)choice.getSelectedItem()).id()).isEqualTo("b");
                        assertThat(field.getFont().getSize2D()).isGreaterThanOrEqualTo(size);
                    }
                }
            }
        });
    }
    private static <T extends Component> T find(Container root,String name,Class<T> type) {
        for(var child:root.getComponents()) {
            if(name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if(child instanceof Container nested) { T found=findOrNull(nested,name,type);if(found!=null)return found; }
        }
        throw new AssertionError("Missing "+name);
    }
    private static <T extends Component> T findOrNull(Container root,String name,Class<T> type) {
        for(var child:root.getComponents()) {
            if(name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if(child instanceof Container nested) { T found=findOrNull(nested,name,type);if(found!=null)return found; }
        }
        return null;
    }
}
