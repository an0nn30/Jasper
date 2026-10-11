package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Behavioral contract shared by the production host and functional fake. */
public abstract class KitContractTest {
    protected abstract UiKit kit(String owner);
    protected abstract void emit(UiView view, UiEvent event);
    protected abstract void secret(UiView view, String id, char[] value);
    protected abstract void owns(UiKit kit, UiView view);
    protected abstract void stop(UiKit kit);
    protected abstract void fieldText(UiView view,String id,String text);
    protected abstract int[] fieldSelection(UiView view,String id);
    protected static void edt(Runnable body) throws Exception { SwingUtilities.invokeAndWait(body); }
    protected static ViewSpec spec(String title) { return new ViewSpec(new Nodes.Stack("root", ControlState.READY,
        Nodes.LayoutRole.VERTICAL, List.of(new Nodes.Text("title", ControlState.READY,title,Nodes.TextRole.BODY),
        new Nodes.Secret("secret",ControlState.READY,"","Password","")))); }
    @Test void settingsControlsPreserveAuthoredPreviewMetadataAndNumericEvents() throws Exception { edt(()->{
        var kit=kit("owner");var events=new ArrayList<UiEvent>();
        var palette=new Option.Palette(0xff111111,0xff222222,0xff333333,0xffeeeeee,0xff4488dd);
        var source=new Option("dark","Dark",Optional.empty(),"appearance","Authored source",Optional.of(new Option.Preview(palette,Optional.of(palette))));
        var nodes=new ArrayList<Nodes.Node>();
        nodes.add(new Nodes.Choice("policy",ControlState.READY,Nodes.ChoiceKind.PREVIEWS,List.of(source),"dark","Appearance policy",""));
        nodes.add(new Nodes.Choice("mode",ControlState.READY,Nodes.ChoiceKind.DROPDOWN,List.of(new Option("card","Card","notification.card","Default")),"card","Notification mode",""));
        for(var presentation:List.of(Nodes.FieldPresentation.STEPPER,Nodes.FieldPresentation.SLIDER))nodes.add(new Nodes.Field(presentation.name(),ControlState.READY,Nodes.FieldKind.NUMBER,"42","%","Value","",Optional.of(new Nodes.Numeric(0,100,1,0)),presentation));
        var spec=new ViewSpec(new Nodes.Stack("settings",ControlState.READY,Nodes.LayoutRole.SETTINGS_PAGE,nodes));var view=kit.create(spec,events::add);
        try {view.update(spec);assertThat(view.spec()).isEqualTo(spec);assertThat(events).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(()->view.selectAll("SLIDER"));assertThatIllegalArgumentException().isThrownBy(()->view.composing("SLIDER"));
            emit(view,new UiEvent.Selected("policy",Set.of("dark")));emit(view,new UiEvent.Edited("SLIDER","43"));
            assertThat(events).containsExactly(new UiEvent.Selected("policy",Set.of("dark")),new UiEvent.Edited("SLIDER","43"));
        }finally{view.close();stop(kit);}
    }); }

    @Test void windowPlainPathAndDiscreetInputRetainTheAdditiveContract() throws Exception { edt(()->{
        var path=new Nodes.Field("path",ControlState.READY,Nodes.FieldKind.PATH,"/synthetic/key","Hint","Path","browse",Optional.empty(),Nodes.FieldPresentation.PLAIN_PATH);
        assertThatThrownBy(()->new Nodes.Field("bad",ControlState.READY,Nodes.FieldKind.TEXT,"","","Bad","",Optional.empty(),Nodes.FieldPresentation.PLAIN_PATH)).isInstanceOf(IllegalArgumentException.class);
        var secret=new Nodes.Secret("secret",ControlState.READY,"","Password","group",Nodes.SecretKind.DISCREET_INPUT,"","submit");
        var content=new Nodes.Stack("content",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(path,secret));
        var action=new Nodes.Button("save",ControlState.READY,"Save","",Nodes.ButtonKind.PRIMARY,"save","");
        assertThatThrownBy(()->new Nodes.Form("bad",ControlState.READY,Nodes.FormKind.WINDOW,"Window",Optional.empty(),List.of(),Optional.empty(),List.of(action))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Nodes.Form("bad",ControlState.READY,Nodes.FormKind.WINDOW,"Window",Optional.of(content),List.of(content),Optional.empty(),List.of(action))).isInstanceOf(IllegalArgumentException.class);
        var kit=kit("owner");var view=kit.create(new ViewSpec(new Nodes.Form("window",ControlState.READY,Nodes.FormKind.WINDOW,"Window",Optional.empty(),List.of(content),Optional.empty(),List.of(action))),e->{});
        try {
            secret(view,"secret","draft".toCharArray());
            for(var policy:Nodes.FormSubmission.values()) {
                var form=new Nodes.Form("window",ControlState.READY,Nodes.FormKind.WINDOW,"Window",Optional.empty(),List.of(content),Optional.empty(),List.of(action),"cancel",policy);
                view.update(new ViewSpec(form));assertThat(view.spec().root()).isEqualTo(form);
                view.withSecret("secret",value->assertThat(value).isEqualTo("draft".toCharArray()));
            }
        }finally{view.close();stop(kit);}
    }); }

