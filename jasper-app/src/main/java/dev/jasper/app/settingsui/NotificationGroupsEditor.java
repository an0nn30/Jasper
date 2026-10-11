package dev.jasper.app.settingsui;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code notifications.groups} editor: one kit form row per known group with its name, who declared it and its
 * id, how its notifications appear (the default marked) and a Reset. The value is the whole table. A group set back
 * to its default has no entry; a group the user has not touched keeps its entry exactly as written, even one naming
 * the default; and entries for groups not known now, such as a plugin that is not running, are kept as they are.
 * EDT only.
 */
final class NotificationGroupsEditor implements ValueEditor<Map<String, String>> {
    /** The modes the dropdown offers, in order, with the names it shows. */
    static final Map<String, String> MODES = modes();

    private final NotificationGroupChoices choices;
    private final java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit;
    private dev.jasper.ui.icons.ReceivingArtwork artwork;
    @Override public void dispose(){if(artwork!=null)artwork.close();}
    private List<NotificationGroupChoices.Group> groups;
    /** The table as last shown. */
    private Map<String, String> shown = Map.of();
    /** The user's choices since then, by group id; a null choice means "back to the default". */
    private final Map<String, String> chosen = new HashMap<>();

    NotificationGroupsEditor(NotificationGroupChoices choices) {this(choices,null);}
    NotificationGroupsEditor(NotificationGroupChoices choices, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this.kit=kit;
        this.choices = Objects.requireNonNull(choices, "choices");
        groups = List.copyOf(choices.groups().get());
    }

    /** The mode dropdown's node id for the group at {@code index}. */
    static String modeId(String id, int index) { return id + ":group-" + index; }

    /** The group's Reset button's node id and action. */
    static String resetId(String id, int index) { return id + ":group-" + index + ":reset"; }

    /** The mode a group's dropdown shows now. */
    String mode(String groupId) {
        var group = groups.stream().filter(candidate -> candidate.id().equals(groupId)).findFirst().orElseThrow();
        String token = value().get(groupId);
        return token != null && MODES.containsKey(token) ? token : group.defaultMode();
    }

    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        if (groups.isEmpty())
            return new Nodes.Stack(id, plain, Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.SettingRow(id + ":empty-row", plain, "No notification groups yet.", "", new Nodes.Stack(id + ":empty", plain, Nodes.LayoutRole.INLINE, List.of()), false, highlighted)));
        Map<String,String> value=value();
        var owners=new LinkedHashMap<String,List<Integer>>();
        for(int i=0;i<groups.size();i++)owners.computeIfAbsent(groups.get(i).ownerId(),ignored->new ArrayList<>()).add(i);
        var sections=new ArrayList<Nodes.Node>();
        sections.add(new Nodes.Text(id+":description",plain,"How each kind of notification appears. A plugin's groups are listed once it has run.",Nodes.TextRole.DESCRIPTION));
        int section=0;
        for(var indices:owners.values()) {
            var first=groups.get(indices.getFirst());var lines=new ArrayList<Nodes.Node>();
            var heading=new ArrayList<Nodes.Node>();
            String ownerIcon=first.ownerIcon();
            if(first.artwork().isPresent() && kit!=null) {
                if(artwork==null)artwork=new dev.jasper.ui.icons.ReceivingArtwork(kit.get().icons());
                try {ownerIcon=artwork.name(first.artwork().get());}catch(IllegalArgumentException rejected){ownerIcon="plugin";}
            }
            if(!ownerIcon.isBlank())heading.add(new Nodes.Icon(id+":owner-"+section+":icon",plain,ownerIcon,first.ownerId().equals("jasper")?Nodes.Hue.BLUE:Nodes.Hue.NEUTRAL,Nodes.IconSize.CONTROL,first.owner()));
            heading.add(new Nodes.Text(id+":owner-"+section+":title",plain,first.owner(),Nodes.TextRole.PANEL_TITLE));
            heading.add(new Nodes.Text(id+":owner-"+section+":count",plain,indices.size()+" groups",Nodes.TextRole.CAPTION));
            lines.add(new Nodes.Stack(id+":owner-"+section,plain,Nodes.LayoutRole.SETTINGS_GROUP_HEADER,heading));
            for(int i:indices) {
                var group=groups.get(i);var options=new ArrayList<Option>();
                for(var mode:MODES.entrySet())options.add(new Option(mode.getKey(),mode.getValue(),modeIcon(mode.getKey()),mode.getKey().equals(group.defaultMode())?"Default":""));
                var choice=new Nodes.Choice(modeId(id,i),plain,Nodes.ChoiceKind.DROPDOWN,options,mode(group.id()),group.name()+": how its notifications appear; "+group.owner()+" · "+group.id(),"");
                var reset=new Nodes.Button(resetId(id,i),new ControlState(plain.visible()&&value.containsKey(group.id()),plain.enabled()&&value.containsKey(group.id()),plain.busy(),""),"","refresh",Nodes.ButtonKind.GHOST,resetId(id,i),"Reset "+group.name()+" to its default");
                var control=new Nodes.Stack(modeId(id,i)+":controls",plain,Nodes.LayoutRole.SETTINGS_TRAILING,List.of(choice,reset));
                lines.add(new Nodes.SettingRow(id+":row-"+i,plain,group.name(),"",control,false,highlighted));
            }
            sections.add(new Nodes.Card(id+":owner-card-"+section++,plain,Nodes.CardKind.SETTINGS,lines));
        }
        return new Nodes.Stack(id,plain,Nodes.LayoutRole.VERTICAL,sections);
    }
    private static String modeIcon(String mode) {return switch(mode){case "card"->"notification.card";case "sticky"->"notification.sticky";case "history"->"notification.history";default->"notification.off";};}
    @Override public boolean fullSpan(){return true;}
    @Override public String revealTarget(String id) { return id + (groups.isEmpty() ? ":empty-row" : ":row-0"); }

    @Override public Map<String, String> value() {
        var value = new LinkedHashMap<>(shown);
        chosen.forEach((id, token) -> {
            if (token == null) value.remove(id);
            else value.put(id, token);
        });
        return value;
    }

    @Override public void show(Map<String, String> value) {
        shown = Map.copyOf(value);
        chosen.clear();
    }

    @Override public boolean event(String id, UiEvent event) {
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            if (event instanceof UiEvent.Selected picked && picked.id().equals(modeId(id, i))) {
                String token = picked.ids().stream().findFirst().orElse("");
                if (!MODES.containsKey(token)) return false;
                choose(group, token);
                return true;
            }
            if (event instanceof UiEvent.Action action && action.id().equals(resetId(id, i))) {
                choose(group, null);
                return true;
            }
        }
        return false;
    }

    /** The table spans the page; the section above it names it. */
    @Override public boolean labelled() { return true; }

    /** No overrides at all removes the table, rather than writing an empty one. */
    @Override public boolean removesAtDefault() { return true; }

    /** Lists the groups declared since the page last looked, keeping what the user chose. */
    @Override public void refresh() {
        var next = choices.groups().get();
        if (!next.equals(groups)) groups = List.copyOf(next);
    }

    /** The user chose {@code token} for {@code group}; null, or the group's default, means "no entry". */
    private void choose(NotificationGroupChoices.Group group, String token) {
        chosen.put(group.id(), token == null || token.equals(group.defaultMode()) ? null : token);
    }

    private static Map<String, String> modes() {
        var modes = new LinkedHashMap<String, String>();
        modes.put("card", "Card");
        modes.put("sticky", "Sticky card");
        modes.put("history", "History only");
        modes.put("off", "Off");
        return Collections.unmodifiableMap(modes);
    }
}
