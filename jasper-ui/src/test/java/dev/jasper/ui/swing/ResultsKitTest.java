package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemePair;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 2 Task 6b-2: what a command palette needs from the kit. Item-list headings, the RESULTS list kind (driven by an
 * external field: no focus, press activates, fixed heights, visible rows, one-line rows whose tag drops first, the
 * selection kept in view), field composition state and unlabelled search fields.
 */
class ResultsKitTest {
    @Test void subtitleInkUsesItsReceivingSlotWhileRoleAndExplicitSeveritySurviveSelection() {
        Edt.run(()->{
            var context=KitFixtures.context();
            for(var role:List.of(TextRole.BODY,TextRole.MONO)) {
                var detail=new Text("detail",READY,"Detail",role);
                var item=new ListItem("one",Optional.empty(),List.of(new Text("title",READY,"Title",TextRole.BODY)),List.of(detail),List.of());
                try(var view=new KitView(context,new ViewSpec(results(List.of(item),Set.of(),OptionalInt.empty())),event->{})) {
                    var label=find(view,"detail",JLabel.class);var font=label.getFont();
                    assertThat(label.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_TERTIARY));
                    for(boolean selected:new boolean[]{true,false,true}) {
                        view.update(new ViewSpec(results(List.of(item),selected?Set.of("one"):Set.of(),OptionalInt.empty())));
                        assertThat(find(view,"detail",JLabel.class)).isSameAs(label);assertThat(label.getFont()).isEqualTo(font);
                        assertThat(label.getForeground()).isEqualTo(selected?dev.jasper.ui.theme.SurfaceContrast.on(context.theme().semantic(),SemanticColor.TEXT_ONACCENT_SECONDARY,SemanticColor.ACCENT,dev.jasper.ui.theme.SurfaceContrast.TEXT):context.theme().semantic().color(SemanticColor.TEXT_TERTIARY));
                    }
                }
            }
            release(context);
        });
    }

    @Test void selectionUsesReceivingInkAndPreservesDeclaredTitleAndDetailFonts() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var items = List.of(heading("section", "Heading"), row("a", "First", "detail", "tag"), row("b", "Second", "other", null));
            try (var view = new KitView(context, new ViewSpec(results(items, Set.of("a"), OptionalInt.empty())), event -> {})) {
                var title = find(view,"a.title",JLabel.class); var detail = find(view,"a.detail",JLabel.class);
                assertThat(title.getFont()).isEqualTo(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(detail.getFont()).isEqualTo(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.DESCRIPTION));
                assertThat(find(view,"section.title",JLabel.class).getFont().isBold()).isFalse();
                assertThat(find(view,"a.tag",JLabel.class).getFont().isBold()).isFalse();
                view.update(new ViewSpec(results(items,Set.of("b"),OptionalInt.empty())));
                assertThat(title.getFont()).isEqualTo(ThemePainting.font(context,dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(detail.getForeground()).isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_TERTIARY));
            } finally { release(context); }
        });
    }

    static final ControlState READY = ControlState.READY;

    static ListItem heading(String id, String title) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".title", READY, title, TextRole.CAPTION)), List.of(), List.of(), true);
    }

    static ListItem row(String id, String title, String detail, String tag) {
        return new ListItem(id, Optional.of(new Nodes.Icon(id + ".icon", READY, "play", Hue.NONE, IconSize.RAIL, "Command")),
            List.of(new Text(id + ".title", READY, title, TextRole.BODY)),
            detail == null ? List.of() : List.of(new Text(id + ".detail", READY, detail, TextRole.DESCRIPTION)),
            tag == null ? List.of() : List.of(new Text(id + ".tag", READY, tag, TextRole.CAPTION)));
    }

    static ItemList results(List<ListItem> items, Set<String> selected, OptionalInt visible) {
        return new ItemList("results", READY, ListKind.RESULTS, items, selected, "run", List.of(), SelectionMode.SINGLE, Optional.empty(), visible);
    }

    static List<ListItem> sections(int rows) {
        var items = new ArrayList<ListItem>();
        items.add(heading("h0", "Commands"));
        for (int i = 0; i < rows; i++) {
            if (i == 2) items.add(heading("h1", "History"));
            items.add(row("r" + i, "Row " + i, null, null));
        }
        return items;
    }

    static InteractiveRows.RowsControl rows(KitView view) { return (InteractiveRows.RowsControl) view.control("results"); }

    static Component row(KitView view, String id) {
        return SwingUtilities.getAncestorOfClass(InteractiveRows.Row.class, find(view, id + ".title", JLabel.class));
    }

    static MouseEvent mouse(Component on, int type, int button, int modifiers, boolean popup) {
        return new MouseEvent(on, type, System.currentTimeMillis(), modifiers, 3, 3, 1, popup, button);
    }

    static void deliver(Component on, MouseEvent event) {
        for (var listener : on.getMouseListeners()) switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event);
            case MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event);
            case MouseEvent.MOUSE_CLICKED -> listener.mouseClicked(event);
            case MouseEvent.MOUSE_ENTERED -> listener.mouseEntered(event);
            case MouseEvent.MOUSE_EXITED -> listener.mouseExited(event);
            default -> { }
        }
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { component.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    static Color over(Color base, Color overlay) {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(base); g.fillRect(0, 0, 1, 1); g.setColor(overlay); g.fillRect(0, 0, 1, 1); } finally { g.dispose(); }
        return new Color(image.getRGB(0, 0), true);
    }

    @Test void headingsAreNeverSelectedActivatedOrSteppedOntoAndOpenTheirSectionWithARule() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var list = new ItemList("results", READY, ListKind.SINGLE, sections(4), Set.of(), "run", List.of(),
                SelectionMode.MULTIPLE, Optional.empty());
            try (var view = new KitView(context, new ViewSpec(list), events::add)) {
                view.setSize(400, 400); InteractiveRowsTest.layout(view);
                var control = rows(view);
                assertThat(control.selectable(0)).isFalse();
                deliver(row(view, "h1"), mouse(row(view, "h1"), MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 0, false));
                assertThat(events).as("a click on a heading does nothing").isEmpty();
                control.activate("h0");
                assertThat(events).as("nor can it be activated").isEmpty();
                control.move(1);
                assertThat(events.getLast()).as("the first step lands on the first row, past the heading")
                    .isEqualTo(new UiEvent.Selected("results", Set.of("r0")));
                control.move(1); control.move(1);
                assertThat(events.getLast()).as("stepping skips the second heading").isEqualTo(new UiEvent.Selected("results", Set.of("r2")));
                control.move(-1);
                assertThat(events.getLast()).isEqualTo(new UiEvent.Selected("results", Set.of("r1")));
                control.move(-1); control.move(-1);
                assertThat(events.getLast()).as("and stops at the first row").isEqualTo(new UiEvent.Selected("results", Set.of("r0")));
                assertThat(((InteractiveRows.Row) row(view, "h1")).check.isVisible()).as("headings have no checkbox").isFalse();
                assertThat(((InteractiveRows.Row) row(view, "r1")).check.isVisible()).isTrue();
                var image = paint(view);
                var rule = SwingUtilities.convertPoint(row(view, "h1"), row(view, "h1").getWidth() / 2, 0, view);
                assertThat(new Color(image.getRGB(rule.x, rule.y), true)).as("the later heading opens with a hairline in the menu edge colour")
                    .isEqualTo(context.theme().semantic().color(SemanticColor.BORDER_MENU));
                var top = SwingUtilities.convertPoint(row(view, "h0"), row(view, "h0").getWidth() / 2, 0, view);
                assertThat(new Color(image.getRGB(top.x, top.y), true)).as("the first heading has none")
                    .isNotEqualTo(context.theme().semantic().color(SemanticColor.BORDER_SUBTLE));
            } finally { release(context); }
        });
    }

    @Test void headingsAreValidatedAsTitleOnlyNeverSelectedAndAbsentFromOrderedLists() {
        var title = List.<Node>of(new Text("t", READY, "Title", TextRole.CAPTION));
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("h", Optional.empty(), title,
            List.of(new Text("s", READY, "detail", TextRole.DESCRIPTION)), List.of(), true));
        assertThatIllegalArgumentException().isThrownBy(() -> new ListItem("h", Optional.empty(), List.of(), List.of(), List.of(), true));
        assertThatIllegalArgumentException().isThrownBy(() -> results(sections(1), Set.of("h0"), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ItemList("o", READY, ListKind.ORDERED, sections(1), Set.of(), "", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> results(sections(1), Set.of(), OptionalInt.of(0)));
        assertThat(new ListItem("a", Optional.empty(), title, List.of(), List.of()).heading()).as("items default to rows").isFalse();
        assertThat(new ItemList("l", READY, ListKind.SINGLE, List.of(), Set.of(), "", List.of()).visibleRows()).isEmpty();
    }

    @Test void resultsKeepFixedHeightsAndShowTheVisibleRowsWithTheirHeadings() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(results(sections(8), Set.of("r0"), OptionalInt.of(5))), e -> { })) {
                view.setSize(500, 600); InteractiveRowsTest.layout(view);
                int rowHeight = UIScale.scale(24), headingHeight = UIScale.scale(22);
                assertThat(row(view, "h0").getHeight()).isEqualTo(headingHeight);
                assertThat(row(view, "r0").getHeight()).isEqualTo(rowHeight);
                assertThat(row(view, "r1").getY()).isEqualTo(headingHeight + rowHeight);
                assertThat(row(view, "h1").getY()).isEqualTo(headingHeight + 2 * rowHeight);
                assertThat(row(view, "r2").getY()).isEqualTo(2 * headingHeight + 2 * rowHeight);
                var canvas = rows(view).canvas;
                assertThat(canvas.getPreferredScrollableViewportSize().height).as("five rows and the two headings above them")
                    .isEqualTo(5 * rowHeight + 2 * headingHeight);
                assertThat(canvas.getPreferredSize().height).isEqualTo(8 * rowHeight + 2 * headingHeight);
                assertThat(canvas.isFocusable()).as("the list never takes the field's focus").isFalse();
                view.update(new ViewSpec(results(sections(8), Set.of("r0"), OptionalInt.of(1))));
                assertThat(canvas.getPreferredScrollableViewportSize().height).isEqualTo(rowHeight + headingHeight);
            } finally { release(context); }
        });
    }

    @Test void aLeftPressRunsARowOnceWhileHeadingsRightPressesAndClicksDoNot() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(results(sections(3), Set.of("r0"), OptionalInt.empty())), events::add)) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var title = find(view, "r1.title", JLabel.class);
                deliver(title, mouse(title, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK, false));
                assertThat(events).containsExactly(new UiEvent.Selected("results", Set.of("r1")), new UiEvent.Action("run"));
                deliver(title, mouse(title, MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 0, false));
                assertThat(events).as("the click that follows the press runs nothing more").hasSize(2);
                events.clear();
                var rowTwo = row(view, "r2");
                deliver(rowTwo, mouse(rowTwo, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK, true));
                deliver(rowTwo, mouse(rowTwo, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK, false));
                var heading = row(view, "h1");
                deliver(heading, mouse(heading, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK, false));
                assertThat(events).as("right presses and headings run nothing").isEmpty();
                var row = row(view, "r2");
                deliver(row, mouse(row, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK, false));
                assertThat(events).containsExactly(new UiEvent.Selected("results", Set.of("r2")), new UiEvent.Action("run"));
                assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isNotSameAs(rows(view).canvas);
            } finally { release(context); }
        });
    }

    @Test void resultRowsLayOutOnOneLineAndTheTagGoesFirstWhenSpaceRunsOut() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var items = List.of(row("r", "Rendered command", "a detail", "Cmd+T"), row("plain", "Plain", null, null));
            try (var view = new KitView(context, new ViewSpec(results(items, Set.of(), OptionalInt.empty())), e -> { })) {
                int side = UIScale.scale(8), gap = UIScale.scale(8), icon = UIScale.scale(16), width = UIScale.scale(680);
                view.setSize(width, 200); InteractiveRowsTest.layout(view);
                var line = (InteractiveRows.Row) row(view, "r");
                JLabel title = find(view, "r.title", JLabel.class), detail = find(view, "r.detail", JLabel.class), tag = find(view, "r.tag", JLabel.class);
                java.util.function.Function<JComponent, Rectangle> in = c -> SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), line);
                assertThat(in.apply(line.leading).x).as("the icon column").isEqualTo(side);
                assertThat(in.apply(title).x).isEqualTo(side + icon + gap);
                assertThat(in.apply(find(view, "plain.title", JLabel.class)).x).as("rows without detail align on the same column")
                    .isEqualTo(side + icon + gap);
                assertThat(in.apply(detail).x).as("the detail follows the title").isGreaterThanOrEqualTo(in.apply(title).x + title.getWidth());
                assertThat(Math.abs(in.apply(detail).getCenterY() - in.apply(title).getCenterY())).as("on the same line").isLessThanOrEqualTo(1);
                assertThat(in.apply(tag).x + tag.getWidth()).as("the tag is right-aligned").isEqualTo(line.getWidth() - side);
                int start = side + icon + gap, titleWidth = title.getPreferredSize().width;
                line.setSize(start + titleWidth + UIScale.scale(40), line.getHeight()); line.doLayout();
                assertThat(line.trailing.getWidth()).as("the tag goes first").isZero();
                assertThat(line.title.getWidth()).isPositive();
                // Wide enough for title + tag alone, but not for title + detail + tag: the tag must still be dropped
                // first, not just cut for the detail's sake, or the detail would never get its turn.
                int fitsTitleAndTagOnly = start + titleWidth + gap + line.trailing.getPreferredSize().width;
                line.setSize(fitsTitleAndTagOnly + side, line.getHeight()); line.doLayout();
                assertThat(line.trailing.getWidth()).as("the tag is dropped when the detail would not otherwise fit too").isZero();
                assertThat(line.subtitle.getWidth()).as("the detail takes its place, cut if needed").isPositive();
            } finally { release(context); }
        });
    }

    @Test void selectedAndHoveredResultsPaintRoundedRoleColours() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(results(List.of(row("a", "A", null, null), row("b", "B", null, null)), Set.of("a"), OptionalInt.empty())), e -> { })) {
                view.setSize(400, 120); InteractiveRowsTest.layout(view);
                var semantic = context.theme().semantic();
                Component first = row(view, "a"), second = row(view, "b");
                deliver(second, mouse(second, MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON, 0, false));
                var image = paint(view);
                java.util.function.BiFunction<Component, Point, Color> at = (c, p) -> {
                    var point = SwingUtilities.convertPoint(c, p, view); return new Color(image.getRGB(point.x, point.y), true);
                };
                int w = first.getWidth();
                assertThat(at.apply(first, new Point(w - UIScale.scale(12), UIScale.scale(3)))).as("the palette highlights with the accent").isEqualTo(semantic.color(SemanticColor.ACCENT));
                assertThat(at.apply(first, new Point(w - UIScale.scale(5), 0)).getAlpha()).as("selected rounded corner").isZero();
                assertThat(at.apply(first, new Point(UIScale.scale(2), UIScale.scale(12))).getAlpha()).as("inset from the edge").isZero();
                var hover = ColorMath.opacity(semantic.color(SemanticColor.HOVER_OVERLAY), .05);
                assertThat(at.apply(second, new Point(w - UIScale.scale(12), UIScale.scale(3)))).isEqualTo(over(new Color(0, 0, 0, 0), hover));
                assertThat(at.apply(second, new Point(w - UIScale.scale(5), 0)).getAlpha()).as("hover rounded corner").isZero();
                // AWT reports where the pointer went: outside the row it left.
                deliver(second, new MouseEvent(second, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, -1, -1, 0, false));
                var after = paint(view); var point = SwingUtilities.convertPoint(second, w - UIScale.scale(12), UIScale.scale(3), view);
                assertThat(after.getRGB(point.x, point.y) >>> 24).as("after the mouse left").isZero();
            } finally { release(context); }
        });
    }

    @Test void hostUpdatesKeepTheSelectedRowAndItsHeadingInViewButAThemeChangeKeepsTheScroll() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var items = new ArrayList<ListItem>();
            for (int i = 0; i < 30; i++) { if (i % 10 == 0) items.add(heading("h" + i, "Section " + i)); items.add(row("r" + i, "Row " + i, null, null)); }
            try (var view = new KitView(context, new ViewSpec(results(items, Set.of("r0"), OptionalInt.of(5))), e -> { })) {
                view.setSize(400, view.getPreferredSize().height); InteractiveRowsTest.layout(view);
                var control = rows(view); var viewport = control.scroll.getViewport();
                view.update(new ViewSpec(results(items, Set.of("r20"), OptionalInt.of(5))));
                InteractiveRowsTest.layout(view);
                int index = control.indexOf("r20");
                Rectangle visible = viewport.getViewRect();
                assertThat(visible.contains(new Rectangle(0, control.top(index), 1, control.heightAt(index)))).isTrue();
                assertThat(visible.contains(new Rectangle(0, control.top(index - 1), 1, control.heightAt(index - 1))))
                    .as("the heading directly above is in view too").isTrue();
                assertThat(visible.y + visible.height).as("scrolled no further than the row").isEqualTo(control.top(index) + control.heightAt(index));
                view.update(new ViewSpec(results(items, Set.of("r20"), OptionalInt.of(5))));
                assertThat(viewport.getViewPosition().y).isEqualTo(visible.y);
                viewport.setViewPosition(new Point(0, control.top(control.indexOf("r5"))));
                int scrolled = viewport.getViewPosition().y;
                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                InteractiveRowsTest.layout(view);
                assertThat(viewport.getViewPosition().y).as("a theme change keeps where the user scrolled").isEqualTo(scrolled);
                assertThat(view.spec().root()).isEqualTo(results(items, Set.of("r20"), OptionalInt.of(5)));
                var reordered = new ArrayList<>(items); reordered.add(1, reordered.remove(reordered.indexOf(items.stream().filter(i -> i.id().equals("r20")).findFirst().orElseThrow())));
                view.update(new ViewSpec(results(reordered, Set.of("r20"), OptionalInt.of(5))));
                InteractiveRowsTest.layout(view);
                assertThat(viewport.getViewRect().contains(new Rectangle(0, control.top(1), 1, control.heightAt(1))))
                    .as("the same selection moved: the update reveals it again").isTrue();
            } finally { release(context); }
        });
    }

    /** Fields a host composition labels (and, transparent, backs): no visible label, the name kept, the outline kept. */
    @Test void embeddedFieldsLeaveTheLabelToTheHostAndTransparentOnesShowTheParentSurface() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            Node standard = new Field("standard", READY, FieldKind.TEXT, "", "", "Standard", "");
            Node embedded = new Field("embedded", READY, FieldKind.TEXT, "", "", "Embedded", "", Optional.empty(), FieldPresentation.EMBEDDED);
            Node transparent = new Field("transparent", READY, FieldKind.SEARCH, "", "", "Transparent", "", Optional.empty(), FieldPresentation.TRANSPARENT);
            assertThat(((Field) standard).presentation()).as("the prior constructors stay standard").isEqualTo(FieldPresentation.STANDARD);
            try (var view = new KitView(context, stack(standard, embedded, transparent), e -> { })) {
                view.setSize(UIScale.scale(300), view.getPreferredSize().height); InteractiveRowsTest.layout(view);
                assertThat(find(view, "standard.label", JLabel.class).isVisible()).isTrue();
                assertThat(find(view, "embedded.label", JLabel.class).isVisible()).as("the host lays out the label").isFalse();
                assertThat(editor(view, "embedded").getAccessibleContext().getAccessibleName()).isEqualTo("Embedded");
                assertThat(find(view, "embedded.label", JLabel.class).getLabelFor()).isSameAs(editor(view, "embedded"));
                assertThat(find(view, "transparent.label", JLabel.class).isVisible()).isFalse();
                var semantic = context.theme().semantic();
                var image = new java.awt.image.BufferedImage(view.getWidth(), view.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { view.paint(g); } finally { g.dispose(); }
                java.util.function.BiFunction<String, Point, Color> at = (id, p) -> {
                    var surface = SwingUtilities.getAncestorOfClass(TextFields.FieldSurface.class, editor(view, id));
                    var point = SwingUtilities.convertPoint(surface, p, view); return new Color(image.getRGB(point.x, point.y), true);
                };
                var embeddedSurface = SwingUtilities.getAncestorOfClass(TextFields.FieldSurface.class, editor(view, "embedded"));
                var transparentSurface = SwingUtilities.getAncestorOfClass(TextFields.FieldSurface.class, editor(view, "transparent"));
                assertThat(at.apply("embedded", new Point(embeddedSurface.getWidth() - UIScale.scale(4), embeddedSurface.getHeight() / 2)))
                    .as("an embedded field keeps the input fill").isEqualTo(semantic.color(SemanticColor.SURFACE_INPUT));
                assertThat(at.apply("transparent", new Point(transparentSurface.getWidth() - UIScale.scale(4), transparentSurface.getHeight() / 2)).getAlpha())
                    .as("a transparent field shows its parent surface").isZero();
                assertThat(at.apply("transparent", new Point(transparentSurface.getWidth() / 2, 0)))
                    .as("and keeps its outline").isEqualTo(semantic.color(SemanticColor.BORDER_INPUT));
            } finally { release(context); }
        });
    }

    @Test void fieldsReportWhileAnInputMethodComposesAndSearchFieldsKeepTheirLabelOnlyAsAName() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(field("query", FieldKind.SEARCH, ""), field("name", FieldKind.TEXT, "")), e -> { })) {
                var input = editor(view, "query");
                assertThat(view.composing("query")).isFalse();
                input.dispatchEvent(new InputMethodEvent(input, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    new AttributedString("ab").getIterator(), 1, null, null));
                assertThat(view.composing("query")).as("one character is still uncommitted").isTrue();
                input.dispatchEvent(new InputMethodEvent(input, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                    new AttributedString("ab").getIterator(), 2, null, null));
                assertThat(view.composing("query")).isFalse();
                assertThatIllegalArgumentException().isThrownBy(() -> view.composing("root"));
                assertThat(find(view, "query.label", JLabel.class).isVisible()).as("a search field shows no label").isFalse();
                assertThat(input.getAccessibleContext().getAccessibleName()).isEqualTo("Name");
                assertThat(find(view, "query.label", JLabel.class).getLabelFor()).isSameAs(input);
                assertThat(find(view, "name.label", JLabel.class).isVisible()).as("other fields keep theirs").isTrue();
                view.update(stack(field("query", FieldKind.SEARCH, "x"), field("name", FieldKind.TEXT, "y")));
                assertThat(find(view, "query.label", JLabel.class).isVisible()).isFalse();
            } finally { release(context); }
        });
    }
}