    @Test void formSubmissionPolicySurvivesHostProjectionAndUpdates() throws Exception { edt(() -> {
        var kit=kit("owner");
        var action=new Nodes.Button("save",ControlState.READY,"Save","",Nodes.ButtonKind.PRIMARY,"save","");
        var legacy=new Nodes.Form("form",ControlState.READY,Nodes.FormKind.FULL,"Form",Optional.empty(),List.of(),Optional.empty(),List.of(action),"cancel");
        assertThat(legacy.submission()).isEqualTo(Nodes.FormSubmission.PRIMARY);
        var view=kit.create(new ViewSpec(legacy),e -> {});
        try { for(var policy:Nodes.FormSubmission.values()) {
            var form=new Nodes.Form("form",ControlState.READY,Nodes.FormKind.FULL,"Form",Optional.empty(),List.of(),Optional.empty(),List.of(action),"cancel",policy);
            view.update(new ViewSpec(form));assertThat(((Nodes.Form)view.spec().root()).submission()).isEqualTo(policy);
        }}finally{view.close();stop(kit);}
    }); }
    @Test void filledFolderBuiltinIsAdmittedByHostAndFakeWithoutARegistration() throws Exception { edt(() -> {
        var kit=kit("owner");
        var spec=new ViewSpec(new Nodes.Icon("folder",ControlState.READY,"folder.filled",Nodes.Hue.NONE,Nodes.IconSize.CONTROL,"Folder"));
        var view=kit.create(spec,e -> {});
        try {view.update(spec);assertThat(view.spec()).isEqualTo(spec);}finally{view.close();stop(kit);}
    }); }
    @Test void supportingTextRoleSurvivesHostProjectionAndRefresh() throws Exception { edt(() -> {
        var kit=kit("owner");
        var text=new Nodes.Text("help",ControlState.READY,"Clients use this account",Nodes.TextRole.SUPPORTING);
        var spec=new ViewSpec(new Nodes.FormRow("row",ControlState.READY,"Account","",new Nodes.Stack("stack",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(text)),Optional.empty()));
        var view=kit.create(spec,e -> {});
        try { view.update(spec);assertThat(view.spec()).isEqualTo(spec); }finally{view.close();stop(kit);}
    }); }
    @Test void numericShapeUpdatePublishesTheNewLiveValue() throws Exception { edt(() -> {
        var kit = kit("owner"); var events = new ArrayList<UiEvent>();
        var view = kit.create(number("1", 0, 0), events::add);
        try {
            for (var next : List.of(number("-2", -100, 0), number("2.5", -100, 1))) {
                view.update(next); view.selectAll("number");
                assertThat(fieldSelection(view, "number")).containsExactly(0, ((Nodes.Field)next.root()).value().length());
                assertThat(view.spec()).isEqualTo(next); assertThat(events).isEmpty();
            }
        } finally { view.close(); stop(kit); }
    }); }
    private static ViewSpec number(String value, double min, int decimals) {
        return new ViewSpec(new Nodes.Field("number", ControlState.READY, Nodes.FieldKind.NUMBER, value, "", "Number", "",
            Optional.of(new Nodes.Numeric(min, 100, 1, decimals))));
    }
    @Test void everyDescriptorIconPositionValidatesOwnershipBeforeCreateOrUpdateMutation() throws Exception { edt(() -> {
        var kit=kit("owner");
        var foreign=kit("foreign");
        foreign.registerIcon(new IconAsset("foreign:custom","M 2 2 L 14 14"));
        try {
            for (var entry : descriptorIconCases().entrySet()) {
                var original=spec("Original");
                var view=kit.create(original,e -> {});
                view.replaceSecret("secret",new char[]{'s','e','c','r','e','t'});
                for (String name : List.of("missing", "foreign:custom")) {
                    var invalid=iconSpec(entry.getValue().apply(name));
                    assertThatIllegalArgumentException().as(entry.getKey()+" create "+name).isThrownBy(() -> kit.create(invalid,e -> {}));
                    assertThatIllegalArgumentException().as(entry.getKey()+" update "+name).isThrownBy(() -> view.update(invalid));
                    assertThat(view.spec()).as(entry.getKey()+" unchanged descriptor").isEqualTo(original);
                    view.withSecret("secret",value -> assertThat(value).containsExactly('s','e','c','r','e','t'));
                }
                var registration=kit.registerIcon(new IconAsset("owner:custom","M 2 2 L 14 14"));
                for(String name : List.of("file", "owner:custom")) {
                    var valid=iconSpec(entry.getValue().apply(name));
                    var created=kit.create(valid,e -> {});assertThat(created.spec()).isEqualTo(valid);created.close();
                    view.update(valid);assertThat(view.spec()).isEqualTo(valid);
                }
                // Remove the live use before unregistering; subsequent contributions must reject the old name.
                view.update(original);registration.close();
                var removed=iconSpec(entry.getValue().apply("owner:custom"));
                assertThatIllegalArgumentException().as(entry.getKey()+" unregistered create").isThrownBy(() -> kit.create(removed,e -> {}));
                assertThatIllegalArgumentException().as(entry.getKey()+" unregistered update").isThrownBy(() -> view.update(removed));
                assertThat(view.spec()).isEqualTo(original);view.close();
                assertThatIllegalStateException().as(entry.getKey()+" closed guard before icon validation").isThrownBy(() -> view.update(removed));
            }
            // Empty button/choice cues, blank menu cues and absent Optional row icons remain valid.
            var empty=new ViewSpec(new Nodes.Stack("empty",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
                descriptorButton(""),
                new Nodes.MenuButton("menu-empty",ControlState.READY,"Menu","",Nodes.ButtonKind.GHOST,"",List.of(descriptorMenu("  ")),""),
                new Nodes.Choice("choice-empty",ControlState.READY,Nodes.ChoiceKind.PICKER,List.of(),"","Pick","pick",""),
                descriptorTable(Optional.empty(),Optional.empty(),List.of()),
                new Nodes.Tree("tree-empty",ControlState.READY,List.of(new TreeItem("leaf","Leaf",Optional.empty(),true,List.of())),Set.of(),Set.of(),""))));
            var view=kit.create(empty,e -> {});view.update(empty);view.close();
        } finally {stop(kit);stop(foreign);}
    }); }
    private static ViewSpec iconSpec(Nodes.Node node) {
        return new ViewSpec(new Nodes.Stack("outer",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Section("section",ControlState.READY,"Section",new Nodes.Card("card",ControlState.READY,Nodes.CardKind.MIXED,List.of(node))))));
    }
    private static Nodes.Icon descriptorIcon(String name) {return new Nodes.Icon("glyph",ControlState.READY,name,Nodes.Hue.NONE,Nodes.IconSize.RAIL,"Glyph");}
    private static Nodes.Button descriptorButton(String name) {return new Nodes.Button("button",ControlState.READY,"Button",name,Nodes.ButtonKind.GHOST,"run","");}
    private static MenuItem descriptorMenu(String name) {return new MenuItem("menu-item","Item",name,"run",true,false);}
    private static Nodes.Table descriptorTable(Optional<String> icon,Optional<List<MenuItem>> rowMenu,List<MenuItem> menu) {
        return new Nodes.Table("table",ControlState.READY,List.of(new Column("col","Column",Nodes.TextRole.BODY,false)),
            List.of(new TableRow("row",Map.of("col","Value"),icon,false,rowMenu)),Set.of(),"",menu);
    }
    private static Map<String,java.util.function.Function<String,Nodes.Node>> descriptorIconCases() {
        var cases=new LinkedHashMap<String,java.util.function.Function<String,Nodes.Node>>();
        cases.put("picker-face",name -> new Nodes.Choice("rich",ControlState.READY,Nodes.ChoiceKind.PICKER,List.of(),"","Credential","pick","",Optional.empty(),Optional.of(new Nodes.PickerFace("Name","Caption",name,Nodes.Hue.GREEN,Nodes.PickerDensity.COMPACT))));
        cases.put("picker-menu",name -> new Nodes.PickerMenu("menu",ControlState.READY,"","Search",List.of(new Nodes.PickerItem("row","Row","Caption",name,Nodes.Hue.BLUE)),"row","row","Empty","Create","create","cancel"));
        cases.put("icon",KitContractTest::descriptorIcon);
        cases.put("button",KitContractTest::descriptorButton);
        cases.put("menu-button",name -> new Nodes.MenuButton("menu",ControlState.READY,"Menu",name,Nodes.ButtonKind.GHOST,"",List.of(descriptorMenu("")),""));
        cases.put("menu-item",name -> new Nodes.MenuButton("menu",ControlState.READY,"Menu","",Nodes.ButtonKind.GHOST,"",List.of(descriptorMenu(name)),""));
        cases.put("choice-option",name -> new Nodes.Choice("option",ControlState.READY,Nodes.ChoiceKind.DROPDOWN,List.of(new Option("one","One",name,"Default")),"one","Options",""));
        cases.put("choice",name -> new Nodes.Choice("choice",ControlState.READY,Nodes.ChoiceKind.PICKER,List.of(),"","Pick","pick",name));
        cases.put("header-overflow",name -> new Nodes.Header("header",ControlState.READY,"Header",OptionalInt.empty(),Optional.empty(),Optional.empty(),List.of(descriptorMenu(name))));
        cases.put("table-row",name -> descriptorTable(Optional.of(name),Optional.empty(),List.of()));
        cases.put("table-row-menu",name -> descriptorTable(Optional.empty(),Optional.of(List.of(descriptorMenu(name))),List.of()));
        cases.put("table-menu",name -> descriptorTable(Optional.empty(),Optional.empty(),List.of(descriptorMenu(name))));
        cases.put("tree-hidden-child",name -> new Nodes.Tree("tree",ControlState.READY,List.of(new TreeItem("branch","Branch",Optional.empty(),false,List.of(new TreeItem("leaf","Leaf",Optional.of(name),true,List.of())))),Set.of(),Set.of(),""));
        cases.put("tree-row-menu",name -> new Nodes.Tree("tree",ControlState.READY,List.of(new TreeItem("leaf","Leaf",Optional.empty(),true,List.of(),OptionalInt.empty(),false,"",Optional.empty(),List.of(),Optional.of(List.of(descriptorMenu(name))))),Set.of(),Set.of(),""));
        cases.put("breadcrumb-menu",name -> new Nodes.Breadcrumbs("crumbs",ControlState.READY,List.of(new Crumb("crumb","Crumb",List.of(descriptorMenu(name)))),"Path",false,"","edit","submit","cancel"));
        cases.put("list-menu",name -> new Nodes.ItemList("list",ControlState.READY,Nodes.ListKind.SINGLE,List.of(),Set.of(),"",List.of(descriptorMenu(name))));
        cases.put("list-row-menu",name -> new Nodes.ItemList("list",ControlState.READY,Nodes.ListKind.SINGLE,List.of(new ListItem("row",Optional.empty(),List.of(),List.of(),List.of(),false,Optional.of(List.of(descriptorMenu(name))))),Set.of(),"",List.of()));
        cases.put("list-add",name -> new Nodes.ItemList("list",ControlState.READY,Nodes.ListKind.SINGLE,List.of(),Set.of(),"",List.of(),Nodes.SelectionMode.SINGLE,Optional.of(descriptorButton(name))));
        for(String slot : List.of("leading","title","subtitle","trailing")) cases.put("list-"+slot,name -> {
            var row=new ListItem("row",slot.equals("leading")?Optional.of(descriptorIcon(name)):Optional.empty(),
                slot.equals("title")?List.of(descriptorIcon(name)):List.of(),slot.equals("subtitle")?List.of(descriptorIcon(name)):List.of(),slot.equals("trailing")?List.of(descriptorIcon(name)):List.of());
            return new Nodes.ItemList("list",ControlState.READY,Nodes.ListKind.SINGLE,List.of(row),Set.of(),"",List.of());
        });
        return cases;
    }

    @Test void passiveRowsRoundTripRevealAndRejectAnyDeclaredSelection() throws Exception { edt(() -> {
        var item = new ListItem("row",Optional.empty(),List.of(new Nodes.Text("line",ControlState.READY,"Error output",Nodes.TextRole.MONO,
            Optional.of(Nodes.Severity.DANGER),OptionalInt.empty())),List.of(),List.of());
        var list = new Nodes.ItemList("output",ControlState.READY,Nodes.ListKind.COMPACT,List.of(item),Set.of(),"",List.of(),Nodes.SelectionMode.NONE,Optional.empty(),OptionalInt.of(12));
        var table = new Nodes.Table("table",ControlState.READY,List.of(new Column("name","Name",Nodes.TextRole.MONO,false)),
            List.of(new TableRow("row",Map.of("name","Output"),Optional.empty())),Set.of(),"",List.of(),Nodes.SelectionMode.NONE);
        var kit=kit("owner"); var spec=new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(list,table)));
        var events=new ArrayList<UiEvent>(); var view=kit.create(spec,events::add);
        assertThat(view.spec()).isEqualTo(spec); view.reveal("line"); view.reveal("table"); assertThat(events).isEmpty();
        assertThatThrownBy(() -> new Nodes.ItemList("bad",ControlState.READY,Nodes.ListKind.COMPACT,List.of(item),Set.of("row"),"",List.of(),Nodes.SelectionMode.NONE,Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Nodes.Table("bad",ControlState.READY,table.columns(),table.rows(),Set.of("row"),"",List.of(),Nodes.SelectionMode.NONE))
            .isInstanceOf(IllegalArgumentException.class);
        stop(kit);
    }); }

    @Test void listCanvasShortcutDescriptorsRoundTripAndRevealRows() throws Exception { edt(() -> {
        var row = new ListItem("server:one", Optional.empty(), List.of(new Nodes.Text("server.name", ControlState.READY,
            "Server", Nodes.TextRole.BODY)), List.of(), List.of());
        var list = new Nodes.ItemList("servers", ControlState.READY, Nodes.ListKind.DOUBLE, List.of(row), Set.of("server:one"),
            "details", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "toggle", "delete",
            "details-key", "copy-key", "cancel-key");
        var kit = kit("owner"); var view = kit.create(new ViewSpec(list), event -> { });
        assertThat(view.spec().root()).isEqualTo(list);
        view.reveal("server.name");
        assertThatThrownBy(() -> new Nodes.ItemList("results", ControlState.READY, Nodes.ListKind.RESULTS, List.of(row),
            Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "",
            "details-key", "", "")).isInstanceOf(IllegalArgumentException.class);
        stop(kit);
    }); }

    /** Settings descriptors: tree counts/emphasis, highlighted rows, number bounds and both searchable choice modes. */
    protected static ViewSpec settings(boolean highlighted, String entry) {
        var toggle=new Nodes.Toggle("toggle",ControlState.READY,"Ligatures",true,"toggle",false);
        var options=List.of(new Option("same","Same as UI theme",Optional.empty()),new Option("nord","Nord",Optional.empty()));
        return new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Tree("pages",ControlState.READY,List.of(new TreeItem("font","Font",Optional.empty(),true,List.of(),OptionalInt.of(2),true)),Set.of(),Set.of("font"),"open"),
            new Nodes.SettingRow("row",ControlState.READY,"Ligatures","",toggle,false,highlighted),
            new Nodes.FormRow("form",ControlState.READY,"Size","",new Nodes.Field("size",ControlState.READY,Nodes.FieldKind.NUMBER,"13","","Size","",Optional.of(new Nodes.Numeric(6,72,0.5,1))),Optional.empty(),highlighted),
            new Nodes.Choice("theme",ControlState.READY,Nodes.ChoiceKind.SEARCHABLE,options,"nord","Theme","","",Optional.of(new Nodes.ChoiceSearch(1,"No matching themes",Optional.empty()))),
            new Nodes.Choice("font",ControlState.READY,Nodes.ChoiceKind.SEARCHABLE,options.subList(1,2),"","Font","","",Optional.of(new Nodes.ChoiceSearch(0,"No matches",Optional.of(entry)))))));
    }
    @Test void settingsDescriptorsMountUpdateRevealAndReportTypedEdits() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(settings(true,"Menlo"),events::add);
        assertThat(view.spec()).isEqualTo(settings(true,"Menlo"));
        view.update(settings(false,"Iosevka"));assertThat(view.spec()).isEqualTo(settings(false,"Iosevka"));
        view.reveal("row");assertThatThrownBy(() -> view.reveal("missing")).isInstanceOf(IllegalArgumentException.class);
        emit(view,new UiEvent.Selected("theme",Set.of("same")));emit(view,new UiEvent.Edited("font","SF Mono"));emit(view,new UiEvent.Edited("size","14.5"));
        assertThat(events).containsExactly(new UiEvent.Selected("theme",Set.of("same")),new UiEvent.Edited("font","SF Mono"),new UiEvent.Edited("size","14.5"));
        stop(kit);assertThatThrownBy(() -> view.reveal("row")).isInstanceOf(IllegalStateException.class);
    }); }
    static ViewSpec browser(boolean editing, Optional<Boolean> sorted) {
        var pin=List.of(new MenuItem("pin","Pin","bookmark","pin",true,false));
        return new ViewSpec(new Nodes.Stack("browser",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Breadcrumbs("path",ControlState.READY,List.of(new Crumb("home","Home",List.of()),new Crumb("docs","docs",pin)),"Location",editing,editing?"/docs":"","edit","go","done"),
            new Nodes.Tree("places",ControlState.READY,List.of(new TreeItem("box","Box",Optional.of("server"),true,List.of(),java.util.OptionalInt.empty(),false,"me@box",Optional.of(Nodes.StatusKind.SUCCESS),List.of("SSH"),Optional.of(pin))),Set.of(),Set.of(),""),
            new Nodes.Table("files",ControlState.READY,List.of(new Column("name","Name",Nodes.TextRole.BODY,false,4.5,sorted),new Column("size","Size",Nodes.TextRole.DESCRIPTION,true,1,Optional.empty())),
                List.of(new TableRow("docs",Map.of("name","docs"),Optional.of("folder"),false,Optional.of(pin)),new TableRow("notes",Map.of("name","notes.txt"),Optional.of("file"),true,Optional.of(List.of()))),
                Set.of("docs"),"open",List.of(),Nodes.SelectionMode.MULTIPLE,Nodes.ScrollPolicy.OVERLAY))));
    }
    @Test void browserDescriptorsMountUpdateAndReportSortsCrumbsAndMenus() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(browser(false,Optional.of(true)),events::add);
        assertThat(view.spec()).isEqualTo(browser(false,Optional.of(true)));
        view.update(browser(true,Optional.of(false)));assertThat(view.spec()).isEqualTo(browser(true,Optional.of(false)));
        view.focus("path");
        emit(view,new UiEvent.Sorted("files","size"));emit(view,new UiEvent.Selected("path",Set.of("docs")));emit(view,new UiEvent.Action("pin"));
        emit(view,new UiEvent.Edited("path","/tmp"));emit(view,new UiEvent.Action("go"));
        emit(view,new UiEvent.Selected("places",Set.of("box")));emit(view,new UiEvent.Clicked("places","box"));
        assertThat(events).containsExactly(new UiEvent.Sorted("files","size"),new UiEvent.Selected("path",Set.of("docs")),new UiEvent.Action("pin"),
            new UiEvent.Edited("path","/tmp"),new UiEvent.Action("go"),new UiEvent.Selected("places",Set.of("box")),new UiEvent.Clicked("places","box"));
        stop(kit);
    }); }
    /** A palette: a search field driving a RESULTS list with headings and a visible row count (Plan 2 Task 6b-2). */
    static ViewSpec results(String selected, int visibleRows) {
        var heading=new ListItem("commands",Optional.empty(),List.of(new Nodes.Text("commands.title",ControlState.READY,"Commands",Nodes.TextRole.CAPTION)),List.of(),List.of(),true);
        var items=new ArrayList<ListItem>(List.of(heading));
        for(String id:List.of("new","close"))items.add(new ListItem(id,Optional.of(new Nodes.Icon(id+".icon",ControlState.READY,"play",Nodes.Hue.NONE,Nodes.IconSize.RAIL,"Command")),
            List.of(new Nodes.Text(id+".title",ControlState.READY,id+" tab",Nodes.TextRole.BODY)),List.of(new Nodes.Text(id+".detail",ControlState.READY,"Tabs",Nodes.TextRole.DESCRIPTION)),
            List.of(new Nodes.Text(id+".tag",ControlState.READY,"Cmd+T",Nodes.TextRole.CAPTION))));
        return new ViewSpec(new Nodes.Stack("palette",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Field("query",ControlState.READY,Nodes.FieldKind.SEARCH,"tab","Search everything","Search all","",Optional.empty(),Nodes.FieldPresentation.TRANSPARENT),
            new Nodes.ItemList("results",ControlState.READY,Nodes.ListKind.RESULTS,items,Set.of(selected),"run",List.of(),Nodes.SelectionMode.SINGLE,Optional.empty(),OptionalInt.of(visibleRows)))));
    }
    @Test void resultListsMountUpdateAndReportPressesAndComposition() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(results("new",5),events::add);
        assertThat(view.spec()).isEqualTo(results("new",5));
        view.update(results("close",8));assertThat(view.spec()).isEqualTo(results("close",8));
        assertThat(view.composing("query")).as("no input method is composing").isFalse();
        assertThatThrownBy(() -> view.composing("results")).isInstanceOf(IllegalArgumentException.class);
        emit(view,new UiEvent.Edited("query","tabs"));emit(view,new UiEvent.Selected("results",Set.of("new")));emit(view,new UiEvent.Action("run"));
        assertThat(events).containsExactly(new UiEvent.Edited("query","tabs"),new UiEvent.Selected("results",Set.of("new")),new UiEvent.Action("run"));
        assertThatThrownBy(() -> new Nodes.ItemList("bad",ControlState.READY,Nodes.ListKind.RESULTS,((Nodes.ItemList)((Nodes.Stack)results("new",1).root()).children().get(1)).items(),
            Set.of("commands"),"run",List.of(),Nodes.SelectionMode.SINGLE,Optional.empty(),OptionalInt.of(1))).as("a heading is never selected").isInstanceOf(IllegalArgumentException.class);
        stop(kit);assertThatThrownBy(() -> view.composing("query")).isInstanceOf(IllegalStateException.class);
    }); }
    /** Plugin-manager style rows: a row-owned menu, a compact switch with a tooltip, and severity-tinted glyphs. */
    static ViewSpec manager(boolean on, String tooltip) {
        var menu=List.of(new MenuItem("settings","Open Settings","","a:settings",true,false),new MenuItem("sep","","","",false,true),new MenuItem("remove","Remove…","","a:remove",true,false));
        return new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Icon("severity",ControlState.READY,"error",Nodes.Hue.NONE,Nodes.IconSize.RAIL,"Error",Optional.of(Nodes.Severity.DANGER)),
            new Nodes.Text("body",ControlState.READY,"A plugin is unrestricted code.\nAllow only plugins you trust.",Nodes.TextRole.BODY),
            new Nodes.Button("open",ControlState.READY,"Open","",Nodes.ButtonKind.LINK,"open","",Nodes.ButtonPresentation.EMBEDDED),
            new Nodes.ItemList("plugins",ControlState.READY,Nodes.ListKind.DOUBLE,List.of(
                new ListItem("group",Optional.empty(),List.of(new Nodes.Text("group.title",ControlState.READY,"Bundled",Nodes.TextRole.CAPTION)),List.of(),List.of(),true),
                new ListItem("a",Optional.of(new Nodes.Icon("a.icon",ControlState.READY,"plugin",Nodes.Hue.NEUTRAL,Nodes.IconSize.RAIL,"Plugin")),
                    List.of(new Nodes.Text("a.name",ControlState.READY,"A",Nodes.TextRole.BODY_STRONG)),List.of(),
                    List.of(new Nodes.Toggle("a.on",ControlState.READY,"Enable A",on,"a:toggle",true,tooltip)),false,Optional.of(menu))),
                Set.of("a"),"",List.of(),Nodes.SelectionMode.SINGLE,Optional.empty()))));
    }
    @Test void managerRowsMountUpdateAndReportSwitchesAndRowMenuActions() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(manager(true,""),events::add);
        assertThat(view.spec()).isEqualTo(manager(true,""));
        view.update(manager(false,"Restart to apply"));assertThat(view.spec()).isEqualTo(manager(false,"Restart to apply"));
        emit(view,new UiEvent.Toggled("a.on",true));emit(view,new UiEvent.Action("a:remove"));
        assertThat(events).containsExactly(new UiEvent.Toggled("a.on",true),new UiEvent.Action("a:remove"));
        stop(kit);
    }); }
    /** Remote rows (Plan 2 Task 8-kit): tooltips, a star button (warning-tinted when a favourite), a pending switch, list Enter and Delete actions and list clicks. */
    static ViewSpec tunnels(String tooltip, boolean pending) {
        var row=new ListItem("web",Optional.of(new Nodes.StatusDot("web.dot",ControlState.READY,Nodes.StatusKind.WARNING)),
            List.of(new Nodes.Text("web.name",ControlState.READY,"web",Nodes.TextRole.BODY)),List.of(new Nodes.Text("web.map",ControlState.READY,"8080 → :80",Nodes.TextRole.MONO_SMALL)),
            List.of(pending ? new Nodes.Button("web.star",ControlState.READY,"","star",Nodes.ButtonKind.GHOST,"favorite:web","Add to favorites",Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED)
                    : new Nodes.Button("web.star",ControlState.READY,"","star.filled",Nodes.ButtonKind.GHOST,"favorite:web","Remove from favorites",Nodes.ButtonPresentation.EMBEDDED,Optional.of(Nodes.Severity.WARNING)),
                new Nodes.Toggle("web.run",ControlState.READY,"Stop tunnel",true,"toggle:web",true,"",pending)),false,Optional.empty(),tooltip);
        return new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.ItemList("tunnels",ControlState.READY,Nodes.ListKind.DOUBLE,List.of(row),Set.of("web"),"edit",List.of(),Nodes.SelectionMode.SINGLE,Optional.empty(),OptionalInt.empty(),"toggle","delete"),
            new Nodes.Table("files",ControlState.READY,List.of(new Column("name","Name",Nodes.TextRole.BODY,false)),
                List.of(new TableRow("notes",Map.of("name","notes.txt"),Optional.of("file"),false,Optional.empty(),tooltip)),Set.of(),"open",List.of()))));
    }
    @Test void remoteRowsMountUpdateAndReportClicksKeysStarsAndPendingSwitches() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(tunnels("up 3 min",true),events::add);
        assertThat(view.spec()).isEqualTo(tunnels("up 3 min",true));
        view.update(tunnels("up 4 min",false));assertThat(view.spec()).isEqualTo(tunnels("up 4 min",false));
        emit(view,new UiEvent.Selected("tunnels",Set.of("web")));emit(view,new UiEvent.Clicked("tunnels","web"));
        emit(view,new UiEvent.Action("toggle"));emit(view,new UiEvent.Action("delete"));emit(view,new UiEvent.Action("favorite:web"));emit(view,new UiEvent.Toggled("web.run",false));
        assertThat(events).containsExactly(new UiEvent.Selected("tunnels",Set.of("web")),new UiEvent.Clicked("tunnels","web"),new UiEvent.Action("toggle"),
            new UiEvent.Action("delete"),new UiEvent.Action("favorite:web"),new UiEvent.Toggled("web.run",false));
        stop(kit);
    }); }
    @Test void disposalCannotReenterUpdate() throws Exception { edt(() -> {
        var kit=kit("owner"); var before=new ViewSpec(new Nodes.Custom("custom",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor"));
        var view=kit.create(before,e -> {}); var calls=new java.util.concurrent.atomic.AtomicInteger(); var rejected=new java.util.concurrent.atomic.AtomicBoolean();
        view.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {
            calls.incrementAndGet(); try {view.update(spec("Nested"));} catch(IllegalStateException expected){rejected.set(true);}
        }));
        view.update(spec("Outer")); assertThat(rejected.get()).isTrue(); assertThat(view.spec()).isEqualTo(spec("Outer"));
        view.close(); stop(kit); assertThat(calls.get()).isEqualTo(1);
    }); }
    @Test void storedSecretDetailsOwnDefensiveTransfersAndWipeTemporaryCopies() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();
        var descriptor=new Nodes.Secret("detail",ControlState.READY,"","Passphrase","",Nodes.SecretKind.DETAIL,"copy-detail");
        var view=kit.create(new ViewSpec(descriptor),events::add);char[] source={'x','y'};view.replaceSecret("detail",source);source[0]='z';
        assertThat(events).isEmpty();char[][] held={null};
        assertThatThrownBy(() -> view.withSecret("detail",chars -> {held[0]=chars;assertThat(chars).containsExactly('x','y');throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);
        assertThat(held[0]).containsOnly((char)0);view.replaceSecret("detail",new char[]{'n'});view.withSecret("detail",chars -> assertThat(chars).containsExactly('n'));
        view.clearSecret("detail");view.withSecret("detail",chars -> assertThat(chars).isEmpty());
        view.replaceSecret("detail",new char[]{'q'});view.update(spec("Input"));view.update(new ViewSpec(descriptor));view.withSecret("detail",chars -> assertThat(chars).isEmpty());
        stop(kit);assertThatThrownBy(() -> view.replaceSecret("detail",new char[]{'s'})).isInstanceOf(IllegalStateException.class);
    }); }
    /** A secure-note secret: clear multiline input over wipeable storage, no reveal group or copy action. */
    protected static ViewSpec note(Nodes.SecretKind kind) { return new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
        new Nodes.Text("title",ControlState.READY,"Note",Nodes.TextRole.BODY),
        new Nodes.Secret("note",ControlState.READY,"","Secure note","",kind,"")))); }
    @Test void multilineSecretsTakeTypedTextAndWipeOnKindChangeAndClose() throws Exception { edt(() -> {
        assertThatThrownBy(() -> new Nodes.Secret("n",ControlState.READY,"","Note","pair",Nodes.SecretKind.MULTILINE,"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Nodes.Secret("n",ControlState.READY,"","Note","",Nodes.SecretKind.MULTILINE,"copy")).isInstanceOf(IllegalArgumentException.class);
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var view=kit.create(note(Nodes.SecretKind.MULTILINE),events::add);
        secret(view,"note","first line\nsecond".toCharArray());
        assertThat(events).containsOnly(new UiEvent.SecretChanged("note"));
        assertThat(events.toString()+view.spec()).doesNotContain("first line");
        view.update(note(Nodes.SecretKind.MULTILINE));
        char[][] held={null};view.withSecret("note",chars -> {held[0]=chars;assertThat(chars).containsExactly("first line\nsecond".toCharArray());});
        assertThat(held[0]).containsOnly((char)0);
        events.clear();char[] owned={'a','\n','b'};view.replaceSecret("note",owned);assertThat(events).isEmpty();assertThat(owned).containsExactly('a','\n','b');
        view.withSecret("note",chars -> assertThat(chars).containsExactly('a','\n','b'));
        view.update(note(Nodes.SecretKind.INPUT));view.withSecret("note",chars -> assertThat(chars).isEmpty());
        view.update(note(Nodes.SecretKind.MULTILINE));view.withSecret("note",chars -> assertThat(chars).isEmpty());
        secret(view,"note","x".toCharArray());view.clearSecret("note");view.withSecret("note",chars -> assertThat(chars).isEmpty());
        view.close();assertThatThrownBy(() -> view.withSecret("note",chars -> {})).isInstanceOf(IllegalStateException.class);stop(kit);
    }); }
    @Test void inputSecretsCarryASubmitActionThroughUpdatesAndOnlyInputsTakeOne() throws Exception { edt(() -> {
        assertThatThrownBy(() -> new Nodes.Secret("n",ControlState.READY,"","Note","",Nodes.SecretKind.MULTILINE,"","go")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Nodes.Secret("n",ControlState.READY,"","Stored","",Nodes.SecretKind.DETAIL,"copy","go")).isInstanceOf(IllegalArgumentException.class);
        var kit=kit("owner");var events=new ArrayList<UiEvent>();
        var withSubmit=new ViewSpec(new Nodes.Secret("secret",ControlState.READY,"","Passphrase","",Nodes.SecretKind.INPUT,"","unlock"));
        var view=kit.create(withSubmit,events::add);
        assertThat(((Nodes.Secret)view.spec().root()).submitAction()).isEqualTo("unlock");
        secret(view,"secret","typed".toCharArray());
        view.update(new ViewSpec(new Nodes.Secret("secret",ControlState.READY,"","Passphrase","")));
        view.withSecret("secret",chars -> assertThat(chars).as("changing the submit action keeps the typed value").containsExactly('t','y','p','e','d'));
        emit(view,new UiEvent.Action("unlock"));assertThat(events).contains(new UiEvent.Action("unlock"));
        stop(kit);
    }); }
    @Test void createUpdateActionAndClose() throws Exception { edt(() -> {
        var kit=kit("owner"); var events=new ArrayList<UiEvent>(); var view=kit.create(spec("Before"),events::add);
        view.update(spec("After")); assertThat(view.spec()).isEqualTo(spec("After"));
        emit(view,new UiEvent.Action("go")); assertThat(events).containsExactly(new UiEvent.Action("go"));
        view.close(); view.close(); assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class); stop(kit);
    }); }
    @Test void preservesSecretsAcrossStableUpdateAndErasesCallbackCopies() throws Exception { edt(() -> {
        var kit=kit("owner"); var view=kit.create(spec("Before"), e -> {});
        secret(view,"secret",new char[]{'x','y'}); view.update(spec("After")); var held=new char[1][];
        assertThatThrownBy(() -> view.withSecret("secret", chars -> { held[0]=chars; assertThat(chars).containsExactly('x','y'); throw new IllegalStateException("callback"); })).isInstanceOf(IllegalStateException.class);
        assertThat(held[0]).containsExactly((char)0,(char)0);
        view.clearSecret("secret"); view.withSecret("secret", chars -> assertThat(chars).isEmpty()); stop(kit);
    }); }
    @Test void removingSecretDestroysItAndHandlerFailuresAreContained() throws Exception { edt(() -> {
        var kit=kit("owner"); var view=kit.create(spec("Before"),e -> { throw new IllegalStateException("handler"); });
        assertThatCode(() -> emit(view,new UiEvent.Action("go"))).doesNotThrowAnyException();
        secret(view,"secret",new char[]{'x'}); view.update(new ViewSpec(new Nodes.Text("title",ControlState.READY,"Only",Nodes.TextRole.BODY)));
        view.update(spec("Again")); view.withSecret("secret", chars -> assertThat(chars).isEmpty()); stop(kit);
    }); }
    @Test void rejectsCrossOwnerAndRetainedViewAfterStop() throws Exception { edt(() -> {
        var first=kit("first"); var second=kit("second"); var view=first.create(spec(""), e -> {});
        assertThatCode(() -> owns(first,view)).doesNotThrowAnyException();
        assertThatThrownBy(() -> owns(second,view)).isInstanceOf(IllegalArgumentException.class);
        stop(first); assertThatThrownBy(() -> view.update(spec("stale"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> first.create(spec("stale"),e -> {})).isInstanceOf(IllegalStateException.class);
        stop(second);
    }); }
    @Test void actualViewOperationsRequireEdtAndCloseMayBeOffEdt() throws Exception {
        var holder=new UiKit[1];var view=new UiView[1]; edt(() -> {holder[0]=kit("owner");view[0]=holder[0].create(spec(""),e -> {});});
        assertThatThrownBy(() -> holder[0].create(spec(""),e -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(view[0]::spec).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view[0].update(spec(""))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> view[0].focus("secret")).isInstanceOf(IllegalStateException.class);
        view[0].close(); edt(() -> stop(holder[0]));
    }
    @Test void scopedIconsAndMalformedDescriptorsAreRejected() throws Exception { edt(() -> {
        var kit=kit("owner");
        assertThatThrownBy(() -> new IconAsset("owner:empty","M 1 1 Z M 15 15 Z")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kit.registerIcon(new IconAsset("foreign:logo","M 1 1 L 15 15"))).isInstanceOf(IllegalArgumentException.class);
        var icon=kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"));
        assertThatThrownBy(() -> kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"))).isInstanceOf(IllegalArgumentException.class);
        icon.close();icon.close();kit.registerIcon(new IconAsset("owner:logo","M 1 1 L 15 15"));stop(kit);
        assertThatThrownBy(() -> new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(spec("").root(),spec("").root())))).isInstanceOf(IllegalArgumentException.class);
    }); }
    @Test void throwingHandlersMayUpdateAndCloseTheirOwnView() throws Exception { edt(() -> {
        var kit=kit("owner");var handle=new UiView[1];handle[0]=kit.create(spec("Before"),e -> {handle[0].update(spec("After"));throw new IllegalStateException("after update");});
        emit(handle[0],new UiEvent.Action("update"));assertThat(handle[0].spec()).isEqualTo(spec("After"));handle[0].close();
        handle[0]=kit.create(spec("Before"),e -> {handle[0].close();throw new IllegalStateException("after close");});
        assertThatCode(() -> emit(handle[0],new UiEvent.Action("close"))).doesNotThrowAnyException();assertThatThrownBy(handle[0]::spec).isInstanceOf(IllegalStateException.class);stop(kit);
    }); }
    @Test void selectAllUsesLiveTextWithoutEditingOrFocusingAndRejectsNonFields() throws Exception { edt(() -> {
        var kit=kit("owner"); var events=new ArrayList<UiEvent>();
        var view=kit.create(new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Field("field",ControlState.READY,Nodes.FieldKind.SEARCH,"old","","Search",""),
            new Nodes.Secret("secret",ControlState.READY,"","Password","")))),events::add);
        fieldText(view,"field","current input"); events.clear();
        view.selectAll("field");
        assertThat(fieldSelection(view,"field")).containsExactly(0,13);
        assertThat(events).isEmpty();
        for(var id:List.of("root","secret","unknown"))
            assertThatThrownBy(() -> view.selectAll(id)).isInstanceOf(IllegalArgumentException.class);
        view.update(new ViewSpec(new Nodes.Field("field",ControlState.READY,Nodes.FieldKind.TEXTAREA,"old","","Search","")));
        view.selectAll("field");
        assertThat(fieldSelection(view,"field")).containsExactly(0,3);
        assertThat(events).isEmpty();
        view.close();assertThatThrownBy(() -> view.selectAll("field")).isInstanceOf(IllegalStateException.class);stop(kit);
    }); }
    @Test void editorAcceptsAPurposeBuiltCanvasButRejectsOrdinaryRoots() throws Exception { edt(() -> {
        var kit=kit("owner");
        var custom=new ViewSpec(new Nodes.Custom("node",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor"));
        for (var root : List.of(new javax.swing.JPanel(), new javax.swing.JButton("Button"), new javax.swing.JLabel("Label"),
                new javax.swing.JList<>(), new javax.swing.JTable(), new javax.swing.JTree(), new javax.swing.JComboBox<>(),
                new javax.swing.JSpinner(), new javax.swing.JSlider(), new javax.swing.JScrollPane(new javax.swing.JPanel()))) {
            var rejected=kit.create(custom,e -> {});
            assertThatThrownBy(() -> rejected.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,root,() -> {})))
                .isInstanceOf(IllegalArgumentException.class);
            rejected.close();
        }
        var view=kit.create(custom,e -> {});
        var disposed=new java.util.concurrent.atomic.AtomicInteger();
        var canvas=new javax.swing.JComponent() { };
        canvas.setLayout(new java.awt.BorderLayout());
        canvas.add(new javax.swing.JScrollPane(new javax.swing.JTextArea("retained")));
        view.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,canvas,disposed::incrementAndGet));
        view.update(custom);
        assertThat(disposed).hasValue(0);
        view.close(); assertThat(disposed).hasValue(1); stop(kit);
    }); }
    @Test void customContentCannotCrossOwnersAndDisposesOnRemoval() throws Exception { edt(() -> {
        var first=kit("first");var second=kit("second");var custom=new ViewSpec(new Nodes.Custom("node",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor"));
        var a=first.create(custom,e -> {});var b=second.create(custom,e -> {});var widget=new javax.swing.JTextArea("document");var disposed=new java.util.concurrent.atomic.AtomicInteger();
        a.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,widget,disposed::incrementAndGet));
        assertThatThrownBy(() -> b.registerContent(new CustomContent("content",Nodes.CustomKind.EDITOR,widget,() -> {}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.registerContent(new CustomContent("node",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {}))).isInstanceOf(IllegalArgumentException.class);
        a.update(new ViewSpec(new Nodes.Custom("replacement",ControlState.READY,"content",Nodes.CustomKind.EDITOR,"Editor")));assertThat(disposed.get()).isEqualTo(1);stop(first);stop(second);assertThat(disposed.get()).isEqualTo(1);
    }); }
    @Test void reentrantCloseDuringRemovalDisposesEachContentExactlyOnce() throws Exception { edt(() -> {
        var kit=kit("owner");
        var tree=new ViewSpec(new Nodes.Stack("root",ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Custom("first",ControlState.READY,"first-content",Nodes.CustomKind.EDITOR,"First"),
            new Nodes.Custom("second",ControlState.READY,"second-content",Nodes.CustomKind.EDITOR,"Second"))));
        var view=kit.create(tree,e -> {});var first=new java.util.concurrent.atomic.AtomicInteger();var second=new java.util.concurrent.atomic.AtomicInteger();
        try {
            view.registerContent(new CustomContent("first-content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {first.incrementAndGet();view.close();}));
            view.registerContent(new CustomContent("second-content",Nodes.CustomKind.EDITOR,new javax.swing.JTextArea(),() -> {second.incrementAndGet();view.close();}));
            view.update(spec("Removed"));
            assertThat(first.get()).isEqualTo(1);assertThat(second.get()).isEqualTo(1);
            view.close();assertThat(first.get()).isEqualTo(1);assertThat(second.get()).isEqualTo(1);
        } finally {view.close();stop(kit);}
    }); }
    /**
     * Editor chrome (Plan 2 Task 9-kit): a DOCUMENT split whose specialized document stays registered while hidden behind a
     * loading state, an inline find bar with field Shift+Enter, Escape and a stack Escape, a pressed option button, a Vim
     * command line with a caret and an error tint, and a filter field driving a list.
     */
    static ViewSpec editor(boolean loading, boolean matchCase, int caret) {
        var hidden=new ControlState(false,true,false,"");
        var find=new Nodes.Field("find",ControlState.READY,Nodes.FieldKind.SEARCH,"needle","Find","Find","next",Optional.empty(),Nodes.FieldPresentation.EMBEDDED,"previous","close","");
        var option=new Nodes.Button("case",ControlState.READY,"Aa","",Nodes.ButtonKind.GHOST,"case","Match case",Nodes.ButtonPresentation.STANDARD,Optional.empty(),Optional.of(matchCase));
        var bar=new Nodes.Stack("find-bar",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(find,option),"close");
        var vim=new Nodes.Text("vim-line",ControlState.READY,":s/a/b",Nodes.TextRole.MONO,Optional.of(Nodes.Severity.DANGER),OptionalInt.of(caret));
        var filter=new Nodes.Field("filter",ControlState.READY,Nodes.FieldKind.SEARCH,"","Filter","Filter","choose",Optional.empty(),Nodes.FieldPresentation.STANDARD,"","","languages");
        var languages=new Nodes.ItemList("languages",ControlState.READY,Nodes.ListKind.SINGLE,List.of(new ListItem("java",Optional.empty(),List.of(new Nodes.Text("java.name",ControlState.READY,"Java",Nodes.TextRole.BODY)),List.of(),List.of())),Set.of(),"choose",List.of());
        return new ViewSpec(new Nodes.Split("tab",ControlState.READY,Nodes.SplitKind.DOCUMENT,List.of(
            new Nodes.Custom("document",loading?hidden:ControlState.READY,"text",Nodes.CustomKind.EDITOR,"Document"),
            new Nodes.Empty("state",loading?ControlState.READY:hidden,new Nodes.Icon("state.icon",ControlState.READY,"file",Nodes.Hue.NEUTRAL,Nodes.IconSize.EMPTY,"File"),"Loading notes.txt…","",List.of()),
            bar,vim,filter,languages)));
    }
    @Test void editorChromeDescriptorsKeepTheirDocumentAndReportTheirKeys() throws Exception { edt(() -> {
        var kit=kit("owner");var events=new ArrayList<UiEvent>();var disposed=new java.util.concurrent.atomic.AtomicInteger();
        var view=kit.create(editor(true,false,6),events::add);
        var document=new javax.swing.JTextArea("kept");
        view.registerContent(new CustomContent("text",Nodes.CustomKind.EDITOR,new javax.swing.JScrollPane(document),disposed::incrementAndGet));
        assertThat(view.spec()).isEqualTo(editor(true,false,6));
        view.update(editor(false,true,2));assertThat(view.spec()).isEqualTo(editor(false,true,2));
        assertThat(disposed.get()).as("showing the hidden document keeps its registration").isZero();
        view.focus("find");view.focus("filter");
        emit(view,new UiEvent.Action("previous"));emit(view,new UiEvent.Action("close"));emit(view,new UiEvent.Action("case"));
        emit(view,new UiEvent.Selected("languages",Set.of("java")));emit(view,new UiEvent.Action("choose"));
        assertThat(events).containsExactly(new UiEvent.Action("previous"),new UiEvent.Action("close"),new UiEvent.Action("case"),
            new UiEvent.Selected("languages",Set.of("java")),new UiEvent.Action("choose"));
        stop(kit);assertThat(disposed.get()).isEqualTo(1);
    }); }
}
