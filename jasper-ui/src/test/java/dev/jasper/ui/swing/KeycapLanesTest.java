package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 9b Rulings 7 and 8: a keycap tree with columns lies on its header's lanes (the Keymap mock's geometry). */
class KeycapLanesTest {
    static final List<Column> COLUMNS = List.of(new Column("action", "Action", Nodes.TextRole.BODY, false),
        new Column("scope", "Scope", Nodes.TextRole.BODY, false), new Column("shortcut", "Shortcut", Nodes.TextRole.BODY, false));

    static ViewSpec tree() {
        var copy = new TreeItem("action/copy", "Copy", Optional.empty(), true, List.of(), OptionalInt.empty(), false, "", Optional.empty(),
            List.of("Global", "⌘C"), Optional.empty());
        var clipboard = new TreeItem("group/Clipboard", "Clipboard", Optional.empty(), false, List.of(copy), OptionalInt.empty(), true, "",
            Optional.empty(), List.of("", "1 shortcut"), Optional.empty());
        return new ViewSpec(new Nodes.Tree("shortcuts", ControlState.READY, List.of(clipboard), Set.of("group/Clipboard"), Set.of(), "", COLUMNS));
    }

    static KeycapSequence keys() { return new KeycapSequence(List.of("⌘", "C"), "Command C"); }

    @Test void theRowsLieOnTheHeadersLanes() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, tree(), event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(keys()))));
                view.setSize(777, 300);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var tree = (Trees.TreeControl) view.control("shortcuts");
                assertThat(tree.laned()).isTrue();
                var group = tree.mounted.get("group/Clipboard"); var leaf = tree.mounted.get("action/copy");
                int width = leaf.getWidth();
                int scope = tree.scopeLaneStart(width), shortcut = tree.shortcutLaneStart(width);
                assertThat(width - shortcut).as("the Shortcut lane").isEqualTo(137);
                assertThat(shortcut - scope).as("the Scope lane").isEqualTo(107);
                var keysLabel = KitViewTest.find(leaf, "@tree/shortcuts/action/copy/keys", javax.swing.JLabel.class);
                var count = KitViewTest.find(group, "@tree/shortcuts/group/Clipboard/trailing/1", javax.swing.JLabel.class);
                var where = KitViewTest.find(leaf, "@tree/shortcuts/action/copy/context", javax.swing.JLabel.class);
                assertThat(SwingUtilities.convertPoint(keysLabel, 0, 0, leaf).x).as("keys 25 into the Shortcut lane").isEqualTo(shortcut + 25);
                assertThat(SwingUtilities.convertPoint(count, 0, 0, group).x).as("the section's count with the keys").isEqualTo(shortcut + 25);
                assertThat(SwingUtilities.convertPoint(where, where.getWidth(), 0, leaf).x).as("the scope ends 30 before Shortcut").isEqualTo(shortcut - 30);
                assertThat(where.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.RIGHT);
                var titles = tree.header.getComponents();
                assertThat(titles[1].getX()).as("Scope's title over its lane").isEqualTo(scope);
                assertThat(titles[2].getX()).as("Shortcut's title over its lane").isEqualTo(shortcut);
                var dot = KitViewTest.find(group, "@tree/shortcuts/group/Clipboard/modified", javax.swing.JComponent.class);
                assertThat(SwingUtilities.convertPoint(dot, 0, 0, group).x).as("the modified mark follows the title")
                    .isLessThan(scope).isGreaterThan(group.title.getX());
            } finally { release(context); }
        });
    }

    @Test void aKeycapTreeWithoutColumnsIsUnchanged() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = tree().root() instanceof Nodes.Tree t ? new ViewSpec(new Nodes.Tree(t.id(), t.state(), t.roots(), t.expandedIds(), t.selectedIds(), t.activateAction())) : null;
            try (var view = new KitView(context, spec, event -> { })) {
                view.setTreeRowPresentation("shortcuts", Map.of("action/copy", new TreeRowPresentation("Global", false, Optional.of(keys()))));
                view.setSize(777, 300);
                InteractiveRowsTest.layout(view);
                assertThat(((Trees.TreeControl) view.control("shortcuts")).laned()).isFalse();
            } finally { release(context); }
        });
    }
}
