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

    static int s(int value) {
        return com.formdev.flatlaf.util.UIScale.scale(value);
    }

    static void layout(Container container) {
        container.doLayout();
        for (Component c : container.getComponents())
            if (c instanceof Container child) layout(child);
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
                        assertThat(host.getX()).isEqualTo(s(108));
                        assertThat(port.getX() - host.getX() - host.getWidth()).isEqualTo(s(12));
                        assertThat(find(view, "row", JComponent.class).getX()).isEqualTo(s(15));
                        assertThat(find(view, "hint", JComponent.class).getX()).isEqualTo(s(108));
                        assertThat(find(view, "host.label", JComponent.class).isVisible())
                                .isFalse();
                        assertThat(card.getInsets().left).isEqualTo(s(15));
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
                                                            case SHEET -> 480;
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
                                    .isLessThanOrEqualTo(control.getX() + ring - s(12));
                            assertThat(control.getX() + control.getWidth() - ring)
                                    .isEqualTo(row.getWidth() - s(13));
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
                            assertThat(save.getHeight()).isGreaterThanOrEqualTo(s(30));
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
                                .isGreaterThanOrEqualTo(s(32));
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
                                .isEqualTo(s(4));
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
                                .isLessThanOrEqualTo(s(196));
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
                        assertThat(row.getX()).isEqualTo(s(15));
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
                        assertThat(previewBottom.y).isLessThanOrEqualTo(actionTop.y - s(12));
                        assertThat(action.getWidth()).isEqualTo(s(344));
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
                                        .isLessThanOrEqualTo(row.getHeight() - s(8));
                                assertThat(following.getY())
                                        .isGreaterThanOrEqualTo(
                                                row.getY() + row.getHeight() + s(10));
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
