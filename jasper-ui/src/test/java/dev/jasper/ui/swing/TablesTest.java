package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class TablesTest {
    @Test void usableMinimumMeasuresAdornmentsAndTracksScaleWithoutChangingFontOverride() {Edt.run(()->{
        var context=KitFixtures.context();Object old=javax.swing.UIManager.get("defaultFont");
        float oldScale=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
        try {
            javax.swing.UIManager.put("defaultFont",new java.awt.Font("Serif",java.awt.Font.PLAIN,32));
            var scaleMethod=com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);scaleMethod.setAccessible(true);
            try {
                for(boolean icon:new boolean[]{false,true})for(var selection:SelectionMode.values()) {
                    var columns=List.of(new Column("name","Name",TextRole.BODY,false,3,Optional.empty()),new Column("size","Size",TextRole.MONO_SMALL,true,1,Optional.empty()));
                    var rows=List.of(new TableRow("a",Map.of("name","MMMMMMMMMMMM","size","123 GB"),icon?Optional.of("file"):Optional.empty()));
                    try(var view=new KitView(context,new ViewSpec(new Table("files",ControlState.READY,columns,rows,Set.of(),"open",List.of(),selection,ScrollPolicy.STANDARD)),e->{})) {
                        var owner=(Tables.TableControl)view.control("files");
                        int prior=0;
                        for(float scale:new float[]{1f,2.5f}) {
                            scaleMethod.invoke(null,scale,false);view.update(new ViewSpec(new Table("files",ControlState.READY,columns,rows,Set.of(),"open",List.of(),selection,ScrollPolicy.STANDARD)));
                            int width=owner.component.getMinimumSize().width;view.setSize(width,300);InteractiveRowsTest.layout(view);
                            var cell=(javax.swing.JLabel)owner.mounted.get("a").title.getComponent(0);
                            int textWidth=cell.getWidth()-(cell.getIcon()==null?0:cell.getIcon().getIconWidth()+cell.getIconTextGap());
                            assertThat(textWidth).isGreaterThanOrEqualTo(NativeChromePainting.measureText(cell.getFont(),"MMMM"));
                            assertThat(cell.getFont().getSize2D()).isEqualTo(32f);
                            if(prior>0)assertThat(width).isGreaterThan(prior);prior=width;
                        }
                    }
                }
            }finally{scaleMethod.invoke(null,oldScale,false);}
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        finally{javax.swing.UIManager.put("defaultFont",old);release(context);}
    });}

    @Test void usableMinimumPreservesLongNumericCellsAcrossTheFeasibilityBoundary() {Edt.run(()->{
        var context=KitFixtures.context();Object old=javax.swing.UIManager.get("defaultFont");
        float oldScale=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
        try {
            javax.swing.UIManager.put("defaultFont",new java.awt.Font("Serif",java.awt.Font.PLAIN,13));
            var scaleMethod=com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);scaleMethod.setAccessible(true);
            try {
                for(boolean icon:new boolean[]{false,true})for(var selection:SelectionMode.values())for(float scale:new float[]{1f,2.5f}) {
                    scaleMethod.invoke(null,scale,false);
                    var columns=List.of(new Column("name","Name",TextRole.BODY,false,1,Optional.empty()),new Column("size","Size",TextRole.BODY,true,1,Optional.empty()));
                    var rows=List.of(new TableRow("a",Map.of("name","MMMMMMMMMMMM","size","123456789012345678901234567890 GB"),icon?Optional.of("file"):Optional.empty()));
                    try(var view=new KitView(context,new ViewSpec(new Table("files",ControlState.READY,columns,rows,Set.of(),"open",List.of(),selection,ScrollPolicy.STANDARD)),e->{})) {
                        var owner=(Tables.TableControl)view.control("files");
                        int minimum=owner.component.getMinimumSize().width;
                        view.setSize(minimum,300);InteractiveRowsTest.layout(view);
                        var retained=owner.mounted.get("a");
                        var name=(javax.swing.JLabel)retained.title.getComponent(0);
                        var number=(javax.swing.JLabel)retained.title.getComponent(1);
                        var ellipsis=Labels.literal();ellipsis.setFont(name.getFont());ellipsis.setText("…");
                        int padding=minimum-retained.title.getWidth();
                        int boundary=padding+ellipsis.getPreferredSize().width+number.getPreferredSize().width;
                        // Include the old infeasible→feasible boundary if the advertised minimum falls below it.
                        for(int width:new int[]{minimum,minimum+1,Math.max(minimum,boundary-1),Math.max(minimum,boundary),Math.max(minimum,boundary+1),minimum+boundary,minimum+500}) {
                            view.setSize(width,300);InteractiveRowsTest.layout(view);
                            int adornment=name.getIcon()==null?0:name.getIcon().getIconWidth()+name.getIconTextGap();
                            assertThat(name.getWidth()-adornment).as("useful name at width %s, minimum %s, boundary %s",width,minimum,boundary).isGreaterThanOrEqualTo(NativeChromePainting.measureText(name.getFont(),"MMMM"));
                            assertThat(number.getWidth()).as("numeric minimum at width %s, minimum %s, boundary %s",width,minimum,boundary).isGreaterThanOrEqualTo(number.getPreferredSize().width);
                            assertThat(owner.mounted.get("a")).isSameAs(retained);
                        }
                        assertThat(minimum).isGreaterThanOrEqualTo(boundary);
                    }
                }
            }finally{scaleMethod.invoke(null,oldScale,false);}
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        finally{javax.swing.UIManager.put("defaultFont",old);release(context);}
    });}

    @Test void numericContentMinimumRedistributesOnlyTheDeficitAndStaysBounded(){Edt.run(()->{
        var context=KitFixtures.context();
        var columns=List.of(new Column("name","Name",TextRole.BODY,false,2.4,Optional.empty()),new Column("size","Size",TextRole.DESCRIPTION,true,1,Optional.empty()));
        var rows=List.of(new TableRow("a",Map.of("name","file.bin","size","123456789 GB"),Optional.of("file")));
        try(var view=new KitView(context,new ViewSpec(new Table("files",ControlState.READY,columns,rows,Set.of(),"open",List.of())),e->{})){
            view.setSize(220,120);InteractiveRowsTest.layout(view);var owner=(Tables.TableControl)view.control("files");var row=owner.mounted.get("a");var number=row.title.getComponent(1);
            assertThat(number.getWidth()).isGreaterThanOrEqualTo(number.getPreferredSize().width);
            assertThat(owner.header.getComponent(1).getWidth()).isEqualTo(number.getWidth());
            view.setSize(600,120);InteractiveRowsTest.layout(view);assertThat(number.getWidth()).isBetween(160,185);
            view.setSize(36,120);InteractiveRowsTest.layout(view);assertThat(number.getWidth()).isPositive();assertThat(number.getX()+number.getWidth()).isLessThanOrEqualTo(row.title.getWidth());
            var raw=new HashMap<Object,Object>();javax.swing.UIManager.getDefaults().forEach(raw::put);
            try{javax.swing.UIManager.put("defaultFont",new java.awt.Font("Dialog",java.awt.Font.PLAIN,20));view.setSize(400,180);InteractiveRowsTest.layout(view);assertThat(number.getWidth()).isGreaterThanOrEqualTo(number.getPreferredSize().width);assertThat(owner.header.getComponent(1).getWidth()).isEqualTo(number.getWidth());}
            finally{javax.swing.UIManager.put("defaultFont",raw.get("defaultFont"));}
            assertThat(owner.mounted.get("a")).isSameAs(row);
        }finally{release(context);}
    });}
    @Test
    void largeTableUsesBoundedMountedRows() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var rows =
                            java.util.stream.IntStream.range(0, 10000)
                                    .mapToObj(
                                            i ->
                                                    new TableRow(
                                                            "r" + i,
                                                            Map.of(
                                                                    "name",
                                                                    "File " + i,
                                                                    "size",
                                                                    "1234"),
                                                            Optional.empty()))
                                    .toList();
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Table(
                                                    "files",
                                                    ControlState.READY,
                                                    List.of(
                                                            new Column(
                                                                    "name",
                                                                    "Name",
                                                                    TextRole.BODY,
                                                                    false),
                                                            new Column(
                                                                    "size",
                                                                    "Size",
                                                                    TextRole.MONO_SMALL,
                                                                    true)),
                                                    rows,
                                                    Set.of(),
                                                    "open",
                                                    List.of())),
                                    e -> {})) {
                        view.setSize(400, 260);
                        InteractiveRowsTest.layout(view);
                        assertThat(InteractiveRowsTest.count(view)).isLessThan(180);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void numericColumnsAlignWithHeadersAndCheckboxesSelectWithoutActivation() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var columns =
                            List.of(
                                    new Column("name", "Name", TextRole.BODY, false),
                                    new Column("size", "Size", TextRole.MONO_SMALL, true));
                    var rows =
                            List.of(
                                    new TableRow(
                                            "a",
                                            Map.of("name", "alpha", "size", "100"),
                                            Optional.of("file")),
                                    new TableRow(
                                            "b",
                                            Map.of("name", "beta", "size", "9"),
                                            Optional.empty()));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Table(
                                                    "files",
                                                    ControlState.READY,
                                                    columns,
                                                    rows,
                                                    Set.of(),
                                                    "open",
                                                    List.of(),
                                                    SelectionMode.MULTIPLE)),
                                    events::add)) {
                        view.setSize(400, 260);
                        InteractiveRowsTest.layout(view);
                        var owner = (Tables.TableControl) view.control("files");
                        var row = owner.mounted.get("a");
                        var number = (javax.swing.JLabel) row.title.getComponent(1);
                        var name = (javax.swing.JLabel) row.title.getComponent(0);
                        assertThat(number.getHorizontalAlignment())
                                .isEqualTo(javax.swing.SwingConstants.RIGHT);
                        assertThat(name.getIcon()).isNotNull();
                        row.check.doClick(0);
                        owner.mounted.get("b").check.doClick(0);
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Selected("files", Set.of("a")),
                                        new UiEvent.Selected("files", Set.of("a", "b")));
                        var header = owner.header.getComponent(1);
                        assertThat(header.getX() + header.getWidth())
                                .isEqualTo(row.title.getX() + number.getX() + number.getWidth());
                        view.update(
                                new ViewSpec(
                                        new Table(
                                                "files",
                                                ControlState.READY,
                                                columns,
                                                List.of(rows.get(1), rows.get(0)),
                                                Set.of(),
                                                "open",
                                                List.of(),
                                                SelectionMode.MULTIPLE)));
                        assertThat(owner.selected).containsExactlyInAnyOrder("a", "b");
                        assertThat(owner.mounted.get("a")).isSameAs(row);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void switchingToSingleSelectionReassertsDeclaredSelection() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var columns = List.of(new Column("name", "Name", TextRole.BODY, false));
                    var rows =
                            List.of(
                                    new TableRow("a", Map.of("name", "A"), Optional.empty()),
                                    new TableRow("b", Map.of("name", "B"), Optional.empty()));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Table(
                                                    "table",
                                                    ControlState.READY,
                                                    columns,
                                                    rows,
                                                    Set.of(),
                                                    "open",
                                                    List.of(),
                                                    SelectionMode.MULTIPLE)),
                                    e -> {})) {
                        var owner = (Tables.TableControl) view.control("table");
                        owner.select("a", true);
                        owner.select("b", true);
                        assertThat(owner.selected).hasSize(2);
                        view.update(
                                new ViewSpec(
                                        new Table(
                                                "table",
                                                ControlState.READY,
                                                columns,
                                                rows,
                                                Set.of(),
                                                "open",
                                                List.of(),
                                                SelectionMode.SINGLE)));
                        assertThat(owner.selected).isEmpty();
                        assertThat(owner.mounted.get("a").check.isVisible()).isFalse();
                        owner.select("b", false);
                        assertThat(owner.selected).containsExactly("b");
                    } finally {
                        release(context);
                    }
                });
    }
}
