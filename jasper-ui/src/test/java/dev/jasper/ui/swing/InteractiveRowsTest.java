package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

@SuppressWarnings("try")
class InteractiveRowsTest {
    static ListItem item(int i) {
        return new ListItem(
                "item" + i,
                Optional.of(
                        new Nodes.Icon(
                                "folder" + i,
                                ControlState.READY,
                                "folder",
                                Hue.BLUE,
                                IconSize.CONTROL,
                                "Folder")),
                List.of(
                        new Text(
                                "name" + i, ControlState.READY, "File Server " + i, TextRole.BODY)),
                List.of(
                        new Chip("http" + i, ControlState.READY, "HTTP", Hue.GREEN),
                        new Text(
                                "endpoint" + i,
                                ControlState.READY,
                                "/a/very/long/path/filename.txt",
                                TextRole.MONO_SMALL)),
                List.of(
                        new Toggle(
                                "run" + i,
                                ControlState.READY,
                                "Run server " + i,
                                false,
                                "start-stop",
                                true)));
    }

    static ItemList list(List<ListItem> items, Set<String> selected) {
        return new ItemList(
                "servers", ControlState.READY, ListKind.DOUBLE, items, selected, "open", List.of());
    }

    static void layout(Component c) {
        if (c instanceof Container p) {
            p.doLayout();
            for (var child : p.getComponents()) layout(child);
        }
    }

    static int count(Component c) {
        int n = 1;
        if (c instanceof Container p) for (var child : p.getComponents()) n += count(child);
        return n;
    }

