package dev.jasper.app.plugins;

import dev.jasper.sdk.settings.*;
import dev.jasper.sdk.ui.*;
import dev.jasper.app.settingsui.SearchText;
import java.util.*;
import java.util.function.Function;

/** Standard descriptor rows; the settings page retains the draft and lifetime. */
final class KitSettingRows {
    private final SettingsPanelHost host;
    private final java.util.function.Consumer<Setting<?>> unset;
    private final Function<Setting<?>, SettingSpec<?>> specs;
    private final Map<String, Setting<?>> controls = new LinkedHashMap<>();
    private final Map<String, String> errors = new HashMap<>(), edits = new HashMap<>();
    private String query = "";
    KitSettingRows(SettingsPanelHost host, Function<Setting<?>, SettingSpec<?>> specs, java.util.function.Consumer<Setting<?>> unset) { this.host=host;this.specs=specs;this.unset=unset; }
    boolean valid(){return errors.isEmpty();}
    void staged(Setting<?> setting){String id="settings:"+setting.key();errors.remove(id);edits.remove(id);}
    void reset(){errors.clear();edits.clear();}
    void highlight(String value){query=Objects.requireNonNull(value);}
    Nodes.Node row(Setting<?> setting){
        var spec=specs.apply(setting);String id="settings:"+setting.key();controls.put(id,setting);
        Object value=host.pending(setting);var state=new ControlState(true,true,false,errors.getOrDefault(id,""));
        Nodes.Node control;
        if(spec.kind() instanceof SettingKind.Bool)control=new Nodes.Toggle(id,state,spec.label(),(Boolean)value,id,false);
        else if(spec.kind() instanceof SettingKind.OneOf<?> choices){
            var options=choices.choices().stream().map(c -> new Option(c.token(),c.displayName(),Optional.empty())).toList();
            String selected=choices.choices().stream().filter(c -> c.value().equals(value)).findFirst().orElseThrow().token();
            control=new Nodes.Choice(id,state,Nodes.ChoiceKind.DROPDOWN,options,selected,spec.label(),id);
        }else {
            String text=value instanceof List<?> list?String.join("\n",list.stream().map(Object::toString).toList()):value.toString();
            var kind=spec.kind() instanceof SettingKind.IntegerRange?Nodes.FieldKind.NUMBER:spec.kind() instanceof SettingKind.StringList?Nodes.FieldKind.TEXTAREA:spec.editor().orElse(null) instanceof EditorHint.File?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;
            var numeric=spec.kind() instanceof SettingKind.IntegerRange range?Optional.of(new Nodes.Numeric(range.min(),range.max(),1,0)):Optional.<Nodes.Numeric>empty();
            // A bounded integer is the Settings stepper, its declared unit after the value.
            control=new Nodes.Field(id,state,kind,edits.getOrDefault(id,text),numeric.isPresent()?spec.unit():"",spec.label(),"",numeric,numeric.isPresent()?Nodes.FieldPresentation.STEPPER:Nodes.FieldPresentation.STANDARD);
        }
        var reset=new Nodes.Button(id+":reset",ControlState.READY,"Reset","",Nodes.ButtonKind.GHOST,id+":reset","Reset to default");
        return new Nodes.SettingRow(id+":row",state,spec.label(),spec.description(),
            new Nodes.Stack(id+":controls",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(control,reset)),false);
    }
    private static boolean matches(SettingSpec<?> spec,String query){return SearchText.matches(String.join(" ",spec.label(),spec.description(),String.join(" ",spec.keywords())),query);}
    @SuppressWarnings({"unchecked","rawtypes"})
    boolean event(UiEvent event){
        boolean reset=event instanceof UiEvent.Action && event.id().endsWith(":reset");
        String id=reset?event.id().substring(0,event.id().length()-6):event.id();
        var setting=controls.get(id);if(setting==null)return false;
        var spec=specs.apply(setting);
        try{
            Object value;
            if(reset){unset.accept(setting);host.changed();return true;}
            else if(event instanceof UiEvent.Toggled toggle && spec.kind() instanceof SettingKind.Bool)value=toggle.checked();
            else if(event instanceof UiEvent.Selected selected && spec.kind() instanceof SettingKind.OneOf<?> choice)
                value=choice.choices().stream().filter(c -> selected.ids().contains(c.token())).findFirst().orElseThrow().value();
            else if(event instanceof UiEvent.Edited edited){
                edits.put(id,edited.text());
                value=switch(spec.kind()){
                    case SettingKind.IntegerRange ignored -> Integer.valueOf(edited.text());
                    case SettingKind.NumberRange ignored -> Double.valueOf(edited.text());
                    case SettingKind.StringList ignored -> edited.text().isEmpty()?List.of():List.of(edited.text().split("\n",-1));
                    case SettingKind.Text ignored -> edited.text();
                    default -> throw new IllegalArgumentException("Unexpected edit");
                };
            }else return false;
            host.stage((Setting)setting,value);errors.remove(id);
        }catch(RuntimeException failure){errors.put(id,failure instanceof IllegalArgumentException && !(failure instanceof NumberFormatException) && failure.getMessage()!=null ? failure.getMessage() : "Enter a valid value.");}
        host.changed();return true;
    }
    ViewSpec refresh(ViewSpec spec){return new ViewSpec(replace(spec.root()));}
    private Nodes.Node replace(Nodes.Node n){
        // A host row is rebuilt from the draft, keeping the presentation the plugin asked for (its search highlight).
        for(var entry:controls.entrySet())if(n.id().equals(entry.getKey()+":row")){
            var fresh=(Nodes.SettingRow)row(entry.getValue());
            boolean highlighted=matches(specs.apply(entry.getValue()),query) || n instanceof Nodes.SettingRow asked && asked.highlighted();
            return n instanceof Nodes.SettingRow asked
                ? new Nodes.SettingRow(fresh.id(),fresh.state(),asked.title(),asked.description(),fresh.control(),asked.dependent(),highlighted,asked.titleNote())
                : fresh;
        }
        return switch(n){
            case Nodes.Stack v -> new Nodes.Stack(v.id(),v.state(),v.role(),v.children().stream().map(this::replace).toList(),v.cancelAction());
            case Nodes.Card v -> new Nodes.Card(v.id(),v.state(),v.kind(),v.children().stream().map(this::replace).toList());
            case Nodes.Section v -> new Nodes.Section(v.id(),v.state(),v.title(),replace(v.body()));
            case Nodes.Form v -> new Nodes.Form(v.id(),v.state(),v.kind(),v.title(),v.hero().map(this::replace),v.sections().stream().map(this::replace).toList(),v.preview().map(this::replace),v.actions(),v.cancelAction(),v.submission());
            case Nodes.Split v -> new Nodes.Split(v.id(),v.state(),v.kind(),v.children().stream().map(this::replace).toList());
            case Nodes.Panel v -> new Nodes.Panel(v.id(),v.state(),(Nodes.Header)replace(v.header()),v.searchOrNavigation().map(this::replace),replace(v.body()),v.footer().map(this::replace));
            case Nodes.FormRow v -> new Nodes.FormRow(v.id(),v.state(),v.label(),v.caption(),replace(v.control()),v.trailing().map(this::replace),v.highlighted());
            case Nodes.SettingRow v -> new Nodes.SettingRow(v.id(),v.state(),v.title(),v.description(),replace(v.control()),v.dependent(),v.highlighted(),v.titleNote());
            case Nodes.Empty v -> new Nodes.Empty(v.id(),v.state(),replace(v.icon()),v.title(),v.description(),v.actions().stream().map(this::replace).toList());
            case Nodes.Header v -> new Nodes.Header(v.id(),v.state(),v.title(),v.count(),v.context().map(this::replace),v.primary(),v.overflow());
            default -> n;
        };
    }
}
