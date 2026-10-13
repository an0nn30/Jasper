package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;
import java.util.Optional;

import javax.swing.*;

class FormsTest {
    static Nodes.Button button(String id, ButtonKind kind) {
        return new Nodes.Button(id, ControlState.READY, id, "", kind, id, "");
    }

    static Form form(FormKind kind, List<Node> sections) {
        return new Form(
                "form",
                ControlState.READY,
                kind,
                "Add Host",
                Optional.empty(),
                sections,
                Optional.empty(),
                List.of(
                        button("cancel", ButtonKind.SECONDARY),
                        button("save", ButtonKind.PRIMARY)));
    }

    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return s(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role)); }

    static int s(int value) {
        return com.formdev.flatlaf.util.UIScale.scale(value);
    }

    static void layout(Container container) {
        container.doLayout();
        for (Component c : container.getComponents())
            if (c instanceof Container child) layout(child);
    }


    @Test void windowBodyFillsTheNativeContentWithoutInsetsAndPinsItsFooter() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var content = new Split("columns", ControlState.READY, SplitKind.THREE_COLUMN, List.of(
                field("left", FieldKind.TEXT, "draft"), field("middle", FieldKind.TEXT, ""), field("right", FieldKind.TEXT, "")));
            var spec = form(FormKind.valueOf("WINDOW"), List.of(content));
            try (var view = new KitView(context, new ViewSpec(spec), e -> {})) {
                view.setSize(s(900), s(555)); layout(view);
                var columns = find(view, "columns", JPanel.class);
                assertThat(SwingUtilities.convertRectangle(columns.getParent(), columns.getBounds(), view)).isEqualTo(new Rectangle(0, 0, s(900), s(555) - (2*m(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_FOOTER_VERTICAL)+m(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT))));
                assertThat(find(view, "left", JPanel.class).getWidth()).isEqualTo(s(192));
                assertThat(find(view, "middle", JPanel.class).getWidth()).isEqualTo(s(272));
                var input = find(view, "left.input", JTextField.class); var document = input.getDocument();
                view.setSize(s(640), s(400)); layout(view); view.update(new ViewSpec(spec)); layout(view);
                assertThat(input.getDocument()).isSameAs(document); assertThat(input.getText()).isEqualTo("draft");
                var owner = (Forms.FormControl)view.control("form");
                assertThat(owner.title.isVisible()).isFalse();
                assertThat(owner.footerScroll.getY() + owner.footerScroll.getHeight()).isEqualTo(s(400));
            } finally { release(context); }
        });
    }

    @Test void windowFooterElidesItsLongStatusAndKeepsTheSharedActionHeight() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            var status=new Text("path",ControlState.READY,"/synthetic/".repeat(30),TextRole.MONO_CAPTION);
            var spec=new Form("form",ControlState.READY,FormKind.WINDOW,"Window",Optional.empty(),
                List.of(field("input",FieldKind.TEXT,"draft")),Optional.of(status),List.of(button("done",ButtonKind.PRIMARY)));
            try(var view=new KitView(context,new ViewSpec(spec),e->{})) {
                view.setSize(s(900),s(555));layout(view);
                var owner=(Forms.FormControl)view.control("form");
                assertThat(owner.footerScroll.getHeight()).isEqualTo((2*m(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_FOOTER_VERTICAL)+m(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT)));
                assertThat(find(view,"path",JLabel.class).getToolTipText()).isEqualTo(status.text());
                assertThat(find(view,"path",JLabel.class).getAccessibleContext().getAccessibleName()).isEqualTo(status.text());
            }finally{release(context);}
        });
    }

    @Test void threeColumnPanelsUseGutteredRailAndListRowsAndAFullBleedDivider() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            java.util.function.BiFunction<String,ListKind,Nodes.Panel> panel=(id,kind)->new Nodes.Panel(id,ControlState.READY,
                new Header(id+".header",new ControlState(false,true,false,""),"Column",java.util.OptionalInt.empty(),Optional.empty(),Optional.empty(),List.of()),
                Optional.empty(),new ItemList(id+".rows",ControlState.READY,kind,List.of(new ListItem("one",Optional.empty(),
                    List.of(new Text(id+".label",ControlState.READY,"Row",TextRole.BODY)),List.of(),List.of())),java.util.Set.of("one"),"",List.of()),Optional.empty());
            var spec=form(FormKind.WINDOW,List.of(new Split("columns",ControlState.READY,SplitKind.THREE_COLUMN,
                List.of(panel.apply("rail",ListKind.SINGLE),panel.apply("list",ListKind.DOUBLE),field("detail",FieldKind.TEXT,"")))));
            try(var view=new KitView(context,new ViewSpec(spec),e->{})) {
                view.setSize(s(900),s(555));layout(view);
                var rail=(Panels.PanelControl)view.control("rail");var list=(Panels.PanelControl)view.control("list");
                // A list body runs edge to edge: the column's side inset is its rows' gutter.
                assertThat(rail.content.getInsets().left).isZero();
                assertThat(((InteractiveRows.RowsControl)view.control("rail.rows")).gutter).isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_LIST_SIDE));
                // Source 2x: rail fill top 10.5 logical, 31 pitch; list rows 45 below a 30-logical search.
                assertThat(rail.content.getInsets().top).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_RAIL_TOP));
                assertThat(list.content.getInsets().left).isZero();
                assertThat(((InteractiveRows.RowsControl)view.control("list.rows")).gutter).isEqualTo(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_LIST_SIDE));
                // A row is its role's height, or its lines plus the one-line row padding when that is taller.
                int line=((InteractiveRows.RowsControl)view.control("rail.rows")).canvas.getFontMetrics(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.BODY)).getHeight();
                assertThat(((InteractiveRows.RowsControl)view.control("rail.rows")).rowHeight).isEqualTo(Math.max(m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_RAIL_ROW), line + m(dev.jasper.ui.metrics.DesignMetrics.Role.ROW_VERTICAL_PADDING)));
                assertThat(((InteractiveRows.RowsControl)view.control("list.rows")).rowHeight).isEqualTo(Math.max(m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_ITEM_ROW), 2 * line + m(dev.jasper.ui.metrics.DesignMetrics.Role.ROW_VERTICAL_PADDING)));
            }finally{release(context);}
        });
    }

    @Test void plainPathOmitsOnlyTheAdornmentAndRetainsItsChooserAndMonospace() {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new java.util.ArrayList<UiEvent>();
            var path=new Field("path",ControlState.READY,FieldKind.PATH,"/synthetic/key","Path","Private key","browse",Optional.empty(),FieldPresentation.valueOf("PLAIN_PATH"));
            try(var view=new KitView(context,new ViewSpec(path),events::add)) {
                var owner=(TextFields.FieldControl)view.control("path");
                assertThat(owner.leading.isVisible()).isFalse();
                assertThat(owner.input.getText()).isEqualTo(path.value());
                assertThat(owner.input.getFont()).isEqualTo(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.MONO));
                assertThat(owner.input.getActionMap().get("field.submit")).isNull();
                ((JButton)owner.browse.component).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("browse"));
                assertThatThrownBy(()->new Field("bad",ControlState.READY,FieldKind.TEXT,"","","Bad","",Optional.empty(),path.presentation())).isInstanceOf(IllegalArgumentException.class);
            }finally{release(context);}
        });
    }

    @Test void formLabelsWrapWithoutElisionAndDetailMetadataUsesItsOwnGap() {
        Edt.run(()->{
            var old=UIManager.get("defaultFont");
            try {for(int points:new int[]{13,20}) {
                UIManager.put("defaultFont",new Font("Dialog",Font.PLAIN,points));var context=KitFixtures.context();
                var row=new FormRow("row",ControlState.READY,"Confirm new master password","",field("input",FieldKind.TEXT,"draft"),Optional.empty());
                var card=new Card("card",ControlState.READY,CardKind.FORM,List.of(row));
                var metadata=new Text("metadata",ControlState.READY,"Modified Oct 2, 2026",TextRole.CAPTION);
                try(var view=new KitView(context,new ViewSpec(new Stack("detail",ControlState.READY,LayoutRole.DETAIL,List.of(card,metadata))),e->{})) {
                    KitFixtures.render(view,482,400,1);var label=find(view,"row.label",Labels.LiteralLabel.class);
                    assertThat(label.wraps()).isTrue();assertThat(label.getHeight()).isGreaterThan(label.getFontMetrics(label.getFont()).getHeight());
                    assertThat(label.getHeight()).isEqualTo(label.wrappedHeight(label.getWidth()));
                    var c=find(view,"card",JComponent.class);var m=find(view,"metadata",JComponent.class);
                    assertThat(m.getY()-c.getY()-c.getHeight()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.DETAIL_METADATA_GAP));
                    assertThat(find(view,"input.input",javax.swing.text.JTextComponent.class).getText()).isEqualTo("draft");
                }finally{release(context);}
            }} finally {UIManager.put("defaultFont",old);}
        });
    }

    @Test void detailSettingRowsPlaceASecretBelowItsCaptionAcrossTheCardWidth() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var row=new SettingRow("row",ControlState.READY,"Password","",new Secret("secret",ControlState.READY,"","Password","",SecretKind.DETAIL,"copy"),false);
            try(var view=new KitView(context,stack(new Stack("detail",ControlState.READY,LayoutRole.DETAIL,List.of(new Card("card",ControlState.READY,CardKind.SETTINGS,List.of(row))))),e->{})) {
                KitFixtures.render(view,436,300,1);
                var owner=(SettingRows.RowControl)view.control("row");var secret=find(view,"secret",JComponent.class);
                assertThat(owner.component.getHeight()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.DETAIL_ROW_HEIGHT));
                assertThat(secret.getX()).isEqualTo(owner.title.getX());
                // Centred in the detail row, with the 2-logical caption/value gap (11 at the compact row height).
                assertThat(owner.title.getY()).isEqualTo(s(11));
                var reveal=find(view,"secret.reveal",JButton.class);
                var value=find(view,"secret.value",JComponent.class);
                var revealBounds=SwingUtilities.convertRectangle(reveal.getParent(),reveal.getBounds(),owner.component);
                var valueBounds=SwingUtilities.convertRectangle(value.getParent(),value.getBounds(),owner.component);
                assertThat(revealBounds.y+revealBounds.height/2).isEqualTo(owner.component.getHeight()/2);
                assertThat(valueBounds.y).isEqualTo(owner.title.getY()+owner.title.getHeight()+s(2));
                assertThat(secret.getWidth()).isGreaterThan(s(300));
                assertThat(owner.title.getFont()).isEqualTo(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.SETTING_DESCRIPTION));
            }finally{release(context);}
        });
    }

    @Test void shortWindowKeepsBothBodyAndFooterScrollableAndKeyboardReachable() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,new ViewSpec(form(FormKind.WINDOW,List.of(field("input",FieldKind.TEXT,"draft")))),e->{})) {
                view.setSize(s(320),s(45));layout(view);
                var owner=(Forms.FormControl)view.control("form");
                assertThat(owner.scroll.getHeight()).isGreaterThan(0);
                assertThat(owner.footerScroll.getHeight()).isGreaterThan(0);
                var input=find(view,"input.input",JTextField.class);
                assertThat(owner.component.getFocusTraversalPolicy().getDefaultComponent(owner.component)).isSameAs(input);
                assertThat(owner.body.getHeight()).isGreaterThan(owner.scroll.getHeight());
                owner.revealFocusedBody(input);
                assertThat(owner.scroll.getViewport().getViewRect().intersects(SwingUtilities.convertRectangle(input.getParent(),input.getBounds(),owner.body))).as("viewport %s; input %s; body %s",owner.scroll.getViewport().getViewRect(),SwingUtilities.convertRectangle(input.getParent(),input.getBounds(),owner.body),owner.body.getBounds()).isTrue();
                var done=find(view,"save",JButton.class);
                assertThat(owner.footerScroll.getViewport().getExtentSize().height).isGreaterThanOrEqualTo(done.getHeight());
                for(var listener:done.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(done,java.awt.event.FocusEvent.FOCUS_GAINED));
                assertThat(owner.footerScroll.getViewport().getViewPosition().y).isGreaterThan(0);
                assertThat(owner.footerScroll.getViewport().getViewRect().contains(SwingUtilities.convertRectangle(done.getParent(),done.getBounds(),owner.footer))).isTrue();
                view.setSize(s(320),s(20));layout(view);
                assertThat(owner.scroll.getHeight()).isPositive();
                assertThat(owner.footerScroll.getHeight()).isLessThan(done.getHeight());
            }finally{release(context);}
        });
    }

    @Test void constrainedFormRowStacksRetainedControlsAndIgnoresInvisibleTrailing() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var row=new FormRow("row",ControlState.READY,"Literal long label","Caption wraps below label",
                field("input",FieldKind.TEXT,"draft"),Optional.of(field("port",FieldKind.PORT,"22")));
            try(var view=new KitView(context,new ViewSpec(row),e->{})) {
                view.setSize(s(150),s(300));for(int i=0;i<5;i++)layout(view);
                var owner=(FormRows.RowControl)view.control("row");var input=find(view,"input",JComponent.class);
                var trailing=find(view,"port",JComponent.class);
                assertThat(input.getWidth()).isEqualTo(s(150));
                assertThat(input.getY()).isGreaterThanOrEqualTo(owner.caption.getY()+owner.caption.getHeight());
                assertThat(trailing.getY()).isGreaterThanOrEqualTo(input.getY()+input.getHeight());
                var editor=find(view,"input.input",JTextField.class);var document=editor.getDocument();
                assertThat(owner.label.getLabelFor()).isSameAs(editor);
                view.setSize(s(650),s(300));for(int i=0;i<5;i++)layout(view);
                assertThat(input.getX()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_LABEL_WIDTH)+m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                assertThat(input.getY()).as("centred on its taller label block").isEqualTo(Math.max(0,owner.caption.getY()+owner.caption.getHeight()-owner.label.getY()-input.getHeight())/2);
                assertThat(editor.getDocument()).isSameAs(document);
                view.update(new ViewSpec(new FormRow("row",ControlState.READY,row.label(),row.caption(),row.control(),
                    Optional.of(new Field("port",new ControlState(false,true,false,""),FieldKind.PORT,"22","","Port","")))));
                view.setSize(s(150),s(300));for(int i=0;i<5;i++)layout(view);
                assertThat(trailing.isVisible()).isFalse();assertThat(input.getWidth()).isEqualTo(s(150));
                int measured=((Composition.WidthLayout)owner.component.getLayout()).height(owner.component,s(150));
                view.setSize(s(650),s(300));layout(view);
                assertThat(((Composition.WidthLayout)owner.component.getLayout()).height(owner.component,s(150))).isEqualTo(measured);
            }finally{release(context);}
        });
    }

    @Test void everyShortFormKeepsBodyControlsAndFooterReachable() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try {
                for(var kind:FormKind.values())try(var view=new KitView(context,new ViewSpec(form(kind,List.of(field("input",FieldKind.TEXT,"draft")))),e->{})) {
                    view.setSize(s(320),s(45));for(int i=0;i<5;i++)layout(view);
                    var owner=(Forms.FormControl)view.control("form");
                    assertThat(owner.scroll.getViewport().getHeight()).as("%s body",kind).isPositive();
                    var input=find(view,"input.input",JTextField.class);
                    owner.revealFocusedBody(input);
                    assertThat(input.getVisibleRect().height).as("%s input scroll reveal",kind).isPositive();
                    var action=find(view,"save",JButton.class);
                    for(var listener:action.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(action,java.awt.event.FocusEvent.FOCUS_GAINED));
                    assertThat(action.getVisibleRect().height).as("%s action scroll reveal",kind).isPositive();
                }
            } finally {release(context);}
        });
    }

    @Test void sheetEnvelopeAddsItsBorderOutsideTheUnchanged480Fill() {
        Edt.run(()->{
            var context=KitFixtures.context();
            try(var view=new KitView(context,new ViewSpec(form(FormKind.SHEET,List.of(new Card("card",ControlState.READY,CardKind.FORM,List.of(new FormRow("row",ControlState.READY,"Name","",field("input",FieldKind.TEXT,""),Optional.empty())))))),e->{})) {
                assertThat(view.getPreferredSize().width).isEqualTo(s(482));
                view.setSize(view.getPreferredSize());layout(view);
                var card=find(view,"card",JComponent.class);
                assertThat(card.getWidth()).isEqualTo(s(480) - 2*m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_SIDES));
                assertThat(find(view,"input",JComponent.class).getWidth()).isEqualTo(card.getWidth() - 2*(m(dev.jasper.ui.metrics.DesignMetrics.Role.CARD_PADDING) + m(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE)) - m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_LABEL_WIDTH) - m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                var owner=(Forms.FormControl)view.control("form");
                assertThat(owner.scroll.getX()).isEqualTo(s(1));assertThat(owner.scroll.getWidth()).isEqualTo(s(480));
            }finally{release(context);}
        });
    }

    @Test void sheetIdentityUsesItsTitleGap() {
        Edt.run(()->{
            var context=KitFixtures.context();
            var heading=new Stack("heading",ControlState.READY,LayoutRole.INLINE,List.of(new Nodes.Icon("icon",ControlState.READY,"user",Hue.BLUE,IconSize.HERO,"Login"),new Text("title",ControlState.READY,"Add Login",TextRole.HERO_TITLE)));
            var spec=new Form("form",ControlState.READY,FormKind.SHEET,"Add Login",Optional.of(heading),List.of(),Optional.empty(),List.of(button("save",ButtonKind.PRIMARY)));
            try(var view=new KitView(context,new ViewSpec(spec),e->{})) {
                view.setSize(view.getPreferredSize());layout(view);
                var icon=find(view,"icon",JComponent.class);var title=find(view,"title",JComponent.class);
                assertThat(title.getX()-icon.getX()-icon.getWidth()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
            }finally{release(context);}
        });
    }

    @Test void retainedActionMovesBetweenFormsWithoutAccumulatingRevealHooks() {
        Edt.run(() -> {
            var context=KitFixtures.context();var action=button("retained",ButtonKind.PRIMARY);
            java.util.function.Function<String,Form> footer=id->new Form(id,ControlState.READY,FormKind.SHEET,"Entry",Optional.empty(),List.of(),Optional.empty(),List.of(action));
            var view=new KitView(context,new ViewSpec(footer.apply("first")),e->{});
            try {
                var button=find(view,"retained",JButton.class);int withHook=button.getFocusListeners().length;
                Forms.FormControl old=(Forms.FormControl)view.control("first");
                for(int i=0;i<4;i++) {
                    String id="next"+i;view.update(new ViewSpec(footer.apply(id)));
                    assertThat(find(view,"retained",JButton.class)).isSameAs(button);
                    assertThat(old.closed).isTrue();assertThat(old.focusReveal).isEmpty();
                    assertThat(button.getFocusListeners()).hasSize(withHook);
                    old=(Forms.FormControl)view.control(id);view.setSize(s(300),s(40));layout(view);
                    for(var listener:button.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(button,java.awt.event.FocusEvent.FOCUS_GAINED));
                    var scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,button);
                    assertThat(scroll.getViewport().getViewRect().contains(SwingUtilities.convertRectangle(button.getParent(),button.getBounds(),scroll.getViewport().getView()))).isTrue();
                }
                view.update(new ViewSpec(new Form("next3",ControlState.READY,FormKind.SHEET,"Entry",Optional.empty(),List.of(action),Optional.empty(),List.of())));
                assertThat(view.control("next3")).isSameAs(old);assertThat(old.focusReveal).isEmpty();
                assertThat(button.getFocusListeners()).hasSize(withHook-1);
                view.update(new ViewSpec(footer.apply("next3")));assertThat(button.getFocusListeners()).hasSize(withHook);
                var reveal=old.focusReveal;view.close();assertThat(reveal).isEmpty();
            } finally {view.close();release(context);}
        });
    }
    @Test void extremeShortSheetKeepsAllFooterActionsScrollReachableWithoutReparenting() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(form(FormKind.SHEET, List.of(field("input", FieldKind.TEXT, "draft")))), e -> {})) {
                view.setSize(s(300), s(300));layout(view);
                var save = find(view, "save", JButton.class); var parent = save.getParent();
                view.setSize(s(300), s(40));layout(view);
                var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, save);
                assertThat(scroll).as("fixed footer becomes scroll-reachable when even screen height cannot hold it").isNotNull();
                assertThat(scroll.getHeight()).isLessThanOrEqualTo(s(40));
                assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
                for (String id : List.of("save", "cancel")) {
                    var action = find(view, id, JButton.class);
                    for (var listener : action.getFocusListeners()) listener.focusGained(new java.awt.event.FocusEvent(action, java.awt.event.FocusEvent.FOCUS_GAINED));
                    var bounds = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), scroll.getViewport().getView());
                    assertThat(scroll.getViewport().getViewRect().contains(bounds)).as("keyboard focus reveals " + id).isTrue();
                }
                view.setSize(s(300), s(300));layout(view);
                assertThat(save.getParent()).isSameAs(parent);assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();
                assertThat(find(view,"input.input",JTextField.class).getText()).isEqualTo("draft");
            } finally { release(context); }
        });
    }

    @Test void visualPrimaryMayOptOutOfEnterWithoutLosingClickOrEscape() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new java.util.ArrayList<UiEvent>();
            // This contract is additive: legacy constructors must still submit the primary.
            assertThat(java.util.Arrays.stream(Form.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .contains("submission");
            try (var view = new KitView(context, new ViewSpec(form(FormKind.FULL, List.of(field("input", FieldKind.TEXT, "")))), events::add)) {
                var original = (Form) view.spec().root();
                try {
                    Class<?> policy = Class.forName("dev.jasper.ui.model.Nodes$FormSubmission");
                    Object none = java.util.Arrays.stream(policy.getEnumConstants()).filter(v -> v.toString().equals("NONE")).findFirst().orElseThrow();
                    var constructor = Form.class.getConstructor(String.class, ControlState.class, FormKind.class, String.class,
                        Optional.class, List.class, Optional.class, List.class, String.class, policy);
                    view.update(new ViewSpec((Form) constructor.newInstance(original.id(), original.state(), original.kind(), original.title(), original.hero(), original.sections(), original.preview(), original.actions(), "cancel", none)));
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                var root = find(view, "form", JComponent.class);
                Object enter = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ENTER"));
                root.getActionMap().get(enter).actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                assertThat(events).isEmpty();
                find(view, "save", JButton.class).doClick(0);
                Object escape = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ESCAPE"));
                root.getActionMap().get(escape).actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                assertThat(events).containsExactly(new UiEvent.Action("save"), new UiEvent.Action("cancel"));
            } finally { release(context); }
        });
    }

    @Test void technicalCaptionKeepsMonoMetricsButUsesTheMutedSemanticColor() {
        Edt.run(()->{
            var context=KitFixtures.context();
            assertThat(java.util.Arrays.stream(TextRole.values()).map(Enum::name)).contains("MONO_CAPTION");
            try(var view=new KitView(context,new ViewSpec(new Text("preview",ControlState.READY,"ssh user@host",TextRole.valueOf("MONO_CAPTION"))),e->{})) {
                var label=find(view,"preview",JLabel.class);
                assertThat(label.getFont().getFamily()).isEqualTo(Font.MONOSPACED);
                assertThat(label.getForeground()).isEqualTo(ThemePainting.color(context,dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
            }finally{release(context);}
        });
    }

    @Test void technicalFieldUsesTheSameMonospaceRoleAsPortAndKeepsOrdinaryGeometry() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            assertThat(java.util.Arrays.stream(FieldPresentation.values()).map(Enum::name)).contains("TECHNICAL");
            var technical=new Field("technical",ControlState.READY,FieldKind.TEXT,"host.invalid","","Host","",Optional.empty(),FieldPresentation.valueOf("TECHNICAL"));
            try(var view=new KitView(context,new ViewSpec(new Stack("root",ControlState.READY,LayoutRole.VERTICAL,List.of(technical,field("port",FieldKind.PORT,"22")))),e->{})) {
                assertThat(find(view,"technical.input",JTextField.class).getFont()).isEqualTo(find(view,"port.input",JTextField.class).getFont());
                assertThat(find(view,"technical.label",JLabel.class).isVisible()).isTrue();
            }finally{release(context);}
        });
    }

    @Test void windowFormTraversalCanReachNativeTitleActionsWhileSheetRemainsContained() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var shell = new JPanel(new BorderLayout());
            shell.setFocusCycleRoot(true); shell.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
            var titleAction = new JButton("Add entry"); shell.add(titleAction, BorderLayout.NORTH);
            try (var view = new KitView(context, new ViewSpec(form(FormKind.WINDOW, List.of(field("input", FieldKind.TEXT, "draft")))), e -> {})) {
                shell.add(view, BorderLayout.CENTER); shell.setSize(700, 500); shell.addNotify(); shell.validate(); layout(shell);
                var input = find(view, "input.input", JTextField.class); var document = input.getDocument();
                assertThat(input.getFocusCycleRootAncestor()).as("native shell owns the window focus cycle").isSameAs(shell);
                assertThat(input.getFocusCycleRootAncestor()).isSameAs(titleAction.getFocusCycleRootAncestor());
                var formRoot = (Container) view.getComponent(0);
                assertThat(formRoot.getFocusTraversalPolicy().getDefaultComponent(formRoot)).isSameAs(input);
                view.update(new ViewSpec(form(FormKind.SHEET, List.of(field("input", FieldKind.TEXT, "draft")))));
                assertThat(input.getFocusCycleRootAncestor()).isSameAs(view.getComponent(0));
                view.update(new ViewSpec(form(FormKind.WINDOW, List.of(field("input", FieldKind.TEXT, "draft")))));
                layout(shell);
                assertThat(input.getFocusCycleRootAncestor()).isSameAs(shell);
                assertThat(input.getFocusCycleRootAncestor()).isSameAs(titleAction.getFocusCycleRootAncestor());
                assertThat(find(view, "input.input", JTextField.class)).isSameAs(input);
                assertThat(input.getDocument()).isSameAs(document);
            } finally { shell.removeNotify(); release(context); }
        });
    }

    @Test void nativeRemovalMayTraverseEveryClosingFormFocusPolicyWithoutDisposedLookup() {
        Edt.run(() -> {
            var context=KitFixtures.context();
            var view=new KitView(context,new ViewSpec(form(FormKind.FULL,List.of(field("input",FieldKind.TEXT,"draft")))),e -> {});
            var surface=(Container)view.getComponent(0);var policy=surface.getFocusTraversalPolicy();
            var input=find(view,"input.input",JComponent.class);
            view.addContainerListener(new java.awt.event.ContainerAdapter(){@Override public void componentRemoved(java.awt.event.ContainerEvent event){
                policy.getDefaultComponent(surface);policy.getFirstComponent(surface);policy.getLastComponent(surface);
                policy.getComponentAfter(surface,input);policy.getComponentBefore(surface,input);
            }});
            try { assertThatCode(view::close).doesNotThrowAnyException(); } finally { view.close();release(context); }
        });
    }

    @Test void nativeRemovalMayMeasureAClosingFormWithoutLookingUpDisposedControls() {
        dev.jasper.ui.testsupport.Edt.run(() -> {
            var context=dev.jasper.ui.testsupport.KitFixtures.context();
            var form=new Nodes.Form("form",ControlState.READY,Nodes.FormKind.FULL,"Entry",java.util.Optional.empty(),java.util.List.of(new Nodes.Text("body",ControlState.READY,"Draft",Nodes.TextRole.BODY)),java.util.Optional.empty(),java.util.List.of(),"");
            var view=new KitView(context,new ViewSpec(form),event->{});var surface=view.getComponent(0);
            view.addContainerListener(new java.awt.event.ContainerAdapter(){@Override public void componentRemoved(java.awt.event.ContainerEvent event){surface.getPreferredSize();}});
            try{assertThatCode(view::close).doesNotThrowAnyException();assertThat(surface.getPreferredSize()).isEqualTo(new java.awt.Dimension());}
            finally{view.close();release(context);}
        });
    }
    @Test
    void formRowAlignsLabelControlHelperAndTrailingPortInsidePaddedCard() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var row =
                            new FormRow(
                                    "row",
                                    ControlState.READY,
                                    "Host",
                                    "",
                                    field("host", FieldKind.TEXT, ""),
                                    Optional.of(field("port", FieldKind.PORT, "22")));
                    var helper =
                            new FormRow(
                                    "helper",
                                    ControlState.READY,
                                    "",
                                    "",
                                    new Text(
                                            "hint",
                                            ControlState.READY,
                                            "Example",
                                            TextRole.CAPTION),
                                    Optional.empty());
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Card(
                                                    "card",
                                                    ControlState.READY,
                                                    CardKind.FORM,
                                                    List.of(row, helper))),
                                    e -> {})) {
                        view.setSize(s(510), s(160));
                        layout(view);
                        var card = find(view, "card", JComponent.class);
                        var host = find(view, "host", JComponent.class);
                        var label = find(view, "row.label", JLabel.class);
                        var port = find(view, "port", JComponent.class);
                        assertThat(label.getX()).isZero();
                        assertThat(label.getWidth()).isEqualTo(s(96));
                        assertThat(host.getX()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_LABEL_WIDTH) + m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                        assertThat(port.getX() - host.getX() - host.getWidth()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                        assertThat(find(view, "row", JComponent.class).getX()).isEqualTo((m(dev.jasper.ui.metrics.DesignMetrics.Role.CARD_PADDING) + m(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE)));
                        assertThat(find(view, "hint", JComponent.class).getX()).isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.FORM_LABEL_WIDTH) + m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                        assertThat(find(view, "host.label", JComponent.class).isVisible())
                                .isFalse();
                        assertThat(card.getInsets().left).isEqualTo((m(dev.jasper.ui.metrics.DesignMetrics.Role.CARD_PADDING) + m(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE)));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void heroButtonsHaveEqualWidthsAndFormsUseSourceTiers() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try {
                        for (var kind : List.of(FormKind.HERO, FormKind.SHEET, FormKind.FULL))
                            try (var view =
                                    new KitView(
                                            ctx,
                                            new ViewSpec(
                                                    form(
                                                            kind,
                                                            List.of(
                                                                    field(
                                                                            "name",
                                                                            FieldKind.TEXT,
                                                                            "")))),
                                            e -> {})) {
                                assertThat(view.getPreferredSize().width)
                                        .isEqualTo(
                                                s(
                                                        switch (kind) {
                                                            case HERO -> 400;
                                                            case SHEET -> 482;
                                                            default -> 540;
                                                        }));
                                view.setSize(view.getPreferredSize());
                                layout(view);
                                if (kind == FormKind.HERO)
                                    assertThat(find(view, "save", JButton.class).getWidth())
                                            .isEqualTo(
                                                    find(view, "cancel", JButton.class).getWidth());
                            }
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void narrowAndEnlargedFontWrapDescriptionsAndKeepControlsInsideBounds() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    Object old = UIManager.get("defaultFont");
                    try {
                        UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18));
                        var toggle =
                                new Toggle(
                                        "toggle",
                                        ControlState.READY,
                                        "Enabled",
                                        true,
                                        "toggle",
                                        false);
                        try (var view =
                                new KitView(
                                        ctx,
                                        new ViewSpec(
                                                new Card(
                                                        "card",
                                                        ControlState.READY,
                                                        CardKind.SETTINGS,
                                                        List.of(
                                                                new SettingRow(
                                                                        "setting",
                                                                        ControlState.READY,
                                                                        "Start automatically",
                                                                        "An intentionally long"
                                                                            + " description must"
                                                                            + " wrap without"
                                                                            + " overlapping the"
                                                                            + " trailing control",
                                                                        toggle,
                                                                        true)))),
                                        e -> {})) {
                            view.setSize(s(250), s(230));
                            layout(view);
                            var row = find(view, "setting", JComponent.class);
                            var description = find(view, "setting.description", JComponent.class);
                            var control = find(view, "toggle", JComponent.class);
                            assertThat(description.getHeight()).isGreaterThan(s(25));
                            int ring = ThemePainting.metric(ctx, dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING);
                            assertThat(description.getX() + description.getWidth())
                                    .isLessThanOrEqualTo(control.getX() + ring - m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                            assertThat(control.getX() + control.getWidth() - ring)
                                    .isEqualTo(row.getWidth() - m(dev.jasper.ui.metrics.DesignMetrics.Role.SETTING_SIDE_PADDING));
                            assertThat(control.getX() + control.getWidth()).isLessThanOrEqualTo(row.getWidth());
                        }
                    } finally {
                        UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void conditionalCompositionRetainsLiveFieldAndSecretDocuments() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            form(
                                                    FormKind.FULL,
                                                    List.of(
                                                            field("name", FieldKind.TEXT, ""),
                                                            new Secret(
                                                                    "secret",
                                                                    ControlState.READY,
                                                                    "Password",
                                                                    "Password",
                                                                    "pair")))),
                                    e -> {})) {
                        var input = editor(view, "name");
                        var secret = editor(view, "secret");
                        input.setText("live");
                        input.select(1, 3);
                        secret.setText("test");
                        var doc = secret.getDocument();
                        view.update(
                                new ViewSpec(
                                        form(
                                                FormKind.FULL,
                                                List.of(
                                                        new Section(
                                                                "section",
                                                                ControlState.READY,
                                                                "Connection",
                                                                new Card(
                                                                        "card",
                                                                        ControlState.READY,
                                                                        CardKind.FORM,
                                                                        List.of(
                                                                                field(
                                                                                        "name",
                                                                                        FieldKind
                                                                                                .TEXT,
                                                                                        ""),
                                                                                new Secret(
                                                                                        "secret",
                                                                                        ControlState
                                                                                                .READY,
                                                                                        "Password",
                                                                                        "Password",
                                                                                        "pair"))))))));
                        assertThat(editor(view, "name")).isSameAs(input);
                        assertThat(input.getText()).isEqualTo("live");
                        assertThat(input.getSelectionEnd()).isEqualTo(3);
                        assertThat(editor(view, "secret").getDocument()).isSameAs(doc);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void sheetPaintHasSquareTopAndOnlyRoundedBottomCorners() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view =
                            new KitView(
                                    ctx, new ViewSpec(form(FormKind.SHEET, List.of())), e -> {})) {
                        var sheet = find(view, "form", JComponent.class);
                        sheet.setSize(s(480), s(200));
                        var image = ButtonsTest.paint(sheet);
                        assertThat((image.getRGB(0, 0) >>> 24) & 255).isEqualTo(255);
                        assertThat((image.getRGB(0, image.getHeight() - 1) >>> 24) & 255).isZero();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void shortHostScrollsBodyAndKeepsFooterReachableAtEnlargedFont() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    Object old = UIManager.get("defaultFont");
                    try {
                        UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18));
                        var rows = new java.util.ArrayList<Node>();
                        for (int i = 0; i < 12; i++)
                            rows.add(
                                    new FormRow(
                                            "row" + i,
                                            ControlState.READY,
                                            "Name",
                                            "",
                                            field("field" + i, FieldKind.TEXT, ""),
                                            Optional.empty()));
                        try (var view =
                                new KitView(
                                        ctx, new ViewSpec(form(FormKind.FULL, rows)), e -> {})) {
                            view.setAvailableHeight(240);
                            assertThat(view.getPreferredSize().height).isEqualTo(s(204));
                            view.setSize(view.getPreferredSize());
                            layout(view);
                            var scroll = find(view, "form.scroll", JScrollPane.class);
                            assertThat(scroll.getViewport().getView().getPreferredSize().height)
                                    .isGreaterThan(scroll.getViewport().getHeight());
                            var save = find(view, "save", JButton.class);
                            Point p = SwingUtilities.convertPoint(save, 0, 0, view);
                            assertThat(p.y + save.getHeight()).isLessThanOrEqualTo(s(204));
                            assertThat(save.getHeight()).isGreaterThanOrEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.FULL_FOOTER_BUTTON_HEIGHT));
                            assertThatThrownBy(() -> view.setAvailableHeight(0))
                                    .isInstanceOf(IllegalArgumentException.class);
                        }
                    } finally {
                        UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }

    @Test
    void primaryValidationAndExplicitCancelKeysDoNotInferLocalizedLabels() {
        assertThatThrownBy(
                        () ->
                                new Form(
                                        "f",
                                        ControlState.READY,
                                        FormKind.FULL,
                                        "Form",
                                        Optional.empty(),
                                        List.of(),
                                        Optional.empty(),
                                        List.of(
                                                button("a", ButtonKind.PRIMARY),
                                                button("b", ButtonKind.PRIMARY)),
                                        "cancel-key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new FormRow(
                                        "r",
                                        ControlState.READY,
                                        null,
                                        "",
                                        field("input", FieldKind.TEXT, ""),
                                        Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var events = new java.util.ArrayList<UiEvent>();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Form(
                                                    "form",
                                                    ControlState.READY,
                                                    FormKind.FULL,
                                                    "Form",
                                                    Optional.empty(),
                                                    List.of(field("input", FieldKind.TEXT, "")),
                                                    Optional.empty(),
                                                    List.of(
                                                            button(
                                                                    "Confirmer",
                                                                    ButtonKind.PRIMARY)),
                                                    "annuler")),
                                    events::add)) {
                        var readySpec = view.spec();
                        var root = find(view, "form", JComponent.class);
                        Object key =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ENTER"));
                        root.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).containsExactly(new UiEvent.Action("Confirmer"));
                        events.clear();
                        key =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ESCAPE"));
                        root.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).containsExactly(new UiEvent.Action("annuler"));
                        events.clear();
                        var invalid =
                                new Field(
                                        "input",
                                        new ControlState(true, true, false, "Required"),
                                        FieldKind.TEXT,
                                        "",
                                        "",
                                        "Name",
                                        "");
                        view.update(
                                new ViewSpec(
                                        new Form(
                                                "form",
                                                ControlState.READY,
                                                FormKind.FULL,
                                                "Form",
                                                Optional.empty(),
                                                List.of(invalid),
                                                Optional.empty(),
                                                List.of(button("Confirmer", ButtonKind.PRIMARY)),
                                                "annuler")));
                        assertThat(find(view, "Confirmer", JButton.class).isEnabled()).isFalse();
                        key =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ENTER"));
                        root.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).isEmpty();
                        view.update(readySpec);
                        assertThat(find(view, "Confirmer", JButton.class).isEnabled()).isTrue();
                        find(view, "Confirmer", JButton.class).doClick(0);
                        assertThat(events).containsExactly(new UiEvent.Action("Confirmer"));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void openMenuConsumesFirstEscapeBeforeFormCancellationAndSuppressesSubmit() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var events = new java.util.ArrayList<UiEvent>();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Form(
                                                    "form",
                                                    ControlState.READY,
                                                    FormKind.FULL,
                                                    "Form",
                                                    Optional.empty(),
                                                    List.of(MenusTest.menu("")),
                                                    Optional.empty(),
                                                    List.of(button("save", ButtonKind.PRIMARY)),
                                                    "cancel-key")),
                                    events::add)) {
                        var root = find(view, "form", JComponent.class);
                        find(view, "menu.main", JButton.class).doClick(0);
                        Object enter =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ENTER"));
                        root.getActionMap()
                                .get(enter)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).isEmpty();
                        Object esc =
                                root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ESCAPE"));
                        root.getActionMap()
                                .get(esc)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).isEmpty();
                        assertThat(
                                        find(view, "menu", JComponent.class)
                                                .getComponentPopupMenu()
                                                .isVisible())
                                .isFalse();
                        root.getActionMap()
                                .get(esc)
                                .actionPerformed(new java.awt.event.ActionEvent(root, 0, ""));
                        assertThat(events).containsExactly(new UiEvent.Action("cancel-key"));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void movingOutOfRowRestoresStandaloneLabelAndNestedHeroFieldsGetHeroSizing() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            form(
                                                    FormKind.HERO,
                                                    List.of(
                                                            new FormRow(
                                                                    "row",
                                                                    ControlState.READY,
                                                                    "Name",
                                                                    "",
                                                                    field(
                                                                            "input",
                                                                            FieldKind.TEXT,
                                                                            ""),
                                                                    Optional.empty())))),
                                    e -> {})) {
                        view.setSize(view.getPreferredSize());
                        layout(view);
                        var input = editor(view, "input");
                        assertThat(input.getParent().getPreferredSize().height)
                                .isGreaterThanOrEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.HERO_FIELD_HEIGHT));
                        view.update(stack(field("input", FieldKind.TEXT, "")));
                        assertThat(find(view, "input.label", JComponent.class).isVisible())
                                .isTrue();
                        assertThat(input.getParent().getPreferredSize().height).isLessThan(s(32));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void heroHeaderCentersItsTileAndTitlesWithSourceGaps() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var hero =
                            new Stack(
                                    "hero",
                                    ControlState.READY,
                                    LayoutRole.HERO,
                                    List.of(
                                            new Nodes.Icon(
                                                    "hero-icon",
                                                    ControlState.READY,
                                                    "lock",
                                                    Hue.BLUE,
                                                    IconSize.HERO,
                                                    "Vault"),
                                            new Text(
                                                    "hero-title",
                                                    ControlState.READY,
                                                    "Unlock Vault",
                                                    TextRole.HERO_TITLE),
                                            new Text(
                                                    "hero-description",
                                                    ControlState.READY,
                                                    "Enter your password to unlock.",
                                                    TextRole.DESCRIPTION)));
                    try (var view = new KitView(ctx, new ViewSpec(hero), e -> {})) {
                        view.setSize(s(340), s(180));
                        layout(view);
                        var tile = find(view, "hero.tile", JComponent.class);
                        var title = find(view, "hero-title", JComponent.class);
                        var description = find(view, "hero-description", JComponent.class);
                        assertThat(tile).isNotNull();
                        assertThat(tile.getSize()).isEqualTo(new Dimension(s(54), s(54)));
                        assertThat(tile.getX() + s(27)).isEqualTo(s(170));
                        assertThat(title.getY() - tile.getY() - s(54)).isEqualTo(s(14));
                        assertThat(description.getY() - title.getY() - title.getHeight())
                                .isEqualTo(m(dev.jasper.ui.metrics.DesignMetrics.Role.DESCRIPTION_GAP));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void textareaKeepsNewlineBindingAndDefaultFocusSkipsHiddenInputs() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var hidden =
                            new Field(
                                    "hidden",
                                    new ControlState(false, true, false, ""),
                                    FieldKind.TEXT,
                                    "",
                                    "",
                                    "Name",
                                    "");
                    var area = field("area", FieldKind.TEXTAREA, "");
                    var events = new java.util.ArrayList<UiEvent>();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(form(FormKind.FULL, List.of(hidden, area))),
                                    events::add)) {
                        var root = find(view, "form", JComponent.class);
                        assertThat(root.getFocusTraversalPolicy().getDefaultComponent(root))
                                .isSameAs(editor(view, "area"));
                        var textarea = editor(view, "area");
                        Object key =
                                textarea.getInputMap(JComponent.WHEN_FOCUSED)
                                        .get(KeyStroke.getKeyStroke("ENTER"));
                        textarea.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(textarea, 0, ""));
                        assertThat(textarea.getText()).isEqualTo("\n");
                        assertThat(events).noneMatch(e -> e instanceof UiEvent.Action);
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void pathBrowseRingAndErrorRemainInsideTheNarrowRowAndActionUsesLatestDescriptor() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var events = new java.util.ArrayList<UiEvent>();
                    var path =
                            new Field(
                                    "path",
                                    new ControlState(true, true, false, "Choose a path"),
                                    FieldKind.PATH,
                                    "",
                                    "Optional",
                                    "Path",
                                    "browse-new");
                    var row =
                            new FormRow(
                                    "row", ControlState.READY, "Path", "", path, Optional.empty());
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Card(
                                                    "card",
                                                    ControlState.READY,
                                                    CardKind.FORM,
                                                    List.of(row))),
                                    events::add)) {
                        view.setSize(s(210), s(150));
                        layout(view);
                        var surface = editor(view, "path").getParent();
                        assertThat(surface.getWidth()).isPositive();
                        var image = ButtonsTest.paint((JComponent) surface);
                        assertThat((image.getRGB(image.getWidth() / 2, 0) >>> 24) & 255)
                                .isGreaterThan(0);
                        assertThat((image.getRGB(0, 0) >>> 24) & 255).isZero();
                        var browse = find(view, "path.browse", JButton.class);
                        browse.doClick(0);
                        assertThat(events).containsExactly(new UiEvent.Action("browse-new"));
                        assertThat(
                                        SwingUtilities.convertPoint(
                                                        browse, browse.getWidth(), 0, view)
                                                .x)
                                .isLessThanOrEqualTo(s(210) - m(dev.jasper.ui.metrics.DesignMetrics.Role.CARD_PADDING));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void mixedCardKeepsSettingsRowsEdgeToEdgeAndPaddedFormRowsAboveDivider() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var formRow =
                            new FormRow(
                                    "row",
                                    ControlState.READY,
                                    "Name",
                                    "",
                                    field("name", FieldKind.TEXT, ""),
                                    Optional.empty());
                    var setting =
                            new SettingRow(
                                    "setting",
                                    ControlState.READY,
                                    "Enabled",
                                    "",
                                    new Toggle(
                                            "toggle",
                                            ControlState.READY,
                                            "Enabled",
                                            true,
                                            "toggle",
                                            false),
                                    true);
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Card(
                                                    "card",
                                                    ControlState.READY,
                                                    CardKind.MIXED,
                                                    List.of(formRow, setting))),
                                    e -> {})) {
                        view.setSize(s(440), s(200));
                        layout(view);
                        var row = find(view, "row", JComponent.class);
                        var settings = find(view, "setting", JComponent.class);
                        assertThat(row.getX()).isEqualTo((m(dev.jasper.ui.metrics.DesignMetrics.Role.CARD_PADDING) + m(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE)));
                        assertThat(settings.getX()).isZero();
                        assertThat(settings.getWidth()).isEqualTo(s(440));
                        find(view, "toggle", JButton.class).doClick(0);
                        assertThat(find(view, "toggle", JButton.class).isSelected()).isTrue();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void splitAndHiddenTrailingCellsFitNarrowAvailableWidth() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var hiddenPort =
                            new Field(
                                    "port",
                                    new ControlState(false, true, false, ""),
                                    FieldKind.PORT,
                                    "22",
                                    "",
                                    "Port",
                                    "");
                    var row =
                            new FormRow(
                                    "row",
                                    ControlState.READY,
                                    "Host",
                                    "",
                                    field("host", FieldKind.TEXT, ""),
                                    Optional.of(hiddenPort));
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Split(
                                                    "split",
                                                    ControlState.READY,
                                                    SplitKind.SIDEBAR_DETAIL,
                                                    List.of(
                                                            new Card(
                                                                    "card",
                                                                    ControlState.READY,
                                                                    CardKind.FORM,
                                                                    List.of(row)),
                                                            new Text(
                                                                    "detail",
                                                                    ControlState.READY,
                                                                    "Detail",
                                                                    TextRole.BODY)))),
                                    e -> {})) {
                        view.setSize(s(500), s(200));
                        layout(view);
                        var host = find(view, "host", JComponent.class);
                        var rowComponent = find(view, "row", JComponent.class);
                        assertThat(host.getX() + host.getWidth())
                                .isEqualTo(rowComponent.getWidth());
                        assertThat(
                                        find(view, "detail", JComponent.class).getX()
                                                + find(view, "detail", JComponent.class).getWidth())
                                .isLessThanOrEqualTo(s(500));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void escapeFromFocusedMenuControlDelegatesToItsFormWhenPopupAlreadyClosed() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var events = new java.util.ArrayList<UiEvent>();
                    try (var view =
                            new KitView(
                                    ctx,
                                    new ViewSpec(
                                            new Form(
                                                    "form",
                                                    ControlState.READY,
                                                    FormKind.FULL,
                                                    "Form",
                                                    Optional.empty(),
                                                    List.of(MenusTest.menu("")),
                                                    Optional.empty(),
                                                    List.of(button("save", ButtonKind.PRIMARY)),
                                                    "cancel-key")),
                                    events::add)) {
                        var menu = find(view, "menu", JComponent.class);
                        var key =
                                menu.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                                        .get(KeyStroke.getKeyStroke("ESCAPE"));
                        find(view, "menu.main", JButton.class).doClick(0);
                        menu.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(menu, 0, ""));
                        assertThat(events).isEmpty();
                        menu.getActionMap()
                                .get(key)
                                .actionPerformed(new java.awt.event.ActionEvent(menu, 0, ""));
                        assertThat(events).containsExactly(new UiEvent.Action("cancel-key"));
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void sheetTitleAppearsInContentWhenNativeTitleBarIsOwnedOutsideTheKit() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    try (var view =
                            new KitView(
                                    ctx, new ViewSpec(form(FormKind.SHEET, List.of())), e -> {})) {
                        var title = find(view, "form.title", JLabel.class);
                        assertThat(title).isNotNull();
                        assertThat(title.getText()).isEqualTo("Add Host");
                        assertThat(title.getClientProperty("html")).isNull();
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void heroPreviewReservesItsOwnHeightAboveFullWidthActions() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    var spec =
                            new ViewSpec(
                                    new Form(
                                            "form",
                                            ControlState.READY,
                                            FormKind.HERO,
                                            "Preview",
                                            Optional.empty(),
                                            List.of(field("name", FieldKind.TEXT, "")),
                                            Optional.of(
                                                    new Text(
                                                            "preview",
                                                            ControlState.READY,
                                                            "Ready to unlock",
                                                            TextRole.MONO_SMALL)),
                                            List.of(button("save", ButtonKind.PRIMARY))));
                    try (var view = new KitView(ctx, spec, e -> {})) {
                        view.setSize(view.getPreferredSize());
                        layout(view);
                        var preview = find(view, "preview", JLabel.class);
                        var action = find(view, "save", JButton.class);
                        assertThat(preview.getHeight())
                                .isGreaterThanOrEqualTo(preview.getPreferredSize().height);
                        assertThat(preview.getParent().getHeight())
                                .isGreaterThanOrEqualTo(preview.getPreferredSize().height);
                        var previewBottom =
                                SwingUtilities.convertPoint(preview, 0, preview.getHeight(), view);
                        var actionTop = SwingUtilities.convertPoint(action, 0, 0, view);
                        assertThat(previewBottom.y).isLessThanOrEqualTo(actionTop.y - m(dev.jasper.ui.metrics.DesignMetrics.Role.COLUMN_GAP));
                        assertThat(action.getWidth()).isEqualTo(s(400) - 2*m(dev.jasper.ui.metrics.DesignMetrics.Role.HERO_SIDES));
                        assertThat(view.getPreferredSize()).isEqualTo(view.getSize());
                    } finally {
                        release(ctx);
                    }
                });
    }

    @Test
    void nestedVerticalStackMeasuresWrappedSettingsAtTheAssignedWidthAndReflowsOnResize() {
        Edt.run(
                () -> {
                    var ctx = KitFixtures.context();
                    Object old = UIManager.get("defaultFont");
                    try {
                        UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18));
                        var setting =
                                new SettingRow(
                                        "setting",
                                        ControlState.READY,
                                        "Automatic Connection",
                                        "Connect automatically when the workspace opens. The saved"
                                            + " credential is used for authentication and the host"
                                            + " must already be reachable before the first"
                                            + " connection begins.",
                                        new Toggle(
                                                "toggle",
                                                ControlState.READY,
                                                "Enabled",
                                                true,
                                                "toggle",
                                                false),
                                        false);
                        var nested =
                                new Stack(
                                        "nested",
                                        ControlState.READY,
                                        LayoutRole.VERTICAL,
                                        List.of(
                                                setting,
                                                new Text(
                                                        "following",
                                                        ControlState.READY,
                                                        "Following row",
                                                        TextRole.BODY)));
                        try (var view =
                                new KitView(
                                        ctx,
                                        new ViewSpec(form(FormKind.FULL, List.of(nested))),
                                        e -> {})) {
                            int widerHeight = 0;
                            for (int width : new int[] {540, 320, 540}) {
                                view.setSize(s(width), s(800));
                                layout(view);
                                var row = find(view, "setting", JComponent.class);
                                var description =
                                        find(view, "setting.description", JComponent.class);
                                var following = find(view, "following", JComponent.class);
                                assertThat(description.getY() + description.getHeight())
                                        .isLessThanOrEqualTo(row.getHeight() - m(dev.jasper.ui.metrics.DesignMetrics.Role.SETTING_VERTICAL_PADDING));
                                assertThat(following.getY())
                                        .isGreaterThanOrEqualTo(
                                                row.getY() + row.getHeight() + m(dev.jasper.ui.metrics.DesignMetrics.Role.ROW_GAP));
                                assertThat(find(view, "nested", JComponent.class).getHeight())
                                        .isGreaterThanOrEqualTo(
                                                following.getY() + following.getHeight());
                                if (width == 320)
                                    assertThat(row.getHeight()).isGreaterThan(widerHeight);
                                else if (widerHeight == 0) widerHeight = row.getHeight();
                                else assertThat(row.getHeight()).isEqualTo(widerHeight);
                            }
                        }
                    } finally {
                        UIManager.put("defaultFont", old);
                        release(ctx);
                    }
                });
    }
    @Test void threeColumnMeasurementUsesTheSameWidthsAsLayoutAfterResize() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var side = new SettingRow("sidebar", ControlState.READY, "Connection", "A sufficiently long description that wraps differently in the narrow semantic sidebar column", new Toggle("toggle", ControlState.READY, "Enabled", false, "toggle", true), false);
            var split = new Split("split", ControlState.READY, SplitKind.THREE_COLUMN, List.of(side, new Text("middle", ControlState.READY, "List", TextRole.BODY), new Text("detail", ControlState.READY, "Detail", TextRole.BODY)));
            try (var view = new KitView(context, stack(split, new Text("following", ControlState.READY, "Following", TextRole.BODY)), e -> {})) {
                for (int width : new int[]{900, 500, 700, 900}) {
                    KitFixtures.render(view, com.formdev.flatlaf.util.UIScale.scale(width), com.formdev.flatlaf.util.UIScale.scale(300), 1);
                    var sidebar = view.control("sidebar").component; var columns = view.control("split").component;
                    int needed = Composition.height(sidebar, sidebar.getWidth());
                    assertThat(columns.getHeight()).as("semantic sidebar width=%s at host=%s", sidebar.getWidth(), width).isGreaterThanOrEqualTo(needed);
                    assertThat(view.control("following").component.getY()).isGreaterThanOrEqualTo(columns.getY() + needed);
                }
            } finally { context.icons().close(); context.binding().close(); }
        });
    }
}
