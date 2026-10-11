package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_FONT_SIZE;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.ui.model.*;
import java.util.*;

/** A platform-size switch and a manual stepper, kept mounted while inheritance controls visibility. */
final class PlatformFontSize implements ValueEditor<Float> {
    private final Editors.NumberField<Float> number;
    private boolean inherited=true;
    static Editors.Factory<Float> factory(){return new Editors.Factory<>((setting,context)->new PlatformFontSize(),List.of("Use platform size","Use platform font size"));}
    PlatformFontSize(){var kind=(SettingKind.NumberRange)UI_FONT_SIZE.kind();number=new Editors.NumberField<>("Font size",new Nodes.Numeric(kind.min(),kind.max(),.5,2),"pt",Float::valueOf,Nodes.FieldPresentation.STEPPER);number.show(UI_FONT_SIZE.example().orElse(13f));}
    @Override public boolean fullSpan(){return true;}
    @Override public boolean cardRows(){return true;}
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var toggle=new Nodes.Toggle(id+":default",state,"Use platform size",inherited,id+":default",true);
        var reset=new Nodes.Button(id+":reset",new ControlState(!inherited,state.enabled(),false,""),"","refresh",Nodes.ButtonKind.GHOST,id+":reset","Reset interface font size to platform default");
        var control=new Nodes.Stack(id+":toggle-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(toggle,reset));
        var manual=new ControlState(!inherited,state.enabled(),state.busy(),state.error());
        return new Nodes.Stack(id+":optional",state,Nodes.LayoutRole.VERTICAL,List.of(new Nodes.SettingRow("row:"+id,state,"Use platform font size","",control,false,highlighted),new Nodes.SettingRow(id+":manual-row",manual,"Font size","",number.node(id,manual),true,highlighted)));
    }
    @Override public Float value(){return inherited?UI_FONT_SIZE.defaultValue():number.value();}
    @Override public void show(Float value){inherited=value.equals(UI_FONT_SIZE.defaultValue());if(!inherited)number.show(value);}
    @Override public boolean event(String id,UiEvent event){if(event instanceof UiEvent.Toggled toggle&&toggle.id().equals(id+":default")){inherited=toggle.checked();return true;}return number.event(id,event);}
    @Override public boolean removesAtDefault(){return true;}
}
