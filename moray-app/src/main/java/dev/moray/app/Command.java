package dev.moray.app;

import java.util.List;
import java.util.Objects;
import javax.swing.Action;
import javax.swing.Icon;

record Command(String id, Action action, List<String> keywords) {
    static final String TITLE = "moray.command.title";
    static final String ICON = "moray.command.icon";

    Command {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("Invalid command ID");
        Objects.requireNonNull(action);
        keywords = List.copyOf(keywords);
        if (!(action.getValue(Action.NAME) instanceof String title) || title.isBlank())
            throw new IllegalArgumentException("Command needs a title");
    }

    String title() {
        Object title = action.getValue(TITLE);
        return title instanceof String value && !value.isBlank()
            ? value : (String) action.getValue(Action.NAME);
    }

    Icon icon() {
        Object icon = action.getValue(ICON);
        return icon instanceof Icon value ? value
            : action.getValue(Action.SMALL_ICON) instanceof Icon value ? value : null;
    }
}