    @Test
    void inlineSwitchClickAndSpaceEmitOnlyToggleAndNarrowTextCannotOverlapIt() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(list(List.of(item(0)), Set.of())),
                                    events::add)) {
                        view.setSize(210, 180);
                        layout(view);
                        var toggle = find(view, "run0", JButton.class);
                        toggle.doClick(0);
                        assertThat(events).containsExactly(new UiEvent.Toggled("run0", true));
                        events.clear();
                        var pressed =
                                toggle.getInputMap().get(KeyStroke.getKeyStroke("pressed SPACE"));
                        var released =
                                toggle.getInputMap().get(KeyStroke.getKeyStroke("released SPACE"));
                        toggle.getActionMap()
                                .get(pressed)
                                .actionPerformed(new java.awt.event.ActionEvent(toggle, 0, ""));
                        toggle.getActionMap()
                                .get(released)
                                .actionPerformed(new java.awt.event.ActionEvent(toggle, 0, ""));
                        assertThat(events).containsExactly(new UiEvent.Toggled("run0", true));
                        var endpoint = find(view, "endpoint0", JLabel.class);
                        Rectangle
                                a =
                                        SwingUtilities.convertRectangle(
                                                endpoint.getParent(), endpoint.getBounds(), view),
                                b =
                                        SwingUtilities.convertRectangle(
                                                toggle.getParent(), toggle.getBounds(), view);
                        assertThat(a.x + a.width).isLessThanOrEqualTo(b.x);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void tenThousandRowsMountOnlyViewportPlusOverscan() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var items =
                            java.util.stream.IntStream.range(0, 10000)
                                    .mapToObj(InteractiveRowsTest::item)
                                    .toList();
                    try (var view =
                            new KitView(context, new ViewSpec(list(items, Set.of())), e -> {})) {
                        view.setSize(420, 260);
                        layout(view);
                        assertThat(count(view)).isLessThan(260);
                        var scroll = find(view, "servers", JScrollPane.class);
                        scroll.getViewport().setViewPosition(new Point(0, 20000));
                        layout(view);
                        assertThat(count(view)).isLessThan(260);
                        view.focus("run9999");
                        layout(view);
                        assertThat(find(view, "run9999", JButton.class).isFocusable()).isTrue();
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void recycleClosesOldControlListenersAndRestoresLogicalFocusAndConfirmedValue() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var items =
                            new ArrayList<>(
                                    java.util.stream.IntStream.range(0, 10000)
                                            .mapToObj(InteractiveRowsTest::item)
                                            .toList());
                    var first = items.getFirst();
                    items.set(
                            0,
                            new ListItem(
                                    first.id(),
                                    first.leading(),
                                    first.title(),
                                    first.subtitle(),
                                    List.of(
                                            new Toggle(
                                                    "run0",
                                                    ControlState.READY,
                                                    "Run server",
                                                    true,
                                                    "toggle",
                                                    true))));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(list(items, Set.of("item0"))),
                                    events::add)) {
                        view.setSize(400, 250);
                        layout(view);
                        var owner = (ItemLists.ListControl) view.control("servers");
                        var shells = new HashSet<>(owner.mounted.values());
                        var old = find(view, "run0", JButton.class);
                        for (var listener : old.getFocusListeners())
                            listener.focusGained(
                                    new java.awt.event.FocusEvent(
                                            old, java.awt.event.FocusEvent.FOCUS_GAINED));
                        owner.scroll.getViewport().setViewPosition(new Point(0, 20000));
                        layout(view);
                        assertThat(owner.mounted.values()).anyMatch(shells::contains);
                        assertThat(old.getActionListeners()).isEmpty();
                        old.doClick(0);
                        assertThat(events).isEmpty();
                        assertThat(owner.focusItem).isEqualTo("item0");
                        assertThat(owner.focusControl).isEqualTo("run0");
                        owner.scroll.getViewport().setViewPosition(new Point());
                        layout(view);
                        var current = find(view, "run0", JButton.class);
                        assertThat(current).isNotSameAs(old);
                        assertThat(current.isSelected()).isTrue();
                        assertThat(owner.selected).containsExactly("item0");
                        view.update(new ViewSpec(list(List.of(item(2), item(1)), Set.of())));
                        assertThat(owner.focusControl).isNull();
                        assertThatThrownBy(() -> view.focus("run0"))
                                .isInstanceOf(IllegalArgumentException.class);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void selectionSurvivesReorderAndRetainedFilterIdsWhileQueryDocumentSurvivesEmptyResult() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var query =
                            new Field(
                                    "query",
                                    ControlState.READY,
                                    FieldKind.SEARCH,
                                    "",
                                    "Filter",
                                    "Search",
                                    "");
                    try (var view =
                            new KitView(
                                    context,
                                    stack(query, list(List.of(item(0), item(1)), Set.of())),
                                    events::add)) {
                        var owner = (ItemLists.ListControl) view.control("servers");
                        owner.select("item1", false);
                        var input = find(view, "query.input", JTextField.class);
                        input.setText("pending query");
                        var document = input.getDocument();
                        view.update(stack(query, list(List.of(item(1), item(0)), Set.of())));
                        assertThat(owner.selected).containsExactly("item1");
                        view.update(stack(query, list(List.of(item(1)), Set.of())));
                        assertThat(owner.selected).containsExactly("item1");
                        view.update(stack(query, list(List.of(), Set.of())));
                        assertThat(owner.selected).isEmpty();
                        assertThat(find(view, "query.input", JTextField.class).getDocument())
                                .isSameAs(document);
                        assertThat(input.getText()).isEqualTo("pending query");
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void multipleCheckboxesAndContextUseCurrentStableSelection() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var menu =
                            new dev.jasper.ui.model.MenuItem(
                                    "delete", "Delete", "delete", "delete-selected", true, false);
                    var spec =
                            new ItemList(
                                    "servers",
                                    ControlState.READY,
                                    ListKind.DOUBLE,
                                    List.of(item(0), item(1)),
                                    Set.of(),
                                    "open",
                                    List.of(menu),
                                    SelectionMode.MULTIPLE,
                                    Optional.empty());
                    try (var view = new KitView(context, new ViewSpec(spec), events::add)) {
                        view.setSize(400, 240);
                        layout(view);
                        var owner = (ItemLists.ListControl) view.control("servers");
                        owner.mounted.get("item0").check.doClick(0);
                        owner.mounted.get("item1").check.doClick(0);
                        assertThat(owner.selected).containsExactlyInAnyOrder("item0", "item1");
                        events.clear();
                        owner.context("item1", owner.canvas, 0, 0);
                        ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Selected("servers", Set.of("item0", "item1")),
                                        new UiEvent.Action("delete-selected"));
                        owner.select("item0", false);
                        assertThat(owner.mounted.get("item1").check.isSelected()).isFalse();
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void activationDispatchesSelectionBeforeActionAndSuppressesActionAfterRebindOrClose() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    KitView[] ref = {null};
                    int[] mode = {0};
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(list(List.of(item(0)), Set.of())),
                                    e -> {
                                        events.add(e);
                                        if (e instanceof UiEvent.Selected && mode[0] == 1)
                                            ref[0].update(new ViewSpec(list(List.of(), Set.of())));
                                        if (e instanceof UiEvent.Selected && mode[0] == 2)
                                            ref[0].close();
                                    })) {
                        ref[0] = view;
                        var owner = (ItemLists.ListControl) view.control("servers");
                        owner.activate("item0");
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Selected("servers", Set.of("item0")),
                                        new UiEvent.Action("open"));
                        events.clear();
                        mode[0] = 1;
                        owner.activate("item0");
                        assertThat(events)
                                .containsExactly(new UiEvent.Selected("servers", Set.of("item0")));
                        events.clear();
                        view.update(new ViewSpec(list(List.of(item(0)), Set.of())));
                        mode[0] = 2;
                        owner.activate("item0");
                        assertThat(events)
                                .containsExactly(new UiEvent.Selected("servers", Set.of("item0")));
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void orderedActionsHaveStablePayloadFooterAndSingleThirtyPercentDisabledPaint() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var add =
                            new Nodes.Button(
                                    "add-key",
                                    ControlState.READY,
                                    "Add Stored Key",
                                    "add",
                                    ButtonKind.GHOST,
                                    "add-key-action",
                                    "");
                    var spec =
                            new ItemList(
                                    "keys",
                                    ControlState.READY,
                                    ListKind.ORDERED,
                                    List.of(item(0), item(1)),
                                    Set.of(),
                                    "",
                                    List.of(),
                                    SelectionMode.SINGLE,
                                    Optional.of(add));
                    try (var view = new KitView(context, new ViewSpec(spec), events::add)) {
                        view.setSize(500, 240);
                        layout(view);
                        var owner = (ItemLists.ListControl) view.control("keys");
                        var first = owner.mounted.get("item0");
                        var last = owner.mounted.get("item1");
                        var up = (Buttons.KitButton) first.trailing.getComponent(1);
                        var down = (Buttons.KitButton) first.trailing.getComponent(2);
                        var lastDown = (Buttons.KitButton) last.trailing.getComponent(2);
                        assertThat(up.isEnabled()).isFalse();
                        assertThat(lastDown.isEnabled()).isFalse();
                        assertThat(down.isEnabled()).isTrue();
                        down.doClick(0);
                        ((JButton) first.trailing.getComponent(3)).doClick(0);
                        find(view, "add-key", JButton.class).doClick(0);
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Reordered("keys", "item0", 1),
                                        new UiEvent.Removed("keys", "item0"),
                                        new UiEvent.Action("add-key-action"));
                        assertThat(owner.footer.getY()).isEqualTo(owner.size() * owner.rowHeight);
                        // The same authored translucent icon is painted once by each actual
                        // disabled-button
                        // owner.
                        javax.swing.Icon translucent =
                                new javax.swing.Icon() {
                                    public int getIconWidth() {
                                        return 8;
                                    }

                                    public int getIconHeight() {
                                        return 8;
                                    }

                                    public void paintIcon(Component c, Graphics g, int x, int y) {
                                        g.setColor(new Color(10, 20, 30, 128));
                                        g.fillRect(x, y, 8, 8);
                                    }
                                };
                        up.putClientProperty("Jasper.button.icon", null);
                        up.setIcon(translucent);
                        up.setSize(30, 30);
                        int a = maxAlpha(ButtonsTest.paint(up));
                        assertThat(a).isBetween(37, 39);
                        var ordinary =
                                new Buttons.ButtonControl(
                                        view,
                                        new Nodes.Button(
                                                "ordinary",
                                                new ControlState(true, false, false, ""),
                                                "",
                                                "close",
                                                ButtonKind.GHOST,
                                                "ordinary",
                                                "Ordinary"));
                        try {
                            ordinary.button.putClientProperty("Jasper.button.icon", null);
                            ordinary.button.setIcon(translucent);
                            ordinary.button.setSize(30, 30);
                            assertThat(maxAlpha(ButtonsTest.paint(ordinary.button)))
                                    .isBetween(53, 55);
                        } finally {
                            ordinary.close();
                        }
                        view.update(new ViewSpec(spec));
                        layout(view);
                        assertThat(first.leading.getComponentCount()).isEqualTo(2);
                    } finally {
                        release(context);
                    }
                });
    }

    static int maxAlpha(java.awt.image.BufferedImage image) {
        int max = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                max = Math.max(max, (image.getRGB(x, y) >>> 24) & 255);
        return max;
    }

    @Test
    void leadingEllipsisRetainsFilenameAndNavigationReservesButtons() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var path =
                            new Text(
                                    "path",
                                    ControlState.READY,
                                    "/some/very/long/folder/filename.txt",
                                    TextRole.MONO_SMALL);
                    var back =
                            new Nodes.Button(
                                    "back",
                                    ControlState.READY,
                                    "",
                                    "back",
                                    ButtonKind.GHOST,
                                    "back",
                                    "Back");
                    var next =
                            new Nodes.Button(
                                    "next",
                                    ControlState.READY,
                                    "",
                                    "forward",
                                    ButtonKind.GHOST,
                                    "next",
                                    "Next");
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Nodes.Stack(
                                                    "nav",
                                                    ControlState.READY,
                                                    LayoutRole.NAVIGATION,
                                                    List.of(back, path, next))),
                                    e -> {})) {
                        view.setSize(
                                com.formdev.flatlaf.util.UIScale.scale(220),
                                com.formdev.flatlaf.util.UIScale.scale(40));
                        layout(view);
                        var label = find(view, "path", JLabel.class);
                        var button = find(view, "next", JButton.class);
                        assertThat(button.getWidth()).isEqualTo(button.getPreferredSize().width);
                        String shown =
                                Navigation.leadingEllipsis(
                                        path.text(),
                                        label.getFontMetrics(label.getFont()),
                                        label.getWidth());
                        assertThat(shown).startsWith("\u2026").endsWith("filename.txt");
                        assertThat(label.getClientProperty("Jasper.leadingEllipsis"))
                                .isEqualTo(true);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void rowSchemaRejectsMutableInputsAndKeepsDefaultSingleSelection() {
        assertThat(list(List.of(item(0)), Set.of()).selectionMode())
                .isEqualTo(SelectionMode.SINGLE);
        assertThatThrownBy(
                        () ->
                                new ListItem(
                                        "bad",
                                        Optional.empty(),
                                        List.of(
                                                new Secret(
                                                        "secret",
                                                        ControlState.READY,
                                                        "",
                                                        "Password",
                                                        "")),
                                        List.of(),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> list(List.of(item(0), item(1)), Set.of("item0", "item1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ViewSpec(list(List.of(item(0)), Set.of("absent"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inlineDisabledAncestorsAndRemovedControlIdentityAreRespected() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var disabled =
                            new Nodes.Stack(
                                    "inline",
                                    new ControlState(true, false, false, ""),
                                    LayoutRole.INLINE,
                                    List.of(
                                            new Toggle(
                                                    "child-toggle",
                                                    ControlState.READY,
                                                    "Toggle",
                                                    false,
                                                    "toggle",
                                                    true)));
                    var row =
                            new ListItem(
                                    "item",
                                    Optional.empty(),
                                    List.of(
                                            new Text(
                                                    "title",
                                                    ControlState.READY,
                                                    "Title",
                                                    TextRole.BODY)),
                                    List.of(),
                                    List.of(disabled));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(list(List.of(row), Set.of())),
                                    events::add)) {
                        var owner = (ItemLists.ListControl) view.control("servers");
                        var button = find(view, "child-toggle", JButton.class);
                        assertThat(button.isEnabled()).isFalse();
                        button.doClick(0);
                        assertThat(events).isEmpty();
                        view.focus("child-toggle");
                        assertThat(owner.focusControl).isEqualTo("child-toggle");
                        view.update(
                                new ViewSpec(
                                        list(
                                                List.of(
                                                        new ListItem(
                                                                "item",
                                                                Optional.empty(),
                                                                row.title(),
                                                                List.of(),
                                                                List.of())),
                                                Set.of())));
                        assertThat(owner.focusControl).isNull();
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void contextPopupOwnsThemedEntriesAndTearsDownTheirListeners() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var action =
                            new dev.jasper.ui.model.MenuItem(
                                    "delete", "Delete", "delete", "delete", true, false, Hue.PURPLE);
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new ItemList(
                                                    "servers",
                                                    ControlState.READY,
                                                    ListKind.SINGLE,
                                                    List.of(item(0)),
                                                    Set.of(),
                                                    "",
                                                    List.of(action))),
                                    e -> {})) {
                        var owner = (ItemLists.ListControl) view.control("servers");
                        owner.context("item0", owner.canvas, 0, 0);
                        var old = (JMenuItem) owner.popup.getComponent(0);
                        assertThat(old).isInstanceOf(Menus.Item.class);
                        assertThat(old.getIcon()).isNotNull();
                        var baseTheme=context.theme();var colors=new java.util.EnumMap<dev.jasper.ui.theme.SemanticColor,java.awt.Color>(dev.jasper.ui.theme.SemanticColor.class);
                        for(var role:dev.jasper.ui.theme.SemanticColor.values())colors.put(role,baseTheme.semantic().color(role));
                        colors.put(dev.jasper.ui.theme.SemanticColor.HUE_PURPLE,java.awt.Color.RED);
                        var contrasting=new dev.jasper.ui.theme.GeneratedTheme(baseTheme.name(),baseTheme.dark(),baseTheme.author(),baseTheme.tokens(),baseTheme.syntax(),baseTheme.terminal(),baseTheme.uiKeys(),baseTheme.icons(),baseTheme.glass(),new dev.jasper.ui.theme.SemanticPalette(colors));
                        for(var theme : List.of(baseTheme, contrasting)) {
                        context.binding().publish(new dev.jasper.ui.theme.ThemePair(theme,theme));
                        var expectedIcon = context.icons().tile("delete", context.theme().semantic(), Hue.PURPLE, com.formdev.flatlaf.util.UIScale.scale(18), false);
                        var actualPixels = new java.awt.image.BufferedImage(expectedIcon.getIconWidth(), expectedIcon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var expectedPixels = new java.awt.image.BufferedImage(expectedIcon.getIconWidth(), expectedIcon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var ag = actualPixels.createGraphics(); var eg = expectedPixels.createGraphics();
                        old.getIcon().paintIcon(old, ag, 0, 0); expectedIcon.paintIcon(old, eg, 0, 0); ag.dispose(); eg.dispose();
                        assertThat(actualPixels.getRGB(0,0,actualPixels.getWidth(),actualPixels.getHeight(),null,0,actualPixels.getWidth())).isEqualTo(expectedPixels.getRGB(0,0,expectedPixels.getWidth(),expectedPixels.getHeight(),null,0,expectedPixels.getWidth()));
                        }
                        owner.context("item0", owner.canvas, 0, 0);
                        assertThat(old.getActionListeners()).isEmpty();
                        var current = (JMenuItem) owner.popup.getComponent(0);
                        view.close();
                        assertThat(current.getActionListeners()).isEmpty();
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void synchronousSelectionConfirmationKeepsActivationButActionRebindingSuppressesIt() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    KitView[] ref = {null};
                    boolean[] rebind = {false};
                    var menu =
                            new dev.jasper.ui.model.MenuItem(
                                    "edit", "Edit", "edit", "edit-action", true, false);
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new ItemList(
                                                    "servers",
                                                    ControlState.READY,
                                                    ListKind.SINGLE,
                                                    List.of(item(0)),
                                                    Set.of(),
                                                    "open",
                                                    List.of(menu))),
                                    e -> {
                                        events.add(e);
                                        if (e instanceof UiEvent.Selected selected)
                                            ref[0].update(
                                                    new ViewSpec(
                                                            new ItemList(
                                                                    "servers",
                                                                    ControlState.READY,
                                                                    ListKind.SINGLE,
                                                                    List.of(item(0)),
                                                                    selected.ids(),
                                                                    rebind[0] ? "rebound" : "open",
                                                                    List.of(menu))));
                                    })) {
                        ref[0] = view;
                        var owner = (ItemLists.ListControl) view.control("servers");
                        owner.activate("item0");
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Selected("servers", Set.of("item0")),
                                        new UiEvent.Action("open"));
                        events.clear();
                        owner.context("item0", owner.canvas, 0, 0);
                        ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Selected("servers", Set.of("item0")),
                                        new UiEvent.Action("edit-action"));
                        events.clear();
                        rebind[0] = true;
                        owner.activate("item0");
                        assertThat(events)
                                .containsExactly(new UiEvent.Selected("servers", Set.of("item0")));
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void unchangedOrderedCommandsStayAttachedAndInlinePopupsParticipateInViewRouting() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var menu =
                            new MenuButton(
                                    "row-menu",
                                    ControlState.READY,
                                    "More",
                                    "",
                                    ButtonKind.GHOST,
                                    "",
                                    List.of(
                                            new dev.jasper.ui.model.MenuItem(
                                                    "edit", "Edit", "edit", "edit", true, false)),
                                    "");
                    var data =
                            new ListItem(
                                    "key",
                                    Optional.empty(),
                                    List.of(
                                            new Text(
                                                    "key-title",
                                                    ControlState.READY,
                                                    "Key",
                                                    TextRole.BODY)),
                                    List.of(),
                                    List.of(menu));
                    var descriptor =
                            new ItemList(
                                    "keys",
                                    ControlState.READY,
                                    ListKind.ORDERED,
                                    List.of(data),
                                    Set.of(),
                                    "",
                                    List.of());
                    try (var view = new KitView(context, new ViewSpec(descriptor), e -> {})) {
                        var owner = (ItemLists.ListControl) view.control("keys");
                        var row = owner.mounted.get("key");
                        var command = row.trailing.getComponent(1);
                        int[] moves = {0};
                        command.addHierarchyListener(
                                e -> {
                                    if ((e.getChangeFlags()
                                                    & java.awt.event.HierarchyEvent.PARENT_CHANGED)
                                            != 0) moves[0]++;
                                });
                        view.update(new ViewSpec(descriptor));
                        assertThat(moves[0]).isZero();
                        var inline = (Menus.MenuControl) view.control("row-menu");
                        inline.popup.setVisible(true);
                        assertThat(view.popupOpen()).isTrue();
                        assertThat(view.dismissPopup()).isTrue();
                        assertThat(inline.popup.isVisible()).isFalse();
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void switchingListToSingleSelectionReassertsDeclaredSelection() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var items = List.of(item(0), item(1));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new ItemList(
                                                    "list",
                                                    ControlState.READY,
                                                    ListKind.DOUBLE,
                                                    items,
                                                    Set.of(),
                                                    "open",
                                                    List.of(),
                                                    SelectionMode.MULTIPLE,
                                                    Optional.empty())),
                                    e -> {})) {
                        var owner = (ItemLists.ListControl) view.control("list");
                        owner.select("item0", true);
                        owner.select("item1", true);
                        assertThat(owner.selected).hasSize(2);
                        view.update(
                                new ViewSpec(
                                        new ItemList(
                                                "list",
                                                ControlState.READY,
                                                ListKind.DOUBLE,
                                                items,
                                                Set.of(),
                                                "open",
                                                List.of(),
                                                SelectionMode.SINGLE,
                                                Optional.empty())));
                        assertThat(owner.selected).isEmpty();
                        assertThat(owner.mounted.get("item0").check.isVisible()).isFalse();
                        owner.select("item1", false);
                        assertThat(owner.selected).containsExactly("item1");
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void inlineChoiceKindsKeepUsableInputHeightWithoutStandaloneLabels() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    try {
                        for (var kind : ChoiceKind.values())
                            for (var listKind : List.of(ListKind.SINGLE, ListKind.DOUBLE))
                                for (boolean inTitle : List.of(false, true)) {
                                    var choice =
                                            new Nodes.Choice(
                                                    "choice",
                                                    ControlState.READY,
                                                    kind,
                                                    List.of(
                                                            new Option(
                                                                    "one", "One", Optional.empty()),
                                                            new Option(
                                                                    "two",
                                                                    "Two",
                                                                    Optional.empty())),
                                                    "one",
                                                    "Transport",
                                                    "pick");
                                    var title =
                                            inTitle
                                                    ? List.<Node>of(choice)
                                                    : List.<Node>of(
                                                            new Text(
                                                                    "title",
                                                                    ControlState.READY,
                                                                    "Server",
                                                                    TextRole.BODY));
                                    var subtitle =
                                            listKind == ListKind.DOUBLE
                                                    ? List.<Node>of(
                                                            new Text(
                                                                    "subtitle",
                                                                    ControlState.READY,
                                                                    "Endpoint",
                                                                    TextRole.MONO_SMALL))
                                                    : List.<Node>of();
                                    var row =
                                            new ListItem(
                                                    "row",
                                                    Optional.empty(),
                                                    title,
                                                    subtitle,
                                                    inTitle ? List.of() : List.of(choice));
                                    try (var view =
                                            new KitView(
                                                    context,
                                                    new ViewSpec(
                                                            new ItemList(
                                                                    "list",
                                                                    ControlState.READY,
                                                                    listKind,
                                                                    List.of(row),
                                                                    Set.of(),
                                                                    "",
                                                                    List.of())),
                                                    e -> {})) {
                                        view.setSize(
                                                com.formdev.flatlaf.util.UIScale.scale(520),
                                                com.formdev.flatlaf.util.UIScale.scale(180));
                                        layout(view);
                                        var control =
                                                (Choices.ChoiceControl) view.control("choice");
                                        assertThat(control.label.isVisible())
                                                .as("%s %s title=%s", kind, listKind, inTitle)
                                                .isFalse();
                                        assertThat(control.input.getHeight())
                                                .as(
                                                        "usable %s %s title=%s",
                                                        kind, listKind, inTitle)
                                                .isGreaterThanOrEqualTo(
                                                        control.input.getPreferredSize().height);
                                        assertThat(
                                                        control.input
                                                                .getAccessibleContext()
                                                                .getAccessibleName())
                                                .isEqualTo("Transport");
                                        var rendered = ButtonsTest.paint(control.input);
                                        assertThat(maxAlpha(rendered)).isPositive();
                                    }
                                }
                    } finally {
                        release(context);
                    }
                });
    }
}
