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
    /** A path's Browse action is its row id followed by this. */
    static final String BROWSE=":browse";
    private final java.util.function.BiFunction<String,Optional<java.nio.file.Path>,Optional<java.nio.file.Path>> chooseFile;
    /** @param chooseFile asks for an existing file, as the generated page's Browse does: title and initial path in, choice out */
    KitSettingRows(SettingsPanelHost host, Function<Setting<?>, SettingSpec<?>> specs, java.util.function.Consumer<Setting<?>> unset,
                   java.util.function.BiFunction<String,Optional<java.nio.file.Path>,Optional<java.nio.file.Path>> chooseFile) {
        this.host=host;this.specs=specs;this.unset=unset;this.chooseFile=chooseFile;
    }
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
        }else if(PluginSettingsPage.Hint.of(spec).font() && spec.kind() instanceof SettingKind.Text){
            // As the generated pages: free text over the installed families, which filter as you type.
            control=font(id,state,spec,edits.getOrDefault(id,value.toString()));
        }else {
            // A decimal is stored as a float: show it at that precision, not its widened double digits.
            String text=value instanceof List<?> list?String.join("\n",list.stream().map(Object::toString).toList())
                :spec.kind() instanceof SettingKind.NumberRange && value instanceof Number number?Float.toString(number.floatValue()):value.toString();
            var numeric=numeric(spec);
            var kind=numeric.isPresent()?Nodes.FieldKind.NUMBER:spec.kind() instanceof SettingKind.StringList?Nodes.FieldKind.TEXTAREA:PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.FILE?Nodes.FieldKind.PATH:Nodes.FieldKind.TEXT;
            // As the generated pages: a bounded integer is the Settings stepper, a bounded decimal a number field;
            // both show the declared unit after the value.
            // As the generated pages: a path ends with Browse.
            control=new Nodes.Field(id,state,kind,edits.getOrDefault(id,text),numeric.isPresent()?spec.unit():"",spec.label(),kind==Nodes.FieldKind.PATH?id+BROWSE:"",numeric,
                spec.kind() instanceof SettingKind.IntegerRange?Nodes.FieldPresentation.STEPPER:Nodes.FieldPresentation.STANDARD);
        }
        boolean resettable=!sameValue(value,spec.defaultValue())||edits.containsKey(id)||errors.containsKey(id);
        return new Nodes.SettingRow(id+":row",state,spec.label(),spec.description(),trailing(id,spec,resettable,control),false);
    }
    /** As the generated pages' font editor: free text over the installed families, which filter as you type. */
    private static Nodes.Choice font(String id,ControlState state,SettingSpec<?> spec,String text){
        List<String> families=dev.jasper.app.settingsui.Editors.fontFamilies(PluginSettingsPage.Hint.of(spec)==PluginSettingsPage.Hint.MONOSPACED_FONT);
        var options=new ArrayList<Option>();
        for(int i=0;i<families.size();i++)options.add(new Option("family-"+i,families.get(i),Optional.empty()));
        return new Nodes.Choice(id,state,Nodes.ChoiceKind.SEARCHABLE,options,"",spec.label(),"","",
            Optional.of(new Nodes.ChoiceSearch(0,dev.jasper.app.settingsui.Editors.NO_FONT_MATCHES,Optional.of(text))));
    }
    /** Where a path's Browse starts: the current path, when the text is one. */
    private static Optional<java.nio.file.Path> initial(String current){
        if(current.isBlank())return Optional.empty();
        try{return Optional.of(java.nio.file.Path.of(current));}catch(java.nio.file.InvalidPathException notAPath){return Optional.empty();}
    }
    /** The bounds of a numeric setting; decimals show two places and step by a tenth, as the generated pages do. */
    static Optional<Nodes.Numeric> numeric(SettingSpec<?> spec){
        if(spec.kind() instanceof SettingKind.IntegerRange range)return Optional.of(new Nodes.Numeric(range.min(),range.max(),1,0));
        if(spec.kind() instanceof SettingKind.NumberRange range)return Optional.of(new Nodes.Numeric(range.min(),range.max(),0.1,2));
        return Optional.empty();
    }
    /** Pending values come back in the app's storage type: a decimal stored as a float compares at float precision. */
    static boolean sameValue(Object value,Object defaultValue){
        if(value instanceof Number a && defaultValue instanceof Number b && !(a instanceof Integer && b instanceof Integer))return a.floatValue()==b.floatValue();
        return Objects.equals(value,defaultValue);
    }
    /**
     * The generated Settings pages' trailing arrangement: the control in the settings control slot, then the icon-only
     * Reset, shown while the pending value differs from the default or an unparsed edit is pending; its slot stays
     * reserved so controls never move.
     */
    static Nodes.Stack trailing(String id,SettingSpec<?> spec,boolean resettable,Nodes.Node control){
        var reset=new Nodes.Button(id+":reset",new ControlState(resettable,true,false,""),"","reset",
            Nodes.ButtonKind.GHOST,id+":reset","Reset "+spec.label()+" to default");
        return new Nodes.Stack(id+":controls",ControlState.READY,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(control,reset));
    }
    private static boolean matches(SettingSpec<?> spec,String query){return SearchText.matches(String.join(" ",spec.label(),spec.description(),String.join(" ",spec.keywords())),query);}
    @SuppressWarnings({"unchecked","rawtypes"})
    boolean event(UiEvent event){
        boolean reset=event instanceof UiEvent.Action && event.id().endsWith(":reset");
        boolean browse=event instanceof UiEvent.Action && event.id().endsWith(BROWSE);
        String id=reset?event.id().substring(0,event.id().length()-6):browse?event.id().substring(0,event.id().length()-BROWSE.length()):event.id();
        var setting=controls.get(id);if(setting==null)return false;
        var spec=specs.apply(setting);
        try{
            Object value;
            if(reset){unset.accept(setting);host.changed();return true;}
            else if(browse){
                if(PluginSettingsPage.Hint.of(spec)!=PluginSettingsPage.Hint.FILE)return false;
                // As the generated pages: Browse asks for an existing file, starting at the current one; cancelling changes nothing.
                var chosen=chooseFile.apply(PluginSettingsPage.Hint.FILE_CHOOSER_TITLE,initial(String.valueOf(host.pending(setting))));
                if(chosen.isEmpty())return true;
                value=chosen.get().toString();
            }
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
