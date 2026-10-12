package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;
import dev.jasper.app.config.ThemeSource;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.*;
import java.util.*;

/** One coupled editor for a manual source and the opt-in system policy. It stages no automatic OS changes. */
final class ThemeSelection extends Editors.ThemePick {
    private final ThemeChoices themes;
    private final SettingsDraft draft;
    private final String label;
    private final Subscription listening;
    private Runnable render = () -> {};
    ThemeSelection(String label, ThemeChoices themes, PageContext context) {
        super(label,themes.names(),Map.of(),themes.importer().apply(context));
        this.label=label;this.themes=themes;this.draft=context.draft();
        listening=themes.onChanged().apply(()->render.run());
    }
    @Override public boolean cardRows(){return true;}
    @Override public boolean fullSpan() { return true; }
    @Override public List<Setting<?>> relatedSettings() { return List.of(UI_THEME_SOURCE,UI_THEME_FOLLOW_SYSTEM); }
    private Optional<ThemeChoices.Source> manual() { return themes.find(draft.value(UI_THEME_NAME),draft.value(UI_THEME_SOURCE)); }
    private Optional<ThemeChoices.Source> bundled(boolean dark) { return themes.find(dark?"Jasper Dark":"Jasper Light",ThemeSource.BUNDLED); }
    private Optional<ThemeChoices.Source> tile(boolean dark) { return manual().filter(source->source.dark()==dark).or(()->bundled(dark)); }
    @Override List<String> labels() {
        if(themes.sources().get().isEmpty())return super.labels();
        return themes.sources().get().stream().map(this::sourceLabel).toList();
    }
    private String sourceLabel(ThemeChoices.Source source) {
        boolean duplicate=themes.sources().get().stream().filter(other->other.name().equalsIgnoreCase(source.name())).count()>1;
        return source.name()+(duplicate?(source.origin()==ThemeSource.BUNDLED?" (bundled)":" (installed)"):"");
    }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var plain=new ControlState(state.visible(),state.enabled(),state.busy(),"");
        var parts=new ArrayList<Nodes.Node>();
        var previews=new ArrayList<Option>();
        for(boolean dark:new boolean[]{true,false}) tile(dark).ifPresent(source->previews.add(new Option(dark?"dark":"light",dark?"Dark":"Light",Optional.empty(),"",sourceLabel(source),Optional.of(new Option.Preview(source.colors(),Optional.empty())))));
        if(bundled(true).isPresent()&&bundled(false).isPresent())previews.add(new Option("system","Match System",Optional.empty(),"",themes.systemDark().get().isPresent()?"System appearance: "+(themes.systemDark().get().get()?"Jasper Dark":"Jasper Light"):"System appearance unavailable; using the remembered manual source",Optional.of(new Option.Preview(bundled(true).get().colors(),Optional.of(bundled(false).get().colors())))));
        if(!previews.isEmpty())parts.add(new Nodes.Choice(id+":policy",plain,Nodes.ChoiceKind.PREVIEWS,previews,draft.value(UI_THEME_FOLLOW_SYSTEM)?"system":manual().map(source->source.dark()?"dark":"light").orElse(""),"Appearance policy",""));
        Nodes.Node picker;
        var sources=themes.sources().get();
        if(sources.isEmpty()) picker=super.node(id,state);
        else {
            var options=new ArrayList<Option>();int selected=-1;
            var resolved=manual();
            for(int i=0;i<sources.size();i++) {var source=sources.get(i);options.add(new Option("theme-"+i,sourceLabel(source),Optional.empty()));if(resolved.isPresent()&&resolved.get().equals(source))selected=i;}
            if(selected<0) {selected=options.size();String missing=draft.value(UI_THEME_NAME)+(draft.value(UI_THEME_SOURCE)==ThemeSource.BUNDLED?" (bundled source not found)":" (not found)");options.add(new Option("theme-"+selected,missing,Optional.empty()));}
            var choice=new Nodes.Choice(id,state,Nodes.ChoiceKind.SEARCHABLE,options,"theme-"+selected,label,"","",Optional.of(new Nodes.ChoiceSearch(0,"No matching themes",Optional.empty())));
            var gear=new Nodes.MenuButton(id+":actions",plain,"","brightness",Nodes.ButtonKind.GHOST,"",List.of(new MenuItem(id+":import",Editors.IMPORT_THEME,"",id+":import",true,false)),"Theme actions");
            picker=new Nodes.Stack(id+":line",plain,Nodes.LayoutRole.SETTINGS_CONTROLS,List.of(choice,gear));
        }
        boolean modified=!draft.value(UI_THEME_NAME).equals(UI_THEME_NAME.defaultValue())||draft.value(UI_THEME_SOURCE)!=UI_THEME_SOURCE.defaultValue()||draft.value(UI_THEME_FOLLOW_SYSTEM);
        var line=(Nodes.Stack)picker;
        var gear=new Nodes.MenuButton(id+":actions",plain,"","brightness",Nodes.ButtonKind.GHOST,"",List.of(
            new MenuItem(id+":import",Editors.IMPORT_THEME,"",id+":import",true,false),
            new MenuItem(id+":reset-menu","Reset to defaults","reset",id+":reset",modified,false)),"Theme actions");
        var controls=new Nodes.Stack(id+":controls",plain,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(line.children().getFirst(),gear));
        parts.add(new Nodes.SettingRow("row:"+id,plain,label,draft.value(UI_THEME_FOLLOW_SYSTEM)?themes.systemDark().get().map(dark->"System uses "+(dark?"Jasper Dark":"Jasper Light")+"; manual theme remembered").orElse("System unavailable; using remembered manual theme"): "",controls,false,highlighted));
        return new Nodes.Stack(id+":selection",plain,Nodes.LayoutRole.VERTICAL,parts);
    }
    @Override public boolean event(String id,UiEvent event) {
        if(event instanceof UiEvent.Selected selected) {
            String choice=selected.ids().stream().findFirst().orElse("");
            if(selected.id().equals(id+":policy")) {
                if(choice.equals("system"))draft.stage(UI_THEME_FOLLOW_SYSTEM,Optional.of(true));
                else if(choice.equals("dark")||choice.equals("light")) {
                    boolean dark=choice.equals("dark");
                    if(manual().filter(source->source.dark()==dark).isPresent())draft.stage(UI_THEME_FOLLOW_SYSTEM,Optional.of(false));
                    else bundled(dark).ifPresent(this::select);
                }
                return false;
            }
            if(selected.id().equals(id)&&!themes.sources().get().isEmpty()&&choice.startsWith("theme-")) {
                int index;try{index=Integer.parseInt(choice.substring(6));}catch(NumberFormatException bad){return false;}
                var sources=themes.sources().get();if(index>=0&&index<sources.size())select(sources.get(index));return false;
            }
        }
        boolean edited=super.event(id,event);
        if(edited) draft.stageAll(Map.of(UI_THEME_NAME,Optional.of(super.value()),UI_THEME_SOURCE,Optional.of(ThemeSource.CATALOG),UI_THEME_FOLLOW_SYSTEM,Optional.of(false)));
        return false;
    }
    private void select(ThemeChoices.Source source) {
        draft.stageAll(Map.of(UI_THEME_NAME,Optional.of(source.name()),UI_THEME_SOURCE,Optional.of(source.origin()),UI_THEME_FOLLOW_SYSTEM,Optional.of(false)));
    }
    @Override public String value() { return themes==null||themes.sources().get().isEmpty()?super.value():draft.value(UI_THEME_NAME); }
    @Override public void onChanged(Runnable render) { this.render=render; }
    @Override public void dispose() { listening.close();super.dispose(); }
}
