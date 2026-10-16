package dev.jasper.sdk.testing;

import dev.jasper.sdk.*;
import dev.jasper.sdk.settings.*;
import dev.jasper.sdk.ui.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeKitMountingTest {
    @Test void independentFakeRowsUseDeclaredKindsValidationAndNestedProjection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();
                host.start(new PluginInfo("test.kinds","Kinds","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    var settings=List.of(
                        context.settings().declare(SettingSpec.of("bool",SettingKind.bool(),false).label("Bool")),
                        context.settings().declare(SettingSpec.of("choice",SettingKind.choice(List.of(Choice.of("off","Off",false),Choice.of("on","On",true))),false).label("Choice")),
                        context.settings().declare(SettingSpec.of("number",SettingKind.number(0,10),1.0).label("Number")),
                        context.settings().declare(SettingSpec.of("text",SettingKind.text(value -> !value.isBlank(),"Required"),"before").label("Text")),
                        context.settings().declare(SettingSpec.of("list",SettingKind.stringList(),List.of("before")).label("List")));
                    context.settings().page((SettingsPanel) rows -> {
                        var body=new Nodes.Section("section",ControlState.READY,"Settings",new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,settings.stream().map(rows::row).toList()));
                        var made=(FakeKitView)context.kit().create(new ViewSpec(body),event -> {throw new AssertionError("Owned setting event leaked");});view.set(made);return made;
                    });
                });
                host.settingsPanel("test.kinds");
                var card=(Nodes.Card)((Nodes.Section)view.get().spec().root()).body();
                var choice=(Nodes.SettingRow)card.children().get(1);
                assertThat(((Nodes.Stack)choice.control()).children().getFirst()).isInstanceOf(Nodes.Choice.class);
                view.get().emit(new UiEvent.Toggled("settings:bool",true));
                view.get().emit(new UiEvent.Selected("settings:choice",Set.of("on")));
                view.get().emit(new UiEvent.Edited("settings:number","NaN"));
                assertThatThrownBy(() -> host.applySettingsPanel("test.kinds")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:number","2.5"));
                view.get().emit(new UiEvent.Edited("settings:text",""));
                assertThatThrownBy(() -> host.applySettingsPanel("test.kinds")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:text","after"));
                view.get().emit(new UiEvent.Removed("settings:list","item-0"));
                view.get().emit(new UiEvent.Edited("settings:list:new","one"));
                view.get().emit(new UiEvent.Action("settings:list:add"));
                view.get().emit(new UiEvent.Edited("settings:list:new","two"));
                view.get().emit(new UiEvent.Action("settings:list:add"));
                host.applySettingsPanel("test.kinds");
                assertThat(host.savedSettings("test.kinds")).containsEntry("bool",true).containsEntry("choice","on")
                    .containsEntry("number",2.5).containsEntry("text","after").containsEntry("list",List.of("one","two"));
                assertThat(view.get().owner.failures()).isEmpty();
            }
        });
    }
    /** Spec 13: a stringList host row is the generated pages' ordered-list editor, here as in the host. */
    @Test void aStringListRowIsTheOrderedListEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();var words=new AtomicReference<Setting<List<String>>>();
                host.start(new PluginInfo("test.list","List","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    words.set(context.settings().declare(SettingSpec.of("words",SettingKind.stringList(word -> !word.contains("/")),List.of("a","b")).label("Words")));
                    context.settings().page((SettingsPanel) h -> {
                        rows.set(h);
                        var made=(FakeKitView)context.kit().create(new ViewSpec(new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(h.row(words.get())))),
                            event -> {throw new AssertionError("Owned setting event leaked");});
                        view.set(made);return made;
                    });
                });
                host.settingsPanel("test.list");
                var editor=editor(view.get());
                assertThat(editor.id()).isEqualTo("settings:words:editor");
                assertThat(editor.children().getFirst()).isInstanceOfSatisfying(Nodes.ItemList.class, list -> {
                    assertThat(list.id()).isEqualTo("settings:words");
                    assertThat(list.kind()).isEqualTo(Nodes.ListKind.ORDERED);
                    assertThat(list.items()).extracting(ListItem::id).containsExactly("item-0","item-1");
                });
                assertThat(entry(view.get()).id()).isEqualTo("settings:words:new");
                view.get().emit(new UiEvent.Reordered("settings:words","item-1",0));
                assertThat(rows.get().pending(words.get())).containsExactly("b","a");
                view.get().emit(new UiEvent.Removed("settings:words","item-9"));
                view.get().emit(new UiEvent.Reordered("settings:words","item-0",7));
                assertThat(rows.get().pending(words.get())).as("a row that is gone moves nothing").containsExactly("b","a");
                view.get().emit(new UiEvent.Edited("settings:words:new","x/y"));
                view.get().emit(new UiEvent.Action("settings:words:add"));
                assertThat(rows.get().pending(words.get())).as("a rejected value is not staged").containsExactly("b","a");
                assertThat(entry(view.get()).value()).as("the entry keeps the text to correct").isEqualTo("x/y");
                assertThat(((Nodes.ItemList)editor(view.get()).children().getFirst()).state().error()).isNotBlank();
                assertThatThrownBy(() -> host.applySettingsPanel("test.list")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:words:new","c"));
                view.get().emit(new UiEvent.Action("settings:words:add"));
                assertThat(rows.get().pending(words.get())).containsExactly("b","a","c");
                assertThat(entry(view.get()).value()).isEmpty();
                assertThat(((Nodes.ItemList)editor(view.get()).children().getFirst()).state().error()).isEmpty();
                host.applySettingsPanel("test.list");
                assertThat(host.savedSettings("test.list")).containsEntry("words",List.of("b","a","c"));
                assertThat(view.get().owner.failures()).isEmpty();
            }
        });
    }
    /** The ordered-list editor in the trailing slot of the card's only host row. */
    private static Nodes.Stack editor(FakeKitView view) {
        var row=(Nodes.SettingRow)((Nodes.Card)view.spec().root()).children().getFirst();
        return (Nodes.Stack)((Nodes.Stack)row.control()).children().getFirst();
    }
    /** The ordered-list editor's entry field. */
    private static Nodes.Field entry(FakeKitView view) { return (Nodes.Field)((Nodes.Stack)editor(view).children().get(1)).children().getFirst(); }
    @Test void rowsKeepTheSearchHighlightThePluginAskedForThroughHostProjection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();var size=new AtomicReference<Setting<Integer>>();
                java.util.function.Function<SettingsPanelHost,ViewSpec> spec=h -> {
                    var hostRow=(Nodes.SettingRow)h.row(size.get());
                    return new ViewSpec(new Nodes.Card("card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(
                        new Nodes.SettingRow(hostRow.id(),hostRow.state(),"Presented size","Presented description",hostRow.control(),true,true,"Recommended"),
                        new Nodes.SettingRow("own",ControlState.READY,"Own","",new Nodes.Toggle("own-toggle",ControlState.READY,"Own",true,"own-toggle",false),false,true,"Optional"),
                        new Nodes.FormRow("form",ControlState.READY,"Form","",new Nodes.Text("form-text",ControlState.READY,"x",Nodes.TextRole.BODY),Optional.empty(),true))));
                };
                host.start(new PluginInfo("test.highlight","Highlight","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
                    context.settings().page((SettingsPanel) h -> {rows.set(h);var made=(FakeKitView)context.kit().create(spec.apply(h),e -> {});view.set(made);return made;});
                });
                host.settingsPanel("test.highlight");
                view.get().update(spec.apply(rows.get()));
                view.get().emit(new UiEvent.Edited("settings:size","5"));
                var card=(Nodes.Card)view.get().spec().root();
            assertThat(((Nodes.SettingRow)card.children().get(0)).titleNote()).isEqualTo("Recommended");
            assertThat(((Nodes.SettingRow)card.children().get(0)).title()).isEqualTo("Presented size");
            assertThat(((Nodes.SettingRow)card.children().get(0)).description()).isEqualTo("Presented description");
            assertThat(((Nodes.SettingRow)card.children().get(0)).dependent()).isTrue();
            assertThat(((Nodes.SettingRow)card.children().get(1)).titleNote()).isEqualTo("Optional");
            host.highlightSettingsPanel("test.highlight", "size");
            var searched=(Nodes.Card)view.get().spec().root();
            assertThat(((Nodes.SettingRow)searched.children().get(0)).titleNote()).isEqualTo("Recommended");
            assertThat(((Nodes.SettingRow)searched.children().get(1)).titleNote()).isEqualTo("Optional");

                assertThat(card.children()).allSatisfy(node -> assertThat(node instanceof Nodes.SettingRow row ? row.highlighted() : ((Nodes.FormRow)node).highlighted()).isTrue());
            }
        });
    }
    @Test void resetRemovesSavedOverrideWithoutChangingOrdinaryStageDefault() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();var size=new AtomicReference<Setting<Integer>>();
                host.start(new PluginInfo("test.reset","Reset","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
                    context.settings().page((SettingsPanel) h -> {rows.set(h);var made=(FakeKitView)context.kit().create(new ViewSpec(h.row(size.get())),e -> {});view.set(made);return made;});
                });
                size.get().set(7).join();host.settingsPanel("test.reset");
                view.get().emit(new UiEvent.Action("settings:size:reset"));host.applySettingsPanel("test.reset");
                assertThat(host.savedSettings("test.reset")).doesNotContainKey("size");
                size.get().set(7).join();rows.get().stage(size.get(),4);host.applySettingsPanel("test.reset");
                assertThat(host.savedSettings("test.reset")).containsEntry("size",4L);
                view.get().emit(new UiEvent.Action("settings:size:reset"));host.applySettingsPanel("test.reset");
                assertThat(host.savedSettings("test.reset")).doesNotContainKey("size");
                assertThat(host.pendingSettings("test.reset")).isEmpty();
            }
        });
    }
    @Test void reloadRebasesCleanAndPendingMountedRowsUntilPageCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();var size=new AtomicReference<Setting<Integer>>();
                var context=host.start(new PluginInfo("test.reload","Reload","1.0.0",Set.of()),Set.of(),Set.of(),started -> {
                    size.set(started.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
                    started.settings().page((SettingsPanel) h -> {rows.set(h);var made=(FakeKitView)started.kit().create(new ViewSpec(h.row(size.get())),e -> {});view.set(made);return made;});
                });
                host.settingsPanel("test.reload");assertThat(context.settings.reloadListenerCount()).isEqualTo(1);size.get().set(6).join();
                assertThat(displayedSize(view.get())).isEqualTo("6");
                rows.get().stage(size.get(),8);size.get().set(5).join();
                assertThat(displayedSize(view.get())).isEqualTo("8");assertThat(host.pendingSettings("test.reload")).containsEntry("size",8);
                size.get().set(8).join();assertThat(displayedSize(view.get())).isEqualTo("8");assertThat(host.pendingSettings("test.reload")).isEmpty();
                host.closeSettingsPanel("test.reload");assertThat(context.settings.reloadListenerCount()).isZero();size.get().set(9).join();
                assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class);
                assertThat(host.failures()).isEmpty();
            }
        });
    }
    private static String displayedSize(FakeKitView view) {
        var row=(Nodes.SettingRow)view.spec().root();
        return ((Nodes.Field)((Nodes.Stack)row.control()).children().getFirst()).value();
    }
    @Test void ownedTabPinsDuringChooserAndOwnerClosureDiscardsSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                var context=host.start(new PluginInfo("test.chooser","Chooser","1.0.0",Set.of()),Set.of(),Set.of(),c -> {});
                var view=context.kit().create(new ViewSpec(new Nodes.Text("body",ControlState.READY,"Document",Nodes.TextRole.BODY)),e -> {});
                var tab=context.windows().openTab(new ContentSpec("doc","Document"),null,view);
                var selected=java.nio.file.Path.of("selected").toAbsolutePath();host.queuePathSelection(List.of(selected));
                host.duringPathSelection(() -> {tab.move(true);assertThat(tab.isSeparate()).isFalse();assertThat(tab.requestClose().join()).isFalse();});
                assertThat(context.windows().chooseFiles(tab,"Choose",Optional.empty())).containsExactly(selected);
                tab.move(true);assertThat(tab.isSeparate()).isTrue();
                host.queuePathSelection(List.of(selected));host.duringPathSelection(tab::close);
                assertThat(context.windows().chooseFiles(tab,"Choose",Optional.empty())).isEmpty();
                assertThatThrownBy(view::spec).isInstanceOf(IllegalStateException.class);
            }
        });
    }
    @Test void settingsFactoryFailureIsContained() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()) {
                host.start(new PluginInfo("test.failure","Failure","1.0.0",Set.of()),Set.of(),Set.of(),context -> context.settings().page(new SettingsPanel(){
                    public UiView create(SettingsPanelHost h){throw new IllegalStateException("Factory failure");}
                }));
                assertThat(host.settingsPanel("test.failure")).isPresent();
                assertThat(host.settingsPanel("test.failure")).isPresent();assertThat(host.failures()).hasSize(1);
                host.closeSettingsPanel("test.failure");
            }
        });
    }
    @Test void settingsDraftEventsApplyCancelAndOwnerClose() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()){
                var view=new AtomicReference<FakeKitView>();var rows=new AtomicReference<SettingsPanelHost>();
                var size=new AtomicReference<Setting<Integer>>();var calls=new ArrayList<String>();
                host.start(new PluginInfo("test.page","Page","1.0.0",Set.of()),Set.of(),Set.of(),context -> {
                    size.set(context.settings().declare(SettingSpec.of("size",SettingKind.integer(1,10),4).label("Size")));
                    context.settings().page(new SettingsPanel(){
                        public UiView create(SettingsPanelHost h){rows.set(h);var result=(FakeKitView)context.kit().create(new ViewSpec(h.row(size.get())),e -> calls.add(e.id()));view.set(result);return result;}
                        public void apply(){calls.add("apply");}
                        public void reset(){calls.add("reset");}
                        public void dispose(){calls.add("dispose");}
                    });
                });
                host.settingsPanel("test.page");
                view.get().emit(new UiEvent.Edited("settings:size","99"));
                assertThatThrownBy(() -> host.applySettingsPanel("test.page")).isInstanceOf(IllegalStateException.class);
                view.get().emit(new UiEvent.Edited("settings:size","7"));
                assertThat(host.pendingSettings("test.page")).containsEntry("size",7);assertThat(size.get().get()).isEqualTo(4);
                view.get().emit(new UiEvent.Action("plugin"));assertThat(calls).containsExactly("plugin");
                host.applySettingsPanel("test.page");assertThat(size.get().get()).isEqualTo(7);
                rows.get().stage(size.get(),9);
                var row=(Nodes.SettingRow)view.get().spec().root();
                assertThat(((Nodes.Field)((Nodes.Stack)row.control()).children().getFirst()).value()).isEqualTo("9");
                view.get().emit(new UiEvent.Action("settings:size:reset"));assertThat(host.pendingSettings("test.page")).containsEntry("size",4);
                host.closeSettingsPanel("test.page");host.closeSettingsPanel("test.page");
                assertThat(calls).containsExactly("plugin","apply","reset","dispose");assertThat(size.get().get()).isEqualTo(7);
                assertThatThrownBy(() -> rows.get().stage(size.get(),8)).isInstanceOf(IllegalStateException.class);
                host.settingsPanel("test.page");host.stopAll();
                assertThatThrownBy(view.get()::spec).isInstanceOf(IllegalStateException.class);
                assertThat(calls).containsExactly("plugin","apply","reset","dispose","reset","dispose");
            }
        });
    }
    @Test void panelRegistrationOwnsEachViewAndContainsFactoryFailure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try(var host=new FakePluginHost()){
                var context=host.start(new PluginInfo("test.panel","Panel","1.0.0",Set.of()),Set.of(),Set.of(),c -> {});
                var views=new ArrayList<UiView>();var disposed=new AtomicInteger();
                var registration=context.panels().register(new PanelSpec("test.panel.files","Files","folder",Anchor.LEFT).withSplit(),h -> {
                    var view=context.kit().create(new ViewSpec(new Nodes.Custom("node",ControlState.READY,"document",Nodes.CustomKind.EDITOR,"Document")),e -> {});
                    view.registerContent(new CustomContent("document",Nodes.CustomKind.EDITOR,new JTextArea(),disposed::incrementAndGet));views.add(view);return view;
                });
                UUID one=UUID.randomUUID();host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",one);host.openPanel("test.panel.files",UUID.randomUUID());
                assertThat(views).hasSize(2);registration.close();registration.close();assertThat(disposed.get()).isEqualTo(2);
                context.panels().register(new PanelSpec("test.panel.broken","Broken","folder",Anchor.LEFT),h -> {throw new IllegalStateException("failure");});
                assertThat(host.openPanel("test.panel.broken",one)).isNull();assertThat(host.failures()).hasSize(1);
            }
        });
    }
}
