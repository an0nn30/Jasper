package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;
import dev.jasper.app.config.*;
import dev.jasper.ui.model.*;
import java.util.*;

/** Workspace overrides read the current draft's effective authored source, retaining inheritance explicitly. */
final class WorkspaceEditors {
    private WorkspaceEditors() { }
    static Editors.Factory<Float> opacity(ThemeChoices choices) { return new Editors.Factory<>((setting,context)->new Opacity(choices,context.draft()),List.of("Use theme's value","opacity","transparency")); }
    static Editors.Factory<WorkspaceBlur> blur(ThemeChoices choices) { return new Editors.Factory<>((setting,context)->new Blur(choices,context.draft()),List.of("Use theme's value","blur")); }
    static Optional<ThemeChoices.Source> source(ThemeChoices choices,SettingsDraft draft) {
        String workspace=draft.value(UI_THEME_WORKSPACE);
        if(!workspace.equalsIgnoreCase("match")) {var found=choices.find(workspace,ThemeSource.CATALOG);if(found.isPresent())return found;}
        if(draft.value(UI_THEME_FOLLOW_SYSTEM)&&choices.systemDark().get().isPresent())return choices.find(choices.systemDark().get().get()?"Jasper Dark":"Jasper Light",ThemeSource.BUNDLED);
        return choices.find(draft.value(UI_THEME_NAME),draft.value(UI_THEME_SOURCE)).or(()->choices.find("Jasper Dark",ThemeSource.BUNDLED));
    }
    static final class Opacity implements ValueEditor<Float> {
        final ThemeChoices choices;final SettingsDraft draft;float value;
        Opacity(ThemeChoices choices,SettingsDraft draft){this.choices=choices;this.draft=draft;value=UI_WORKSPACE_OPACITY.defaultValue();}
        private float effective(){return value==UI_WORKSPACE_OPACITY.defaultValue()?source(choices,draft).map(ThemeChoices.Source::opacity).orElse(100f):value;}
        @Override public boolean fullSpan(){return true;}
        @Override public boolean cardRows(){return true;}
        @Override public Nodes.Node node(String id,ControlState state){
            boolean inherited=value==UI_WORKSPACE_OPACITY.defaultValue();
            var toggle=new Nodes.Toggle(id+":default",state,"Use theme's opacity",inherited,id+":default",true);
            var slider=new Nodes.Field(id,new ControlState(state.visible(),state.enabled()&&!inherited,state.busy(),state.error()),Nodes.FieldKind.NUMBER,Editors.NumberField.format(effective(),0),"%","Workspace opacity","",Optional.of(new Nodes.Numeric(10,100,1,0)),Nodes.FieldPresentation.SLIDER);
            var reset=new Nodes.Button(id+":reset",new ControlState(!inherited,state.enabled(),false,""),"","refresh",Nodes.ButtonKind.GHOST,id+":reset","Reset workspace opacity to theme value");
            var blank=new Nodes.Button(id+":reserved",new ControlState(false,false,false,""),"","refresh",Nodes.ButtonKind.GHOST,id+":reserved","Reserved reset space");
            return new Nodes.Stack(id+":optional",state,Nodes.LayoutRole.VERTICAL,List.of(
                new Nodes.SettingRow(id+":inheritance-row",state,"Use theme's opacity","",new Nodes.Stack(id+":inheritance-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(toggle,blank)),false,false),
                new Nodes.SettingRow("row:"+id,state,"Opacity","",new Nodes.Stack(id+":slider-controls",state,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(slider,reset)),true,false)));
        }
        @Override public Float value(){return value;}
        @Override public void show(Float value){this.value=value;}
        @Override public boolean event(String id,UiEvent event){
            if(event instanceof UiEvent.Toggled toggle&&toggle.id().equals(id+":default")){float actual=effective();value=toggle.checked()?UI_WORKSPACE_OPACITY.defaultValue():actual;return true;}
            if(event instanceof UiEvent.Edited edit&&edit.id().equals(id)){value=Float.parseFloat(edit.text());return true;}return false;
        }
        @Override public boolean removesAtDefault(){return true;}
    }
    static final class Blur implements ValueEditor<WorkspaceBlur> {
        final ThemeChoices choices;final SettingsDraft draft;WorkspaceBlur value=WorkspaceBlur.THEME;
        Blur(ThemeChoices choices,SettingsDraft draft){this.choices=choices;this.draft=draft;}
        @Override public Nodes.Node node(String id,ControlState state){
            boolean enabled=value==WorkspaceBlur.ON||value==WorkspaceBlur.THEME&&source(choices,draft).map(ThemeChoices.Source::blur).orElse(false);
            var toggle=new Nodes.Toggle(id,state,value==WorkspaceBlur.THEME?"Use theme's value":"Blur desktop behind workspace",enabled,id,false);
            var menu=new Nodes.MenuButton(id+":inheritance",state,"","chevron.down",Nodes.ButtonKind.GHOST,"",List.of(new MenuItem(id+":theme","Use theme's value","",id+":theme",true,value==WorkspaceBlur.THEME)),"Workspace blur inheritance");
            return new Nodes.Stack(id+":line",state,Nodes.LayoutRole.SETTINGS_CONTROLS,List.of(toggle,menu));
        }
        @Override public String rowLabel(String label){return "Background blur";}
        @Override public WorkspaceBlur value(){return value;}
        @Override public void show(WorkspaceBlur value){this.value=value;}
        @Override public boolean event(String id,UiEvent event){
            if(event instanceof UiEvent.Toggled toggle&&toggle.id().equals(id)){value=toggle.checked()?WorkspaceBlur.ON:WorkspaceBlur.OFF;return true;}
            if(event instanceof UiEvent.Action action&&action.id().equals(id+":theme")){value=WorkspaceBlur.THEME;return true;}return false;
        }
        @Override public boolean removesAtDefault(){return true;}
    }
}
