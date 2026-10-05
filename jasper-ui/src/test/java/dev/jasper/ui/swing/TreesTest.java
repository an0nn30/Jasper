package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class TreesTest {
    @Test
    void treeMountsCollapsedBranch() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Tree(
                                                    "tree",
                                                    ControlState.READY,
                                                    List.of(
                                                            new TreeItem(
                                                                    "root",
                                                                    "Root",
                                                                    Optional.of("folder"),
                                                                    false,
                                                                    List.of(
                                                                            new TreeItem(
                                                                                    "file",
                                                                                    "File",
                                                                                    Optional
                                                                                            .empty(),
                                                                                    true,
                                                                                    List.of())))),
                                                    Set.of(),
                                                    Set.of(),
                                                    "open")),
                                    e -> {})) {
                        view.setSize(300, 200);
                        InteractiveRowsTest.layout(view);
                        assertThat(InteractiveRowsTest.count(view)).isLessThan(50);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void expansionIsOwnerScopedConfirmedAndKeyboardOperable() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var events = new ArrayList<UiEvent>();
                    var root =
                            new TreeItem(
                                    "root",
                                    "Root",
                                    Optional.of("folder"),
                                    false,
                                    List.of(
                                            new TreeItem(
                                                    "file",
                                                    "File",
                                                    Optional.empty(),
                                                    true,
                                                    List.of())));
                    var first =
                            new Tree(
                                    "one",
                                    ControlState.READY,
                                    List.of(root),
                                    Set.of(),
                                    Set.of(),
                                    "open-one");
                    var second =
                            new Tree(
                                    "two",
                                    ControlState.READY,
                                    List.of(root),
                                    Set.of(),
                                    Set.of(),
                                    "open-two");
                    try (var view = new KitView(context, stack(first, second), events::add)) {
                        var owner = (Trees.TreeControl) view.control("one");
                        var other = (Trees.TreeControl) view.control("two");
                        ((javax.swing.JButton) owner.mounted.get("root").leading.getComponent(0))
                                .doClick(0);
                        ((javax.swing.JButton) other.mounted.get("root").leading.getComponent(0))
                                .doClick(0);
                        assertThat(events)
                                .containsExactly(
                                        new UiEvent.Expanded("one", "root", true),
                                        new UiEvent.Expanded("two", "root", true));
                        assertThat(owner.size()).isEqualTo(1);
                        view.update(
                                stack(
                                        new Tree(
                                                "one",
                                                ControlState.READY,
                                                List.of(root),
                                                Set.of("root"),
                                                Set.of("file"),
                                                "open-one"),
                                        second));
                        assertThat(owner.size()).isEqualTo(2);
                        assertThat(owner.mounted.get("file").indent).isPositive();
                        events.clear();
                        owner.select("root", false);
                        owner.canvas
                                .getActionMap()
                                .get("LEFT")
                                .actionPerformed(
                                        new java.awt.event.ActionEvent(owner.canvas, 0, ""));
                        assertThat(events).endsWith(new UiEvent.Expanded("one", "root", false));
                        view.update(stack(first, second));
                        assertThat(owner.size()).isEqualTo(1);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void tenThousandExpandedTreeRowsStayBounded() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var leaves =
                            java.util.stream.IntStream.range(0, 10000)
                                    .mapToObj(
                                            i ->
                                                    new TreeItem(
                                                            "f" + i,
                                                            "File " + i,
                                                            Optional.empty(),
                                                            true,
                                                            List.of()))
                                    .toList();
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Tree(
                                                    "tree",
                                                    ControlState.READY,
                                                    List.of(
                                                            new TreeItem(
                                                                    "root",
                                                                    "Root",
                                                                    Optional.empty(),
                                                                    false,
                                                                    leaves)),
                                                    Set.of("root"),
                                                    Set.of(),
                                                    "open")),
                                    e -> {})) {
                        view.setSize(300, 250);
                        InteractiveRowsTest.layout(view);
                        assertThat(InteractiveRowsTest.count(view)).isLessThan(180);
                    } finally {
                        release(context);
                    }
                });
    }

    @Test
    void collapsingAndReopeningBranchRetainsDeclaredChildSelection() {
        Edt.run(
                () -> {
                    var context = KitFixtures.context();
                    var root =
                            new TreeItem(
                                    "root",
                                    "Root",
                                    Optional.empty(),
                                    false,
                                    List.of(
                                            new TreeItem(
                                                    "child",
                                                    "Child",
                                                    Optional.empty(),
                                                    true,
                                                    List.of())));
                    try (var view =
                            new KitView(
                                    context,
                                    new ViewSpec(
                                            new Tree(
                                                    "tree",
                                                    ControlState.READY,
                                                    List.of(root),
                                                    Set.of("root"),
                                                    Set.of("child"),
                                                    "open")),
                                    e -> {})) {
                        var owner = (Trees.TreeControl) view.control("tree");
                        assertThat(owner.selected).containsExactly("child");
                        view.update(
                                new ViewSpec(
                                        new Tree(
                                                "tree",
                                                ControlState.READY,
                                                List.of(root),
                                                Set.of(),
                                                Set.of("child"),
                                                "open")));
                        assertThat(owner.mounted).doesNotContainKey("child");
                        assertThat(owner.selected).containsExactly("child");
                        view.update(
                                new ViewSpec(
                                        new Tree(
                                                "tree",
                                                ControlState.READY,
                                                List.of(root),
                                                Set.of("root"),
                                                Set.of("child"),
                                                "open")));
                        assertThat(owner.mounted).containsKey("child");
                        assertThat(owner.selected).containsExactly("child");
                        view.update(
                                new ViewSpec(
                                        new Tree(
                                                "tree",
                                                ControlState.READY,
                                                List.of(),
                                                Set.of(),
                                                Set.of(),
                                                "open")));
                        assertThat(owner.selected).isEmpty();
                    } finally {
                        release(context);
                    }
                });
    }
}
