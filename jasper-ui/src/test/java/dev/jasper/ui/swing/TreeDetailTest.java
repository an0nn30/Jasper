package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TreeDetailTest {
    private static ViewSpec tree(boolean open) {return new ViewSpec(new Tree("tree",ControlState.READY,List.of(new TreeItem("group","Group",Optional.empty(),false,List.of(new TreeItem("a","First",Optional.empty(),true,List.of()),new TreeItem("b","Second",Optional.empty(),true,List.of())))),open?Set.of("group"):Set.of(),Set.of(),""));}
    private static ViewSpec body(String label) {return new ViewSpec(new Button("edit",ControlState.READY,label,"",ButtonKind.SECONDARY,"edit",""));}
    @Test void rejectedSameIdIconUpdateRetainsPriorActionDocumentAndRecovery() {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            var priorFocus=java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus=new SettingsKitTest.Focus();
            java.util.function.BiFunction<String,String,ViewSpec> spec=(label,icon)->new ViewSpec(new Nodes.Stack("body",ControlState.READY,LayoutRole.VERTICAL,List.of(
                    new Field("input",ControlState.READY,FieldKind.TEXT,label,"","Input",""),
                    new Button("edit",ControlState.READY,label,icon,ButtonKind.SECONDARY,label,""))));
            try(var view=new KitView(context,tree(true),events::add)) {
                var detail=view.attachTreeDetail("tree","a",spec.apply("Original",""),events::add,()->{});
                var button=KitViewTest.find(view,"edit",javax.swing.JButton.class);
                var input=KitViewTest.find(view,"input.input",javax.swing.text.JTextComponent.class);
                var document=input.getDocument();input.select(1,4);
                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);focus.focus(input);
                assertThatThrownBy(()->detail.update(spec.apply("Rejected","missing:icon"))).isInstanceOf(IllegalArgumentException.class);
                assertThat(button.getText()).isEqualTo("Original");
                button.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("Original"));
                assertThat(input.getDocument()).isSameAs(document);assertThat(input.getText()).isEqualTo("Original");
                assertThat(focus.getFocusOwner()).isSameAs(input);
                assertThat(input.getSelectionStart()).isEqualTo(1);assertThat(input.getSelectionEnd()).isEqualTo(4);
                detail.update(spec.apply("Accepted","add"));
                assertThat(KitViewTest.find(view,"edit",javax.swing.JButton.class)).isSameAs(button);
                assertThat(input.getDocument()).isSameAs(document);button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("Original"),new UiEvent.Action("Accepted"));
            } finally {java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus);KitViewTest.release(context);}
        });
    }

    @Test void detailPreflightsLazyArtworkBeforePublishingAnyControlChanges() {
        Edt.run(() -> {
            var context=KitFixtures.context();var bad=new MenuItem("bad","Bad","missing:icon","bad",true,false);
            List<Nodes.Node> lazy=List.of(
                new MenuButton("lazy",ControlState.READY,"Menu","",ButtonKind.GHOST,"",List.of(bad),""),
                new Choice("lazy",ControlState.READY,ChoiceKind.DROPDOWN,List.of(new Option("bad","Bad","missing:icon","")),"","Choice",""),
                new Header("lazy",ControlState.READY,"Header",OptionalInt.empty(),Optional.empty(),Optional.empty(),List.of(bad)),
                new ItemList("lazy",ControlState.READY,ListKind.SINGLE,List.of(),Set.of(),"",List.of(bad)),
                new Table("lazy",ControlState.READY,List.of(),List.of(new TableRow("row",Map.of(),Optional.of("missing:icon"))),Set.of(),"",List.of()),
                new Tree("lazy",ControlState.READY,List.of(new TreeItem("closed","Closed",Optional.empty(),false,
                    List.of(new TreeItem("hidden","Hidden",Optional.of("missing:icon"),true,List.of())))),Set.of(),Set.of(),""),
                new Breadcrumbs("lazy",ControlState.READY,List.of(new Crumb("crumb","Crumb",List.of(bad))),"Path",false,"","","",""));
            try(var view=new KitView(context,tree(true),e->{})) {
                var detail=view.attachTreeDetail("tree","a",body("Original"),e->{},()->{});
                var button=KitViewTest.find(view,"edit",javax.swing.JButton.class);
                for(var rejected:lazy) {
                    var spec=new ViewSpec(new Nodes.Stack("body",ControlState.READY,LayoutRole.VERTICAL,List.of(body("Rejected").root(),rejected)));
                    assertThatThrownBy(()->detail.update(spec)).as(rejected.getClass().getSimpleName()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing:icon");
                    assertThat(button.getText()).isEqualTo("Original");assertThat(detail.isAttached()).isTrue();
                }
                detail.update(body("Accepted"));assertThat(button.getText()).isEqualTo("Accepted");
            } finally {KitViewTest.release(context);}
        });
    }

    @Test void keycapsArePassiveOnRowsAndPreserveCandidateButtonAction() {
        Edt.run(()->{
            var context=KitFixtures.context();var events=new java.util.ArrayList<UiEvent>();
            try(var view=new KitView(context,tree(true),events::add)) {
                var sequence=new KeycapSequence(List.of("Cmd","Shift","H"),"Command Shift H");
                view.setTreeRowPresentation("tree",Map.of("a",new TreeRowPresentation("Global",false,Optional.of(sequence))));
                var detail=view.attachTreeDetail("tree","a",body("Candidate"),events::add,()->{});
                view.setButtonKeycaps("edit",Optional.of(sequence));
                view.setSize(500,250);InteractiveRowsTest.layout(view);
                var button=KitViewTest.find(view,"edit",javax.swing.JButton.class);
                assertThat(button.getIcon()).isNotNull();
                button.doClick(0);assertThat(events).contains(new UiEvent.Action("edit"));
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Command Shift H");
                detail.close();
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void detailInheritsBusyAndOldHandleCannotCloseReplacement() {
        Edt.run(()->{
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();int[] detached={0};
            try(var view=new KitView(context,tree(true),events::add)) {
                var first=view.attachTreeDetail("tree","a",body("Edit"),events::add,()->detached[0]++);
                var button=KitViewTest.find(view,"edit",javax.swing.JButton.class);
                var value=(Tree)tree(true).root();
                view.update(new ViewSpec(new Tree(value.id(),new ControlState(true,true,true,""),value.roots(),value.expandedIds(),value.selectedIds(),value.activateAction())));
                assertThat(button.isEnabled()).isFalse();button.doClick(0);assertThat(events).isEmpty();
                view.update(tree(true));assertThat(button.isEnabled()).isTrue();
                first.close();assertThat(button.getActionListeners()).isEmpty();
                var second=view.attachTreeDetail("tree","b",body("Second"),events::add,()->detached[0]++);
                first.close();assertThat(second.isAttached()).isTrue();assertThat(detached[0]).isEqualTo(1);
                view.close();assertThat(second.isAttached()).isFalse();assertThat(detached[0]).isEqualTo(2);
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void constrainedKeycapsGrowRowsAndRemainInsideTheViewport() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,tree(true),event->{})) {
                var sequence=new KeycapSequence(List.of("Command","Shift","Option","Control","Return"),"Command Shift Option Control Return");
                view.setTreeRowPresentation("tree",Map.of("a",new TreeRowPresentation("Global",true,Optional.of(sequence))));
                view.setSize(600,260);InteractiveRowsTest.layout(view);var control=(Trees.TreeControl)view.control("tree");int wide=control.rowShellHeight(1);
                view.setSize(260,260);InteractiveRowsTest.layout(view);
                assertThat(control.rowShellHeight(1)).isGreaterThan(wide);
                var label=KitViewTest.find(view,"@tree/tree/a/keys",javax.swing.JLabel.class);
                assertThat(label.getHeight()).isGreaterThanOrEqualTo(((Keycaps)label.getIcon()).heightAt(label.getWidth()));
                var rect=javax.swing.SwingUtilities.convertRectangle(label.getParent(),label.getBounds(),control.canvas);
                assertThat(rect.x).isGreaterThanOrEqualTo(0);assertThat(rect.x+rect.width).isLessThanOrEqualTo(control.scroll.getViewport().getWidth());
                view.setTreeRowPresentation("tree",Map.of());assertThat(control.rowShellHeight(1)).isLessThanOrEqualTo(wide);
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void oneStableDetailMovesFollowingRowsAndClosesExactlyOnceOnCollapse() {
        Edt.run(()->{
            var context=KitFixtures.context();int[] detached={0};
            try(var view=new KitView(context,tree(true),event->{})) {
                view.setSize(400,240);InteractiveRowsTest.layout(view);
                var control=(Trees.TreeControl)view.control("tree");
                int before=control.top(2);
                var detail=view.attachTreeDetail("tree","a",body("Edit"),event->{},()->detached[0]++);
                InteractiveRowsTest.layout(view);
                assertThat(control.top(2)).isGreaterThan(before);
                assertThatThrownBy(()->view.attachTreeDetail("tree","b",body("Other"),event->{},()->{})).isInstanceOf(IllegalStateException.class);
                var button=KitViewTest.find(view,"edit",javax.swing.JButton.class);
                detail.update(body("Changed"));
                assertThat(KitViewTest.find(view,"edit",javax.swing.JButton.class)).isSameAs(button);
                view.update(tree(false));
                assertThat(detail.isAttached()).isFalse();assertThat(detached[0]).isEqualTo(1);
                detail.close();assertThat(detached[0]).isEqualTo(1);
                assertThatThrownBy(()->detail.update(body("Late"))).isInstanceOf(IllegalStateException.class);
            } finally {KitViewTest.release(context);}
        });
    }
}
