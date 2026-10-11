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
