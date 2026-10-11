package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;
import dev.jasper.app.settings.*;
import dev.jasper.ui.model.*;
import java.util.*;

/** Coupled initial columns/lines, independently bounded and committed/reset as one row. */
final class InitialSizeEditor implements ValueEditor<Integer> {
    private final SettingsDraft draft;
    private final Editors.NumberField<Integer> columns,lines;
    private Integer lastLines;
    static Editors.Factory<Integer> factory(){return new Editors.Factory<>((setting,context)->new InitialSizeEditor(context.draft()),List.of("Initial size","columns","lines"));}
    InitialSizeEditor(SettingsDraft draft){this.draft=draft;columns=number(WINDOW_COLUMNS,"columns");lines=number(WINDOW_LINES,"lines");showRelated();}
    private static Editors.NumberField<Integer> number(Setting<Integer> setting,String unit){var kind=(SettingKind.IntegerRange)setting.kind();return new Editors.NumberField<>(setting.label(),new Nodes.Numeric(kind.min(),kind.max(),1,0),unit,Integer::valueOf,Nodes.FieldPresentation.STEPPER);}
    @Override public Nodes.Node node(String id,ControlState state){return new Nodes.Stack(id+":pair",state,Nodes.LayoutRole.SETTINGS_CONTROLS,List.of(columns.node(id,state),new Nodes.Text(id+":by",ControlState.READY,"×",Nodes.TextRole.DESCRIPTION),lines.node(WINDOW_LINES.name(),state)));}
    @Override public Integer value(){var value=lines.value();if(WINDOW_LINES.kind().check(value).isPresent())throw new IllegalArgumentException("Invalid lines");return columns.value();}
    @Override public void show(Integer value){columns.show(value);lastLines=draft.value(WINDOW_LINES);lines.show(lastLines);}
    @Override public void showRelated(){int current=draft.value(WINDOW_LINES);if(!Objects.equals(lastLines,current)){lines.show(current);lastLines=current;}}
    @Override public boolean event(String id,UiEvent event){return columns.event(id,event)||lines.event(WINDOW_LINES.name(),event);}
    @Override public Map<Setting<?>,Optional<?>> companionValues(){return Map.of(WINDOW_LINES,Optional.of(lines.value()));}
    @Override public List<Setting<?>> relatedSettings(){return List.of(WINDOW_LINES);}
    @Override public String rowLabel(String label){return "Initial size";}
}
