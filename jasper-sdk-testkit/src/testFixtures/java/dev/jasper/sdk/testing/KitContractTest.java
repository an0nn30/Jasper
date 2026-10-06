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
    protected static void edt(Runnable body) throws Exception { SwingUtilities.invokeAndWait(body); }
    protected static ViewSpec spec(String title) { return new ViewSpec(new Nodes.Stack("root", ControlState.READY,
        Nodes.LayoutRole.VERTICAL, List.of(new Nodes.Text("title", ControlState.READY,title,Nodes.TextRole.BODY),
        new Nodes.Secret("secret",ControlState.READY,"","Password","")))); }
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
            List.of(pending ? new Nodes.Button("web.star",ControlState.READY,"","star",Nodes.ButtonKind.GHOST,"favorite:web","Add to favorites",Nodes.ButtonPresentation.EMBEDDED)
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
