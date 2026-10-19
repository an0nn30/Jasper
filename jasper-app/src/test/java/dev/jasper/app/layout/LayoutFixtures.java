package dev.jasper.app.layout;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Short names for layout tests: ids by number, groups, children, splits and layouts. */
final class LayoutFixtures {
    private LayoutFixtures() { }

    static TabId tab(int n) {
        return new TabId(new UUID(0, n));
    }

    static GroupId groupId(int n) {
        return new GroupId(new UUID(1, n));
    }

    /** Group {@code id} holding the one tab of the same number. */
    static Group group(int id) {
        return group(id, id, id);
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(LayoutFixtures::tab).toList(), Optional.of(tab(selected)));
    }

    static Child child(Node node, double weight) {
        return new Child(node, weight);
    }

    static Split h(Child... children) {
        return new Split(Orientation.HORIZONTAL, List.of(children));
    }

    static Split v(Child... children) {
        return new Split(Orientation.VERTICAL, List.of(children));
    }

    /** A layout over {@code root} with group {@code active} active and nothing maximized. */
    static Layout layout(Node root, int active) {
        return new Layout(root, groupId(active), Optional.empty());
    }
}
